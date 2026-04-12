package com.mw.offlineupi.service

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.telecom.TelecomManager
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import androidx.core.content.ContextCompat
import com.mw.offlineupi.BiometricAuthActivity
import com.mw.offlineupi.OfflineUpiApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first

sealed class UssdState {
    object Idle : UssdState()
    object Dialing : UssdState()
    data class Processing(val step: String, val progress: Float) : UssdState()
    data class WaitingForPin(
        val message: String,
        val wrongAttempts: Int = 0,
        val errorMessage: String? = null
    ) : UssdState()
    data class WaitingForInput(val message: String, val verifiedName: String? = null) : UssdState()
    data class Success(val message: String, val referenceId: String? = null, val verifiedPayeeName: String? = null) : UssdState()
    data class Failed(val reason: String, val canRetry: Boolean = true) : UssdState()
}

data class UssdCommand(
    val type: UssdCommandType,
    val recipientId: String = "",
    val amount: String = "",
    val note: String = "",
    val accountIndex: Int = 1
)

enum class UssdCommandType {
    SEND_MONEY,
    REQUEST_MONEY,
    CHECK_BALANCE,
    MY_PROFILE
}

/**
 * Multi-session USSD manager using AccessibilityService.
 *
 * Flow: Dial *99# via ACTION_CALL → AccessibilityService intercepts USSD dialogs →
 * this manager sends menu selections step-by-step → handles PIN prompt → returns result.
 */
object UssdManager {
    private const val TAG = "UssdManager"

    private val _state = MutableStateFlow<UssdState>(UssdState.Idle)
    val state: StateFlow<UssdState> = _state.asStateFlow()

    private var currentCommand: UssdCommand? = null
    private var currentStep = 0
    private var messageQueue: List<String> = emptyList()
    private var sessionComplete = false
    private var simSlot = 0
    private var wrongPinAttempts = 0
    private var pinSubmitted = false
    private var pendingPin: String? = null
    private const val MAX_PIN_ATTEMPTS = 3
    @Volatile
    private var cancelRequested = false

    // After a session fails, briefly accept events to dismiss stale USSD dialogs
    @Volatile
    private var staleDismissUntil: Long = 0
    private const val STALE_DISMISS_WINDOW_MS = 5000L

    var appContext: Context? = null
        private set
    private var verifiedPayeeName: String? = null

    /** Amount submitted via overlay during two-step flow */
    var lastSubmittedAmount: String = ""
        private set
    /** Note submitted via overlay during two-step flow */
    var lastSubmittedNote: String = ""
        private set

    @Volatile
    var isRunning: Boolean = false
        private set

    /**
     * Returns true if a stale USSD dialog should be auto-dismissed.
     * After a session fails, we briefly accept events (5s window) to close
     * any leftover dialogs (e.g. the menu that was behind an error-code dialog).
     */
    fun shouldDismissStaleDialog(): Boolean {
        return System.currentTimeMillis() < staleDismissUntil
    }

    private val handler = Handler(Looper.getMainLooper())

    // Session tracking: incremented on each new session to invalidate stale callbacks
    @Volatile
    private var sessionId = 0L

    // Timeout constants
    private const val DIAL_TIMEOUT_MS = 30_000L       // 30s to get first USSD dialog
    private const val STEP_TIMEOUT_MS = 20_000L       // 20s between steps
    private const val CANCEL_TIMEOUT_MS = 5_000L      // 5s for cancel to complete

    fun hasCallPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context, Manifest.permission.CALL_PHONE
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun hasPhoneStatePermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context, Manifest.permission.READ_PHONE_STATE
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun hasRequiredPermissions(context: Context): Boolean {
        return hasCallPermission(context) && hasPhoneStatePermission(context)
    }

    val requiredPermissions = arrayOf(
        Manifest.permission.CALL_PHONE,
        Manifest.permission.READ_PHONE_STATE
    )

    fun isAccessibilityEnabled(context: Context): Boolean {
        val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
            ?: return false
        val enabledServices = am.getEnabledAccessibilityServiceList(
            AccessibilityEvent.TYPES_ALL_MASK
        )
        val targetService = "${context.packageName}/${context.packageName}.service.UssdAccessibilityService"
        for (service in enabledServices) {
            if (service.id.contains(context.packageName)) return true
        }
        // Also check via Settings.Secure
        val enabledString = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabledString.contains(context.packageName)
    }

    /**
     * Get the USSD shortcode that navigates directly to the right sub-menu.
     * This avoids unreliable multi-step menu navigation.
     *
     * *99*1*1# → Send Money → Mobile Number
     * *99*1*3# → Send Money → UPI ID / VPA
     * *99*2#   → Request Money
     * *99*3#   → Check Balance
     */
    private fun getUssdCode(command: UssdCommand): String {
        return when (command.type) {
            UssdCommandType.SEND_MONEY -> {
                val isVpa = command.recipientId.contains("@")
                if (isVpa) "*99*1*3#" else "*99*1*1#"
            }
            UssdCommandType.REQUEST_MONEY -> "*99*2#"
            UssdCommandType.CHECK_BALANCE -> "*99*3#"
            UssdCommandType.MY_PROFILE -> "*99*4*3#"
        }
    }

    /**
     * Build the data-only message queue (no menu selections — shortcode handles navigation).
     */
    private fun buildMessageQueue(command: UssdCommand): List<String> {
        return when (command.type) {
            UssdCommandType.SEND_MONEY -> {
                if (command.amount.isEmpty()) {
                    // Two-step flow: only send recipient, wait for name verification
                    listOf(command.recipientId)
                } else {
                    // After shortcode navigates: VPA/Mobile → Amount → Remarks
                    listOf(command.recipientId, command.amount, command.note.ifEmpty { "1" })
                }
            }
            UssdCommandType.REQUEST_MONEY -> {
                if (command.amount.isEmpty()) {
                    // Two-step flow: only send recipient, wait for name verification
                    listOf(command.recipientId)
                } else {
                    // Recipient → Amount
                    listOf(command.recipientId, command.amount)
                }
            }
            UssdCommandType.CHECK_BALANCE -> {
                // No data needed — just PIN
                emptyList()
            }
            UssdCommandType.MY_PROFILE -> {
                // No data needed — response is automatic
                emptyList()
            }
        }
    }

    private fun hasCellularConnectivity(context: Context): Boolean {
        // USSD works over the cellular signaling channel, not data.
        // We only need the SIM to be registered on a network (voice service).
        val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? android.telephony.TelephonyManager
            ?: return true // Assume connected if we can't check
        val simState = tm.simState
        if (simState != android.telephony.TelephonyManager.SIM_STATE_READY) return false
        // Check if registered on a network (voice/signaling, not data)
        val serviceState = tm.serviceState
        return serviceState == null || serviceState.state == android.telephony.ServiceState.STATE_IN_SERVICE
    }

    /**
     * Schedule a timeout that fires if no USSD response is received within the given duration.
     * The timeout is tied to the current sessionId — if the session advances or resets, the
     * callback becomes a no-op.
     */
    private fun scheduleTimeout(timeoutMs: Long, reason: String) {
        val expectedSession = sessionId
        handler.postDelayed({
            if (sessionId == expectedSession && isRunning && !sessionComplete) {
                Log.w(TAG, "Timeout ($reason) fired for session $expectedSession")
                failSession("$reason. Please try again.", canRetry = true)
            }
        }, timeoutMs)
    }

    /**
     * Central failure handler — ensures consistent cleanup.
     */
    private fun failSession(reason: String, canRetry: Boolean = true) {
        Log.d(TAG, "failSession: $reason")
        isRunning = false
        sessionComplete = false
        cancelRequested = false
        retryCount = 0
        // Keep isRunning false but set a short window to dismiss stale dialogs
        staleDismissUntil = System.currentTimeMillis() + STALE_DISMISS_WINDOW_MS
        _state.value = UssdState.Failed(friendlyErrorMessage(reason), canRetry)
        OverlayManager.hide()
    }

    /**
     * Bump session ID to invalidate all pending handler callbacks from the old session.
     */
    private fun newSession() {
        sessionId++
    }

    /**
     * Post a delayed runnable that is scoped to the current session.
     * If the session has changed by the time the runnable fires, it's a no-op.
     */
    private fun postSessionDelayed(delayMs: Long, action: () -> Unit) {
        val expectedSession = sessionId
        handler.postDelayed({
            if (sessionId == expectedSession) {
                action()
            } else {
                Log.d(TAG, "Ignoring stale callback from session $expectedSession (current=$sessionId)")
            }
        }, delayMs)
    }

    fun startCommand(context: Context, command: UssdCommand) {
        appContext = context.applicationContext

        // Prevent concurrent sessions
        if (isRunning) {
            Log.w(TAG, "startCommand called while session is already running — ignoring")
            return
        }

        if (!hasRequiredPermissions(context)) {
            _state.value = UssdState.Failed("Phone call & read phone state permissions required", canRetry = true)
            return
        }

        if (!isAccessibilityEnabled(context)) {
            _state.value = UssdState.Failed(
                "Accessibility service not enabled. Please enable it in Settings.",
                canRetry = true
            )
            return
        }

        if (!hasCellularConnectivity(context)) {
            _state.value = UssdState.Failed(
                "No mobile network. USSD requires cellular connectivity.",
                canRetry = true
            )
            return
        }

        newSession()
        currentCommand = command
        currentStep = 0
        retryCount = 0
        sessionComplete = false
        pinSubmitted = false
        verifiedPayeeName = null
        cancelRequested = false
        lastSubmittedAmount = ""
        lastSubmittedNote = ""
        // Only reset wrong PIN counter at the start of a fresh command (not retries)
        if (_state.value is UssdState.Idle || _state.value is UssdState.Failed || _state.value is UssdState.Success) {
            wrongPinAttempts = 0
        }
        messageQueue = buildMessageQueue(command)
        isRunning = true

        // Load saved SIM slot
        simSlot = try {
            val prefs = (context.applicationContext as? OfflineUpiApp)?.preferences
            kotlinx.coroutines.runBlocking { prefs?.simSlot?.first() } ?: 0
        } catch (_: Exception) { 0 }

        val description = when (command.type) {
            UssdCommandType.SEND_MONEY -> "Sending ₹${command.amount} to ${command.recipientId}..."
            UssdCommandType.REQUEST_MONEY -> if (command.amount.isNotEmpty()) "Requesting ₹${command.amount} from ${command.recipientId}..." else "Verifying ${command.recipientId}..."
            UssdCommandType.CHECK_BALANCE -> "Checking balance..."
            UssdCommandType.MY_PROFILE -> "Fetching profile..."
        }
        _state.value = UssdState.Dialing
        OverlayManager.showProgress("Processing...", "Please wait")

        val ussdCode = getUssdCode(command)
        Log.d(TAG, "Starting USSD: command=${command.type}, code=$ussdCode, queue=$messageQueue, sim=$simSlot")
        dialUssd(context, ussdCode, simSlot)

        // Start dial timeout
        scheduleTimeout(DIAL_TIMEOUT_MS, "No response from USSD service")
    }

    private fun dialUssd(context: Context, code: String, slot: Int) {
        val phone = code.replace("#", Uri.encode("#"))
        val uri = Uri.parse("tel:$phone")
        val intent = Intent(Intent.ACTION_CALL, uri).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
            putExtra("com.android.phone.force.slot", true)
            putExtra("Cdma_Supp", true)
            // SIM slot extras
            val simSlotNames = arrayOf(
                "extra_asus_dial_use_dualsim", "com.android.phone.extra.slot",
                "slot", "simslot", "sim_slot", "subscription", "Subscription",
                "phone", "com.android.phone.DialingMode", "simSlot",
                "slot_id", "simId", "simnum", "phone_type", "slotId", "slotIdx"
            )
            simSlotNames.forEach { name -> putExtra(name, slot) }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                try {
                    val tm = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
                    val handles = tm?.callCapablePhoneAccounts
                    if (handles != null && handles.size > slot) {
                        putExtra("android.telecom.extra.PHONE_ACCOUNT_HANDLE", handles[slot])
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Could not set phone account handle", e)
                }
            }
        }

        try {
            context.startActivity(intent)
            Log.d(TAG, "Dialed $code via ACTION_CALL")
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception dialing USSD", e)
            isRunning = false
            _state.value = UssdState.Failed("Phone permission denied")
            OverlayManager.hide()
        } catch (e: Exception) {
            Log.e(TAG, "Error dialing USSD", e)
            isRunning = false
            _state.value = UssdState.Failed("Error: ${e.message}")
            OverlayManager.hide()
        }
    }

    /**
     * Called by UssdAccessibilityService when a USSD dialog with an EditText is detected.
     * This means the dialog expects user input.
     */
    fun onUssdResponse(responseText: String) {
        Log.d(TAG, "onUssdResponse step=$currentStep/${messageQueue.size}: '$responseText'")

        // Already waiting for user input — don't re-process the same USSD prompt
        if (_state.value is UssdState.WaitingForInput) {
            Log.d(TAG, "Already in WaitingForInput, ignoring re-shown USSD dialog")
            return
        }

        // Reset timeout — we got a response
        newSession()

        // If cancel was requested, now we have output — cancel the dialog and clean up
        if (cancelRequested) {
            Log.d(TAG, "Cancel was requested, cancelling now that we have output")
            cancelRequested = false
            isRunning = false
            UssdAccessibilityService.cancel()
            _state.value = UssdState.Idle
            OverlayManager.hide()
            return
        }

        // If we already completed (success/error), dismiss any follow-up dialogs
        if (sessionComplete) {
            Log.d(TAG, "Session complete, dismissing follow-up dialog")
            UssdAccessibilityService.cancel()
            isRunning = false
            sessionComplete = false
            OverlayManager.hide()
            return
        }

        val command = currentCommand ?: run {
            failSession("No active command")
            return
        }

        // Check for wrong PIN response (before generic error)
        if (pinSubmitted && isWrongPinResponse(responseText)) {
            handleWrongPin(responseText)
            return
        }

        // Check for session expiry (e.g. "error-code 1" from USSD timeout)
        if (isSessionExpiredResponse(responseText)) {
            Log.d(TAG, "Session expired response detected in interactive dialog")
            UssdAccessibilityService.cancel()
            failSession(responseText.take(200))
            return
        }

        // Check for error responses
        if (isErrorResponse(responseText)) {
            Log.d(TAG, "Error response detected")
            UssdAccessibilityService.cancel()
            failSession(responseText.take(200))
            return
        }

        // If we still have data to send in the queue
        if (currentStep < messageQueue.size) {
            // Try to extract payee name from intermediate responses
            // (e.g. after UPI ID is sent, bank may respond with "Name: JOHN DOE\nEnter amount")
            if (verifiedPayeeName == null) {
                val extracted = extractPayeeName(responseText)
                if (extracted != null) {
                    verifiedPayeeName = extracted
                }
            }

            val nextMessage = messageQueue[currentStep]
            currentStep++

            val totalSteps = messageQueue.size + 1
            val progress = currentStep.toFloat() / totalSteps
            _state.value = UssdState.Processing("Processing...", progress)
            OverlayManager.updateStatus("Processing...", "Please wait")

            // Block event processing until send() completes
            UssdAccessibilityService.setWaitingForSend(true)

            // Delay to allow dialog to stabilize before interacting
            postSessionDelayed(800) {
                Log.d(TAG, "Sending: '$nextMessage'")
                UssdAccessibilityService.send(nextMessage)
            }

            // Schedule step timeout
            scheduleTimeout(STEP_TIMEOUT_MS, "USSD response timed out")
        } else {
            // All data sent — check if this is a success/error/PIN response

            // MY_PROFILE: treat the response as the profile data and complete
            // Must be checked before PIN/success — profile text contains "UPI PIN Set"
            if (command.type == UssdCommandType.MY_PROFILE) {
                Log.d(TAG, "MY_PROFILE response received, completing")
                sessionComplete = true
                isRunning = false
                _state.value = UssdState.Success(responseText.take(500))
                OverlayManager.hide()
                UssdAccessibilityService.cancel()
                return
            }

            // Check for success first (e.g. "payment successful (RefId: ...)")
            // These come as interactive dialogs with "1. Save contact / 2. Exit"
            if (isSuccessResponse(responseText)) {
                Log.d(TAG, "Success response detected after queue exhausted")
                sessionComplete = true // keep isRunning=true so follow-up "Thank you" dialog is processed
                val refId = extractReferenceId(responseText)
                // Always try to extract payee name from success text (may be more accurate)
                verifiedPayeeName = extractPayeeName(responseText) ?: verifiedPayeeName
                val isBalance = isBalanceResponse(responseText)
                _state.value = UssdState.Success(responseText.take(200), refId, verifiedPayeeName)
                OverlayManager.updateStatus(
                    if (isBalance) "Balance retrieved!" else "Payment successful!",
                    "Please wait"
                )
                // Dismiss the dialog — cancel for balance check, send "2" for payment
                UssdAccessibilityService.setWaitingForSend(true)
                postSessionDelayed(500) {
                    if (isBalance) {
                        Log.d(TAG, "Cancelling balance check dialog")
                        UssdAccessibilityService.cancel()
                        isRunning = false
                        sessionComplete = false
                        OverlayManager.hide()
                    } else {
                        Log.d(TAG, "Sending '2' to exit success screen")
                        UssdAccessibilityService.send("2")
                    }
                }
                return
            }

            if (isErrorResponse(responseText)) {
                Log.d(TAG, "Error response detected after queue exhausted")
                UssdAccessibilityService.cancel()
                failSession(responseText.take(200))
                return
            }

            if (!pinSubmitted && isPinPrompt(responseText)) {
                Log.d(TAG, "PIN prompt detected (wrongPinAttempts=$wrongPinAttempts, pendingPin=${pendingPin != null})")
                
                // Extract bank-verified payee name from PIN prompt text
                // Only use if we don't already have a verified name (PIN prompt extractions are less reliable)
                if (verifiedPayeeName == null) {
                    val extractedName = extractPayeeName(responseText)
                    if (extractedName != null) {
                        verifiedPayeeName = extractedName
                    }
                }
                
                // If we have a pending PIN from a retry, auto-submit it
                val pin = pendingPin
                if (pin != null) {
                    pendingPin = null
                    pinSubmitted = true
                    _state.value = UssdState.Processing("Processing...", 0.9f)
                    OverlayManager.updateStatus("Processing...", "Please wait")
                    UssdAccessibilityService.setWaitingForSend(true)
                    postSessionDelayed(800) {
                        Log.d(TAG, "Auto-sending pending PIN")
                        UssdAccessibilityService.send(pin)
                    }
                    scheduleTimeout(STEP_TIMEOUT_MS, "PIN verification timed out")
                    return
                }

                // Check if biometric auto-fill is available (first attempt only)
                if (wrongPinAttempts == 0 && tryBiometricPinAutoFill()) {
                    return
                }

                // Waiting for user PIN — no timeout here (user interaction)
                pinSubmitted = false
                if (wrongPinAttempts > 0) {
                    val errorMsg = "Wrong PIN entered $wrongPinAttempts/$MAX_PIN_ATTEMPTS times"
                    _state.value = UssdState.WaitingForPin(
                        message = responseText.take(200),
                        wrongAttempts = wrongPinAttempts,
                        errorMessage = errorMsg
                    )
                    OverlayManager.showPinEntry(
                        responseText.take(200),
                        wrongAttempts = wrongPinAttempts,
                        maxAttempts = MAX_PIN_ATTEMPTS,
                        payeeName = verifiedPayeeName ?: currentCommand?.recipientId,
                        amount = lastSubmittedAmount.ifEmpty { currentCommand?.amount }.takeIf { !it.isNullOrEmpty() }
                    )
                } else {
                    _state.value = UssdState.WaitingForPin(responseText.take(200))
                    OverlayManager.showPinEntry(
                        responseText.take(200),
                        payeeName = verifiedPayeeName ?: currentCommand?.recipientId,
                        amount = lastSubmittedAmount.ifEmpty { currentCommand?.amount }.takeIf { !it.isNullOrEmpty() }
                    )
                }
                return
            }

            Log.d(TAG, "All data sent, got prompt (possibly confirmation): $responseText")

            val lower = responseText.lowercase()

            // Check if this is a confirmation prompt (e.g. "1.Confirm 2.Change")
            // Must be checked BEFORE amount — confirmation text may contain the word "Amount"
            if (lower.contains("1.confirm") || lower.contains("1. confirm") ||
                (lower.contains("confirm") && lower.contains("change"))) {
                Log.d(TAG, "Confirmation prompt detected, auto-sending '1' to confirm")
                // Extract payee name from confirmation text
                val confirmName = extractPayeeName(responseText)
                Log.d(TAG, "Name from confirmation: '$confirmName' (current: '$verifiedPayeeName')")
                if (confirmName != null) {
                    verifiedPayeeName = confirmName
                }
                _state.value = UssdState.Processing("Confirming...", 0.9f)
                OverlayManager.updateStatus("Confirming...", "Please wait")
                UssdAccessibilityService.setWaitingForSend(true)
                postSessionDelayed(800) {
                    Log.d(TAG, "Sending '1' to confirm")
                    UssdAccessibilityService.send("1")
                }
                scheduleTimeout(STEP_TIMEOUT_MS, "Confirmation response timed out")
                return
            }

            // Check if this looks like an "Enter Amount" prompt (two-step flow)
            if (lower.contains("amount") || lower.contains("enter amount")) {
                Log.d(TAG, "Input prompt detected after queue exhausted, waiting for user input")
                val extractedName = extractPayeeName(responseText) ?: verifiedPayeeName
                verifiedPayeeName = extractedName
                _state.value = UssdState.WaitingForInput(responseText.take(200), extractedName)
                // Show amount entry overlay on top of USSD dialog
                val recipientId = currentCommand?.recipientId ?: ""
                OverlayManager.showAmountEntry(extractedName, recipientId)
                // No timeout — user will provide input
                return
            }

            _state.value = UssdState.Processing("Processing...", 0.9f)
            scheduleTimeout(STEP_TIMEOUT_MS, "Confirmation timed out")
        }
    }

    /**
     * Called by UssdAccessibilityService when a USSD dialog without EditText is detected.
     * This is a final message that was auto-dismissed.
     *
     * However, some devices fire typeWindowContentChanged before the EditText is
     * added to the view hierarchy. If we still have steps queued, ignore empty/short
     * responses and wait for the real dialog with input.
     */
    private var retryCount = 0
    private const val MAX_RETRIES = 2

    fun onUssdFinalResponse(responseText: String) {
        Log.d(TAG, "onUssdFinalResponse: '$responseText' (step=$currentStep/${messageQueue.size}, retry=$retryCount)")

        // Reset timeout — we got a response
        newSession()

        // If cancel was requested, complete it now
        if (cancelRequested) {
            Log.d(TAG, "Cancel was requested, completing cancellation")
            cancelRequested = false
            isRunning = false
            _state.value = UssdState.Idle
            OverlayManager.hide()
            return
        }

        // If session is already complete, just ignore any trailing dialogs
        if (sessionComplete) {
            Log.d(TAG, "Session complete, ignoring final response")
            isRunning = false
            sessionComplete = false
            OverlayManager.hide()
            return
        }

        // Ignore non-meaningful dialogs (progress, welcome, blank)
        val lower = responseText.lowercase()
        if (lower.contains("welcome") || lower.contains("running") || responseText.isBlank()) {
            Log.d(TAG, "Ignoring informational/blank final response")
            return
        }

        // If we still have steps to send, the USSD session died mid-flow.
        // Retry the entire payment from scratch.
        if (currentStep < messageQueue.size) {
            if (retryCount < MAX_RETRIES) {
                retryCount++
                Log.d(TAG, "Session died mid-flow, retrying from scratch (attempt $retryCount)")
                _state.value = UssdState.Processing("Processing...", 0.1f)
                OverlayManager.updateStatus("Processing...", "Please wait")
                val ctx = appContext
                val cmd = currentCommand
                if (ctx != null && cmd != null) {
                    // Reset step counter and re-dial after a delay
                    currentStep = 0
                    postSessionDelayed(2000) {
                        val ussdCode = getUssdCode(cmd)
                        Log.d(TAG, "Retry dialing $ussdCode")
                        dialUssd(ctx, ussdCode, simSlot)
                    }
                    scheduleTimeout(DIAL_TIMEOUT_MS, "Retry connection timed out")
                } else {
                    failSession("Session ended unexpectedly")
                }
            } else {
                Log.d(TAG, "Max retries reached, giving up")
                failSession("Payment failed after $MAX_RETRIES retries. Please try again.")
            }
            return
        }

        // Check for wrong PIN in final response
        if (pinSubmitted && isWrongPinResponse(responseText)) {
            handleWrongPin(responseText)
            return
        }

        // Check for session expiry (e.g. "error-code 1" from USSD timeout)
        if (isSessionExpiredResponse(responseText)) {
            Log.d(TAG, "Session expired response detected, dismissing dialog")
            UssdAccessibilityService.cancel()
            failSession(responseText.take(200))
            return
        }

        // Check for errors
        if (isErrorResponse(responseText)) {
            UssdAccessibilityService.cancel()
            failSession(responseText.take(200))
            return
        }

        // Check for success
        if (isSuccessResponse(responseText)) {
            isRunning = false
            retryCount = 0
            val refId = extractReferenceId(responseText)
            // Always try to extract payee name from success text (may be more accurate)
            verifiedPayeeName = extractPayeeName(responseText) ?: verifiedPayeeName
            _state.value = UssdState.Success(responseText.take(200), refId, verifiedPayeeName)
            OverlayManager.hide()
            return
        }

        // MY_PROFILE: treat any non-error final response as profile data
        if (currentCommand?.type == UssdCommandType.MY_PROFILE) {
            Log.d(TAG, "MY_PROFILE final response received, completing")
            isRunning = false
            _state.value = UssdState.Success(responseText.take(500))
            OverlayManager.hide()
            return
        }

        // Default: treat unrecognized final response as failure, not success.
        // Unknown USSD responses should not silently appear as successful transactions.
        Log.w(TAG, "Unrecognized final response, treating as failure: $responseText")
        failSession(responseText.take(200).ifBlank { "Unexpected USSD response" })
    }

    /**
     * Check if biometric PIN auto-fill is available and initiate it.
     * Returns true if biometric flow was started, false if manual PIN entry should be shown.
     */
    private fun tryBiometricPinAutoFill(): Boolean {
        val ctx = appContext ?: return false
        val app = ctx as? OfflineUpiApp ?: return false

        val biometricEnabled = try {
            kotlinx.coroutines.runBlocking { app.preferences.biometricEnabled.first() }
        } catch (_: Exception) { false }

        if (!biometricEnabled || !app.preferences.hasEncryptedUpiPin()) {
            return false
        }

        Log.d(TAG, "Biometric PIN auto-fill: launching biometric auth")
        _state.value = UssdState.Processing("Authenticating...", 0.9f)
        OverlayManager.showProgress("Waiting for biometric...", "Authenticate to authorize payment")

        BiometricAuthActivity.onBiometricSuccess = {
            Log.d(TAG, "Biometric auth succeeded, auto-filling PIN")
            val storedPin = app.preferences.getEncryptedUpiPin()
            if (storedPin != null) {
                sendPinResponse(storedPin)
            } else {
                Log.w(TAG, "Stored PIN is null after biometric success")
                // Fall back to manual PIN entry
                _state.value = UssdState.WaitingForPin("Enter UPI PIN")
                OverlayManager.showPinEntry(
                    "Enter UPI PIN",
                    payeeName = verifiedPayeeName ?: currentCommand?.recipientId,
                    amount = lastSubmittedAmount.ifEmpty { currentCommand?.amount }.takeIf { !it.isNullOrEmpty() }
                )
            }
        }
        BiometricAuthActivity.onBiometricError = { msg ->
            Log.d(TAG, "Biometric auth failed: $msg — falling back to manual PIN entry")
            // Fall back to manual PIN entry
            _state.value = UssdState.WaitingForPin("Enter UPI PIN")
            OverlayManager.showPinEntry(
                "Enter UPI PIN",
                payeeName = verifiedPayeeName ?: currentCommand?.recipientId,
                amount = lastSubmittedAmount.ifEmpty { currentCommand?.amount }.takeIf { !it.isNullOrEmpty() }
            )
        }

        try {
            val intent = android.content.Intent(ctx, BiometricAuthActivity::class.java).apply {
                flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or
                        android.content.Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
            }
            ctx.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch BiometricAuthActivity", e)
            BiometricAuthActivity.onBiometricSuccess = null
            BiometricAuthActivity.onBiometricError = null
            return false
        }

        return true
    }

    fun sendPinResponse(pin: String) {
        Log.d(TAG, "Sending PIN response (wrongPinAttempts=$wrongPinAttempts)")
        pinSubmitted = true
        _state.value = UssdState.Processing("Processing...", 0.9f)
        OverlayManager.showProgress("Processing...", "Please wait")

        if (wrongPinAttempts > 0) {
            // After a wrong PIN, the USSD session is dead. We need to re-dial
            // the entire flow and auto-submit the PIN when the prompt appears.
            pendingPin = pin
            val ctx = appContext
            val cmd = currentCommand
            if (ctx != null && cmd != null) {
                newSession()
                currentStep = 0
                messageQueue = buildMessageQueue(cmd)
                OverlayManager.updateStatus("Processing...", "Please wait")
                postSessionDelayed(1500) {
                    val ussdCode = getUssdCode(cmd)
                    Log.d(TAG, "Re-dialing $ussdCode for PIN retry")
                    dialUssd(ctx, ussdCode, simSlot)
                }
                scheduleTimeout(DIAL_TIMEOUT_MS, "Reconnection timed out")
            } else {
                failSession("Cannot retry — session data lost")
            }
        } else {
            postSessionDelayed(300) {
                UssdAccessibilityService.send(pin)
            }
            scheduleTimeout(STEP_TIMEOUT_MS, "PIN verification timed out")
        }
    }

    /**
     * Continue an in-progress USSD session by adding more messages to the queue.
     * Used for two-step flows where the first step collects recipient info and
     * the second step collects amount/note after name verification.
     */
    fun continueSession(vararg messages: String) {
        if (_state.value !is UssdState.WaitingForInput) {
            Log.w(TAG, "continueSession called but not in WaitingForInput state")
            return
        }
        // Store submitted amount/note for ViewModel to read
        if (messages.isNotEmpty()) lastSubmittedAmount = messages[0]
        if (messages.size > 1) lastSubmittedNote = messages[1]
        Log.d(TAG, "Continuing session with ${messages.size} messages: ${messages.toList()}")
        messageQueue = messageQueue + messages.toList()
        val nextMessage = messageQueue[currentStep]
        currentStep++

        val totalSteps = messageQueue.size + 1
        val progress = currentStep.toFloat() / totalSteps
        _state.value = UssdState.Processing("Processing...", progress)
        OverlayManager.updateStatus("Processing...", "Please wait")

        UssdAccessibilityService.setWaitingForSend(true)
        postSessionDelayed(800) {
            Log.d(TAG, "Sending continued message: '$nextMessage'")
            UssdAccessibilityService.send(nextMessage)
        }
        scheduleTimeout(STEP_TIMEOUT_MS, "USSD response timed out")
    }

    /**
     * Called when send() cannot find the phone USSD dialog (e.g. dialog timed out).
     */
    fun onSendFailed() {
        Log.d(TAG, "Send failed - USSD dialog not available")
        if (sessionComplete) {
            isRunning = false
            sessionComplete = false
            OverlayManager.hide()
            return
        }
        isRunning = false
        _state.value = UssdState.Failed("USSD session timed out. Please try again.", canRetry = true)
        OverlayManager.hide()
    }

    fun reset() {
        newSession() // Invalidate all pending callbacks
        isRunning = false
        currentCommand = null
        currentStep = 0
        retryCount = 0
        wrongPinAttempts = 0
        pinSubmitted = false
        pendingPin = null
        sessionComplete = false
        cancelRequested = false
        staleDismissUntil = 0
        lastSubmittedAmount = ""
        lastSubmittedNote = ""
        messageQueue = emptyList()
        _state.value = UssdState.Idle
        OverlayManager.hide()
    }

    fun cancelSession() {
        Log.d(TAG, "Cancelling USSD session")
        cancelRequested = false
        isRunning = false
        UssdAccessibilityService.cancel()
        _state.value = UssdState.Idle
        OverlayManager.hide()
    }

    /**
     * Request cancellation. Shows "Cancelling" overlay and waits for the next
     * USSD response before actually cancelling, so we don't leave a dangling dialog.
     */
    fun requestCancel() {
        Log.d(TAG, "Cancel requested by user")
        // Check if waiting for user interaction before setting state
        val wasWaitingForPin = _state.value is UssdState.WaitingForPin
        val wasWaitingForInput = _state.value is UssdState.WaitingForInput
        cancelRequested = true
        _state.value = UssdState.Processing("Processing...", 0f)
        OverlayManager.updateStatus("Cancelling...", "Please wait")
        // If we're currently waiting for user input (PIN or amount prompt) or not running,
        // cancel immediately since there's no pending USSD operation
        if (wasWaitingForPin || wasWaitingForInput || !isRunning) {
            cancelSession()
        } else {
            // Force cancel after timeout if no USSD response comes
            postSessionDelayed(CANCEL_TIMEOUT_MS) {
                if (cancelRequested) {
                    Log.w(TAG, "Cancel timeout — forcing cancellation")
                    cancelSession()
                }
            }
        }
    }

    fun getCurrentCommand(): UssdCommand? = currentCommand

    /**
     * Returns true if we're in a state where we expect the next dialog to have input.
     * Used by the AccessibilityService to handle typeWindowContentChanged events
     * where the EditText appears after the initial typeWindowStateChanged.
     */
    fun isWaitingForDialogInput(): Boolean {
        return isRunning && currentStep <= messageQueue.size
    }

    private fun isErrorResponse(text: String): Boolean {
        val lower = text.lowercase()
        // Don't treat wrong PIN as a generic error — it has its own handler
        if (isWrongPinResponse(text)) return false
        return lower.contains("error") ||
            lower.contains("failed") ||
            lower.contains("invalid") ||
            lower.contains("unable to process") ||
            lower.contains("try again later") ||
            lower.contains("service unavailable") ||
            lower.contains("not registered") ||
            lower.contains("transaction declined") ||
            lower.contains("problem") ||
            lower.contains("connection problem")
    }

    private fun isWrongPinResponse(text: String): Boolean {
        val lower = text.lowercase()
        return lower.contains("incorrect pin") ||
            lower.contains("wrong pin") ||
            lower.contains("invalid pin") ||
            lower.contains("incorrect upi pin") ||
            lower.contains("wrong upi pin") ||
            lower.contains("pin is incorrect") ||
            lower.contains("pin is wrong") ||
            (lower.contains("incorrect") && lower.contains("pin"))
    }

    private fun handleWrongPin(responseText: String) {
        wrongPinAttempts++
        pinSubmitted = false
        Log.d(TAG, "Wrong PIN detected (attempt $wrongPinAttempts/$MAX_PIN_ATTEMPTS)")

        // Cancel the current USSD dialog
        UssdAccessibilityService.cancel()

        if (wrongPinAttempts >= MAX_PIN_ATTEMPTS) {
            isRunning = false
            _state.value = UssdState.Failed(
                "UPI PIN blocked. Too many wrong attempts ($MAX_PIN_ATTEMPTS/$MAX_PIN_ATTEMPTS). " +
                "Your UPI will be suspended for 24 hours. Contact your bank to reset.",
                canRetry = false
            )
            OverlayManager.hide()
            return
        }

        // Show PIN entry again with error counter
        val errorMsg = "Wrong PIN entered $wrongPinAttempts/$MAX_PIN_ATTEMPTS times"
        _state.value = UssdState.WaitingForPin(
            message = "Enter UPI PIN",
            wrongAttempts = wrongPinAttempts,
            errorMessage = errorMsg
        )
        OverlayManager.showPinEntry(
            "Enter UPI PIN",
            wrongAttempts = wrongPinAttempts,
            maxAttempts = MAX_PIN_ATTEMPTS,
            payeeName = verifiedPayeeName ?: currentCommand?.recipientId,
            amount = lastSubmittedAmount.ifEmpty { currentCommand?.amount }.takeIf { !it.isNullOrEmpty() }
        )
    }

    private fun isSuccessResponse(text: String): Boolean {
        val lower = text.lowercase()
        return lower.contains("successful") ||
            lower.contains("success") ||
            lower.contains("completed") ||
            lower.contains("txn id") ||
            lower.contains("transaction id") ||
            lower.contains("reference no") ||
            lower.contains("has been sent") ||
            lower.contains("has been credited") ||
            lower.contains("has been debited") ||
            isBalanceResponse(text)
    }

    private fun isBalanceResponse(text: String): Boolean {
        val lower = text.lowercase()
        return (lower.contains("balance") && (lower.contains("rs") || lower.contains("inr") || lower.contains("₹"))) ||
            lower.contains("your account balance") ||
            lower.contains("available balance") ||
            lower.contains("a/c bal")
    }

    private fun isPinPrompt(text: String): Boolean {
        val lower = text.lowercase()
        return lower.contains("enter pin") ||
            lower.contains("upi pin") ||
            lower.contains("mpin") ||
            lower.contains("enter your pin") ||
            lower.contains("transaction pin") ||
            lower.contains("enter upi pin")
    }

    /**
     * Detect USSD session expiry responses like "error-code\n1" or "error code 1".
     * These appear when the USSD menu times out (~60s of inactivity).
     */
    private fun isSessionExpiredResponse(text: String): Boolean {
        val lower = text.lowercase().trim()
        return lower.startsWith("error-code") || lower.startsWith("error code")
    }

    private fun extractReferenceId(text: String): String? {
        val patterns = listOf(
            Regex("(?i)txn\\s*id[:\\s]*([A-Za-z0-9]+)"),
            Regex("(?i)ref\\s*id[:\\s]*([A-Za-z0-9]+)"),
            Regex("(?i)ref[erence]*\\s*no[:\\s]*([A-Za-z0-9]+)"),
            Regex("(?i)reference[:\\s]*([A-Za-z0-9]+)")
        )
        for (pattern in patterns) {
            val match = pattern.find(text)
            if (match != null) return match.groupValues[1]
        }
        return null
    }

    /**
     * Extract the bank-verified payee name from USSD response text.
     * Typical formats:
     * - "Sending Rs.100.00 to JOHN DOE (john@bank). Enter UPI PIN"
     * - "Your payment to JOHN DOE, for Rs.1 is successful..."
     * - "Rs 100.00 will be sent to JOHN DOE. Enter UPI PIN"
     * - "Pay Rs.100.00 to JOHN DOE@bank..."
     * - "Payee Name: JOHN DOE\nEnter amount"
     * - "Name: JOHN DOE\n..."
     * - "Paying JOHN DOE\nAmount:"
     */
    private fun extractPayeeName(text: String): String? {
        Log.d(TAG, "extractPayeeName input: '${text.take(200)}'")
        val patterns = listOf(
            // "Payee Name: JOHN DOE" or "Name: JOHN DOE" (line-based)
            Regex("(?i)(?:payee\\s*)?name\\s*[:=]\\s*(.+?)(?:\\n|$)"),
            // "Paying JOHN DOE" (before newline or amount)
            Regex("(?i)paying\\s+(.+?)(?:\\n|\\s+Rs|\\s+INR|$)"),
            // "Collecting from NAME" (request money - enter amount prompt)
            Regex("(?i)collecting\\s+from\\s+(.+?)(?:\\n|\\s+Enter|$)"),
            // "You are requesting NAME Amount" or "requesting\nNAME Amount"
            Regex("(?i)(?:you are )?requesting\\s+(.+?)\\s+Amount"),
            // "collect Rs.X from NAME" (request money success)
            Regex("(?i)collect\\s+Rs\\.?\\s*\\d+\\s+from\\s+(.+?)(?:\\s+is\\s|\\.|$)"),
            // "from NAME is successful" / "from NAME."
            Regex("(?i)from\\s+(.+?)\\s+is\\s+successful"),
            // "requesting NAME Amount" (broader fallback)
            Regex("(?i)requesting\\s+(.+?)\\s+amount"),
            // "to PAYEE_NAME, for Rs" or "to PAYEE_NAME for Rs"
            Regex("(?i)to\\s+(.+?)(?:,?\\s+for\\s+Rs)", RegexOption.DOT_MATCHES_ALL),
            // "to PAYEE_NAME. Enter"
            Regex("(?i)to\\s+(.+?)(?:\\.\\s*Enter|\\s+Enter)", RegexOption.DOT_MATCHES_ALL),
            // "sent to PAYEE_NAME Rs"
            Regex("(?i)sent\\s+to\\s+(.+?)\\s+Rs"),
            // "to PAYEE_NAME (upi@id)"
            Regex("(?i)to\\s+(.+?)\\s*\\("),
        )
        for (pattern in patterns) {
            val match = pattern.find(text)
            if (match != null) {
                val name = match.groupValues[1].trim().trimEnd(',', ' ')
                Log.d(TAG, "Pattern '${pattern.pattern}' matched, candidate name: '$name'")
                if (isValidPayeeName(name)) {
                    Log.d(TAG, "Extracted verified payee name: '$name'")
                    return name
                } else {
                    Log.d(TAG, "Name validation failed for: '$name'")
                }
            }
        }
        Log.d(TAG, "No payee name pattern matched for text")
        return null
    }

    private fun isValidPayeeName(name: String): Boolean {
        if (name.length < 2) return false
        val lower = name.lowercase()
        // Reject identifiers and common false matches
        if (lower.startsWith("rs") || lower.startsWith("inr") || lower == "you" || lower == "to") return false
        if (lower.startsWith("upi id") || lower.startsWith("mobile") || lower.startsWith("vpa")) return false
        // Reject if it looks like a UPI address (contains @)
        if (name.contains("@")) return false
        // Reject if it's purely numeric (phone number)
        if (name.all { it.isDigit() || it == '+' || it == '-' || it == ' ' }) return false
        return true
    }

    /**
     * Map raw USSD error text to user-friendly messages.
     */
    private fun friendlyErrorMessage(raw: String): String {
        val lower = raw.lowercase()
        return when {
            lower.contains("insufficient") || lower.contains("low balance") ->
                "Insufficient balance. Please check your account and try again."
            lower.contains("daily limit") || lower.contains("exceeds limit") ->
                "Transaction limit exceeded. Try a smaller amount or try again tomorrow."
            lower.contains("beneficiary bank") || lower.contains("payee bank") ->
                "The recipient's bank is currently unavailable. Please try again later."
            lower.contains("service unavailable") || lower.contains("temporarily unavailable") ->
                "UPI service is temporarily unavailable. Please try again in a few minutes."
            lower.contains("not registered") ->
                "The recipient is not registered for UPI. Please verify the details."
            lower.contains("transaction declined") || lower.contains("declined") ->
                "Transaction was declined by your bank. Please contact your bank for details."
            lower.contains("connection problem") || lower.contains("network") ->
                "Network issue. Please check your mobile signal and try again."
            lower.contains("timeout") || lower.contains("timed out") ->
                "The request timed out. Please check your network and try again."
            lower.contains("no response from ussd") ->
                "Could not connect to USSD service. Ensure you have mobile network coverage."
            lower.contains("invalid") && lower.contains("upi") ->
                "Invalid UPI ID. Please check and try again."
            lower.contains("invalid") && lower.contains("mobile") ->
                "Invalid mobile number. Please check and try again."
            lower.contains("session ended") || lower.contains("session expired") ->
                "USSD session expired. Please try again."
            isSessionExpiredResponse(raw) ->
                "USSD session expired. Please try again."
            lower.contains("retries") || lower.contains("retry") -> raw // Already friendly
            lower.contains("permission") -> raw // Already friendly
            lower.contains("accessibility") -> raw // Already friendly
            lower.contains("cellular") || lower.contains("mobile network") -> raw // Already friendly
            else -> raw // Pass through if no better message
        }
    }
}
