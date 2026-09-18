package com.mw.offlineupi.service

import android.accessibilityservice.AccessibilityService
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import com.mw.offlineupi.util.DiagnosticLog

/**
 * AccessibilityService that intercepts USSD dialogs from com.android.phone.
 * Based on the multi-session approach from ussd_advanced plugin.
 *
 * The accessibility_service_config.xml restricts to packageNames="com.android.phone"
 * and eventTypes="typeWindowStateChanged|typeWindowContentChanged".
 *
 * Key insight from ussd_advanced: clickOnButton uses leaf INDEX across ALL leaves,
 * not filtered to Button class. Index 0 = first clickable leaf (usually Cancel/OK),
 * index 1 = second clickable leaf (usually Send/Reply).
 */
class UssdAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "UssdA11yService"

        @Volatile
        private var instance: UssdAccessibilityService? = null

        fun getInstance(): UssdAccessibilityService? = instance

        fun send(text: String) = send(text as CharSequence)

        /**
         * Sends [text] into the USSD dialog's EditText and clicks Send.
         *
         * Takes a [CharSequence] so a PIN can be passed as a wipeable `CharArray` wrapper
         * ([CharArrayCharSequence]) instead of an immutable `String`. The accessibility
         * `ACTION_SET_TEXT` argument is itself a `CharSequence`, so no copy is needed on the
         * happy path.
         */
        fun send(text: CharSequence) {
            val svc = instance
            if (svc == null) {
                Log.w(TAG, "send() called but no service instance")
                return
            }
            // Reset dedup so we can detect the NEW dialog that appears after clicking Send
            svc.lastProcessedText = ""
            svc.waitingForSendCallback = false

            Log.d(TAG, "send(${redact(text)})")
            val root = findPhoneDialogRoot(svc)
            if (root == null) {
                Log.w(TAG, "send() could not find phone dialog root")
                UssdManager.onSendFailed()
                return
            }
            // Verify root is actually the phone dialog, not our own app
            if (root.packageName?.toString() != "com.android.phone") {
                Log.w(TAG, "send() root is not phone dialog: pkg=${root.packageName}")
                UssdManager.onSendFailed()
                return
            }
            val leaves = mutableListOf<AccessibilityNodeInfo>()
            collectLeaves(leaves, root)
            Log.d(TAG, "send() leaves=${leaves.size}")
            try {
                setTextIntoField(leaves, text)
                clickOnButton(leaves, 1) // index 1 = Send/Reply
            } finally {
                recycleLeaves(leaves)
            }
        }

        /** Masks anything that looks like a PIN before it reaches logcat. */
        private fun redact(text: CharSequence): String =
            if (text.length in 4..6 && (0 until text.length).all { text[it].isDigit() }) "****"
            else "'$text'"

        fun cancel() {
            val svc = instance
            if (svc == null) {
                Log.w(TAG, "cancel() called but no service instance")
                return
            }
            svc.waitingForSendCallback = false
            Log.d(TAG, "cancel()")
            val root = findPhoneDialogRoot(svc)
            if (root == null) {
                Log.w(TAG, "cancel() could not find phone dialog root")
                return
            }
            val leaves = mutableListOf<AccessibilityNodeInfo>()
            collectLeaves(leaves, root)
            try {
                clickOnButton(leaves, 0) // index 0 = Cancel/OK
            } finally {
                recycleLeaves(leaves)
            }
        }

        fun setWaitingForSend(waiting: Boolean) {
            instance?.waitingForSendCallback = waiting
        }

        /**
         * Find the root node of the com.android.phone dialog by scanning all windows.
         */
        private fun findPhoneDialogRoot(svc: UssdAccessibilityService): AccessibilityNodeInfo? {
            try {
                for (window in svc.windows) {
                    val root = window.root ?: continue
                    if (root.packageName?.toString() == "com.android.phone") {
                        Log.d(TAG, "Found phone dialog window (type=${window.type})")
                        return root
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error scanning windows", e)
            }
            // Fallback to rootInActiveWindow
            val root = svc.rootInActiveWindow
            Log.d(TAG, "Falling back to rootInActiveWindow: pkg=${root?.packageName}")
            return root
        }

        private fun setTextIntoField(leaves: List<AccessibilityNodeInfo>, data: CharSequence) {
            val arguments = Bundle().apply {
                putCharSequence(
                    AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                    data
                )
            }
            for (leaf in leaves) {
                if (leaf.className?.toString() == "android.widget.EditText") {
                    Log.d(TAG, "Found EditText, setting text: ${redact(data)}")
                    if (!leaf.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)) {
                        Log.d(TAG, "ACTION_SET_TEXT failed, trying paste fallback")
                        pasteFallback(leaf, data)
                    }
                    return
                }
            }
            Log.w(TAG, "No EditText found in leaves to set text")
        }

        /**
         * Last-resort clipboard paste when `ACTION_SET_TEXT` is refused by the dialog.
         *
         * The clipboard is a system-wide, cross-app surface, so the PIN must not survive this
         * call. Overwriting with an empty clip (the previous approach) still leaves a readable
         * clipboard entry and, on API 29+, fires the "app pasted from your clipboard" listeners
         * of every clipboard-watching app. [ClipboardManager.clearPrimaryClip] (API 28+) actually
         * removes the entry; below 28 an empty clip is the only option available.
         */
        private fun pasteFallback(leaf: AccessibilityNodeInfo, data: CharSequence) {
            val cm = UssdManager.appContext?.getSystemService(Context.CLIPBOARD_SERVICE)
                as? ClipboardManager ?: return
            try {
                cm.setPrimaryClip(ClipData.newPlainText("ussd", data))
                leaf.performAction(AccessibilityNodeInfo.ACTION_PASTE)
            } catch (e: Exception) {
                Log.e(TAG, "Paste fallback failed", e)
            } finally {
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        cm.clearPrimaryClip()
                    } else {
                        cm.setPrimaryClip(ClipData.newPlainText("", ""))
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Could not clear clipboard after paste fallback")
                }
            }
        }

        /**
         * Click on a Button by index among buttons only.
         * ussd_advanced filters leaves by class containing "button", then indexes.
         * index 0 = Cancel/OK (first button), index 1 = Send/Reply (second button).
         */
        private fun clickOnButton(leaves: List<AccessibilityNodeInfo>, index: Int) {
            Log.d(TAG, "clickOnButton(index=$index) leaves=${leaves.size}")
            var buttonCount = -1
            for (leaf in leaves) {
                val cls = leaf.className?.toString()?.lowercase() ?: ""
                if (cls.contains("button")) {
                    buttonCount++
                    if (buttonCount == index) {
                        Log.d(TAG, "Clicking button at index $index: class=${leaf.className} text=${leaf.text}")
                        leaf.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                        return
                    }
                }
            }
            Log.w(TAG, "Could not find button at index $index (found ${buttonCount + 1} buttons)")
        }

        /**
         * Get all leaf nodes. Try event.source first, then rootInActiveWindow as fallback.
         */
        private fun getLeaves(svc: UssdAccessibilityService, event: AccessibilityEvent): List<AccessibilityNodeInfo> {
            val leaves = mutableListOf<AccessibilityNodeInfo>()
            val root = event.source ?: svc.rootInActiveWindow
            if (root == null) {
                Log.w(TAG, "Both event.source and rootInActiveWindow are null")
                return leaves
            }
            collectLeaves(leaves, root)
            return leaves
        }

        /**
         * Depth cap for [collectLeaves]. USSD dialogs are shallow (root → container → 3-4
         * widgets); anything deeper than this is either an OEM skin's decoration or a cycle in
         * the node graph, which the old uncapped recursion would follow until StackOverflowError
         * inside an accessibility callback — enough to have the service unbound by the system.
         */
        private const val MAX_NODE_DEPTH = 40

        /** Hard cap on collected leaves, as a second guard against a pathological tree. */
        private const val MAX_LEAVES = 500

        private fun collectLeaves(
            leaves: MutableList<AccessibilityNodeInfo>,
            node: AccessibilityNodeInfo,
            depth: Int = 0
        ) {
            if (depth > MAX_NODE_DEPTH || leaves.size >= MAX_LEAVES) {
                Log.w(TAG, "collectLeaves stopped early (depth=$depth, leaves=${leaves.size})")
                return
            }
            if (node.childCount == 0) {
                leaves.add(node)
                return
            }
            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                collectLeaves(leaves, child, depth + 1)
            }
        }

        /**
         * Returns every node obtained by [collectLeaves] to the framework pool.
         *
         * On API 26–32 each `getChild` / `rootInActiveWindow` call hands out a node backed by a
         * finite framework-side pool; a USSD flow walks the dialog tree several times per step,
         * and never recycling eventually exhausts it and starts returning null nodes. From API 33
         * `recycle()` is deprecated and a documented no-op — the platform tracks the objects
         * itself — so it is only called below that, which also keeps the deprecation contained.
         */
        @Suppress("DEPRECATION")
        private fun recycleLeaves(leaves: List<AccessibilityNodeInfo>) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return
            for (leaf in leaves) {
                try {
                    leaf.recycle()
                } catch (_: IllegalStateException) {
                    // Already recycled by the framework; nothing to do.
                }
            }
        }

        private fun hasInputText(svc: UssdAccessibilityService, event: AccessibilityEvent): Boolean {
            val leaves = getLeaves(svc, event)
            try {
                for (leaf in leaves) {
                    if (leaf.className?.toString() == "android.widget.EditText") return true
                }
                return false
            } finally {
                recycleLeaves(leaves)
            }
        }

        /**
         * Check if this event originates from a USSD dialog.
         * We check both by known dialog class names AND by package name.
         * The config already filters to com.android.phone, but some manufacturers
         * use different alert dialog classes.
         */
        private val USSD_DIALOG_CLASSES = setOf(
            "android.app.AlertDialog",
            "amigo.app.AmigoAlertDialog",
            "com.android.phone.oppo.settings.LocalAlertDialog",
            "com.zte.mifavor.widget.AlertDialog",
            "color.support.v7.app.AlertDialog",
            "miui.app.AlertDialog",
            // Samsung
            "com.android.phone.MMIDialogActivity",
            "android.app.Dialog",
            "androidx.appcompat.app.AlertDialog"
        )

        private fun isUSSDWidget(event: AccessibilityEvent): Boolean {
            val className = event.className?.toString() ?: return false
            // Direct class match
            if (className in USSD_DIALOG_CLASSES) return true
            // For Samsung/other OEMs: any AlertDialog from com.android.phone
            if (className.contains("AlertDialog", ignoreCase = true)) return true
            if (className.contains("Dialog", ignoreCase = true) &&
                event.packageName?.toString() == "com.android.phone") return true
            return false
        }
    }

    // Track last processed dialog text to deduplicate duplicate typeWindowStateChanged events
    private var lastProcessedText: String = ""
    private var lastProcessedTime: Long = 0

    // Flag: true while we're waiting for send() postDelayed callback
    private var waitingForSendCallback: Boolean = false

    /**
     * Accessibility callbacks run on the service's main looper: an exception thrown out of here
     * is an uncaught exception in the service process, which tears the service down and silently
     * unbinds it — the user then has to re-enable it in Settings before any payment works again.
     * Everything is therefore funnelled through [handleEvent] behind a catch-all, and a session
     * in flight is failed loudly instead of hanging.
     */
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        try {
            handleEvent(event)
        } catch (t: Throwable) {
            Log.e(TAG, "Unhandled error while processing accessibility event", t)
            try {
                UssdManager.onAccessibilityFailure()
            } catch (inner: Throwable) {
                Log.e(TAG, "Failure handler itself threw", inner)
            }
        }
    }

    private fun handleEvent(event: AccessibilityEvent) {
        val pkg = event.packageName?.toString() ?: ""
        val cls = event.className?.toString() ?: ""
        val eventType = event.eventType

        // Log ALL events from com.android.phone for debugging
        if (pkg == "com.android.phone") {
            Log.d(TAG, "EVENT from phone: type=$eventType class=$cls text=${event.text} source=${event.source != null}")
        }
        // Recorded for every package, not just com.android.phone. If the OEM raises its USSD
        // dialog from a different package, the packageNames filter in the service config means
        // nothing is delivered at all -- which is indistinguishable from a dead service unless
        // the trace shows which packages did get through.
        DiagnosticLog.log("event pkg=$pkg type=$eventType cls=${cls.substringAfterLast('.')}")

        // Only process if UssdManager has an active session
        if (!UssdManager.isRunning) {
            DiagnosticLog.log("  dropped: no active session")
            // Session ended — but there may be stale USSD dialogs to dismiss
            // (e.g. the original menu that was behind an error-code dialog)
            if (UssdManager.shouldDismissStaleDialog() && pkg == "com.android.phone" && isUSSDWidget(event)) {
                Log.d(TAG, "Dismissing stale USSD dialog after session ended: class=$cls")
                cancel()
            }
            return
        }

        // Defence in depth: accessibility_service_config.xml no longer subscribes to
        // typeWindowContentChanged, so this should never fire. Kept because an OEM that delivers
        // unrequested event types would otherwise re-enter the dialog handling below on every
        // character the bank's dialog renders.
        if (eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            return
        }

        // typeWindowStateChanged — new dialog appeared
        if (!isUSSDWidget(event)) {
            DiagnosticLog.log("  dropped: not a USSD dialog")
            // ProgressDialog is not a USSD widget but is from com.android.phone
            if (pkg == "com.android.phone") {
                Log.d(TAG, "Skipped non-dialog event: class=$cls")
            }
            return
        }

        // Extract response text
        val textParts = event.text?.toMutableList() ?: mutableListOf()
        textParts.removeAll { it.toString().uppercase().trim() in listOf("SEND", "CANCEL", "OK") }
        val responseText = textParts.joinToString("\n").trim()

        // Deduplicate: if same text within 2 seconds, skip
        val now = System.currentTimeMillis()
        if (responseText == lastProcessedText && (now - lastProcessedTime) < 2000) {
            Log.d(TAG, "Duplicate event (same text within 2s), skipping")
            return
        }

        // If we're waiting for a send() callback to complete, don't process new events
        if (waitingForSendCallback) {
            Log.d(TAG, "Waiting for send callback, skipping event")
            return
        }

        lastProcessedText = responseText
        lastProcessedTime = now

        Log.d(TAG, "=== USSD DIALOG DETECTED === class=$cls")

        // (The obtained copy of the event that used to be cached here was never read; obtaining
        // it only leaked a pooled AccessibilityEvent per dialog.)

        val hasInput = hasInputText(this, event)
        Log.d(TAG, "USSD text: '$responseText', hasInput=$hasInput")

        DiagnosticLog.log("  USSD dialog accepted, hasInput=$hasInput")
        if (hasInput) {
            // Dialog has an EditText → interactive prompt
            UssdManager.onUssdResponse(responseText)
        } else {
            // No EditText — final message or informational
            UssdManager.onUssdFinalResponse(responseText)
        }
    }

    override fun onInterrupt() {
        Log.d(TAG, "onInterrupt")
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.d(TAG, "Accessibility service connected")
        DiagnosticLog.log("service CONNECTED")
    }

    override fun onDestroy() {
        instance = null
        DiagnosticLog.log("service DESTROYED")
        super.onDestroy()
    }
}
