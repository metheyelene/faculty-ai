package com.bits.facultyai.ui.students

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bits.facultyai.ui.navigation.studentDetailRoute
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bits.facultyai.ui.components.KineticDisplayText
import com.bits.facultyai.ui.components.KineticDivider
import com.bits.facultyai.ui.components.KineticEmptyState
import com.bits.facultyai.ui.components.KineticSectionHeader
import com.bits.facultyai.ui.components.KineticStat
import com.bits.facultyai.ui.components.KineticTextField
import com.bits.facultyai.ui.theme.KineticSpacing
import com.bits.facultyai.ui.theme.KineticType
import com.bits.facultyai.ui.theme.LocalKineticColors

@Composable
fun StudentsScreen(
    onNavigate: (String) -> Unit,
    vm: StudentsViewModel = viewModel(),
) {
    val k = LocalKineticColors.current
    val students by vm.students.collectAsStateWithLifecycle()
    val search by vm.searchQuery.collectAsStateWithLifecycle()
    val records by vm.attendanceRecords.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(k.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = KineticSpacing.lg),
    ) {
        Spacer(Modifier.height(KineticSpacing.xl))
        KineticDisplayText(text = "STUDENTS", style = KineticType.display.copy(fontSize = 44.sp))
        Row(horizontalArrangement = Arrangement.spacedBy(KineticSpacing.xl)) {
            KineticStat(value = students.size.toString().padStart(2, '0'), label = "TOTAL")
            KineticStat(value = records.size.toString().padStart(2, '0'), label = "SESSIONS")
        }
        Spacer(Modifier.height(KineticSpacing.md))

        KineticTextField(
            value = search,
            onValueChange = vm::setSearch,
            hint = "SEARCH NAME, ROLL NUMBER, SECTION...",
        )
        Spacer(Modifier.height(KineticSpacing.lg))

        if (students.isEmpty()) {
            KineticEmptyState(
                title = "NO STUDENTS FOUND",
                message = "TRY A DIFFERENT SEARCH",
            )
        } else {
            students.forEachIndexed { index, student ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onNavigate(studentDetailRoute(student.id)) }
                        .padding(vertical = KineticSpacing.md),
                ) {
                    Text(
                        text = (index + 1).toString().padStart(2, '0'),
                        style = KineticType.labelBold,
                        color = k.mutedForeground,
                        modifier = Modifier.width(36.dp),
                    )
                    Column(Modifier.weight(1f)) {
                        Text(text = student.name, style = KineticType.bodyMedium, color = k.foreground)
                        Text(
                            text = student.rollNumber + " · " + student.section,
                            style = KineticType.label,
                            color = k.mutedForeground,
                        )
                    }
                    Text(text = "→", style = KineticType.headingSm, color = k.accent)
                }
                KineticDivider()
            }
        }
        Spacer(Modifier.height(96.dp))
    }
}
