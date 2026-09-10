package com.bits.facultyai.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.bits.facultyai.R

val SpaceGrotesk = FontFamily(
    Font(R.font.space_grotesk_regular, FontWeight.Normal),
    Font(R.font.space_grotesk_medium, FontWeight.Medium),
    Font(R.font.space_grotesk_bold, FontWeight.Bold),
)

object KineticType {
    // Display: extremely large, uppercase, tight tracking. Used sparingly.
    val display = TextStyle(
        fontFamily = SpaceGrotesk,
        fontWeight = FontWeight.Bold,
        fontSize = 56.sp,
        lineHeight = 56.sp,
        letterSpacing = (-1.5).sp,
    )

    val displayLg = display.copy(fontSize = 76.sp, lineHeight = 72.sp)

    val heading = TextStyle(
        fontFamily = SpaceGrotesk,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 24.sp,
        letterSpacing = (-0.4).sp,
    )

    val headingSm = TextStyle(
        fontFamily = SpaceGrotesk,
        fontWeight = FontWeight.Bold,
        fontSize = 16.sp,
        lineHeight = 19.sp,
        letterSpacing = 0.sp,
    )

    val label = TextStyle(
        fontFamily = SpaceGrotesk,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 13.sp,
        letterSpacing = 1.2.sp,
    )

    val labelBold = label.copy(fontWeight = FontWeight.Bold)

    val body = TextStyle(
        fontFamily = SpaceGrotesk,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 21.sp,
        letterSpacing = 0.sp,
    )

    val bodyMedium = body.copy(fontWeight = FontWeight.Medium)

    val statNumber = TextStyle(
        fontFamily = SpaceGrotesk,
        fontWeight = FontWeight.Bold,
        fontSize = 48.sp,
        lineHeight = 44.sp,
        letterSpacing = (-1).sp,
    )

    val code = TextStyle(
        fontFamily = SpaceGrotesk,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.sp,
    )
}

fun kineticTypography() = Typography(
    displayLarge = KineticType.displayLg,
    displayMedium = KineticType.display,
    displaySmall = KineticType.display.copy(fontSize = 44.sp, lineHeight = 44.sp),
    headlineMedium = KineticType.heading,
    headlineSmall = KineticType.headingSm,
    titleLarge = KineticType.headingSm,
    titleMedium = KineticType.bodyMedium,
    titleSmall = KineticType.body,
    bodyLarge = KineticType.body,
    bodyMedium = KineticType.body.copy(fontSize = 14.sp, lineHeight = 19.sp),
    bodySmall = KineticType.body.copy(fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = KineticType.labelBold.copy(fontSize = 13.sp, lineHeight = 15.sp),
    labelMedium = KineticType.label,
    labelSmall = KineticType.label.copy(fontSize = 10.sp, letterSpacing = 1.sp),
)
