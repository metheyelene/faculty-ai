package com.bits.facultyai.ui.notes

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bits.facultyai.data.attachments.NOTE_FILE_EXTENSIONS
import com.bits.facultyai.data.local.FacultyDatabase
import com.bits.facultyai.data.local.NoteAttachmentEntity
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
    val notes: StateFlow<List<NoteUi>> = combine(
        dao.observeNotes(),
        dao.observeAllAttachments(),
        query.debounce(150),
        selectedFolder,
    ) { all, attachments, q, folder ->
        // Search covers titles, bodies, subjects AND attachment file names —
        // a note is findable by the files inside it.
        val attachmentNames = attachments.groupBy { it.noteId }
            .mapValues { (_, list) -> list.joinToString(" ") { it.displayName } }
        all.filter { n ->
            (folder == null || n.folder == folder) &&
                (q.isBlank() ||
                    n.title.contains(q, true) ||
                    n.body.contains(q, true) ||
                    (n.subject?.contains(q, true) == true) ||
                    (attachmentNames[n.id]?.contains(q, true) == true))
        }.map { note ->
            val mine = attachments.filter { it.noteId == note.id }
            NoteUi(
                note = note,
                attachmentCount = mine.size,
                failedCount = mine.count { it.state == "FAILED" },
                inProgressCount = mine.count { it.state == "UPLOADING" || it.state == "PROCESSING" },
                syncedCount = mine.count { it.state == "SYNCED" },
            )
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
}

/** One row of the notes list: the note plus derived attachment state. */
data class NoteUi(
    val note: NoteEntity,
    val attachmentCount: Int,
    val failedCount: Int,
    val inProgressCount: Int,
    val syncedCount: Int,
) {
    /** SYNCED only when everything attached made it to the cloud; FAILED wins visually. */
    val syncLabel: String? = when {
        failedCount > 0 -> "FAILED"
        inProgressCount > 0 -> "SYNCING"
        attachmentCount > 0 && syncedCount == attachmentCount -> "SYNCED"
        attachmentCount > 0 -> "SAVED"
        else -> null
    }
}
