package com.bits.facultyai.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/** Parses simple natural-language due dates from quick-add task text. */
object NaturalDateParser {

    data class Result(val dueAtMillis: Long?, val remainingText: String)

    fun parse(text: String): Result {
        val lower = text.lowercase().trim()
        var dueDate: LocalDate? = null
        var time: LocalTime? = null
        var remaining = text.trim()

        // Time: "9am", "2:30 pm", "14:00"
        val timeRegex = Regex("(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)?")
        val timeMatch = timeRegex.find(lower)
        if (timeMatch != null) {
            var hour = timeMatch.groupValues[1].toIntOrNull() ?: -1
            val minute = timeMatch.groupValues[2].toIntOrNull() ?: 0
            val ampm = timeMatch.groupValues[3]
            if (hour in 0..23) {
                if (ampm == "pm" && hour < 12) hour += 12
                if (ampm == "am" && hour == 12) hour = 0
                time = LocalTime.of(hour, minute)
            }
        }

        // Day expressions
        when {
            lower.contains("today") -> dueDate = LocalDate.now()
            lower.contains("tomorrow") -> dueDate = LocalDate.now().plusDays(1)
            else -> {
                DayOfWeek.entries.forEach { day ->
                    val name = day.name.lowercase() // monday, tuesday...
                    val short = name.take(3)
                    if (lower.contains(" $name") || lower.contains(" $short ")) {
                        var target = LocalDate.now().with(java.time.temporal.TemporalAdjusters.next(day))
                        dueDate = target
                    }
                }
            }
        }

        // Strip recognized tokens from the remaining text
        if (dueDate != null || time != null) {
            remaining = remaining
                .replace(Regex("(?i)\\b(today|tomorrow)\\b"), "")
                .replace(Regex("(?i)\\b(mon|tues?|wed(?:nesday)?|thu(?:rs?)?|fri|sat(?:urday)?|sun)(?:day)?\\b"), "")
                .replace(timeRegex, "")
                .replace(Regex("\\s+"), " ")
                .trim()
        }

        val dueAt = if (dueDate != null || time != null) {
            val d = dueDate ?: LocalDate.now()
            val t = time ?: LocalTime.of(9, 0)
            LocalDateTime.of(d, t).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        } else null

        return Result(dueAtMillis = dueAt, remainingText = remaining.ifBlank { text.trim() })
    }
}
