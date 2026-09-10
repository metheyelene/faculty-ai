package com.bits.facultyai.widget

import android.content.Context
import com.bits.facultyai.data.local.FacultyDatabase
import com.bits.facultyai.data.local.TaskEntity
import com.bits.facultyai.domain.ScheduleEngine
import com.bits.facultyai.domain.TimeUtils
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Lightweight data bridge for home-screen widgets. Widgets run outside the
 * activity lifecycle, so this reads the local Room cache synchronously via
 * runBlocking — bounded, index-backed queries only, never the network.
 */
object WidgetData {

    data class WidgetClass(
        val subject: String,
        val section: String,
        val room: String,
        val timeLabel: String,
        val whenLabel: String,
    )

    data class WidgetSnapshot(
        val hasData: Boolean,
        val nextClass: WidgetClass?,
        val todayClasses: List<WidgetClass>,
        val nextReminders: List<String>,
    )

    fun snapshot(context: Context): WidgetSnapshot = kotlinx.coroutines.runBlocking {
        val dao = FacultyDatabase.get(context).facultyDao()
        val timetable = dao.getTimetable()
        if (timetable.isEmpty()) {
            return@runBlocking WidgetSnapshot(false, null, emptyList(), emptyList())
        }
        val today = LocalDate.now()
        val nowMinutes = TimeUtils.nowMinutes()

        val next = ScheduleEngine.nextClass(timetable, today.dayOfWeek.value, nowMinutes, today)
        val nextClass = next?.let {
            WidgetClass(
                subject = it.slot.subject,
                section = it.slot.section,
                room = it.slot.room,
                timeLabel = TimeUtils.formatTime(it.slot.startTimeMinutes),
                whenLabel = ScheduleEngine.whenLabel(it, today),
            )
        }

        val todayClasses = ScheduleEngine.slotsForDay(timetable, today.dayOfWeek.value)
            .map {
                WidgetClass(
                    subject = it.subject,
                    section = it.section,
                    room = it.room,
                    timeLabel = TimeUtils.formatTime(it.startTimeMinutes),
                    whenLabel = "",
                )
            }

        val reminders: List<TaskEntity> = dao.getTasks()
            .filter { !it.completed && it.dueAt != null }
            .sortedBy { it.dueAt }
            .take(3)

        WidgetSnapshot(
            hasData = true,
            nextClass = nextClass,
            todayClasses = todayClasses,
            nextReminders = reminders.map { task ->
                val date = Instant.ofEpochMilli(task.dueAt ?: 0L)
                    .atZone(ZoneId.systemDefault()).toLocalDate()
                "${TimeUtils.relativeDayLabel(date)} — ${task.title}"
            },
        )
    }
}
