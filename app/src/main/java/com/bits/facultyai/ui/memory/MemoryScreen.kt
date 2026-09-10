package com.bits.facultyai.ui.memory

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bits.facultyai.ui.components.KineticBadge
import com.bits.facultyai.ui.theme.KineticBorder
import com.bits.facultyai.ui.components.KineticButton
import com.bits.facultyai.ui.components.KineticDivider
import com.bits.facultyai.ui.components.KineticDisplayText
import com.bits.facultyai.ui.components.KineticGhostButton
import com.bits.facultyai.ui.components.KineticSectionHeader
import com.bits.facultyai.ui.theme.KineticSpacing
import com.bits.facultyai.ui.theme.KineticType
import com.bits.facultyai.ui.theme.LocalKineticColors

@Composable
fun MemoryScreen(vm: MemoryViewModel = viewModel()) {
    val k = LocalKineticColors.current
    val memories by vm.memories.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    var editingId by remember { mutableStateOf<Long?>(null) }
    var editText by remember { mutableStateOf("") }
    var confirmClear by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(k.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = KineticSpacing.lg),
    ) {
        Spacer(Modifier.height(KineticSpacing.xl))
        KineticDisplayText(text = "MY MEMORY", style = KineticType.display.copy(fontSize = 44.sp))
        Text(
            text = "What your assistant remembers — only from what you allowed",
            style = KineticType.label,
            color = k.mutedForeground,
        )
        Spacer(Modifier.height(KineticSpacing.xl))

        KineticSectionHeader(title = "CONTROLS")
        ToggleRow(
            label = "ASSISTANT MEMORY",
            description = "Allow the assistant to save new memories",
            checked = settings?.memoryEnabled ?: true,
            onToggle = { vm.setMemoryEnabled(it) },
        )
        ToggleRow(
            label = "AI ACCESS TO MY NOTES",
            description = "Let the assistant read your notes when you ask about them",
            checked = settings?.aiAccessToNotes ?: true,
            onToggle = { vm.setAiNotes(it) },
        )
        ToggleRow(
            label = "PERSONALIZED NOTIFICATIONS",
            description = "Reminders speak to you and your schedule",
            checked = settings?.personalizedNotifications ?: true,
            onToggle = { vm.setPersonalizedNotifications(it) },
        )

        KineticSectionHeader(title = "SAVED MEMORIES", trailing = { Text(text = memories.size.toString(), style = KineticType.labelBold, color = k.accent) })
        if (memories.isEmpty()) {
            Text(text = "NOTHING SAVED YET", style = KineticType.label, color = k.mutedForeground)
        } else {
            memories.forEach { memory ->
                if (editingId == memory.id) {
                    var temp by remember(memory.id) { mutableStateOf(memory.text) }
                    com.bits.facultyai.ui.components.KineticTextField(value = temp, onValueChange = { temp = it }, hint = "EDIT MEMORY")
                    Row(horizontalArrangement = Arrangement.spacedBy(KineticSpacing.sm), modifier = Modifier.padding(vertical = KineticSpacing.sm)) {
                        KineticButton(text = "SAVE", onClick = { vm.updateMemory(memory.id, temp); editingId = null }, height = 36)
                        KineticGhostButton(text = "CANCEL", onClick = { editingId = null })
                    }
                } else {
                    Column(Modifier.padding(vertical = KineticSpacing.md)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            KineticBadge(text = memory.category)
                            Spacer(Modifier.width(KineticSpacing.sm))
                            Text(text = memory.source, style = KineticType.label.copy(fontSize = 11.sp), color = k.mutedForeground)
                        }
                        Spacer(Modifier.height(KineticSpacing.xs))
                        Text(text = memory.text, style = KineticType.bodyMedium, color = k.foreground)
                        Row(horizontalArrangement = Arrangement.spacedBy(KineticSpacing.lg), modifier = Modifier.padding(top = KineticSpacing.xs)) {
                            Text(
                                text = "EDIT",
                                style = KineticType.labelBold,
                                color = k.accent,
                                modifier = Modifier.clickable { editingId = memory.id },
                            )
                            Text(
                                text = "DELETE",
                                style = KineticType.labelBold,
                                color = k.mutedForeground,
                                modifier = Modifier.clickable { vm.deleteMemory(memory.id) },
                            )
                        }
                    }
                    KineticDivider()
                }
            }
        }

        Spacer(Modifier.height(KineticSpacing.xl))
        if (confirmClear) {
            Text(text = "DELETE ALL MEMORIES? THIS CANNOT BE UNDONE.", style = KineticType.labelBold, color = k.statusError)
            Spacer(Modifier.height(KineticSpacing.sm))
            Row(horizontalArrangement = Arrangement.spacedBy(KineticSpacing.sm)) {
                KineticButton(text = "YES, CLEAR ALL", onClick = { vm.clearAll(); confirmClear = false })
                KineticGhostButton(text = "CANCEL", onClick = { confirmClear = false })
            }
        } else {
            com.bits.facultyai.ui.components.KineticOutlinedButton(
                text = "CLEAR ALL MEMORY",
                onClick = { confirmClear = true },
            )
        }
        Spacer(Modifier.height(96.dp))
    }
}

@Composable
private fun ToggleRow(label: String, description: String, checked: Boolean, onToggle: (Boolean) -> Unit) {
    val k = LocalKineticColors.current
    val context = androidx.compose.ui.platform.LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = KineticSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(text = label, style = KineticType.bodyMedium, color = k.foreground)
            Text(text = description, style = KineticType.label, color = k.mutedForeground)
        }
        // Track color cross-fades; knob slides on a fast spring (level-1 motion).
        val trackColor by androidx.compose.animation.animateColorAsState(
            targetValue = if (checked) k.accent else Color.Transparent,
            animationSpec = tween(com.bits.facultyai.ui.theme.KineticMotion.FAST_MS),
            label = "toggleTrack",
        )
        val knobAlignment by androidx.compose.animation.core.animateDpAsState(
            targetValue = if (checked) 4.dp else (-4).dp,
            animationSpec = com.bits.facultyai.ui.theme.KineticMotion.springFast(),
            label = "toggleKnob",
        )
        Box(
            modifier = Modifier
                .size(width = 44.dp, height = 24.dp)
                .border(KineticBorder.heavy, if (checked) k.accent else k.border)
                .background(trackColor)
                .clickable {
                    com.bits.facultyai.ui.components.KineticHaptics.toggle(context)
                    onToggle(!checked)
                },
            contentAlignment = Alignment.CenterEnd,
        ) {
            Box(
                Modifier
                    .padding(end = knobAlignment)
                    .size(14.dp)
                    .background(if (checked) k.accentForeground else k.mutedForeground),
            )
        }
    }
}
