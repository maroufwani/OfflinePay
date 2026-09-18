package com.mw.offlineupi.util

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Build
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

sealed class InstallResult {
    /** APK downloaded, SHA-256 and signing certificate verified, install session committed. */
    object Success : InstallResult()

    /**
     * The update has no APK URL, no SHA-256, or the app is not permitted to install packages.
     * The caller should fall back to opening [AppUpdate.htmlUrl] in the browser.
     */
    object FallbackToBrowser : InstallResult()

    /** Downloaded file's SHA-256 does not match the published checksum. File has been deleted. */
    data class HashMismatch(val expected: String, val actual: String) : InstallResult()

    /**
     * The downloaded APK is signed by a different key than the installed app, or is for a
     * different package. File has been deleted.
     */
    data class SignatureMismatch(val reason: String) : InstallResult()

    /** Network or I/O error while downloading the APK. */
    data class DownloadFailed(val reason: String) : InstallResult()
}

object ApkInstaller {
    private const val TAG = "ApkInstaller"

    /** Hard ceiling on the download. The app's own APK is a few MB; 100 MB is generous. */
    private const val MAX_APK_BYTES = 100L * 1024 * 1024

    /**
     * Downloads the APK from [AppUpdate.downloadUrl], verifies it, and hands it to the system
     * package installer.
     *
     * Two checks stand between the download and the install, and both must pass:
     * 1. SHA-256 against the checksum published in the release.
     * 2. The APK's package name and signing certificate against the running app's.
     *
     * Check 2 is the one that matters if the release assets themselves are ever tampered with: a
     * hash only proves the file matches the `.sha256` file next to it, and whoever could replace
     * one could replace the other. A signature match cannot be forged without the signing key, and
     * the system would reject a mismatched update anyway — checking here means the user gets a
     * clear message instead of an opaque installer failure after a full download.
     *
     * Returns [InstallResult.FallbackToBrowser] when there is no APK URL, no checksum, or the
     * "install unknown apps" permission has not been granted; the caller should then open
     * [AppUpdate.htmlUrl].
     */
    suspend fun installUpdate(context: Context, update: AppUpdate): InstallResult =
        withContext(Dispatchers.IO) {
            val apkUrl = update.downloadUrl
                .takeIf { it.endsWith(".apk", ignoreCase = true) }
                ?: return@withContext InstallResult.FallbackToBrowser

            val expectedHash = update.apkSha256
                ?: return@withContext InstallResult.FallbackToBrowser

            // On API 26+ the user must explicitly grant "Install unknown apps" to this app.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                !context.packageManager.canRequestPackageInstalls()
            ) {
                Log.w(TAG, "REQUEST_INSTALL_PACKAGES not granted — falling back to browser")
                return@withContext InstallResult.FallbackToBrowser
            }

            val outDir = File(context.cacheDir, "apk_updates").apply { mkdirs() }
            // Clear stale downloads first: a failed or abandoned update previously left its APK in
            // the cache indefinitely, so the directory grew by one full APK per attempted update.
            outDir.listFiles()?.forEach { it.delete() }
            val outFile = File(outDir, "update-${update.versionName}.apk")

            try {
                download(apkUrl, outFile)
            } catch (e: Exception) {
                Log.e(TAG, "APK download failed", e)
                outFile.delete()
                return@withContext InstallResult.DownloadFailed(e.message ?: "Download error")
            }

            val actualHash = sha256Hex(outFile)
            if (!actualHash.equals(expectedHash.trim(), ignoreCase = true)) {
                Log.e(TAG, "SHA-256 mismatch for downloaded APK")
                outFile.delete()
                return@withContext InstallResult.HashMismatch(expectedHash.trim(), actualHash)
            }

            verifySameApp(context, outFile)?.let { reason ->
                Log.e(TAG, "Signature check failed: $reason")
                outFile.delete()
                return@withContext InstallResult.SignatureMismatch(reason)
            }

            try {
                commitSession(context, outFile)
            } catch (e: Exception) {
                Log.e(TAG, "PackageInstaller session failed", e)
                outFile.delete()
                return@withContext InstallResult.DownloadFailed(
                    e.message ?: "Could not start the installer"
                )
            }
            InstallResult.Success
        }

    /** Streams [url] into [outFile], refusing to write more than [MAX_APK_BYTES]. */
    private fun download(url: String, outFile: File) {
        var conn: HttpURLConnection? = null
        try {
            conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 30_000
                readTimeout = 60_000
                instanceFollowRedirects = true
            }
            if (conn.responseCode != 200) throw java.io.IOException("HTTP ${conn.responseCode}")
            conn.inputStream.use { input ->
                outFile.outputStream().use { output ->
                    val buf = ByteArray(64 * 1024)
                    var total = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        total += n
                        if (total > MAX_APK_BYTES) throw java.io.IOException("APK exceeds size limit")
                        output.write(buf, 0, n)
                    }
                }
            }
        } finally {
            try {
                conn?.disconnect()
            } catch (_: Exception) {
            }
        }
    }

    /**
     * Returns a reason string if [apk] is not a same-package, same-signer build of the running
     * app, or `null` when it is.
     */
    private fun verifySameApp(context: Context, apk: File): String? {
        val pm = context.packageManager
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            @Suppress("DEPRECATION")
            PackageManager.GET_SIGNATURES
        }

        val apkInfo = try {
            pm.getPackageArchiveInfo(apk.absolutePath, flags)
        } catch (e: Exception) {
            Log.w(TAG, "Could not read downloaded APK", e)
            null
        } ?: return "The downloaded file is not a readable APK."

        if (apkInfo.packageName != context.packageName) {
            return "The downloaded APK is for a different app (${apkInfo.packageName})."
        }

        val installed = try {
            pm.getPackageInfo(context.packageName, flags)
        } catch (e: Exception) {
            Log.w(TAG, "Could not read installed package info", e)
            return "Could not verify the update's signature."
        }

        val apkCerts = signatureSet(apkInfo.signingCertsCompat())
        val installedCerts = signatureSet(installed.signingCertsCompat())
        if (apkCerts.isEmpty() || installedCerts.isEmpty()) {
            return "Could not verify the update's signature."
        }
        // Any overlap is enough: a key rotation legitimately yields two certificate histories that
        // share a lineage entry, and requiring set equality would reject those valid updates.
        if (apkCerts.intersect(installedCerts).isEmpty()) {
            return "The update is signed by a different key and was not installed."
        }
        return null
    }

    @Suppress("DEPRECATION")
    private fun android.content.pm.PackageInfo.signingCertsCompat(): Array<Signature>? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            signingInfo?.let {
                if (it.hasMultipleSigners()) it.apkContentsSigners else it.signingCertificateHistory
            }
        } else {
            signatures
        }

    /** SHA-256 of each certificate, so comparison never relies on `Signature.equals`. */
    private fun signatureSet(sigs: Array<Signature>?): Set<String> {
        if (sigs == null) return emptySet()
        val digest = MessageDigest.getInstance("SHA-256")
        return sigs.mapNotNull { sig ->
            try {
                digest.reset()
                digest.digest(sig.toByteArray()).joinToString("") { "%02x".format(it) }
            } catch (e: Exception) {
                null
            }
        }.toSet()
    }

    /**
     * Installs [apk] through [PackageInstaller].
     *
     * `ACTION_INSTALL_PACKAGE` (the previous approach) is deprecated since API 29 and gives no
     * result back — the app could not tell an install from a user cancellation, and the FileProvider
     * URI grant it depended on is the part OEM installers most often get wrong. A session hands the
     * bytes to the installer directly and reports the outcome to
     * [ApkInstallReceiver].
     */
    private fun commitSession(context: Context, apk: File) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(
            PackageInstaller.SessionParams.MODE_FULL_INSTALL
        ).apply {
            setAppPackageName(context.packageName)
        }
        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { session ->
            session.openWrite("base.apk", 0, apk.length()).use { out ->
                apk.inputStream().use { input -> input.copyTo(out) }
                session.fsync(out)
            }
            val intent = Intent(context, ApkInstallReceiver::class.java).apply {
                action = ApkInstallReceiver.ACTION_INSTALL_STATUS
            }
            var flags = PendingIntent.FLAG_UPDATE_CURRENT
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                flags = flags or PendingIntent.FLAG_MUTABLE
            }
            val pending = PendingIntent.getBroadcast(context, sessionId, intent, flags)
            session.commit(pending.intentSender)
        }
    }

    private fun sha256Hex(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8 * 1024)
            var read: Int
            while (input.read(buffer).also { read = it } != -1) {
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
