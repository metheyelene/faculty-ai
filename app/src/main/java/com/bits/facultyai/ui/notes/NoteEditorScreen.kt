package com.bits.facultyai.ui.notes

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bits.facultyai.data.local.FacultyDatabase
import com.bits.facultyai.data.local.MemoryEntity
import com.bits.facultyai.data.local.NoteEntity
import com.bits.facultyai.ui.components.Glass
import com.bits.facultyai.ui.components.GlassSurface
import com.bits.facultyai.ui.components.GlassStrength
import com.bits.facultyai.ui.components.KineticButton
import com.bits.facultyai.ui.components.KineticGhostButton
import com.bits.facultyai.ui.components.KineticTextField
import com.bits.facultyai.ui.theme.KineticSpacing
import com.bits.facultyai.ui.theme.KineticType
import com.bits.facultyai.ui.theme.LocalKineticColors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

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
                    dao.updateNote(
                        existing.copy(
                            title = title.ifBlank { "Untitled note" },
                            body = body,
                            updatedAt = System.currentTimeMillis(),
                        )
                    )
                } else {
                    // Route carried an id with no row yet (e.g. process death):
                    // insert so autosave has a real target.
                    createdId = dao.insertNote(
                        NoteEntity(
                            id = targetId,
                            title = title.ifBlank { "Untitled note" },
                            body = body,
                            folder = "LECTURES",
                            subject = null,
                            updatedAt = System.currentTimeMillis(),
                            createdAt = System.currentTimeMillis(),
                        )
                    )
                }
            } else {
                createdId = dao.insertNote(
                    NoteEntity(
                        title = title.ifBlank { "Untitled note" },
                        body = body,
                        folder = "LECTURES",
                        subject = null,
                        updatedAt = System.currentTimeMillis(),
                        createdAt = System.currentTimeMillis(),
                    )
                )
            }
            saveState.value = "SAVED"
        }
    }

    fun saveToMemory(text: String) {
        viewModelScope.launch {
            dao.insertMemory(
                MemoryEntity(
                    category = "NOTES",
                    text = text.take(200),
                    source = "Saved from note",
                    createdAt = System.currentTimeMillis(),
                )
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

    // Undo/redo: full body snapshots taken at each structural edit.
    var undoStack by remember { mutableStateOf(listOf<String>()) }
    var redoStack by remember { mutableStateOf(listOf<String>()) }
    var lastSnapshotBody by remember { mutableStateOf(note?.body ?: "") }

    fun pushUndo() {
        if (body != lastSnapshotBody) {
            undoStack = (undoStack + lastSnapshotBody).takeLast(50)
            redoStack = emptyList()
            lastSnapshotBody = body
        }
    }

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
            .navigationBarsPadding()
            .padding(horizontal = KineticSpacing.lg),
    ) {
        Spacer(Modifier.height(KineticSpacing.xl))
        Row(verticalAlignment = Alignment.CenterVertically) {
            KineticGhostButton(text = "← BACK", onClick = {
                vm.save(title, body)
                onBack()
            })
            Spacer(Modifier.weight(1f))
            Text(text = saveState, style = KineticType.label, color = k.mutedForeground)
        }
        Spacer(Modifier.height(KineticSpacing.md))

        // ---- Minimal contextual glass toolbar: undo/redo/checklist/bullet/clear ----
        GlassSurface(strength = GlassStrength.REGULAR, shape = Glass.shape()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = KineticSpacing.xs, vertical = KineticSpacing.xs),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(KineticSpacing.xs),
            ) {
                ToolbarAction(label = "↺", desc = "Undo", enabled = undoStack.isNotEmpty()) {
                    if (undoStack.isNotEmpty()) {
                        redoStack = (redoStack + body).takeLast(50)
                        body = undoStack.last()
                        undoStack = undoStack.dropLast(1)
                        lastSnapshotBody = body
                    }
                }
                ToolbarAction(label = "↻", desc = "Redo", enabled = redoStack.isNotEmpty()) {
                    if (redoStack.isNotEmpty()) {
                        undoStack = (undoStack + body).takeLast(50)
                        body = redoStack.last()
                        redoStack = redoStack.dropLast(1)
                        lastSnapshotBody = body
                    }
                }
                ToolbarDivider()
                ToolbarAction(label = "☑", desc = "Insert checklist line") {
                    pushUndo()
                    body = body.ensureLineEnd() + "☐ "
                }
                ToolbarAction(label = "•", desc = "Insert bullet line") {
                    pushUndo()
                    body = body.ensureLineEnd() + "• "
                }
                ToolbarDivider()
                ToolbarAction(label = "✕", desc = "Clear note body", enabled = body.isNotEmpty()) {
                    pushUndo()
                    body = ""
                }
            }
        }
        Spacer(Modifier.height(KineticSpacing.md))

        KineticTextField(value = title, onValueChange = { title = it }, hint = "NOTE TITLE")
        Spacer(Modifier.height(KineticSpacing.md))

        BasicTextField(
            value = body,
            onValueChange = { body = it },
            textStyle = KineticType.body.copy(color = k.foreground),
            cursorBrush = SolidColor(k.accent),
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            decorationBox = { inner ->
                Column {
                    if (body.isEmpty()) {
                        Text(
                            "Start writing... your assistant can find this later.",
                            style = KineticType.body,
                            color = k.mutedForeground,
                        )
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
        Spacer(Modifier.height(KineticSpacing.xl))
    }
}

private fun String.ensureLineEnd(): String =
    if (isEmpty() || last() == '\n') this else this + "\n"

/** 44dp-minimum toolbar action with screen-reader description. */
@Composable
private fun ToolbarAction(
    label: String,
    desc: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val k = LocalKineticColors.current
    Text(
        text = label,
        style = KineticType.labelBold.copy(fontSize = 15.sp),
        color = if (enabled) k.foreground else k.mutedForeground.copy(alpha = 0.45f),
        modifier = Modifier
            .size(width = 44.dp, height = 44.dp)
            .clickable(enabled = enabled, onClick = onClick)
            .wrapContentSize(Alignment.Center)
            .semantics { contentDescription = desc },
    )
}

@Composable
private fun ToolbarDivider() {
    val k = LocalKineticColors.current
    Box(
        Modifier
            .size(width = 1.dp, height = 22.dp)
            .background(k.border)
    )
}
