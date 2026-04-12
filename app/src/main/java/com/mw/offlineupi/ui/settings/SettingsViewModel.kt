package com.mw.offlineupi.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mw.offlineupi.OfflineUpiApp
import com.mw.offlineupi.data.local.entity.UserProfile
import com.mw.offlineupi.service.UssdManager
import com.mw.offlineupi.util.AppUpdate
import com.mw.offlineupi.util.UpdateChecker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class SettingsState(
    val appLockEnabled: Boolean = false,
    val biometricEnabled: Boolean = false,
    val themeMode: String = "system",
    val selectedSim: Int = 0,
    val accessibilityEnabled: Boolean = false,
    val userProfile: UserProfile? = null,
    val updateCheckInProgress: Boolean = false,
    val availableUpdate: AppUpdate? = null,
    val updateCheckMessage: String? = null
)

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as OfflineUpiApp
    private val _state = MutableStateFlow(SettingsState())
    val state: StateFlow<SettingsState> = _state

    init {
        viewModelScope.launch {
            val appLock = app.preferences.appLockEnabled.first()
            val biometric = app.preferences.biometricEnabled.first()
            val theme = app.preferences.themeMode.first()
            val sim = app.preferences.simSlot.first()
            val accessibility = UssdManager.isAccessibilityEnabled(application)
            val profile = app.database.userProfileDao().getProfile().first()
            _state.value = SettingsState(
                appLockEnabled = appLock,
                biometricEnabled = biometric,
                themeMode = theme,
                selectedSim = sim,
                accessibilityEnabled = accessibility,
                userProfile = profile
            )
        }
    }

    fun setAppLock(enabled: Boolean) {
        _state.value = _state.value.copy(appLockEnabled = enabled)
        viewModelScope.launch { app.preferences.setAppLockEnabled(enabled) }
    }

    fun setBiometric(enabled: Boolean) {
        _state.value = _state.value.copy(biometricEnabled = enabled)
        viewModelScope.launch { app.preferences.setBiometricEnabled(enabled) }
        if (!enabled) {
            app.preferences.clearEncryptedUpiPin()
        }
    }

    fun saveUpiPin(pin: String) {
        app.preferences.setEncryptedUpiPin(pin)
    }

    fun hasStoredPin(): Boolean {
        return app.preferences.hasEncryptedUpiPin()
    }

    fun setTheme(mode: String) {
        _state.value = _state.value.copy(themeMode = mode)
        viewModelScope.launch { app.preferences.setThemeMode(mode) }
    }

    fun toggleSim() {
        val newSim = if (_state.value.selectedSim == 0) 1 else 0
        _state.value = _state.value.copy(selectedSim = newSim)
        viewModelScope.launch { app.preferences.setSimSlot(newSim) }
    }

    fun setSim(slot: Int) {
        _state.value = _state.value.copy(selectedSim = slot)
        viewModelScope.launch { app.preferences.setSimSlot(slot) }
    }

    fun checkForUpdates() {
        _state.value = _state.value.copy(updateCheckInProgress = true, updateCheckMessage = null)
        viewModelScope.launch {
            val currentVersion = try {
                app.packageManager.getPackageInfo(app.packageName, 0).versionName ?: ""
            } catch (_: Exception) { "" }

            val update = UpdateChecker.checkForUpdate(currentVersion)
            if (update != null) {
                _state.value = _state.value.copy(
                    updateCheckInProgress = false,
                    availableUpdate = update
                )
            } else {
                _state.value = _state.value.copy(
                    updateCheckInProgress = false,
                    updateCheckMessage = "You're on the latest version"
                )
            }
        }
    }

    fun dismissUpdate() {
        _state.value = _state.value.copy(availableUpdate = null)
    }

    fun ignoreUpdate(version: String) {
        _state.value = _state.value.copy(availableUpdate = null)
        viewModelScope.launch { app.preferences.setIgnoredUpdateVersion(version) }
    }

    fun remindLater() {
        _state.value = _state.value.copy(availableUpdate = null)
        viewModelScope.launch { app.preferences.setRemindLaterTime(System.currentTimeMillis()) }
    }

    fun clearUpdateMessage() {
        _state.value = _state.value.copy(updateCheckMessage = null)
    }
}
