package com.bits.facultyai

import android.app.Application
import com.bits.facultyai.notifications.SyncScheduler

class FacultyAIApp : Application() {
    override fun onCreate() {
        super.onCreate()
        SyncScheduler.ensureChannels(this)
    }
}
