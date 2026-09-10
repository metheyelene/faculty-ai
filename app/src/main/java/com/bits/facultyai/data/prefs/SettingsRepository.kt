package com.bits.facultyai.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.bits.facultyai.ui.theme.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "faculty_settings")

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val onboardingComplete: Boolean = false,
    val memoryEnabled: Boolean = true,
    val aiAccessToNotes: Boolean = true,
    val personalizedNotifications: Boolean = true,
    val greetingStyle: Int = 0, // 0 = Good morning..., 1 = Welcome back..., 2 = Hello...
    val accentPulse: Boolean = true, // kinetic motion enabled
)

class SettingsRepository(private val context: Context) {

    private object Keys {
        val THEME = stringPreferencesKey("theme_mode")
        val ONBOARDING = booleanPreferencesKey("onboarding_complete")
        val MEMORY = booleanPreferencesKey("memory_enabled")
        val AI_NOTES = booleanPreferencesKey("ai_access_notes")
        val PERS_NOTIF = booleanPreferencesKey("personalized_notifications")
        val GREETING = intPreferencesKey("greeting_style")
        val MOTION = booleanPreferencesKey("accent_pulse")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { p ->
        AppSettings(
            themeMode = when (p[Keys.THEME]) {
                "LIGHT" -> ThemeMode.LIGHT
                "DARK" -> ThemeMode.DARK
                else -> ThemeMode.SYSTEM
            },
            onboardingComplete = p[Keys.ONBOARDING] ?: false,
            memoryEnabled = p[Keys.MEMORY] ?: true,
            aiAccessToNotes = p[Keys.AI_NOTES] ?: true,
            personalizedNotifications = p[Keys.PERS_NOTIF] ?: true,
            greetingStyle = p[Keys.GREETING] ?: 0,
            accentPulse = p[Keys.MOTION] ?: true,
        )
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { it[Keys.THEME] = mode.name }
    }

    suspend fun setOnboardingComplete() {
        context.dataStore.edit { it[Keys.ONBOARDING] = true }
    }

    suspend fun setMemoryEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.MEMORY] = enabled }
    }

    suspend fun setAiAccessToNotes(enabled: Boolean) {
        context.dataStore.edit { it[Keys.AI_NOTES] = enabled }
    }

    suspend fun setPersonalizedNotifications(enabled: Boolean) {
        context.dataStore.edit { it[Keys.PERS_NOTIF] = enabled }
    }

    suspend fun setGreetingStyle(style: Int) {
        context.dataStore.edit { it[Keys.GREETING] = style }
    }

    suspend fun setAccentPulse(enabled: Boolean) {
        context.dataStore.edit { it[Keys.MOTION] = enabled }
    }
}
