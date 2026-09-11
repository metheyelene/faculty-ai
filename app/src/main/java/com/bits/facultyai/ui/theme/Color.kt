package com.bits.facultyai.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

// =====================================================================
// ACADORA — Obsidian Purple design tokens (single source of truth).
//
// A deep, muted, premium purple identity: near-black violet backgrounds,
// layered translucent glass, and carefully controlled purple highlights.
// Every screen reads these tokens via LocalKineticColors — no hardcoded
// colors in feature code.
// =====================================================================

// ---- Core palette ----

/** Primary purple — interactive accent on dark surfaces. */
val PrimaryPurple = Color(0xFF7C5CFF)

/** Deep purple — accents on light surfaces, pressed/active fills. */
val DeepPurple = Color(0xFF5B3FC4)

/** Soft purple highlight — subtle strokes, glows, secondary accents. */
val SoftPurple = Color(0xFFA78BFA)

/** Obsidian dark background. */
val ObsidianBackground = Color(0xFF0B0A10)

/** Obsidian secondary background. */
val ObsidianSurface = Color(0xFF12101A)

/** Obsidian elevated surface (cards' quiet fill, muted surfaces). */
val ObsidianElevated = Color(0xFF181522)

/** Purple-tinted glass base: rgba(30, 24, 45, 0.55). */
val ObsidianGlass = Color(0xFF1E182D)

/** Primary text on dark. */
val ObsidianTextPrimary = Color(0xFFF5F3FA)

/** Secondary text on dark. */
val ObsidianTextSecondary = Color(0xFFAAA5B8)

/** Muted/tertiary text on dark. */
val ObsidianTextMuted = Color(0xFF777181)

/** Dark border: thin purple-neutral hairline. */
val ObsidianBorder = Color(0xFF302A3D)

// ---- Light-theme palette (lavender-neutral, NOT an inversion) ----

val LilacBackground = Color(0xFFFAF9FC)
val LilacSurface = Color(0xFFEFEDF6)
val LilacForeground = Color(0xFF17141F)
val LilacMutedFg = Color(0xFF5A5568)
val LilacBorder = Color(0xFFDDD8E8)
val LilacGlassBorder = Color(0xFFCFC8E2)

// Status colors: subordinate, used sparingly. Desaturated to sit calmly
// inside the purple identity.
val StatusSuccess = Color(0xFF3E9E6E)
val StatusWarning = Color(0xFFC08A2E)
val StatusError = Color(0xFFC75555)

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
    /** Softer variant of the accent for strokes and gentle highlights. */
    val accentSoft: Color = SoftPurple,
    /** Extremely subtle glow — transparency, never neon. */
    val glow: Color = accent.copy(alpha = 0.25f),
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
    background = LilacBackground,
    foreground = LilacForeground,
    // Light lavender quiet surface — bright and airy, never flat gray.
    muted = LilacSurface,
    mutedForeground = LilacMutedFg,
    // Deep purple carries the accent on light: readable as text, elegant
    // as fill. SoftPurple is reserved for gentle highlights.
    accent = DeepPurple,
    accentForeground = Color.White,
    border = LilacBorder,
    // Translucent lavender glass: borders + highlights carry the definition
    // so text contrast never depends on fill opacity.
    glassSurface = LilacBackground.copy(alpha = 0.72f),
    glassBorder = LilacGlassBorder.copy(alpha = 0.9f),
    statusSuccess = StatusSuccess,
    statusWarning = StatusWarning,
    statusError = StatusError,
    accentSoft = DeepPurple.copy(alpha = 0.55f),
    glow = DeepPurple.copy(alpha = 0.16f),
    glassUltraThin = LilacBackground.copy(alpha = 0.42f),
    glassThin = LilacBackground.copy(alpha = 0.56f),
    glassRegular = LilacBackground.copy(alpha = 0.68f),
    glassThick = LilacBackground.copy(alpha = 0.90f),
    glassHighlight = Color.White.copy(alpha = 0.85f),
    glassBlurTint = LilacBackground.copy(alpha = 0.50f),
)

fun darkKineticColors() = KineticColors(
    background = ObsidianBackground,
    foreground = ObsidianTextPrimary,
    // Elevated obsidian surface as the quiet fill.
    muted = ObsidianElevated,
    // Secondary text for readability; tertiary contexts use borders/muted.
    mutedForeground = ObsidianTextSecondary,
    accent = PrimaryPurple,
    accentForeground = Color.White,
    border = ObsidianBorder,
    // Purple-tinted glass ladder — layered obsidian depth.
    glassSurface = ObsidianGlass.copy(alpha = 0.55f),
    glassBorder = Color(0xFF372F49).copy(alpha = 0.9f),
    statusSuccess = StatusSuccess,
    statusWarning = StatusWarning,
    statusError = StatusError,
    accentSoft = SoftPurple,
    glow = PrimaryPurple.copy(alpha = 0.25f),
    glassUltraThin = ObsidianGlass.copy(alpha = 0.34f),
    glassThin = Color(0xFF1A1526).copy(alpha = 0.52f),
    glassRegular = Color(0xFF191427).copy(alpha = 0.68f),
    glassThick = Color(0xFF171221).copy(alpha = 0.90f),
    // Soft purple-tinted inner highlight: the glass reads as lit from above
    // with a violet cast, not white.
    glassHighlight = SoftPurple.copy(alpha = 0.14f),
    glassBlurTint = ObsidianGlass.copy(alpha = 0.55f),
)

fun kineticMaterialScheme(c: KineticColors): ColorScheme {
    // Theme flavor is derived from background luminance, not identity
    // comparison — the palette may change, this must not.
    return if (c.background.luminance() > 0.5f) lightColorScheme(
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
}
