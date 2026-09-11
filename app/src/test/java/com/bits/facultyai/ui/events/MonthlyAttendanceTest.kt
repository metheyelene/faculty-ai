package com.bits.facultyai.ui.events

import com.bits.facultyai.data.local.AttendanceEntryEntity
import com.bits.facultyai.data.local.AttendanceRecordEntity
import com.bits.facultyai.data.local.StudentEntity
import com.bits.facultyai.domain.AttendanceExcelParser
import com.bits.facultyai.domain.AttendanceExcelWriter
import com.bits.facultyai.domain.MonthlyAttendance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream

/**
 * Monthly attendance: aggregation math, status/date parsing, Excel layout
 * detection + manual mapping, roster matching, and the summary writer.
 */
class MonthlyAttendanceTest {

    // ---- fixtures ----

    private fun student(id: Long, roll: String, name: String = "Student $id") = StudentEntity(
        id = id, rollNumber = roll, name = name, section = "A", year = 3,
    )

    private fun record(id: Long, date: String, subject: String = "DSP") = AttendanceRecordEntity(
        id = id, classSlotId = 1, subject = subject, section = "A", date = date,
        year = 3, markedAt = 0, presentCount = 0, absentCount = 0, lateCount = 0,
    )

    private fun entry(rid: Long, sid: Long, status: String) =
        AttendanceEntryEntity(recordId = rid, studentId = sid, status = status)

    private val parse = { raw: String? -> AttendanceExcelParser.Status.parse(raw) }

    // ---- aggregation math ----

    @Test
    fun `percentage is present over definitive days`() {
        assertEquals(75.0, MonthlyAttendance.percentage(3, 1), 1e-9)
        assertEquals(0.0, MonthlyAttendance.percentage(0, 0), 1e-9)
        assertEquals(100.0, MonthlyAttendance.percentage(10, 0), 1e-9)
    }

    @Test
    fun `late counts present, excused leaves the denominator`() {
        assertTrue(MonthlyAttendance.isPresent(MonthlyAttendance.LATE))
        assertTrue(MonthlyAttendance.countsTowardWorkingDays(MonthlyAttendance.LATE))
        assertTrue(!MonthlyAttendance.countsTowardWorkingDays(MonthlyAttendance.EXCUSED))
    }

    @Test
    fun `aggregate sums per-student totals and class stats`() {
        val roster = listOf(student(1, "21"), student(2, "22"), student(3, "23"))
        val dates = listOf("2026-09-01", "2026-09-02", "2026-09-03", "2026-09-04")
        val records = dates.mapIndexed { i, d -> record((i + 1).toLong(), d) }
        val entries = buildList {
            // s1: P P P P -> 100%
            dates.forEachIndexed { i, _ -> add(entry((i + 1).toLong(), 1, MonthlyAttendance.PRESENT)) }
            // s2: A P L E -> 2/3 definitive = 66.7%, 1 excused
            add(entry(1, 2, MonthlyAttendance.ABSENT))
            add(entry(2, 2, MonthlyAttendance.PRESENT))
            add(entry(3, 2, MonthlyAttendance.LATE))
            add(entry(4, 2, MonthlyAttendance.EXCUSED))
            // s3: no marks at all -> workingDays 0
        }
        val summary = MonthlyAttendance.aggregate(records, entries, roster, "2026-09-01", "2026-09-30")

        val s1 = summary.students[0]
        assertEquals(4, s1.workingDays)
        assertEquals(4, s1.present)
        assertEquals(100.0, s1.percentage, 1e-9)

        val s2 = summary.students[1]
        assertEquals(3, s2.workingDays) // excused day excluded from the denominator
        assertEquals(1, s2.excused)
        assertEquals(2, s2.present + s2.late)
        assertEquals(1, s2.absent)
        assertEquals(66.66, s2.percentage, 0.01)
        assertEquals(0, s2.notMarked)

        val s3 = summary.students[2]
        assertEquals(0, s3.workingDays)
        assertEquals(4, s3.notMarked)
        assertEquals(0.0, s3.percentage, 1e-9)

        assertEquals(3, summary.classStats.totalStudents)
        assertEquals(1, summary.classStats.shortage) // only s2 <75%; s3 has no attendance so isn't counted
        assertEquals(100.0, summary.classStats.highest, 1e-9)
        assertEquals(66.66, summary.classStats.lowest, 0.01)
        assertEquals(83.33, summary.classStats.averageAttendance, 0.01) // (100 + 66.66)/2 — s3 has no attendance
        assertEquals(dates, summary.sessionDates)
    }

    @Test
    fun `aggregate with only excused marks yields zero working days without absence`() {
        val roster = listOf(student(1, "21"))
        val records = listOf(record(1, "2026-09-01"), record(2, "2026-09-02"))
        val entries = listOf(
            entry(1, 1, MonthlyAttendance.EXCUSED),
            entry(2, 1, MonthlyAttendance.EXCUSED),
        )
        val summary = MonthlyAttendance.aggregate(records, entries, roster, "2026-09-01", "2026-09-30")
        val s = summary.students[0]
        assertEquals(0, s.workingDays)
        assertEquals(2, s.excused)
        assertEquals(0.0, s.percentage, 1e-9)
        assertEquals(0, summary.classStats.shortage) // no attendance data -> not a shortage
    }

    @Test
    fun `monthRange covers the whole month`() {
        val (start, end) = MonthlyAttendance.monthRange(2026, 9)
        assertEquals("2026-09-01", start)
        assertEquals("2026-09-30", end)
        val (fs, fe) = MonthlyAttendance.monthRange(2024, 2) // leap year
        assertEquals("2024-02-01", fs)
        assertEquals("2024-02-29", fe)
    }

    // ---- status + date parsing ----

    @Test
    fun `status parsing accepts all documented variants`() {
        assertEquals(MonthlyAttendance.PRESENT, parse("P"))
        assertEquals(MonthlyAttendance.PRESENT, parse("p"))
        assertEquals(MonthlyAttendance.PRESENT, parse("Present"))
        assertEquals(MonthlyAttendance.ABSENT, parse("A"))
        assertEquals(MonthlyAttendance.ABSENT, parse("0"))
        assertEquals(MonthlyAttendance.LATE, parse("L"))
        assertEquals(MonthlyAttendance.EXCUSED, parse("E"))
        assertEquals(MonthlyAttendance.EXCUSED, parse("Excused"))
        assertNull(parse("X"))
        assertNull(parse(""))
        assertNull(parse(null))
        assertNull(parse(" "))
    }

    @Test
    fun `flexible date parsing handles the common formats`() {
        assertEquals("2026-09-05", AttendanceExcelParser.parseDateFlexible("2026-09-05"))
        assertEquals("2026-09-05", AttendanceExcelParser.parseDateFlexible("2026-9-5"))
        assertEquals("2026-09-05", AttendanceExcelParser.parseDateFlexible("05/09/2026"))
        assertEquals("2026-09-05", AttendanceExcelParser.parseDateFlexible("5-9-2026"))
        assertEquals("2026-09-05", AttendanceExcelParser.parseDateFlexible("5 Sep 2026"))
        assertEquals("2026-09-05", AttendanceExcelParser.parseDateFlexible("05 September 2026"))
        assertNull(AttendanceExcelParser.parseDateFlexible("09/2026"))
        assertNull(AttendanceExcelParser.parseDateFlexible("not a date"))
        assertNull(AttendanceExcelParser.parseDateFlexible("2026-13-40"))
    }

    // ---- wide format ----

    @Test
    fun `wide format is detected and parsed with roster matching`() {
        val roster = listOf(student(1, "21", "Ananya"), student(2, "22", "Bharat"))
        val table = listOf(
            listOf("Roll No", "Student Name", "01", "02", "03", "04"),
            listOf("21", "Ananya", "P", "P", "A", "P"),
            listOf("22", "Bharat", "P", "L", "E", "P"),
            listOf("99", "Ghost", "P", "P", "P", "P"), // unmatched roll
        )
        val preview = AttendanceExcelParser.parse(table, roster, 2026, 9)

        assertEquals(AttendanceExcelParser.Format.WIDE, preview.format)
        assertEquals(8, preview.marks.size) // 2 matched students x 4 days; the unmatched row's marks are dropped
        assertTrue(preview.unmatched.any { it.rollNumber == "99" })
        assertEquals(4, preview.detectedDates.size)

        val ananya = preview.marks.filter { it.studentId == 1L }
        assertEquals(4, ananya.size)
        assertEquals("2026-09-01", ananya[0].dateIso)
        assertEquals(MonthlyAttendance.ABSENT, ananya[2].status)

        val bharat = preview.marks.filter { it.studentId == 2L }
        assertEquals(4, bharat.size)
        assertEquals(MonthlyAttendance.LATE, bharat[1].status)
        assertEquals(MonthlyAttendance.EXCUSED, bharat[2].status)
    }

    @Test
    fun `wide format ignores blank cells and off-month headers`() {
        val roster = listOf(student(1, "21"))
        val table = listOf(
            listOf("Roll No", "Name", "30/08/2026", "01", "02"),
            listOf("21", "Ananya", "P", null, "A"),
        )
        val preview = AttendanceExcelParser.parse(table, roster, 2026, 9)
        assertEquals(1, preview.marks.size)
        assertEquals("2026-09-02", preview.marks[0].dateIso)
    }

    @Test
    fun `wide format flags rows with no readable marks`() {
        val roster = listOf(student(1, "21"))
        val table = listOf(
            listOf("Roll No", "Name", "01", "02"),
            listOf("21", "Ananya", "", "?"),
        )
        val preview = AttendanceExcelParser.parse(table, roster, 2026, 9)
        assertEquals(0, preview.marks.size)
        assertEquals(1, preview.invalid.size)
    }

    // ---- long format ----

    @Test
    fun `long format is detected and parsed per-row`() {
        val roster = listOf(student(1, "21", "Ananya"), student(2, "22", "Bharat"))
        val table = listOf(
            listOf("Student ID", "Name", "Date", "Status"),
            listOf("21", "Ananya", "2026-09-01", "P"),
            listOf("22", "Bharat", "01/09/2026", "A"),
            listOf("22", "Bharat", "2026-09-02", "L"),
            listOf("21", "Ananya", "2026-10-01", "P"), // outside month -> invalid
            listOf("77", "Ghost", "2026-09-03", "P"), // unmatched
        )
        val preview = AttendanceExcelParser.parse(table, roster, 2026, 9)
        assertEquals(AttendanceExcelParser.Format.LONG, preview.format)
        assertEquals(3, preview.marks.size)
        assertEquals(1, preview.invalid.size)
        assertEquals(1, preview.unmatched.size)
        assertTrue(preview.invalid[0].reason.contains("outside the selected month"))
    }

    @Test
    fun `duplicate student-date rows inside the file are flagged`() {
        val roster = listOf(student(1, "21"))
        val table = listOf(
            listOf("Student ID", "Date", "Status"),
            listOf("21", "2026-09-01", "P"),
            listOf("21", "2026-09-01", "A"),
        )
        val preview = AttendanceExcelParser.parse(table, roster, 2026, 9)
        assertEquals(1, preview.marks.size)
        assertEquals(1, preview.fileDuplicates.size)
        assertEquals(3, preview.fileDuplicates[0].rowNumber)
    }

    // ---- detection + manual mapping ----

    @Test
    fun `detection returns the same shape the manual mapping builds`() {
        val headers = listOf("Roll No", "Name", "01", "02", "03")
        val table = listOf(headers, listOf("21", "Ananya", "P", "A", "P"))
        val mapping = AttendanceExcelParser.detectMapping(table, 2026, 9)

        val auto = AttendanceExcelParser.parse(table, listOf(student(1, "21")), 2026, 9)
        val manual = AttendanceExcelParser.parse(
            table,
            listOf(student(1, "21")),
            AttendanceExcelParser.Mapping(
                format = AttendanceExcelParser.Format.WIDE,
                rollColumn = 0,
                nameColumn = 1,
                dayColumns = listOf(2 to 1, 3 to 2, 4 to 3),
            ),
            2026,
            9,
        )
        assertEquals(auto.marks, manual.marks)
        assertTrue(mapping is AttendanceExcelParser.Mapping)
    }

    @Test
    fun `day headers accept numbers, D prefixes and real dates`() {
        assertEquals(1, AttendanceExcelParser.dayFromHeader("01", 9))
        assertEquals(5, AttendanceExcelParser.dayFromHeader("D5", 9))
        assertEquals(21, AttendanceExcelParser.dayFromHeader("21 Sep", 9))
        assertEquals(5, AttendanceExcelParser.dayFromHeader("05/09/2026", 9))
        assertNull(AttendanceExcelParser.dayFromHeader("Roll No", 9))
        assertNull(AttendanceExcelParser.dayFromHeader("Name", 9))
        assertNull(AttendanceExcelParser.dayFromHeader("32", 9))
        assertEquals(1, AttendanceExcelParser.dayFromHeader("01/10/2026", 10)) // October month selected
    }

    // ---- writer round trip (JVM-safe: plain zip inspection) ----

    @Test
    fun `writer produces a zip whose sheet contains the summary content`() {
        val roster = listOf(student(1, "21", "Ananya"), student(2, "22", "Bharat"))
        val records = listOf(record(1, "2026-09-01"), record(2, "2026-09-02"))
        val entries = listOf(
            entry(1, 1, MonthlyAttendance.PRESENT),
            entry(2, 1, MonthlyAttendance.ABSENT),
            entry(1, 2, MonthlyAttendance.LATE),
            entry(2, 2, MonthlyAttendance.PRESENT),
        )
        val summary = MonthlyAttendance.aggregate(records, entries, roster, "2026-09-01", "2026-09-30")

        val out = ByteArrayOutputStream()
        AttendanceExcelWriter.writeSummary(out, "III A", "DIGITAL SIGNAL PROCESSING", java.time.YearMonth.of(2026, 9), summary)

        var sheetText = ""
        ZipInputStream(ByteArrayInputStream(out.toByteArray())).use { zip ->
            while (true) {
                val e = zip.nextEntry ?: break
                if (e.name == "xl/worksheets/sheet1.xml") {
                    sheetText = zip.readBytes().toString(Charsets.UTF_8)
                }
                zip.closeEntry()
            }
        }

        // The row math embedded in the sheet matches the aggregation source of truth.
        assertTrue(sheetText.contains("Roll No"))
        assertTrue(sheetText.contains("Ananya"))
        assertTrue(sheetText.contains("100%"))
        assertTrue(sheetText.contains("50%"))
        assertTrue(sheetText.contains("DIGITAL SIGNAL PROCESSING"))
    }
}
