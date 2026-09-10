package com.bits.facultyai.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bits.facultyai.ui.theme.*

/** Screen scaffold with standard horizontal padding. */
@Composable
fun KineticScreen(
    modifier: Modifier = Modifier,
    topBar: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = modifier.fillMaxSize()) {
        topBar?.invoke()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = KineticSpacing.lg),
            content = content,
        )
    }
}

/** Giant editorial title. Uppercase, tight tracking, optional line-by-line reveal. */
@Composable
fun KineticDisplayText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = KineticType.display,
    color: Color = LocalKineticColors.current.foreground,
) {
    Text(
        text = text.uppercase(),
        style = style,
        color = color,
        modifier = modifier,
    )
}

/** Section header: small bold uppercase label over a structural rule. */
@Composable
fun KineticSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = KineticSpacing.xl, bottom = KineticSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title.uppercase(),
            style = KineticType.labelBold,
            color = LocalKineticColors.current.foreground,
        )
        Spacer(Modifier.width(KineticSpacing.md))
        Box(
            Modifier
                .weight(1f)
                .height(1.dp)
                .background(LocalKineticColors.current.border)
        )
        if (trailing != null) {
            Spacer(Modifier.width(KineticSpacing.md))
            trailing()
        }
    }
}

/** Large numerical statistic with an uppercase caption underneath. */
@Composable
fun KineticStat(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    emphasized: Boolean = false,
) {
    val k = LocalKineticColors.current
    Column(modifier = modifier.clearAndSetSemantics {}) {
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

/** Flat structural card: border instead of shadow, optional accent stripe. */
@Composable
fun KineticCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    accentStripe: Boolean = false,
    selected: Boolean = false,
    content: @Composable RowScope.() -> Unit,
) {
    val k = LocalKineticColors.current
    val borderColor = if (selected) k.accent else k.border
    val bg = if (selected) k.muted else Color.Transparent
    Row(
        modifier = modifier
            .fillMaxWidth()
            .let {
                if (onClick != null) it.clickable(onClick = onClick) else it
            }
            .border(KineticBorder.hair, borderColor)
            .background(bg)
            .padding(KineticSpacing.lg),
        content = content,
    )
    if (accentStripe) {
        // rendered via parent composition; stripe drawn as part of card in variant below
    }
}

/** Flat bordered card with vertical content. */
@Composable
fun KineticBlock(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    borderColor: Color? = null,
    backgroundColor: Color = Color.Transparent,
    content: @Composable ColumnScope.() -> Unit,
) {
    val k = LocalKineticColors.current
    Column(
        modifier = modifier
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .border(KineticBorder.hair, borderColor ?: k.border)
            .background(backgroundColor)
            .padding(KineticSpacing.lg)
            .animateContentSize(tween(KineticMotion.MEDIUM_MS)),
        content = content,
    )
}

/** Press-scale micro-interaction wrapper. */
@Composable
fun KineticPressScale(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = tween(KineticMotion.FAST_MS),
        label = "pressScale",
    )
    Box(
        modifier = modifier
            .scale(scale)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
    ) {
        content()
    }
}

/** Primary button: accent background, black text, sharp corners, bold uppercase. */
@Composable
fun KineticButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    height: Int = 52,
) {
    val k = LocalKineticColors.current
    val interaction = remember { MutableInteractionSource() }
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = KineticShape.sharp,
        interactionSource = interaction,
        colors = ButtonDefaults.buttonColors(
            containerColor = k.accent,
            contentColor = k.accentForeground,
            disabledContainerColor = k.muted,
            disabledContentColor = k.mutedForeground,
        ),
        contentPadding = PaddingValues(horizontal = KineticSpacing.lg, vertical = KineticSpacing.md),
        modifier = modifier
            .height(height.dp)
            .kineticPressable(interaction, pressedScale = 0.96f),
    ) {
        Text(
            text = text.uppercase(),
            style = KineticType.labelBold.copy(fontSize = 13.sp, letterSpacing = 1.5.sp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Secondary button: transparent with 2px structural border. */
@Composable
fun KineticOutlinedButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    height: Int = 52,
) {
    val k = LocalKineticColors.current
    val interaction = remember { MutableInteractionSource() }
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = KineticShape.sharp,
        interactionSource = interaction,
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = k.foreground,
            disabledContentColor = k.mutedForeground,
        ),
        border = BorderStroke(KineticBorder.heavy, if (enabled) k.foreground else k.border),
        contentPadding = PaddingValues(horizontal = KineticSpacing.lg, vertical = KineticSpacing.md),
        modifier = modifier
            .height(height.dp)
            .kineticPressable(interaction, pressedScale = 0.96f),
    ) {
        Text(
            text = text.uppercase(),
            style = KineticType.labelBold.copy(fontSize = 13.sp, letterSpacing = 1.5.sp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Ghost button: no border, no background. */
@Composable
fun KineticGhostButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color? = null,
) {
    val k = LocalKineticColors.current
    TextButton(onClick = onClick, modifier = modifier) {
        Text(
            text = text.uppercase(),
            style = KineticType.labelBold,
            color = color ?: k.mutedForeground,
        )
    }
}

/** Editorial input: transparent, bottom structural border, strong focus state. */
@Composable
fun KineticTextField(
    value: String,
    onValueChange: (String) -> Unit,
    hint: String,
    modifier: Modifier = Modifier,
    minLines: Int = 1,
    maxLines: Int = 1,
    isError: Boolean = false,
    errorMessage: String? = null,
    keyboardOptions: androidx.compose.foundation.text.KeyboardOptions =
        androidx.compose.foundation.text.KeyboardOptions.Default,
) {
    val k = LocalKineticColors.current
    var focused by remember { mutableStateOf(false) }
    val borderColor = when {
        isError -> k.statusError
        focused -> k.accent
        else -> k.border
    }
    Column(modifier) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = KineticType.body.copy(color = k.foreground),
            cursorBrush = SolidColor(k.accent),
            minLines = minLines,
            maxLines = maxLines,
            keyboardOptions = keyboardOptions,
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { focused = it.isFocused }
                .padding(vertical = KineticSpacing.md),
            decorationBox = { inner ->
                if (value.isEmpty()) {
                    Text(hint, style = KineticType.body, color = k.mutedForeground)
                }
                inner()
            },
        )
        Box(
            Modifier
                .fillMaxWidth()
                .height(if (focused || isError) KineticBorder.heavy else KineticBorder.hair)
                .background(borderColor)
        )
        if (isError && errorMessage != null) {
            Text(
                text = errorMessage,
                style = KineticType.label,
                color = k.statusError,
                modifier = Modifier.padding(top = KineticSpacing.xs),
            )
        }
    }
}

/** Structural divider. */
@Composable
fun KineticDivider(modifier: Modifier = Modifier, thickness: Int = 1) {
    Box(
        modifier
            .fillMaxWidth()
            .height(thickness.dp)
            .background(LocalKineticColors.current.border)
    )
}

/** Small uppercase status badge. */
@Composable
fun KineticBadge(
    text: String,
    modifier: Modifier = Modifier,
    filled: Boolean = false,
    color: Color? = null,
) {
    val k = LocalKineticColors.current
    val c = color ?: k.foreground
    Box(
        modifier = modifier
            .border(KineticBorder.hair, if (filled) c else k.border)
            .background(if (filled) c else Color.Transparent)
            .padding(horizontal = KineticSpacing.sm, vertical = 3.dp)
    ) {
        Text(
            text = text.uppercase(),
            style = KineticType.label.copy(fontSize = 10.sp),
            color = if (filled) k.accentForeground else k.mutedForeground,
        )
    }
}

/** Editorial list row with optional index number. */
@Composable
fun KineticListItem(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    index: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    val k = LocalKineticColors.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(vertical = KineticSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (index != null) {
            Text(
                text = index,
                style = KineticType.labelBold.copy(fontSize = 12.sp),
                color = k.mutedForeground,
                modifier = Modifier.width(32.dp),
            )
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = KineticType.bodyMedium,
                color = k.foreground,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = KineticType.label,
                    color = k.mutedForeground,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        trailing?.invoke()
    }
}

/** Empty state with editorial typography and optional action. */
@Composable
fun KineticEmptyState(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    actionText: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val k = LocalKineticColors.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .border(KineticBorder.hair, k.border)
            .padding(KineticSpacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = title.uppercase(),
            style = KineticType.heading,
            color = k.foreground,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(KineticSpacing.sm))
        Text(
            text = message.uppercase(),
            style = KineticType.label,
            color = k.mutedForeground,
            textAlign = TextAlign.Center,
        )
        if (actionText != null && onAction != null) {
            Spacer(Modifier.height(KineticSpacing.lg))
            KineticOutlinedButton(text = actionText, onClick = onAction)
        }
    }
}

/** Loading state: kinetic typography, no generic spinner. */
@Composable
fun KineticLoadingState(
    label: String = "LOADING",
    modifier: Modifier = Modifier,
) {
    val k = LocalKineticColors.current
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0.3f,
        animationSpec = tween(KineticMotion.SLOW_MS),
        label = "loadingPulse",
    )
    Row(
        modifier = modifier.fillMaxWidth().padding(KineticSpacing.xl),
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "$label ...",
            style = KineticType.heading,
            color = k.foreground.copy(alpha = alpha),
        )
    }
}

/** Error state with retry action. */
@Composable
fun KineticErrorState(
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
) {
    KineticEmptyState(
        title = "COULDN'T LOAD",
        message = "CHECK YOUR CONNECTION AND TRY AGAIN",
        modifier = modifier,
        actionText = if (onRetry != null) "TRY AGAIN" else null,
        onAction = onRetry,
    )
}
