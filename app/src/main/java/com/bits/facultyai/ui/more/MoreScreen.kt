package com.bits.facultyai.ui.more

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bits.facultyai.ui.components.GlassCard
import com.bits.facultyai.ui.components.GlassDialogSurface
import com.bits.facultyai.ui.components.GlassStrength
import com.bits.facultyai.ui.components.KineticButton
import com.bits.facultyai.ui.components.KineticGhostButton
import com.bits.facultyai.ui.components.KineticDisplayText
import com.bits.facultyai.ui.components.KineticDivider
import com.bits.facultyai.ui.components.KineticSectionHeader
import com.bits.facultyai.ui.navigation.KineticBottomNavigation
import com.bits.facultyai.ui.theme.KineticSpacing
import com.bits.facultyai.ui.theme.KineticType
import com.bits.facultyai.ui.theme.LocalKineticColors

@Composable
fun MoreScreen(onNavigate: (String) -> Unit) {
    val k = LocalKineticColors.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(k.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = KineticSpacing.lg),
    ) {
        Spacer(Modifier.height(KineticSpacing.xl))
        KineticDisplayText(text = "MY SPACE", style = KineticType.display.copy(fontSize = 44.sp))
        Spacer(Modifier.height(KineticSpacing.xl))

        PresentationModeCard()

        KineticSectionHeader(title = "PERSONAL")
        MoreRow("MY PROFILE", "Name, designation, subjects", { onNavigate("profile") })
        MoreRow("MY MEMORY", "What your assistant remembers", { onNavigate("memory") })
        MoreRow("MY NOTES", "Your knowledge space", { onNavigate("notes") })
        MoreRow("MY TASKS", "Reminders and to-dos", { onNavigate("tasks") })

        KineticSectionHeader(title = "ACADEMIC")
        MoreRow("MY TIMETABLE", "Weekly schedule", { onNavigate("timetable") })
        MoreRow("ACADEMIC CALENDAR", "Events, exams and deadlines", { onNavigate("calendar") })
        MoreRow("ATTENDANCE", "Mark and review sessions", { onNavigate("attendance") })
        MoreRow("STUDENTS", "Sections and rosters", { onNavigate("students") })

        KineticSectionHeader(title = "EVENTS")
        MoreRow("EVENT MANAGER", "Fests, budgets, photos and finances", { onNavigate("events") })

        KineticSectionHeader(title = "SYSTEM")
        MoreRow("SETTINGS", "Theme, privacy, data", { onNavigate("settings") })
        // Clears the floating glass dock + system navigation area.
        Spacer(Modifier.height(KineticBottomNavigation.bottomClearance()))
    }
}

/**
 * One-tap presentation mode: loads the marked demo dataset (now including
 * calendar exams/holidays) and tears it down again — from the home of the
 * app, without digging through Settings. The destructive END action needs an
 * explicit confirmation; LOAD is idempotent and reversible, so it runs
 * directly. State/busy/message live in SettingsViewModel so this card and
 * the Settings section can never disagree.
 */
@Composable
private fun PresentationModeCard() {
    val k = LocalKineticColors.current
    val vm: SettingsViewModel = viewModel()
    val demoLoaded by vm.demoLoaded.collectAsStateWithLifecycle()
    val demoBusy by vm.demoBusy.collectAsStateWithLifecycle()
    val demoMessage by vm.demoMessage.collectAsStateWithLifecycle()
    var confirmEnd by remember { mutableStateOf(false) }

    GlassCard(strength = GlassStrength.THIN) {
        Text(
            text = "PRESENTATION MODE",
            style = KineticType.labelBold,
            color = k.accent,
        )
        Spacer(Modifier.height(KineticSpacing.xs))
        Text(
            text = "One tap fills every screen with marked sample data — timetable, " +
                "students, attendance, notes, tasks, calendar exams and an event budget — " +
                "and one tap removes it all. Real data is never touched.",
            style = KineticType.label.copy(fontSize = 12.sp),
            color = k.mutedForeground,
        )
        Spacer(Modifier.height(KineticSpacing.sm))
        Row(horizontalArrangement = Arrangement.spacedBy(KineticSpacing.sm)) {
            KineticButton(
                text = if (demoLoaded == true) "DEMO ACTIVE" else "START DEMO",
                onClick = { vm.loadDemoData() },
                enabled = !demoBusy && demoLoaded != true,
                modifier = Modifier.weight(1f),
            )
            KineticGhostButton(
                text = "END DEMO",
                onClick = { confirmEnd = true },
                enabled = demoLoaded == true && !demoBusy,
                color = k.statusError,
            )
        }
        demoMessage?.let { msg ->
            Spacer(Modifier.height(KineticSpacing.xs))
            Text(
                text = msg,
                style = KineticType.label.copy(fontSize = 12.sp),
                color = if (msg.startsWith("Demo")) k.statusSuccess else k.statusError,
            )
        }
    }

    if (confirmEnd) {
        Dialog(onDismissRequest = { confirmEnd = false }) {
            GlassDialogSurface {
                Text("END PRESENTATION MODE?", style = KineticType.heading, color = k.foreground)
                Spacer(Modifier.height(KineticSpacing.sm))
                Text(
                    text = "All [DEMO] sample content will be removed from this device. " +
                        "Anything you created yourself stays.",
                    style = KineticType.label.copy(fontSize = 12.sp),
                    color = k.mutedForeground,
                )
                Spacer(Modifier.height(KineticSpacing.lg))
                Row(horizontalArrangement = Arrangement.spacedBy(KineticSpacing.sm)) {
                    KineticGhostButton(
                        text = "KEEP",
                        onClick = { confirmEnd = false },
                        modifier = Modifier.weight(1f),
                    )
                    KineticButton(
                        text = "REMOVE DEMO DATA",
                        onClick = {
                            confirmEnd = false
                            vm.wipeDemoData()
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun MoreRow(title: String, subtitle: String, onClick: () -> Unit) {
    val k = LocalKineticColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = KineticSpacing.md),
    ) {
        Column(Modifier.weight(1f)) {
            Text(text = title, style = KineticType.bodyMedium, color = k.foreground)
            Text(text = subtitle, style = KineticType.label, color = k.mutedForeground)
        }
        Text(text = "→", style = KineticType.headingSm, color = k.accent)
    }
    KineticDivider()
}
