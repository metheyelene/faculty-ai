package com.bits.facultyai.ui.splash

import androidx.compose.animation.Animatable
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import com.bits.facultyai.ui.theme.KineticMotion
import com.bits.facultyai.ui.theme.KineticType
import com.bits.facultyai.ui.theme.LocalKineticColors
import kotlinx.coroutines.launch
import kotlin.math.min
import kotlin.math.sin

/** Easing that mimics a soft spring settle (slight overshoot, no bounce). */
private val GlassSettle = CubicBezierEasing(0.22f, 1.14f, 0.32f, 1f)
private val EaseOut = CubicBezierEasing(0.16f, 0.84f, 0.32f, 1f)

/** Maps [p] from the [start,end] window into 0..1. */
private fun phase(p: Float, start: Float, end: Float): Float =
    ((p - start) / (end - start)).coerceIn(0f, 1f)

/**
 * The signature opening: a translucent liquid-glass form settles behind the
 * Acadora wordmark, which reveals itself kinetically (clip + rise), closed by
 * a restrained accent underline. One timeline (~1.15s), GPU-cheap: canvas
 * shapes and graphicsLayer transforms only — no RenderEffect blur.
 *
 * The animatables are hoisted by the caller so the intro can continue
 * seamlessly across the settings-load boundary (no replay, no freeze).
 *
 * While [settingsReady] is false the composition holds a barely-breathing
 * final state (never frozen, never blocking longer than data needs). When the
 * app is ready the whole overlay expands+fades while the real content fades
 * in underneath — the first screen emerges from the splash.
 *
 * Reduced motion: no morph, no glint; a short opacity/scale settle.
 */
@Composable
fun KineticSplashOverlay(
    intro: Animatable<Float, AnimationVector1D>,
    exit: Animatable<Float, AnimationVector1D>,
    contentIn: Animatable<Float, AnimationVector1D>,
    settingsReady: Boolean,
    reducedMotion: Boolean,
    onExitComplete: () -> Unit,
) {
    val k = LocalKineticColors.current

    // One orchestrating coroutine: intro runs once; when settings are ready
    // (whenever that happens relative to the intro) the overlay dissolves
    // while the real content rises in. Relaunching on settingsReady is safe:
    // the intro step is skipped once it has finished, so nothing replays.
    LaunchedEffect(settingsReady, reducedMotion) {
        if (intro.value < 1f) {
            if (reducedMotion) intro.snapTo(1f) else intro.animateTo(1f, tween(1150, easing = LinearEasing))
        }
        if (settingsReady) {
            if (reducedMotion) {
                contentIn.snapTo(1f)
                exit.snapTo(1f)
            } else {
                // Content rises concurrently with the splash dissolving.
                launch { contentIn.animateTo(1f, tween(420, easing = EaseOut)) }
                exit.animateTo(1f, tween(420, easing = EaseOut))
            }
            onExitComplete()
        }
    }

    // Barely-perceptible breathing while waiting for initialization.
    val breathe = if (settingsReady || reducedMotion) 1f else {
        val t = rememberInfiniteTransition(label = "splashBreathe")
        val v by t.animateFloat(
            initialValue = 0.965f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(1600, easing = KineticMotion.easeOut), RepeatMode.Reverse),
            label = "breathe",
        )
        v
    }

    val wordReveal1 = EaseOut.transform(phase(intro.value, 0.30f, 0.62f))
    val wordReveal2 = EaseOut.transform(phase(intro.value, 0.44f, 0.82f))
    val underline = EaseOut.transform(phase(intro.value, 0.82f, 1f))
    val labelAlpha = EaseOut.transform(phase(intro.value, 0.88f, 1f))
    val wholeAlpha = 1f - exit.value
    val wholeScale = 1f + 0.035f * exit.value

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(k.background)
            .graphicsLayer {
                alpha = wholeAlpha
                scaleX = wholeScale
                scaleY = wholeScale
            },
        contentAlignment = Alignment.Center,
    ) {
        // ---- The liquid glass form ----
        val formP = GlassSettle.transform(phase(intro.value, 0f, 0.55f))
        val density = LocalDensity.current
        val shiftPx = with(density) { 10.dp.toPx() }
        Canvas(
            modifier = Modifier
                .size(280.dp)
                .graphicsLayer {
                    scaleX = formP * breathe
                    scaleY = formP * breathe
                },
        ) {
            val r = min(size.width, size.height) / 2f
            val c = Offset(size.width / 2f, size.height / 2f)
            // Layer 1: broad translucent body — the "liquid" mass.
            drawCircle(
                brush = androidx.compose.ui.graphics.Brush.radialGradient(
                    colors = listOf(
                        k.glassHighlight.copy(alpha = 0.10f),
                        Color.Transparent,
                    ),
                    center = c,
                    radius = r,
                ),
                radius = r,
                center = c,
            )
            // Layer 2: the glass shell — thin border, offset by the morph so
            // the surface visibly shifts against the mass beneath it.
            val shellCenter = Offset(c.x + sin(intro.value * Math.PI).toFloat() * shiftPx, c.y)
            drawCircle(
                color = k.glassBorder.copy(alpha = 0.55f),
                radius = r * 0.82f,
                center = shellCenter,
                style = Stroke(width = 1.2.dp.toPx()),
            )
            // Layer 3: inner highlight pool — depth between the layers.
            drawCircle(
                brush = androidx.compose.ui.graphics.Brush.radialGradient(
                    colors = listOf(
                        k.glassHighlight.copy(alpha = 0.16f),
                        Color.Transparent,
                    ),
                    center = Offset(c.x, c.y - r * 0.35f),
                    radius = r * 0.5f,
                ),
                radius = r * 0.5f,
                center = Offset(c.x, c.y - r * 0.35f),
            )
            // Layer 4: a single restrained accent arc riding the shell.
            if (formP > 0.4f) {
                val sweep = 52f * phase(intro.value, 0.4f, 1f)
                drawArc(
                    color = k.accent.copy(alpha = 0.85f),
                    startAngle = -118f,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = Offset(shellCenter.x - r * 0.82f, shellCenter.y - r * 0.82f),
                    size = androidx.compose.ui.geometry.Size(r * 1.64f, r * 1.64f),
                    style = Stroke(width = 2.2.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round),
                )
            }
        }

        // ---- Kinetic wordmark: two letter groups, clipped + rising ----
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            androidx.compose.foundation.layout.Row {
                SplashWordGroup(text = "ACA", reveal = wordReveal1)
                SplashWordGroup(text = "DORA", reveal = wordReveal2)
            }
            Spacer(Modifier.height(10.dp))
            // Accent underline — draws outward from center, restrained.
            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .width(96.dp * underline)
                    .height(2.dp)
                    .background(k.accent.copy(alpha = 0.9f)),
            )
            Spacer(Modifier.height(14.dp))
            Text(
                text = "FACULTY ASSISTANT",
                style = KineticType.label.copy(letterSpacing = 3.sp),
                color = k.mutedForeground,
                textAlign = TextAlign.Center,
                modifier = Modifier.alpha(labelAlpha * wholeAlpha),
            )
        }
    }
}

/**
 * Minimal themed hold for states where the overlay orchestration is not
 * running (defensive fallback; the normal flow uses the full overlay).
 */
@Composable
fun KineticSplashLoading() {
    val k = LocalKineticColors.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(k.background),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "ACADORA",
            style = KineticType.display.copy(fontSize = 40.sp),
            color = k.foreground,
        )
    }
}

/** One letter group: clip-revealed, rising 18dp, fading in. */
@Composable
private fun SplashWordGroup(text: String, reveal: Float) {
    val k = LocalKineticColors.current
    val density = LocalDensity.current
    val rise = with(density) { 18.dp.toPx() }
    Box(
        modifier = Modifier
            .clipToBounds()
            .graphicsLayer {
                translationY = (1f - reveal) * rise
                alpha = reveal
            },
    ) {
        Text(
            text = text,
            style = KineticType.display.copy(fontSize = 40.sp),
            color = k.foreground,
        )
    }
}


