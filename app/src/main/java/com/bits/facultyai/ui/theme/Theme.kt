package com.bits.facultyai.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext

enum class ThemeMode { SYSTEM, LIGHT, DARK }

val LocalKineticColors = staticCompositionLocalOf { lightKineticColors() }
val LocalIsDark = staticCompositionLocalOf { false }

@Composable
fun FacultyAITheme(
    mode: ThemeMode,
    content: @Composable () -> Unit,
) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val colors = if (dark) darkKineticColors() else lightKineticColors()
    CompositionLocalProvider(
        LocalKineticColors provides colors,
        LocalIsDark provides dark,
    ) {
        MaterialTheme(
            colorScheme = kineticMaterialScheme(colors),
            typography = kineticTypography(),
            shapes = MaterialShapes.allSharp(),
            content = content,
        )
    }
}

private object MaterialShapes {
    fun allSharp() = androidx.compose.material3.Shapes(
        extraSmall = KineticShape.slight,
        small = KineticShape.slight,
        medium = KineticShape.slight,
        large = KineticShape.slight,
        extraLarge = KineticShape.slight,
    )
}
