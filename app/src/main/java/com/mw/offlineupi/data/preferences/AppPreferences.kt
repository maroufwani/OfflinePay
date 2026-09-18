package com.mw.offlineupi.data.preferences

import android.content.Context
import android.content.SharedPreferences
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.mw.offlineupi.util.BalanceParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "app_preferences")

class AppPreferences(private val context: Context) {

    /**
     * Standard (non-auth-bound) encrypted prefs: phone number, cached balance.
     *
     * Genuinely lazy — opening this triggers Keystore init plus an
     * EncryptedSharedPreferences open, tens of milliseconds. The flows below no longer touch
     * it from their field initialisers, so nothing forces it open during
     * `OfflineUpiApp.onCreate()` on the main thread.
     */
    private val encryptedPrefs: SharedPreferences by lazy {
        SecurePrefs.openStandard(context, PREFS_SECURE)
    }

    companion object {
        private const val PREFS_SECURE = "secure_prefs"

        /**
         * Separate file for the UPI PIN, encrypted with the auth-bound master key so the
         * Keystore — not just the UI — enforces authentication before the PIN can be read.
         */
        private const val PREFS_PIN = "secure_pin_prefs"

        private val KEY_ONBOARDED = booleanPreferencesKey("is_onboarded")
        private val KEY_ACCESSIBILITY_ENABLED = booleanPreferencesKey("accessibility_consent")
        private val KEY_BIOMETRIC_ENABLED = booleanPreferencesKey("biometric_enabled")
        private val KEY_SIM_SLOT = intPreferencesKey("sim_slot")
        private val KEY_APP_LOCK_ENABLED = booleanPreferencesKey("app_lock_enabled")
        private val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
        private val KEY_IGNORED_UPDATE_VERSION = stringPreferencesKey("ignored_update_version")
        private val KEY_REMIND_LATER_TIME = longPreferencesKey("remind_later_time_ms")
        private val KEY_LAST_UPDATE_CHECK = longPreferencesKey("last_update_check_ms")

        /** Legacy string-typed key, read once for migration. See [getRemindLaterTime]. */
        private val KEY_REMIND_LATER_TIME_LEGACY = stringPreferencesKey("remind_later_time")

        // Keys for encrypted prefs
        private const val ENC_PHONE_NUMBER = "phone_number"
        private const val ENC_LAST_BALANCE = "last_balance"
        private const val ENC_LAST_BALANCE_TIME = "last_balance_time"
        private const val ENC_UPI_PIN = "upi_pin"

        /** Marker in DataStore recording that a PIN exists, so presence checks need no auth. */
        private val KEY_HAS_UPI_PIN = booleanPreferencesKey("has_upi_pin")
    }

    // Backing flows for encrypted values. Seeded empty and hydrated by loadEncryptedValues()
    // off the main thread — reading encryptedPrefs here would defeat the `by lazy` above.
    private val _phoneNumber = MutableStateFlow("")
    private val _lastBalance = MutableStateFlow("")
    private val _lastBalanceTime = MutableStateFlow(0L)

    /**
     * Hydrates the encrypted-prefs-backed flows. Call once from a background coroutine at
     * startup; until it completes the flows emit their empty defaults.
     */
    suspend fun loadEncryptedValues() = withContext(Dispatchers.IO) {
        _phoneNumber.value = encryptedPrefs.getString(ENC_PHONE_NUMBER, "") ?: ""
        _lastBalanceTime.value = encryptedPrefs.getLong(ENC_LAST_BALANCE_TIME, 0L)
        _lastBalance.value = sanitizeStoredBalance(encryptedPrefs.getString(ENC_LAST_BALANCE, "") ?: "")
    }

    /**
     * Cleans up a balance written by a build that stored the bank's entire USSD response.
     *
     * Those entries hold up to 200 characters of bank text — often including a masked account
     * number — under a key the UI renders as "Last Known Balance". Only the amount is kept now
     * (see [com.mw.offlineupi.ui.balance.BalanceCheckViewModel.saveBalance]), so on the first read
     * after an upgrade the stored value is reduced to its number, or dropped entirely when no
     * number can be found rather than left sitting on disk.
     */
    private fun sanitizeStoredBalance(stored: String): String {
        if (stored.isEmpty()) return ""
        val amount = BalanceParser.extractAmount(stored)
        if (amount == stored) return stored
        if (amount == null) {
            encryptedPrefs.edit()
                .remove(ENC_LAST_BALANCE)
                .remove(ENC_LAST_BALANCE_TIME)
                .apply()
            _lastBalanceTime.value = 0L
            return ""
        }
        encryptedPrefs.edit().putString(ENC_LAST_BALANCE, amount).apply()
        return amount
    }

    val isOnboarded: Flow<Boolean> = context.dataStore.data.map { it[KEY_ONBOARDED] ?: false }
    val phoneNumber: Flow<String> = _phoneNumber
    val simSlot: Flow<Int> = context.dataStore.data.map { it[KEY_SIM_SLOT] ?: 0 }
    val accessibilityConsent: Flow<Boolean> = context.dataStore.data.map { it[KEY_ACCESSIBILITY_ENABLED] ?: false }
    val lastBalance: Flow<String> = _lastBalance
    val lastBalanceTime: Flow<Long> = _lastBalanceTime
    val biometricEnabled: Flow<Boolean> = context.dataStore.data.map { it[KEY_BIOMETRIC_ENABLED] ?: false }
    val appLockEnabled: Flow<Boolean> = context.dataStore.data.map { it[KEY_APP_LOCK_ENABLED] ?: false }
    val themeMode: Flow<String> = context.dataStore.data.map { it[KEY_THEME_MODE] ?: "system" }

    suspend fun setOnboarded(value: Boolean) {
        context.dataStore.edit { it[KEY_ONBOARDED] = value }
    }

    suspend fun setPhoneNumber(value: String) = withContext(Dispatchers.IO) {
        // putString performs AES-GCM encryption synchronously; only the disk flush is deferred.
        encryptedPrefs.edit().putString(ENC_PHONE_NUMBER, value).apply()
        _phoneNumber.value = value
    }

    suspend fun setAccessibilityConsent(value: Boolean) {
        context.dataStore.edit { it[KEY_ACCESSIBILITY_ENABLED] = value }
    }

    suspend fun setLastBalance(balance: String, time: Long = System.currentTimeMillis()) =
        withContext(Dispatchers.IO) {
            encryptedPrefs.edit()
                .putString(ENC_LAST_BALANCE, balance)
                .putLong(ENC_LAST_BALANCE_TIME, time)
                .apply()
            _lastBalance.value = balance
            _lastBalanceTime.value = time
        }

    suspend fun setBiometricEnabled(value: Boolean) {
        context.dataStore.edit { it[KEY_BIOMETRIC_ENABLED] = value }
    }

    suspend fun setSimSlot(value: Int) {
        context.dataStore.edit { it[KEY_SIM_SLOT] = value }
    }

    suspend fun setAppLockEnabled(value: Boolean) {
        context.dataStore.edit { it[KEY_APP_LOCK_ENABLED] = value }
    }

    suspend fun setThemeMode(value: String) {
        context.dataStore.edit { it[KEY_THEME_MODE] = value }
    }

    // ---- UPI PIN, auth-bound ----------------------------------------------------------------

    /**
     * Stores the UPI PIN under the auth-bound Keystore key.
     *
     * Requires a device authentication within the last [SecurePrefs.AUTH_VALIDITY_SECONDS] —
     * callers must show a biometric/credential prompt immediately before this. Returns false
     * if the key was unavailable, in which case nothing was written.
     *
     * The PIN arrives as a [CharArray] so no immutable `String` copy is created; the array is
     * the caller's to wipe.
     */
    suspend fun setEncryptedUpiPin(pin: CharArray): Boolean = withContext(Dispatchers.IO) {
        val prefs = SecurePrefs.openAuthBound(context, PREFS_PIN)
            ?: return@withContext false
        // String.valueOf is unavoidable at the SharedPreferences boundary; keep the window
        // as small as possible and never hold a field reference to it.
        val asString = String(pin)
        val ok = try {
            prefs.edit().putString(ENC_UPI_PIN, asString).commit()
        } catch (e: Exception) {
            false
        }
        if (ok) context.dataStore.edit { it[KEY_HAS_UPI_PIN] = true }
        ok
    }

    /**
     * Reads the stored UPI PIN. Returns `null` when no PIN is stored **or** when the Keystore
     * refuses to decrypt because there was no recent authentication — the caller cannot
     * distinguish the two, and in both cases must fall back to manual PIN entry.
     *
     * The result is a [CharArray] the caller is responsible for wiping after use.
     */
    suspend fun getEncryptedUpiPin(): CharArray? = withContext(Dispatchers.IO) {
        val prefs = SecurePrefs.openAuthBound(context, PREFS_PIN) ?: return@withContext null
        val value = try {
            prefs.getString(ENC_UPI_PIN, null)
        } catch (e: Exception) {
            null
        }
        value?.takeIf { it.isNotEmpty() }?.toCharArray()
    }

    suspend fun clearEncryptedUpiPin() = withContext(Dispatchers.IO) {
        // Drop the whole file and the Keystore alias: the PIN is the only thing in there, and
        // this also recovers from a key permanently invalidated by a biometric enrollment change.
        SecurePrefs.resetAuthBound(context, PREFS_PIN)
        context.dataStore.edit { it[KEY_HAS_UPI_PIN] = false }
    }

    /**
     * Whether a PIN is stored. Backed by a DataStore marker rather than by opening the
     * auth-bound prefs, so this answers correctly without requiring authentication.
     */
    suspend fun hasEncryptedUpiPin(): Boolean =
        context.dataStore.data.map { it[KEY_HAS_UPI_PIN] ?: false }.first()

    // ---- Update checking --------------------------------------------------------------------

    suspend fun setIgnoredUpdateVersion(version: String) {
        context.dataStore.edit { it[KEY_IGNORED_UPDATE_VERSION] = version }
    }

    suspend fun getIgnoredUpdateVersion(): String {
        return context.dataStore.data.map { it[KEY_IGNORED_UPDATE_VERSION] ?: "" }.first()
    }

    suspend fun setRemindLaterTime(time: Long) {
        context.dataStore.edit { it[KEY_REMIND_LATER_TIME] = time }
    }

    suspend fun getRemindLaterTime(): Long {
        return context.dataStore.data.map { prefs ->
            // Prefer the long-typed key; fall back to the legacy string key written by
            // versions <= 0.1.1-beta so an existing snooze is not lost.
            prefs[KEY_REMIND_LATER_TIME]
                ?: prefs[KEY_REMIND_LATER_TIME_LEGACY]?.toLongOrNull()
                ?: 0L
        }.first()
    }

    suspend fun setLastUpdateCheck(time: Long) {
        context.dataStore.edit { it[KEY_LAST_UPDATE_CHECK] = time }
    }

    suspend fun getLastUpdateCheck(): Long {
        return context.dataStore.data.map { it[KEY_LAST_UPDATE_CHECK] ?: 0L }.first()
    }
}
