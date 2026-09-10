package com.bits.facultyai.reminders

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.bits.facultyai.domain.TimeUtils
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

object ReminderScheduler {

    const val CHANNEL_ID = "faculty_reminders"
    private const val BASE_REQUEST_CODE = 4200

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Reminders",
            NotificationManager.IMPORTANCE_DEFAULT,
        )
        channel.description = "Personal reminders and task due notifications"
        manager.createNotificationChannel(channel)
    }

    fun scheduleTaskReminder(context: Context, taskId: Long, title: String, dueAtMillis: Long) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra("task_id", taskId)
            putExtra("title", title)
        }
        val pi = PendingIntent.getBroadcast(
            context,
            (BASE_REQUEST_CODE + taskId).toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, dueAtMillis, pi)
    }

    fun cancelTaskReminder(context: Context, taskId: Long) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, ReminderReceiver::class.java)
        val pi = PendingIntent.getBroadcast(
            context,
            (BASE_REQUEST_CODE + taskId).toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        am.cancel(pi)
    }

    fun postNotification(context: Context, id: Int, title: String, text: String) {
        ensureChannel(context)
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notif = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .build()
        manager.notify(id, notif)
    }
}

/** Fires the reminder notification at the scheduled time. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getLongExtra("task_id", -1)
        val title = intent.getStringExtra("title") ?: "Reminder"
        ReminderScheduler.postNotification(
            context = context,
            id = (taskId and 0x7fffffff).toInt(),
            title = "Faculty AI",
            text = title,
        )
    }
}

/** Re-schedules upcoming reminders after reboot. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // Reminders are persisted in Room; re-arm the next due task(s).
        // Full re-arm logic reads the DB via goAsync; kept minimal for v1.
    }
}
