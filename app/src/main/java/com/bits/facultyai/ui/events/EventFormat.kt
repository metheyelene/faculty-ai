package com.bits.facultyai.ui.events

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/**
 * Event-finance presentation helpers. Money is stored in paise (Long) —
 * formatting happens only at the UI edge, so totals never drift.
 */
object EventFormat {
    private val dayNumber = DateTimeFormatter.ofPattern("d", Locale.ENGLISH)
    private val monthYear = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH).withLocale(Locale.ENGLISH)

    /** "18 SEPTEMBER 2026" — the editorial event-date style. */
    fun fullDate(dateIso: String): String {
        val d = parse(dateIso) ?: return dateIso
        return "${d.format(dayNumber)} ${d.month.name} ${d.year}".uppercase(Locale.ENGLISH)
    }

    /** "FRIDAY" — derived from the date, never stored separately. */
    fun dayName(dateIso: String): String =
        parse(dateIso)?.dayOfWeek?.getDisplayName(TextStyle.FULL, Locale.ENGLISH)?.uppercase(Locale.ENGLISH) ?: ""

    fun monthYear(dateIso: String): String = parse(dateIso)?.format(monthYear)?.uppercase(Locale.ENGLISH) ?: dateIso

    fun parse(dateIso: String): LocalDate? = runCatching { LocalDate.parse(dateIso) }.getOrNull()

    /** Indian-format rupee string from paise: 2545900 → "₹25,459". */
    fun money(paisa: Long): String {
        val whole = paisa / 100
        val paisePart = (paisa % 100).toInt()
        val sign = if (whole < 0) "-" else ""
        val digits = kotlin.math.abs(whole).toString()
        val grouped = when {
            digits.length <= 3 -> digits
            else -> {
                val last3 = digits.takeLast(3)
                var rest = digits.dropLast(3)
                val groups = mutableListOf<String>()
                while (rest.length > 2) {
                    groups.add(0, rest.takeLast(2)); rest = rest.dropLast(2)
                }
                if (rest.isNotEmpty()) groups.add(0, rest)
                groups.joinToString(",") + "," + last3
            }
        }
        return if (paisePart == 0) "$sign₹$grouped" else "$sign₹$grouped.${paisePart.toString().padStart(2, '0')}"
    }

    fun minutesToTime(minutes: Int): String {
        val h24 = (minutes / 60).coerceIn(0, 23)
        val m = minutes % 60
        val ampm = if (h24 < 12) "AM" else "PM"
        val h12 = when {
            h24 == 0 -> 12
            h24 > 12 -> h24 - 12
            else -> h24
        }
        return "$h12:${m.toString().padStart(2, '0')} $ampm"
    }

    fun timeToMinutes(raw: String): Int? {
        val s = raw.trim().uppercase(Locale.ENGLISH).replace(" ", "")
        val ampm = when {
            s.endsWith("AM") -> "AM"; s.endsWith("PM") -> "PM"; else -> null
        }
        val body = if (ampm != null) s.dropLast(2) else s
        val parts = body.split(":")
        val h = parts.getOrNull(0)?.toIntOrNull() ?: return null
        val m = if (parts.size > 1) parts[1].toIntOrNull() ?: return null else 0
        if (h !in 0..23 || m !in 0..59) return null
        val h24 = when {
            ampm == "AM" -> if (h == 12) 0 else h
            ampm == "PM" -> if (h == 12) 12 else h + 12
            else -> h
        }
        return h24 * 60 + m
    }
}
