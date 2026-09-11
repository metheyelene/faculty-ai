package com.bits.facultyai.ui.attendance

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bits.facultyai.ui.components.KineticButton
import com.bits.facultyai.ui.components.KineticDivider
import com.bits.facultyai.ui.components.KineticGhostButton
import com.bits.facultyai.ui.components.KineticStat
import com.bits.facultyai.ui.students.ordinalYear
import com.bits.facultyai.ui.theme.KineticBorder
import com.bits.facultyai.ui.theme.KineticSpacing
import com.bits.facultyai.ui.theme.KineticType
import com.bits.facultyai.ui.theme.LocalKineticColors

@Composable
fun AttendanceClassScreen(
    slotId: Long,
    onDone: () -> Unit,
    vm: AttendanceViewModel = viewModel(),
) {
    val k = LocalKineticColors.current
    val timetable by vm.timetable.collectAsStateWithLifecycle()
    val students by vm.students.collectAsStateWithLifecycle()
    val marks by vm.marks.collectAsStateWithLifecycle()
    val slot = timetable.firstOrNull { it.id == slotId }

    LaunchedEffect(slotId, timetable.size, students.size) {
        if (students.isEmpty()) return@LaunchedEffect
        timetable.firstOrNull { it.id == slotId }?.let { vm.loadStudentsForSlot(it) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(k.background)
            .navigationBarsPadding()
            .padding(horizontal = KineticSpacing.lg),
    ) {
        Spacer(Modifier.height(KineticSpacing.xl))
        if (slot == null) {
            Text(text = "LOADING...", style = KineticType.heading, color = k.mutedForeground)
        } else {
            Text(text = "ATTENDANCE", style = KineticType.display.copy(fontSize = 40.sp), color = k.foreground)
            Text(
                text = slot.subject.uppercase() + " · " + slot.section,
                style = KineticType.labelBold,
                color = k.accent,
            )
            Spacer(Modifier.height(KineticSpacing.md))

            Row(horizontalArrangement = Arrangement.spacedBy(KineticSpacing.sm)) {
                KineticGhostButton(text = "ALL PRESENT", onClick = { vm.markAll("PRESENT") })
                KineticGhostButton(text = "ALL ABSENT", onClick = { vm.markAll("ABSENT") })
            }
            Spacer(Modifier.height(KineticSpacing.md))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
            ) {
                // Exact roster match: the slot's academic year AND section.
                val roster = students.filter { it.year == slot.year && it.section == slot.section }
                if (roster.isEmpty()) {
                    Text(
                        text = "NO STUDENTS IMPORTED FOR ${slot.year.ordinalYear()} YEAR · SECTION ${slot.section}. IMPORT THE ROSTER FROM THE STUDENTS TAB.",
                        style = KineticType.labelBold,
                        color = k.statusWarning,
                    )
                }
                roster.forEachIndexed { index, student ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = KineticSpacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = (index + 1).toString().padStart(2, '0'),
                            style = KineticType.labelBold,
                            color = k.mutedForeground,
                            modifier = Modifier.width(32.dp),
                        )
                        Column(Modifier.weight(1f)) {
                            Text(text = student.name, style = KineticType.bodyMedium, color = k.foreground)
                            Text(text = student.rollNumber, style = KineticType.label, color = k.mutedForeground)
                        }
                        MarkChip("P", status = "PRESENT", current = marks[student.id]) { vm.setMark(student.id, "PRESENT") }
                        Spacer(Modifier.width(KineticSpacing.xs))
                        MarkChip("A", status = "ABSENT", current = marks[student.id]) { vm.setMark(student.id, "ABSENT") }
                        Spacer(Modifier.width(KineticSpacing.xs))
                        MarkChip("L", status = "LATE", current = marks[student.id]) { vm.setMark(student.id, "LATE") }
                        Spacer(Modifier.width(KineticSpacing.xs))
                        MarkChip("E", status = "EXCUSED", current = marks[student.id]) { vm.setMark(student.id, "EXCUSED") }
                    }
                    KineticDivider()
                }
            }

            Spacer(Modifier.height(KineticSpacing.md))
            val present = marks.values.count { it == "PRESENT" }
            val absent = marks.values.count { it == "ABSENT" }
            val excused = marks.values.count { it == "EXCUSED" }
            Row(verticalAlignment = Alignment.CenterVertically) {
                KineticStat(value = present.toString(), label = "PRESENT")
                Spacer(Modifier.width(KineticSpacing.xl))
                KineticStat(value = absent.toString(), label = "ABSENT")
                if (excused > 0) {
                    Spacer(Modifier.width(KineticSpacing.xl))
                    KineticStat(value = excused.toString(), label = "EXCUSED")
                }
                Spacer(Modifier.weight(1f))
                KineticButton(
                    text = "SAVE",
                    enabled = marks.isNotEmpty(),
                    onClick = { vm.saveAttendance(onDone) },
                )
            }
            Spacer(Modifier.height(KineticSpacing.lg))
        }
    }
}

@Composable
private fun MarkChip(letter: String, status: String, current: String?, onClick: () -> Unit) {
    val k = LocalKineticColors.current
    val selected = current == status
    Box(
        modifier = Modifier
            .border(if (selected) KineticBorder.heavy else KineticBorder.hair, if (selected) k.accent else k.border)
            .background(if (selected) k.accent else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(
            text = letter,
            style = KineticType.labelBold,
            color = if (selected) k.accentForeground else k.mutedForeground,
        )
    }
}
