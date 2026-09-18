package com.mw.offlineupi.ui.paymobile

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ContactResult(
    val name: String,
    val phoneNumber: String
)

data class PayMobileState(
    val phoneNumber: String = "",
    val recipientName: String = "",
    val error: String? = null,
    val contactQuery: String = "",
    val contactResults: List<ContactResult> = emptyList(),
    val hasContactsPermission: Boolean = false
)

class PayMobileViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as OfflineUpiApp
    private val _state = MutableStateFlow(PayMobileState(
        hasContactsPermission = hasContactsPermission()
    ))
    val state: StateFlow<PayMobileState> = _state
    val ussdState: StateFlow<UssdState> = UssdManager.state
    val recentRecipients: Flow<List<RecipientEntity>> = app.recipientRepository.getRecentPhoneRecipients()
    private var lastTransactionId: Long = -1
    private var wasWaitingForInput = false

    /**
     * Token of the USSD session this ViewModel started, or -1 when it has none in flight.
     *
     * Every payment screen collects the same global [UssdManager.state], so without this two live
     * ViewModels (e.g. Pay-by-mobile left on the back stack while Scan-and-pay runs) both saw the
     * same Success and each wrote its own history row. Terminal states are now only acted on by
     * the ViewModel that owns the session.
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
                            recipientName = ussd.verifiedName ?: _state.value.recipientName
                        )
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
            val recipientName = current.recipientName.ifEmpty { current.phoneNumber }
            val amount = UssdManager.lastSubmittedAmount
            val note = UssdManager.lastSubmittedNote

            app.recipientRepository.insertRecipient(
                RecipientEntity(
                    name = recipientName,
                    phoneNumber = current.phoneNumber,
                    lastUsedAt = System.currentTimeMillis()
                )
            )

            lastTransactionId = app.transactionRepository.insertTransaction(
                TransactionEntity(
                    type = "SEND",
                    status = "PENDING",
                    amount = amount.toDoubleOrNull() ?: 0.0,
                    recipientName = recipientName,
                    recipientId = current.phoneNumber,
                    note = note
                )
            )
        }
    }

    fun hasContactsPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            app, Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun onContactsPermissionResult(granted: Boolean) {
        _state.value = _state.value.copy(hasContactsPermission = granted)
        if (granted && _state.value.contactQuery.isNotEmpty()) {
            searchContacts(_state.value.contactQuery)
        }
    }

    fun updatePhoneNumber(phone: String) {
        if (phone.length <= 10 && phone.all { it.isDigit() }) {
            _state.value = _state.value.copy(
                phoneNumber = phone,
                contactQuery = phone,
                error = null
            )
            if (hasContactsPermission()) {
                searchContacts(phone)
            }
        }
    }

    fun updateContactQuery(query: String) {
        _state.value = _state.value.copy(contactQuery = query, error = null)
        // If query is purely digits, also update phone number
        val digitsOnly = query.filter { it.isDigit() }
        if (query.all { it.isDigit() || it.isWhitespace() } && digitsOnly.length <= 10) {
            _state.value = _state.value.copy(phoneNumber = digitsOnly)
        }
        if (hasContactsPermission() && query.length >= 2) {
            searchContacts(query)
        } else if (query.length < 2) {
            _state.value = _state.value.copy(contactResults = emptyList())
        }
    }

    /**
     * Debounce before hitting the contacts provider. Every keystroke previously fired a fresh
     * `LIKE '%…%'` query, and `viewModelScope` runs on `Dispatchers.Main.immediate`, so the
     * cursor walk happened on the main thread — a 2000-contact address book dropped frames on
     * each digit typed. 250 ms is below the threshold where the list feels laggy while still
     * collapsing a fast typist's burst into one query.
     */
    private val contactSearchDebounceMs = 250L
    private var contactSearchJob: Job? = null

    private fun searchContacts(query: String) {
        // Cancel the in-flight search: its results are already stale, and letting it finish would
        // also let it overwrite the newer query's results.
        contactSearchJob?.cancel()
        contactSearchJob = viewModelScope.launch {
            delay(contactSearchDebounceMs)
            val results = queryContacts(query)
            if (results == null) {
                _state.value = _state.value.copy(
                    hasContactsPermission = false,
                    contactResults = emptyList()
                )
            } else {
                _state.value = _state.value.copy(contactResults = results)
            }
        }
    }

    /** Returns matching contacts, or `null` if the read permission was revoked. */
    private suspend fun queryContacts(query: String): List<ContactResult>? =
        withContext(Dispatchers.IO) {
            val results = mutableListOf<ContactResult>()
            val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
            val projection = arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            )
            val selection = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ? OR " +
                "${ContactsContract.CommonDataKinds.Phone.NUMBER} LIKE ?"
            // Escaped so a literal % or _ typed by the user is not read as a LIKE wildcard.
            val pattern = "%" + query.replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_") + "%"
            val selectionArgs = arrayOf(pattern, pattern)
            val sortOrder = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC"

            try {
                app.contentResolver.query(uri, projection, selection, selectionArgs, sortOrder)
                    ?.use { cursor ->
                        val nameIdx = cursor.getColumnIndex(
                            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME
                        )
                        val numberIdx = cursor.getColumnIndex(
                            ContactsContract.CommonDataKinds.Phone.NUMBER
                        )
                        val seen = mutableSetOf<String>()
                        while (cursor.moveToNext() && results.size < 10) {
                            val name = cursor.getString(nameIdx) ?: continue
                            val rawNumber = cursor.getString(numberIdx) ?: continue
                            val number = rawNumber.filter { it.isDigit() }.takeLast(10)
                            if (number.length == 10 && seen.add(number)) {
                                results.add(ContactResult(name = name, phoneNumber = number))
                            }
                        }
                    }
            } catch (_: SecurityException) {
                return@withContext null
            } catch (e: Exception) {
                // A provider-side failure is not worth surfacing — the user can still type the
                // number by hand.
                return@withContext emptyList()
            }
            results
        }

    fun selectContact(contact: ContactResult) {
        _state.value = _state.value.copy(
            phoneNumber = contact.phoneNumber,
            recipientName = contact.name,
            contactQuery = contact.name,
            contactResults = emptyList()
        )
    }

    fun selectRecipient(recipient: RecipientEntity) {
        _state.value = _state.value.copy(
            phoneNumber = recipient.phoneNumber,
            recipientName = recipient.name,
            contactQuery = recipient.name.ifEmpty { recipient.phoneNumber }
        )
    }

    fun verifyRecipient() {
        val current = _state.value
        if (!Validators.isValidPhoneNumber(current.phoneNumber)) {
            _state.value = current.copy(error = "Enter a valid 10-digit mobile number")
            return
        }
        _state.value = current.copy(error = null)
        viewModelScope.launch {
            UssdManager.startCommand(
                app,
                UssdCommand(
                    type = UssdCommandType.SEND_MONEY,
                    recipientId = current.phoneNumber,
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
        _state.value = PayMobileState(hasContactsPermission = hasContactsPermission())
        lastTransactionId = -1
        wasWaitingForInput = false
        sessionToken = -1
        UssdManager.reset()
    }
}
