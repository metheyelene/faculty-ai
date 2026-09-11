package com.bits.facultyai.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

// ---- Core palette (single source of truth) ----
val AcidYellow = Color(0xFFDFE104)
val InkBlack = Color(0xFF09090B)
val PaperWhite = Color(0xFFFAFAFA)

val LightBorder = Color(0xFFD4D4D8)
val DarkBorder = Color(0xFF3F3F46)
val LightMutedSurface = Color(0xFFE4E4E7)
val DarkMutedSurface = Color(0xFF27272A)
val LightMutedFg = Color(0xFF52525B)
val DarkMutedFg = Color(0xFFA1A1AA)

// Status colors: subordinate, used sparingly.
val StatusSuccess = Color(0xFF1A9E4B)
val StatusWarning = Color(0xFFC97A0E)
val StatusError = Color(0xFFC4372D)

@Immutable
data class KineticColors(
    val background: Color,
    val foreground: Color,
    val muted: Color,
    val mutedForeground: Color,
    val accent: Color,
    val accentForeground: Color,
    val border: Color,
    val glassSurface: Color,
    val glassBorder: Color,
    val statusSuccess: Color,
    val statusWarning: Color,
    val statusError: Color,
    // ---- Liquid Glass surface ladder (thin -> thick) ----
    val glassUltraThin: Color = glassSurface.copy(alpha = 0.55f),
    val glassThin: Color = glassSurface.copy(alpha = 0.74f),
    val glassRegular: Color = glassSurface.copy(alpha = 0.87f),
    val glassThick: Color = glassSurface.copy(alpha = 0.96f),
    /** Soft top-edge highlight that gives glass its lit edge. */
    val glassHighlight: Color = Color.White.copy(alpha = 0.16f),
    /**
     * Tint applied over real backdrop blur (RenderEffect via Haze) on floating
     * surfaces. Translucent enough to let the blurred content read through —
     * only used where hardware blur is actually active.
     */
    val glassBlurTint: Color = glassSurface.copy(alpha = 0.62f),
)

fun lightKineticColors() = KineticColors(
    background = PaperWhite,
    foreground = InkBlack,
    muted = LightMutedSurface,
    mutedForeground = LightMutedFg,
    accent = AcidYellow,
    accentForeground = InkBlack,
    border = LightBorder,
    glassSurface = PaperWhite.copy(alpha = 0.85f),
    glassBorder = LightBorder.copy(alpha = 0.9f),
    statusSuccess = StatusSuccess,
    statusWarning = StatusWarning,
    statusError = StatusError,
    glassUltraThin = PaperWhite.copy(alpha = 0.60f),
    glassThin = PaperWhite.copy(alpha = 0.76f),
    glassRegular = PaperWhite.copy(alpha = 0.88f),
    glassThick = PaperWhite.copy(alpha = 0.965f),
    glassHighlight = Color.White.copy(alpha = 0.75f),
    glassBlurTint = PaperWhite.copy(alpha = 0.62f),
)

fun darkKineticColors() = KineticColors(
    background = InkBlack,
    foreground = PaperWhite,
    muted = DarkMutedSurface,
    mutedForeground = DarkMutedFg,
    accent = AcidYellow,
    accentForeground = InkBlack,
    border = DarkBorder,
    glassSurface = Color(0xFF18181B).copy(alpha = 0.88f),
    glassBorder = DarkBorder.copy(alpha = 0.9f),
    statusSuccess = StatusSuccess,
    statusWarning = StatusWarning,
    statusError = StatusError,
    glassUltraThin = Color(0xFF18181B).copy(alpha = 0.55f),
    glassThin = Color(0xFF1C1C20).copy(alpha = 0.74f),
    glassRegular = Color(0xFF1B1B1F).copy(alpha = 0.87f),
    glassThick = Color(0xFF1A1A1E).copy(alpha = 0.96f),
    glassHighlight = Color.White.copy(alpha = 0.10f),
    glassBlurTint = Color(0xFF18181B).copy(alpha = 0.62f),
)

fun kineticMaterialScheme(c: KineticColors): ColorScheme =
    if (c.background == PaperWhite) lightColorScheme(
        primary = c.accent,
        onPrimary = c.accentForeground,
        background = c.background,
        onBackground = c.foreground,
        surface = c.background,
        onSurface = c.foreground,
        surfaceVariant = c.muted,
        onSurfaceVariant = c.mutedForeground,
        outline = c.border,
        error = c.statusError,
    ) else darkColorScheme(
        primary = c.accent,
        onPrimary = c.accentForeground,
        background = c.background,
        onBackground = c.foreground,
        surface = c.background,
        onSurface = c.foreground,
        surfaceVariant = c.muted,
        onSurfaceVariant = c.mutedForeground,
        outline = c.border,
        error = c.statusError,
    )
