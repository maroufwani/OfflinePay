package com.mw.offlineupi.ui.scanpay

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mw.offlineupi.OfflineUpiApp
import com.mw.offlineupi.data.local.entity.TransactionEntity
import com.mw.offlineupi.service.UssdCommand
import com.mw.offlineupi.service.UssdCommandType
import com.mw.offlineupi.service.UssdManager
import com.mw.offlineupi.service.UssdState
import com.mw.offlineupi.util.UpiPaymentInfo
import com.mw.offlineupi.util.UpiQrParser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class ScanPayState(
    val paymentInfo: UpiPaymentInfo? = null,
    val isScanning: Boolean = true,
    val error: String? = null
)

class ScanPayViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as OfflineUpiApp
    private val _state = MutableStateFlow(ScanPayState())
    val state: StateFlow<ScanPayState> = _state
    val ussdState: StateFlow<UssdState> = UssdManager.state
    private var lastTransactionId: Long = -1
    private var wasWaitingForInput = false

    init {
        viewModelScope.launch {
            UssdManager.state.collect { ussd ->
                when (ussd) {
                    is UssdState.WaitingForInput -> {
                        wasWaitingForInput = true
                    }
                    is UssdState.Processing -> {
                        // Amount was submitted via overlay — insert transaction
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
            val info = _state.value.paymentInfo ?: return@launch
            val amount = UssdManager.lastSubmittedAmount
            val note = UssdManager.lastSubmittedNote

            lastTransactionId = app.transactionRepository.insertTransaction(
                TransactionEntity(
                    type = "SEND",
                    status = "PENDING",
                    amount = amount.toDoubleOrNull() ?: 0.0,
                    recipientName = info.payeeName,
                    recipientId = info.payeeAddress,
                    note = note
                )
            )
        }
    }

    fun onQrScanned(rawData: String) {
        val info = UpiQrParser.parse(rawData)
        if (info != null) {
            _state.value = _state.value.copy(
                paymentInfo = info,
                isScanning = false
            )
        } else {
            _state.value = _state.value.copy(error = "Invalid UPI QR code")
        }
    }

    fun onGalleryError(message: String) {
        _state.value = _state.value.copy(error = message)
    }

    fun initiatePayment() {
        val info = _state.value.paymentInfo ?: return

        UssdManager.startCommand(
            app,
            UssdCommand(
                type = UssdCommandType.SEND_MONEY,
                recipientId = info.payeeAddress,
                amount = "",
                note = ""
            )
        )
    }

    fun sendPin(pin: String) {
        UssdManager.sendPinResponse(pin)
    }

    fun rescan() {
        _state.value = ScanPayState()
        lastTransactionId = -1
        wasWaitingForInput = false
        UssdManager.reset()
    }

    fun resetUssd() {
        lastTransactionId = -1
        wasWaitingForInput = false
        UssdManager.reset()
    }
}
