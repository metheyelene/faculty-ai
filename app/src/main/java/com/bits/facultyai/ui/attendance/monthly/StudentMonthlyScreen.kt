package com.bits.facultyai.ui.attendance.monthly

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import com.bits.facultyai.data.local.FacultyDatabase
import com.bits.facultyai.data.local.StudentEntity
import com.bits.facultyai.data.local.StudentMonthEntry
import com.bits.facultyai.domain.MonthlyAttendance
import com.bits.facultyai.domain.TimeUtils
import com.bits.facultyai.ui.components.GlassStat
import com.bits.facultyai.ui.components.KineticDivider
import com.bits.facultyai.ui.components.KineticEmptyState
import com.bits.facultyai.ui.components.KineticSectionHeader
import com.bits.facultyai.ui.navigation.KineticBottomNavigation
import com.bits.facultyai.ui.theme.KineticBorder
import com.bits.facultyai.ui.theme.KineticSpacing
import com.bits.facultyai.ui.theme.KineticType
import com.bits.facultyai.ui.theme.LocalKineticColors
import java.time.LocalDate
import java.time.YearMonth

/** Day-by-day rows for one student's month: session date + status. */
class StudentMonthlyViewModel(
    dao: com.bits.facultyai.data.local.FacultyDao,
    studentId: Long,
    monthIso: String,
) : ViewModel() {

    data class Ui(
        val student: StudentEntity? = null,
        val entries: List<StudentMonthEntry> = emptyList(),
    ) {
        // Counting lives ONLY in MonthlyAttendance — this delegates so a rule
        // change can never split the two views.
        private val row = MonthlyAttendance.StudentMonthly(
            student = StudentEntity(
                id = student?.id ?: 0, rollNumber = student?.rollNumber ?: "",
                name = student?.name ?: "", section = student?.section ?: "", year = student?.year ?: 0,
            ),
            workingDays = entries.count { MonthlyAttendance.countsTowardWorkingDays(it.status) },
            present = entries.count { it.status == MonthlyAttendance.PRESENT },
            late = entries.count { it.status == MonthlyAttendance.LATE },
            absent = entries.count { it.status == MonthlyAttendance.ABSENT },
            excused = entries.count { it.status == MonthlyAttendance.EXCUSED },
            notMarked = 0,
        )
        val workingDays get() = row.workingDays
        val percentage get() = row.percentage
        val present get() = row.present
        val late get() = row.late
        val excused get() = row.excused
        val absent get() = row.absent
        val shortage get() = row.shortage
    }

    val ui = combine(
        dao.observeStudent(studentId),
        dao.observeStudentEntriesBetween(studentId, "$monthIso-01", lastDayIso(monthIso)),
    ) { student, entries -> Ui(student, entries) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), Ui())

    private fun lastDayIso(monthIso: String): String {
        val parts = monthIso.split("-")
        return YearMonth.of(parts[0].toInt(), parts[1].toInt()).atEndOfMonth().toString()
    }
}

/**
 * One student's month: identity header, totals band, then a day-by-day
 * calendar/list of every session mark.
 */
@Composable
fun StudentMonthlyScreen(
    studentId: Long,
    monthIso: String,
    vm: StudentMonthlyViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY])
                StudentMonthlyViewModel(
                    FacultyDatabase.get(app).facultyDao(),
                    studentId,
                    monthIso,
                )
            }
        },
    ),
) {
    val k = LocalKineticColors.current
    val ui by vm.ui.collectAsStateWithLifecycle()
    val student = ui.student

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(k.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = KineticSpacing.lg),
    ) {
        Spacer(Modifier.height(KineticSpacing.xl))
        Text(text = "MONTHLY", style = KineticType.display, color = k.foreground)
        Text(text = "DETAIL", style = KineticType.display, color = k.accent)
        Spacer(Modifier.height(KineticSpacing.lg))

        if (student == null) {
            KineticEmptyState(title = "STUDENT NOT FOUND", message = "THIS STUDENT IS NO LONGER IN THE ROSTER")
            return@Column
        }

        // ---- identity ----
        KineticSectionHeader(title = student.name.uppercase())
        Text(
            text = "ROLL ${student.rollNumber} \u00B7 ${romanOf(student.year)} ${student.section} \u00B7 ${
                monthLabelOf(monthIso)
            }",
            style = KineticType.label,
            color = k.mutedForeground,
        )
        Spacer(Modifier.height(KineticSpacing.lg))

        // ---- totals ----
        Row(horizontalArrangement = Arrangement.spacedBy(KineticSpacing.sm)) {
            GlassStat(value = ui.workingDays.toString(), label = "WORKING DAYS", modifier = Modifier.weight(1f), emphasized = true)
            GlassStat(
                value = if (ui.workingDays == 0) "\u2014" else "${"%.0f".format(ui.percentage)}%",
                label = "ATTENDANCE",
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(KineticSpacing.sm))
        Row(horizontalArrangement = Arrangement.spacedBy(KineticSpacing.sm)) {
            GlassStat(value = ui.present.toString(), label = "PRESENT", modifier = Modifier.weight(1f))
            GlassStat(value = ui.late.toString(), label = "LATE", modifier = Modifier.weight(1f))
            GlassStat(value = ui.excused.toString(), label = "EXCUSED", modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(KineticSpacing.sm))
        Row(horizontalArrangement = Arrangement.spacedBy(KineticSpacing.sm)) {
            GlassStat(value = ui.absent.toString(), label = "ABSENT", modifier = Modifier.weight(1f))
            Box(modifier = Modifier.weight(2f))
        }
        Spacer(Modifier.height(KineticSpacing.lg))

        // ---- day-by-day calendar ----
        KineticSectionHeader(title = "DAY BY DAY")
        if (ui.entries.isEmpty()) {
            KineticEmptyState(
                title = "NO SESSIONS THIS MONTH",
                message = "NO ATTENDANCE WAS MARKED FOR THIS STUDENT",
            )
        } else {
            ui.entries
                .groupBy { it.recordDate }
                .toSortedMap()
                .forEach { (dateIso, entriesOfDay) ->
                    val date = LocalDate.parse(dateIso)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = KineticSpacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = "${date.dayOfMonth} ${date.month.name.take(3)}".uppercase(),
                                style = KineticType.bodyMedium,
                                color = k.foreground,
                            )
                            Text(
                                text = TimeUtils.dayName(date.dayOfWeek.value).uppercase() +
                                    (entriesOfDay.firstOrNull()?.recordSubject?.takeIf { it != "IMPORTED" }
                                        ?.let { " \u00B7 ${it.uppercase()}" } ?: ""),
                                style = KineticType.label.copy(fontSize = 9.sp),
                                color = k.mutedForeground,
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            entriesOfDay.forEach { entry ->
                                StatusBadge(status = entry.status)
                            }
                        }
                    }
                    KineticDivider()
                }
        }
        Spacer(Modifier.height(KineticBottomNavigation.bottomClearance()))
    }
}

@Composable
private fun StatusBadge(status: String) {
    val k = LocalKineticColors.current
    val (label, color, filled) = when (status) {
        MonthlyAttendance.PRESENT -> Triple("P", k.statusSuccess, true)
        MonthlyAttendance.LATE -> Triple("L", k.statusWarning, true)
        MonthlyAttendance.EXCUSED -> Triple("E", k.mutedForeground, false)
        else -> Triple("A", k.statusError, true)
    }
    Box(
        modifier = Modifier
            .size(26.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = if (filled) 0.18f else 0.06f))
            .border(KineticBorder.hair, color),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = KineticType.labelBold.copy(fontSize = 11.sp),
            color = if (filled) color else k.mutedForeground,
            textAlign = TextAlign.Center,
        )
    }
}

private fun monthLabelOf(monthIso: String): String {
    val parts = monthIso.split("-")
    val m = YearMonth.of(parts[0].toInt(), parts[1].toInt())
    return m.month.name.take(3).replaceFirstChar { it.uppercase() } + " " + m.year
}
