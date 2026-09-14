package com.bits.facultyai.ui.more

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewModelScope
import com.bits.facultyai.data.local.FacultyDatabase
import com.bits.facultyai.data.prefs.AppSettings
import com.bits.facultyai.data.prefs.SettingsRepository
import com.bits.facultyai.ui.components.GlassTextField
import com.bits.facultyai.ui.components.GlassTopBar
import com.bits.facultyai.ui.components.KineticButton
import com.bits.facultyai.ui.components.KineticDisplayText
import com.bits.facultyai.data.auth.AuthRepository
import com.bits.facultyai.data.auth.FirebaseAuthSource
import com.bits.facultyai.data.demo.DemoData
import com.bits.facultyai.data.sync.SyncEngine
import com.bits.facultyai.data.sync.SyncStatus
import com.bits.facultyai.data.update.UpdateChecker
import com.bits.facultyai.data.update.UpdateDownloader
import com.bits.facultyai.ui.components.KineticGhostButton
import com.bits.facultyai.ui.components.KineticOutlinedButton
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

// AndroidViewModel (not plain ViewModel): the Android ViewModelFactory only
// instantiates plain ViewModels via a NO-ARG constructor — this class only has
// an (Application) constructor, which crashed the screen on open in release.
class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val settingsRepo = SettingsRepository(application)
    private val db = FacultyDatabase.get(application)
    private val dao = db.facultyDao()
    private val syncEngine = SyncEngine.get(application)

    val settings: StateFlow<AppSettings?> = settingsRepo.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** Live sync status for the Settings row. */
    val syncStatus: StateFlow<SyncStatus> = syncEngine.status
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SyncStatus.Idle)

    /** True when signed in (sync UI is meaningless in guest mode). */
    val signedIn: StateFlow<Boolean> = syncEngine.signedIn
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun setTheme(mode: ThemeMode) = viewModelScope.launch { settingsRepo.setThemeMode(mode) }
    fun setGreetingStyle(style: Int) = viewModelScope.launch { settingsRepo.setGreetingStyle(style) }

    fun syncNow() = viewModelScope.launch { syncEngine.syncNow() }

    // ---- Demo mode (presentations): load/wipe the [DEMO] dataset ----

    /** null = still checking; true = demo rows present. */
    val demoLoaded = MutableStateFlow<Boolean?>(null)
    val demoBusy = MutableStateFlow(false)
    val demoMessage = MutableStateFlow<String?>(null)

    init {
        refreshDemoState()
    }

    fun refreshDemoState() {
        viewModelScope.launch {
            demoLoaded.value =
                db.facultyDao().countDemoSlots() > 0 || db.facultyDao().countDemoStudents() > 0
        }
    }

    fun loadDemoData() {
        if (demoBusy.value) return
        viewModelScope.launch {
            demoBusy.value = true
            demoMessage.value = null
            when (val r = DemoData.load(getApplication())) {
                DemoData.Result.AlreadyLoaded -> demoMessage.value = "Demo data is already loaded."
                DemoData.Result.Loaded -> {
                    demoMessage.value = "Demo data loaded — every screen now has content."
                    syncEngine.syncNow() // signed in: propagate the demo set like any data
                }
                is DemoData.Result.Failed -> demoMessage.value = r.message
            }
            demoBusy.value = false
            refreshDemoState()
        }
    }

    fun wipeDemoData() {
        if (demoBusy.value) return
        viewModelScope.launch {
            demoBusy.value = true
            demoMessage.value = null
            DemoData.wipe(getApplication())
            demoMessage.value = "Demo data removed."
            demoBusy.value = false
            refreshDemoState()
        }
    }

    // ---- Updates (GitHub Releases) ----

    val updateState = MutableStateFlow<UpdateChecker.Result?>(null)
    val updateChecking = MutableStateFlow(false)

    /** Download lifecycle: IDLE → DOWNLOADING(p) → READY | FAILED. */
    data class DownloadState(
        val phase: Phase = Phase.IDLE,
        val progress: Float = 0f,
        val apkFile: java.io.File? = null,
        val error: String? = null,
    ) {
        enum class Phase { IDLE, DOWNLOADING, READY, FAILED }
    }

    val downloadState = MutableStateFlow(DownloadState())

    val appVersion: String = UpdateChecker.installedVersion(application)

    fun checkForUpdates() {
        if (updateChecking.value) return
        viewModelScope.launch {
            updateChecking.value = true
            updateState.value = UpdateChecker.check(getApplication())
            updateChecking.value = false
        }
    }

    /** Downloads the update APK with progress; result surfaces in [downloadState]. */
    fun downloadUpdate(info: UpdateChecker.UpdateInfo) {
        val current = downloadState.value
        if (current.phase == DownloadState.Phase.DOWNLOADING) return
        viewModelScope.launch {
            downloadState.value = DownloadState(DownloadState.Phase.DOWNLOADING)
            val result = UpdateDownloader.download(
                context = getApplication(),
                url = info.downloadUrl,
                version = info.latestVersion,
            ) { p -> downloadState.value = downloadState.value.copy(progress = p) }
            downloadState.value = result.fold(
                onSuccess = { apk ->
                    DownloadState(DownloadState.Phase.READY, apkFile = apk)
                },
                onFailure = { e ->
                    DownloadState(DownloadState.Phase.FAILED, error = e.message ?: "Download failed")
                },
            )
        }
    }

    /** Reusable install intent for a downloaded APK. */
    fun installIntentFor(apk: java.io.File) =
        UpdateDownloader.installIntent(getApplication(), apk)

    /** Erases all user content. The app starts empty — no demo data is restored. */
    fun resetData() = viewModelScope.launch {
        // Signed in: tombstone cloud-known rows first so the reset erases the
        // account's cloud home too and no pull can resurrect the erased data.
        syncEngine.enqueueTombstonesForAllUserData()
        dao.clearTimetable()
        dao.clearTimetableVersions()
        dao.clearTasks()
        dao.clearNotes()
        dao.clearMemories()
        dao.clearAttendance()
        dao.clearAttendanceEntries()
        dao.clearStudents()
        dao.clearAcademicEvents()
        dao.clearEventPhotos()
        dao.clearEventExpenses()
        dao.clearEventCollections()
        dao.clearEvents()
        // Drain runs right away (engine debounces) — the reset propagates.
        syncEngine.syncNow()
    }

    /**
     * Signs out: clears auth state (and the Credential Manager state so the
     * next Google sign-in shows the full chooser) plus all account-owned
     * local data, so a different account never sees the previous one's data.
     */
    /** Guest → login screen. Local data stays until an account claims the device. */
    fun enterSignIn() = viewModelScope.launch { settingsRepo.setGuestMode(false) }

    fun signOut() = viewModelScope.launch {
        syncEngine.signOut()
        AuthRepository(getApplication(), FirebaseAuthSource()).signOut()
        // No other account may ever see this one's data on this device.
        db.clearAllUserData()
        settingsRepo.setGuestMode(false)
    }
}

@Composable
fun SettingsScreen(onBack: () -> Unit, vm: SettingsViewModel = viewModel()) {
    val k = LocalKineticColors.current
    val settings by vm.settings.collectAsStateWithLifecycle()

    // Feature-request / update-launch state — function scope so the
    // LaunchedEffect handlers below can read and reset them.
    var featureText by remember { mutableStateOf("") }
    var launchMail by remember { mutableStateOf(false) }
    var openReleasePage by remember { mutableStateOf(false) }
    var installApk by remember { mutableStateOf<java.io.File?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(k.background)
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(horizontal = KineticSpacing.lg),
    ) {
        Spacer(Modifier.height(KineticSpacing.xl))
        GlassTopBar(title = "SETTINGS", onBack = onBack)
        Spacer(Modifier.height(KineticSpacing.xl))

        KineticSectionHeader(title = "APPEARANCE")
        val currentMode = settings?.themeMode ?: ThemeMode.SYSTEM
        // Horizontally scrollable instead of a fixed Row: at 200% font scale
        // three uppercase chips overflow the screen width.
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(KineticSpacing.sm),
        ) {
            ThemeChip("SYSTEM", currentMode == ThemeMode.SYSTEM) { vm.setTheme(ThemeMode.SYSTEM) }
            ThemeChip("LIGHT", currentMode == ThemeMode.LIGHT) { vm.setTheme(ThemeMode.LIGHT) }
            ThemeChip("DARK", currentMode == ThemeMode.DARK) { vm.setTheme(ThemeMode.DARK) }
        }

        KineticSectionHeader(title = "GREETING STYLE")
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(KineticSpacing.sm),
        ) {
            ThemeChip("TIME-BASED", (settings?.greetingStyle ?: 0) == 0) { vm.setGreetingStyle(0) }
            ThemeChip("WELCOME BACK", settings?.greetingStyle == 1) { vm.setGreetingStyle(1) }
            ThemeChip("HELLO", settings?.greetingStyle == 2) { vm.setGreetingStyle(2) }
        }

        KineticSectionHeader(title = "PRIVACY")
        Text(
            text = "Memory and AI privacy controls live in MY MEMORY.",
            style = KineticType.label.copy(fontSize = 12.sp),
            color = k.mutedForeground,
        )

        KineticSectionHeader(title = "HOME-SCREEN WIDGETS")
        Text(
            text = "Add \"Next Class\", \"Today's Schedule\" and \"Quick Assistant\" from your launcher's widget picker. They update automatically with your timetable and tasks.",
            style = KineticType.label.copy(fontSize = 12.sp),
            color = k.mutedForeground,
        )

        KineticSectionHeader(title = "DATA")
        var confirmReset by remember { mutableStateOf(false) }
        if (confirmReset) {
            Text(
                text = "DELETE ALL TIMETABLE, TASKS, NOTES, MEMORIES, STUDENTS AND ATTENDANCE? THIS CANNOT BE UNDONE.",
                style = KineticType.labelBold,
                color = k.statusError,
            )
            Spacer(Modifier.height(KineticSpacing.sm))
            Row(horizontalArrangement = Arrangement.spacedBy(KineticSpacing.sm)) {
                KineticButton(text = "YES, RESET", onClick = { vm.resetData(); confirmReset = false })
                KineticGhostButton(text = "CANCEL", onClick = { confirmReset = false })
            }
        } else {
            KineticOutlinedButton(text = "RESET APP DATA", onClick = { confirmReset = true })
        }

        Spacer(Modifier.height(KineticSpacing.lg))

        // ---- SYNC (visible only when signed in) ----
        val syncStatus by vm.syncStatus.collectAsStateWithLifecycle()
        val signedIn by vm.signedIn.collectAsStateWithLifecycle()
        if (signedIn) {
            KineticSectionHeader(title = "SYNC")
            Text(
                text = when (val s = syncStatus) {
                    is SyncStatus.Synced ->
                        "All changes saved to your account · " +
                            java.text.SimpleDateFormat("d MMM, HH:mm", java.util.Locale.getDefault())
                                .format(java.util.Date(s.at))
                    SyncStatus.Syncing -> "Syncing…"
                    is SyncStatus.Error -> s.message
                    SyncStatus.Idle -> "Signed in — changes sync automatically"
                },
                style = KineticType.label.copy(fontSize = 12.sp),
                color = if (syncStatus is SyncStatus.Error) k.statusError else k.mutedForeground,
            )
            Spacer(Modifier.height(KineticSpacing.sm))
            KineticGhostButton(text = "SYNC NOW", onClick = { vm.syncNow() })
            Spacer(Modifier.height(KineticSpacing.lg))
        }

        KineticSectionHeader(title = "ACCOUNT")
        if (signedIn) {
            KineticOutlinedButton(
                text = "SIGN OUT",
                onClick = {
                    vm.signOut()
                    onBack()
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = "Signing out clears this account's data on this device so the " +
                    "next sign-in starts fresh. Your onboarding and theme choices stay.",
                style = KineticType.label.copy(fontSize = 12.sp),
                color = k.mutedForeground,
            )
        } else {
            KineticOutlinedButton(
                text = "SIGN IN / CREATE ACCOUNT",
                onClick = {
                    vm.enterSignIn()
                    onBack()
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = "You're using Acadora as a guest on this device only. " +
                    "Sign in to back up and sync your data across devices.",
                style = KineticType.label.copy(fontSize = 12.sp),
                color = k.mutedForeground,
            )
        }

        // ---- DEMO DATA (presentations) ----
        KineticSectionHeader(title = "DEMO DATA")
        val demoLoaded by vm.demoLoaded.collectAsStateWithLifecycle()
        val demoBusy by vm.demoBusy.collectAsStateWithLifecycle()
        val demoMessage by vm.demoMessage.collectAsStateWithLifecycle()
        val demoTitle = "Load demo data for presentations"
        Text(
            text = demoTitle,
            style = KineticType.labelBold,
            color = k.foreground,
        )
        Spacer(Modifier.height(KineticSpacing.xs))
        Text(
            text = "Fills the timetable, two ECE sections, attendance history, notes, tasks " +
                "and an event with finances with clearly marked [DEMO] sample content so every " +
                "screen can be demonstrated. One tap removes it again — real data is never touched.",
            style = KineticType.label.copy(fontSize = 12.sp),
            color = k.mutedForeground,
        )
        Spacer(Modifier.height(KineticSpacing.sm))
        Row(horizontalArrangement = Arrangement.spacedBy(KineticSpacing.sm)) {
            KineticButton(
                text = if (demoLoaded == true) "DEMO DATA LOADED" else "LOAD DEMO DATA",
                onClick = { vm.loadDemoData() },
                enabled = !demoBusy && demoLoaded != true,
            )
            KineticGhostButton(
                text = "REMOVE",
                onClick = { vm.wipeDemoData() },
                enabled = demoLoaded == true && !demoBusy,
                color = k.statusError,
            )
        }
        demoMessage?.let { msg ->
            Spacer(Modifier.height(KineticSpacing.xs))
            Text(
                text = msg,
                style = KineticType.label.copy(fontSize = 12.sp),
                color = k.statusSuccess,
            )
        }

        // ---- UPDATES ----
        KineticSectionHeader(title = "UPDATES")
        val updateState by vm.updateState.collectAsStateWithLifecycle()
        val updateChecking by vm.updateChecking.collectAsStateWithLifecycle()
        val download by vm.downloadState.collectAsStateWithLifecycle()
        Text(
            text = "Acadora v" + vm.appVersion + " — checks GitHub Releases for a newer build.",
            style = KineticType.label.copy(fontSize = 12.sp),
            color = k.mutedForeground,
        )
        Spacer(Modifier.height(KineticSpacing.sm))
        when (val u = updateState) {
            is UpdateChecker.Result.UpdateAvailable -> {
                Text(
                    text = "UPDATE AVAILABLE — v" + u.info.latestVersion,
                    style = KineticType.labelBold,
                    color = k.accent,
                )
                if (u.info.releaseNotes.isNotBlank()) {
                    Spacer(Modifier.height(KineticSpacing.xs))
                    Text(
                        text = u.info.releaseNotes.take(280),
                        style = KineticType.label.copy(fontSize = 12.sp),
                        color = k.mutedForeground,
                    )
                }
                Spacer(Modifier.height(KineticSpacing.sm))
                when (download.phase) {
                    com.bits.facultyai.ui.more.SettingsViewModel.DownloadState.Phase.DOWNLOADING -> {
                        LinearProgressIndicator(
                            progress = { download.progress },
                            modifier = Modifier.fillMaxWidth(),
                            color = k.accent,
                            trackColor = k.glassRegular,
                        )
                        Spacer(Modifier.height(KineticSpacing.xs))
                        Text(
                            text = "DOWNLOADING… " + (download.progress * 100).toInt() + "%",
                            style = KineticType.label.copy(fontSize = 12.sp),
                            color = k.mutedForeground,
                        )
                    }
                    com.bits.facultyai.ui.more.SettingsViewModel.DownloadState.Phase.READY -> {
                        KineticButton(
                            text = "INSTALL NOW",
                            onClick = { installApk = download.apkFile },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            text = "Downloaded. Android will ask to confirm the install — your data stays untouched.",
                            style = KineticType.label.copy(fontSize = 12.sp),
                            color = k.mutedForeground,
                        )
                    }
                    com.bits.facultyai.ui.more.SettingsViewModel.DownloadState.Phase.FAILED -> {
                        KineticButton(
                            text = "RETRY DOWNLOAD",
                            onClick = { vm.downloadUpdate(u.info) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            text = download.error ?: "Download failed",
                            style = KineticType.label.copy(fontSize = 12.sp),
                            color = k.statusError,
                        )
                    }
                    else -> {
                        KineticButton(
                            text = "DOWNLOAD & INSTALL",
                            onClick = { vm.downloadUpdate(u.info) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                Spacer(Modifier.height(KineticSpacing.xs))
                KineticGhostButton(
                    text = "OPEN RELEASE PAGE",
                    onClick = { openReleasePage = true },
                )
            }
            UpdateChecker.Result.UpToDate -> Text(
                text = "You're on the latest version.",
                style = KineticType.label.copy(fontSize = 12.sp),
                color = k.statusSuccess,
            )
            is UpdateChecker.Result.Error -> Text(
                text = u.message,
                style = KineticType.label.copy(fontSize = 12.sp),
                color = k.statusError,
            )
            null -> {}
        }
        if (updateState !is UpdateChecker.Result.UpdateAvailable) {
            Spacer(Modifier.height(KineticSpacing.sm))
            KineticOutlinedButton(
                text = if (updateChecking) "CHECKING…" else "CHECK FOR UPDATES",
                onClick = { vm.checkForUpdates() },
                enabled = !updateChecking,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        // ---- SEND FEEDBACK / FEATURE REQUEST ----
        KineticSectionHeader(title = "SEND FEEDBACK")
        GlassTextField(
            value = featureText,
            onValueChange = { featureText = it },
            hint = "Describe the feature you'd like to see…",
            minLines = 3,
            maxLines = 6,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(KineticSpacing.sm))
        KineticButton(
            text = "SEND TO THE TEAM",
            onClick = { launchMail = true },
            enabled = featureText.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = "Opens your mail app with the request addressed to the Acadora team.",
            style = KineticType.label.copy(fontSize = 12.sp),
            color = k.mutedForeground,
        )

        // ---- ABOUT / CREDITS ----
        KineticSectionHeader(title = "ABOUT")
        Text(
            text = "ACADORA — Faculty AI",
            style = KineticType.heading,
            color = k.foreground,
        )
        Text(
            text = "Version " + vm.appVersion + " · Built for faculty at BITS Vizag",
            style = KineticType.label.copy(fontSize = 12.sp),
            color = k.mutedForeground,
        )
        Spacer(Modifier.height(KineticSpacing.sm))
        Text(text = "FOUNDER & CREATOR", style = KineticType.label.copy(fontSize = 11.sp), color = k.accent)
        Text(
            text = "Mithil Viswas",
            style = KineticType.body,
            color = k.foreground,
        )
        Spacer(Modifier.height(KineticSpacing.xs))
        Text(text = "CO-FOUNDERS", style = KineticType.label.copy(fontSize = 11.sp), color = k.accent)
        Text(
            text = "Rushi Kiran Bhaskar  ·  Dilip Kumar",
            style = KineticType.body,
            color = k.foreground,
        )
        Spacer(Modifier.height(KineticSpacing.xs))
        Text(
            text = "Students of BITS Vizag — ECE, 4th Year, Section B",
            style = KineticType.label.copy(fontSize = 12.sp),
            color = k.mutedForeground,
        )

        Spacer(Modifier.height(KineticSpacing.lg))
        Spacer(Modifier.height(KineticSpacing.xl))
    }

    // Side-effect launches (outside composition-triggering state reads).
    val context = LocalContext.current
    LaunchedEffect(openReleasePage) {
        if (openReleasePage) {
            runCatching {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(UpdateChecker.RELEASES_PAGE)))
            }
            openReleasePage = false
        }
    }
    LaunchedEffect(launchMail) {
        if (launchMail) {
            runCatching {
                context.startActivity(
                    Intent.createChooser(
                        com.bits.facultyai.data.feedback.FeatureRequestSender.intent(context, featureText),
                        "Send feature request",
                    ),
                )
            }
            launchMail = false
            featureText = ""
        }
    }
    LaunchedEffect(installApk) {
        installApk?.let { apk ->
            runCatching { context.startActivity(vm.installIntentFor(apk)) }
                .onFailure {
                    // No installer handler (rare) — fall back to the release page.
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(UpdateChecker.RELEASES_PAGE))) }
                }
            installApk = null
        }
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
