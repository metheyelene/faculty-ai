package com.bits.facultyai.notifications

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.bits.facultyai.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Fires scheduled notifications (class reminders, task reminders).
 * Posts the notification, then re-arms the next upcoming ones so the
 * alarm chain never dies — including after device reboot.
 */
class NotificationReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val kind = intent.getStringExtra("kind") ?: "class"
        val id = intent.getIntExtra("notif_id", 1000)

        val goPending = PendingIntent.getActivity(
            context,
            id,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(
                    SyncScheduler.EXTRA_DEEP_LINK,
                    if (kind == "task") SyncScheduler.DEEP_LINK_TASKS else SyncScheduler.DEEP_LINK_TIMETABLE
                )
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val title: String
        val text: String
        val route: String
        if (kind == "task") {
            val taskId = intent.getLongExtra("task_id", -1)
            title = "Reminder"
            text = intent.getStringExtra("title") ?: "You have a reminder"
            route = SyncScheduler.DEEP_LINK_TASKS
            // One-shot: clean up the stored payload.
            context.getSharedPreferences("sync_pending", Context.MODE_PRIVATE)
                .edit().remove("task_$taskId").apply()
        } else {
            title = intent.getStringExtra("title") ?: "Upcoming class"
            text = intent.getStringExtra("text") ?: ""
            route = SyncScheduler.DEEP_LINK_TIMETABLE
        }

        val notif = NotificationCompat.Builder(context, SyncScheduler.CHANNEL_REMINDERS)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setContentIntent(goPending)
            .setCategory(if (kind == "task") NotificationCompat.CATEGORY_REMINDER else NotificationCompat.CATEGORY_EVENT)
            .build()

        notifySafely(context, id, notif)

        // Keep the chain alive: schedule the next upcoming notifications.
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                SyncScheduler.syncAll(context)
            } finally {
                pending.finish()
            }
        }
    }

}

private fun notifySafely(context: Context, id: Int, notif: android.app.Notification) {
    if (android.os.Build.VERSION.SDK_INT >= 33 &&
        androidx.core.content.ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.POST_NOTIFICATIONS
        ) != android.content.pm.PackageManager.PERMISSION_GRANTED
    ) return
    try {
        NotificationManagerCompat.from(context).notify(id, notif)
    } catch (_: SecurityException) {
    }
}

/** Re-arms every scheduled notification after device reboot. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                SyncScheduler.syncAll(context)
            } finally {
                pending.finish()
            }
        }
    }
}
