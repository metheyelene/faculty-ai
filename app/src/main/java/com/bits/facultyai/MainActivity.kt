package com.bits.facultyai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bits.facultyai.ui.AppViewModel
import com.bits.facultyai.ui.navigation.FacultyAINavHost
import com.bits.facultyai.ui.theme.FacultyAITheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val vm: AppViewModel = viewModel()
            val settings by vm.settings.collectAsStateWithLifecycle()
            val mode = settings?.themeMode ?: com.bits.facultyai.ui.theme.ThemeMode.SYSTEM
            FacultyAITheme(mode = mode) {
                FacultyAINavHost(
                    settings = settings,
                    onThemeChange = vm::setTheme,
                    onOnboardingComplete = vm::setOnboardingComplete,
                )
            }
        }
    }
}
