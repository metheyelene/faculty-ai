package com.bits.facultyai.domain

import com.bits.facultyai.data.local.ClassSlotEntity
import com.bits.facultyai.data.local.FacultyProfileEntity

/** Real statistics for the faculty's day — nothing hardcoded. */
data class DayContext(
    val classesToday: Int,
    val tasksOpen: Int,
    val meetingsToday: Int,
    val deadlinesThisWeek: Int,
    val nextClass: ClassSlotEntity?,
    val minutesUntilNextClass: Int?,
)

object ContextEngine {

    fun computeDayContext(
        timetable: List<ClassSlotEntity>,
        tasks: List<com.bits.facultyai.data.local.TaskEntity>,
        todayDayOfWeek: Int,
        nowMinutes: Int,
    ): DayContext {
        val todayClasses = timetable
            .filter { it.dayOfWeek == todayDayOfWeek }
            .sortedBy { it.startTimeMinutes }

        val next = todayClasses.firstOrNull { it.endTimeMinutes > nowMinutes }

        val today = com.bits.facultyai.domain.TimeUtils.today()
        val weekEnd = today.plusDays(7)
        val deadlines = tasks.count { !it.completed && it.dueAt != null &&
            java.time.Instant.ofEpochMilli(it.dueAt).atZone(java.time.ZoneId.systemDefault()).toLocalDate() <= weekEnd }

        return DayContext(
            classesToday = todayClasses.size,
            tasksOpen = tasks.count { !it.completed },
            meetingsToday = todayClasses.count { it.subject.contains("meeting", ignoreCase = true) },
            deadlinesThisWeek = deadlines,
            nextClass = next,
            minutesUntilNextClass = next?.let { it.startTimeMinutes - nowMinutes },
        )
    }

    /** Greets by time of day using the faculty's preferred style. */
    fun greeting(hour: Int, style: Int): String = when (style) {
        1 -> "WELCOME BACK"
        2 -> "HELLO"
        else -> when {
            hour < 12 -> "GOOD MORNING"
            hour < 17 -> "GOOD AFTERNOON"
            else -> "GOOD EVENING"
        }
    }

    fun displayName(profile: FacultyProfileEntity?): String {
        if (profile == null) return ""
        val pref = profile.preferredName.trim()
        if (pref.isNotEmpty()) return pref
        val full = profile.fullName.trim()
        return if (full.contains(" ")) {
            val parts = full.split(" ")
            parts.first() + " " + parts.last()
        } else full
    }

    /** Time-of-day remark shown in the morning brief, from real data only. */
    fun briefLine(ctx: DayContext): String? = when {
        ctx.nextClass == null && ctx.classesToday == 0 -> null
        ctx.nextClass != null && (ctx.minutesUntilNextClass ?: 0) in 0..30 ->
            "Your ${ctx.nextClass.subject} class starts in ${ctx.minutesUntilNextClass} minutes."
        ctx.deadlinesThisWeek > 0 ->
            "You have ${ctx.deadlinesThisWeek} task${if (ctx.deadlinesThisWeek > 1) "s" else ""} due this week."
        else -> null
    }
}
