package com.bits.facultyai.ui.attendance

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bits.facultyai.domain.TimeUtils
import com.bits.facultyai.ui.navigation.attendanceRoute
import com.bits.facultyai.ui.navigation.monthlyAttendanceRoute
import com.bits.facultyai.ui.components.GlassCard
import com.bits.facultyai.ui.components.GlassStat
import com.bits.facultyai.ui.components.GlassStrength
import com.bits.facultyai.ui.components.KineticBadge
import com.bits.facultyai.ui.components.KineticDivider
import com.bits.facultyai.ui.components.KineticEmptyState
import com.bits.facultyai.ui.theme.KineticBorder
import com.bits.facultyai.ui.components.KineticSectionHeader
import com.bits.facultyai.ui.navigation.KineticBottomNavigation
import com.bits.facultyai.ui.theme.KineticSpacing
import com.bits.facultyai.ui.theme.KineticType
import com.bits.facultyai.ui.theme.LocalKineticColors

@Composable
fun AttendanceScreen(
    onNavigate: (String) -> Unit,
    vm: AttendanceViewModel = viewModel(),
) {
    val k = LocalKineticColors.current
    val timetable by vm.timetable.collectAsStateWithLifecycle()
    val records by vm.attendanceRecords.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(k.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = KineticSpacing.lg),
    ) {
        Spacer(Modifier.height(KineticSpacing.xl))
        Text(text = "MY", style = KineticType.display, color = k.foreground)
        Text(text = "ATTENDANCE", style = KineticType.display, color = k.accent)
        Spacer(Modifier.height(KineticSpacing.lg))

        // Overview stat band — actual stored-record counts, on glass.
        Row(horizontalArrangement = Arrangement.spacedBy(KineticSpacing.sm)) {
            GlassStat(
                value = records.size.toString().padStart(2, '0'),
                label = "SESSIONS",
                modifier = Modifier.weight(1f),
                emphasized = records.isNotEmpty(),
            )
            GlassStat(
                value = records.sumOf { it.presentCount }.toString().padStart(2, '0'),
                label = "MARKS TAKEN",
                modifier = Modifier.weight(1f),
            )
            GlassStat(
                value = timetable.size.toString().padStart(2, '0'),
                label = "WEEKLY CLASSES",
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(KineticSpacing.xl))

        // ---- Monthly attendance ----
        GlassCard(strength = GlassStrength.THIN, onClick = { onNavigate(monthlyAttendanceRoute()) }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(text = "MONTHLY ATTENDANCE", style = KineticType.bodyMedium, color = k.foreground)
                    Text(
                        text = "CLASS SUMMARY \u00B7 EXCEL IMPORT",
                        style = KineticType.label,
                        color = k.mutedForeground,
                    )
                }
                Text(text = "\u2192", style = KineticType.headingSm, color = k.accent)
            }
        }
        Spacer(Modifier.height(KineticSpacing.lg))

        KineticSectionHeader(title = "TODAY'S CLASSES")
        val today = java.time.LocalDate.now().dayOfWeek.value
        val todaySlots = timetable.filter { it.dayOfWeek == today }.sortedBy { it.startTimeMinutes }
        if (todaySlots.isEmpty()) {
            KineticEmptyState(
                title = "NO CLASSES TODAY",
                message = "YOUR TIMETABLE IS CLEAR",
            )
        } else {
            todaySlots.forEach { slot ->
                GlassCard(strength = GlassStrength.THIN, onClick = { onNavigate(attendanceRoute(slot.id)) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = TimeUtils.formatTime(slot.startTimeMinutes),
                            style = KineticType.headingSm,
                            color = k.accent,
                        )
                        Spacer(Modifier.width(KineticSpacing.md))
                        Column(Modifier.weight(1f)) {
                            Text(text = slot.subject.uppercase(), style = KineticType.bodyMedium, color = k.foreground)
                            Text(text = slot.section + " · ROOM " + slot.room, style = KineticType.label, color = k.mutedForeground)
                        }
                        Text(text = "MARK \u2192", style = KineticType.labelBold, color = k.accent)
                    }
                }
                Spacer(Modifier.height(KineticSpacing.sm))
            }
        }

        KineticSectionHeader(title = "RECENT SESSIONS")
        if (records.isEmpty()) {
            Text(
                text = "NO SESSIONS RECORDED YET",
                style = KineticType.label,
                color = k.mutedForeground,
            )
        } else {
            records.take(5).forEach { record ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = KineticSpacing.sm),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(text = record.subject.uppercase(), style = KineticType.bodyMedium, color = k.foreground)
                        Text(
                            text = record.section + " · " + record.date,
                            style = KineticType.label,
                            color = k.mutedForeground,
                        )
                    }
                    KineticBadge(text = record.presentCount.toString() + " PRESENT", filled = true)
                }
                KineticDivider()
            }
        }
        // Clears the floating glass dock + system navigation area.
        Spacer(Modifier.height(KineticBottomNavigation.bottomClearance()))
    }
}
