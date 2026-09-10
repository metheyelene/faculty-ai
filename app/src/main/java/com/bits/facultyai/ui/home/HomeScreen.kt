package com.bits.facultyai.ui.home

import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
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
import com.bits.facultyai.domain.FacultyAssistant
import com.bits.facultyai.domain.TimeUtils
import com.bits.facultyai.ui.components.KineticBlock
import com.bits.facultyai.ui.theme.KineticBorder
import com.bits.facultyai.ui.components.KineticCard
import com.bits.facultyai.ui.components.KineticDisplayText
import com.bits.facultyai.ui.components.KineticEmptyState
import com.bits.facultyai.ui.components.KineticSectionHeader
import com.bits.facultyai.ui.theme.KineticSpacing
import com.bits.facultyai.ui.theme.KineticType
import com.bits.facultyai.ui.theme.LocalKineticColors

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
    val nowMinute by vm.nowMinuteOfDay.collectAsStateWithLifecycle()

    val ctx = ContextEngine.computeDayContext(
        timetable = timetable,
        tasks = tasks,
        todayDayOfWeek = java.time.LocalDate.now().dayOfWeek.value,
        nowMinutes = nowMinute,
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = KineticSpacing.lg),
    ) {
        Spacer(Modifier.height(KineticSpacing.xl))

        // Personal greeting from real profile data
        Text(
            text = ContextEngine.greeting(hour = java.time.LocalTime.now().hour, style = settings?.greetingStyle ?: 0),
            style = KineticType.labelBold,
            color = k.accent,
        )
        KineticDisplayText(
            text = ContextEngine.displayName(profile),
            style = KineticType.display.copy(fontSize = 44.sp),
        )
        Text(
            text = listOfNotNull(profile?.designation, profile?.department).joinToString(" · "),
            style = KineticType.label,
            color = k.mutedForeground,
        )

        Spacer(Modifier.height(KineticSpacing.xl))

        KineticSectionHeader(title = "TODAY")
        Row(horizontalArrangement = Arrangement.spacedBy(KineticSpacing.xl)) {
            StatBlock(value = ctx.classesToday, label = "CLASSES", onClick = { onNavigate("timetable") })
            StatBlock(value = ctx.tasksOpen, label = "TASKS", onClick = { onNavigate("tasks") })
            StatBlock(value = ctx.deadlinesThisWeek, label = "DUE THIS WEEK", onClick = { onNavigate("tasks") })
        }

        Spacer(Modifier.height(KineticSpacing.xl))

        KineticSectionHeader(title = "NEXT CLASS")
        val next = ctx.nextClass
        if (next != null) {
            NextClassCard(next = next, minutesUntil = ctx.minutesUntilNextClass)
        } else {
            KineticEmptyState(
                title = "NO MORE CLASSES TODAY",
                message = "YOUR TIMETABLE IS CLEAR",
                actionText = "VIEW WEEK",
                onAction = { onNavigate("timetable") },
            )
        }

        ContextEngine.briefLine(ctx)?.let { line ->
            Spacer(Modifier.height(KineticSpacing.lg))
            KineticBlock(borderColor = k.accent) {
                Text(text = line, style = KineticType.bodyMedium, color = k.accent)
            }
        }

        KineticSectionHeader(title = "YOUR SHORTCUTS")
        Row(horizontalArrangement = Arrangement.spacedBy(KineticSpacing.sm)) {
            ShortcutCell("ATTENDANCE", { onNavigate("attendance") }, Modifier.weight(1f))
            ShortcutCell("TASKS", { onNavigate("tasks") }, Modifier.weight(1f))
            ShortcutCell("NOTES", { onNavigate("notes") }, Modifier.weight(1f))
            ShortcutCell("ASK AI", { onNavigate("assistant") }, Modifier.weight(1f))
        }

        KineticSectionHeader(title = "UPCOMING")
        val upcoming = FacultyAssistant.timetableForNextDayWithClasses(timetable, java.time.LocalDate.now())
        if (upcoming != null) {
            KineticCard(onClick = { onNavigate("timetable") }) {
                Column {
                    Text(
                        text = TimeUtils.dayShort(upcoming.dayOfWeek) + " · " + TimeUtils.formatTime(upcoming.startTimeMinutes),
                        style = KineticType.labelBold,
                        color = k.mutedForeground,
                    )
                    Text(
                        text = upcoming.subject.uppercase(),
                        style = KineticType.headingSm,
                        color = k.foreground,
                    )
                    Text(
                        text = upcoming.section + " · ROOM " + upcoming.room,
                        style = KineticType.label,
                        color = k.mutedForeground,
                    )
                }
            }
        } else {
            Text(text = "NOTHING SCHEDULED AHEAD", style = KineticType.label, color = k.mutedForeground)
        }

        if (notes.isNotEmpty()) {
            KineticSectionHeader(title = "RECENT NOTES")
            notes.take(2).forEach { note ->
                KineticCard(onClick = { onNavigate("notes") }) {
                    Column {
                        Text(text = note.title, style = KineticType.bodyMedium, color = k.foreground)
                        Text(text = note.folder, style = KineticType.label, color = k.mutedForeground)
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
        Spacer(Modifier.height(96.dp))
    }
}

@Composable
private fun StatBlock(value: Int, label: String, onClick: () -> Unit) {
    val k = LocalKineticColors.current
    var started by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { started = true }
    val display by animateIntAsState(
        targetValue = if (started) value else 0,
        animationSpec = tween(600),
        label = "stat",
    )
    Column(modifier = Modifier.clickable(onClick = onClick)) {
        Text(
            text = display.toString().padStart(2, '0'),
            style = KineticType.statNumber,
            color = k.foreground,
        )
        Text(text = label, style = KineticType.label, color = k.mutedForeground)
    }
}

@Composable
private fun ShortcutCell(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val k = LocalKineticColors.current
    Column(
        modifier = modifier
            .border(KineticBorder.hair, k.border)
            .clickable(onClick = onClick)
            .padding(KineticSpacing.md),
    ) {
        Box(
            Modifier
                .width(14.dp)
                .height(3.dp)
                .background(k.accent)
        )
        Spacer(Modifier.height(KineticSpacing.sm))
        Text(text = label, style = KineticType.labelBold, color = k.foreground)
    }
}

@Composable
private fun NextClassCard(next: ClassSlotEntity, minutesUntil: Int?) {
    val k = LocalKineticColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(KineticBorder.heavy, k.accent)
            .padding(KineticSpacing.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = next.subject.uppercase(),
                style = KineticType.heading,
                color = k.foreground,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = next.section + " · ROOM " + next.room,
                style = KineticType.label,
                color = k.mutedForeground,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = TimeUtils.formatTime(next.startTimeMinutes),
                style = KineticType.headingSm,
                color = k.accent,
            )
            if (minutesUntil != null && minutesUntil in 0..90) {
                Text(
                    text = "STARTS IN " + minutesUntil + " MIN",
                    style = KineticType.label,
                    color = k.accent,
                )
            }
        }
    }
}
