package com.bits.facultyai.ui.assistant

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bits.facultyai.ui.components.KineticBadge
import com.bits.facultyai.ui.theme.KineticBorder
import com.bits.facultyai.ui.components.KineticButton
import com.bits.facultyai.ui.components.KineticGhostButton
import com.bits.facultyai.ui.components.KineticSectionHeader
import com.bits.facultyai.ui.theme.KineticSpacing
import com.bits.facultyai.ui.theme.KineticType
import com.bits.facultyai.ui.theme.LocalKineticColors

@Composable
fun AssistantScreen(vm: AssistantViewModel = viewModel()) {
    val k = LocalKineticColors.current
    val chat by vm.chat.collectAsStateWithLifecycle()
    val memories by vm.memories.collectAsStateWithLifecycle()
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(chat.size) {
        if (chat.isNotEmpty()) listState.animateScrollToItem(chat.size - 1)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(k.background)
            .padding(horizontal = KineticSpacing.lg),
    ) {
        Spacer(Modifier.height(KineticSpacing.xl))
        Text(text = "MY", style = KineticType.display, color = k.foreground)
        Text(text = "ASSISTANT", style = KineticType.display, color = k.accent)
        Spacer(Modifier.height(KineticSpacing.lg))

        // Context-aware suggestions
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(KineticSpacing.sm),
        ) {
            listOf(
                "What is my next class?",
                "What do I have tomorrow?",
                "What are my open tasks?",
                "Create a lesson plan",
                "Find my notes about DSP",
            ).forEach { suggestion ->
                Box(
                    modifier = Modifier
                        .border(KineticBorder.hair, k.border)
                        .clickable { input = suggestion }
                        .padding(horizontal = KineticSpacing.md, vertical = KineticSpacing.xs),
                ) {
                    Text(text = suggestion, style = KineticType.label, color = k.mutedForeground)
                }
            }
        }
        Spacer(Modifier.height(KineticSpacing.md))

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(KineticSpacing.lg),
        ) {
            items(chat) { message ->
                ChatMessageView(message = message,
                    onRemember = { vm.confirmMemory(message.pendingMemory ?: "") },
                    onNotNow = { vm.dismissMemoryOffer() })
            }
        }

        Spacer(Modifier.height(KineticSpacing.md))
        Row(verticalAlignment = Alignment.CenterVertically) {
            BasicTextField(
                value = input,
                onValueChange = { input = it },
                textStyle = KineticType.body.copy(color = k.foreground),
                cursorBrush = SolidColor(k.accent),
                modifier = Modifier
                    .weight(1f)
                    .border(KineticBorder.hair, k.border)
                    .padding(KineticSpacing.md),
                decorationBox = { inner ->
                    if (input.isEmpty()) {
                        Text("Ask about your day, notes, tasks...", style = KineticType.body, color = k.mutedForeground)
                    }
                    inner()
                },
            )
            Spacer(Modifier.width(KineticSpacing.md))
            KineticButton(
                text = "ASK",
                onClick = { vm.ask(input); input = "" },
                height = 48,
            )
        }
        Spacer(Modifier.height(KineticSpacing.lg))
        Text(
            text = "ANSWERS COME FROM YOUR SCHEDULE, TASKS, NOTES AND SAVED MEMORIES",
            style = KineticType.label.copy(fontSize = 9.sp),
            color = k.mutedForeground,
        )
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun ChatMessageView(
    message: ChatMessage,
    onRemember: () -> Unit,
    onNotNow: () -> Unit,
) {
    val k = LocalKineticColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .let { m ->
                if (message.isUser) m
                else m.border(KineticBorder.hair, k.border).padding(KineticSpacing.lg)
            },
    ) {
        if (!message.isUser) {
            Text(text = "FACULTY AI", style = KineticType.labelBold, color = k.accent)
            Spacer(Modifier.height(KineticSpacing.xs))
        } else {
            Text(text = "YOU", style = KineticType.labelBold, color = k.mutedForeground)
            Spacer(Modifier.height(KineticSpacing.xs))
        }
        message.lines.forEach { line ->
            Text(
                text = line,
                style = if (message.isUser) KineticType.bodyMedium else KineticType.body,
                color = k.foreground,
            )
            Spacer(Modifier.height(2.dp))
        }
        if (message.sources.isNotEmpty()) {
            Spacer(Modifier.height(KineticSpacing.sm))
            Row(horizontalArrangement = Arrangement.spacedBy(KineticSpacing.sm)) {
                message.sources.take(2).forEach { source ->
                    KineticBadge(text = source.kind + ": " + source.detail)
                }
            }
        }
        if (message.pendingMemory != null) {
            Spacer(Modifier.height(KineticSpacing.sm))
            Row(horizontalArrangement = Arrangement.spacedBy(KineticSpacing.sm)) {
                KineticButton(text = "REMEMBER", onClick = onRemember, height = 36)
                KineticGhostButton(text = "NOT NOW", onClick = onNotNow)
            }
        }
    }
}
