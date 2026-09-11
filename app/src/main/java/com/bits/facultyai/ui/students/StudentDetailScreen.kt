package com.bits.facultyai.ui.students

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import com.bits.facultyai.data.local.StudentEntity
import com.bits.facultyai.ui.components.GlassTopBar
import com.bits.facultyai.ui.components.KineticDisplayText
import com.bits.facultyai.ui.components.KineticLoadingState
import com.bits.facultyai.ui.components.KineticSectionHeader
import com.bits.facultyai.ui.components.KineticStat
import com.bits.facultyai.ui.theme.KineticSpacing
import com.bits.facultyai.ui.theme.KineticType
import com.bits.facultyai.ui.theme.LocalKineticColors
import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bits.facultyai.data.local.AttendanceEntryEntity
import com.bits.facultyai.data.local.FacultyDatabase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import androidx.compose.ui.unit.dp

class StudentDetailViewModel(savedStateHandle: SavedStateHandle, application: Application) : ViewModel() {
    private val dao = FacultyDatabase.get(application).facultyDao()
    val studentId: Long = savedStateHandle.get<String>("studentId")?.toLongOrNull() ?: 0L

    private val studentFlow = MutableStateFlow<StudentEntity?>(null)
    val student: StateFlow<StudentEntity?> = studentFlow

    val entries = dao.observeAttendanceEntriesForStudent(studentId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch { studentFlow.value = dao.getStudent(studentId) }
    }
}

@Composable
fun StudentDetailScreen(
    studentId: Long,
    onBack: () -> Unit,
    vm: StudentDetailViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                StudentDetailViewModel(
                    createSavedStateHandle(),
                    checkNotNull(this[APPLICATION_KEY]),
                )
            }
        },
    ),
) {
    val k = LocalKineticColors.current
    val student by vm.student.collectAsStateWithLifecycle()
    val entries by vm.entries.collectAsStateWithLifecycle()
    // LATE counts as attended for the percentage; totals show the full picture.
    val attended = entries.count { it.status == "PRESENT" || it.status == "LATE" }
    val total = entries.size
    val pct = if (total > 0) (attended * 100 / total) else 0
    val loaded = student != null

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(k.background)
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(horizontal = KineticSpacing.lg),
    ) {
        Spacer(Modifier.height(KineticSpacing.xl))
        if (!loaded) {
            GlassTopBar(title = "STUDENT", onBack = onBack)
            KineticLoadingState(label = "LOADING STUDENT")
            Spacer(Modifier.height(KineticSpacing.xl))
            return@Column
        }
        GlassTopBar(title = student?.name ?: "STUDENT", onBack = onBack)
        Spacer(Modifier.height(KineticSpacing.lg))

        KineticSectionHeader(title = "ATTENDANCE")
        Row(horizontalArrangement = Arrangement.spacedBy(KineticSpacing.xl)) {
            KineticStat(value = "$pct%", label = "ATTENDED")
            KineticStat(value = "$total", label = "SESSIONS")
        }

        KineticSectionHeader(title = "INFO")
        InfoRow("SECTION", student?.section ?: "—")
        InfoRow("YEAR", student?.year?.toString() ?: "—")
        InfoRow("PROGRAM", student?.degree ?: "—")
        Spacer(Modifier.height(KineticSpacing.xl))
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    val k = LocalKineticColors.current
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = KineticSpacing.sm)) {
        Text(text = label, style = KineticType.label, color = k.mutedForeground, modifier = Modifier.width(120.dp))
        Text(text = value, style = KineticType.bodyMedium, color = k.foreground)
    }
}
