package com.bits.facultyai.ui.attendance.monthly

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bits.facultyai.data.local.AttendanceEntryEntity
import com.bits.facultyai.data.local.FacultyDao
import com.bits.facultyai.data.local.StudentEntity
import com.bits.facultyai.domain.AttendanceExcelWriter
import com.bits.facultyai.domain.MonthlyAttendance
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import java.io.OutputStream
import java.time.YearMonth

/** Selections for the MONTHLY ATTENDANCE view. Subject "ALL" shows every subject of the class. */
data class MonthlySelection(
    val year: Int = 0, // academic year 1..4; 0 = none picked yet
    val section: String = "", // e.g. "A"
    val subject: String = "ALL",
    val month: YearMonth = YearMonth.now(),
)

/**
 * Monthly attendance: selectors + live per-student summary computed from the
 * same attendance records the daily marking flow writes. No duplicate source
 * of truth — everything is a query over `attendance_record`/`attendance_entry`.
 */
class MonthlyAttendanceViewModel(
    private val dao: FacultyDao,
) : ViewModel() {

    val selection = MutableStateFlow(MonthlySelection())

    val students: StateFlow<List<StudentEntity>> = dao.observeStudents()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Sections that actually exist in the roster for the selected academic year. */
    val availableSections: StateFlow<List<String>> = selection
        .flatMapLatest { sel ->
            if (sel.year == 0) flowOf(emptyList()) else dao.observeDistinctSectionsFor(sel.year)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Subjects with recorded sessions for the class — the truthful filter source. */
    val availableSubjects: StateFlow<List<String>> = selection
        .flatMapLatest { sel ->
            if (sel.year == 0 || sel.section.isEmpty()) flowOf(emptyList())
            else dao.observeRecordedSubjects(sel.year, sel.section)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val summary: StateFlow<MonthlySummaryUi?> =
        selection.flatMapLatest { sel ->
            if (sel.year == 0 || sel.section.isEmpty()) {
                flowOf(null)
            } else {
                val (startIso, endIso) = MonthlyAttendance.monthRange(sel.month.year, sel.month.monthValue)
                combine(
                    dao.observeRecordsBetween(startIso, endIso),
                    dao.observeStudentsFor(sel.year, sel.section),
                ) { records, roster ->
                    val inClass = records.filter { it.year == sel.year && it.section == sel.section }
                    val subjectRecords = if (sel.subject == "ALL") inClass else inClass.filter { it.subject == sel.subject }
                    val entryIds = subjectRecords.mapTo(HashSet()) { it.id }
                    val entryRows = dao.getStudentMonthEntriesBetween(startIso, endIso)
                        .filter { it.recordId in entryIds }
                        .map { AttendanceEntryEntity(it.id, it.recordId, it.studentId, it.status) }
                    val agg = MonthlyAttendance.aggregate(subjectRecords, entryRows, roster, startIso, endIso)
                    MonthlySummaryUi(
                        year = sel.year,
                        section = sel.section,
                        subject = sel.subject,
                        month = sel.month,
                        students = agg.students,
                        classStats = agg.classStats,
                        sessionDates = agg.sessionDates,
                    )
                }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // ---- selectors ----

    fun setYear(year: Int) {
        selection.value = selection.value.copy(year = year, section = "", subject = "ALL")
    }

    fun setSection(section: String) {
        selection.value = selection.value.copy(section = section, subject = "ALL")
    }

    fun setSubject(subject: String) {
        selection.value = selection.value.copy(subject = subject)
    }

    fun setMonth(month: YearMonth) {
        selection.value = selection.value.copy(month = month)
    }

    /** Encoded class selection handed to the import screen: "year|section|subject|yyyy-MM". */
    fun importPayload(): String {
        val sel = selection.value
        return "${sel.year}|${sel.section}|${sel.subject}|${sel.month.year}-%02d".format(sel.month.monthValue)
    }

    /**
     * Writes the current summary as a shareable .xlsx via [out] (a SAF stream).
     * Returns false when nothing is loaded yet.
     */
    fun exportCurrentSummary(out: OutputStream): Boolean {
        val s = summary.value ?: return false
        AttendanceExcelWriter.writeSummary(
            out = out,
            classLabel = s.classLabel,
            subjectLabel = s.subjectLabel,
            month = s.month,
            summary = MonthlyAttendance.MonthlySummary(s.students, s.classStats, s.sessionDates),
        )
        return true
    }
}

/** UI-facing monthly summary. */
data class MonthlySummaryUi(
    val year: Int,
    val section: String,
    val subject: String,
    val month: YearMonth,
    val students: List<MonthlyAttendance.StudentMonthly>,
    val classStats: MonthlyAttendance.ClassStats,
    val sessionDates: List<String>,
) {
    val classLabel: String get() = "${romanOf(year)} $section"
    val monthLabel: String get() = month.month.name.take(3).replaceFirstChar { it.uppercase() } + " " + month.year
    val subjectLabel: String get() = if (subject == "ALL") "ALL SUBJECTS" else subject.uppercase()
}

fun romanOf(year: Int): String = when (year) {
    1 -> "I"; 2 -> "II"; 3 -> "III"; 4 -> "IV"; else -> ""
}
