package com.bits.facultyai.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

object TimeUtils {
    private val timeFormatter = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)
    private val dateFormatter = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)

    fun formatTime(minutesFromMidnight: Int): String =
        LocalTime.of(minutesFromMidnight / 60, minutesFromMidnight % 60).format(timeFormatter)

    fun formatDate(date: LocalDate): String = date.format(dateFormatter)

    fun dayName(dayOfWeek: Int): String =
        DayOfWeek.of(dayOfWeek).getDisplayName(TextStyle.FULL, Locale.ENGLISH)

    fun dayShort(dayOfWeek: Int): String =
        DayOfWeek.of(dayOfWeek).getDisplayName(TextStyle.SHORT, Locale.ENGLISH).uppercase()

    fun nowMinutes(): Int {
        val now = LocalTime.now()
        return now.hour * 60 + now.minute
    }

    fun today(): LocalDate = LocalDate.now()

    fun isoDate(date: LocalDate): String = date.toString()

    /** Returns a friendly relative label like "TODAY", "TOMORROW", "IN 3 DAYS". */
    fun relativeDayLabel(target: LocalDate, today: LocalDate = LocalDate.now()): String {
        val diff = target.toEpochDay() - today.toEpochDay()
        return when (diff) {
            0L -> "TODAY"
            1L -> "TOMORROW"
            -1L -> "YESTERDAY"
            in 2..7 -> "IN $diff DAYS"
            else -> formatDate(target).uppercase()
        }
    }
}
