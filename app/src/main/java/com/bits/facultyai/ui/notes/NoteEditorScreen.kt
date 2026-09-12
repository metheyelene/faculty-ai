package com.bits.facultyai.ui.notes

import android.app.Application
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.bits.facultyai.data.attachments.NoteAttachments
import com.bits.facultyai.data.local.FacultyDatabase
import com.bits.facultyai.data.local.MemoryEntity
import com.bits.facultyai.data.local.NoteAttachmentEntity
import com.bits.facultyai.data.local.NoteEntity
import com.bits.facultyai.ui.components.Glass
import com.bits.facultyai.ui.components.GlassChip
import com.bits.facultyai.ui.components.GlassSurface
import com.bits.facultyai.ui.components.GlassStrength
import com.bits.facultyai.ui.components.KineticButton
import com.bits.facultyai.ui.components.KineticGhostButton
import com.bits.facultyai.ui.components.KineticTextField
import com.bits.facultyai.ui.theme.KineticSpacing
import com.bits.facultyai.ui.theme.KineticType
import com.bits.facultyai.ui.theme.LocalKineticColors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class NoteEditorViewModel(savedStateHandle: SavedStateHandle, application: Application) : ViewModel() {
    private val dao = FacultyDatabase.get(application).facultyDao()
    private val attachments = NoteAttachments(application, dao)
    val noteId: Long = savedStateHandle.get<String>("noteId")?.toLongOrNull() ?: -1L

    val note = MutableStateFlow<NoteEntity?>(null)
    var saveState = MutableStateFlow("SAVED")
        private set

    /** Attachment rows for this note — states drive the upload UI badges. */
    val attachmentList = attachments.observeAttachments(noteId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Real subjects from the faculty's own timetable — never invented defaults. */
    val subjectOptions = kotlinx.coroutines.flow.flow {
        emit(dao.getTimetable().map { it.subject }.filter { it.isNotBlank() }.distinct().sorted())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private var createdId: Long = -1L

    init {
        viewModelScope.launch {
            note.value = if (noteId > 0) dao.getNote(noteId) else null
            saveState.value = "SAVED"
        }
    }

    fun attach(uris: List<Uri>) {
        val target = if (noteId > 0) noteId else createdId
        if (target > 0) attachments.attach(target, uris)
    }

    fun renameAttachment(id: Long, newName: String) = attachments.renameAttachment(id, newName)
    fun removeAttachment(id: Long) = attachments.removeAttachment(id)
    fun retryAttachment(id: Long) = attachments.retryAttachment(id)
    fun openAttachment(id: Long, onMissing: () -> Unit) = attachments.openAttachment(id, onMissing)

    fun setFolder(folder: String) {
        viewModelScope.launch {
            note.value?.let { n ->
                dao.updateNote(n.copy(folder = folder, updatedAt = System.currentTimeMillis()))
                note.value = dao.getNote(noteId.takeIf { it > 0 } ?: n.id)
            }
        }
    }

    fun setSubject(subject: String?) {
        viewModelScope.launch {
            note.value?.let { n ->
                dao.updateNote(n.copy(subject = subject, updatedAt = System.currentTimeMillis()))
                note.value = dao.getNote(noteId.takeIf { it > 0 } ?: n.id)
            }
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
                            title = title.ifBlank { DEFAULT_TITLE },
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
                            title = title.ifBlank { DEFAULT_TITLE },
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
                        title = title.ifBlank { DEFAULT_TITLE },
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
    vm: NoteEditorViewModel = viewModel(
    factory = viewModelFactory {
        initializer {
            NoteEditorViewModel(
                createSavedStateHandle(),
                checkNotNull(this[APPLICATION_KEY]),
            )
        }
    },
)
) {
    val k = LocalKineticColors.current
    val context = LocalContext.current
    val note by vm.note.collectAsStateWithLifecycle()
    val saveState by vm.saveState.collectAsStateWithLifecycle()
    val attachments by vm.attachmentList.collectAsStateWithLifecycle()
    val subjectOptions by vm.subjectOptions.collectAsStateWithLifecycle()

    var title by remember(note) { mutableStateOf(note?.title?.takeUnless { it == DEFAULT_TITLE } ?: "") }
    var body by remember(note) { mutableStateOf(note?.body ?: "") }
    var lastEdit by remember { mutableStateOf(0L) }
    var renaming by remember { mutableStateOf<NoteAttachmentEntity?>(null) }

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

    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        val resolved = uris.filterNotNull()
        if (resolved.isNotEmpty()) {
            // Take persistable grants when offered so retries can re-read sources.
            resolved.forEach { uri ->
                runCatching {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
                    )
                }
            }
            vm.attach(resolved)
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

        // ---- Minimal contextual glass toolbar: undo/redo/checklist/bullet/attach/clear ----
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
                ToolbarAction(label = "📎", desc = "Attach files (PDF, DOCX, PPTX, TXT, images)") {
                    filePicker.launch(arrayOf("*/*"))
                }
                ToolbarAction(label = "✕", desc = "Clear note body", enabled = body.isNotEmpty()) {
                    pushUndo()
                    body = ""
                }
            }
        }
        Spacer(Modifier.height(KineticSpacing.md))

        KineticTextField(value = title, onValueChange = { title = it }, hint = "NOTE TITLE")
        Spacer(Modifier.height(KineticSpacing.md))

        // ---- Context: folder + subject chips (subject options come from the
        // faculty's REAL timetable; empty timetable shows no subject chips) ----
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(KineticSpacing.sm),
        ) {
            FOLDERS.forEach { f ->
                GlassChip(label = f, selected = note?.folder == f, onClick = { vm.setFolder(f) })
            }
        }
        if (subjectOptions.isNotEmpty()) {
            Spacer(Modifier.height(KineticSpacing.sm))
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(KineticSpacing.sm),
            ) {
                GlassChip(label = "NO SUBJECT", selected = note?.subject == null, onClick = { vm.setSubject(null) })
                subjectOptions.forEach { s ->
                    GlassChip(label = s.uppercase(), selected = note?.subject == s, onClick = { vm.setSubject(s) })
                }
            }
        }
        Spacer(Modifier.height(KineticSpacing.md))

        // ---- Attachments ----
        if (attachments.isNotEmpty()) {
            attachments.forEach { att ->
                AttachmentRow(
                    attachment = att,
                    onOpen = { vm.openAttachment(att.id) { /* missing handled below */ } },
                    onRetry = { vm.retryAttachment(att.id) },
                    onRename = { renaming = att },
                    onRemove = { vm.removeAttachment(att.id) },
                )
                Spacer(Modifier.height(KineticSpacing.xs))
            }
            Spacer(Modifier.height(KineticSpacing.sm))
        }

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

    renaming?.let { target ->
        RenameAttachmentDialog(
            currentName = target.displayName,
            onDismiss = { renaming = null },
            onConfirm = { newName ->
                vm.renameAttachment(target.id, newName)
                renaming = null
            },
        )
    }
}

private val FOLDERS = listOf("LECTURES", "MEETINGS", "RESEARCH", "LESSON PLANS", "PERSONAL", "IDEAS")

/** One attachment: glyph, name (tap to open), size/ext, upload state, actions. */
@Composable
private fun AttachmentRow(
    attachment: NoteAttachmentEntity,
    onOpen: () -> Unit,
    onRetry: () -> Unit,
    onRename: () -> Unit,
    onRemove: () -> Unit,
) {
    val k = LocalKineticColors.current
    val stateColor = when (attachment.state) {
        "UPLOADING", "PROCESSING" -> k.mutedForeground
        "SAVED" -> k.statusSuccess
        "SYNCED" -> k.statusSuccess
        "FAILED" -> k.statusError
        else -> k.mutedForeground
    }
    val glyph = when (attachment.ext) {
        "pdf" -> "PDF"
        "doc", "docx" -> "DOC"
        "ppt", "pptx" -> "PPT"
        "txt", "md", "csv" -> "TXT"
        "png", "jpg", "jpeg", "webp", "heic" -> "IMG"
        else -> attachment.ext.uppercase().take(3).ifBlank { "FILE" }
    }
    GlassSurface(strength = GlassStrength.ULTRA_THIN, shape = Glass.shape(Glass.cornerSm)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = KineticSpacing.md, vertical = KineticSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = glyph,
                style = KineticType.labelBold.copy(fontSize = 10.sp),
                color = k.accent,
                modifier = Modifier
                    .size(width = 38.dp, height = 30.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(k.accent.copy(alpha = 0.14f))
                    .wrapContentSize(Alignment.Center),
            )
            Spacer(Modifier.width(KineticSpacing.md))
            Column(Modifier.weight(1f)) {
                Text(
                    text = attachment.displayName,
                    style = KineticType.bodyMedium,
                    color = k.foreground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.clickable(onClick = onOpen),
                )
                Text(
                    text = buildString {
                        append(attachment.state)
                        if (attachment.state == "FAILED" && attachment.errorMessage.isNotBlank()) {
                            append(" · ")
                            append(attachment.errorMessage)
                        } else if (attachment.sizeBytes > 0) {
                            append(" · ")
                            append(humanBytes(attachment.sizeBytes))
                        }
                    },
                    style = KineticType.label.copy(fontSize = 11.sp),
                    color = stateColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (attachment.state == "FAILED") {
                Text(
                    text = "RETRY",
                    style = KineticType.labelBold,
                    color = k.accent,
                    modifier = Modifier
                        .clickable(onClick = onRetry)
                        .padding(horizontal = KineticSpacing.sm, vertical = KineticSpacing.xs),
                )
            }
            Text(
                text = "✎",
                style = KineticType.labelBold,
                color = k.mutedForeground,
                modifier = Modifier
                    .clickable(onClick = onRename)
                    .padding(KineticSpacing.xs)
                    .semantics { contentDescription = "Rename attachment" },
            )
            Text(
                text = "✕",
                style = KineticType.labelBold,
                color = k.mutedForeground,
                modifier = Modifier
                    .clickable(onClick = onRemove)
                    .padding(KineticSpacing.xs)
                    .semantics { contentDescription = "Remove attachment" },
            )
        }
    }
}

@Composable
private fun RenameAttachmentDialog(
    currentName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    val k = LocalKineticColors.current
    var value by remember { mutableStateOf(currentName) }
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        GlassSurface(strength = GlassStrength.THICK, shape = Glass.shape(Glass.cornerLg)) {
            Column(Modifier.padding(KineticSpacing.lg)) {
                Text(text = "RENAME ATTACHMENT", style = KineticType.labelBold, color = k.accent)
                Spacer(Modifier.height(KineticSpacing.md))
                KineticTextField(value = value, onValueChange = { value = it }, hint = "FILE NAME")
                Spacer(Modifier.height(KineticSpacing.md))
                Row(horizontalArrangement = Arrangement.spacedBy(KineticSpacing.sm)) {
                    KineticButton(
                        text = "RENAME",
                        modifier = Modifier.weight(1f),
                        onClick = { if (value.isNotBlank()) onConfirm(value) },
                    )
                    KineticGhostButton(text = "CANCEL", onClick = onDismiss)
                }
            }
        }
    }
}

private fun humanBytes(bytes: Long): String = when {
    bytes >= 1_048_576 -> "%.1f MB".format(bytes / 1_048_576f)
    bytes >= 1024 -> "${bytes / 1024} KB"
    else -> "$bytes B"
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
