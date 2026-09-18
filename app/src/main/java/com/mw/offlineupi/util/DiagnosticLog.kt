package com.mw.offlineupi.util

import android.content.Context
import android.content.Intent
import android.os.Build
import java.util.ArrayDeque

/**
 * A small in-memory trace of what the USSD automation actually did, shareable from Settings.
 *
 * The accessibility flow fails silently by nature: if the service is enabled in system Settings
 * but never bound, or an event never arrives, nothing throws and nothing is logged anywhere the
 * user can reach. On a sideloaded beta with no adb, that is undiagnosable. This keeps the last
 * [CAPACITY] lines in memory -- accessibility callbacks are frequent, so nothing touches disk
 * until the user asks to share.
 *
 * Deliberately not a replacement for logcat: entries are short, carry no bank response text, no
 * amounts and no PIN, so the log can be shared without redaction. See [CrashLog] for the
 * complementary case where the process dies.
 */
object DiagnosticLog {

    private const val CAPACITY = 400
    private const val FRAMES = 14
    private const val CAUSE_DEPTH = 3

    private val lines = ArrayDeque<String>(CAPACITY)

    @Synchronized
    fun log(message: String) {
        if (lines.size >= CAPACITY) lines.removeFirst()
        lines.addLast("${DateFormats.time(System.currentTimeMillis())} $message")
    }

    @Synchronized
    fun snapshot(): List<String> = lines.toList()

    @Synchronized
    fun clear() = lines.clear()

    /**
     * Records a throwable with enough of its stack to place it.
     *
     * A class name on its own is not a diagnosis. "NullPointerException" from the camera
     * initialiser could have come from any of half a dozen library calls, and picking between them
     * by guesswork costs the user a sideload each time. The frames narrow it to one.
     *
     * Frames stay obfuscated on purpose -- they are meant to be retraced against the mapping.txt
     * that shipped with the build, and rewriting them here would need the mapping on the device.
     * Causes are followed because library code wraps aggressively and the outer frame is usually
     * the least interesting one.
     */
    @Synchronized
    fun logThrowable(label: String, t: Throwable) {
        var current: Throwable? = t
        var depth = 0
        while (current != null && depth < CAUSE_DEPTH) {
            val prefix = if (depth == 0) "$label: " else "  caused by "
            log("$prefix${current.javaClass.name}: ${current.message}")
            current.stackTrace.take(FRAMES).forEach { log("    at $it") }
            val next = current.cause
            current = if (next === current) null else next
            depth++
        }
    }

    /**
     * Live state of the pieces the automation depends on, evaluated at call time.
     *
     * [UssdAccessibilityService.getInstance] is the one that matters most: it is null when the
     * service is switched on in system Settings but not actually bound to the app, which presents
     * to the user as "enabled but nothing happens" and is invisible from every other angle.
     */
    fun status(context: Context): String {
        val bound = com.mw.offlineupi.service.UssdAccessibilityService.getInstance() != null
        val enabled = com.mw.offlineupi.service.UssdManager.isAccessibilityEnabled(context)
        val running = com.mw.offlineupi.service.UssdManager.isRunning
        val version = try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "unknown"
        } catch (t: Throwable) {
            "unknown"
        }
        val enabledRaw = try {
            android.provider.Settings.Secure.getString(
                context.contentResolver,
                android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: "(none)"
        } catch (t: Throwable) {
            "(unreadable)"
        }
        return buildString {
            appendLine("App version: $version")
            appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine()
            appendLine("Service BOUND (instance alive): $bound")
            appendLine("Service ENABLED (system list):  $enabled")
            appendLine("Session running:                $running")
            appendLine()
            appendLine("Enabled accessibility services, as the system reports them:")
            for (id in enabledRaw.split(':')) appendLine("  $id")
        }
    }

    /** Opens the share sheet with [status] followed by the trace and any recorded crash. */
    fun share(context: Context) {
        val text = buildString {
            appendLine("--- Offline Pay diagnostics ---")
            appendLine()
            append(status(context))
            appendLine()
            appendLine("--- recent activity ---")
            val trace = snapshot()
            if (trace.isEmpty()) appendLine("(nothing recorded yet)")
            else trace.forEach { appendLine(it) }
            CrashLog.read(context)?.let {
                appendLine()
                appendLine("--- crashes ---")
                appendLine(it)
            }
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Offline Pay diagnostics")
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(Intent.createChooser(intent, "Share diagnostics"))
    }
}
