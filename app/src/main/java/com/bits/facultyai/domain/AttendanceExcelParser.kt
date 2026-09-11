package com.bits.facultyai.domain

import com.bits.facultyai.data.local.StudentEntity
import java.time.LocalDate
import java.time.YearMonth

/**
 * Parses monthly-attendance .xlsx sheets (via the shared [XlsxReader]) into
 * per-student day cells. Two layouts are recognized:
 *
 *  - WIDE: `Roll No | Student Name | 01 | 02 | ... | 30` (day-of-month columns,
 *    also accepting real dates or "D1"/"1 SEP" style headers within the month)
 *  - LONG: `Student ID | Name | Date | Status` (one row per student-day)
 *
 * Automatic detection resolves to the same [Mapping] the manual-mapping UI
 * builds — detection failure is recoverable by hand, never a dead end.
 * Pure logic — no Android dependencies beyond [XlsxReader]. Nothing is written
 * to the database here; the caller shows the preview and commits explicitly.
 */
object AttendanceExcelParser {

    enum class Format { WIDE, LONG }

    /** Status values accepted in sheets, and their canonical names. */
    object Status {
        const val PRESENT = "PRESENT"
        const val ABSENT = "ABSENT"
        const val LATE = "LATE"
        const val EXCUSED = "EXCUSED"

        /** P = Present, A = Absent, L = Late, E = Excused; word and numeric variants accepted. */
        fun parse(raw: String?): String? = when (raw?.trim()?.uppercase()) {
            "P", "PRESENT", "1", "1.0" -> PRESENT
            "A", "ABSENT", "0", "0.0" -> ABSENT
            "L", "LATE", "2", "2.0" -> LATE
            "E", "EXCUSED", "LEAVE", "3", "3.0" -> EXCUSED
            else -> null
        }
    }

    /** Manual column mapping — what the automatic detection also produces. */
    data class Mapping(
        val format: Format,
        val rollColumn: Int,
        val nameColumn: Int?,
        /** WIDE only: sheet column index -> day of month. */
        val dayColumns: List<Pair<Int, Int>> = emptyList(),
        /** LONG only. */
        val dateColumn: Int? = null,
        val statusColumn: Int? = null,
    )

    /** A resolved attendance cell tied to an existing roster student. */
    data class ParsedMark(
        val rowNumber: Int,
        val studentId: Long,
        val rollNumber: String,
        val studentName: String,
        val dateIso: String,
        val dayOfMonth: Int,
        val status: String,
    )

    /** A row that references a roll number not present in the selected class roster. */
    data class UnmatchedRow(val rowNumber: Int, val rollNumber: String, val name: String, val raw: String)

    data class InvalidRow(val rowNumber: Int, val reason: String, val raw: String)

    /** Same student+date appearing more than once inside the file itself. */
    data class FileDuplicate(val rowNumber: Int, val rollNumber: String, val dateIso: String, val firstRowNumber: Int)

    /** Import marks that collide with attendance already stored for that student+date+subject. */
    data class Conflict(
        val mark: ParsedMark,
        val existingStatus: String,
        val existingRecordId: Long,
    )

    data class Preview(
        val format: Format?,
        val detectedDates: List<String>,
        val marks: List<ParsedMark>,
        val unmatched: List<UnmatchedRow>,
        val invalid: List<InvalidRow>,
        val fileDuplicates: List<FileDuplicate>,
    )

    private fun normalize(s: String?) = s.orEmpty().lowercase().filter { it.isLetterOrDigit() }

    private val NAME_KEYS = listOf("studentname", "name", "fullname", "student")
    private val ROLL_KEYS = listOf("rollnumber", "rollno", "roll", "rollnum", "studentid", "id")

    private fun headerIndex(headers: List<String?>, keys: List<String>): Int {
        val normalized = headers.map { normalize(it) }
        keys.forEach { key ->
            normalized.indexOfFirst { it == key }.takeIf { it >= 0 }?.let { return it }
        }
        keys.forEach { key ->
            normalized.indexOfFirst { it.contains(key) }.takeIf { it >= 0 }?.let { return it }
        }
        return -1
    }

    /** A header that identifies a day of [month]: "01", "1", "D5", "5 SEP", "2026-09-05", "05/09/2026". */
    fun dayFromHeader(raw: String?, month: Int): Int? {
        val s = raw.orEmpty().trim()
        if (s.isEmpty()) return null
        parseDateFlexible(s)?.let { iso ->
            val d = iso.substring(5, 7).toInt() to iso.substring(8, 10).toInt()
            return if (d.first == month) d.second else null
        }
        val cleaned = s.removePrefix("D").removePrefix("d").replace(Regex("[^0-9]"), "")
        val n = cleaned.toIntOrNull() ?: return null
        return if (n in 1..YearMonth.of(2024, month).lengthOfMonth()) n else null
    }

    /** Roll-number key for roster matching: digits+letters, case-insensitive, no separators. */
    private fun rollKey(raw: String): String = raw.trim().lowercase().filter { it.isLetterOrDigit() }

    /** Lookup over one class roster: roll first, then registration number. */
    private class RosterMatch(roster: List<StudentEntity>) {
        val byRoll = roster.associateBy { rollKey(it.rollNumber) }
        val byReg = roster.filter { it.registrationNumber.isNotBlank() }.associateBy { rollKey(it.registrationNumber) }

        fun find(rawRoll: String): StudentEntity? =
            byRoll[rollKey(rawRoll)] ?: byReg[rollKey(rawRoll)]
    }

    /**
     * Automatic parse: detect the layout, then run the single parsing path.
     * @throws XlsxReader.ImportException with a human-readable reason for unusable sheets.
     */
    fun parse(
        table: List<List<String?>>,
        roster: List<StudentEntity>,
        year: Int,
        month: Int,
    ): Preview {
        val mapping = detectMapping(table, year, month)
            ?: throw XlsxReader.ImportException("Couldn't detect the sheet's columns.")
        return parse(table, roster, mapping, year, month)
    }

    /**
     * Manual-mapping entry point — same machinery as [parse]; the mapping UI
     * calls this with the faculty's choices.
     */
    fun parse(
        table: List<List<String?>>,
        roster: List<StudentEntity>,
        mapping: Mapping,
        year: Int,
        month: Int,
    ): Preview {
        if (table.isEmpty()) throw XlsxReader.ImportException("The sheet is empty.")
        val headers = table.first()
        if (headers.all { it.isNullOrBlank() }) throw XlsxReader.ImportException("The first row doesn't contain column headers.")
        return when (mapping.format) {
            Format.WIDE -> parseWide(table, mapping, year, month, RosterMatch(roster))
            Format.LONG -> parseLong(table, mapping, month, RosterMatch(roster))
        }
    }

    /**
     * Layout detection for the mapping UI. Returns null when nothing usable is
     * found — the caller then opens manual mapping instead of failing.
     */
    fun detectMapping(table: List<List<String?>>, year: Int, month: Int): Mapping? {
        if (table.isEmpty()) return null
        val headers = table.first()
        val dayCols = headers.mapIndexedNotNull { i, h -> dayFromHeader(h, month)?.let { i to it } }
        if (dayCols.size >= 2) {
            val iRoll = headerIndex(headers, ROLL_KEYS).takeIf { it >= 0 } ?: 0
            val dayIdxs = dayCols.map { it.first }.toHashSet()
            val iName = headerIndex(headers, NAME_KEYS).takeIf { it >= 0 && it !in dayIdxs && it != iRoll }
            return Mapping(Format.WIDE, iRoll, iName, dayCols)
        }
        // LONG: find one date column and one status column among the headers/first rows.
        val firstDataRow = table.getOrNull(1).orEmpty()
        var iDate = headers.indexOfFirst { parseDateFlexible(it) != null }
        var iStatus = -1
        if (iDate >= 0) {
            iStatus = headers.indices.firstOrNull { it != iDate && (firstDataRow.getOrNull(it)?.let { c -> Status.parse(c) } ?: headerStatusFrom(headers[it])) != null } ?: -1
        }
        if (iDate < 0 || iStatus < 0) {
            // Fall back to scanning the first data row cell-wise.
            iDate = firstDataRow.indexOfFirst { parseDateFlexible(it) != null }
            iStatus = firstDataRow.indexOfFirst { Status.parse(it) != null && firstDataRow.indexOf(it) != iDate }
        }
        if (iDate < 0 || iStatus < 0) return null
        val iRoll = headerIndex(headers, ROLL_KEYS).takeIf { it >= 0 } ?: 0
        val iName = headerIndex(headers, NAME_KEYS).takeIf { it >= 0 && it != iRoll }
        return Mapping(Format.LONG, iRoll, iName, dateColumn = iDate, statusColumn = iStatus)
    }

    private fun headerStatusFrom(raw: String?): String? =
        listOf("status", "attendance", "present").firstOrNull { normalize(raw).contains(it) }?.let { Status.PRESENT } // marker only

    // ---- WIDE: Roll | Name | d1 | d2 | ... ----

    private fun parseWide(
        table: List<List<String?>>,
        mapping: Mapping,
        year: Int,
        month: Int,
        roster: RosterMatch,
    ): Preview {
        val marks = mutableListOf<ParsedMark>()
        val unmatched = mutableListOf<UnmatchedRow>()
        val invalid = mutableListOf<InvalidRow>()
        val fileDupes = mutableListOf<FileDuplicate>()
        val seen = HashMap<Pair<String, String>, Int>()
        val dates = sortedSetOf<String>()

        for (r in 1 until table.size) {
            val cells = table[r]
            fun cell(idx: Int): String = if (idx >= 0 && idx in cells.indices) cells[idx].orEmpty().trim() else ""
            val rowText = cells.filterNotNull().joinToString(" ") { it.trim() }.trim()
            if (rowText.isEmpty()) continue
            val rowNumber = r + 1

            val rawRoll = cell(mapping.rollColumn)
            val student = roster.find(rawRoll)
            if (student == null) {
                val name = mapping.nameColumn?.let(::cell).orEmpty()
                unmatched.add(UnmatchedRow(rowNumber, rawRoll, name, rowText.take(80)))
                continue
            }

            var rowHadData = false
            for ((col, day) in mapping.dayColumns) {
                val status = Status.parse(cell(col)) ?: continue
                rowHadData = true
                val iso = LocalDate.of(year, month, day).toString()
                dates.add(iso)
                val key = rollKey(rawRoll) to iso
                val first = seen[key]
                if (first != null) {
                    fileDupes.add(FileDuplicate(rowNumber, rawRoll, iso, first))
                    continue
                }
                seen[key] = rowNumber
                marks.add(ParsedMark(rowNumber, student.id, student.rollNumber, student.name, iso, day, status))
            }
            if (!rowHadData) {
                invalid.add(InvalidRow(rowNumber, "no readable attendance marks in the day columns", rowText.take(80)))
            }
        }
        return Preview(Format.WIDE, dates.toList(), marks, unmatched, invalid, fileDupes)
    }

    // ---- LONG: Roll | Name | Date | Status ----

    private fun parseLong(
        table: List<List<String?>>,
        mapping: Mapping,
        month: Int,
        roster: RosterMatch,
    ): Preview {
        val marks = mutableListOf<ParsedMark>()
        val unmatched = mutableListOf<UnmatchedRow>()
        val invalid = mutableListOf<InvalidRow>()
        val fileDupes = mutableListOf<FileDuplicate>()
        val seen = HashMap<Pair<String, String>, Int>()
        val dates = sortedSetOf<String>()

        for (r in 1 until table.size) {
            val cells = table[r]
            fun cell(idx: Int): String = if (idx >= 0 && idx in cells.indices) cells[idx].orEmpty().trim() else ""
            val rowText = cells.filterNotNull().joinToString(" ") { it.trim() }.trim()
            if (rowText.isEmpty()) continue
            val rowNumber = r + 1

            val dateIso = parseDateFlexible(cell(mapping.dateColumn!!))
            if (dateIso == null) {
                invalid.add(InvalidRow(rowNumber, "no readable date (expected yyyy-mm-dd or dd/mm/yyyy)", rowText.take(80)))
                continue
            }
            if (dateIso.substring(5, 7).toInt() != month) {
                invalid.add(InvalidRow(rowNumber, "date $dateIso is outside the selected month", rowText.take(80)))
                continue
            }
            val status = Status.parse(cell(mapping.statusColumn!!))
            if (status == null) {
                invalid.add(InvalidRow(rowNumber, "no readable status (P/A/L/E)", rowText.take(80)))
                continue
            }

            val rawRoll = cell(mapping.rollColumn)
            val student = roster.find(rawRoll)
            if (student == null) {
                unmatched.add(UnmatchedRow(rowNumber, rawRoll, cell(mapping.nameColumn ?: -1), rowText.take(80)))
                continue
            }

            dates.add(dateIso)
            val day = dateIso.substring(8, 10).toInt()
            val key = rollKey(student.rollNumber) to dateIso
            val first = seen[key]
            if (first != null) {
                fileDupes.add(FileDuplicate(rowNumber, student.rollNumber, dateIso, first))
                continue
            }
            seen[key] = rowNumber
            marks.add(ParsedMark(rowNumber, student.id, student.rollNumber, student.name, dateIso, day, status))
        }
        return Preview(Format.LONG, dates.toList(), marks, unmatched, invalid, fileDupes)
    }

    /** ISO date or null. Accepts yyyy-m-d, dd/mm/yyyy, dd-mm-yyyy, and "5 Sep 2026" styles. */
    fun parseDateFlexible(raw: String?): String? {
        val s = raw.orEmpty().trim()
        if (s.isEmpty()) return null
        MonthlyAttendance.parseIsoLoose(s)?.let { return it }
        Regex("^(\\d{1,2})[/-](\\d{1,2})[/-](\\d{4})$").find(s)?.let { m ->
            val (d, mo, y) = m.destructured
            return try {
                LocalDate.of(y.toInt(), mo.toInt(), d.toInt()).toString()
            } catch (_: java.time.DateTimeException) {
                null
            }
        }
        val months = listOf("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec")
        val m2 = Regex("^(\\d{1,2})\\s+([A-Za-z]{3,9})\\.?\\s+(\\d{4})$").find(s) ?: return null
        val (d, monName, y) = m2.destructured
        val mi = months.indexOf(monName.take(3).lowercase())
        if (mi < 0) return null
        return try {
            LocalDate.of(y.toInt(), mi + 1, d.toInt()).toString()
        } catch (_: java.time.DateTimeException) {
            null
        }
    }
}
