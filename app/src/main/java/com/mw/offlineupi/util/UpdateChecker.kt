package com.mw.offlineupi.util

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class AppUpdate(
    val versionName: String,
    val downloadUrl: String,
    val releaseNotes: String,
    val htmlUrl: String
)

object UpdateChecker {
    private const val TAG = "UpdateChecker"
    private const val GITHUB_API_URL =
        "https://api.github.com/repos/maroufwani/OfflinePay/releases/latest"

    suspend fun checkForUpdate(currentVersion: String): AppUpdate? = withContext(Dispatchers.IO) {
        try {
            val url = URL(GITHUB_API_URL)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("Accept", "application/vnd.github.v3+json")
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000

            if (connection.responseCode != 200) {
                Log.w(TAG, "GitHub API returned ${connection.responseCode}")
                connection.disconnect()
                return@withContext null
            }

            val response = connection.inputStream.bufferedReader().use { it.readText() }
            connection.disconnect()

            val json = JSONObject(response)
            val tagName = json.optString("tag_name", "").removePrefix("v")
            val body = json.optString("body", "")
            val htmlUrl = json.optString("html_url", "")

            // Find APK asset download URL
            var apkUrl = ""
            val assets = json.optJSONArray("assets")
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    val name = asset.optString("name", "")
                    if (name.endsWith(".apk")) {
                        apkUrl = asset.optString("browser_download_url", "")
                        break
                    }
                }
            }

            if (tagName.isEmpty()) {
                Log.w(TAG, "No tag_name in release response")
                return@withContext null
            }

            if (isNewerVersion(tagName, currentVersion)) {
                Log.d(TAG, "Update available: $currentVersion -> $tagName")
                AppUpdate(
                    versionName = tagName,
                    downloadUrl = apkUrl.ifEmpty { htmlUrl },
                    releaseNotes = body,
                    htmlUrl = htmlUrl
                )
            } else {
                Log.d(TAG, "Already on latest version ($currentVersion >= $tagName)")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to check for updates", e)
            null
        }
    }

    /**
     * Compare semantic versions. Handles formats like "0.1.0-beta", "1.2.3", "0.2.0".
     * Returns true if [remote] is newer than [current].
     */
    fun isNewerVersion(remote: String, current: String): Boolean {
        val remoteParts = parseVersion(remote)
        val currentParts = parseVersion(current)

        for (i in 0 until maxOf(remoteParts.size, currentParts.size)) {
            val r = remoteParts.getOrElse(i) { 0 }
            val c = currentParts.getOrElse(i) { 0 }
            if (r > c) return true
            if (r < c) return false
        }

        // Same numeric version — check pre-release suffix
        // "0.1.0" > "0.1.0-beta", no suffix > suffix
        val remoteSuffix = extractSuffix(remote)
        val currentSuffix = extractSuffix(current)
        if (remoteSuffix.isEmpty() && currentSuffix.isNotEmpty()) return true
        if (remoteSuffix.isNotEmpty() && currentSuffix.isEmpty()) return false

        return false
    }

    private fun parseVersion(version: String): List<Int> {
        return version.split("-")[0]
            .split(".")
            .mapNotNull { it.toIntOrNull() }
    }

    private fun extractSuffix(version: String): String {
        val idx = version.indexOf('-')
        return if (idx >= 0) version.substring(idx + 1) else ""
    }
}
