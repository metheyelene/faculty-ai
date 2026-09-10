package com.bits.facultyai.ui.assistant

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bits.facultyai.domain.ContextEngine
import com.bits.facultyai.domain.FacultyAssistant
import com.bits.facultyai.ui.components.GlassCard
import com.bits.facultyai.ui.components.GlassChip
import com.bits.facultyai.ui.components.GlassStrength
import com.bits.facultyai.ui.components.GlassSurface
import com.bits.facultyai.ui.components.GlassTextField
import com.bits.facultyai.ui.components.GlassDialogSurface
import com.bits.facultyai.ui.components.KineticBadge
import com.bits.facultyai.ui.components.KineticButton
import com.bits.facultyai.ui.components.KineticGhostButton
import com.bits.facultyai.ui.theme.KineticBorder
import com.bits.facultyai.ui.theme.KineticSpacing
import com.bits.facultyai.ui.theme.KineticType
import com.bits.facultyai.ui.theme.LocalKineticColors

@Composable
fun AssistantScreen(vm: AssistantViewModel = viewModel()) {
    val k = LocalKineticColors.current
    val chat by vm.chat.collectAsStateWithLifecycle()
    val phase by vm.phase.collectAsStateWithLifecycle()
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val clipboard = LocalClipboardManager.current

    LaunchedEffect(chat.size) {
        if (chat.isNotEmpty()) listState.animateScrollToItem(chat.size - 1)
    }

    // Context-aware suggestions grounded in real data.
    val profile by vm.settings.collectAsStateWithLifecycle()
    val suggestions = remember(chat.size) {
        listOf(
            "What is my next class?",
            "What do I have tomorrow?",
            "What are my open tasks?",
            "Create a lesson plan",
            "Find my notes about DSP",
            "What did I save about my students?",
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(k.background)
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = KineticSpacing.lg),
    ) {
        Spacer(Modifier.height(KineticSpacing.xl))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(text = "MY", style = KineticType.display, color = k.foreground)
                Text(text = "ASSISTANT", style = KineticType.display, color = k.accent)
            }
            KineticGhostButton(text = "CLEAR", onClick = { vm.clearConversation() })
        }
        Spacer(Modifier.height(KineticSpacing.lg))

        // Suggested prompts — glass chips
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(KineticSpacing.sm),
        ) {
            suggestions.forEach { suggestion ->
                GlassChip(
                    label = suggestion,
                    selected = false,
                    onClick = { if (phase != AssistantPhase.THINKING) input = suggestion },
                )
            }
        }
        Spacer(Modifier.height(KineticSpacing.md))

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(KineticSpacing.lg),
        ) {
            items(chat) { message ->
                ChatMessageView(
                    message = message,
                    onRemember = { vm.confirmMemory(message.pendingMemory ?: "") },
                    onNotNow = { vm.dismissMemoryOffer() },
                    onCopy = { clipboard.setText(AnnotatedString(message.lines.joinToString("\n"))) },
                    onRetry = { vm.retry() },
                )
            }
            if (phase == AssistantPhase.THINKING) {
                item { ThinkingRow() }
            }
        }

        Spacer(Modifier.height(KineticSpacing.md))

        // ---- Composer: glass input, always-visible, high-contrast ----
        Row(verticalAlignment = Alignment.Bottom) {
            GlassTextField(
                value = input,
                onValueChange = { input = it },
                hint = "Ask about your day, notes, tasks…",
                modifier = Modifier.weight(1f),
                maxLines = 5,
                minHeight = 52.dp,
            )
            Spacer(Modifier.width(KineticSpacing.sm))
            KineticButton(
                text = if (phase == AssistantPhase.THINKING) "…" else "ASK",
                onClick = {
                    val q = input.trim()
                    if (q.isNotEmpty()) {
                        vm.ask(q)
                        input = ""
                    }
                },
                enabled = phase != AssistantPhase.THINKING && input.isNotBlank(),
                height = 52,
            )
            // The disabled ASK button stays tappable-height; no layout shift
            // when it flips enabled as the user types.
        }
        Spacer(Modifier.height(KineticSpacing.sm))
        Text(
            text = "ANSWERS COME FROM YOUR SCHEDULE, TASKS, NOTES AND SAVED MEMORIES",
            style = KineticType.label.copy(fontSize = 11.sp),
            color = k.mutedForeground,
        )
        Spacer(Modifier.height(KineticSpacing.sm))
    }
}

/** Animated orb row shown while the assistant thinks. */
@Composable
private fun ThinkingRow() {
    val k = LocalKineticColors.current
    val phaseText by rememberInfiniteTransition(label = "thinkCycle").animateFloat(
        initialValue = 0f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(
            tween(2400, easing = LinearEasing),
            RepeatMode.Restart,
        ),
        label = "thinkPhase",
    )
    val reduced = com.bits.facultyai.ui.theme.rememberReducedMotion()
    val label = if (reduced) "THINKING…" else when (phaseText.toInt()) {
        0 -> "THINKING…"
        1 -> "READING YOUR CONTEXT…"
        2 -> "ANALYZING…"
        else -> "COMPOSING…"
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AssistantOrb(active = true)
        Spacer(Modifier.width(KineticSpacing.md))
        Text(
            text = label,
            style = KineticType.labelBold,
            color = k.mutedForeground,
        )
    }
}

/**
 * Assistant orb — ambient level-4 motion.
 * IDLE: extremely subtle breathing. THINKING: livelier pulse + glow.
 * ERROR: restrained, status-colored. All decorative motion is disabled
 * under the system reduced-motion preference.
 */
@Composable
fun AssistantOrb(
    active: Boolean,
    modifier: Modifier = Modifier,
    error: Boolean = false,
) {
    val k = LocalKineticColors.current
    val reduced = com.bits.facultyai.ui.theme.rememberReducedMotion()
    val size = 36.dp
    val coreColor = if (error) k.statusError else k.accent

    // Continuous animations only while active and motion is allowed.
    val transition = rememberInfiniteTransition(label = "orb")
    val breathing by transition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(tween(1600, easing = LinearEasing), RepeatMode.Reverse),
        label = "breath",
    )
    val pulse by transition.animateFloat(
        initialValue = 0.88f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Reverse),
        label = "pulse",
    )
    val glow by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Reverse),
        label = "glow",
    )

    val coreScale = when {
        reduced -> 1f
        active -> pulse
        else -> breathing
    }
    val glowAlpha = when {
        reduced || !active -> 0.28f
        else -> glow
    }

    Box(
        modifier
            .size(size)
            .clip(RoundedCornerShape(50))
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        coreColor.copy(alpha = glowAlpha),
                        coreColor.copy(alpha = glowAlpha * 0.35f),
                        Color.Transparent,
                    )
                )
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(14.dp)
                .scale(coreScale)
                .clip(RoundedCornerShape(50))
                .background(coreColor),
        )
    }
}

@Composable
private fun ChatMessageView(
    message: ChatMessage,
    onRemember: () -> Unit,
    onNotNow: () -> Unit,
    onCopy: () -> Unit,
    onRetry: () -> Unit,
) {
    val k = LocalKineticColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(
                animationSpec = com.bits.facultyai.ui.theme.KineticMotion.springStandard(),
            ),
    ) {
        if (message.isUser) {
            // User message: quiet editorial block — right-weighted label, no bubble.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "YOU",
                    style = KineticType.labelBold,
                    color = k.mutedForeground,
                )
                Spacer(Modifier.weight(1f))
            }
            Spacer(Modifier.height(KineticSpacing.xs))
            message.lines.forEach { line ->
                Text(text = line, style = KineticType.bodyMedium, color = k.foreground)
                Spacer(Modifier.height(2.dp))
            }
        } else {
            // AI response: document-style glass sheet with typographic hierarchy.
            GlassSurface(strength = GlassStrength.THIN, borderColor = if (message.isError) k.statusError else null) {
                Column(Modifier.padding(KineticSpacing.lg)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AssistantOrb(
                            active = false,
                            error = message.isError,
                            modifier = Modifier.size(20.dp).padding(end = 6.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = if (message.isError) "ERROR" else "ACADORA AI",
                            style = KineticType.labelBold,
                            color = if (message.isError) k.statusError else k.accent,
                        )
                        Spacer(Modifier.weight(1f))
                        if (!message.isError) {
                            Text(
                                text = "COPY",
                                style = KineticType.label.copy(fontSize = 11.sp),
                                color = k.accent,
                                modifier = Modifier
                                    .clickable(onClick = onCopy)
                                    .padding(KineticSpacing.xs),
                            )
                        }
                    }
                    Spacer(Modifier.height(KineticSpacing.sm))
                    message.lines.forEachIndexed { i, line ->
                        // First line = document heading when the engine emits one.
                        val isHeading = !message.isError && i == 0 &&
                            line.length < 42 && !line.endsWith(".")
                        Text(
                            text = line,
                            style = if (isHeading) KineticType.headingSm else KineticType.body,
                            color = if (isHeading) k.foreground else k.foreground,
                        )
                        Spacer(Modifier.height(3.dp))
                    }
                    if (message.sources.isNotEmpty()) {
                        Spacer(Modifier.height(KineticSpacing.sm))
                        Row(horizontalArrangement = Arrangement.spacedBy(KineticSpacing.sm)) {
                            message.sources.take(2).forEach { source ->
                                KineticBadge(text = source.kind + ": " + source.detail)
                            }
                        }
                    }
                    when {
                        message.isError -> {
                            Spacer(Modifier.height(KineticSpacing.sm))
                            KineticButton(text = "TRY AGAIN", onClick = onRetry, height = 40)
                        }
                        message.pendingMemory != null -> {
                            Spacer(Modifier.height(KineticSpacing.sm))
                            Row(horizontalArrangement = Arrangement.spacedBy(KineticSpacing.sm)) {
                                KineticButton(text = "REMEMBER", onClick = onRemember, height = 40)
                                KineticGhostButton(text = "NOT NOW", onClick = onNotNow)
                            }
                        }
                    }
                }
            }
        }
    }
}
