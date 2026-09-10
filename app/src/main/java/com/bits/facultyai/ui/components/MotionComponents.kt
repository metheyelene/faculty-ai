package com.bits.facultyai.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.bits.facultyai.ui.theme.KineticMotion
import com.bits.facultyai.ui.theme.rememberReducedMotion

/**
 * Press-scale micro-interaction. Applies a tiny scale-down while pressed with
 * a fast spring release. GPU-friendly (graphicsLayer), 60fps safe.
 */
fun Modifier.kineticPressable(
    interactionSource: MutableInteractionSource,
    pressedScale: Float = 0.97f,
): Modifier = composed {
    val reduced = rememberReducedMotion()
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && !reduced) pressedScale else 1f,
        animationSpec = KineticMotion.springFast(),
        label = "pressScale",
    )
    this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/**
 * Convenience overload creating its own interaction source and wiring the
 * pressable scale into a click handler.
 */
@Composable
fun Modifier.kineticClickable(
    enabled: Boolean = true,
    pressedScale: Float = 0.97f,
    onClick: () -> Unit,
): Modifier {
    val interaction = remember { MutableInteractionSource() }
    return this
        .kineticPressable(interaction, pressedScale)
        .clickable(interactionSource = interaction, indication = null, enabled = enabled, onClick = onClick)
}

/** Entrance animation for one dashboard section: rise + fade, spring settle. */
@Composable
fun KineticEntrance(
    index: Int,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val reduced = rememberReducedMotion()
    var visible by remember { mutableStateOf(reduced) }
    LaunchedEffect(Unit) {
        if (!reduced) visible = true
    }
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(KineticMotion.MEDIUM_MS, delayMillis = if (reduced) 0 else index * 45),
        label = "entranceAlpha",
    )
    val translationY by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (visible) 0f else 28f,
        animationSpec = KineticMotion.springStandard(),
        label = "entranceY",
    )
    Box(
        modifier = modifier.graphicsLayer {
            this.alpha = alpha
            this.translationY = translationY
        },
    ) {
        content()
    }
}

/** Animated presence helper matching the motion language (fade + tiny rise). */
@Composable
fun KineticAnimatedVisibility(
    visible: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val reduced = rememberReducedMotion()
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = if (reduced) fadeIn(tween(80)) else
            fadeIn(tween(KineticMotion.MEDIUM_MS)) +
                slideInVertically(KineticMotion.springStandard()) { it / 12 },
        exit = fadeOut(tween(KineticMotion.FAST_MS)),
    ) {
        content()
    }
}
