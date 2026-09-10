package com.bits.facultyai.ui.components

import android.content.Context
import android.os.Build
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants

/**
 * Subtle haptic feedback — used sparingly for toggles, confirms and completions.
 * Honors the view's haptic-feedback setting; no continuous vibration.
 */
object KineticHaptics {

    fun toggle(context: Context) = light(context)

    fun light(context: Context) {
        val vibrator = if (Build.VERSION.SDK_INT >= 31) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        } ?: return
        if (!vibrator.hasVibrator()) return
        val effect = if (Build.VERSION.SDK_INT >= 29) {
            HapticFeedbackConstants.CONFIRM
        } else {
            HapticFeedbackConstants.VIRTUAL_KEY
        }
        runCatching { vibrator.vibrate(android.os.VibrationEffect.createPredefined(effect)) }
    }

    fun success(context: Context) = light(context)
}
