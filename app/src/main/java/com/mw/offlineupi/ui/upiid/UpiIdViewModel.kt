package com.mw.offlineupi.ui.upiid

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mw.offlineupi.OfflineUpiApp
import com.mw.offlineupi.data.local.entity.RecipientEntity
import com.mw.offlineupi.data.local.entity.TransactionEntity
import com.mw.offlineupi.service.UssdCommand
import com.mw.offlineupi.service.UssdCommandType
import com.mw.offlineupi.service.UssdManager
import com.mw.offlineupi.service.UssdState
import com.mw.offlineupi.util.Validators
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class UpiIdState(
    val upiId: String = "",
    val recipientName: String = "",
    val verifiedName: String? = null,
    val error: String? = null
)

class UpiIdViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as OfflineUpiApp
    private val _state = MutableStateFlow(UpiIdState())
    val state: StateFlow<UpiIdState> = _state
    val ussdState: StateFlow<UssdState> = UssdManager.state
    val recentRecipients: Flow<List<RecipientEntity>> = app.recipientRepository.getRecentUpiRecipients()
    private var lastTransactionId: Long = -1
    private var wasWaitingForInput = false

    /**
     * Token of the USSD session this ViewModel started, or -1 when it has none in flight.
     * Every payment screen collects the same global [UssdManager.state], so terminal states are
     * only acted on by the ViewModel that owns the session — otherwise two live ViewModels each
     * wrote their own history row for one payment.
     */
    private var sessionToken: Long = -1

    init {
        viewModelScope.launch {
            UssdManager.state.collect { ussd ->
                if (sessionToken < 0 || !UssdManager.ownsSession(sessionToken)) return@collect
                when (ussd) {
                    is UssdState.WaitingForInput -> {
                        wasWaitingForInput = true
                        _state.value = _state.value.copy(
                            verifiedName = ussd.verifiedName,
                            recipientName = ussd.verifiedName ?: _state.value.recipientName
                        )
                    }
                    is UssdState.Processing -> {
                        // Amount was submitted via overlay — insert recipient + transaction
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
            val recipientName = current.verifiedName ?: current.recipientName.ifEmpty { current.upiId }
            val amount = UssdManager.lastSubmittedAmount
            val note = UssdManager.lastSubmittedNote

            app.recipientRepository.insertRecipient(
                RecipientEntity(
                    name = recipientName,
                    upiId = current.upiId,
                    lastUsedAt = System.currentTimeMillis()
                )
            )

            lastTransactionId = app.transactionRepository.insertTransaction(
                TransactionEntity(
                    type = "SEND",
                    status = "PENDING",
                    amount = amount.toDoubleOrNull() ?: 0.0,
                    recipientName = recipientName,
                    recipientId = current.upiId,
                    note = note
                )
            )
        }
    }

    fun updateUpiId(id: String) {
        _state.value = _state.value.copy(upiId = id.trim(), error = null)
    }

    fun selectRecipient(recipient: RecipientEntity) {
        _state.value = _state.value.copy(
            upiId = recipient.upiId,
            recipientName = recipient.name
        )
    }

    fun verifyRecipient() {
        val current = _state.value
        if (!Validators.isValidUpiId(current.upiId)) {
            _state.value = current.copy(error = "Enter a valid UPI ID (e.g., name@bank)")
            return
        }
        _state.value = current.copy(error = null)
        viewModelScope.launch {
            UssdManager.startCommand(
                app,
                UssdCommand(
                    type = UssdCommandType.SEND_MONEY,
                    recipientId = current.upiId,
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
        _state.value = UpiIdState()
        lastTransactionId = -1
        wasWaitingForInput = false
        sessionToken = -1
        UssdManager.reset()
    }
}
