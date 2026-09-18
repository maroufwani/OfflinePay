package com.mw.offlineupi.util

import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

/**
 * Records uncaught exceptions to a file so a crash can be diagnosed after the fact.
 *
 * This app ships as a sideloaded beta: there is no crash reporter, and a tester who is not set up
 * for adb has no way to recover a stack trace once the process has died. Release builds are also
 * minified, so the little that Android does surface is obfuscated. Writing the trace to disk and
 * offering it through the share sheet is the only diagnostic channel that survives both.
 *
 * The handler chains to whatever handler was installed before it, so the process still dies the
 * way it normally would; this only observes. Nothing is sent anywhere on its own -- the user reads
 * the text in the share sheet and chooses whether to send it, the same contract as
 * [shareUnrecognisedResponse].
 */
object CrashLog {

    private const val TAG = "CrashLog"
    private const val FILE_NAME = "crash-log.txt"

    /** Keeps the newest traces and drops older ones once the file passes this size. */
    private const val MAX_BYTES = 64 * 1024

    private fun file(context: Context) = File(context.filesDir, FILE_NAME)

    /**
     * Installs the handler. Safe to call once, early in [android.app.Application.onCreate] -- it
     * touches only [Context.getFilesDir] and does no I/O until something actually crashes.
     */
    fun install(context: Context) {
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            // A failure while recording the crash must not replace the crash being recorded.
            try {
                record(app, thread, error)
            } catch (t: Throwable) {
                Log.w(TAG, "Could not write crash log", t)
            }
            previous?.uncaughtException(thread, error)
        }
    }

    private fun record(context: Context, thread: Thread, error: Throwable) {
        val trace = StringWriter().also { error.printStackTrace(PrintWriter(it)) }.toString()
        val version = try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "unknown"
        } catch (t: Throwable) {
            "unknown"
        }
        val entry = buildString {
            appendLine("=== crash at ${DateFormats.full(System.currentTimeMillis())} ===")
            appendLine("App version: $version")
            appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("Thread: ${thread.name}")
            appendLine(trace)
        }
        val target = file(context)
        val existing = if (target.exists()) target.readText() else ""
        val combined = existing + entry
        // Trim from the front so the most recent crash -- the one being reported -- always survives.
        target.writeText(
            if (combined.length > MAX_BYTES) combined.takeLast(MAX_BYTES) else combined
        )
    }

    /** The recorded traces, or null when the app has not crashed since the log was last cleared. */
    fun read(context: Context): String? {
        val target = file(context)
        if (!target.exists()) return null
        val text = try {
            target.readText()
        } catch (t: Throwable) {
            Log.w(TAG, "Could not read crash log", t)
            return null
        }
        return text.ifBlank { null }
    }

    fun clear(context: Context) {
        runCatching { file(context).delete() }
    }

    /**
     * Opens the share sheet with the recorded traces, or returns false when there is nothing to
     * report. The text is plain and editable in the target app, so the user can read it first.
     */
    fun share(context: Context): Boolean {
        val text = read(context) ?: return false
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Offline Pay crash log")
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(Intent.createChooser(intent, "Share crash log"))
        return true
    }
}
