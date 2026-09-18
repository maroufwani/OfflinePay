package com.mw.offlineupi.ui.requestmoney

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mw.offlineupi.OfflineUpiApp
import com.mw.offlineupi.data.local.entity.TransactionEntity
import com.mw.offlineupi.service.UssdCommand
import com.mw.offlineupi.service.UssdCommandType
import com.mw.offlineupi.service.UssdManager
import com.mw.offlineupi.service.UssdState
import com.mw.offlineupi.util.Validators
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class RequestMoneyState(
    val recipientId: String = "",
    val error: String? = null
)

class RequestMoneyViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as OfflineUpiApp
    private val _state = MutableStateFlow(RequestMoneyState())
    val state: StateFlow<RequestMoneyState> = _state
    val ussdState: StateFlow<UssdState> = UssdManager.state
    private var lastTransactionId: Long = -1
    private var wasWaitingForInput = false

    /**
     * Token of the USSD session this ViewModel started, or -1 when it has none in flight.
     * Every payment screen collects the same global [UssdManager.state], so terminal states are
     * only acted on by the ViewModel that owns the session — otherwise two live ViewModels each
     * wrote their own history row for one request.
     */
    private var sessionToken: Long = -1

    init {
        viewModelScope.launch {
            UssdManager.state.collect { ussd ->
                if (sessionToken < 0 || !UssdManager.ownsSession(sessionToken)) return@collect
                when (ussd) {
                    is UssdState.WaitingForInput -> {
                        wasWaitingForInput = true
                    }
                    is UssdState.Processing -> {
                        if (wasWaitingForInput && lastTransactionId <= 0) {
                            wasWaitingForInput = false
                            insertTransactionFromOverlay()
                        }
                    }
                    is UssdState.Success -> {
                        if (lastTransactionId > 0) {
                            app.transactionRepository.updateTransactionStatus(
                                lastTransactionId, "SUCCESS", ussd.referenceId ?: ""
                            )
                            if (!ussd.verifiedPayeeName.isNullOrBlank()) {
                                app.transactionRepository.updateTransactionName(
                                    lastTransactionId, ussd.verifiedPayeeName
                                )
                            }
                        }
                    }
                    is UssdState.Failed -> {
                        if (lastTransactionId > 0) {
                            app.transactionRepository.updateTransactionFailure(
                                lastTransactionId, ussd.reason
                            )
                        }
                    }
                    else -> {}
                }
            }
        }
    }

    private fun insertTransactionFromOverlay() {
        viewModelScope.launch {
            val current = _state.value
            val amount = UssdManager.lastSubmittedAmount
            val note = UssdManager.lastSubmittedNote

            lastTransactionId = app.transactionRepository.insertTransaction(
                TransactionEntity(
                    type = "REQUEST",
                    status = "PENDING",
                    amount = amount.toDoubleOrNull() ?: 0.0,
                    recipientName = current.recipientId,
                    recipientId = current.recipientId,
                    note = note
                )
            )
        }
    }

    fun updateRecipientId(id: String) {
        _state.value = _state.value.copy(recipientId = id, error = null)
    }

    fun initiateRequest() {
        val current = _state.value
        val isUpiId = current.recipientId.contains("@")
        val isValid = if (isUpiId) {
            Validators.isValidUpiId(current.recipientId)
        } else {
            Validators.isValidPhoneNumber(current.recipientId)
        }
        if (!isValid) {
            _state.value = current.copy(error = "Enter a valid mobile number or UPI ID")
            return
        }
        _state.value = current.copy(error = null)
        viewModelScope.launch {
            UssdManager.startCommand(
                app,
                UssdCommand(
                    type = UssdCommandType.REQUEST_MONEY,
                    recipientId = current.recipientId,
                    amount = "",
                    note = ""
                )
            )
            sessionToken = UssdManager.currentSessionOwner
        }
    }

    fun sendPin(pin: CharArray) {
        UssdManager.sendPinResponse(pin)
    }

    fun reset() {
        _state.value = RequestMoneyState()
        lastTransactionId = -1
        wasWaitingForInput = false
        sessionToken = -1
        UssdManager.reset()
    }
}
