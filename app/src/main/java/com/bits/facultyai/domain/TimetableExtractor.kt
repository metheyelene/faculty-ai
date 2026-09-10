package com.bits.facultyai.domain

import com.bits.facultyai.data.local.ClassSlotEntity
import java.util.Locale

/**
 * On-device heuristic "OCR" for timetable photos.
 *
 * Production OCR (ML Kit / cloud vision) can be dropped in behind this same
 * interface — the review step below guarantees no extraction is ever trusted
 * blindly: every row must be confirmed or corrected by the faculty member.
 *
 * Heuristic line grammar (rows like):
 *   "Mon 9:00-10:00 DSP III ECE-A B204"
 *   "Tuesday | 14:00 – 15:30 | Communication Systems | II ECE-B | Room 105"
 *   "Wednesday 11 AM 12 PM VLSI IV ECE-B 301"
 */
object TimetableExtractor {

    data class ExtractedSlot(
        val dayOfWeek: Int = 1,
        val startMinutes: Int = 9 * 60,
        val endMinutes: Int = 10 * 60,
        val subject: String = "",
        val section: String = "",
        val room: String = "",
    )

    data class ExtractionResult(
        val slots: List<ExtractedSlot>,
        val skippedLines: List<String>,
    )

    private val dayTokens = mapOf(
        "mon" to 1, "monday" to 1,
        "tue" to 2, "tues" to 2, "tuesday" to 2,
        "wed" to 3, "weds" to 3, "wednesday" to 3,
        "thu" to 4, "thur" to 4, "thurs" to 4, "thursday" to 4,
        "fri" to 5, "friday" to 5,
        "sat" to 6, "saturday" to 6,
        "sun" to 7, "sunday" to 7,
    )

    fun extract(lines: List<String>): ExtractionResult {
        val slots = mutableListOf<ExtractedSlot>()
        val skipped = mutableListOf<String>()
        var carryDay: Int? = null

        for (raw in lines) {
            val line = raw.trim()
            if (line.isBlank()) continue
            val parsed = parseLine(line, carryDay)
            if (parsed == null) {
                skipped.add(line)
            } else {
                slots.add(parsed.slot)
                carryDay = parsed.day
            }
        }
        return ExtractionResult(slots, skipped)
    }

    private data class LineParse(val day: Int?, val slot: ExtractedSlot)

    private fun parseLine(line: String, carryDay: Int?): LineParse? {
        val lower = line.lowercase(Locale.ENGLISH)
        var day = dayTokens.entries
            .filter { Regex("\\b${it.key}\\b").containsMatchIn(lower) }
            .maxByOrNull { it.key.length }
            ?.value
        if (day == null && carryDay != null) day = carryDay
        if (day == null) return null

        val times = parseTimes(lower)
        if (times == null) return null
        val (start, end) = times

        var rest = line
            .replace(Regex("(?i)\\b(mon|tues?|weds?|thur?s?|fri|sat|sun)(day)?\\b"), " ")
            .replace(Regex("(?i)\\b\\d{1,2}(:\\d{2})?\\s*(am|pm)\\b"), " ")
            .replace(Regex("(?i)\\b\\d{1,2}:\\d{2}\\b"), " ")
            .replace(Regex("(?i)\\b\\d{1,2}\\s*(am|pm)\\b"), " ")
            .replace(Regex("[-–—|,/]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

        // Pull room tokens like "B204", "Room 105", "Lab 2", "301"
        var room = ""
        val roomRegex = Regex("(?i)(?:room|rm|lab)?\\s*([a-z]?\\d{2,4}[a-z]?)\\b")
        roomRegex.find(rest)?.let { m ->
            room = m.value.trim().replace(Regex("(?i)^(room|rm)\\s*"), "").uppercase(Locale.ENGLISH)
            rest = rest.replaceRange(m.range, " ")
        }

        // Pull section tokens like "III ECE-A", "II CSE B", "ECE-B", "Section A"
        var section = ""
        val sectionRegex = Regex("(?i)\\b([ivx]{1,4})\\s*([a-z]{2,5})\\s*-?\\s*([abcd])\\b")
        sectionRegex.find(rest)?.let { m ->
            section = buildString {
                append(m.groupValues[1].uppercase(Locale.ENGLISH))
                append(' ')
                append(m.groupValues[2].uppercase(Locale.ENGLISH))
                append('-')
                append(m.groupValues[3].uppercase(Locale.ENGLISH))
            }
            rest = rest.replaceRange(m.range, " ")
        } ?: run {
            val shortSection = Regex("(?i)\\bsec(?:tion)?\\s*([abcd])\\b").find(rest)
            if (shortSection != null) {
                section = shortSection.groupValues[1].uppercase(Locale.ENGLISH)
                rest = rest.replaceRange(shortSection.range, " ")
            }
        }

        rest = rest.replace(Regex("(?i)\\b(room|rm|lab|sec|section|period|class)\\b"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .trim('-', '–', '—', '|', ',')

        val subject = rest.ifBlank { "Class" }
        return LineParse(day, ExtractedSlot(day, start, end, subject, section, room))
    }

    /** Finds the first two time tokens; returns (start, end) minutes or null. */
    private fun parseTimes(lower: String): Pair<Int, Int>? {
        val timeRegex = Regex("\\b(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)?\\b")
        val matches = timeRegex.findAll(lower).toList()
        val parsed = matches.mapNotNull { m ->
            val h = m.groupValues[1].toIntOrNull() ?: return@mapNotNull null
            val min = m.groupValues[2].toIntOrNull() ?: 0
            var hour = h
            when (m.groupValues[3]) {
                "pm" -> if (hour < 12) hour += 12
                "am" -> if (hour == 12) hour = 0
            }
            // Bare numbers 0..7 are likely day indexes or room digits, not times.
            if (m.groupValues[3].isEmpty() && m.groupValues[2].isEmpty() && (h !in 1..12)) null
            else if (hour in 0..23 && min in 0..59) hour * 60 + min
            else null
        }
        val start = parsed.firstOrNull() ?: return null
        val end = parsed.firstOrNull { it > start } ?: (start + 60)
        return start to end
    }

    /** Serializes extracted slots for version archiving. */
    fun serialize(slots: List<ExtractedSlot>): String =
        slots.joinToString("\n") { "${it.dayOfWeek}|${it.startMinutes}|${it.endMinutes}|${it.subject}|${it.section}|${it.room}" }

    fun deserialize(json: String): List<ClassSlotEntity> =
        json.split("\n").filter { it.isNotBlank() }.mapNotNull { row ->
            val parts = row.split("|")
            if (parts.size < 6) return@mapNotNull null
            ClassSlotEntity(
                dayOfWeek = parts[0].toIntOrNull() ?: return@mapNotNull null,
                startTimeMinutes = parts[1].toIntOrNull() ?: return@mapNotNull null,
                endTimeMinutes = parts[2].toIntOrNull() ?: return@mapNotNull null,
                subject = parts[3],
                section = parts[4],
                room = parts[5],
            )
        }
}
