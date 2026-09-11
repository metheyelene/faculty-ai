package com.bits.facultyai.domain

import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.time.YearMonth
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Monthly attendance summary as a minimal .xlsx (a zip of three small XML
 * parts, matching what [XlsxReader] consumes). Pure Kotlin — no Android
 * dependencies — so it is unit-testable as bytes.
 */
object AttendanceExcelWriter {

    private const val XML_DECL = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
    private const val NS = "http://schemas.openxmlformats.org/spreadsheetml/2006/main"

    /** Column name for a 0-based index: 0->A, 26->AA. */
    private fun columnName(index: Int): String {
        var n = index + 1
        val sb = StringBuilder()
        while (n > 0) {
            val rem = (n - 1) % 26
            sb.insert(0, 'A' + rem)
            n = (n - 1) / 26
        }
        return sb.toString()
    }

    private fun esc(s: String): String =
        s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    private fun rowOf(r: Int, cells: List<String>): String =
        "<row r=\"$r\">" +
            cells.mapIndexed { c, v ->
                "<c r=\"${columnName(c)}$r\" t=\"inlineStr\"><is><t xml:space=\"preserve\">${esc(v)}</t></is></c>"
            }.joinToString("") +
            "</row>"

    /**
     * One row per student: Roll | Name | P | L | E | A | Days | %.
     * Header carries class, subject, month and the class stats.
     */
    fun writeSummary(
        out: OutputStream,
        classLabel: String,
        subjectLabel: String,
        month: YearMonth,
        summary: MonthlyAttendance.MonthlySummary,
    ) {
        val rows = mutableListOf<List<String>>()
        rows.add(listOf("MONTHLY ATTENDANCE", classLabel, subjectLabel, month.month.name.lowercase().replaceFirstChar { it.uppercase() } + " " + month.year))
        val stats = summary.classStats
        rows.add(
            listOf(
                "STUDENTS ${stats.totalStudents}",
                "AVERAGE ${"%.1f".format(stats.averageAttendance)}%",
                "HIGHEST ${"%.1f".format(stats.highest)}%",
                "LOWEST ${"%.1f".format(stats.lowest)}%",
                "SHORTAGE ${stats.shortage}",
            ),
        )
        rows.add(emptyList())
        rows.add(listOf("Roll No", "Student", "P", "L", "E", "A", "Days", "%"))
        summary.students.forEach { s ->
            rows.add(
                listOf(
                    s.student.rollNumber,
                    s.student.name,
                    s.present.toString(),
                    s.late.toString(),
                    s.excused.toString(),
                    s.absent.toString(),
                    s.workingDays.toString(),
                    if (s.workingDays == 0) "-" else "${"%.0f".format(s.percentage)}%",
                ),
            )
        }

        val sheetXml = StringBuilder("$XML_DECL<worksheet xmlns=\"$NS\"><sheetData>")
        rows.forEachIndexed { i, cells -> sheetXml.append(rowOf(i + 1, cells)) }
        sheetXml.append("</sheetData></worksheet>")

        ZipOutputStream(out.buffered()).use { zip ->
            zip.putNextEntry(ZipEntry("xl/worksheets/sheet1.xml"))
            zip.write(sheetXml.toString().toByteArray(StandardCharsets.UTF_8))
            zip.closeEntry()
        }
    }
}
