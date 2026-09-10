package com.bits.facultyai.notifications

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.bits.facultyai.MainActivity
import com.bits.facultyai.data.local.ClassSlotEntity
import com.bits.facultyai.data.local.FacultyDatabase
import com.bits.facultyai.domain.TimeUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * Central, idempotent alarm/notification scheduler.
 *
 * - Timetable sync: arms alarms for upcoming classes ("class tomorrow" and
 *   "starting soon"), re-armed whenever the timetable changes, after boot and
 *   after any notification fires. The alarm chain never dies.
 * - Task reminders: one alarm per task with a due date, keyed by task id so
 *   re-scheduling never duplicates.
 *
 * Every notification deep-links back into the app via MainActivity.
 */
object SyncScheduler {

    const val EXTRA_DEEP_LINK = "deep_link_route"
    const val DEEP_LINK_TIMETABLE = "timetable"
    const val DEEP_LINK_TASKS = "tasks"
    const val DEEP_LINK_CALENDAR = "calendar"

    const val CHANNEL_REMINDERS = "faculty_reminders"
    const val CHANNEL_CLASSES = "faculty_classes"

    private const val CLASS_BASE = 900_000 // class-reminder request codes
    private const val TASK_BASE = 800_000 // task-reminder request codes (offset by task id)

    fun ensureChannels(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val reminders = NotificationChannel(
            CHANNEL_REMINDERS,
            "Reminders",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "Personal reminders and task due notifications"
        }
        val classes = NotificationChannel(
            CHANNEL_CLASSES,
            "Class reminders",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Timetable-based class start notifications"
        }
        manager.createNotificationChannel(reminders)
        manager.createNotificationChannel(classes)
    }

    private fun alarmManager(context: Context) =
        context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    private fun canScheduleExact(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        return alarmManager(context).canScheduleExactAlarms()
    }

    private fun contentIntent(context: Context, route: String?, requestCode: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_DEEP_LINK, route)
        }
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun broadcastIntent(
        context: Context,
        kind: String,
        requestCode: Int,
        title: String,
        text: String,
        taskId: Long = -1L,
    ): PendingIntent {
        val intent = Intent(context, NotificationReceiver::class.java).apply {
            putExtra("kind", kind)
            putExtra("notif_id", requestCode)
            putExtra("title", title)
            putExtra("text", text)
            putExtra("task_id", taskId)
        }
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun setAlarm(context: Context, triggerAt: Long, pi: PendingIntent) {
        if (triggerAt <= System.currentTimeMillis()) return
        if (canScheduleExact(context)) {
            alarmManager(context).setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        } else {
            alarmManager(context).setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        }
    }

    private fun cancelActivityIntent(context: Context, requestCode: Int, route: String?) {
        alarmManager(context).cancel(contentIntent(context, route, requestCode))
    }

    private fun codeFor(date: LocalDate, slot: ClassSlotEntity, variant: Int): Int =
        CLASS_BASE + ((date.toEpochDay().toInt() * 31 + slot.id.toInt() * 7 + variant) and 0x7FF)

    /**
     * Idempotent timetable sync: cancels previously armed class reminders,
     * then arms "class tomorrow" (24h early) and "starting soon" (15 min early)
     * alarms for upcoming classes within the next week. Safe to call any time —
     * repeated calls replace, never duplicate.
     */
    suspend fun syncTimetableReminders(context: Context) = withContext(Dispatchers.IO) {
        val dao = FacultyDatabase.get(context).facultyDao()
        val slots = dao.getTimetable()

        // Cancel every possible class-reminder broadcast (same request-code space).
        for (code in CLASS_BASE until CLASS_BASE + 2048) {
            alarmManager(context).cancel(
                broadcastIntent(context, "class", code, "", "")
            )
        }

        val today = LocalDate.now()
        val nowMillis = System.currentTimeMillis()
        var armed = 0
        for (offset in 0..7) {
            val date = today.plusDays(offset.toLong())
            val daySlots = slots.filter { it.dayOfWeek == date.dayOfWeek.value }
                .sortedBy { it.startTimeMinutes }
            for (slot in daySlots) {
                if (armed >= 40) return@withContext
                val start = LocalDateTime.of(
                    date,
                    LocalTime.of(slot.startTimeMinutes / 60, slot.startTimeMinutes % 60),
                )
                val zone = ZoneId.systemDefault()
                val dayBeforeTrigger = start.minusHours(24).atZone(zone).toInstant().toEpochMilli()
                val soonTrigger = start.minusMinutes(15).atZone(zone).toInstant().toEpochMilli()

                if (dayBeforeTrigger > nowMillis) {
                    setAlarm(
                        context,
                        dayBeforeTrigger,
                        broadcastIntent(
                            context,
                            "class",
                            codeFor(date, slot, 0),
                            "Class tomorrow",
                            "${slot.subject} · ${slot.section} — ${TimeUtils.formatTime(slot.startTimeMinutes)}",
                        ),
                    )
                    armed++
                }
                if (soonTrigger > nowMillis) {
                    setAlarm(
                        context,
                        soonTrigger,
                        broadcastIntent(
                            context,
                            "class",
                            codeFor(date, slot, 1),
                            "Class starting soon",
                            "${slot.subject} · ${slot.section} · Room ${slot.room} — starts in 15 minutes",
                        ),
                    )
                    armed++
                }
            }
        }
    }

    /**
     * Arms (or replaces) the reminder alarm for one task. Keyed by task id —
     * calling twice with the same task never creates a duplicate.
     */
    fun scheduleTaskReminder(context: Context, taskId: Long, title: String, dueAtMillis: Long) {
        setAlarm(
            context,
            dueAtMillis,
            broadcastIntent(
                context,
                "task",
                (TASK_BASE + taskId).toInt(),
                "Reminder",
                title,
                taskId,
            ),
        )
    }

    fun cancelTaskReminder(context: Context, taskId: Long) {
        val am = alarmManager(context)
        am.cancel(
            broadcastIntent(context, "task", (TASK_BASE + taskId).toInt(), "", "", taskId)
        )
    }

    /** Re-arms task reminders for every open, future-dated task (post-boot / post-update). */
    suspend fun rearmAllTaskReminders(context: Context) = withContext(Dispatchers.IO) {
        val dao = FacultyDatabase.get(context).facultyDao()
        dao.getTasks()
            .filter { !it.completed && it.dueAt != null && it.dueAt > System.currentTimeMillis() }
            .forEach { scheduleTaskReminder(context, it.id, it.title, it.dueAt ?: return@forEach) }
    }

    /** Full idempotent resync — call after timetable/task changes, on boot, on app open. */
    suspend fun syncAll(context: Context) {
        ensureChannels(context)
        rearmAllTaskReminders(context)
        syncTimetableReminders(context)
    }
}
