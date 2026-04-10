package com.mw.offlineupi.data.repository

import com.mw.offlineupi.data.local.dao.TransactionDao
import com.mw.offlineupi.data.local.entity.TransactionEntity
import kotlinx.coroutines.flow.Flow

class TransactionRepository(private val transactionDao: TransactionDao) {

    fun getAllTransactions(): Flow<List<TransactionEntity>> =
        transactionDao.getAllTransactions()

    fun getRecentTransactions(limit: Int = 10): Flow<List<TransactionEntity>> =
        transactionDao.getRecentTransactions(limit)

    fun searchTransactions(query: String): Flow<List<TransactionEntity>> =
        transactionDao.searchTransactions(query)

    fun getTransactionsByDateRange(startTime: Long, endTime: Long): Flow<List<TransactionEntity>> =
        transactionDao.getTransactionsByDateRange(startTime, endTime)

    fun getTransactionsByStatus(status: String): Flow<List<TransactionEntity>> =
        transactionDao.getTransactionsByStatus(status)

    fun getTransactionById(id: Long): Flow<TransactionEntity?> =
        transactionDao.getTransactionById(id)

    suspend fun insertTransaction(transaction: TransactionEntity): Long =
        transactionDao.insertTransaction(transaction)

    suspend fun updateTransaction(transaction: TransactionEntity) =
        transactionDao.updateTransaction(transaction)

    suspend fun updateTransactionStatus(id: Long, status: String, referenceId: String = "") =
        transactionDao.updateTransactionStatus(id, status, referenceId)

    suspend fun updateTransactionFailure(id: Long, reason: String) =
        transactionDao.updateTransactionFailure(id, reason = reason)

    suspend fun updateTransactionName(id: Long, name: String) =
        transactionDao.updateTransactionName(id, name)
}
