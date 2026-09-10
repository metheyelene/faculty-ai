package com.bits.facultyai.ui.onboarding

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bits.facultyai.data.local.FacultyDatabase
import com.bits.facultyai.data.local.FacultyProfileEntity
import com.bits.facultyai.data.prefs.AppSettings
import com.bits.facultyai.data.prefs.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class OnboardingViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = FacultyDatabase.get(application).facultyDao()
    private val settingsRepo = SettingsRepository(application)

    val step = MutableStateFlow(0)

    val profile: StateFlow<FacultyProfileEntity?> = dao.observeProfile()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val settings: StateFlow<AppSettings?> = settingsRepo.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    var fullName = MutableStateFlow("")
        private set
    var preferredName = MutableStateFlow("")
        private set
    var designation = MutableStateFlow("")
        private set
    var department = MutableStateFlow("")
        private set
    var subjects = MutableStateFlow("")
        private set

    fun setFullName(v: String) { fullName.value = v }
    fun setPreferredName(v: String) { preferredName.value = v }
    fun setDesignation(v: String) { designation.value = v }
    fun setDepartment(v: String) { department.value = v }
    fun setSubjects(v: String) { subjects.value = v }

    fun next(onDone: () -> Unit) {
        viewModelScope.launch {
            if (step.value < 2) {
                if (step.value == 0) persistProfile()
                step.value += 1
            } else {
                persistProfile()
                persistAssistantPrefs()
                settingsRepo.setOnboardingComplete()
                onDone()
            }
        }
    }

    fun previous() {
        if (step.value > 0) step.value -= 1
    }

    fun complete(onDone: () -> Unit) {
        viewModelScope.launch {
            persistProfile()
            persistAssistantPrefs()
            settingsRepo.setOnboardingComplete()
            onDone()
        }
    }

    private suspend fun persistProfile() {
        val existing = dao.getProfile()
        val name = fullName.value.ifBlank { existing?.fullName ?: "" }
        if (name.isBlank() && existing == null) return
        dao.upsertProfile(
            FacultyProfileEntity(
                id = 1L,
                fullName = name.ifBlank { "Faculty Member" },
                preferredName = preferredName.value.ifBlank {
                    existing?.preferredName ?: name.split(" ").firstOrNull() ?: ""
                },
                designation = designation.value.ifBlank { existing?.designation ?: "Faculty" },
                department = department.value.ifBlank { existing?.department ?: "ECE" },
                employeeId = existing?.employeeId ?: "",
                email = existing?.email ?: "",
                phone = existing?.phone ?: "",
                qualification = existing?.qualification ?: "",
                specialization = existing?.specialization ?: "",
                cabin = existing?.cabin ?: "",
                academicYear = existing?.academicYear ?: "2026 — 27",
                semester = existing?.semester ?: "Semester I",
                onboardingComplete = true,
                profileLocked = existing?.profileLocked ?: false,
                updatedAt = System.currentTimeMillis(),
            )
        )
    }

    private suspend fun persistAssistantPrefs() {
        // Memory stays enabled by default; subjects text is saved as a teaching memory.
        if (subjects.value.isNotBlank()) {
            dao.insertMemory(
                com.bits.facultyai.data.local.MemoryEntity(
                    category = "TEACHING",
                    text = "Handles subjects: ${subjects.value}.",
                    source = "Onboarding",
                    createdAt = System.currentTimeMillis(),
                )
            )
        }
    }
}
