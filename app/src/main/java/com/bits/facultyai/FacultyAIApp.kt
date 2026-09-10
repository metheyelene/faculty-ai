package com.bits.facultyai

import android.app.Application
import com.bits.facultyai.reminders.ReminderScheduler

class FacultyAIApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ReminderScheduler.ensureChannel(this)
    }
}
