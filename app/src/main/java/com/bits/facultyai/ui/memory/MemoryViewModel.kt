package com.bits.facultyai.ui.memory

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bits.facultyai.data.local.FacultyDatabase
import com.bits.facultyai.data.local.MemoryEntity
import com.bits.facultyai.data.prefs.AppSettings
import com.bits.facultyai.data.prefs.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MemoryViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = FacultyDatabase.get(application).facultyDao()
    private val settingsRepo = SettingsRepository(application)

    val memories: StateFlow<List<MemoryEntity>> = dao.observeMemories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val settings: StateFlow<AppSettings?> = settingsRepo.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun deleteMemory(id: Long) = viewModelScope.launch { dao.deleteMemory(id) }

    fun clearAll() = viewModelScope.launch { dao.clearMemories() }

    fun updateMemory(id: Long, newText: String) = viewModelScope.launch {
        val m = dao.getMemories().firstOrNull { it.id == id } ?: return@launch
        dao.updateMemory(m.copy(text = newText))
    }

    fun setMemoryEnabled(enabled: Boolean) = viewModelScope.launch { settingsRepo.setMemoryEnabled(enabled) }
    fun setAiNotes(enabled: Boolean) = viewModelScope.launch { settingsRepo.setAiAccessToNotes(enabled) }
    fun setPersonalizedNotifications(enabled: Boolean) = viewModelScope.launch { settingsRepo.setPersonalizedNotifications(enabled) }
}
