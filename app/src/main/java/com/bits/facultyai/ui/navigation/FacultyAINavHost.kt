package com.bits.facultyai.ui.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.bits.facultyai.data.prefs.AppSettings
import com.bits.facultyai.ui.theme.KineticMotion
import com.bits.facultyai.ui.theme.KineticSpacing
import com.bits.facultyai.ui.theme.KineticType
import com.bits.facultyai.ui.theme.LocalKineticColors
import com.bits.facultyai.ui.theme.ThemeMode
import com.bits.facultyai.ui.home.HomeScreen
import com.bits.facultyai.ui.timetable.TimetableScreen
import com.bits.facultyai.ui.timetable.TimetableViewModel
import com.bits.facultyai.ui.attendance.AttendanceScreen
import com.bits.facultyai.ui.attendance.AttendanceClassScreen
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
import com.bits.facultyai.ui.onboarding.OnboardingScreen

private object Routes {
    const val ONBOARDING = "onboarding"
    const val HOME = "home"
    const val TIMETABLE = "timetable"
    const val ATTENDANCE = "attendance"
    const val ATTENDANCE_CLASS = "attendance_class/{slotId}"
    const val STUDENTS = "students"
    const val STUDENT_DETAIL = "student_detail/{studentId}"
    const val NOTES = "notes"
    const val NOTE_EDITOR = "note_editor/{noteId}"
    const val TASKS = "tasks"
    const val ASSISTANT = "assistant"
    const val MEMORY = "memory"
    const val MORE = "more"
    const val PROFILE = "profile"
    const val SETTINGS = "settings"
}

fun attendanceRoute(slotId: Long) = "attendance_class/$slotId"
fun noteEditorRoute(noteId: Long) = "note_editor/$noteId"
fun studentDetailRoute(studentId: Long) = "student_detail/$studentId"

private val topLevelRoutes = setOf(Routes.HOME, Routes.TIMETABLE, Routes.ATTENDANCE, Routes.ASSISTANT, Routes.MORE)

@Composable
fun FacultyAINavHost(
    settings: AppSettings?,
    onThemeChange: (ThemeMode) -> Unit,
    onOnboardingComplete: () -> Unit,
) {
    val k = LocalKineticColors.current

    // Wait for settings before deciding the start destination (splash-like hold).
    if (settings == null) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(k.background),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.weight(1f))
            Text(text = "FACULTY AI", style = KineticType.display.copy(fontSize = 40.sp), color = k.foreground)
            Text(text = "LOADING", style = KineticType.label, color = k.accent)
            Spacer(modifier = Modifier.weight(1f))
        }
        return
    }

    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val showBottomBar = currentRoute in topLevelRoutes

    Column(modifier = Modifier.fillMaxSize().background(k.background)) {
        NavHost(
            navController = navController,
            startDestination = if (!settings.onboardingComplete) Routes.ONBOARDING else Routes.HOME,
            modifier = Modifier.weight(1f),
            enterTransition = {
                fadeIn(tween(KineticMotion.MEDIUM_MS)) + slideInHorizontally(tween(KineticMotion.MEDIUM_MS)) { it / 8 }
            },
            exitTransition = { fadeOut(tween(KineticMotion.FAST_MS)) },
            popEnterTransition = { fadeIn(tween(KineticMotion.FAST_MS)) },
            popExitTransition = {
                fadeOut(tween(KineticMotion.FAST_MS)) + slideOutHorizontally(tween(KineticMotion.FAST_MS)) { it / 8 }
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
            composable(Routes.STUDENTS) { StudentsScreen(onNavigate = { navController.navigate(it) }) }
            composable(Routes.STUDENT_DETAIL) { entry ->
                val id = entry.arguments?.getString("studentId")?.toLongOrNull() ?: 0L
                StudentDetailScreen(studentId = id, onBack = { navController.popBackStack() })
            }
            composable(Routes.NOTES) { NotesScreen(onNavigate = { navController.navigate(it) }) }
            composable(Routes.NOTE_EDITOR) { entry ->
                val id = entry.arguments?.getString("noteId")?.toLongOrNull() ?: -1L
                NoteEditorScreen(noteId = id, onBack = { navController.popBackStack() })
            }
            composable(Routes.TASKS) { TasksScreen() }
            composable(Routes.ASSISTANT) { AssistantScreen() }
            composable(Routes.MEMORY) { MemoryScreen() }
            composable(Routes.MORE) { MoreScreen(onNavigate = { navController.navigate(it) }) }
            composable(Routes.PROFILE) { ProfileScreen(onBack = { navController.popBackStack() }) }
            composable(Routes.SETTINGS) {
                SettingsScreen(onBack = { navController.popBackStack() }, onThemeChange = onThemeChange)
            }
        }
        if (showBottomBar) {
            KineticBottomNavigation(navController = navController)
        }
    }
}
