package com.mw.offlineupi.util

import android.app.KeyguardManager
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

object SecurityUtil {

    private const val TAG = "SecurityUtil"

    /**
     * The authenticator set this API level actually accepts.
     *
     * `BIOMETRIC_STRONG or DEVICE_CREDENTIAL` is documented as an unsupported combination before
     * API 30: on API 26-29 `canAuthenticate` answers `BIOMETRIC_ERROR_UNSUPPORTED` for it and
     * `authenticate` throws. The app used to pass it unconditionally, so on every pre-30 device
     * `canUseBiometric` returned false — which the callers read as "this phone has no lock", and
     * app lock silently did nothing there. Below 30 the credential fallback has to be requested
     * with the deprecated [BiometricPrompt.PromptInfo.Builder.setDeviceCredentialAllowed] instead,
     * which androidx implements down to API 21.
     */
    private fun allowedAuthenticators(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
                    BiometricManager.Authenticators.DEVICE_CREDENTIAL
        } else {
            BiometricManager.Authenticators.BIOMETRIC_STRONG
        }

    /**
     * True when [showBiometricPrompt] can actually authenticate the user.
     *
     * Callers treat false as "there is nothing to verify against" and skip the check entirely, so
     * this must not report false for a phone that does have a usable lock. On API 26-29 that means
     * asking [KeyguardManager] separately: `canAuthenticate` cannot be queried about a device
     * credential there, but the prompt below can still fall back to one.
     */
    fun canUseBiometric(context: Context): Boolean {
        val biometricManager = BiometricManager.from(context)
        if (biometricManager.canAuthenticate(allowedAuthenticators()) ==
            BiometricManager.BIOMETRIC_SUCCESS
        ) {
            return true
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            val keyguard = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
            return keyguard?.isDeviceSecure == true
        }
        return false
    }

    /**
     * Shows the system authentication prompt.
     *
     * The three outcomes are kept apart deliberately:
     *  - [onSuccess]: authenticated.
     *  - [onCancel]: the user dismissed the prompt (back press or negative button), or the system
     *    withdrew it. Terminal, but not something to report as a failure — callers that showed a
     *    toast on every dismissal were telling the user off for changing their mind.
     *  - [onError]: a real, terminal error worth surfacing (lockout, no hardware, ...).
     *
     * [onFailedAttempt] is **not** terminal. `onAuthenticationFailed` only means "that read did not
     * match" — [BiometricPrompt] stays up and handles the retry itself. This used to be wired to
     * [onError], which tore down the payment on one badly-placed finger; the default is now to do
     * nothing and let the prompt ask again.
     */
    fun showBiometricPrompt(
        activity: FragmentActivity,
        title: String = "Verify Identity",
        subtitle: String = "Confirm to proceed with payment",
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
        onCancel: () -> Unit = {},
        onFailedAttempt: () -> Unit = {}
    ) {
        val executor = ContextCompat.getMainExecutor(activity)
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                onSuccess()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                when (errorCode) {
                    BiometricPrompt.ERROR_USER_CANCELED,
                    BiometricPrompt.ERROR_NEGATIVE_BUTTON,
                    BiometricPrompt.ERROR_CANCELED -> onCancel()

                    else -> onError(errString.toString())
                }
            }

            override fun onAuthenticationFailed() {
                onFailedAttempt()
            }
        }

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    setAllowedAuthenticators(allowedAuthenticators())
                } else {
                    // Offers the same biometric-or-screen-lock choice as the API 30+ branch, and
                    // is the only form the platform accepts there. Neither branch may set a
                    // negative button: both allow a device credential.
                    @Suppress("DEPRECATION")
                    setDeviceCredentialAllowed(true)
                }
            }
            .build()

        try {
            BiometricPrompt(activity, executor, callback).authenticate(promptInfo)
        } catch (e: Exception) {
            // authenticate() rejects an authenticator combination the device cannot honour by
            // throwing, and this runs on the payment path: a crash here would take the process
            // down mid-transaction. Report it as a terminal error so the caller can fall back.
            Log.e(TAG, "Could not show biometric prompt", e)
            onError("Authentication unavailable on this device")
        }
    }
}
