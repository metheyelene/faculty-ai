package com.bits.facultyai.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bits.facultyai.data.local.FacultyDatabase
import com.bits.facultyai.data.prefs.AppSettings
import com.bits.facultyai.data.prefs.SettingsRepository
import com.bits.facultyai.ui.theme.ThemeMode
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * App-root ViewModel: settings, theme, onboarding gate. Data is 100% the
 * faculty member's own — the app never seeds demo content.
 */
class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val settingsRepo = SettingsRepository(application)

    val settings: StateFlow<AppSettings?> = settingsRepo.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun setTheme(mode: ThemeMode) = viewModelScope.launch { settingsRepo.setThemeMode(mode) }
    fun setOnboardingComplete() = viewModelScope.launch { settingsRepo.setOnboardingComplete() }
}
