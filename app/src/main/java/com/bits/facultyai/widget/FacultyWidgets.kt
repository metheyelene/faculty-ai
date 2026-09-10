package com.bits.facultyai.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.bits.facultyai.MainActivity
import com.bits.facultyai.R

/**
 * Home-screen widgets implemented with RemoteViews (no extra dependencies,
 * no risk to the app process). All tap targets deep-link into MainActivity
 * via the standard deep-link route extra.
 *
 * Dark/light: backgrounds resolve through drawable/widget_bg (light) and
 * drawable-night/widget_bg (dark); text colors resolve through
 * values/values-night color qualifiers — set once via setColorFilter-free
 * theme attributes and preserved across updates.
 */
sealed class FacultyWidget(
    private val receiverClass: Class<out AppWidgetProvider>,
) {
    object NextClass : FacultyWidget(NextClassWidgetProvider::class.java)
    object TodaySchedule : FacultyWidget(TodayWidgetProvider::class.java)
    object QuickAssistant : FacultyWidget(QuickAssistantWidgetProvider::class.java)

    fun updateAll(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val provider = ComponentName(context, receiverClass)
        val ids = manager.getAppWidgetIds(provider)
        if (ids.isEmpty()) return
        val snapshot = WidgetData.snapshot(context)
        ids.forEach { id -> manager.updateAppWidget(id, buildViews(context, snapshot)) }
    }

    fun buildViews(context: Context, snapshot: WidgetData.WidgetSnapshot): RemoteViews =
        when (this) {
            NextClass -> buildNextClass(context, snapshot)
            TodaySchedule -> buildToday(context, snapshot)
            QuickAssistant -> buildQuickAssistant(context)
        }

    private fun openIntent(context: Context, route: String, requestCode: Int): PendingIntent =
        PendingIntent.getActivity(
            context,
            requestCode,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("deep_link_route", route)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun baseViews(context: Context, layoutId: Int): RemoteViews =
        RemoteViews(context.packageName, layoutId)

    private fun buildNextClass(context: Context, snapshot: WidgetData.WidgetSnapshot): RemoteViews {
        val views = baseViews(context, R.layout.widget_next_class)
        views.setOnClickPendingIntent(R.id.widget_root, openIntent(context, "timetable", 4101))
        if (snapshot.nextClass == null) {
            views.setTextViewText(R.id.widget_when, "NO TIMETABLE YET")
            views.setTextViewText(R.id.widget_subject, "Add your timetable")
            views.setTextViewText(R.id.widget_detail, "Tap to open the app")
        } else {
            val next = snapshot.nextClass
            views.setTextViewText(R.id.widget_when, next.whenLabel)
            views.setTextViewText(R.id.widget_subject, next.subject)
            views.setTextViewText(R.id.widget_detail, "${next.section} · Room ${next.room}")
        }
        return views
    }

    private fun buildToday(context: Context, snapshot: WidgetData.WidgetSnapshot): RemoteViews {
        val views = baseViews(context, R.layout.widget_today)
        views.setOnClickPendingIntent(R.id.widget_root, openIntent(context, "timetable", 4102))
        if (snapshot.todayClasses.isEmpty()) {
            views.setTextViewText(R.id.widget_today_title, "Today · Schedule clear")
            views.setTextViewText(R.id.widget_today_body, "No classes scheduled for today.")
        } else {
            views.setTextViewText(
                R.id.widget_today_title,
                "Today · ${snapshot.todayClasses.size} class${if (snapshot.todayClasses.size > 1) "es" else ""}",
            )
            views.setTextViewText(
                R.id.widget_today_body,
                snapshot.todayClasses.joinToString("\n") { "${it.timeLabel} — ${it.subject}" },
            )
        }
        return views
    }

    private fun buildQuickAssistant(context: Context): RemoteViews {
        val views = baseViews(context, R.layout.widget_quick_assistant)
        views.setOnClickPendingIntent(R.id.widget_root, openIntent(context, "assistant", 4103))
        return views
    }
}

/** HOME-SCREEN WIDGET: next upcoming class. */
class NextClassWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        FacultyWidget.NextClass.updateAll(context)
    }
}

/** HOME-SCREEN WIDGET: today's schedule list. */
class TodayWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        FacultyWidget.TodaySchedule.updateAll(context)
    }
}

/** HOME-SCREEN WIDGET: one-tap assistant. */
class QuickAssistantWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        FacultyWidget.QuickAssistant.updateAll(context)
    }
}

/** Refreshes every widget when the underlying data changes. */
object WidgetRefresher {
    fun refreshAll(context: Context) {
        FacultyWidget.NextClass.updateAll(context)
        FacultyWidget.TodaySchedule.updateAll(context)
    }
}
