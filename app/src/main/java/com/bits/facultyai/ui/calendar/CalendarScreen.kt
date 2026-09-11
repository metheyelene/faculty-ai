package com.bits.facultyai.ui.calendar

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bits.facultyai.data.local.AcademicEventEntity
import com.bits.facultyai.domain.TimeUtils
import com.bits.facultyai.ui.components.GlassTopBar
import com.bits.facultyai.ui.components.KineticBadge
import com.bits.facultyai.ui.components.KineticButton
import com.bits.facultyai.ui.components.KineticDisplayText
import com.bits.facultyai.ui.components.KineticDivider
import com.bits.facultyai.ui.components.KineticEmptyState
import com.bits.facultyai.ui.components.KineticGhostButton
import com.bits.facultyai.ui.components.KineticOutlinedButton
import com.bits.facultyai.ui.components.KineticTextField
import com.bits.facultyai.ui.theme.KineticBorder
import com.bits.facultyai.ui.theme.KineticSpacing
import com.bits.facultyai.ui.theme.KineticType
import com.bits.facultyai.ui.theme.LocalKineticColors
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val EVENT_CATEGORIES = listOf("EXAM", "DEADLINE", "MEETING", "HOLIDAY", "ACADEMIC", "EVENT")

@Composable
fun CalendarScreen(vm: CalendarViewModel = viewModel(), onBack: () -> Unit) {
    val k = LocalKineticColors.current
    val events by vm.events.collectAsStateWithLifecycle()
    val month by vm.visibleMonth.collectAsStateWithLifecycle()
    val selectedDate by vm.selectedDate.collectAsStateWithLifecycle()
    var showAdd by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(k.background)
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(horizontal = KineticSpacing.lg),
    ) {
        Spacer(Modifier.height(KineticSpacing.xl))
        GlassTopBar(title = "ACADEMIC CALENDAR", onBack = onBack)
        Spacer(Modifier.height(KineticSpacing.lg))

        // ---- Month switcher ----
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "←",
                style = KineticType.heading,
                color = k.accent,
                modifier = Modifier
                    .clickable { vm.previousMonth() }
                    .padding(KineticSpacing.sm),
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = month.format(DateTimeFormatter.ofPattern("MMMM yyyy")).uppercase(),
                style = KineticType.headingSm,
                color = k.foreground,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "→",
                style = KineticType.heading,
                color = k.accent,
                modifier = Modifier
                    .clickable { vm.nextMonth() }
                    .padding(KineticSpacing.sm),
            )
        }
        Spacer(Modifier.height(KineticSpacing.md))

        // Month grid slides like a physical calendar page.
        val monthShift = remember { androidx.compose.animation.core.Animatable(0f) }
        var lastMonth by remember { androidx.compose.runtime.mutableStateOf(month) }
        LaunchedEffect(month) {
            if (month != lastMonth) {
                val forward = month.isAfter(lastMonth)
                monthShift.snapTo(if (forward) 80f else -80f)
                monthShift.animateTo(0f, com.bits.facultyai.ui.theme.KineticMotion.springStandard())
                lastMonth = month
            }
        }

        val today = LocalDate.now()

        Column(
            modifier = Modifier.graphicsLayer {
                translationX = monthShift.value
                alpha = 1f - (kotlin.math.abs(monthShift.value) / 160f)
            },
        ) {
        // ---- Weekday header ----
        Row(modifier = Modifier.fillMaxWidth()) {
            listOf("M", "T", "W", "T", "F", "S", "S").forEach { d ->
                Text(
                    text = d,
                    style = KineticType.label,
                    color = k.mutedForeground,
                    modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
        Spacer(Modifier.height(KineticSpacing.xs))

        // ---- Month grid ----
        val firstOfMonth = month.atDay(1)
        val leadingBlanks = (firstOfMonth.dayOfWeek.value + 6) % 7 // Monday-first grid
        val daysInMonth = month.lengthOfMonth()
        val cells = List(leadingBlanks) { null } + (1..daysInMonth).map { month.atDay(it) }
        val today = LocalDate.now()
        val eventDates = events.mapNotNull { runCatching { LocalDate.parse(it.date) }.getOrNull() }

        cells.chunked(7).forEach { week ->
            Row(modifier = Modifier.fillMaxWidth()) {
                week.forEach { day ->
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (day == null) {
                            Spacer(Modifier.height(40.dp))
                        } else {
                            val isSelected = selectedDate == day
                            val isToday = day == today
                            val hasEvent = eventDates.contains(day)
                            Column(
                                modifier = Modifier
                                    .clickable { vm.selectDate(day) }
                                    .padding(2.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .border(
                                            when {
                                                isSelected -> KineticBorder.heavy
                                                isToday -> KineticBorder.standard
                                                else -> KineticBorder.hair
                                            },
                                            when {
                                                isSelected -> k.accent
                                                isToday -> k.foreground
                                                else -> Color.Transparent
                                            },
                                        )
                                        .background(if (isSelected) k.accent else Color.Transparent),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = day.dayOfMonth.toString(),
                                        style = KineticType.bodyMedium.copy(fontSize = 13.sp),
                                        color = if (isSelected) k.accentForeground else k.foreground,
                                    )
                                }
                                Box(
                                    Modifier
                                        .padding(top = 2.dp)
                                        .size(4.dp)
                                        .background(if (hasEvent) k.accent else Color.Transparent)
                                )
                            }
                        }
                    }
                }
                // pad short weeks
                if (week.size < 7) {
                    repeat(7 - week.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
        } // end month-slide column

        Spacer(Modifier.height(KineticSpacing.lg))

        // ---- Agenda for the selected day ----
        val selected = selectedDate
        if (selected != null) {
            val dayEvents = events.filter { it.date == selected.toString() }
            KineticSectionHeaderLocal(
                title = TimeUtils.formatDate(selected).uppercase() + (if (selected == today) " · TODAY" else ""),
            )
            if (dayEvents.isEmpty()) {
                Text(
                    text = "NOTHING SCHEDULED FOR THIS DAY",
                    style = KineticType.label,
                    color = k.mutedForeground,
                )
            } else {
                dayEvents.forEach { event ->
                    EventRow(event = event, onDelete = { vm.deleteEvent(event.id) })
                    KineticDivider()
                }
            }
        }

        Spacer(Modifier.height(KineticSpacing.md))
        KineticButton(text = "ADD EVENT", onClick = { showAdd = true })
        Spacer(Modifier.height(KineticSpacing.md))

        // ---- Upcoming agenda (next 30 days) ----
        KineticSectionHeaderLocal(title = "UPCOMING")
        val upcoming = events
            .mapNotNull { e -> runCatching { LocalDate.parse(e.date) }.getOrNull()?.let { e to it } }
            .filter { it.second >= today }
            .sortedBy { it.second }
            .take(8)
        if (upcoming.isEmpty()) {
            KineticEmptyState(
                title = "NO UPCOMING EVENTS",
                message = "ADD SEMESTER DATES, EXAMS AND MEETINGS",
            )
        } else {
            upcoming.forEach { (event, date) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = KineticSpacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(text = event.title, style = KineticType.bodyMedium, color = k.foreground)
                        Text(
                            text = TimeUtils.relativeDayLabel(date) + " · " + TimeUtils.formatDate(date),
                            style = KineticType.label,
                            color = k.mutedForeground,
                        )
                    }
                    KineticBadge(text = event.category, filled = event.category == "EXAM")
                }
                KineticDivider()
            }
        }

        Spacer(Modifier.height(KineticSpacing.xl))
    }

    if (showAdd) {
        AddEventDialog(vm = vm, defaultDate = selectedDate ?: LocalDate.now(), onDismiss = { showAdd = false })
    }
}

@Composable
private fun KineticSectionHeaderLocal(title: String) {
    val k = LocalKineticColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = KineticSpacing.xl, bottom = KineticSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = title, style = KineticType.labelBold, color = k.foreground)
        Spacer(Modifier.width(KineticSpacing.md))
        Box(Modifier.weight(1f).height(1.dp).background(k.border))
    }
}

@Composable
private fun EventRow(event: AcademicEventEntity, onDelete: () -> Unit) {
    val k = LocalKineticColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = KineticSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(text = event.title, style = KineticType.bodyMedium, color = k.foreground)
            KineticBadge(text = event.category)
        }
        Spacer(Modifier.width(KineticSpacing.md))
        Text(
            text = "✕",
            style = KineticType.labelBold,
            color = k.mutedForeground,
            modifier = Modifier
                .clickable(onClick = onDelete)
                .padding(KineticSpacing.sm),
        )
    }
}

@Composable
private fun AddEventDialog(vm: CalendarViewModel, defaultDate: LocalDate, onDismiss: () -> Unit) {
    val k = LocalKineticColors.current
    var title by remember { mutableStateOf("") }
    var day by remember { mutableStateOf(defaultDate.dayOfMonth.toString()) }
    var monthNum by remember { mutableStateOf(defaultDate.monthValue.toString()) }
    var category by remember { mutableStateOf("EXAM") }

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        com.bits.facultyai.ui.components.GlassDialogSurface {
            Text("ADD EVENT", style = KineticType.heading, color = k.foreground)
            Spacer(Modifier.height(KineticSpacing.md))
            KineticTextField(value = title, onValueChange = { title = it }, hint = "EVENT TITLE")
            Spacer(Modifier.height(KineticSpacing.sm))
            Row(horizontalArrangement = Arrangement.spacedBy(KineticSpacing.sm)) {
                KineticTextField(value = day, onValueChange = { day = it }, hint = "DAY", modifier = Modifier.weight(1f), keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number))
                KineticTextField(value = monthNum, onValueChange = { monthNum = it }, hint = "MONTH", modifier = Modifier.weight(1f), keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number))
            }
            Spacer(Modifier.height(KineticSpacing.sm))
            Row(horizontalArrangement = Arrangement.spacedBy(KineticSpacing.xs), modifier = Modifier.fillMaxWidth()) {
                EVENT_CATEGORIES.take(3).forEach { c ->
                    CategoryChip(c, category == c, Modifier.weight(1f)) { category = c }
                }
            }
            Spacer(Modifier.height(KineticSpacing.xs))
            Row(horizontalArrangement = Arrangement.spacedBy(KineticSpacing.xs), modifier = Modifier.fillMaxWidth()) {
                EVENT_CATEGORIES.drop(3).forEach { c ->
                    CategoryChip(c, category == c, Modifier.weight(1f)) { category = c }
                }
            }
            Spacer(Modifier.height(KineticSpacing.lg))
            Row(horizontalArrangement = Arrangement.spacedBy(KineticSpacing.sm)) {
                KineticOutlinedButton(text = "CANCEL", onClick = onDismiss, modifier = Modifier.weight(1f))
                KineticButton(
                    text = "SAVE",
                    onClick = {
                        val date = runCatching {
                            java.time.LocalDate.of(java.time.Year.now().value, monthNum.toIntOrNull() ?: defaultDate.monthValue, day.toIntOrNull() ?: defaultDate.dayOfMonth)
                        }.getOrNull()
                        if (title.isNotBlank() && date != null) {
                            vm.addEvent(title, date, category)
                            onDismiss()
                        }
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun CategoryChip(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val k = LocalKineticColors.current
    Box(
        modifier = modifier
            .border(if (selected) KineticBorder.heavy else KineticBorder.hair, if (selected) k.accent else k.border)
            .background(if (selected) k.accent else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = KineticSpacing.sm, vertical = KineticSpacing.sm),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = KineticType.label.copy(fontSize = 10.sp),
            color = if (selected) k.accentForeground else k.mutedForeground,
        )
    }
}
