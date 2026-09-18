package com.mw.offlineupi.service

import android.Manifest
import android.content.ComponentName
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
import com.mw.offlineupi.util.DiagnosticLog
import com.mw.offlineupi.BiometricAuthActivity
import com.mw.offlineupi.OfflineUpiApp
import com.mw.offlineupi.util.CharArrayCharSequence
import com.mw.offlineupi.util.Validators
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

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

    /**
     * @param unrecognizedResponse the bank's raw final text, set only when the failure *is*
     *   "the app could not classify this reply". Every other failure — a decline, a timeout, a
     *   wrong-PIN lockout — leaves it null. The UI offers to report it; see
     *   [com.mw.offlineupi.util.shareUnrecognisedResponse].
     */
    data class Failed(
        val reason: String,
        val canRetry: Boolean = true,
        val unrecognizedResponse: String? = null
    ) : UssdState()
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
    private var cachedBiometricEnabled: Boolean = false

    /**
     * Whether a PIN is stored, read once per session in [startCommand]. Cached because the check
     * is now a suspend call (DataStore) and the PIN-prompt path runs inside a non-suspend
     * accessibility callback.
     */
    private var cachedHasStoredPin: Boolean = false

    /**
     * Scope for the few genuinely asynchronous steps that start from non-suspend callbacks
     * (reading the auth-bound PIN after a biometric success). `Main.immediate` keeps the state
     * machine on the main thread, which every other part of this object already assumes; the
     * suspend functions it calls switch to IO internally.
     */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var wrongPinAttempts = 0
    private var pinSubmitted = false

    /**
     * PIN carried across a re-dial after a wrong-PIN attempt, held as a [CharArray] so it can be
     * wiped. Always clear it through [clearPendingPin] — a `String` here would leave the PIN
     * recoverable from the heap for the lifetime of the process.
     */
    private var pendingPin: CharArray? = null
    private const val MAX_PIN_ATTEMPTS = 3

    /** Zeroes and drops [pendingPin]. Safe to call when nothing is pending. */
    private fun clearPendingPin() {
        pendingPin?.fill(' ')
        pendingPin = null
    }
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

    /**
     * Whether *this app's* USSD accessibility service is the one the user enabled.
     *
     * Both checks used to be a substring test for the package name, which passes for any
     * accessibility service shipped by this package and also for any other package whose id merely
     * contains this one's name. The component is compared properly instead: parsing each id back
     * into a [ComponentName] normalises the two forms the platform stores it in
     * (`pkg/.service.UssdAccessibilityService` and `pkg/com.mw.offlineupi.service.…`), which a
     * string comparison against one spelling would get wrong. This removes the dead
     * `targetService` string the loose check left behind.
     *
     * The `Settings.Secure` fallback stays: [AccessibilityManager.getEnabledAccessibilityServiceList]
     * has been observed to come back empty on some OEM builds for a moment after the user toggles
     * a service on, and this gate decides whether a payment can start at all.
     */
    fun isAccessibilityEnabled(context: Context): Boolean {
        val target = ComponentName(context.packageName, UssdAccessibilityService::class.java.name)

        val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
        val enabledServices = am?.getEnabledAccessibilityServiceList(
            AccessibilityEvent.TYPES_ALL_MASK
        ).orEmpty()
        if (enabledServices.any { ComponentName.unflattenFromString(it.id) == target }) return true

        val enabledString = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabledString.split(':').any { ComponentName.unflattenFromString(it) == target }
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

    /**
     * Whether the SIM looks usable for USSD. USSD rides the cellular signalling channel, not
     * data, so only SIM readiness and voice registration matter.
     *
     * Both reads are inside the `try`: `simState` throws on some OEM builds with no SIM tray, and
     * `serviceState` throws `SecurityException` on API 31+ when `READ_PHONE_STATE` was revoked
     * after the check above — an uncaught throw here surfaced as a crash instead of a "no
     * network" message. Any failure to determine the state is treated as "probably fine" and the
     * dial is attempted, since a false negative blocks a payment that would have worked.
     */
    private fun hasCellularConnectivity(context: Context): Boolean {
        val tm = context.getSystemService(Context.TELEPHONY_SERVICE)
            as? android.telephony.TelephonyManager ?: return true
        return try {
            if (tm.simState != android.telephony.TelephonyManager.SIM_STATE_READY) return false
            val serviceState = tm.serviceState
            serviceState == null ||
                serviceState.state == android.telephony.ServiceState.STATE_IN_SERVICE
        } catch (e: Exception) {
            Log.w(TAG, "Could not read telephony state: ${e.javaClass.simpleName}")
            true
        }
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
    /**
     * Central failure handler — ensures consistent cleanup.
     *
     * This clears the *whole* session, not just the run flags. Leaving `pendingPin`,
     * `currentCommand`, `messageQueue`, `verifiedPayeeName` and `lastSubmittedAmount` behind meant
     * a failed payment left the PIN in memory and let the next, unrelated session inherit the
     * previous recipient's queue and payee name.
     */
    private fun failSession(
        reason: String,
        canRetry: Boolean = true,
        unrecognizedResponse: String? = null
    ) {
        Log.d(TAG, "failSession: $reason")
        isRunning = false
        sessionComplete = false
        cancelRequested = false
        retryCount = 0
        pinSubmitted = false
        clearPendingPin()
        currentCommand = null
        currentStep = 0
        messageQueue = emptyList()
        verifiedPayeeName = null
        lastSubmittedAmount = ""
        lastSubmittedNote = ""
        // Keep isRunning false but set a short window to dismiss stale dialogs
        staleDismissUntil = System.currentTimeMillis() + STALE_DISMISS_WINDOW_MS
        _state.value =
            UssdState.Failed(friendlyErrorMessage(reason), canRetry, unrecognizedResponse)
        OverlayManager.hide()
    }

    /**
     * Bump session ID to invalidate all pending handler callbacks from the old session.
     */
    private fun newSession() {
        sessionId++
    }

    /**
     * Validates a command before any dialing happens.
     *
     * This is the one funnel every screen goes through, which is why the check lives here rather
     * than in each screen: `Validators.isValidAmount` previously had no call sites at all, and the
     * only real check was an inline one in the overlay's amount field — so the QR-scan and
     * saved-recipient paths reached the bank unvalidated.
     *
     * Returns a user-facing reason, or `null` when the command is fine.
     */
    private fun validateCommand(command: UssdCommand): String? {
        when (command.type) {
            UssdCommandType.SEND_MONEY, UssdCommandType.REQUEST_MONEY -> {
                val id = command.recipientId.trim()
                if (id.isEmpty()) return "No recipient specified"
                val idOk = if (id.contains("@")) Validators.isValidUpiId(id)
                else Validators.isValidPhoneNumber(id)
                if (!idOk) {
                    return if (id.contains("@")) "Invalid UPI ID. Please check and try again."
                    else "Invalid mobile number. Please check and try again."
                }
                // An empty amount is legitimate here: the two-step flow collects it from the
                // overlay after the bank verifies the payee name. It is validated on that path by
                // sendAmountResponse().
                if (command.amount.isNotEmpty()) {
                    Validators.amountError(command.amount)?.let { return it }
                }
            }
            UssdCommandType.CHECK_BALANCE, UssdCommandType.MY_PROFILE -> Unit
        }
        return null
    }

    /**
     * Monotonic session-ownership token, handed to callers by [currentSessionOwner] and checked
     * with [ownsSession].
     *
     * Every screen's ViewModel collects the same global [state], so with two of them alive both
     * would react to a single Success and each write its own transaction row. A ViewModel now
     * records the token it started its command with and ignores terminal states that belong to
     * someone else's session.
     */
    @Volatile
    private var sessionOwner: Long = 0

    /** Token identifying the session started by the most recent [startCommand]. */
    val currentSessionOwner: Long get() = sessionOwner

    /** True when [token] identifies the session currently in flight. */
    fun ownsSession(token: Long): Boolean = token == sessionOwner

    /**
     * Post a delayed runnable that is scoped to the current session.
     * If the session has changed by the time the runnable fires, it's a no-op.
     */
    private fun postSessionDelayed(delayMs: Long, action: () -> Unit) {
        val expectedSession = sessionId
        handler.postDelayed({
            if (sessionId == expectedSession) {
                // These run on the main thread and drive the same accessibility node tree as
                // onAccessibilityEvent, which already refuses to let a throw escape. This path
                // had no such guard, so an exception here reached the main Looper and killed the
                // app mid-payment instead of failing a session the user could retry.
                try {
                    action()
                } catch (t: Throwable) {
                    Log.e(TAG, "Session callback threw", t)
                    DiagnosticLog.logThrowable("  session callback threw", t)
                    onAccessibilityFailure()
                }
            } else {
                Log.d(TAG, "Ignoring stale callback from session $expectedSession (current=$sessionId)")
            }
        }, delayMs)
    }

    suspend fun startCommand(context: Context, command: UssdCommand) {
        appContext = context.applicationContext
        DiagnosticLog.log("startCommand type=${command.type}")

        // Prevent concurrent sessions
        if (isRunning) {
            Log.w(TAG, "startCommand called while session is already running — ignoring")
            DiagnosticLog.log("  refused: session already running")
            return
        }

        if (!hasRequiredPermissions(context)) {
            DiagnosticLog.log("  refused: missing phone permissions")
            _state.value = UssdState.Failed("Phone call & read phone state permissions required", canRetry = true)
            return
        }

        if (!isAccessibilityEnabled(context)) {
            DiagnosticLog.log("  refused: accessibility not enabled")
            _state.value = UssdState.Failed(
                "Accessibility service not enabled. Please enable it in Settings.",
                canRetry = true
            )
            return
        }

        if (!hasCellularConnectivity(context)) {
            DiagnosticLog.log("  refused: no cellular")
            _state.value = UssdState.Failed(
                "No mobile network. USSD requires cellular connectivity.",
                canRetry = true
            )
            return
        }

        // Validate the payload at this boundary — the single point every screen funnels through.
        // Doing it only in the overlay/screen left the QR-scan and deep-link paths unchecked, and
        // an out-of-range amount is rejected by the bank *after* the PIN has been entered.
        validateCommand(command)?.let { reason ->
            _state.value = UssdState.Failed(reason, canRetry = true)
            return
        }

        // Read prefs BEFORE claiming the session. These are suspending, and setting isRunning
        // first meant a cancelled caller (screen closed mid-read) left isRunning stuck true,
        // locking out every future payment until the process died.
        val prefs = (context.applicationContext as? OfflineUpiApp)?.preferences
        val slot = try { prefs?.simSlot?.first() ?: 0 } catch (_: Exception) { 0 }
        val biometric = try { prefs?.biometricEnabled?.first() ?: false } catch (_: Exception) { false }
        val hasPin = if (biometric) {
            try { prefs?.hasEncryptedUpiPin() ?: false } catch (_: Exception) { false }
        } else false

        // Re-check: another caller may have claimed the session while we were suspended.
        if (isRunning) {
            Log.w(TAG, "Session claimed while loading preferences — ignoring")
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
        clearPendingPin()
        simSlot = slot
        cachedBiometricEnabled = biometric
        cachedHasStoredPin = hasPin
        // Only reset wrong PIN counter at the start of a fresh command (not retries)
        if (_state.value is UssdState.Idle || _state.value is UssdState.Failed || _state.value is UssdState.Success) {
            wrongPinAttempts = 0
        }
        messageQueue = buildMessageQueue(command)
        // Last statement before the (non-suspending) dial: no suspension point can now strand it.
        isRunning = true
        sessionOwner++

        _state.value = UssdState.Dialing
        OverlayManager.showProgress("Processing...", "Please wait")

        DiagnosticLog.log("  session claimed, dialing; serviceBound=${UssdAccessibilityService.getInstance() != null}")
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
                val isBalance = command.type == UssdCommandType.CHECK_BALANCE &&
                    isBalanceResponse(responseText)
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
                        try {
                            UssdAccessibilityService.send(CharArrayCharSequence(pin))
                        } finally {
                            pin.fill(' ')
                        }
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
            // The newSession() above cancelled the pending timeout. Returning without re-arming
            // left the session with no watchdog at all: if the bank sent a "please wait" dialog and
            // then nothing, the spinner stayed up forever and the only way out was force-stopping
            // the app. Re-arm the step timeout so a stalled session still fails cleanly.
            scheduleTimeout(STEP_TIMEOUT_MS, "USSD response timed out")
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
        //
        // The response text itself is deliberately not logged: Log.w survives R8 into release
        // builds (only v/d are stripped), and bank USSD text can carry account fragments and
        // balances. The length is all that is useful for spotting a classifier gap.
        //
        // It is carried on the Failed state instead, so the failure screen can show the user the
        // exact text and offer to share it. That is the only route by which an unclassified bank
        // wording can reach the pattern lists in UssdResponseClassifier: it never leaves the
        // device unless the user reads it and picks a destination themselves.
        Log.w(TAG, "Unrecognized final response (${responseText.length} chars), treating as failure")
        failSession(
            responseText.take(200).ifBlank { "Unexpected USSD response" },
            unrecognizedResponse = responseText.take(500).takeIf { it.isNotBlank() }
        )
    }

    /**
     * Check if biometric PIN auto-fill is available and initiate it.
     * Returns true if biometric flow was started, false if manual PIN entry should be shown.
     */
    private fun tryBiometricPinAutoFill(): Boolean {
        val ctx = appContext ?: return false
        val app = ctx as? OfflineUpiApp ?: return false

        // Both reads are cached at startCommand(): the PIN-presence check is a suspend DataStore
        // read and this runs inside a non-suspend accessibility callback.
        if (!cachedBiometricEnabled || !cachedHasStoredPin) {
            return false
        }

        Log.d(TAG, "Biometric PIN auto-fill: launching biometric auth")
        _state.value = UssdState.Processing("Authenticating...", 0.9f)
        OverlayManager.showProgress("Waiting for biometric...", "Authenticate to authorize payment")

        var intent: Intent? = null
        try {
            intent = BiometricAuthActivity.createIntent(
                context = ctx,
                onSuccess = {
                    Log.d(TAG, "Biometric auth succeeded, reading stored PIN")
                    // The PIN store is auth-bound: this read only succeeds because the prompt
                    // above just authenticated the user, and it must happen inside the Keystore's
                    // validity window (SecurePrefs.AUTH_VALIDITY_SECONDS), hence no delay here.
                    scope.launch {
                        val storedPin = app.preferences.getEncryptedUpiPin()
                        if (storedPin != null) {
                            sendPinResponse(storedPin)
                        } else {
                            // Either no PIN, or the Keystore refused (biometrics re-enrolled, or
                            // the auth window already elapsed). Indistinguishable, and manual
                            // entry is the right answer for both.
                            Log.w(TAG, "Stored PIN unavailable after biometric success")
                            promptManualPin()
                        }
                    }
                },
                onError = { msg ->
                    Log.d(TAG, "Biometric auth failed: $msg — falling back to manual PIN entry")
                    promptManualPin()
                }
            )
            ctx.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch BiometricAuthActivity", e)
            // createIntent already parked the callbacks in a process-lifetime map keyed by the
            // intent's token. Nothing will ever consume them now, so drop the entry instead of
            // leaking it — and the pair captures this whole session's continuation.
            intent?.let { BiometricAuthActivity.discardRequest(it) }
            return false
        }

        return true
    }

    /** Shows the overlay PIN pad for the current command. */
    private fun promptManualPin() {
        _state.value = UssdState.WaitingForPin("Enter UPI PIN")
        OverlayManager.showPinEntry(
            "Enter UPI PIN",
            payeeName = verifiedPayeeName ?: currentCommand?.recipientId,
            amount = lastSubmittedAmount.ifEmpty { currentCommand?.amount }
                .takeIf { !it.isNullOrEmpty() }
        )
    }

    /**
     * Submits a UPI PIN to the USSD dialog.
     *
     * Takes ownership of [pin]: the array is wiped once it has been handed to the accessibility
     * service (or stored as [pendingPin] for a post-wrong-PIN re-dial), so callers must not reuse
     * it. It is a [CharArray] rather than a `String` so the PIN can actually be erased — see
     * [CharArrayCharSequence].
     */
    fun sendPinResponse(pin: CharArray) {
        Log.d(TAG, "Sending PIN response (wrongPinAttempts=$wrongPinAttempts)")
        pinSubmitted = true
        _state.value = UssdState.Processing("Processing...", 0.9f)
        OverlayManager.showProgress("Processing...", "Please wait")

        if (wrongPinAttempts > 0) {
            // After a wrong PIN, the USSD session is dead. We need to re-dial
            // the entire flow and auto-submit the PIN when the prompt appears.
            clearPendingPin()
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
                try {
                    UssdAccessibilityService.send(CharArrayCharSequence(pin))
                } finally {
                    pin.fill(' ')
                }
            }
            scheduleTimeout(STEP_TIMEOUT_MS, "PIN verification timed out")
        }
    }

    /**
     * Called by [UssdAccessibilityService] when an accessibility callback threw.
     *
     * The screen-scraping state machine cannot recover from an unknown node-tree failure
     * mid-session — the phone dialog may or may not still be up — so the session is failed with a
     * retryable message rather than left waiting on a timeout that may never fire.
     */
    fun onAccessibilityFailure() {
        if (!isRunning) return
        Log.w(TAG, "Accessibility failure while a session was active — failing the session")
        failSession("Something went wrong reading the USSD dialog. Please try again.")
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
        // The amount collected by the overlay reaches the bank through here, so it is validated
        // here too — the overlay's own field check is a convenience, not the boundary.
        if (messages.isNotEmpty()) {
            Validators.amountError(messages[0])?.let { reason ->
                Log.w(TAG, "Rejecting continueSession amount: $reason")
                OverlayManager.showAmountError(reason)
                return
            }
        }
        // Store submitted amount/note for ViewModel to read
        if (messages.isNotEmpty()) lastSubmittedAmount = messages[0]
        if (messages.size > 1) lastSubmittedNote = messages[1]
        Log.d(TAG, "Continuing session with ${messages.size} messages")
        messageQueue = messageQueue + messages.toList()
        // Bounds guard: currentStep is advanced from accessibility callbacks, and a duplicate
        // dialog event could push it past the queue. An IndexOutOfBoundsException thrown from
        // inside an a11y callback takes the whole service down with it.
        if (currentStep !in messageQueue.indices) {
            Log.w(TAG, "continueSession: step $currentStep outside queue (${messageQueue.size})")
            failSession("Lost track of the USSD session. Please try again.")
            return
        }
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

    // ---- Response classification -------------------------------------------------------------
    // The pattern lists and the pure predicates live in UssdResponseClassifier so they can be
    // unit-tested without this object's Looper/Dispatchers.Main initialisers. These wrappers keep
    // the call sites below unchanged.

    internal fun isErrorResponse(text: String): Boolean =
        UssdResponseClassifier.isErrorResponse(text)

    internal fun isWrongPinResponse(text: String): Boolean =
        UssdResponseClassifier.isWrongPinResponse(text)

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

    /** Classifies against the command currently in flight. */
    internal fun isSuccessResponse(text: String): Boolean =
        UssdResponseClassifier.isSuccessResponse(text, currentCommand?.type)

    internal fun isSuccessResponse(text: String, commandType: UssdCommandType?): Boolean =
        UssdResponseClassifier.isSuccessResponse(text, commandType)

    internal fun isBalanceResponse(text: String): Boolean =
        UssdResponseClassifier.isBalanceResponse(text)

    private fun isPinPrompt(text: String): Boolean =
        UssdResponseClassifier.isPinPrompt(text)

    private fun isSessionExpiredResponse(text: String): Boolean =
        UssdResponseClassifier.isSessionExpiredResponse(text)

    private fun extractReferenceId(text: String): String? =
        UssdResponseClassifier.extractReferenceId(text)

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
