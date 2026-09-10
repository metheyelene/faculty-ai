package com.bits.facultyai.ui.notes

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bits.facultyai.data.local.NoteEntity
import com.bits.facultyai.domain.TimeUtils
import com.bits.facultyai.ui.navigation.noteEditorRoute
import com.bits.facultyai.ui.theme.KineticBorder
import com.bits.facultyai.ui.components.KineticDisplayText
import com.bits.facultyai.ui.components.KineticDivider
import com.bits.facultyai.ui.components.KineticEmptyState
import com.bits.facultyai.ui.components.KineticTextField
import com.bits.facultyai.ui.theme.KineticSpacing
import com.bits.facultyai.ui.theme.KineticType
import com.bits.facultyai.ui.theme.LocalKineticColors

private val FOLDERS = listOf("LECTURES", "MEETINGS", "RESEARCH", "LESSON PLANS", "PERSONAL", "IDEAS")

@Composable
fun NotesScreen(
    onNavigate: (String) -> Unit,
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
            .padding(horizontal = KineticSpacing.lg),
    ) {
        Spacer(Modifier.height(KineticSpacing.xl))
        KineticDisplayText(text = "MY NOTES", style = KineticType.display.copy(fontSize = 44.sp))
        Spacer(Modifier.height(KineticSpacing.md))

        KineticTextField(value = search, onValueChange = vm::setSearch, hint = "SEARCH MY NOTES...")

        Spacer(Modifier.height(KineticSpacing.md))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(KineticSpacing.sm)) {
            FolderChip(label = "ALL", selected = folder == null) { vm.setFolder(null) }
            FOLDERS.forEach { f ->
                FolderChip(label = f, selected = folder == f) { vm.setFolder(f) }
            }
        }
        Spacer(Modifier.height(KineticSpacing.lg))

        if (notes.isEmpty()) {
            KineticEmptyState(
                title = "NO NOTES",
                message = "YOUR KNOWLEDGE SPACE IS EMPTY",
                actionText = "NEW NOTE",
                onAction = { vm.createNote("", "LECTURES") { id -> onNavigate(noteEditorRoute(id)) } },
            )
        } else {
            notes.forEach { note ->
                NoteRow(note = note, onClick = { onNavigate(noteEditorRoute(note.id)) })
                KineticDivider()
            }
        }

        Spacer(Modifier.height(KineticSpacing.lg))
        com.bits.facultyai.ui.components.KineticButton(
            text = "NEW NOTE",
            onClick = { vm.createNote("", "LECTURES") { id -> onNavigate(noteEditorRoute(id)) } },
        )
        Spacer(Modifier.height(96.dp))
    }
}

@Composable
private fun FolderChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val k = LocalKineticColors.current
    Box(
        modifier = Modifier
            .border(if (selected) KineticBorder.heavy else KineticBorder.hair, if (selected) k.accent else k.border)
            .background(if (selected) k.accent else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = KineticSpacing.md, vertical = KineticSpacing.xs),
    ) {
        Text(
            text = label,
            style = KineticType.labelBold,
            color = if (selected) k.accentForeground else k.mutedForeground,
        )
    }
}

@Composable
private fun NoteRow(note: NoteEntity, onClick: () -> Unit) {
    val k = LocalKineticColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = KineticSpacing.md),
    ) {
        Column(Modifier.weight(1f)) {
            Text(text = note.title, style = KineticType.bodyMedium, color = k.foreground)
            Text(
                text = note.folder + " · " + TimeUtils.formatDate(java.time.Instant.ofEpochMilli(note.updatedAt).atZone(java.time.ZoneId.systemDefault()).toLocalDate()),
                style = KineticType.label,
                color = k.mutedForeground,
            )
            if (note.body.isNotBlank()) {
                Text(
                    text = note.body.take(80),
                    style = KineticType.label,
                    color = k.mutedForeground,
                    maxLines = 1,
                )
            }
        }
        Text(text = "→", style = KineticType.headingSm, color = k.accent)
    }
}
