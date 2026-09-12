package com.bits.facultyai.domain

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.util.zip.ZipInputStream

/**
 * Minimal, dependency-free .xlsx reader.
 *
 * An .xlsx file is a zip of XML parts. We read:
 *   - xl/sharedStrings.xml  (string table)
 *   - the first worksheet   (xl/worksheets/sheet1.xml, or the first sheet*.xml part)
 *
 * Cells are returned as a rectangular table of trimmed strings (null = empty).
 * Contents are treated strictly as data — formulas are never evaluated, macros
 * cannot exist in .xlsx, and nothing is uploaded anywhere.
 */
object XlsxReader {

    /** Thrown for structurally broken workbooks with a human-readable message. */
    class ImportException(message: String) : Exception(message)

    // Resource guards: the reader is fed arbitrary files picked from SAF, so a
    // hostile workbook (zip bomb / giant sheet) must fail fast instead of
    // exhausting memory. Generous limits — far above any real roster or
    // attendance sheet, small enough to keep the parse bounded.
    private const val MAX_ENTRIES = 256
    private const val MAX_SHARED_STRINGS = 100_000
    private const val MAX_STRING_LENGTH = 32_768
    private const val MAX_ROWS = 20_000
    private const val MAX_CELLS_PER_ROW = 512

    fun readFirstSheet(input: InputStream): List<List<String?>> {
        sharedStrings = null
        var sheet: List<List<String?>>? = null
        var sawZipEntry = false
        var entries = 0

        ZipInputStream(input.buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                sawZipEntry = true
                if (++entries > MAX_ENTRIES) {
                    throw ImportException("This workbook has too many parts to read safely.")
                }
                when {
                    entry.name.equals("xl/sharedStrings.xml", ignoreCase = true) ->
                        sharedStrings = readSharedStrings(zip)
                    entry.name.startsWith("xl/worksheets/") &&
                        entry.name.endsWith(".xml") &&
                        sheet == null -> sheet = readSheet(zip)
                }
                zip.closeEntry()
            }
        }
        if (!sawZipEntry) throw ImportException("This file is not a valid .xlsx workbook.")
        val s = sheet ?: throw ImportException("No worksheet found in the workbook.")
        return s
    }

    // ---- sharedStrings.xml ----

    private fun readSharedStrings(stream: InputStream): List<String> {
        val parser = newParser(stream)
        val strings = mutableListOf<String>()
        val sb = StringBuilder()
        var inSi = false
        while (true) {
            val ev = parser.next()
            if (ev == XmlPullParser.END_DOCUMENT) break
            when (ev) {
                XmlPullParser.START_TAG -> if (parser.name == "si") {
                    if (strings.size >= MAX_SHARED_STRINGS) {
                        throw ImportException("This workbook's shared string table is too large.")
                    }
                    inSi = true; sb.clear()
                }
                XmlPullParser.TEXT -> if (inSi) {
                    if (sb.length < MAX_STRING_LENGTH) sb.append(parser.text)
                }
                XmlPullParser.END_TAG -> if (parser.name == "si" && inSi) {
                    strings.add(sb.toString().trim())
                    inSi = false
                }
            }
        }
        return strings
    }

    // ---- worksheet ----

    /** Shared-string table of the workbook being read (zip entries may come in any order). */
    private var sharedStrings: List<String>? = null

    private fun readSheet(stream: InputStream): List<List<String?>> {
        val parser = newParser(stream)
        val rows = mutableListOf<MutableMap<Int, String?>>()
        var currentRow: MutableMap<Int, String?>? = null
        var currentCol = -1
        var cellType = ""
        var cellSb: StringBuilder? = null
        var inSharedItem = false
        var sharedItemSb: StringBuilder? = null

        while (true) {
            val ev = parser.next()
            if (ev == XmlPullParser.END_DOCUMENT) break
            when (ev) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "row" -> {
                        if (rows.size >= MAX_ROWS) {
                            throw ImportException("This worksheet has too many rows to import.")
                        }
                        currentRow = mutableMapOf(); rows.add(currentRow!!)
                    }
                    "c" -> {
                        currentCol = parser.getAttributeValue(null, "r")?.let(::colIndex) ?: (currentRow?.size ?: 0)
                        if (currentCol >= MAX_CELLS_PER_ROW) currentCol = -1
                        cellType = parser.getAttributeValue(null, "t") ?: ""
                        cellSb = StringBuilder()
                    }
                    "is" -> { inSharedItem = true; sharedItemSb = StringBuilder() }
                }
                XmlPullParser.TEXT -> {
                    if (inSharedItem) sharedItemSb?.append(parser.text)
                    else cellSb?.append(parser.text)
                }
                XmlPullParser.END_TAG -> when (parser.name) {
                    "v", "t" -> Unit // text already accumulated
                    "is" -> inSharedItem = false
                    "c" -> {
                        val raw = when (cellType) {
                            "inlineStr" -> sharedItemSb?.toString()
                            else -> cellSb?.toString()
                        } ?: ""
                        val value = raw.trim().ifEmpty { null }
                        if (currentRow != null && currentCol >= 0) {
                            val resolved = if (cellType == "s") sharedStrings?.getOrNull(value?.toIntOrNull() ?: -1) ?: value else value
                            currentRow[currentCol] = resolved
                        }
                        cellSb = null; sharedItemSb = null; cellType = ""
                    }
                    "row" -> currentRow = null
                }
            }
        }

        val width = rows.maxOfOrNull { it.keys.maxOrNull() ?: -1 }?.plus(1) ?: 0
        // Cells beyond the guard (currentCol = -1) stay null; every returned
        // string is clamped so a single cell can't balloon the row list.
        return rows.map { row ->
            List(width) { i -> row[i]?.take(MAX_STRING_LENGTH) }
        }
    }

    private fun newParser(stream: InputStream): XmlPullParser =
        Xml.newPullParser().apply {
            setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            setInput(stream, null)
        }

    /** "A1" -> 0, "AB12" -> 27. Letters only, case-insensitive. */
    private fun colIndex(ref: String): Int {
        var n = 0
        for (ch in ref) {
            if (ch.isLetter()) n = n * 26 + (ch.uppercaseChar() - 'A' + 1) else break
        }
        return n - 1
    }
}

/**
 * Parses a raw sheet into validated student rows for the import preview.
 * Pure logic — no Android dependencies beyond [XlsxReader].
 */
object StudentExcelParser {

    data class ParsedStudent(
        val rowNumber: Int,
        val rollNumber: String,
        val registrationNumber: String,
        val name: String,
        val email: String,
        val phone: String,
        val year: Int?,      // null => faculty must choose during preview
        val section: String?, // null => faculty must choose during preview
    )

    data class InvalidRow(val rowNumber: Int, val reason: String, val raw: String)

    data class FileDuplicate(val rowNumber: Int, val rollNumber: String, val name: String, val firstRowNumber: Int)

    data class Preview(
        val totalRows: Int,
        val valid: List<ParsedStudent>,
        val duplicatesInFile: List<FileDuplicate>,
        val invalid: List<InvalidRow>,
        val yearDetected: Boolean,
        val sectionDetected: Boolean,
    ) {
        val importable: List<ParsedStudent> get() = valid
        val needsYearSectionPick: Boolean get() = valid.isNotEmpty() && (!yearDetected || !sectionDetected)
    }

    // ---- header normalization + synonyms ----

    private fun normalize(s: String?) = s.orEmpty().lowercase().filter { it.isLetterOrDigit() }

    private val NAME_KEYS = listOf("studentname", "name", "fullname", "student")
    private val ROLL_KEYS = listOf("rollnumber", "rollno", "roll", "rollnum")
    private val REG_KEYS = listOf("registrationnumber", "regnumber", "regno", "registration", "regnum", "regnoid", "registrationid")
    private val EMAIL_KEYS = listOf("email", "emailid", "mail", "studentemail")
    private val PHONE_KEYS = listOf("phone", "phonenumber", "mobile", "mobileno", "contact", "contactnumber")
    private val YEAR_KEYS = listOf("year", "academicyear", "studentyear", "studyyear", "yearofstudy")
    private val SECTION_KEYS = listOf("section", "sec", "sectionname", "batchsection")

    private fun headerIndex(headers: List<String?>, keys: List<String>): Int {
        val normalized = headers.map { normalize(it) }
        keys.forEach { key ->
            normalized.indexOfFirst { it == key }.takeIf { it >= 0 }?.let { return it }
        }
        // Substring fallback: "3rd year section a" etc.
        keys.forEach { key ->
            normalized.indexOfFirst { it.contains(key) }.takeIf { it >= 0 }?.let { return it }
        }
        return -1
    }

    /** Parses "1", "1st", "I", "1st year", "first" -> 1..4; else null. */
    fun parseYear(raw: String?): Int? {
        val s = normalize(raw)
        if (s.isEmpty()) return null
        val digits = s.takeWhile { it.isDigit() }
        if (digits.isNotEmpty()) {
            val n = digits.toInt()
            return if (n in 1..4) n else null
        }
        return when {
            s.startsWith("first") || s.startsWith("i") && !s.startsWith("ii") && !s.startsWith("iv") -> 1
            s.startsWith("second") || s.startsWith("ii") && !s.startsWith("iii") -> 2
            s.startsWith("third") || s.startsWith("iii") -> 3
            s.startsWith("fourth") || s.startsWith("iv") -> 4
            else -> null
        }
    }

    /** "Section A", "A", "sec-a", "III ECE-A" -> "A". Uppercase section token or null. */
    fun parseSection(raw: String?): String? {
        val cleaned = raw.orEmpty()
            .replace(Regex("(?i)\\bsection\\b|\\bsec\\b|\\bbatch\\b"), " ")
            .replace(Regex("[^A-Za-z0-9]"), " ")
            .trim()
        val tokens = cleaned.split(Regex("\\s+")).filter { it.isNotBlank() }
        // Prefer a trailing single-letter section ("... ECE-A" -> A).
        tokens.lastOrNull { it.length == 1 && it[0].isLetter() }?.let { return it.uppercase() }
        // Fall back to the first non-roman, non-numeric token.
        val nonRoman = tokens.firstOrNull {
            it.length > 1 && it.uppercase() !in setOf("I", "II", "III", "IV", "V") && !it.all(Char::isDigit)
        }
        return nonRoman?.uppercase()?.take(4)
    }

    /** Extracts the academic year from strings like "III ECE-A" (III -> 3) or "2nd Year". */
    fun parseYearFromSection(raw: String?): Int? {
        val tokens = raw.orEmpty().replace(Regex("[^A-Za-z0-9]"), " ").trim().split(Regex("\\s+"))
        val roman = mapOf("I" to 1, "II" to 2, "III" to 3, "IV" to 4)
        tokens.firstNotNullOfOrNull { t ->
            val up = t.uppercase()
            if (up == "1ST" || up == "2ND" || up == "3RD" || up == "4TH") up.take(1).toIntOrNull() else null
        }?.let { return it }
        tokens.firstNotNullOfOrNull { roman[it.uppercase()] }?.let { return it }
        tokens.firstNotNullOfOrNull { it.toIntOrNull()?.takeIf { n -> n in 1..4 } }?.let { return it }
        return null
    }

    private val EMAIL_RE = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")

    /**
     * @param fallbackYear default year when the sheet has no usable year column
     * @param fallbackSection default section when the sheet has no usable section column
     */
    fun parse(table: List<List<String?>>, fallbackYear: Int? = null, fallbackSection: String? = null): Preview {
        if (table.isEmpty()) throw XlsxReader.ImportException("The sheet is empty.")
        val headers = table.first()
        if (headers.all { it.isNullOrBlank() }) throw XlsxReader.ImportException("The first row doesn't contain column headers.")

        val iName = headerIndex(headers, NAME_KEYS)
        val iRoll = headerIndex(headers, ROLL_KEYS)
        val iReg = headerIndex(headers, REG_KEYS)
        val iEmail = headerIndex(headers, EMAIL_KEYS)
        val iPhone = headerIndex(headers, PHONE_KEYS)
        val iYear = headerIndex(headers, YEAR_KEYS)
        val iSection = headerIndex(headers, SECTION_KEYS)

        if (iName < 0) throw XlsxReader.ImportException("Couldn't find a \"Name\" column. Include a column named Name or Student Name.")
        if (iRoll < 0) throw XlsxReader.ImportException("Couldn't find a \"Roll Number\" column. Include a column named Roll No or Roll Number.")

        val valid = mutableListOf<ParsedStudent>()
        val invalid = mutableListOf<InvalidRow>()
        val fileDuplicates = mutableListOf<FileDuplicate>()
        val seenRolls = HashMap<String, Int>()
        var yearDetected = iYear >= 0
        var sectionDetected = iSection >= 0

        for (r in 1 until table.size) {
            val cells = table[r]
            fun cell(idx: Int): String = if (idx in cells.indices) cells[idx].orEmpty().trim() else ""
            val rawName = cell(iName)
            val rawRoll = cell(iRoll)
            val rowText = cells.filterNotNull().joinToString(" ") { it.trim() }.trim()

            // Skip fully blank rows silently.
            if (rowText.isEmpty()) continue

            val rowNumber = r + 1
            val problems = mutableListOf<String>()

            if (rawName.isEmpty()) problems.add("missing name")
            if (rawRoll.isEmpty()) problems.add("missing roll number")

            val email = cell(iEmail)
            if (email.isNotEmpty() && !EMAIL_RE.matches(email)) problems.add("invalid email \"$email\"")

            val year = parseYear(cell(iYear)) ?: fallbackYear
            if (year == null) problems.add("invalid or missing year")

            val section = parseSection(cell(iSection)) ?: fallbackSection?.let { parseSection(it) }
            if (section == null) problems.add("invalid or missing section")

            if (problems.isNotEmpty()) {
                invalid.add(InvalidRow(rowNumber, problems.joinToString(", "), rowText.take(80)))
                continue
            }

            val key = rawRoll.lowercase()
            val firstRow = seenRolls[key]
            if (firstRow != null) {
                fileDuplicates.add(FileDuplicate(rowNumber, rawRoll, rawName, firstRow))
                continue
            }
            seenRolls[key] = rowNumber

            valid.add(
                ParsedStudent(
                    rowNumber = rowNumber,
                    rollNumber = rawRoll,
                    registrationNumber = cell(iReg),
                    name = rawName,
                    email = email,
                    phone = cell(iPhone),
                    year = year,
                    section = section,
                )
            )
        }

        return Preview(
            totalRows = table.size - 1,
            valid = valid,
            duplicatesInFile = fileDuplicates,
            invalid = invalid,
            yearDetected = yearDetected,
            sectionDetected = sectionDetected,
        )
    }
}
