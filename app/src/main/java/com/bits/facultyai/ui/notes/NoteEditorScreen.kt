package com.bits.facultyai.ui.notes

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import com.bits.facultyai.data.local.FacultyDatabase
import com.bits.facultyai.data.local.MemoryEntity
import com.bits.facultyai.data.local.NoteEntity
import com.bits.facultyai.ui.components.KineticButton
import com.bits.facultyai.ui.components.KineticDivider
import com.bits.facultyai.ui.components.KineticGhostButton
import com.bits.facultyai.ui.components.KineticTextField
import com.bits.facultyai.ui.theme.KineticSpacing
import com.bits.facultyai.ui.theme.KineticType
import com.bits.facultyai.ui.theme.LocalKineticColors
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId

class NoteEditorViewModel(savedStateHandle: SavedStateHandle, application: Application) : ViewModel() {
    private val dao = FacultyDatabase.get(application).facultyDao()
    val noteId: Long = savedStateHandle.get<String>("noteId")?.toLongOrNull() ?: -1L

    val note = MutableStateFlow<NoteEntity?>(null)
    var saveState = MutableStateFlow("SAVED")
        private set

    private var createdId: Long = -1L

    init {
        viewModelScope.launch {
            note.value = if (noteId > 0) dao.getNote(noteId) else null
            saveState.value = "SAVED"
        }
    }

    fun save(title: String, body: String) {
        viewModelScope.launch {
            saveState.value = "SAVING..."
            val targetId = if (noteId > 0) noteId else createdId
            if (targetId > 0) {
                val existing = dao.getNote(targetId)
                if (existing != null) {
                    dao.updateNote(existing.copy(title = title.ifBlank { "Untitled note" }, body = body, updatedAt = System.currentTimeMillis()))
                }
            } else {
                createdId = dao.insertNote(
                    NoteEntity(title = title.ifBlank { "Untitled note" }, body = body, folder = "LECTURES", subject = null, updatedAt = System.currentTimeMillis(), createdAt = System.currentTimeMillis())
                )
            }
            saveState.value = "SAVED"
        }
    }

    fun saveToMemory(text: String) {
        viewModelScope.launch {
            dao.insertMemory(
                MemoryEntity(category = "NOTES", text = text.take(200), source = "Saved from note", createdAt = System.currentTimeMillis())
            )
        }
    }
}

@Composable
fun NoteEditorScreen(
    noteId: Long,
    onBack: () -> Unit,
    vm: NoteEditorViewModel = viewModel(),
) {
    val k = LocalKineticColors.current
    val note by vm.note.collectAsStateWithLifecycle()
    val saveState by vm.saveState.collectAsStateWithLifecycle()

    var title by remember(note) { mutableStateOf(note?.title ?: "") }
    var body by remember(note) { mutableStateOf(note?.body ?: "") }
    var lastEdit by remember { mutableStateOf(0L) }

    // Continuous autosave: 1.2s after the last keystroke.
    LaunchedEffect(title, body) {
        if (note == null && noteId > 0) return@LaunchedEffect
        if (title == (note?.title ?: "") && body == (note?.body ?: "")) return@LaunchedEffect
        lastEdit = System.currentTimeMillis()
        kotlinx.coroutines.delay(1200)
        if (System.currentTimeMillis() - lastEdit >= 1100) {
            vm.save(title, body)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(k.background)
            .imePadding()
            .padding(horizontal = KineticSpacing.lg),
    ) {
        Spacer(Modifier.height(KineticSpacing.xl))
        Row(verticalAlignment = Alignment.CenterVertically) {
            KineticGhostButton(text = "← BACK", onClick = onBack)
            Spacer(Modifier.weight(1f))
            Text(text = saveState, style = KineticType.label, color = k.mutedForeground)
        }
        Spacer(Modifier.height(KineticSpacing.md))

        // Minimalist formatting toolbar (monochrome, accent-active)
        Row(horizontalArrangement = Arrangement.spacedBy(KineticSpacing.lg)) {
            listOf("B", "I", "U", "•", "1.", "☑", "🔗", "H1").forEach { tool ->
                Text(
                    text = tool,
                    style = KineticType.labelBold,
                    color = k.mutedForeground,
                    modifier = Modifier.clickable { /* formatting applied inline in v2 */ },
                )
            }
        }
        Spacer(Modifier.height(KineticSpacing.md))

        KineticTextField(value = title, onValueChange = { title = it }, hint = "NOTE TITLE")
        Spacer(Modifier.height(KineticSpacing.md))

        androidx.compose.foundation.text.BasicTextField(
            value = body,
            onValueChange = { body = it },
            textStyle = KineticType.body.copy(color = k.foreground),
            cursorBrush = androidx.compose.ui.graphics.SolidColor(k.accent),
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            decorationBox = { inner ->
                Column {
                    if (body.isEmpty()) {
                        Text("Start writing... your assistant can find this later.", style = KineticType.body, color = k.mutedForeground)
                    }
                    inner()
                }
            },
        )

        Spacer(Modifier.height(KineticSpacing.md))
        Row(horizontalArrangement = Arrangement.spacedBy(KineticSpacing.sm)) {
            KineticButton(
                text = "SAVE",
                onClick = { vm.save(title, body); onBack() },
                modifier = Modifier.weight(1f),
            )
            KineticButton(
                text = "SAVE TO MEMORY",
                onClick = { vm.saveToMemory(title.ifBlank { body.take(60) }) },
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(KineticSpacing.lg))
    }
}
