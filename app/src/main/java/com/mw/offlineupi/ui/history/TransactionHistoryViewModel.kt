package com.mw.offlineupi.ui.history

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.mw.offlineupi.OfflineUpiApp
import com.mw.offlineupi.data.local.entity.TransactionEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest

class TransactionHistoryViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as OfflineUpiApp
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    val transactions: Flow<List<TransactionEntity>> = _searchQuery.flatMapLatest { query ->
        if (query.isEmpty()) app.transactionRepository.getAllTransactions()
        else app.transactionRepository.searchTransactions(query)
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun getTransactionById(id: Long): Flow<TransactionEntity?> =
        app.transactionRepository.getTransactionById(id)
}
