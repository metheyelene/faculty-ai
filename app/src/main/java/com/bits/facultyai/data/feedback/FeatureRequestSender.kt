package com.bits.facultyai.data.feedback

import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Sends feature requests straight to the founders' inbox via the device's
 * mail app (Gmail, Outlook, any handler). No server, no API key — the draft
 * is composed in front of the user, never sent silently.
 */
object FeatureRequestSender {

    const val RECIPIENT = "mithilviswaskasi@gmail.com"

    fun intent(context: Context, userMessage: String): Intent {
        val appVersion = try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "?"
        } catch (_: Exception) {
            "?"
        }
        val subject = "Acadora feature request (v$appVersion)"
        val body = buildString {
            append(userMessage.trim())
            append("\n\n----\nSent from Acadora v")
            append(appVersion)
            append(" · Android ")
            append(android.os.Build.VERSION.RELEASE)
        }
        return Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:")
            putExtra(Intent.EXTRA_EMAIL, arrayOf(RECIPIENT))
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, body)
        }
    }
}
