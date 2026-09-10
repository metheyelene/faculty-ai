package com.bits.facultyai.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Tests for the Excel import pipeline's pure-logic layer:
 * [StudentExcelParser] (row validation, duplicates, header synonyms, year/section
 * parsing) and the JVM-safe failure modes of [XlsxReader] (malformed workbooks).
 *
 * The full XML worksheet parsing path of XlsxReader needs Android's Xml class
 * and is exercised on-device instead.
 */
class StudentExcelParserTest {

    // ---- helpers ----

    private fun row(vararg cells: String?): List<String?> = cells.toList()

    private fun parse(vararg rows: List<String?>, year: Int? = null, section: String? = null) =
        StudentExcelParser.parse(rows.toList(), fallbackYear = year, fallbackSection = section)

    private val headers = row("Roll No", "Name", "Email", "Year", "Section")

    // ---- happy path ----

    @Test
    fun `parses valid students with year and section columns`() {
        val table = listOf(
            headers,
            row("23B101", "Asha Rao", "asha@college.edu", "2", "A"),
            row("23B102", "Bilal Khan", "bilal@college.edu", "2", "B"),
        )
        val p = StudentExcelParser.parse(table)

        assertEquals(2, p.valid.size)
        assertEquals(0, p.invalid.size)
        assertEquals(0, p.duplicatesInFile.size)
        assertTrue(p.yearDetected && p.sectionDetected)
        assertEquals(2, p.totalRows)

        val first = p.valid.first()
        assertEquals("23B101", first.rollNumber)
        assertEquals("Asha Rao", first.name)
        assertEquals("asha@college.edu", first.email)
        assertEquals(2, first.year)
        assertEquals("A", first.section)
    }

    @Test
    fun `header name variations map to the same logical fields`() {
        val table = listOf(
            row("Roll_Number", "Student Name", "Registration Number", "Reg No", "Mail ID", "Study Year", "Sec"),
            row("101", "Asha", "REG2023001", "REG2023001", "asha@x.com", "III", "A"),
        )
        val p = StudentExcelParser.parse(table)

        assertEquals(1, p.valid.size)
        val s = p.valid.first()
        assertEquals("101", s.rollNumber)
        assertEquals("Asha", s.name)
        assertEquals("REG2023001", s.registrationNumber)
        assertEquals("asha@x.com", s.email)
        assertEquals(3, s.year)
        assertEquals("A", s.section)
    }

    // ---- blank rows ----

    @Test
    fun `blank rows are skipped without becoming errors`() {
        val table = listOf(
            headers,
            row("101", "Asha", "", "1", "A"),
            row(null, null, null, null, null),
            row("", "", "", "", ""),
            row("102", "Bilal", "", "1", "A"),
        )
        val p = StudentExcelParser.parse(table)

        assertEquals(2, p.valid.size)
        assertEquals(0, p.invalid.size)
        assertEquals(listOf("101", "102"), p.valid.map { it.rollNumber })
    }

    @Test
    fun `header-only sheet yields zero students without crashing`() {
        val p = StudentExcelParser.parse(listOf(headers))

        assertEquals(0, p.valid.size)
        assertEquals(0, p.invalid.size)
        assertEquals(0, p.totalRows)
    }

    @Test
    fun `empty sheet is rejected with a readable message`() {
        try {
            StudentExcelParser.parse(emptyList())
            throw AssertionError("expected ImportException")
        } catch (e: XlsxReader.ImportException) {
            assertTrue(e.message!!.contains("empty", ignoreCase = true))
        }
    }

    // ---- duplicates within the file ----

    @Test
    fun `duplicate roll numbers in the file are detected`() {
        val table = listOf(
            headers,
            row("101", "Asha", "", "1", "A"),
            row("101", "Asha Duplicate", "", "1", "A"),
            row("102", "Bilal", "", "1", "A"),
        )
        val p = StudentExcelParser.parse(table)

        assertEquals(2, p.valid.size)
        assertEquals(1, p.duplicatesInFile.size)
        val dup = p.duplicatesInFile.first()
        assertEquals(3, dup.rowNumber) // sheet row number (header is row 1)
        assertEquals("101", dup.rollNumber)
        assertEquals(2, dup.firstRowNumber)
    }

    @Test
    fun `duplicate detection is case-insensitive on roll numbers`() {
        val table = listOf(
            headers,
            row("a101", "Asha", "", "1", "A"),
            row("A101", "Asha Again", "", "1", "A"),
        )
        val p = StudentExcelParser.parse(table)

        assertEquals(1, p.valid.size)
        assertEquals(1, p.duplicatesInFile.size)
    }

    // ---- invalid rows ----

    @Test
    fun `rows missing name or roll number are reported with reasons`() {
        val table = listOf(
            headers,
            row("101", "", "", "1", "A"),      // missing name
            row("", "NoRoll", "", "1", "A"),   // missing roll
        )
        val p = StudentExcelParser.parse(table)

        assertEquals(2, p.invalid.size)
        assertTrue(p.invalid[0].reason.contains("missing name"))
        assertTrue(p.invalid[1].reason.contains("missing roll number"))
    }

    @Test
    fun `malformed email invalidates the row`() {
        val table = listOf(
            headers,
            row("101", "Asha", "not-an-email", "1", "A"),
            row("102", "Bilal", "bilal@x.com", "1", "A"),
        )
        val p = StudentExcelParser.parse(table)

        assertEquals(1, p.valid.size)
        assertEquals(1, p.invalid.size)
        assertTrue(p.invalid[0].reason.contains("invalid email"))
    }

    @Test
    fun `out-of-range year invalidates the row when no fallback is given`() {
        val table = listOf(
            headers,
            row("101", "Asha", "", "5", "A"),
        )
        val p = StudentExcelParser.parse(table)

        assertEquals(0, p.valid.size)
        assertEquals(1, p.invalid.size)
        assertTrue(p.invalid[0].reason.contains("year"))
    }

    // ---- fallbacks ----

    @Test
    fun `fallback year and section apply when columns are absent`() {
        val table = listOf(
            row("Roll No", "Name"),
            row("101", "Asha"),
            row("102", "Bilal"),
        )
        val p = StudentExcelParser.parse(table, fallbackYear = 3, fallbackSection = "B")

        assertTrue(p.valid.isNotEmpty())
        assertTrue(p.valid.all { it.year == 3 && it.section == "B" })
        assertTrue(p.needsYearSectionPick)
    }

    @Test
    fun `needsYearSectionPick is false when the sheet provides both`() {
        val table = listOf(
            headers,
            row("101", "Asha", "", "2", "A"),
        )
        val p = StudentExcelParser.parse(table)

        assertTrue(p.valid.isNotEmpty())
        assertTrue(!p.needsYearSectionPick)
    }

    // ---- missing required columns ----

    @Test
    fun `missing name column is rejected with guidance`() {
        try {
            StudentExcelParser.parse(listOf(row("Roll No", "Email"), row("101", "a@x.com")))
            throw AssertionError("expected ImportException")
        } catch (e: XlsxReader.ImportException) {
            assertTrue(e.message!!.contains("Name"))
        }
    }

    @Test
    fun `missing roll column is rejected with guidance`() {
        try {
            StudentExcelParser.parse(listOf(row("Name", "Email"), row("Asha", "a@x.com")))
            throw AssertionError("expected ImportException")
        } catch (e: XlsxReader.ImportException) {
            assertTrue(e.message!!.contains("Roll"))
        }
    }

    @Test
    fun `sheet without header row is rejected`() {
        try {
            StudentExcelParser.parse(listOf(row(null, null, null, null, null)))
            throw AssertionError("expected ImportException")
        } catch (e: XlsxReader.ImportException) {
            assertTrue(e.message!!.contains("headers"))
        }
    }

    // ---- year parsing ----

    @Test
    fun `parseYear accepts digits, ordinals and roman numerals`() {
        assertEquals(1, StudentExcelParser.parseYear("1"))
        assertEquals(2, StudentExcelParser.parseYear("2nd"))
        assertEquals(3, StudentExcelParser.parseYear("3rd Year"))
        assertEquals(4, StudentExcelParser.parseYear("4"))
        assertEquals(1, StudentExcelParser.parseYear("I"))
        assertEquals(2, StudentExcelParser.parseYear("II"))
        assertEquals(3, StudentExcelParser.parseYear("III"))
        assertEquals(4, StudentExcelParser.parseYear("IV"))
        assertEquals(1, StudentExcelParser.parseYear("first"))
        assertEquals(4, StudentExcelParser.parseYear("Fourth"))
    }

    @Test
    fun `parseYear rejects out-of-range and nonsense values`() {
        assertNull(StudentExcelParser.parseYear("0"))
        assertNull(StudentExcelParser.parseYear("5"))
        assertNull(StudentExcelParser.parseYear("abc"))
        assertNull(StudentExcelParser.parseYear(""))
        assertNull(StudentExcelParser.parseYear(null))
    }

    @Test
    fun `year embedded in section string is extracted`() {
        assertEquals(3, StudentExcelParser.parseYearFromSection("III ECE-A"))
        assertEquals(2, StudentExcelParser.parseYearFromSection("2nd Year"))
        assertEquals(4, StudentExcelParser.parseYearFromSection("IV CSE B"))
        assertNull(StudentExcelParser.parseYearFromSection("ECE-A"))
        assertNull(StudentExcelParser.parseYearFromSection(""))
    }

    // ---- section parsing ----

    @Test
    fun `parseSection normalizes common formats`() {
        assertEquals("A", StudentExcelParser.parseSection("Section A"))
        assertEquals("B", StudentExcelParser.parseSection("B"))
        assertEquals("A", StudentExcelParser.parseSection("sec-a"))
        assertEquals("A", StudentExcelParser.parseSection("III ECE-A"))
        assertEquals("B", StudentExcelParser.parseSection("II CSE B"))
        assertEquals("A", StudentExcelParser.parseSection(" a "))
    }

    @Test
    fun `parseSection falls back to the department token`() {
        assertEquals("CSE", StudentExcelParser.parseSection("CSE"))
        assertEquals("ECE", StudentExcelParser.parseSection("ECE"))
    }

    @Test
    fun `parseSection returns null for empty input`() {
        assertNull(StudentExcelParser.parseSection(""))
        assertNull(StudentExcelParser.parseSection(null))
        assertNull(StudentExcelParser.parseSection("123"))
    }

    // ---- large roster ----

    @Test
    fun `large roster parses without duplicates or errors`() {
        val table = mutableListOf<List<String?>>(headers)
        repeat(120) { i ->
            table.add(row("${300 + i}", "Student ${300 + i}", "", "2", if (i % 2 == 0) "A" else "B"))
        }
        val p = StudentExcelParser.parse(table)

        assertEquals(120, p.valid.size)
        assertEquals(0, p.duplicatesInFile.size)
        assertEquals(0, p.invalid.size)
        assertEquals(120, p.totalRows)
    }

    // ---- XlsxReader failure modes (JVM-safe: no Android Xml usage) ----

    @Test
    fun `non-zip file is rejected as not a workbook`() {
        val bytes = "this is definitely not a spreadsheet".toByteArray()
        try {
            XlsxReader.readFirstSheet(ByteArrayInputStream(bytes))
            throw AssertionError("expected ImportException")
        } catch (e: XlsxReader.ImportException) {
            assertTrue(e.message!!.contains("not a valid .xlsx"))
        }
    }

    @Test
    fun `zip without worksheets is rejected`() {
        // Only an unrelated entry: the reader must fail without parsing any XML
        // (Android's Xml parser is a JVM stub, so keep this path parser-free).
        val bytes = ByteArrayOutputStream().use { out ->
            ZipOutputStream(out).use { zip ->
                zip.putNextEntry(ZipEntry("docProps/core.xml"))
                zip.write("not parsed here".toByteArray())
                zip.closeEntry()
            }
            out.toByteArray()
        }
        try {
            XlsxReader.readFirstSheet(ByteArrayInputStream(bytes))
            throw AssertionError("expected ImportException")
        } catch (e: XlsxReader.ImportException) {
            assertTrue(e.message!!.contains("worksheet"))
        }
    }

    @Test
    fun `empty file is rejected`() {
        try {
            XlsxReader.readFirstSheet(ByteArrayInputStream(ByteArray(0)))
            throw AssertionError("expected ImportException")
        } catch (e: XlsxReader.ImportException) {
            assertNotNull(e.message)
        }
    }
}
