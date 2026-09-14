package com.bits.facultyai.data.update

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * In-app update check against the project's GitHub Releases. The release
 * workflow publishes a signed APK per version tag, so "latest release" is the
 * canonical newest build. Comparison is numeric per segment so 1.10.0 beats
 * 1.9.0 (a plain string compare would not).
 */
object UpdateChecker {

    const val OWNER = "metheyelene"
    const val REPO = "faculty-ai"
    private const val RELEASES_API = "https://api.github.com/repos/$OWNER/$REPO/releases/latest"
    const val RELEASES_PAGE = "https://github.com/$OWNER/$REPO/releases"

    data class UpdateInfo(
        val latestVersion: String,
        val downloadUrl: String,
        val releaseNotes: String,
    )

    sealed interface Result {
        data class UpdateAvailable(val info: UpdateInfo) : Result
        data object UpToDate : Result
        data class Error(val message: String) : Result
    }

    suspend fun check(context: Context): Result = withContext(Dispatchers.IO) {
        val installed = installedVersion(context)
        val conn = try {
            (URL(RELEASES_API).openConnection() as HttpURLConnection).apply {
                connectTimeout = 8000
                readTimeout = 8000
                // GitHub's API rejects requests without a User-Agent.
                setRequestProperty("User-Agent", "Acadora-App")
                setRequestProperty("Accept", "application/vnd.github+json")
            }
        } catch (e: Exception) {
            return@withContext Result.Error("Network unavailable")
        }
        try {
            if (conn.responseCode != 200) {
                return@withContext Result.Error("GitHub error ${conn.responseCode}")
            }
            val json = JSONObject(conn.inputStream.bufferedReader().readText())
            val tag = json.optString("tag_name").removePrefix("v")
            if (tag.isEmpty()) return@withContext Result.Error("No published release yet")
            val apkUrl = json.optJSONArray("assets")?.let { assets ->
                (0 until assets.length())
                    .map { assets.getJSONObject(it) }
                    .firstOrNull { it.optString("name").endsWith(".apk") }
                    ?.optString("browser_download_url")
            }
            val info = UpdateInfo(
                latestVersion = tag,
                downloadUrl = apkUrl ?: RELEASES_PAGE,
                releaseNotes = json.optString("body"),
            )
            if (isNewer(tag, installed)) Result.UpdateAvailable(info) else Result.UpToDate
        } catch (e: Exception) {
            Result.Error(e.message ?: "Check failed")
        } finally {
            conn.disconnect()
        }
    }

    fun installedVersion(context: Context): String = try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "0"
    } catch (_: Exception) {
        "0"
    }

    /** Segment-wise numeric compare: "1.10.0" > "1.9.2". */
    fun isNewer(latest: String, installed: String): Boolean {
        val l = latest.split('.').map { Regex("\\d+").find(it)?.value?.toIntOrNull() ?: 0 }
        val i = installed.split('.').map { Regex("\\d+").find(it)?.value?.toIntOrNull() ?: 0 }
        for (k in 0 until maxOf(l.size, i.size)) {
            val lv = l.getOrElse(k) { 0 }
            val iv = i.getOrElse(k) { 0 }
            if (lv != iv) return lv > iv
        }
        return false
    }
}
