package com.bits.facultyai.ui.components

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bits.facultyai.ui.theme.KineticBorder
import com.bits.facultyai.ui.theme.KineticMotion
import com.bits.facultyai.ui.theme.KineticSpacing
import com.bits.facultyai.ui.theme.KineticType
import com.bits.facultyai.ui.theme.LocalKineticColors
import com.bits.facultyai.ui.theme.rememberReducedMotion

/**
 * Liquid Glass surface strengths.
 *
 * ULTRA_THIN — subtle layering over content (chips, stat layers, quiet containers).
 * THIN       — standard contextual glass (cards, list sections).
 * REGULAR    — interactive controls (buttons, inputs, dock).
 * THICK      — anything holding critical info: dialogs, sheets, the composer.
 */
enum class GlassStrength { ULTRA_THIN, THIN, REGULAR, THICK }

/**
 * Liquid Glass primitives — translucent, bordered, edge-lit surfaces.
 *
 * Design rules:
 *  - Glass is reserved for CONTEXTUAL and FLOATING surfaces (dock, composer,
 *    dialogs, stat layers). Structural/editorial chrome (headers, section
 *    rules, big numerals themselves) stays flat ink-on-paper.
 *  - Blur is a progressive enhancement: on Android 12+ with hardware blur
 *    support the fallback opacity is lowered slightly; otherwise the surface
 *    runs more opaque for guaranteed readability. Real backdrop blur is
 *    applied by hosts (dock/dialog) — this surface only guarantees the
 *    translucent layer, border and highlight.
 */
object Glass {

    /** Corner radius used across glass surfaces — moderate, not pill-like. */
    val corner: Dp = 14.dp
    val cornerSm: Dp = 10.dp
    val cornerLg: Dp = 18.dp

    fun shape(corner: Dp = Glass.corner): Shape = RoundedCornerShape(corner)
}

/** Whether the device can afford real backdrop blur (API 31+, motion allowed). */
@Composable
fun rememberBlurSupported(): Boolean {
    val reduced = rememberReducedMotion()
    return remember { Build.VERSION.SDK_INT >= 31 && !reduced }
}

/**
 * The core glass surface: translucent fill + hairline border + a soft lit
 * top edge. [onClick] makes the whole surface tappable with no ripple
 * (press feedback comes from callers adding scale, keeping the glass calm).
 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    strength: GlassStrength = GlassStrength.THIN,
    shape: Shape = Glass.shape(),
    borderColor: Color? = null,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val k = LocalKineticColors.current
    val blur = rememberBlurSupported()
    val baseColor = when (strength) {
        GlassStrength.ULTRA_THIN -> k.glassUltraThin
        GlassStrength.THIN -> k.glassThin
        GlassStrength.REGULAR -> k.glassRegular
        GlassStrength.THICK -> k.glassThick
    }
    // Without hardware blur, raise opacity slightly for readability.
    val boost = if (!blur && strength != GlassStrength.THICK) 0.08f else 0f
    val surface = baseColor.copy(alpha = (baseColor.alpha + boost).coerceAtMost(0.99f))
    val border = when {
        selected -> k.accent
        borderColor != null -> borderColor
        else -> k.glassBorder
    }

    val core = Modifier
        .clip(shape)
        .border(KineticBorder.hair, border, shape)
        .background(surface, shape)
        .drawBehind {
            // Soft highlight along the top edge — the "lit glass" cue.
            drawRect(
                Brush.verticalGradient(
                    colors = listOf(k.glassHighlight, Color.Transparent),
                    startY = 0f,
                    endY = size.height * 0.45f,
                )
            )
        }

    Box(
        modifier = if (onClick != null) {
            val interaction = remember { MutableInteractionSource() }
            val pressed by interaction.collectIsPressedAsState()
            val reduced = rememberReducedMotion()
            val scale by animateFloatAsState(
                targetValue = if (pressed && !reduced) 0.985f else 1f,
                animationSpec = KineticMotion.springFast(),
                label = "glassSurfaceScale",
            )
            core
                .scale(scale)
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                    onClick = onClick,
                )
        } else core,
        content = content,
    )
}

/** Glass card with padded vertical content — the standard contextual container. */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    strength: GlassStrength = GlassStrength.THIN,
    onClick: (() -> Unit)? = null,
    selected: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    GlassSurface(
        modifier = modifier.fillMaxWidth(),
        strength = strength,
        onClick = onClick,
        selected = selected,
    ) {
        Column(
            modifier = Modifier.padding(KineticSpacing.lg),
            content = content,
        )
    }
}

/** Press-aware scale helper shared by glass controls. */
@Composable
private fun rememberPressScale(interaction: MutableInteractionSource): Float {
    val pressed by interaction.collectIsPressedAsState()
    val reduced = rememberReducedMotion()
    val scale by animateFloatAsState(
        targetValue = if (pressed && !reduced) 0.97f else 1f,
        animationSpec = KineticMotion.springFast(),
        label = "glassPressScale",
    )
    return scale
}

/** Primary glass button: accent-selected glass with black text when enabled. */
@Composable
fun GlassButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tintAccent: Boolean = true,
    height: Dp = 52.dp,
) {
    val k = LocalKineticColors.current
    val interaction = remember { MutableInteractionSource() }
    val scale = rememberPressScale(interaction)

    GlassSurface(
        modifier = modifier
            .height(height)
            .scale(scale),
        strength = GlassStrength.REGULAR,
        selected = tintAccent && enabled,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                    enabled = enabled,
                    onClick = onClick,
                )
                .padding(horizontal = KineticSpacing.lg),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Text(
                text = text.uppercase(),
                style = KineticType.labelBold.copy(fontSize = 13.sp, letterSpacing = 1.5.sp),
                color = when {
                    !enabled -> k.mutedForeground
                    tintAccent -> k.accentForeground
                    else -> k.foreground
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Small glass chip — filters and selectors with an accent selected state. */
@Composable
fun GlassChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val k = LocalKineticColors.current
    GlassSurface(
        modifier = modifier,
        strength = GlassStrength.ULTRA_THIN,
        shape = Glass.shape(Glass.cornerSm),
        selected = selected,
    ) {
        Row(
            modifier = Modifier
                .clickable(onClick = onClick)
                .padding(horizontal = KineticSpacing.md, vertical = KineticSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label.uppercase(),
                style = KineticType.labelBold,
                color = if (selected) k.foreground else k.mutedForeground,
            )
        }
    }
}

/** Glass input: translucent surface, accent focus border, always-readable text. */
@Composable
fun GlassTextField(
    value: String,
    onValueChange: (String) -> Unit,
    hint: String,
    modifier: Modifier = Modifier,
    minLines: Int = 1,
    maxLines: Int = 1,
    isError: Boolean = false,
    errorMessage: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    minHeight: Dp = 48.dp,
) {
    val k = LocalKineticColors.current
    var focused by remember { mutableStateOf(false) }
    val borderColor = when {
        isError -> k.statusError
        focused -> k.accent
        else -> k.glassBorder
    }
    GlassSurface(
        modifier = modifier.fillMaxWidth(),
        strength = GlassStrength.REGULAR,
        borderColor = borderColor,
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = KineticType.body.copy(fontSize = 16.sp, color = k.foreground),
            cursorBrush = SolidColor(k.accent),
            minLines = minLines,
            maxLines = maxLines,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = minHeight)
                .onFocusChanged { focused = it.isFocused }
                .padding(horizontal = KineticSpacing.md, vertical = KineticSpacing.md),
            decorationBox = { inner ->
                Column {
                    if (value.isEmpty()) {
                        Text(
                            text = hint,
                            style = KineticType.body.copy(fontSize = 16.sp),
                            color = k.mutedForeground,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    inner()
                }
            },
        )
    }
    if (isError && errorMessage != null) {
        Text(
            text = errorMessage,
            style = KineticType.label,
            color = k.statusError,
            modifier = Modifier.padding(top = KineticSpacing.xs),
        )
    }
}

/**
 * Glass dialog container — THICK strength with an accent border, used inside
 * every Dialog host so modals feel like part of the same glass system.
 */
@Composable
fun GlassDialogSurface(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val k = LocalKineticColors.current
    val shape = Glass.shape(Glass.cornerLg)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .border(KineticBorder.hair, k.accent, shape)
            .background(k.glassThick, shape)
            .drawBehind {
                drawRect(
                    Brush.verticalGradient(
                        colors = listOf(k.glassHighlight, Color.Transparent),
                        startY = 0f,
                        endY = size.height * 0.35f,
                    )
                )
            }
            .padding(KineticSpacing.lg),
        content = content,
    )
}

/** Editorial empty state on a quiet glass layer. */
@Composable
fun GlassEmptyState(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    actionText: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val k = LocalKineticColors.current
    GlassCard(modifier = modifier, strength = GlassStrength.ULTRA_THIN) {
        Text(
            text = title.uppercase(),
            style = KineticType.heading,
            color = k.foreground,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        androidx.compose.foundation.layout.Spacer(Modifier.height(KineticSpacing.sm))
        Text(
            text = message.uppercase(),
            style = KineticType.label,
            color = k.mutedForeground,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        if (actionText != null && onAction != null) {
            androidx.compose.foundation.layout.Spacer(Modifier.height(KineticSpacing.lg))
            Row(
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth(),
            ) {
                GlassButton(text = actionText, onClick = onAction)
            }
        }
    }
}

/**
 * Glass top bar — a THICK floating slab for secondary screens: back
 * affordance, small uppercase context title and a trailing action slot.
 * Deliberately near-opaque (THICK) so it stays readable over scrolling
 * content without needing its own backdrop blur; the dock remains the one
 * blurred surface.
 */
@Composable
fun GlassTopBar(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    val k = LocalKineticColors.current
    GlassSurface(
        modifier = modifier.fillMaxWidth(),
        strength = GlassStrength.THICK,
        shape = RectangleShape,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .padding(horizontal = KineticSpacing.md, vertical = KineticSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onBack != null) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(Glass.shape(Glass.cornerSm))
                        .clickable(onClickLabel = "Back") { onBack() }
                        .wrapContentSize(Alignment.Center),
                ) {
                    Text(
                        text = "←",
                        style = KineticType.headingSm,
                        color = k.accent,
                    )
                }
                Spacer(Modifier.width(KineticSpacing.sm))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    text = title.uppercase(),
                    style = KineticType.labelBold.copy(fontSize = 13.sp, letterSpacing = 1.5.sp),
                    color = k.foreground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = KineticType.label.copy(fontSize = 11.sp),
                        color = k.mutedForeground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            trailing()
        }
    }
}

/**
 * Oversized numeral on a restrained glass layer — the signature
 * Kinetic + Glass composition (large number, uppercase caption beneath).
 */
@Composable
fun GlassStat(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    emphasized: Boolean = false,
) {
    val k = LocalKineticColors.current
    GlassSurface(modifier = modifier, strength = GlassStrength.ULTRA_THIN) {
        Column(
            modifier = Modifier
                .padding(horizontal = KineticSpacing.lg, vertical = KineticSpacing.md)
                .clearAndSetSemantics { },
        ) {
            Text(
                text = value,
                style = KineticType.statNumber,
                color = if (emphasized) k.accent else k.foreground,
            )
            Text(
                text = label.uppercase(),
                style = KineticType.label,
                color = k.mutedForeground,
            )
        }
    }
}
