package com.bits.facultyai.ui.navigation

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.haze
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.bits.facultyai.data.prefs.AppSettings
import com.bits.facultyai.ui.calendar.CalendarScreen
import com.bits.facultyai.ui.components.rememberBlurSupported
import com.bits.facultyai.ui.theme.KineticMotion
import com.bits.facultyai.ui.theme.KineticType
import com.bits.facultyai.ui.theme.LocalKineticColors
import com.bits.facultyai.ui.theme.rememberReducedMotion
import dev.chrisbanes.haze.HazeState
import com.bits.facultyai.ui.home.HomeScreen
import com.bits.facultyai.ui.timetable.TimetableScreen
import com.bits.facultyai.ui.timetable.TimetableViewModel
import com.bits.facultyai.ui.attendance.AttendanceScreen
import com.bits.facultyai.ui.attendance.AttendanceClassScreen
import com.bits.facultyai.ui.attendance.monthly.MonthlyAttendanceScreen
import com.bits.facultyai.ui.attendance.monthly.MonthlyImportScreen
import com.bits.facultyai.ui.attendance.monthly.StudentMonthlyScreen
import com.bits.facultyai.ui.students.StudentsScreen
import com.bits.facultyai.ui.students.StudentDetailScreen
import com.bits.facultyai.ui.notes.NotesScreen
import com.bits.facultyai.ui.notes.NoteEditorScreen
import com.bits.facultyai.ui.tasks.TasksScreen
import com.bits.facultyai.ui.assistant.AssistantScreen
import com.bits.facultyai.ui.memory.MemoryScreen
import com.bits.facultyai.ui.more.MoreScreen
import com.bits.facultyai.ui.more.ProfileScreen
import com.bits.facultyai.ui.more.SettingsScreen
import com.bits.facultyai.ui.events.EventDetailScreen
import com.bits.facultyai.ui.events.EventEditorScreen
import com.bits.facultyai.ui.events.EventsScreen
import com.bits.facultyai.ui.onboarding.OnboardingScreen
import com.bits.facultyai.ui.splash.KineticSplashOverlay
import com.bits.facultyai.ui.splash.KineticSplashLoading

private object Routes {
    const val ONBOARDING = "onboarding"
    const val HOME = "home"
    const val TIMETABLE = "timetable"
    const val ATTENDANCE = "attendance"
    const val ATTENDANCE_CLASS = "attendance_class/{slotId}"
    const val MONTHLY_ATTENDANCE = "monthly_attendance"
    const val MONTHLY_IMPORT = "monthly_import/{payload}"
    const val STUDENT_MONTHLY = "student_monthly/{studentId}/{monthIso}"
    const val STUDENTS = "students"
    const val STUDENT_DETAIL = "student_detail/{studentId}"
    const val NOTES = "notes"
    const val NOTE_EDITOR = "note_editor/{noteId}"
    const val TASKS = "tasks"
    const val CALENDAR = "calendar"
    const val ASSISTANT = "assistant"
    const val MEMORY = "memory"
    const val MORE = "more"
    const val PROFILE = "profile"
    const val SETTINGS = "settings"
    const val EVENTS = "events"
    const val EVENT_DETAIL = "event_detail/{eventId}"
    const val EVENT_EDITOR = "event_editor/{eventId}"
}

fun attendanceRoute(slotId: Long) = "attendance_class/$slotId"
fun monthlyAttendanceRoute() = Routes.MONTHLY_ATTENDANCE
fun monthlyImportRoute(payload: String) = "monthly_import/" + android.net.Uri.encode(payload)
fun studentMonthlyRoute(studentId: Long, monthIso: String) = "student_monthly/$studentId/" + android.net.Uri.encode(monthIso)
fun noteEditorRoute(noteId: Long) = "note_editor/$noteId"
fun studentDetailRoute(studentId: Long) = "student_detail/$studentId"
fun eventDetailRoute(eventId: Long) = "event_detail/$eventId"
fun eventEditorRoute(eventId: Long) = "event_editor/$eventId"

private val topLevelRoutes = setOf(Routes.HOME, Routes.TIMETABLE, Routes.ATTENDANCE, Routes.ASSISTANT, Routes.MORE)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FacultyAINavHost(
    settings: AppSettings?,
    deepLinkRoute: String? = null,
    onDeepLinkHandled: () -> Unit = {},
    onOnboardingComplete: () -> Unit,
) {
    val k = LocalKineticColors.current
    // Hoisted splash animatables: remembered at this scope, they survive the
    // settings-load boundary, so the intro never replays when data arrives.
    val splashIntro = remember { androidx.compose.animation.core.Animatable(0f) }
    val splashExit = remember { androidx.compose.animation.core.Animatable(0f) }
    val splashContentIn = remember { androidx.compose.animation.core.Animatable(0f) }
    var splashGone by remember { mutableStateOf(false) }
    val reducedMotion = rememberReducedMotion()

    // Wait for settings before deciding the start destination. The liquid
    // glass splash owns this window — no static loading text.
    if (settings == null) {
        KineticSplashOverlay(
            intro = splashIntro,
            exit = splashExit,
            contentIn = splashContentIn,
            settingsReady = false,
            reducedMotion = reducedMotion,
            onExitComplete = { splashGone = true },
        )
        return
    }

    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val showBottomBar = currentRoute in topLevelRoutes
    // The dock steps aside while the keyboard is up — it must never cover
    // the assistant composer or any active input.
    val imeVisible = WindowInsets.isImeVisible
    val showDock = showBottomBar && !imeVisible
    // Dock enters by rising + fading; exits the same way. Reduced motion:
    // no transition, instant presence.
    val dockProgress by animateFloatAsState(
        targetValue = if (showDock) 1f else 0f,
        animationSpec = if (reducedMotion) snap() else tween(KineticMotion.MEDIUM_MS, easing = KineticMotion.easeOut),
        label = "dockProgress",
    )

    // Liquid Glass: the NavHost is the blur SOURCE; the floating dock is the
    // hazeChild that blurs this content via RenderEffect (Android 12+).
    // PERFORMANCE GATE: the source layer is only attached when something can
    // actually consume the blur — a top-level screen is visible AND the dock
    // is shown (keyboard closed, motion allowed, API 31+). On secondary
    // screens, dialogs, onboarding and pre-Android-12 devices the NavHost
    // renders with zero blur overhead.
    val hazeState = remember { HazeState() }
    val blurCapable = rememberBlurSupported()
    val hazeActive = showDock && blurCapable
    var lastHazeActive by remember { mutableStateOf(hazeActive) }
    LaunchedEffect(hazeActive) { lastHazeActive = hazeActive }
    val effectiveHaze = hazeActive || lastHazeActive

    // Notification deep links: navigate once when a link arrives.
    LaunchedEffect(deepLinkRoute) {
        when (deepLinkRoute) {
            "timetable" -> navController.navigate(Routes.TIMETABLE) { launchSingleTop = true }
            "tasks" -> navController.navigate(Routes.TASKS) { launchSingleTop = true }
            "calendar" -> navController.navigate(Routes.CALENDAR) { launchSingleTop = true }
        }
        if (deepLinkRoute != null) onDeepLinkHandled()
    }

    // Root layout: a Box so the dock can genuinely FLOAT over the NavHost
    // (screens scroll underneath it — required for the backdrop blur to have
    // moving content to diffuse).
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(k.background)
            // Global safe areas, applied ONCE at the root: every screen (with
            // or without its own top bar) clears the status bar; the whole
            // box lifts above the keyboard instead of colliding with it.
            .statusBarsPadding()
            .imePadding()
    ) {
        NavHost(
            navController = navController,
            startDestination = if (!settings.onboardingComplete) Routes.ONBOARDING else Routes.HOME,
            modifier = Modifier
                .matchParentSize()
                // Splash handoff: the real content rises from a whisper of
                // scale as the splash dissolves. graphicsLayer reads state so
                // only the draw phase invalidates (no recomposition).
                .graphicsLayer {
                    alpha = splashContentIn.value
                    val s = 0.985f + 0.015f * splashContentIn.value
                    scaleX = s
                    scaleY = s
                }
                .then(if (hazeActive) Modifier.haze(hazeState) else Modifier),
            // Spatial continuity: forward slides left, back slides right,
            // always with a soft cross-fade. Fast, Apple-like ease-out.
            enterTransition = {
                fadeIn(tween(KineticMotion.MEDIUM_MS, easing = KineticMotion.easeOut)) +
                    slideInHorizontally(tween(KineticMotion.MEDIUM_MS, easing = KineticMotion.easeOut)) { it / 10 }
            },
            exitTransition = {
                fadeOut(tween(KineticMotion.FAST_MS, easing = KineticMotion.standard)) +
                    slideOutHorizontally(tween(KineticMotion.FAST_MS, easing = KineticMotion.standard)) { -it / 14 }
            },
            popEnterTransition = {
                fadeIn(tween(KineticMotion.MEDIUM_MS, easing = KineticMotion.easeOut)) +
                    slideInHorizontally(tween(KineticMotion.MEDIUM_MS, easing = KineticMotion.easeOut)) { -it / 10 }
            },
            popExitTransition = {
                fadeOut(tween(KineticMotion.FAST_MS, easing = KineticMotion.standard)) +
                    slideOutHorizontally(tween(KineticMotion.FAST_MS, easing = KineticMotion.standard)) { it / 14 }
            },
        ) {
            composable(Routes.ONBOARDING) {
                OnboardingScreen(onComplete = onOnboardingComplete)
            }
            composable(Routes.HOME) { HomeScreen(onNavigate = { route -> navController.navigate(route) }) }
            composable(Routes.TIMETABLE) {
                val vm: TimetableViewModel = viewModel()
                LaunchedEffect(Unit) {
                    vm.attendanceEvent.collect { slotId -> navController.navigate(attendanceRoute(slotId)) }
                }
                TimetableScreen(vm = vm)
            }
            composable(Routes.ATTENDANCE) { AttendanceScreen(onNavigate = { navController.navigate(it) }) }
            composable(Routes.ATTENDANCE_CLASS) { entry ->
                val slotId = entry.arguments?.getString("slotId")?.toLongOrNull() ?: 0L
                AttendanceClassScreen(slotId = slotId, onDone = { navController.popBackStack() })
            }
            composable(Routes.MONTHLY_ATTENDANCE) {
                MonthlyAttendanceScreen(
                    onOpenStudent = { studentId, monthIso -> navController.navigate(studentMonthlyRoute(studentId, monthIso)) },
                    onImport = { payload -> navController.navigate(monthlyImportRoute(payload)) },
                )
            }
            composable(Routes.MONTHLY_IMPORT) { entry ->
                val payload = entry.arguments?.getString("payload").orEmpty()
                MonthlyImportScreen(payload = payload, onDone = { navController.popBackStack() })
            }
            composable(Routes.STUDENT_MONTHLY) { entry ->
                val studentId = entry.arguments?.getString("studentId")?.toLongOrNull() ?: 0L
                val monthIso = entry.arguments?.getString("monthIso").orEmpty()
                StudentMonthlyScreen(studentId = studentId, monthIso = monthIso)
            }
            composable(Routes.STUDENTS) { StudentsScreen(onNavigate = { navController.navigate(it) }) }
            composable(Routes.STUDENT_DETAIL) { entry ->
                val id = entry.arguments?.getString("studentId")?.toLongOrNull() ?: 0L
                StudentDetailScreen(
                    studentId = id,
                    onBack = { navController.popBackStack() },
                    onNavigateMonthly = { sid ->
                        navController.navigate(studentMonthlyRoute(sid, java.time.YearMonth.now().toString()))
                    },
                )
            }
            composable(Routes.NOTES) {
                NotesScreen(
                    onNavigate = { navController.navigate(it) },
                    onBack = { navController.popBackStack() },
                )
            }
            composable(Routes.NOTE_EDITOR) { entry ->
                val id = entry.arguments?.getString("noteId")?.toLongOrNull() ?: -1L
                NoteEditorScreen(noteId = id, onBack = { navController.popBackStack() })
            }
            composable(Routes.TASKS) { TasksScreen(onBack = { navController.popBackStack() }) }
            composable(Routes.CALENDAR) {
                CalendarScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.ASSISTANT) { AssistantScreen() }
            composable(Routes.MEMORY) { MemoryScreen(onBack = { navController.popBackStack() }) }
            composable(Routes.MORE) { MoreScreen(onNavigate = { navController.navigate(it) }) }
            composable(Routes.PROFILE) { ProfileScreen(onBack = { navController.popBackStack() }) }
            composable(Routes.SETTINGS) {
                SettingsScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.EVENTS) {
                EventsScreen(
                    onBack = { navController.popBackStack() },
                    onOpenEvent = { id ->
                        if (id > 0) navController.navigate(eventDetailRoute(id))
                        else navController.navigate(eventEditorRoute(0L))
                    },
                )
            }
            composable(Routes.EVENT_DETAIL) { entry ->
                val id = entry.arguments?.getString("eventId")?.toLongOrNull() ?: 0L
                EventDetailScreen(
                    onBack = { navController.popBackStack() },
                    onEditEvent = { editId -> navController.navigate(eventEditorRoute(editId)) },
                )
            }
            composable(Routes.EVENT_EDITOR) { entry ->
                val id = entry.arguments?.getString("eventId")?.toLongOrNull() ?: 0L
                EventEditorScreen(
                    onBack = { navController.popBackStack() },
                    onSaved = { savedId ->
                        if (id > 0) {
                            navController.popBackStack()
                        } else {
                            navController.navigate(eventDetailRoute(savedId)) {
                                popUpTo(Routes.EVENTS)
                            }
                        }
                    },
                )
            }
        }

        // The floating dock is the ONLY bottom-inset consumer in the app. It
        // overlays the NavHost, so screens genuinely scroll under it and the
        // RenderEffect backdrop blur has content to diffuse. Every top-level
        // screen ends with KineticBottomNavigation.bottomClearance().
        if (dockProgress > 0.01f) {
            KineticBottomNavigation(
                navController = navController,
                // Only hand the blur state to blur-capable devices; everyone
                // else gets the classic translucent glass fallback surface.
                hazeState = if (blurCapable) hazeState else null,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .graphicsLayer {
                        alpha = dockProgress
                        translationY = (1f - dockProgress) * 40f
                    },
            )
        }

        // Liquid glass splash sits ABOVE everything and dissolves into the
        // app. Removed from composition once fully gone (no cost afterwards).
        if (!splashGone) {
            KineticSplashOverlay(
                intro = splashIntro,
                exit = splashExit,
                contentIn = splashContentIn,
                settingsReady = true,
                reducedMotion = reducedMotion,
                onExitComplete = { splashGone = true },
            )
        }
    }
}
