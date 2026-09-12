package com.bits.facultyai.ui.components

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * Subtle haptic feedback — used sparingly for toggles, confirms and completions.
 * Honors the view's haptic-feedback setting; no continuous vibration.
 *
 * Requires the normal-level VIBRATE permission (declared in the manifest).
 * API 26–28 devices get a short one-shot tick instead of the predefined
 * CONFIRM effect (createPredefined needs API 29).
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
        runCatching {
            if (Build.VERSION.SDK_INT >= 29) {
                vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
            } else {
                vibrator.vibrate(VibrationEffect.createOneShot(20L, VibrationEffect.DEFAULT_AMPLITUDE))
            }
        }
    }

    fun success(context: Context) = light(context)
}
