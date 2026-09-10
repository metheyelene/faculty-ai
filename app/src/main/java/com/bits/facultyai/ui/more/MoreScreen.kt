package com.bits.facultyai.ui.more

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.bits.facultyai.ui.components.KineticDisplayText
import com.bits.facultyai.ui.components.KineticDivider
import com.bits.facultyai.ui.components.KineticSectionHeader
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

        KineticSectionHeader(title = "SYSTEM")
        MoreRow("SETTINGS", "Theme, privacy, data", { onNavigate("settings") })
        Spacer(Modifier.height(KineticSpacing.xl))
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
