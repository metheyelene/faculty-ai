package com.bits.facultyai.ui.theme

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

object KineticMotion {
    const val FAST_MS = 160
    const val MEDIUM_MS = 280
    const val SLOW_MS = 520
}
