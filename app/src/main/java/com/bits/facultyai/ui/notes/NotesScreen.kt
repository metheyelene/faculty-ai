package com.bits.facultyai.ui.notes

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bits.facultyai.domain.TimeUtils
import com.bits.facultyai.ui.navigation.noteEditorRoute
import com.bits.facultyai.ui.components.GlassChip
import com.bits.facultyai.ui.components.GlassEmptyState
import com.bits.facultyai.ui.components.GlassTopBar
import com.bits.facultyai.ui.components.KineticDivider
import com.bits.facultyai.ui.components.KineticTextField
import com.bits.facultyai.ui.theme.KineticSpacing
import com.bits.facultyai.ui.theme.KineticType
import com.bits.facultyai.ui.theme.LocalKineticColors

private val FOLDERS = listOf("LECTURES", "MEETINGS", "RESEARCH", "LESSON PLANS", "PERSONAL", "IDEAS")

@Composable
fun NotesScreen(
    onNavigate: (String) -> Unit,
    onBack: () -> Unit = {},
    vm: NotesViewModel = viewModel(),
) {
    val k = LocalKineticColors.current
    val notes by vm.notes.collectAsStateWithLifecycle()
    val search by vm.searchQuery.collectAsStateWithLifecycle()
    val folder by vm.selectedFolder.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(k.background)
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(horizontal = KineticSpacing.lg),
    ) {
        GlassTopBar(title = "MY NOTES", onBack = onBack)
        Spacer(Modifier.height(KineticSpacing.md))

        KineticTextField(value = search, onValueChange = vm::setSearch, hint = "SEARCH NOTES, SUBJECTS, FILES...")

        Spacer(Modifier.height(KineticSpacing.md))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(KineticSpacing.sm)) {
            FolderChip(label = "ALL", selected = folder == null) { vm.setFolder(null) }
            FOLDERS.forEach { f ->
                FolderChip(label = f, selected = folder == f) { vm.setFolder(f) }
            }
        }
        Spacer(Modifier.height(KineticSpacing.lg))

        when {
            notes.isEmpty() && search.isBlank() && folder == null -> {
                GlassEmptyState(
                    title = "NO NOTES",
                    message = "CREATE YOUR FIRST NOTE OR ATTACH A FILE",
                    actionText = "NEW NOTE",
                    onAction = { vm.createNote("", "LECTURES") { id -> onNavigate(noteEditorRoute(id)) } },
                )
            }
            notes.isEmpty() -> {
                GlassEmptyState(
                    title = "NOTHING FOUND",
                    message = if (folder == null) "NO NOTES MATCH YOUR SEARCH" else "NO NOTES IN $folder YET",
                )
            }
            else -> {
                notes.forEach { row ->
                    NoteRow(row = row, onClick = { onNavigate(noteEditorRoute(row.note.id)) })
                    KineticDivider()
                }
            }
        }

        Spacer(Modifier.height(KineticSpacing.lg))
        com.bits.facultyai.ui.components.KineticButton(
            text = "NEW NOTE",
            onClick = { vm.createNote("", "LECTURES") { id -> onNavigate(noteEditorRoute(id)) } },
        )
        Spacer(Modifier.height(KineticSpacing.xl))
    }
}

@Composable
private fun FolderChip(label: String, selected: Boolean, onClick: () -> Unit) {
    GlassChip(label = label, selected = selected, onClick = onClick)
}

@Composable
private fun NoteRow(row: NoteUi, onClick: () -> Unit) {
    val k = LocalKineticColors.current
    val note = row.note
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = KineticSpacing.md),
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = note.title,
                    style = KineticType.bodyMedium,
                    color = k.foreground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (row.attachmentCount > 0) {
                    Spacer(Modifier.width(KineticSpacing.sm))
                    Text(
                        text = "📎 ${row.attachmentCount}",
                        style = KineticType.label.copy(fontSize = 11.sp),
                        color = k.accent,
                    )
                }
            }
            val meta = buildList {
                add(note.folder)
                note.subject?.takeIf { it.isNotBlank() }?.let { add(it) }
                add(TimeUtils.formatDate(java.time.Instant.ofEpochMilli(note.updatedAt).atZone(java.time.ZoneId.systemDefault()).toLocalDate()))
            }.joinToString(" · ")
            Text(
                text = meta,
                style = KineticType.label,
                color = k.mutedForeground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (note.body.isNotBlank()) {
                Text(
                    text = note.body.take(80),
                    style = KineticType.label,
                    color = k.mutedForeground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(KineticSpacing.sm))
        row.syncLabel?.let { label ->
            val color = when (label) {
                "FAILED" -> k.statusError
                "SYNCING" -> k.mutedForeground
                "SAVED", "SYNCED" -> k.statusSuccess
                else -> k.mutedForeground
            }
            Text(
                text = label,
                style = KineticType.labelBold.copy(fontSize = 10.sp),
                color = color,
                modifier = Modifier.align(Alignment.CenterVertically),
            )
        }
        Text(text = "→", style = KineticType.headingSm, color = k.accent)
    }
}
