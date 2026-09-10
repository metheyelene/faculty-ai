package com.bits.facultyai.ui.more

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewModelScope
import com.bits.facultyai.data.local.FacultyDatabase
import com.bits.facultyai.data.Seeder
import com.bits.facultyai.data.prefs.AppSettings
import com.bits.facultyai.data.prefs.SettingsRepository
import com.bits.facultyai.ui.components.KineticDisplayText
import com.bits.facultyai.ui.components.KineticSectionHeader
import com.bits.facultyai.ui.theme.KineticBorder
import com.bits.facultyai.ui.theme.KineticSpacing
import com.bits.facultyai.ui.theme.KineticType
import com.bits.facultyai.ui.theme.LocalKineticColors
import com.bits.facultyai.ui.theme.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : ViewModel() {
    private val settingsRepo = SettingsRepository(application)
    private val dao = FacultyDatabase.get(application).facultyDao()

    val settings: StateFlow<AppSettings?> = settingsRepo.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun setTheme(mode: ThemeMode) = viewModelScope.launch { settingsRepo.setThemeMode(mode) }
    fun setGreetingStyle(style: Int) = viewModelScope.launch { settingsRepo.setGreetingStyle(style) }

    /** Clears all user-created content so the seeder restores the demo data set. */
    fun resetData() = viewModelScope.launch {
        dao.clearTimetable()
        dao.clearTasks()
        dao.clearNotes()
        dao.clearMemories()
        dao.clearAttendance()
        dao.clearAttendanceEntries()
        dao.clearStudents()
        Seeder.seedIfFirstRun(dao)
    }
}

@Composable
fun SettingsScreen(onBack: () -> Unit, onThemeChange: (ThemeMode) -> Unit, vm: SettingsViewModel = viewModel()) {
    val k = LocalKineticColors.current
    val settings by vm.settings.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(k.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = KineticSpacing.lg),
    ) {
        Spacer(Modifier.height(KineticSpacing.xl))
        KineticDisplayText(text = "SETTINGS", style = KineticType.display.copy(fontSize = 44.sp))
        Spacer(Modifier.height(KineticSpacing.xl))

        KineticSectionHeader(title = "APPEARANCE")
        Row(horizontalArrangement = Arrangement.spacedBy(KineticSpacing.sm)) {
            ThemeChip("SYSTEM", settings?.themeMode == ThemeMode.SYSTEM) { onThemeChange(ThemeMode.SYSTEM); vm.setTheme(ThemeMode.SYSTEM) }
            ThemeChip("LIGHT", settings?.themeMode == ThemeMode.LIGHT) { onThemeChange(ThemeMode.LIGHT); vm.setTheme(ThemeMode.LIGHT) }
            ThemeChip("DARK", settings?.themeMode == ThemeMode.DARK) { onThemeChange(ThemeMode.DARK); vm.setTheme(ThemeMode.DARK) }
        }

        KineticSectionHeader(title = "GREETING STYLE")
        Row(horizontalArrangement = Arrangement.spacedBy(KineticSpacing.sm)) {
            ThemeChip("TIME-BASED", (settings?.greetingStyle ?: 0) == 0) { vm.setGreetingStyle(0) }
            ThemeChip("WELCOME BACK", (settings?.greetingStyle ?: 0) == 1) { vm.setGreetingStyle(1) }
            ThemeChip("HELLO", (settings?.greetingStyle ?: 0) == 2) { vm.setGreetingStyle(2) }
        }

        KineticSectionHeader(title = "PRIVACY")
        Text(
            text = "Memory and AI privacy controls live in MY MEMORY.",
            style = KineticType.label,
            color = k.mutedForeground,
        )

        KineticSectionHeader(title = "DATA")
        var confirmReset by remember { mutableStateOf(false) }
        if (confirmReset) {
            Text(text = "DELETE ALL TIMETABLE, TASKS, NOTES, MEMORIES AND ATTENDANCE?", style = KineticType.labelBold, color = k.statusError)
            Spacer(Modifier.height(KineticSpacing.sm))
            Row(horizontalArrangement = Arrangement.spacedBy(KineticSpacing.sm)) {
                com.bits.facultyai.ui.components.KineticButton(text = "YES, RESET", onClick = { vm.resetData(); confirmReset = false })
                com.bits.facultyai.ui.components.KineticGhostButton(text = "CANCEL", onClick = { confirmReset = false })
            }
        } else {
            com.bits.facultyai.ui.components.KineticOutlinedButton(text = "RESET DEMO DATA", onClick = { confirmReset = true })
        }

        Spacer(Modifier.height(KineticSpacing.lg))
        com.bits.facultyai.ui.components.KineticGhostButton(text = "← BACK", onClick = onBack)
        Spacer(Modifier.height(96.dp))
    }
}

@Composable
private fun ThemeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val k = LocalKineticColors.current
    Box(
        modifier = Modifier
            .border(if (selected) KineticBorder.heavy else KineticBorder.hair, if (selected) k.accent else k.border)
            .background(if (selected) k.accent else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = KineticSpacing.md, vertical = KineticSpacing.sm),
    ) {
        Text(
            text = label,
            style = KineticType.labelBold,
            color = if (selected) k.accentForeground else k.mutedForeground,
        )
    }
}
