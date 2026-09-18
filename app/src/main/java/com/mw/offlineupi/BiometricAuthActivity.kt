package com.mw.offlineupi

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.fragment.app.FragmentActivity
import com.mw.offlineupi.util.SecurityUtil
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Transparent activity used to show BiometricPrompt from non-activity contexts
 * (e.g. AccessibilityService). Finishes immediately after biometric result.
 *
 * Callbacks are registered by a per-request token stored in a ConcurrentHashMap,
 * preventing race conditions when multiple biometric flows are initiated concurrently.
 */
class BiometricAuthActivity : FragmentActivity() {

    companion object {
        private const val TAG = "BiometricAuthActivity"
        private const val EXTRA_REQUEST_TOKEN = "biometric_request_token"
        private val pendingCallbacks =
            ConcurrentHashMap<String, Pair<() -> Unit, (String) -> Unit>>()

        fun createIntent(
            context: Context,
            onSuccess: () -> Unit,
            onError: (String) -> Unit
        ): Intent {
            val token = UUID.randomUUID().toString()
            pendingCallbacks[token] = Pair(onSuccess, onError)
            return Intent(context, BiometricAuthActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
                putExtra(EXTRA_REQUEST_TOKEN, token)
            }
        }

        /**
         * Drops the callbacks [createIntent] registered for [intent], without invoking either.
         *
         * Must be called by anyone who builds an intent and then fails to start it: the pair is
         * held in a process-lifetime static map, so an unstarted request otherwise pins the
         * callbacks — and every object they capture — until the process dies.
         */
        fun discardRequest(intent: Intent) {
            intent.getStringExtra(EXTRA_REQUEST_TOKEN)?.let { pendingCallbacks.remove(it) }
        }
    }

    private var requestToken: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestToken = intent.getStringExtra(EXTRA_REQUEST_TOKEN)
        if (requestToken == null || !pendingCallbacks.containsKey(requestToken)) {
            finish()
            return
        }

        if (!SecurityUtil.canUseBiometric(this)) {
            invokeError("Biometric not available")
            finish()
            return
        }

        SecurityUtil.showBiometricPrompt(
            activity = this,
            title = "Verify Identity",
            subtitle = "Authenticate to authorize payment",
            onSuccess = {
                invokeSuccess()
                finish()
            },
            onError = { msg ->
                invokeError(msg)
                finish()
            },
            // Dismissing the prompt has to finish this activity too. It previously did not: the
            // cancel codes were swallowed inside SecurityUtil, so neither callback ran, and a
            // transparent activity with no visible prompt was left sitting on top of the phone
            // dialog until the user found the back button.
            onCancel = {
                invokeError("Authentication cancelled")
                finish()
            }
            // onFailedAttempt is left at its default no-op: the prompt is still up and asking.
        )
    }

    private fun invokeSuccess() {
        val token = requestToken ?: return
        pendingCallbacks.remove(token)?.first?.invoke()
    }

    private fun invokeError(msg: String) {
        val token = requestToken ?: return
        pendingCallbacks.remove(token)?.second?.invoke(msg)
    }

    override fun onDestroy() {
        super.onDestroy()
        // If destroyed without a result (e.g. back press), notify so the
        // USSD session doesn't hang waiting for a callback that will never come.
        //
        // A configuration change is not such a case: the activity is coming straight back and
        // onCreate will re-show the prompt against the same still-registered token. Cancelling
        // here aborted the payment on a rotation, or on any resize/night-mode/locale change the
        // system decided to deliver while the prompt was up.
        if (isChangingConfigurations) {
            Log.d(TAG, "Recreating for a configuration change; keeping the request alive")
            return
        }
        requestToken?.let { token ->
            pendingCallbacks.remove(token)?.second?.invoke("Biometric cancelled")
        }
    }
}
