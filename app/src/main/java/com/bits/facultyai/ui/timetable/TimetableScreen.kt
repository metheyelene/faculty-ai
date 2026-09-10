package com.bits.facultyai.ui.timetable

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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bits.facultyai.data.local.ClassSlotEntity
import com.bits.facultyai.domain.TimeUtils
import com.bits.facultyai.ui.theme.KineticBorder
import com.bits.facultyai.ui.components.KineticButton
import com.bits.facultyai.ui.components.KineticEmptyState
import com.bits.facultyai.ui.components.KineticOutlinedButton
import com.bits.facultyai.ui.components.KineticTextField
import com.bits.facultyai.ui.theme.KineticSpacing
import com.bits.facultyai.ui.theme.KineticType
import com.bits.facultyai.ui.theme.LocalKineticColors

@Composable
fun TimetableScreen(vm: TimetableViewModel = viewModel()) {
    val k = LocalKineticColors.current
    val timetable by vm.timetable.collectAsStateWithLifecycle()
    val selectedDay by vm.selectedDay.collectAsStateWithLifecycle()
    val showAdd by vm.showAddDialog.collectAsStateWithLifecycle()
    var expandedSlotId by remember { mutableStateOf<Long?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(k.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = KineticSpacing.lg),
    ) {
        Spacer(Modifier.height(KineticSpacing.xl))
        Text(text = "MY", style = KineticType.display, color = k.foreground)
        Text(text = "TIMETABLE", style = KineticType.display, color = k.accent)
        Spacer(Modifier.height(KineticSpacing.lg))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(KineticSpacing.sm)) {
            listOf(1, 2, 3, 4, 5, 6).forEach { day ->
                val selected = selectedDay == day
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .border(if (selected) KineticBorder.heavy else KineticBorder.hair, if (selected) k.accent else k.border)
                        .background(if (selected) k.accent else Color.Transparent)
                        .clickable { vm.selectDay(day) }
                        .padding(vertical = KineticSpacing.sm),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = TimeUtils.dayShort(day),
                        style = KineticType.labelBold,
                        color = if (selected) k.accentForeground else k.mutedForeground,
                    )
                }
            }
        }
        Spacer(Modifier.height(KineticSpacing.lg))

        val slots = timetable.filter { it.dayOfWeek == selectedDay }.sortedBy { it.startTimeMinutes }
        if (slots.isEmpty()) {
            KineticEmptyState(
                title = "NO CLASSES",
                message = "NOTHING SCHEDULED FOR " + TimeUtils.dayName(selectedDay),
                actionText = "ADD CLASS",
                onAction = { vm.openAddDialog() },
            )
        } else {
            slots.forEach { slot ->
                val nowMin = TimeUtils.nowMinutes()
                val isToday = selectedDay == java.time.LocalDate.now().dayOfWeek.value
                ClassCard(
                    slot = slot,
                    isNow = isToday && nowMin >= slot.startTimeMinutes && nowMin < slot.endTimeMinutes,
                    isPast = isToday && nowMin >= slot.endTimeMinutes,
                    expanded = expandedSlotId == slot.id,
                    onClick = { expandedSlotId = if (expandedSlotId == slot.id) null else slot.id },
                    onTakeAttendance = { vm.onAttendanceRequested(slot) },
                    onDelete = { vm.deleteSlot(slot.id) },
                )
                Spacer(Modifier.height(KineticSpacing.sm))
            }
        }

        Spacer(Modifier.height(KineticSpacing.md))
        KineticButton(text = "ADD CLASS", onClick = { vm.openAddDialog() })
        Spacer(Modifier.height(96.dp))
    }

    if (showAdd) {
        AddClassDialog(vm = vm)
    }
}

@Composable
private fun ClassCard(
    slot: ClassSlotEntity,
    isNow: Boolean,
    isPast: Boolean,
    expanded: Boolean,
    onClick: () -> Unit,
    onTakeAttendance: () -> Unit,
    onDelete: () -> Unit,
) {
    val k = LocalKineticColors.current
    val bg = when {
        isNow -> k.accent
        isPast -> k.muted
        else -> Color.Transparent
    }
    val fg = when {
        isNow -> k.accentForeground
        isPast -> k.mutedForeground
        else -> k.foreground
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(KineticBorder.heavy, if (isNow) k.accent else k.border)
            .background(bg)
            .clickable(onClick = onClick)
            .padding(KineticSpacing.lg),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Text(
                text = TimeUtils.formatTime(slot.startTimeMinutes),
                style = KineticType.headingSm,
                color = fg,
            )
            Spacer(Modifier.width(KineticSpacing.sm))
            Text(
                text = "— " + TimeUtils.formatTime(slot.endTimeMinutes),
                style = KineticType.label,
                color = fg.copy(alpha = 0.7f),
            )
        }
        Text(
            text = slot.subject.uppercase(),
            style = KineticType.heading,
            color = fg,
        )
        Text(
            text = slot.section + " · ROOM " + slot.room,
            style = KineticType.label,
            color = fg.copy(alpha = 0.8f),
        )
        if (expanded) {
            Spacer(Modifier.height(KineticSpacing.md))
            Row(horizontalArrangement = Arrangement.spacedBy(KineticSpacing.sm)) {
                KineticButton(text = "ATTENDANCE", onClick = onTakeAttendance, height = 40, modifier = Modifier.weight(1f))
                KineticOutlinedButton(text = "DELETE", onClick = onDelete, height = 40, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun AddClassDialog(vm: TimetableViewModel) {
    val k = LocalKineticColors.current
    var subject by remember { mutableStateOf("") }
    var section by remember { mutableStateOf("") }
    var room by remember { mutableStateOf("") }
    var startHour by remember { mutableStateOf("9") }
    var startMin by remember { mutableStateOf("00") }
    var endHour by remember { mutableStateOf("10") }
    var endMin by remember { mutableStateOf("00") }
    val selectedDay by vm.selectedDay.collectAsStateWithLifecycle()

    androidx.compose.ui.window.Dialog(onDismissRequest = { vm.closeAddDialog() }) {
        Column(
            modifier = Modifier
                .background(k.background)
                .border(KineticBorder.heavy, k.accent)
                .padding(KineticSpacing.lg),
        ) {
            Text("ADD CLASS", style = KineticType.heading, color = k.foreground)
            Spacer(Modifier.height(KineticSpacing.md))
            KineticTextField(value = subject, onValueChange = { subject = it }, hint = "SUBJECT")
            Spacer(Modifier.height(KineticSpacing.sm))
            KineticTextField(value = section, onValueChange = { section = it }, hint = "SECTION (e.g. III ECE-A)")
            Spacer(Modifier.height(KineticSpacing.sm))
            KineticTextField(value = room, onValueChange = { room = it }, hint = "ROOM")
            Spacer(Modifier.height(KineticSpacing.sm))
            Row(horizontalArrangement = Arrangement.spacedBy(KineticSpacing.sm)) {
                KineticTextField(value = startHour, onValueChange = { startHour = it }, hint = "START H", modifier = Modifier.weight(1f))
                KineticTextField(value = startMin, onValueChange = { startMin = it }, hint = "MIN", modifier = Modifier.weight(1f))
                KineticTextField(value = endHour, onValueChange = { endHour = it }, hint = "END H", modifier = Modifier.weight(1f))
                KineticTextField(value = endMin, onValueChange = { endMin = it }, hint = "MIN", modifier = Modifier.weight(1f))
            }
            Spacer(Modifier.height(KineticSpacing.lg))
            Row(horizontalArrangement = Arrangement.spacedBy(KineticSpacing.sm)) {
                KineticOutlinedButton(text = "CANCEL", onClick = { vm.closeAddDialog() }, modifier = Modifier.weight(1f))
                KineticButton(
                    text = "SAVE",
                    onClick = {
                        val s = (startHour.toIntOrNull() ?: 9) * 60 + (startMin.toIntOrNull() ?: 0)
                        val e = (endHour.toIntOrNull() ?: 10) * 60 + (endMin.toIntOrNull() ?: 0)
                        if (subject.isNotBlank() && e > s) {
                            vm.addSlot(
                                ClassSlotEntity(
                                    dayOfWeek = selectedDay,
                                    startTimeMinutes = s,
                                    endTimeMinutes = e,
                                    subject = subject.trim(),
                                    section = section.ifBlank { "—" },
                                    room = room.ifBlank { "—" },
                                )
                            )
                            vm.closeAddDialog()
                        }
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
