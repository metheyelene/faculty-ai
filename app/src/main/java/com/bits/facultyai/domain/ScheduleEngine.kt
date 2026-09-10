package com.bits.facultyai.domain

import com.bits.facultyai.data.local.ClassSlotEntity
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * Single source of truth for "what happens next in the schedule".
 * Used by the dashboard, assistant, widgets and reminders so every surface
 * agrees on the same next-class computation. Stateless and cheap — works
 * entirely on the local timetable snapshot (no I/O).
 */
object ScheduleEngine {

    data class Upcoming(
        val slot: ClassSlotEntity,
        val date: LocalDate,
        /** true when the slot belongs to today's remaining schedule */
        val isToday: Boolean,
    )

    /** Slots scheduled on the given date's weekday, ordered by start time. */
    fun slotsForDay(timetable: List<ClassSlotEntity>, dayOfWeek: Int): List<ClassSlotEntity> =
        timetable.filter { it.dayOfWeek == dayOfWeek }.sortedBy { it.startTimeMinutes }

    /**
     * The next class strictly after [nowMinutes] if one exists on the given
     * day; when the day is over, falls forward day-by-day up to a week.
     * Pass [fromDay] = today's day-of-week and [nowMinutes] = current minute.
     */
    fun nextClass(
        timetable: List<ClassSlotEntity>,
        fromDay: Int,
        nowMinutes: Int,
        searchFrom: LocalDate = LocalDate.now(),
    ): Upcoming? {
        if (timetable.isEmpty()) return null
        // Remaining classes today
        slotsForDay(timetable, fromDay).firstOrNull { it.endTimeMinutes > nowMinutes }?.let {
            return Upcoming(it, searchFrom, isToday = true)
        }
        // Then the first class of each following day within a week
        for (offset in 1..7) {
            val date = searchFrom.plusDays(offset.toLong())
            val first = slotsForDay(timetable, date.dayOfWeek.value).firstOrNull() ?: continue
            return Upcoming(first, date, isToday = false)
        }
        return null
    }

    /** The first slot of the next day (from tomorrow) that has any class. */
    fun nextDayWithClasses(
        timetable: List<ClassSlotEntity>,
        today: LocalDate = LocalDate.now(),
    ): ClassSlotEntity? {
        for (offset in 1..7) {
            val day = today.plusDays(offset.toLong()).dayOfWeek.value
            val slot = slotsForDay(timetable, day).firstOrNull()
            if (slot != null) return slot
        }
        return null
    }

    /** Human label like "TODAY · 10:30 AM" or "MON · 21 SEP". */
    fun whenLabel(upcoming: Upcoming, today: LocalDate = LocalDate.now()): String {
        val time = TimeUtils.formatTime(upcoming.slot.startTimeMinutes)
        return when {
            upcoming.isToday -> "TODAY · $time"
            upcoming.date == today.plusDays(1) -> "TOMORROW · $time"
            else -> "${TimeUtils.dayShort(upcoming.date.dayOfWeek.value)} · ${
                TimeUtils.formatDate(upcoming.date).uppercase()
            } · $time"
        }
    }
}
