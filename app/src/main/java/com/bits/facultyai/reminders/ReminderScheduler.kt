package com.bits.facultyai.reminders

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import com.bits.facultyai.notifications.SyncScheduler

/**
 * Compatibility facade over [SyncScheduler]. Keeps the original public API
 * (`ensureChannel`, `scheduleTaskReminder`, `cancelTaskReminder`,
 * `postNotification`) so existing call sites continue to work.
 */
object ReminderScheduler {

    const val CHANNEL_ID = SyncScheduler.CHANNEL_REMINDERS

    fun ensureChannel(context: Context) = SyncScheduler.ensureChannels(context)

    fun scheduleTaskReminder(context: Context, taskId: Long, title: String, dueAtMillis: Long) =
        SyncScheduler.scheduleTaskReminder(context, taskId, title, dueAtMillis)

    fun cancelTaskReminder(context: Context, taskId: Long) =
        SyncScheduler.cancelTaskReminder(context, taskId)
}
