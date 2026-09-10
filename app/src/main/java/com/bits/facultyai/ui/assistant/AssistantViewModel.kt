package com.bits.facultyai.ui.assistant

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bits.facultyai.data.local.FacultyDatabase
import com.bits.facultyai.data.local.MemoryEntity
import com.bits.facultyai.data.prefs.SettingsRepository
import com.bits.facultyai.domain.AnswerSource
import com.bits.facultyai.domain.FacultyAssistant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ChatMessage(
    val isUser: Boolean,
    val lines: List<String>,
    val sources: List<AnswerSource> = emptyList(),
    val pendingMemory: String? = null,
)

class AssistantViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = FacultyDatabase.get(application).facultyDao()
    private val settingsRepo = SettingsRepository(application)

    val settings = settingsRepo.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val memories = dao.observeMemories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val chat = MutableStateFlow<List<ChatMessage>>(
        listOf(
            ChatMessage(
                isUser = false,
                lines = listOf(
                    "Hello! I'm your Faculty AI assistant.",
                    "I answer from YOUR timetable, tasks, notes and memories — I don't invent anything.",
                    "Try: \"What is my next class?\" or \"Find my notes about DSP\".",
                ),
            ),
        )
    )

    fun ask(query: String) {
        if (query.isBlank()) return
        val app = getApplication<Application>()
        viewModelScope.launch {
            chat.value = chat.value + ChatMessage(isUser = true, lines = listOf(query))

            val profile = dao.getProfile()
            val timetable = dao.getTimetable()
            val tasks = dao.getTasks()
            val notes = dao.getNotes()
            val memoriesList = dao.getMemories()
            val students = dao.getStudents()
            val allowNotes = settingsRepo.settings.first().aiAccessToNotes
            val memoryEnabled = settingsRepo.settings.first().memoryEnabled

            val answer = FacultyAssistant.respond(
                query = query,
                profile = profile,
                timetable = timetable,
                tasks = tasks,
                notes = notes,
                memories = memoriesList,
                students = students,
                aiNotesAllowed = allowNotes,
            )

            chat.value = chat.value + ChatMessage(
                isUser = false,
                lines = answer.lines,
                sources = answer.sources,
            )

            // Memory consent: offer to remember scheduling-related statements.
            if (memoryEnabled && looksLikePreference(query)) {
                chat.value = chat.value + ChatMessage(
                    isUser = false,
                    lines = listOf("Remember this for later?"),
                    pendingMemory = query,
                )
            }
        }
    }

    fun confirmMemory(text: String) {
        viewModelScope.launch {
            dao.insertMemory(
                MemoryEntity(category = "WORK", text = text.take(240), source = "Saved from Assistant", createdAt = System.currentTimeMillis())
            )
            dismissMemoryOffer()
            chat.value = chat.value + ChatMessage(isUser = false, lines = listOf("Saved to MY MEMORY."))
        }
    }

    fun dismissMemoryOffer() {
        chat.value = chat.value.mapNotNull { msg ->
            if (msg.pendingMemory != null) null else msg
        }
    }

    private fun looksLikePreference(q: String): Boolean {
        val lower = q.lowercase()
        return lower.contains("prefer") || lower.contains("always") || lower.contains("usually") ||
            lower.contains("every week") || lower.contains("recurring")
    }
}
