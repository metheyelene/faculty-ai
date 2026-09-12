package com.bits.facultyai

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bits.facultyai.notifications.SyncScheduler
import com.bits.facultyai.ui.AppViewModel
import com.bits.facultyai.ui.auth.AuthGate
import com.bits.facultyai.ui.auth.AuthViewModel
import com.bits.facultyai.ui.auth.LoginScreen
import com.bits.facultyai.ui.auth.resolveGate
import com.bits.facultyai.ui.navigation.FacultyAINavHost
import com.bits.facultyai.ui.splash.KineticSplashLoading
import com.bits.facultyai.ui.theme.FacultyAITheme
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {

    /** Route a notification deep link wants the app to open, e.g. "timetable". */
    val pendingDeepLink = MutableStateFlow<String?>(null)

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        captureDeepLink(intent)

        // Ask once for the Android 13+ notification permission.
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            val appVm: AppViewModel = viewModel()
            val settings by appVm.settings.collectAsStateWithLifecycle()
            val deepLink by pendingDeepLink.collectAsStateWithLifecycle()
            val mode = settings?.themeMode ?: com.bits.facultyai.ui.theme.ThemeMode.SYSTEM

            FacultyAITheme(mode = mode) {
                val authVm: AuthViewModel = viewModel(factory = AuthViewModel.Factory)
                val authState by authVm.authState.collectAsStateWithLifecycle()
                val gate = resolveGate(settings, authState)

                Crossfade(targetState = gate, animationSpec = tween(300), label = "authGate") { g ->
                    when (g) {
                        AuthGate.LOADING -> KineticSplashLoading() // themed hold, no flash
                        AuthGate.LOGIN -> LoginScreen(
                            vm = authVm,
                            onGuest = { appVm.setGuestMode(true) },
                        )
                        AuthGate.APP -> FacultyAINavHost(
                            settings = settings,
                            deepLinkRoute = deepLink,
                            onDeepLinkHandled = { pendingDeepLink.value = null },
                            onOnboardingComplete = appVm::setOnboardingComplete,
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent?) {
        super.onNewIntent(intent)
        captureDeepLink(intent)
    }

    private fun captureDeepLink(intent: android.content.Intent?) {
        // Only trust deep-link routes from our own notification/alarm pipeline.
        // Any external app can send an explicit intent to the launcher activity,
        // and a foreign route string would make the app navigate on their behalf.
        if (intent == null || intent.`package` != packageName) return
        val route = intent.getStringExtra(SyncScheduler.EXTRA_DEEP_LINK)
        if (!route.isNullOrBlank()) pendingDeepLink.value = route
    }
}
