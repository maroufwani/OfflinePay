package com.mw.offlineupi.ui.balance

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mw.offlineupi.OfflineUpiApp
import com.mw.offlineupi.service.UssdCommand
import com.mw.offlineupi.service.UssdCommandType
import com.mw.offlineupi.service.UssdManager
import com.mw.offlineupi.service.UssdState
import com.mw.offlineupi.util.BalanceParser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class BalanceCheckViewModel(application: Application) : AndroidViewModel(application) {

    private companion object {
        const val TAG = "BalanceCheckVM"
    }

    private val app = application as OfflineUpiApp
    private val _lastBalance = MutableStateFlow("")
    val lastBalance: StateFlow<String> = _lastBalance
    private val _lastBalanceTime = MutableStateFlow(0L)
    val lastBalanceTime: StateFlow<Long> = _lastBalanceTime
    val ussdState: StateFlow<UssdState> = UssdManager.state

    init {
        viewModelScope.launch {
            app.preferences.lastBalance.collect { _lastBalance.value = it }
        }
        viewModelScope.launch {
            app.preferences.lastBalanceTime.collect { _lastBalanceTime.value = it }
        }
    }

    fun checkBalance() {
        viewModelScope.launch {
            UssdManager.startCommand(app, UssdCommand(type = UssdCommandType.CHECK_BALANCE))
        }
    }

    /**
     * Persists the balance from a successful CHECK_BALANCE response.
     *
     * Only the number is stored. [rawMessage] is the bank's whole USSD reply — up to 200 characters
     * that can include a masked account number and the bank's own wording — and it used to be
     * written to encrypted prefs verbatim and then rendered as "Last Known Balance". If no amount
     * can be found, nothing is written: a stale number is more useful than the raw text, and the
     * response itself is not something to keep on disk.
     */
    fun saveBalance(rawMessage: String) {
        val amount = BalanceParser.extractAmount(rawMessage)
        if (amount == null) {
            Log.w(TAG, "No amount found in balance response (${rawMessage.length} chars); not stored")
            return
        }
        viewModelScope.launch {
            app.preferences.setLastBalance(amount)
        }
    }

    fun sendPin(pin: CharArray) {
        UssdManager.sendPinResponse(pin)
    }

    fun reset() {
        UssdManager.reset()
    }
}
