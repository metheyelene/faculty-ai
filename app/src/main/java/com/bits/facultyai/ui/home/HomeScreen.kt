package com.bits.facultyai.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bits.facultyai.data.local.ClassSlotEntity
import com.bits.facultyai.domain.ContextEngine
import com.bits.facultyai.domain.ScheduleEngine
import com.bits.facultyai.domain.TimeUtils
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import com.bits.facultyai.ui.components.KineticBlock
import com.bits.facultyai.ui.navigation.KineticBottomNavigation
import com.bits.facultyai.ui.theme.KineticBorder
import com.bits.facultyai.ui.components.GlassCard
import com.bits.facultyai.ui.components.GlassStat
import com.bits.facultyai.ui.components.GlassSurface
import com.bits.facultyai.ui.components.GlassStrength
import com.bits.facultyai.ui.components.KineticCard
import com.bits.facultyai.ui.components.KineticDisplayText
import com.bits.facultyai.ui.components.KineticEntrance
import com.bits.facultyai.ui.components.KineticEmptyState
import com.bits.facultyai.ui.components.KineticSectionHeader
import com.bits.facultyai.ui.theme.KineticSpacing
import com.bits.facultyai.ui.theme.KineticType
import com.bits.facultyai.ui.theme.LocalKineticColors
import java.time.LocalDate

@Composable
fun HomeScreen(
    onNavigate: (String) -> Unit,
    vm: HomeViewModel = viewModel(),
) {
    val k = LocalKineticColors.current
    val profile by vm.profile.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val timetable by vm.timetable.collectAsStateWithLifecycle()
    val tasks by vm.tasks.collectAsStateWithLifecycle()
    val notes by vm.notes.collectAsStateWithLifecycle()
    val events by vm.upcomingEvents.collectAsStateWithLifecycle()
    val nowMinute by vm.nowMinuteOfDay.collectAsStateWithLifecycle()

    val ctx = ContextEngine.computeDayContext(
        timetable = timetable,
        tasks = tasks,
        todayDayOfWeek = LocalDate.now().dayOfWeek.value,
        nowMinutes = nowMinute,
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = KineticSpacing.lg),
    ) {
        Spacer(Modifier.height(KineticSpacing.xl))

        // Brand line — tiny editorial mark above the greeting.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(6.dp).background(k.accent))
            Spacer(Modifier.width(KineticSpacing.sm))
            Text(
                text = "FACULTY AI",
                style = KineticType.labelBold.copy(fontSize = 11.sp),
                color = k.mutedForeground,
            )
        }
        Spacer(Modifier.height(KineticSpacing.sm))

        // Personal greeting from real profile data
        Text(
            text = ContextEngine.greeting(hour = java.time.LocalTime.now().hour, style = settings?.greetingStyle ?: 0),
            style = KineticType.labelBold.copy(fontSize = 13.sp),
            color = k.accent,
        )
        KineticDisplayText(
            text = ContextEngine.displayName(profile).ifBlank { "FACULTY" },
            style = KineticType.display.copy(fontSize = 40.sp),
        )
        val subtitle = listOfNotNull(profile?.designation, profile?.department)
            .filter { it.isNotBlank() }.joinToString(" · ")
        if (subtitle.isNotBlank()) {
            Text(
                text = subtitle,
                style = KineticType.label.copy(fontSize = 12.sp),
                color = k.mutedForeground,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Spacer(Modifier.height(KineticSpacing.xl))

        // TODAY — the command-center stat band: oversized numerals on quiet
        // glass layers, straight from the faculty's real data.
        Row(horizontalArrangement = Arrangement.spacedBy(KineticSpacing.sm)) {
            GlassStat(
                value = ctx.classesToday.toString().padStart(2, '0'),
                label = "CLASSES",
                modifier = Modifier.weight(1f),
                emphasized = ctx.classesToday > 0,
            )
            GlassStat(
                value = ctx.tasksOpen.toString().padStart(2, '0'),
                label = "TASKS",
                modifier = Modifier.weight(1f),
            )
            GlassStat(
                value = events.size.toString().padStart(2, '0'),
                label = "EVENTS",
                modifier = Modifier.weight(1f),
            )
        }

        KineticEntrance(index = 0) {
            Column {
                KineticSectionHeader(title = "NEXT CLASS")
                val next = ctx.nextClass
                if (next != null) {
                    NextClassCard(next = next, minutesUntil = ctx.minutesUntilNextClass, date = LocalDate.now())
                } else {
                    KineticEmptyState(
                        title = "YOUR SCHEDULE IS CLEAR TODAY",
                        message = if (timetable.isEmpty()) "YOUR TIMETABLE HASN'T BEEN ADDED YET" else "NO MORE CLASSES TODAY",
                        actionText = if (timetable.isEmpty()) "OPEN TIMETABLE" else "VIEW WEEK",
                        onAction = { onNavigate("timetable") },
                    )
                }
            }
        }

        ContextEngine.briefLine(ctx)?.let { line ->
            Spacer(Modifier.height(KineticSpacing.lg))
            KineticBlock(borderColor = k.accent) {
                Text(text = line, style = KineticType.bodyMedium, color = k.accent)
            }
        }

        Spacer(Modifier.height(KineticSpacing.xl))
        KineticEntrance(index = 1) {
            Column {
                KineticSectionHeader(
                    title = "TODAY'S SCHEDULE",
                    trailing = {
                        Text(
                            text = if (ctx.classesToday > 0) "${ctx.classesToday} CLASS${if (ctx.classesToday > 1) "ES" else ""}" else "—",
                            style = KineticType.labelBold,
                            color = k.accent,
                        )
                    },
                )
            }
        }
        val todaySlots = ScheduleEngine.slotsForDay(timetable, LocalDate.now().dayOfWeek.value)
        if (todaySlots.isEmpty()) {
            Text(
                text = if (timetable.isEmpty())
                    "Your timetable hasn't been added yet — open MY TIMETABLE and upload a photo."
                else "Nothing scheduled today. Your schedule is clear.",
                style = KineticType.bodyMedium,
                color = k.mutedForeground,
            )
        } else {
            todaySlots.forEach { slot ->
                TodaySlotRow(
                    slot = slot,
                    state = when {
                        nowMinute >= slot.endTimeMinutes -> "PAST"
                        nowMinute >= slot.startTimeMinutes -> "NOW"
                        else -> "UPCOMING"
                    },
                    onClick = { onNavigate("timetable") },
                )
            }
        }

        Spacer(Modifier.height(KineticSpacing.xl))
        KineticEntrance(index = 2) {
            Column {
                KineticSectionHeader(title = "UPCOMING EVENTS")
            }
        }
        if (events.isEmpty()) {
            Text(
                text = "No academic events yet. Add semester dates and exams in the calendar.",
                style = KineticType.bodyMedium,
                color = k.mutedForeground,
            )
        } else {
            events.take(3).forEach { event ->
                GlassCard(strength = GlassStrength.ULTRA_THIN, onClick = { onNavigate("calendar") }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(text = event.title, style = KineticType.bodyMedium, color = k.foreground, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            val date = runCatching { LocalDate.parse(event.date) }.getOrNull()
                            Text(
                                text = (date?.let { TimeUtils.relativeDayLabel(it) } ?: "") + " · " + event.category,
                                style = KineticType.label.copy(fontSize = 12.sp),
                                color = k.mutedForeground,
                            )
                        }
                        Text(text = "→", style = KineticType.headingSm, color = k.accent)
                    }
                }
                Spacer(Modifier.height(KineticSpacing.sm))
            }
        }

        Spacer(Modifier.height(KineticSpacing.xl))
        KineticSectionHeader(
            title = "TASKS",
            trailing = {
                val open = ctx.tasksOpen
                if (open > 0) {
                    Text(text = "$open OPEN", style = KineticType.labelBold, color = k.accent)
                }
            },
        )
        val openTasks = tasks.filter { !it.completed }.take(3)
        if (openTasks.isEmpty()) {
            Text(text = "You're all caught up.", style = KineticType.bodyMedium, color = k.mutedForeground)
        } else {
            openTasks.forEach { task ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onNavigate("tasks") }
                        .padding(vertical = KineticSpacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .size(10.dp)
                            .border(KineticBorder.heavy, k.foreground),
                    )
                    Spacer(Modifier.width(KineticSpacing.md))
                    Text(
                        text = task.title,
                        style = KineticType.bodyMedium,
                        color = k.foreground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    task.dueAt?.let {
                        Text(
                            text = TimeUtils.relativeDayLabel(
                                java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
                            ),
                            style = KineticType.label.copy(fontSize = 11.sp),
                            color = k.accent,
                        )
                    }
                }
            }
        }

        if (notes.isNotEmpty()) {
            Spacer(Modifier.height(KineticSpacing.xl))
            KineticSectionHeader(title = "RECENT NOTES")
            notes.take(2).forEach { note ->
                GlassCard(strength = GlassStrength.ULTRA_THIN, onClick = { onNavigate("notes") }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(text = note.title, style = KineticType.bodyMedium, color = k.foreground, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(text = note.folder, style = KineticType.label.copy(fontSize = 11.sp), color = k.mutedForeground)
                        }
                        Text(text = "→", style = KineticType.headingSm, color = k.accent)
                    }
                }
                Spacer(Modifier.height(KineticSpacing.sm))
            }
        }

        Spacer(Modifier.height(KineticSpacing.xl))
        KineticBlock(
            onClick = { onNavigate("assistant") },
            backgroundColor = k.accent,
        ) {
            Text(text = "YOUR ASSISTANT", style = KineticType.labelBold, color = k.accentForeground)
            Text(text = "\"What should we work on?\"", style = KineticType.headingSm, color = k.accentForeground)
        }
        // Clears the floating glass dock + system navigation area.
        Spacer(Modifier.height(KineticBottomNavigation.bottomClearance()))
    }
}

@Composable
private fun TodaySlotRow(slot: ClassSlotEntity, state: String, onClick: () -> Unit) {
    val k = LocalKineticColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = KineticSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = TimeUtils.formatTime(slot.startTimeMinutes),
            style = KineticType.bodyMedium.copy(fontSize = 15.sp),
            color = if (state == "PAST") k.mutedForeground else k.accent,
            modifier = Modifier.width(88.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(
                text = slot.subject,
                style = KineticType.bodyMedium,
                color = if (state == "PAST") k.mutedForeground else k.foreground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = slot.section + " · ROOM " + slot.room,
                style = KineticType.label.copy(fontSize = 11.sp),
                color = k.mutedForeground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        when (state) {
            "NOW" -> Text(text = "NOW", style = KineticType.labelBold, color = k.accent)
            "PAST" -> Text(text = "DONE", style = KineticType.label.copy(fontSize = 10.sp), color = k.mutedForeground)
        }
    }
}

@Composable
private fun NextClassCard(next: ClassSlotEntity, minutesUntil: Int?, date: LocalDate) {
    val k = LocalKineticColors.current
    val isLive = minutesUntil != null && minutesUntil >= 0 && minutesUntil <= 0
    GlassSurface(strength = GlassStrength.THIN, selected = isLive) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            // Accent spine — urgency marker, editorial structure.
            Box(
                Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(k.accent)
            )
            Column(Modifier.padding(KineticSpacing.lg)) {
                Text(
                    text = ScheduleEngine.whenLabel(
                        ScheduleEngine.Upcoming(next, date, isToday = minutesUntil != null && minutesUntil >= 0)
                    ),
                    style = KineticType.labelBold.copy(fontSize = 12.sp),
                    color = k.accent,
                )
                Spacer(Modifier.height(KineticSpacing.xs))
                Text(
                    text = next.subject.uppercase(),
                    style = KineticType.heading,
                    color = k.foreground,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = TimeUtils.formatTime(next.startTimeMinutes) + "  ·  " + next.section + "  ·  ROOM " + next.room,
                    style = KineticType.label.copy(fontSize = 12.sp),
                    color = k.mutedForeground,
                )
                if (minutesUntil != null && minutesUntil in 0..90) {
                    Spacer(Modifier.height(KineticSpacing.xs))
                    Text(
                        text = "STARTS IN $minutesUntil MIN",
                        style = KineticType.labelBold,
                        color = k.accent,
                    )
                }
            }
        }
    }
}
