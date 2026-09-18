package com.mw.offlineupi.data.preferences

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.security.keystore.UserNotAuthenticatedException
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Central factory for [EncryptedSharedPreferences] instances.
 *
 * Two distinct master keys, because they have different threat models:
 *
 * - [openStandard] — a plain AES-256-GCM Keystore key, usable by the app process at any time.
 *   Correct for data that must be readable without user presence (the SQLCipher passphrase,
 *   the cached balance, the phone number).
 *
 * - [openAuthBound] — a key created with `setUserAuthenticationRequired(true)`. The Keystore
 *   physically refuses to decrypt with it unless the user authenticated within
 *   [AUTH_VALIDITY_SECONDS]. This is what makes biometric more than a UI gate for the UPI PIN:
 *   without a fresh auth the ciphertext cannot be read even by code running in this process,
 *   on a rooted device, or through a code-execution bug.
 *
 * Both replace the deprecated `MasterKeys` API with `MasterKey.Builder`.
 */
object SecurePrefs {
    private const val TAG = "SecurePrefs"

    /** Alias of the plain (non-auth-bound) master key. Matches the legacy `MasterKeys` default. */
    private const val STANDARD_KEY_ALIAS = MasterKey.DEFAULT_MASTER_KEY_ALIAS

    /** Alias of the auth-bound master key used for the UPI PIN. */
    private const val AUTH_BOUND_KEY_ALIAS = "offlinepay_auth_bound_master_key"

    /**
     * How long a successful device authentication authorizes the auth-bound key for.
     *
     * 30 s: long enough for the biometric prompt to return and the USSD PIN prompt to be
     * answered on a slow network, short enough that a stolen unlocked device does not leave
     * the PIN readable indefinitely.
     */
    const val AUTH_VALIDITY_SECONDS = 30

    private fun standardMasterKey(context: Context): MasterKey =
        MasterKey.Builder(context.applicationContext, STANDARD_KEY_ALIAS)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

    private fun authBoundMasterKey(context: Context): MasterKey {
        val spec = KeyGenParameterSpec.Builder(
            AUTH_BOUND_KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(true)
            // Deprecated on API 30+ in favour of setUserAuthenticationParameters, but it is the
            // only form available on minSdk 26 and remains functional on newer releases.
            .setUserAuthenticationValidityDurationSeconds(AUTH_VALIDITY_SECONDS)
            .build()

        return MasterKey.Builder(context.applicationContext, AUTH_BOUND_KEY_ALIAS)
            .setKeyGenParameterSpec(spec)
            .build()
    }

    /** Opens prefs encrypted with the plain master key. */
    fun openStandard(context: Context, fileName: String): SharedPreferences =
        create(context, fileName, standardMasterKey(context))

    /**
     * Opens prefs encrypted with the auth-bound master key.
     *
     * Returns `null` when the key cannot be used — no device credential enrolled, biometrics
     * cleared (which permanently invalidates the key), or no authentication within
     * [AUTH_VALIDITY_SECONDS]. Callers must treat `null` as "PIN unavailable" and fall back to
     * manual entry rather than surfacing an error.
     */
    fun openAuthBound(context: Context, fileName: String): SharedPreferences? {
        return try {
            create(context, fileName, authBoundMasterKey(context))
        } catch (e: UserNotAuthenticatedException) {
            Log.d(TAG, "Auth-bound prefs unavailable: no fresh authentication")
            null
        } catch (e: Exception) {
            // KeyPermanentlyInvalidatedException (biometrics changed), no secure lock screen, etc.
            Log.w(TAG, "Auth-bound prefs unavailable: ${e.javaClass.simpleName}")
            null
        }
    }

    /**
     * Deletes the auth-bound key file and its Keystore entry. Used when the stored PIN is
     * cleared, or when the key was permanently invalidated by a biometric enrollment change
     * and must be regenerated.
     */
    fun resetAuthBound(context: Context, fileName: String) {
        try {
            context.applicationContext.deleteSharedPreferences(fileName)
        } catch (e: Exception) {
            Log.w(TAG, "Could not delete $fileName", e)
        }
        try {
            java.security.KeyStore.getInstance("AndroidKeyStore").apply {
                load(null)
                if (containsAlias(AUTH_BOUND_KEY_ALIAS)) deleteEntry(AUTH_BOUND_KEY_ALIAS)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not delete auth-bound Keystore entry", e)
        }
    }

    private fun create(
        context: Context,
        fileName: String,
        masterKey: MasterKey
    ): SharedPreferences = EncryptedSharedPreferences.create(
        context.applicationContext,
        fileName,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )
}
