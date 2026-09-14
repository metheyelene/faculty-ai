package com.bits.facultyai.data.update

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Downloads a release APK into app-private storage and hands back an
 * install intent for it. Progress is reported as a 0f..1f fraction for the
 * Settings progress bar. The file lives under cacheDir/updates and is
 * shared with the system installer via FileProvider — no storage permission
 * and nothing leaves the app sandbox.
 */
object UpdateDownloader {

    private fun updatesDir(context: Context): File =
        File(context.cacheDir, "updates").apply { mkdirs() }

    /** Known APK for a version, if a previous download completed. */
    fun downloadedApk(context: Context, version: String): File? {
        val f = File(updatesDir(context), "Acadora-v$version.apk")
        return if (f.exists() && f.length() > 0) f else null
    }

    suspend fun download(
        context: Context,
        url: String,
        version: String,
        onProgress: (Float) -> Unit,
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 20_000
                setRequestProperty("User-Agent", "Acadora-App")
            }
            try {
                if (conn.responseCode != 200) error("Download failed: HTTP ${conn.responseCode}")
                val total = conn.contentLengthLong.takeIf { it > 0 } ?: -1L
                val out = File(updatesDir(context), "Acadora-v$version.apk")
                conn.inputStream.use { input ->
                    out.outputStream().use { output ->
                        val buf = ByteArray(64 * 1024)
                        var read = 0L
                        var lastPct = -1
                        while (true) {
                            val n = input.read(buf)
                            if (n == -1) break
                            output.write(buf, 0, n)
                            read += n
                            if (total > 0) {
                                val pct = ((read * 100) / total).toInt()
                                if (pct != lastPct) {
                                    lastPct = pct
                                    onProgress(pct / 100f)
                                }
                            }
                        }
                    }
                }
                if (total > 0 && out.length() != total) error("Download incomplete: ${out.length()}/$total bytes")
                out
            } finally {
                conn.disconnect()
            }
        }.onFailure {
            // Never leave a truncated APK around for the installer to choke on.
            File(updatesDir(context), "Acadora-v$version.apk").delete()
        }
    }

    /**
     * Returns an intent that opens the system package installer for the
     * downloaded APK. Requires REQUEST_INSTALL_PACKAGES; the user sees the
     * standard "allow installs from this app" flow once, then a straight
     * install prompt.
     */
    fun installIntent(context: Context, apk: File): Intent {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apk,
        )
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    /** Best-effort cleanup of old downloads (called before a new one starts). */
    fun clearDownloads(context: Context) {
        updatesDir(context).listFiles()?.forEach { it.delete() }
    }
}
