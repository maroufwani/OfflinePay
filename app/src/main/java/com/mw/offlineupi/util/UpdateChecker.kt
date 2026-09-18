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
    val htmlUrl: String,
    /** First token of the SHA-256 checksum file published alongside the APK release asset. */
    val apkSha256: String? = null
)

object UpdateChecker {
    private const val TAG = "UpdateChecker"
    private const val GITHUB_API_URL =
        "https://api.github.com/repos/maroufwani/OfflinePay/releases/latest"

    /** Refuses to read an unbounded response body into memory. Releases are a few KB of JSON. */
    private const val MAX_RESPONSE_BYTES = 512 * 1024

    suspend fun checkForUpdate(currentVersion: String): AppUpdate? = withContext(Dispatchers.IO) {
        val response = httpGet(GITHUB_API_URL) ?: return@withContext null
        try {
            val json = JSONObject(response)
            val tagName = json.optString("tag_name", "").removePrefix("v")
            val body = json.optString("body", "")
            val htmlUrl = json.optString("html_url", "")

            if (tagName.isEmpty()) {
                Log.w(TAG, "No tag_name in release response")
                return@withContext null
            }

            // Nothing newer — bail out before spending a second request on the checksum file.
            if (!isNewerVersion(tagName, currentVersion)) {
                Log.d(TAG, "Already on latest version ($currentVersion >= $tagName)")
                return@withContext null
            }

            // Find APK asset and its matching SHA-256 checksum asset
            var apkUrl = ""
            var sha256Url = ""
            val assets = json.optJSONArray("assets")
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val asset = assets.optJSONObject(i) ?: continue
                    val name = asset.optString("name", "")
                    when {
                        name.endsWith(".apk", ignoreCase = true) ->
                            apkUrl = asset.optString("browser_download_url", "")
                        name.endsWith(".sha256", ignoreCase = true) ->
                            sha256Url = asset.optString("browser_download_url", "")
                    }
                }
            }

            // Fetch checksum content (e.g. "abc123...  OfflinePay.apk" — take the first token)
            val apkSha256 = if (sha256Url.isNotEmpty()) fetchFirstToken(sha256Url) else null

            Log.d(TAG, "Update available: $currentVersion -> $tagName")
            AppUpdate(
                versionName = tagName,
                downloadUrl = apkUrl.ifEmpty { htmlUrl },
                releaseNotes = body,
                htmlUrl = htmlUrl,
                apkSha256 = apkSha256
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse release response", e)
            null
        }
    }

    /**
     * GETs [url] and returns the body, or `null` on any failure.
     *
     * `disconnect()` lives in a `finally`: every previous call site only disconnected on the happy
     * path and on an explicit non-200, so a `SocketTimeoutException` mid-read leaked the connection
     * and its socket back to the keep-alive pool in an unusable state. Repeated failed update
     * checks then piled up file descriptors for the life of the process.
     */
    private fun httpGet(url: String): String? {
        var connection: HttpURLConnection? = null
        return try {
            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Accept", "application/vnd.github.v3+json")
                connectTimeout = 10_000
                readTimeout = 10_000
                instanceFollowRedirects = true
            }
            if (connection.responseCode != 200) {
                Log.w(TAG, "GET $url returned ${connection.responseCode}")
                return null
            }
            connection.inputStream.bufferedReader().use { reader ->
                val buf = CharArray(8 * 1024)
                val sb = StringBuilder()
                while (true) {
                    val n = reader.read(buf)
                    if (n < 0) break
                    sb.append(buf, 0, n)
                    if (sb.length > MAX_RESPONSE_BYTES) {
                        Log.w(TAG, "Response from $url exceeded $MAX_RESPONSE_BYTES bytes")
                        return null
                    }
                }
                sb.toString()
            }
        } catch (e: Exception) {
            Log.w(TAG, "GET $url failed", e)
            null
        } finally {
            try {
                connection?.disconnect()
            } catch (_: Exception) {
            }
        }
    }

    /**
     * Whether [remote] is a newer version than [current].
     *
     * Numeric components are compared first, then the pre-release suffix. Suffix handling follows
     * semver's spirit: a release outranks any pre-release of the same version, and two pre-releases
     * of the same version are compared by their own numeric tail so `0.1.1-beta2` is newer than
     * `0.1.1-beta1`. The previous version returned `false` for every same-version pair, which meant
     * no beta could ever offer an update to a later beta — the exact upgrade path this app ships on.
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

        // Same numeric version — compare pre-release suffixes.
        val remoteSuffix = extractSuffix(remote)
        val currentSuffix = extractSuffix(current)
        // A release (no suffix) is newer than any pre-release of the same version.
        if (remoteSuffix.isEmpty()) return currentSuffix.isNotEmpty()
        if (currentSuffix.isEmpty()) return false
        return compareSuffix(remoteSuffix, currentSuffix) > 0
    }

    /**
     * Orders two pre-release suffixes.
     *
     * Split into alphabetic and numeric runs so `beta10` sorts after `beta2` rather than before it
     * (a plain string comparison gets that backwards). Alphabetic runs compare case-insensitively,
     * which puts `alpha` < `beta` < `rc` as intended.
     */
    private fun compareSuffix(a: String, b: String): Int {
        val ta = suffixTokens(a)
        val tb = suffixTokens(b)
        for (i in 0 until maxOf(ta.size, tb.size)) {
            val x = ta.getOrNull(i)
            val y = tb.getOrNull(i)
            // A shorter suffix is the earlier release: "beta" < "beta2".
            if (x == null) return -1
            if (y == null) return 1
            val xn = x.toIntOrNull()
            val yn = y.toIntOrNull()
            val cmp = when {
                xn != null && yn != null -> xn.compareTo(yn)
                // Numeric identifiers sort before alphanumeric ones, per semver.
                xn != null -> -1
                yn != null -> 1
                else -> x.compareTo(y, ignoreCase = true)
            }
            if (cmp != 0) return cmp
        }
        return 0
    }

    /** Splits "beta10" into ["beta", "10"], and "rc.2" into ["rc", "2"]. */
    private fun suffixTokens(suffix: String): List<String> =
        Regex("[0-9]+|[^0-9.]+").findAll(suffix).map { it.value }.toList()

    /**
     * Fetches [url] and returns the first whitespace-delimited token of the response body.
     * SHA-256 checksum files are typically formatted as "<hex>  <filename>" (sha256sum output)
     * or just the bare hex string.
     */
    private fun fetchFirstToken(url: String): String? {
        val body = httpGet(url) ?: return null
        return body.lineSequence().firstOrNull()
            ?.trim()
            ?.split(Regex("\\s+"))
            ?.firstOrNull()
            ?.takeIf { it.length == 64 && it.all { c -> c.isDigit() || c in 'a'..'f' || c in 'A'..'F' } }
    }

    private fun parseVersion(version: String): List<Int> {
        return version.trim().removePrefix("v").split("-")[0]
            .split(".")
            .mapNotNull { it.toIntOrNull() }
    }

    private fun extractSuffix(version: String): String {
        val idx = version.indexOf('-')
        return if (idx >= 0) version.substring(idx + 1).trim() else ""
    }
}
