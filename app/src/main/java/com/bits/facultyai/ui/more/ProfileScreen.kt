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
import com.bits.facultyai.ui.components.KineticButton
import com.bits.facultyai.ui.components.KineticDisplayText
import com.bits.facultyai.ui.components.KineticDivider
import com.bits.facultyai.ui.components.KineticGhostButton
import com.bits.facultyai.ui.components.KineticSectionHeader
import com.bits.facultyai.ui.components.KineticTextField
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

    val saved = MutableStateFlow(false)

    init {
        viewModelScope.launch { _profile.value = dao.getProfile() }
    }

    fun save(
        fullName: String,
        preferredName: String,
        designation: String,
        department: String,
        employeeId: String,
        email: String,
        phone: String,
        qualification: String,
        specialization: String,
        cabin: String,
        subjects: String,
        academicYear: String,
        semester: String,
    ) {
        viewModelScope.launch {
            val p = _profile.value ?: return@launch
            dao.upsertProfile(
                p.copy(
                    fullName = fullName.ifBlank { p.fullName },
                    preferredName = preferredName.ifBlank { fullName.ifBlank { p.preferredName } },
                    designation = designation.ifBlank { p.designation },
                    department = department.ifBlank { p.department },
                    employeeId = employeeId.trim(),
                    email = email.trim(),
                    phone = phone.trim(),
                    qualification = qualification.trim(),
                    specialization = specialization.trim(),
                    cabin = cabin.trim(),
                    subjects = subjects.trim(),
                    academicYear = academicYear.trim().ifBlank { p.academicYear },
                    semester = semester.trim().ifBlank { p.semester },
                    updatedAt = System.currentTimeMillis(),
                )
            )
            _profile.value = dao.getProfile()
            saved.value = true
        }
    }

    fun consumeSaved() {
        saved.value = false
    }
}

@Composable
fun ProfileScreen(onBack: () -> Unit, vm: ProfileViewModel = viewModel()) {
    val k = LocalKineticColors.current
    val profile by vm.profile.collectAsStateWithLifecycle()
    val savedFlag by vm.saved.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf(false) }

    var fullName by remember(profile) { mutableStateOf(profile?.fullName ?: "") }
    var preferredName by remember(profile) { mutableStateOf(profile?.preferredName ?: "") }
    var designation by remember(profile) { mutableStateOf(profile?.designation ?: "") }
    var department by remember(profile) { mutableStateOf(profile?.department ?: "") }
    var employeeId by remember(profile) { mutableStateOf(profile?.employeeId ?: "") }
    var email by remember(profile) { mutableStateOf(profile?.email ?: "") }
    var phone by remember(profile) { mutableStateOf(profile?.phone ?: "") }
    var qualification by remember(profile) { mutableStateOf(profile?.qualification ?: "") }
    var specialization by remember(profile) { mutableStateOf(profile?.specialization ?: "") }
    var cabin by remember(profile) { mutableStateOf(profile?.cabin ?: "") }
    var subjects by remember(profile) { mutableStateOf(profile?.subjects ?: "") }
    var academicYear by remember(profile) { mutableStateOf(profile?.academicYear ?: "") }
    var semester by remember(profile) { mutableStateOf(profile?.semester ?: "") }

    val nameError = if (editing && fullName.isBlank()) "Please enter your name." else null

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(k.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = KineticSpacing.lg),
    ) {
        Spacer(Modifier.height(KineticSpacing.xl))
        Row(verticalAlignment = Alignment.CenterVertically) {
            KineticGhostButton(text = "← BACK", onClick = onBack)
            Spacer(Modifier.weight(1f))
            Text(
                text = if (editing) "CANCEL" else "EDIT",
                style = KineticType.labelBold,
                color = k.accent,
                modifier = Modifier.clickable {
                    if (editing) vm.consumeSaved()
                    editing = !editing
                },
            )
        }
        Spacer(Modifier.height(KineticSpacing.lg))

        // Identity block — initial letter avatar (photo hook ready: replace Box with AsyncImage)
        Box(
            modifier = Modifier
                .size(80.dp)
                .background(k.accent),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = (profile?.preferredName?.take(1) ?: "?").uppercase(),
                style = KineticType.display.copy(fontSize = 36.sp),
                color = k.accentForeground,
            )
        }
        Spacer(Modifier.height(KineticSpacing.md))
        KineticDisplayText(
            text = (profile?.fullName ?: "FACULTY").ifBlank { "FACULTY" },
            style = KineticType.display.copy(fontSize = 36.sp),
        )
        val subtitle = listOfNotNull(
            profile?.designation?.takeIf { it.isNotBlank() },
            profile?.department?.takeIf { it.isNotBlank() },
        ).joinToString(" · ")
        if (subtitle.isNotBlank()) {
            Text(text = subtitle, style = KineticType.labelBold.copy(fontSize = 12.sp), color = k.accent)
        }
        Spacer(Modifier.height(KineticSpacing.xl))

        if (editing) {
            KineticTextField(value = fullName, onValueChange = { fullName = it }, hint = "FULL NAME *", isError = nameError != null, errorMessage = nameError)
            Spacer(Modifier.height(KineticSpacing.md))
            KineticTextField(value = preferredName, onValueChange = { preferredName = it }, hint = "PREFERRED DISPLAY NAME")
            Spacer(Modifier.height(KineticSpacing.md))
            KineticTextField(value = designation, onValueChange = { designation = it }, hint = "DESIGNATION")
            Spacer(Modifier.height(KineticSpacing.md))
            KineticTextField(value = department, onValueChange = { department = it }, hint = "DEPARTMENT")
            Spacer(Modifier.height(KineticSpacing.md))
            KineticTextField(value = employeeId, onValueChange = { employeeId = it }, hint = "FACULTY / EMPLOYEE ID")
            Spacer(Modifier.height(KineticSpacing.md))
            KineticTextField(value = email, onValueChange = { email = it }, hint = "EMAIL")
            Spacer(Modifier.height(KineticSpacing.md))
            KineticTextField(value = phone, onValueChange = { phone = it }, hint = "PHONE")
            Spacer(Modifier.height(KineticSpacing.md))
            KineticTextField(value = qualification, onValueChange = { qualification = it }, hint = "QUALIFICATION")
            Spacer(Modifier.height(KineticSpacing.md))
            KineticTextField(value = specialization, onValueChange = { specialization = it }, hint = "SPECIALIZATION")
            Spacer(Modifier.height(KineticSpacing.md))
            KineticTextField(value = cabin, onValueChange = { cabin = it }, hint = "CABIN / OFFICE")
            Spacer(Modifier.height(KineticSpacing.md))
            KineticTextField(value = subjects, onValueChange = { subjects = it }, hint = "SUBJECTS HANDLED (COMMA SEPARATED)", minLines = 2, maxLines = 3)
            Spacer(Modifier.height(KineticSpacing.md))
            KineticTextField(value = academicYear, onValueChange = { academicYear = it }, hint = "ACADEMIC YEAR")
            Spacer(Modifier.height(KineticSpacing.md))
            KineticTextField(value = semester, onValueChange = { semester = it }, hint = "SEMESTER")
            Spacer(Modifier.height(KineticSpacing.lg))
            KineticButton(
                text = "SAVE PROFILE",
                enabled = fullName.isNotBlank(),
                onClick = {
                    vm.save(
                        fullName, preferredName, designation, department, employeeId, email, phone,
                        qualification, specialization, cabin, subjects, academicYear, semester,
                    )
                    editing = false
                },
            )
            if (savedFlag) {
                Spacer(Modifier.height(KineticSpacing.sm))
                Text(text = "PROFILE SAVED", style = KineticType.labelBold, color = k.accent)
            }
        } else {
            KineticSectionHeader(title = "IDENTITY")
            InfoRow("EMPLOYEE ID", profile?.employeeId?.ifBlank { null } ?: "—")
            InfoRow("EMAIL", profile?.email?.ifBlank { null } ?: "—")
            InfoRow("PHONE", profile?.phone?.ifBlank { null } ?: "—")
            InfoRow("CABIN", profile?.cabin?.ifBlank { null } ?: "—")
            KineticSectionHeader(title = "ACADEMIC")
            InfoRow("QUALIFICATION", profile?.qualification?.ifBlank { null } ?: "—")
            InfoRow("SPECIALIZATION", profile?.specialization?.ifBlank { null } ?: "—")
            InfoRow("SUBJECTS", profile?.subjects?.ifBlank { null } ?: "—")
            InfoRow("ACADEMIC YEAR", profile?.academicYear?.ifBlank { null } ?: "—")
            InfoRow("SEMESTER", profile?.semester?.ifBlank { null } ?: "—")
        }
        Spacer(Modifier.height(KineticSpacing.xl))
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    val k = LocalKineticColors.current
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = KineticSpacing.sm)) {
        Text(
            text = label,
            style = KineticType.label,
            color = k.mutedForeground,
            modifier = Modifier.width(140.dp),
        )
        Text(
            text = value,
            style = KineticType.bodyMedium,
            color = k.foreground,
            modifier = Modifier.weight(1f),
        )
    }
    KineticDivider()
}
