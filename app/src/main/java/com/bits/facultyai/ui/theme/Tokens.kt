package com.bits.facultyai.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

object KineticSpacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
    val hero = 48.dp
}

object KineticShape {
    val sharp = RoundedCornerShape(0.dp)
    val slight = RoundedCornerShape(2.dp)
    val native = RoundedCornerShape(6.dp)
}

object KineticBorder {
    val hair = 1.dp
    val standard = 1.5.dp
    val heavy = 2.dp
}

/**
 * Centralized motion system — Apple-inspired fluidity on a native Compose base.
 *
 * LEVEL 1 — FAST_MS: presses, toggles, icon flips (~180ms)
 * LEVEL 2 — MEDIUM_MS: cards, dialogs, expanding sections (~300ms spring)
 * LEVEL 3 — SLOW_MS: screen transitions, large transformations (~480ms)
 * LEVEL 4 — AMBIENT: orb breathing; extremely subtle, reduced-motion aware.
 */
object KineticMotion {
    // Durations (used where a tween is clearer than a spring).
    const val FAST_MS = 180
    const val MEDIUM_MS = 300
    const val SLOW_MS = 480

    /** Apple-like ease-out curve: fast start, gentle settle. */
    val easeOut: Easing = CubicBezierEasing(0.2f, 0.8f, 0.2f, 1f)

    /** Gentle standard ease for fades. */
    val standard: Easing = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)

    // ---- Spring presets (controlled damping: settle quickly, no bounce) ----

    /** Immediate interactive response. */
    fun <T> springFast(): SpringSpec<T> = spring(
        dampingRatio = 0.9f,
        stiffness = Spring.StiffnessHigh,
    )

    /** Cards, toggles, expanding content. */
    fun <T> springStandard(): SpringSpec<T> = spring(
        dampingRatio = 0.88f,
        stiffness = Spring.StiffnessMediumLow,
    )

    /** Larger spatial movement — screen-level. */
    fun <T> springSpatial(): SpringSpec<T> = spring(
        dampingRatio = 0.92f,
        stiffness = 320f,
    )
}
