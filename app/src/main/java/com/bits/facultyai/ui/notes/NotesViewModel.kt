package com.bits.facultyai.ui.notes

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bits.facultyai.data.local.FacultyDatabase
import com.bits.facultyai.data.local.NoteEntity
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class NotesViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = FacultyDatabase.get(application).facultyDao()

    private val query = MutableStateFlow("")
    val searchQuery: StateFlow<String> = query
    val selectedFolder = MutableStateFlow<String?>(null)

    @OptIn(FlowPreview::class)
    val notes: StateFlow<List<NoteEntity>> = combine(
        dao.observeNotes(),
        query.debounce(150),
        selectedFolder,
    ) { all, q, folder ->
        all.filter { n ->
            (folder == null || n.folder == folder) &&
                (q.isBlank() || n.title.contains(q, true) || n.body.contains(q, true))
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setSearch(q: String) { query.value = q }
    fun setFolder(f: String?) { selectedFolder.value = f }

    fun createNote(title: String, folder: String, onCreated: (Long) -> Unit) {
        viewModelScope.launch {
            val id = dao.insertNote(
                NoteEntity(
                    title = title.ifBlank { "Untitled note" },
                    body = "",
                    folder = folder,
                    subject = null,
                    updatedAt = System.currentTimeMillis(),
                    createdAt = System.currentTimeMillis(),
                )
            )
            onCreated(id)
        }
    }

    fun deleteNote(id: Long) = viewModelScope.launch { dao.deleteNote(id) }

    /** Autosave used by the editor: updates title/body in place. */
    fun saveNote(id: Long, title: String, body: String) {
        viewModelScope.launch {
            val existing = dao.getNote(id) ?: return@launch
            dao.updateNote(
                existing.copy(title = title.ifBlank { "Untitled note" }, body = body, updatedAt = System.currentTimeMillis())
            )
        }
    }
}
