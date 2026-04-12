package com.mw.offlineupi

import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import com.mw.offlineupi.util.SecurityUtil

/**
 * Transparent activity used to show BiometricPrompt from non-activity contexts
 * (e.g. AccessibilityService). Finishes immediately after biometric result.
 */
class BiometricAuthActivity : FragmentActivity() {

    companion object {
        const val ACTION_BIOMETRIC_AUTH = "com.mw.offlineupi.BIOMETRIC_AUTH"

        var onBiometricSuccess: (() -> Unit)? = null
        var onBiometricError: ((String) -> Unit)? = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!SecurityUtil.canUseBiometric(this)) {
            onBiometricError?.invoke("Biometric not available")
            finish()
            return
        }

        SecurityUtil.showBiometricPrompt(
            activity = this,
            title = "Verify Identity",
            subtitle = "Authenticate to authorize payment",
            onSuccess = {
                onBiometricSuccess?.invoke()
                onBiometricSuccess = null
                onBiometricError = null
                finish()
            },
            onError = { msg ->
                onBiometricError?.invoke(msg)
                onBiometricSuccess = null
                onBiometricError = null
                finish()
            }
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        // Clear callbacks to avoid leaks
        onBiometricSuccess = null
        onBiometricError = null
    }
}
