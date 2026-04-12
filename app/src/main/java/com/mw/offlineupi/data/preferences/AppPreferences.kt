package com.mw.offlineupi.data.preferences

import android.content.Context
import android.content.SharedPreferences
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "app_preferences")

class AppPreferences(private val context: Context) {

    private val encryptedPrefs: SharedPreferences by lazy {
        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        EncryptedSharedPreferences.create(
            "secure_prefs",
            masterKeyAlias,
            context.applicationContext,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    companion object {
        private val KEY_ONBOARDED = booleanPreferencesKey("is_onboarded")
        private val KEY_ACCESSIBILITY_ENABLED = booleanPreferencesKey("accessibility_consent")
        private val KEY_BIOMETRIC_ENABLED = booleanPreferencesKey("biometric_enabled")
        private val KEY_SIM_SLOT = intPreferencesKey("sim_slot")
        private val KEY_APP_LOCK_ENABLED = booleanPreferencesKey("app_lock_enabled")
        private val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
        private val KEY_IGNORED_UPDATE_VERSION = stringPreferencesKey("ignored_update_version")
        private val KEY_REMIND_LATER_TIME = stringPreferencesKey("remind_later_time")

        // Keys for encrypted prefs
        private const val ENC_PHONE_NUMBER = "phone_number"
        private const val ENC_LAST_BALANCE = "last_balance"
        private const val ENC_LAST_BALANCE_TIME = "last_balance_time"
        private const val ENC_UPI_PIN = "upi_pin"
    }

    // Flows backed by encrypted prefs
    private val _phoneNumber = MutableStateFlow(encryptedPrefs.getString(ENC_PHONE_NUMBER, "") ?: "")
    private val _lastBalance = MutableStateFlow(encryptedPrefs.getString(ENC_LAST_BALANCE, "") ?: "")
    private val _lastBalanceTime = MutableStateFlow(encryptedPrefs.getLong(ENC_LAST_BALANCE_TIME, 0L))

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

    suspend fun setPhoneNumber(value: String) {
        encryptedPrefs.edit().putString(ENC_PHONE_NUMBER, value).apply()
        _phoneNumber.value = value
    }

    suspend fun setAccessibilityConsent(value: Boolean) {
        context.dataStore.edit { it[KEY_ACCESSIBILITY_ENABLED] = value }
    }

    suspend fun setLastBalance(balance: String, time: Long = System.currentTimeMillis()) {
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

    fun setEncryptedUpiPin(pin: String) {
        encryptedPrefs.edit().putString(ENC_UPI_PIN, pin).apply()
    }

    fun getEncryptedUpiPin(): String? {
        return encryptedPrefs.getString(ENC_UPI_PIN, null)
    }

    fun clearEncryptedUpiPin() {
        encryptedPrefs.edit().remove(ENC_UPI_PIN).apply()
    }

    fun hasEncryptedUpiPin(): Boolean {
        return !encryptedPrefs.getString(ENC_UPI_PIN, null).isNullOrEmpty()
    }

    suspend fun setIgnoredUpdateVersion(version: String) {
        context.dataStore.edit { it[KEY_IGNORED_UPDATE_VERSION] = version }
    }

    suspend fun getIgnoredUpdateVersion(): String {
        return context.dataStore.data.map { it[KEY_IGNORED_UPDATE_VERSION] ?: "" }.first()
    }

    suspend fun setRemindLaterTime(time: Long) {
        context.dataStore.edit { it[KEY_REMIND_LATER_TIME] = time.toString() }
    }

    suspend fun getRemindLaterTime(): Long {
        return context.dataStore.data.map {
            (it[KEY_REMIND_LATER_TIME] ?: "0").toLongOrNull() ?: 0L
        }.first()
    }
}
