package com.bits.facultyai.domain

import com.bits.facultyai.data.local.AttendanceEntryEntity
import com.bits.facultyai.data.local.AttendanceRecordEntity
import com.bits.facultyai.data.local.StudentEntity
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

/**
 * Monthly attendance aggregation — pure Kotlin over the existing session/entry
 * tables. No new attendance source of truth: a month's summary is computed from
 * the same records the daily marking flow writes.
 */
object MonthlyAttendance {

    // Statuses: PRESENT | ABSENT | LATE | EXCUSED (EXCUSED added with monthly attendance)
    const val PRESENT = "PRESENT"
    const val ABSENT = "ABSENT"
    const val LATE = "LATE"
    const val EXCUSED = "EXCUSED"

    /** True when the student was physically in class — PRESENT or LATE (counted present, flagged separately). */
    fun isPresent(status: String): Boolean = status == PRESENT || status == LATE

    /** Counted in working days? PRESENT/ABSENT/LATE yes; EXCUSED excluded from the denominator. */
    fun countsTowardWorkingDays(status: String): Boolean = status != EXCUSED

    /** Percentage of sessions attended (PRESENT/LATE) out of sessions with a definitive mark. */
    fun percentage(present: Int, absent: Int): Double =
        if (present + absent <= 0) 0.0 else present * 100.0 / (present + absent)

    /** Month bounds as ISO dates, e.g. 2026-09-01..2026-09-30. */
    fun monthRange(year: Int, month: Int): Pair<String, String> {
        val m = YearMonth.of(year, month)
        return m.atDay(1).toString() to m.atEndOfMonth().toString()
    }

    data class StudentMonthly(
        val student: StudentEntity,
        /** Sessions where the student has any definitive mark (the month's working days for them). */
        val workingDays: Int,
        val present: Int,
        val late: Int,
        val absent: Int,
        val excused: Int,
        /** Days where the roster has the student but no session mark was recorded. */
        val notMarked: Int,
    ) {
        val percentage: Double get() = percentage(present + late, absent)
        /** True when below the standard 75% requirement (with any attendance at all). */
        val shortage: Boolean get() = workingDays > 0 && percentage < 75.0
    }

    data class ClassStats(
        val totalStudents: Int,
        val averageAttendance: Double,
        val highest: Double,
        val lowest: Double,
        val shortage: Int,
    )

    data class MonthlySummary(
        val students: List<StudentMonthly>,
        val classStats: ClassStats,
        /** ISO dates of the month that have at least one session for this class+subject. */
        val sessionDates: List<String>,
    )

    /**
     * Aggregates a month of sessions for one class.
     *
     * @param records sessions in [startIso, endIso] — the caller filters year/section/subject
     * @param roster students of the class (already filtered); drives row order and notMarked
     */
    fun aggregate(
        records: List<AttendanceRecordEntity>,
        entries: List<AttendanceEntryEntity>,
        roster: List<StudentEntity>,
        startIso: String,
        endIso: String,
    ): MonthlySummary {
        val byRecord = entries.groupBy { it.recordId }
        val recordIds = records.mapTo(HashSet()) { it.id }
        val sessionDates = records.mapTo(mutableListOf()) { it.date }.distinct().sorted()

        val rows = roster.map { student ->
            var present = 0; var late = 0; var absent = 0; var excused = 0
            var definitive = 0 // days counting toward the denominator (not EXCUSED)
            var markedAny = 0 // days with any mark — drives the not-marked hint
            for (record in records) {
                val status = byRecord[record.id]?.firstOrNull { it.studentId == student.id }?.status ?: continue
                markedAny++
                when (status) {
                    PRESENT -> { present++; definitive++ }
                    LATE -> { late++; definitive++ }
                    ABSENT -> { absent++; definitive++ }
                    EXCUSED -> excused++ // accounted, but outside the working-day denominator
                }
            }
            StudentMonthly(
                student = student,
                workingDays = definitive,
                present = present,
                late = late,
                absent = absent,
                excused = excused,
                notMarked = recordIds.size - markedAny,
            )
        }

        val withAttendance = rows.filter { it.workingDays > 0 }
        val pcts = withAttendance.map { it.percentage }
        val classStats = ClassStats(
            totalStudents = roster.size,
            averageAttendance = if (pcts.isEmpty()) 0.0 else pcts.average(),
            highest = pcts.maxOrNull() ?: 0.0,
            lowest = pcts.minOrNull() ?: 0.0,
            shortage = withAttendance.count { it.shortage },
        )
        return MonthlySummary(rows, classStats, sessionDates)
    }

    /** Strict ISO (yyyy-M-d, 1-2 digit parts); null unless it parses fully. Parser-owned date formats build on this. */
    fun parseIsoLoose(raw: String?): String? {
        val m = Regex("^(\\d{4})-(\\d{1,2})-(\\d{1,2})$").find(raw.orEmpty().trim()) ?: return null
        val (y, mo, d) = m.destructured
        val year = y.toInt(); val month = mo.toInt(); val day = d.toInt()
        if (month !in 1..12 || day !in 1..31) return null
        return try {
            LocalDate.of(year, month, day).toString()
        } catch (_: java.time.DateTimeException) {
            null
        }
    }
}
