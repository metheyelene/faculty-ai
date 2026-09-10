package com.bits.facultyai.ui.more

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.foundation.clickable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewModelScope
import com.bits.facultyai.data.local.FacultyDatabase
import com.bits.facultyai.data.local.FacultyProfileEntity
import com.bits.facultyai.ui.components.KineticDisplayText
import com.bits.facultyai.ui.components.KineticDivider
import com.bits.facultyai.ui.components.KineticSectionHeader
import com.bits.facultyai.ui.theme.KineticSpacing
import com.bits.facultyai.ui.theme.KineticType
import com.bits.facultyai.ui.theme.LocalKineticColors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class ProfileViewModel(application: Application) : ViewModel() {
    private val dao = FacultyDatabase.get(application).facultyDao()
    private val _profile = MutableStateFlow<FacultyProfileEntity?>(null)
    val profile: StateFlow<FacultyProfileEntity?> = _profile

    init {
        viewModelScope.launch { _profile.value = dao.getProfile() }
    }

    fun saveEditableFields(fullName: String, preferredName: String, designation: String, department: String) {
        viewModelScope.launch {
            val p = _profile.value ?: return@launch
            dao.upsertProfile(
                p.copy(
                    fullName = fullName.ifBlank { p.fullName },
                    preferredName = preferredName.ifBlank { p.preferredName },
                    designation = designation.ifBlank { p.designation },
                    department = department.ifBlank { p.department },
                    updatedAt = System.currentTimeMillis(),
                )
            )
            _profile.value = dao.getProfile()
        }
    }
}

@Composable
fun ProfileScreen(onBack: () -> Unit, vm: ProfileViewModel = viewModel()) {
    val k = LocalKineticColors.current
    val profile by vm.profile.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf(false) }
    var fullName by remember(profile) { mutableStateOf(profile?.fullName ?: "") }
    var preferredName by remember(profile) { mutableStateOf(profile?.preferredName ?: "") }
    var designation by remember(profile) { mutableStateOf(profile?.designation ?: "") }
    var department by remember(profile) { mutableStateOf(profile?.department ?: "") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(k.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = KineticSpacing.lg),
    ) {
        Spacer(Modifier.height(KineticSpacing.xl))
        Row(verticalAlignment = Alignment.CenterVertically) {
            com.bits.facultyai.ui.components.KineticGhostButton(text = "← BACK", onClick = onBack)
            Spacer(Modifier.weight(1f))
            Text(
                text = if (editing) "EDITING" else "EDIT",
                style = KineticType.labelBold,
                color = k.accent,
                modifier = Modifier.clickable { editing = !editing },
            )
        }
        Spacer(Modifier.height(KineticSpacing.lg))

        // Identity block
        Box(
            modifier = Modifier
                .size(72.dp)
                .background(k.accent),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = (profile?.preferredName?.take(1) ?: "?").uppercase(),
                style = KineticType.display.copy(fontSize = 32.sp),
                color = k.accentForeground,
            )
        }
        Spacer(Modifier.height(KineticSpacing.md))
        KineticDisplayText(text = profile?.fullName ?: "FACULTY", style = KineticType.display.copy(fontSize = 40.sp))
        Text(
            text = (profile?.designation ?: "") + " · " + (profile?.department ?: ""),
            style = KineticType.labelBold,
            color = k.accent,
        )
        Spacer(Modifier.height(KineticSpacing.xl))

        if (editing) {
            com.bits.facultyai.ui.components.KineticTextField(value = fullName, onValueChange = { fullName = it }, hint = "FULL NAME")
            Spacer(Modifier.height(KineticSpacing.md))
            com.bits.facultyai.ui.components.KineticTextField(value = preferredName, onValueChange = { preferredName = it }, hint = "PREFERRED NAME")
            Spacer(Modifier.height(KineticSpacing.md))
            com.bits.facultyai.ui.components.KineticTextField(value = designation, onValueChange = { designation = it }, hint = "DESIGNATION")
            Spacer(Modifier.height(KineticSpacing.md))
            com.bits.facultyai.ui.components.KineticTextField(value = department, onValueChange = { department = it }, hint = "DEPARTMENT")
            Spacer(Modifier.height(KineticSpacing.lg))
            com.bits.facultyai.ui.components.KineticButton(
                text = "SAVE PROFILE",
                onClick = { vm.saveEditableFields(fullName, preferredName, designation, department); editing = false },
            )
        } else {
            KineticSectionHeader(title = "IDENTITY")
            InfoRow("EMPLOYEE ID", profile?.employeeId ?: "—")
            InfoRow("EMAIL", profile?.email ?: "—")
            InfoRow("PHONE", profile?.phone ?: "—")
            KineticSectionHeader(title = "ACADEMIC")
            InfoRow("QUALIFICATION", profile?.qualification ?: "—")
            InfoRow("SPECIALIZATION", profile?.specialization ?: "—")
            InfoRow("ACADEMIC YEAR", profile?.academicYear ?: "—")
            InfoRow("SEMESTER", profile?.semester ?: "—")
        }
        Spacer(Modifier.height(96.dp))
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    val k = LocalKineticColors.current
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = KineticSpacing.sm)) {
        Text(text = label, style = KineticType.label, color = k.mutedForeground, modifier = Modifier.width(150.dp))
        Text(text = value, style = KineticType.bodyMedium, color = k.foreground)
    }
}
