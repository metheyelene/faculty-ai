package com.bits.facultyai.ui.events

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/** Which cohort of events is shown on the list screen. */
enum class EventFilter { ALL, UPCOMING, PAST, THIS_MONTH, THIS_YEAR }

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

    /** Indian-format rupee string from paise: 2545900 → "₹25,459". Negative balances get a leading minus. */
    fun money(paisa: Long): String {
        // Work on the absolute value so the sign never lands inside the
        // digit/paise groups (e.g. -50 paise used to render as "₹0.-50").
        val negative = paisa < 0
        val abs = kotlin.math.abs(paisa)
        val whole = abs / 100
        val paisePart = (abs % 100).toInt()
        val sign = if (negative) "-" else ""
        val digits = whole.toString()
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

    /**
     * Parses a rupee amount typed by the user into exact paise — integer
     * math only, never Double, so values like 18450.10 or 0.99 cannot drift
     * by a paise. Accepts Indian comma grouping and an optional ₹ prefix.
     * Returns null for anything malformed (the editor treats null as a
     * validation error, not a guess).
     */
    fun paisaFromRupees(raw: String): Long? {
        val cleaned = raw.trim().removePrefix("₹").replace(",", "").trim()
        if (cleaned.isEmpty()) return null
        val parts = cleaned.split(".")
        if (parts.size > 2) return null
        val rupees = parts[0]
        if (rupees.isEmpty() || rupees.length > 12 || rupees.any { !it.isDigit() }) return null
        val whole = rupees.toLongOrNull() ?: return null
        val paise = when (parts.size) {
            1 -> 0L
            2 -> {
                val frac = parts[1]
                when {
                    // A lone trailing point ("25000.") means whole rupees.
                    frac.isEmpty() -> 0L
                    frac.length > 2 -> return null
                    frac.length == 1 -> frac.toLong() * 10
                    else -> frac.toLong()
                }
            }
            else -> return null
        }
        return whole * 100 + paise
    }

    /** Pure date-window predicate behind the list filters — inject [today] for tests. */
    fun passesFilter(dateIso: String, filter: EventFilter, today: LocalDate): Boolean {
        val d = parse(dateIso) ?: return false
        return when (filter) {
            EventFilter.ALL -> true
            EventFilter.UPCOMING -> d >= today
            EventFilter.PAST -> d < today
            EventFilter.THIS_MONTH -> d.year == today.year && d.month == today.month
            EventFilter.THIS_YEAR -> d.year == today.year
        }
    }
}
