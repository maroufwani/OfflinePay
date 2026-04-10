package com.mw.offlineupi.ui.balance

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mw.offlineupi.OfflineUpiApp
import com.mw.offlineupi.service.UssdCommand
import com.mw.offlineupi.service.UssdCommandType
import com.mw.offlineupi.service.UssdManager
import com.mw.offlineupi.service.UssdState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class BalanceCheckViewModel(application: Application) : AndroidViewModel(application) {
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
        UssdManager.startCommand(app, UssdCommand(type = UssdCommandType.CHECK_BALANCE))
    }

    fun saveBalance(balance: String) {
        viewModelScope.launch {
            app.preferences.setLastBalance(balance)
        }
    }

    fun sendPin(pin: String) {
        UssdManager.sendPinResponse(pin)
    }

    fun reset() {
        UssdManager.reset()
    }
}
