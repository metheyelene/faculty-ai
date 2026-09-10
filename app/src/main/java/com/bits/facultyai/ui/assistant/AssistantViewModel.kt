package com.bits.facultyai.ui.assistant

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bits.facultyai.data.local.FacultyDatabase
import com.bits.facultyai.data.local.MemoryEntity
import com.bits.facultyai.data.prefs.SettingsRepository
import com.bits.facultyai.domain.AnswerSource
import com.bits.facultyai.domain.FacultyAssistant
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class AssistantPhase { IDLE, THINKING, ERROR }

data class ChatMessage(
    val isUser: Boolean,
    val lines: List<String>,
    val sources: List<AnswerSource> = emptyList(),
    val pendingMemory: String? = null,
    val isError: Boolean = false,
    val timestamp: Long = System.currentTimeMillis(),
    val rawQuery: String? = null, // set on error replies so we can regenerate
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
                    "Hello! I'm your ACADORA assistant.",
                    "I answer from YOUR timetable, tasks, notes and memories — I don't invent anything.",
                    "Try: \"What is my next class?\" or \"Find my notes about DSP\".",
                ),
            ),
        )
    )

    val phase = MutableStateFlow(AssistantPhase.IDLE)

    fun ask(query: String) {
        if (query.isBlank() || phase.value == AssistantPhase.THINKING) return
        viewModelScope.launch { runQuery(query) }
    }

    /** Re-runs the query that produced the last error. */
    fun retry() {
        val lastUser = chat.value.lastOrNull { it.isUser }?.lines?.firstOrNull() ?: return
        if (phase.value == AssistantPhase.THINKING) return
        viewModelScope.launch { runQuery(lastUser) }
    }

    fun clearConversation() {
        phase.value = AssistantPhase.IDLE
        chat.value = listOf(
            ChatMessage(
                isUser = false,
                lines = listOf(
                    "Fresh start. What would you like to know?",
                    "I answer from your timetable, tasks, notes and memories.",
                ),
            ),
        )
    }

    private suspend fun runQuery(query: String) {
        // Remove any previous error bubble before answering again.
        chat.value = chat.value.filter { !it.isError }
        chat.value = chat.value + ChatMessage(isUser = true, lines = listOf(query))
        phase.value = AssistantPhase.THINKING

        try {
            // Small delay so the thinking orb is perceptible for instant answers.
            delay(450)

            val app = getApplication<Application>()
            val profile = dao.getProfile()
            val timetable = dao.getTimetable()
            val tasks = dao.getTasks()
            val notes = dao.getNotes()
            val memoriesList = dao.getMemories()
            val students = dao.getStudents()
            val prefs = settingsRepo.settings.first()
            val answer = FacultyAssistant.respond(
                query = query,
                profile = profile,
                timetable = timetable,
                tasks = tasks,
                notes = notes,
                memories = memoriesList,
                students = students,
                aiNotesAllowed = prefs.aiAccessToNotes,
            )

            chat.value = chat.value + ChatMessage(
                isUser = false,
                lines = answer.lines,
                sources = answer.sources,
            )
            phase.value = AssistantPhase.IDLE

            // Memory consent: offer to remember scheduling-related statements.
            if (prefs.memoryEnabled && looksLikePreference(query)) {
                chat.value = chat.value + ChatMessage(
                    isUser = false,
                    lines = listOf("Remember this for later?"),
                    pendingMemory = query,
                )
            }
        } catch (t: Throwable) {
            phase.value = AssistantPhase.ERROR
            chat.value = chat.value + ChatMessage(
                isUser = false,
                isError = true,
                lines = listOf(
                    "Something went wrong while answering that.",
                    "Check your app state and try again.",
                ),
                rawQuery = query,
            )
        }
    }

    fun confirmMemory(text: String) {
        viewModelScope.launch {
            dao.insertMemory(
                MemoryEntity(
                    category = "WORK",
                    text = text.take(240),
                    source = "Saved from Assistant",
                    createdAt = System.currentTimeMillis(),
                )
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
