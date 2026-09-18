package com.mw.offlineupi.ui.onboarding

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mw.offlineupi.OfflineUpiApp
import com.mw.offlineupi.data.local.entity.UserProfile
import com.mw.offlineupi.service.UssdCommand
import com.mw.offlineupi.service.UssdCommandType
import com.mw.offlineupi.service.UssdManager
import com.mw.offlineupi.service.UssdState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class OnboardingState(
    val currentPage: Int = 0,
    val selectedSim: Int = 0,
    val accessibilityConsent: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null,
    val profileFetching: Boolean = false,
    val profileFetched: Boolean = false,
    val profileName: String = "",
    val profileUpiId: String = "",
    val profileBank: String = "",
    val profileAccount: String = "",
    val profileError: String? = null
)

class OnboardingViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as OfflineUpiApp
    private val _state = MutableStateFlow(OnboardingState())
    val state: StateFlow<OnboardingState> = _state

    fun selectSim(slot: Int) {
        _state.value = _state.value.copy(selectedSim = slot)
    }

    fun setAccessibilityConsent(consent: Boolean) {
        _state.value = _state.value.copy(accessibilityConsent = consent)
    }

    fun nextPage() {
        val current = _state.value
        _state.value = current.copy(currentPage = current.currentPage + 1, error = null)
    }

    fun previousPage() {
        val current = _state.value
        if (current.currentPage > 0) {
            _state.value = current.copy(currentPage = current.currentPage - 1, error = null)
        }
    }

    fun fetchProfile() {
        _state.value = _state.value.copy(profileFetching = true, profileError = null)

        val command = UssdCommand(type = UssdCommandType.MY_PROFILE)
        viewModelScope.launch {
            // Save SIM slot first so UssdManager reads the updated value
            app.preferences.setSimSlot(_state.value.selectedSim)
            UssdManager.startCommand(app, command)
        }

        // Observe UssdManager state for the result
        viewModelScope.launch {
            UssdManager.state.collect { ussdState ->
                when (ussdState) {
                    is UssdState.Success -> {
                        val parsed = parseProfileResponse(ussdState.message)
                        if (parsed != null) {
                            // Save to database
                            val profile = UserProfile(
                                phoneNumber = parsed.upiId.substringBefore("@"),
                                name = parsed.name,
                                bankName = parsed.bankName,
                                maskedAccountNumber = parsed.accountNumber,
                                upiId = parsed.upiId,
                                isOnboarded = false
                            )
                            app.database.userProfileDao().upsertProfile(profile)
                            _state.value = _state.value.copy(
                                profileFetching = false,
                                profileFetched = true,
                                profileName = parsed.name,
                                profileUpiId = parsed.upiId,
                                profileBank = parsed.bankName,
                                profileAccount = parsed.accountNumber,
                                profileError = null
                            )
                        } else {
                            _state.value = _state.value.copy(
                                profileFetching = false,
                                profileError = "Could not parse profile"
                            )
                        }
                        UssdManager.reset()
                    }
                    is UssdState.Failed -> {
                        _state.value = _state.value.copy(
                            profileFetching = false,
                            profileError = ussdState.reason
                        )
                        UssdManager.reset()
                    }
                    else -> {} // Dialing, Processing — wait
                }
            }
        }
    }

    fun skipProfile() {
        _state.value = _state.value.copy(
            profileFetching = false,
            profileFetched = false,
            profileError = null
        )
        nextPage()
    }

    fun completeOnboarding(onComplete: () -> Unit) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true)
            val current = _state.value
            app.preferences.setOnboarded(true)
            app.preferences.setSimSlot(current.selectedSim)
            app.preferences.setAccessibilityConsent(current.accessibilityConsent)
            _state.value = _state.value.copy(isLoading = false)
            onComplete()
        }
    }

    private data class ParsedProfile(
        val name: String,
        val upiId: String,
        val bankName: String,
        val accountNumber: String
    )

    private fun parseProfileResponse(response: String): ParsedProfile? {
        Log.d("OnboardingVM", "Parsing profile response: '$response'")

        var name = ""
        var upiId = ""
        var bankLine = ""
        var account = ""

        for (line in response.lines()) {
            val trimmed = line.trim()
            when {
                trimmed.startsWith("Name:", ignoreCase = true) ->
                    name = trimmed.substringAfter(":").trim()
                trimmed.startsWith("UPI ID:", ignoreCase = true) ||
                trimmed.startsWith("VPA:", ignoreCase = true) ->
                    upiId = trimmed.substringAfter(":").trim()
                trimmed.contains("@") && upiId.isEmpty() ->
                    upiId = trimmed.trim()
                trimmed.matches(Regex(".*[A-Za-z].*X{2,}.*\\d+.*")) -> {
                    // e.g. "State Bank Of India XXXXXX6565"
                    val parts = trimmed.split(Regex("\\s+(X+\\d+)"))
                    if (parts.isNotEmpty()) bankLine = parts[0].trim()
                    val match = Regex("(X+\\d+)").find(trimmed)
                    if (match != null) account = match.value
                }
            }
        }

        if (name.isEmpty() && upiId.isEmpty()) return null
        return ParsedProfile(name, upiId, bankLine, account)
    }
}
