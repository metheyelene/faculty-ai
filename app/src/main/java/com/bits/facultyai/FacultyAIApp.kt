package com.bits.facultyai

import android.app.Application
import com.bits.facultyai.data.auth.AuthRepository
import com.bits.facultyai.data.auth.FirebaseAuthSource
import com.bits.facultyai.data.sync.SyncEngine
import com.bits.facultyai.notifications.SyncScheduler

/**
 * App object: owns the process-wide [SyncEngine] and starts it against the
 * auth state flow. The engine self-activates on sign-in and idles in
 * guest/unconfigured mode, so no other component needs to know it exists.
 */
class FacultyAIApp : Application() {

    val authRepository: AuthRepository by lazy {
        AuthRepository(this, FirebaseAuthSource())
    }

    override fun onCreate() {
        super.onCreate()
        SyncScheduler.ensureChannels(this)
        SyncEngine.get(this).start(authRepository.authState)
    }
}
