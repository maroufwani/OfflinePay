package com.mw.offlineupi.service

import android.accessibilityservice.AccessibilityService
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo

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

        @Volatile
        private var lastEvent: AccessibilityEvent? = null

        fun getInstance(): UssdAccessibilityService? = instance

        fun send(text: String) {
            val svc = instance
            if (svc == null) {
                Log.w(TAG, "send() called but no service instance")
                return
            }
            // Reset dedup so we can detect the NEW dialog that appears after clicking Send
            svc.lastProcessedText = ""
            svc.waitingForSendCallback = false

            Log.d(TAG, "send('${if (text.all { it.isDigit() } && text.length in 4..6) "****" else text}')")
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
            Log.d(TAG, "send() leaves=${leaves.size}: ${leaves.map { "${it.className}='${it.text}'" }}")
            setTextIntoField(leaves, text)
            clickOnButton(leaves, 1) // index 1 = Send/Reply
        }

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
            clickOnButton(leaves, 0) // index 0 = Cancel/OK
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

        private fun setTextIntoField(leaves: List<AccessibilityNodeInfo>, data: String) {
            val arguments = Bundle().apply {
                putCharSequence(
                    AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                    data
                )
            }
            // Avoid logging sensitive data (PIN)
            val logData = if (data.all { it.isDigit() } && data.length in 4..6) "****" else "'$data'"
            for (leaf in leaves) {
                if (leaf.className?.toString() == "android.widget.EditText") {
                    Log.d(TAG, "Found EditText, setting text: $logData")
                    if (!leaf.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)) {
                        // Fallback: clipboard paste
                        Log.d(TAG, "ACTION_SET_TEXT failed, trying paste fallback")
                        try {
                            val cm = UssdManager.appContext?.getSystemService(Context.CLIPBOARD_SERVICE)
                                    as? ClipboardManager
                            cm?.setPrimaryClip(ClipData.newPlainText("ussd", data))
                            leaf.performAction(AccessibilityNodeInfo.ACTION_PASTE)
                            // Clear clipboard immediately to avoid leaking sensitive data
                            cm?.setPrimaryClip(ClipData.newPlainText("", ""))
                        } catch (e: Exception) {
                            Log.e(TAG, "Paste fallback failed", e)
                        }
                    }
                    return
                }
            }
            Log.w(TAG, "No EditText found in leaves to set text")
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

        private fun collectLeaves(
            leaves: MutableList<AccessibilityNodeInfo>,
            node: AccessibilityNodeInfo
        ) {
            if (node.childCount == 0) {
                leaves.add(node)
                return
            }
            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                collectLeaves(leaves, child)
            }
        }

        private fun hasInputText(svc: UssdAccessibilityService, event: AccessibilityEvent): Boolean {
            for (leaf in getLeaves(svc, event)) {
                if (leaf.className?.toString() == "android.widget.EditText") return true
            }
            return false
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

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val pkg = event.packageName?.toString() ?: ""
        val cls = event.className?.toString() ?: ""
        val eventType = event.eventType

        // Log ALL events from com.android.phone for debugging
        if (pkg == "com.android.phone") {
            Log.d(TAG, "EVENT from phone: type=$eventType class=$cls text=${event.text} source=${event.source != null}")
        }

        // Only process if UssdManager has an active session
        if (!UssdManager.isRunning) {
            // Session ended — but there may be stale USSD dialogs to dismiss
            // (e.g. the original menu that was behind an error-code dialog)
            if (UssdManager.shouldDismissStaleDialog() && pkg == "com.android.phone" && isUSSDWidget(event)) {
                Log.d(TAG, "Dismissing stale USSD dialog after session ended: class=$cls")
                cancel()
            }
            return
        }

        // Ignore typeWindowContentChanged entirely.
        if (eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            return
        }

        // typeWindowStateChanged — new dialog appeared
        if (!isUSSDWidget(event)) {
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

        // Store the event for later operations
        lastEvent?.recycle()
        lastEvent = AccessibilityEvent.obtain(event)

        val hasInput = hasInputText(this, event)
        Log.d(TAG, "USSD text: '$responseText', hasInput=$hasInput")

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
    }

    override fun onDestroy() {
        instance = null
        lastEvent?.recycle()
        lastEvent = null
        super.onDestroy()
    }
}
