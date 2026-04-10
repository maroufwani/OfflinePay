package com.mw.offlineupi.data.repository

import com.mw.offlineupi.data.local.dao.RecipientDao
import com.mw.offlineupi.data.local.entity.RecipientEntity
import kotlinx.coroutines.flow.Flow

class RecipientRepository(private val recipientDao: RecipientDao) {

    fun getAllRecipients(): Flow<List<RecipientEntity>> =
        recipientDao.getAllRecipients()

    fun getFavorites(): Flow<List<RecipientEntity>> =
        recipientDao.getFavorites()

    fun getFrequentRecipients(limit: Int = 5): Flow<List<RecipientEntity>> =
        recipientDao.getFrequentRecipients(limit)

    fun getRecentPhoneRecipients(limit: Int = 5): Flow<List<RecipientEntity>> =
        recipientDao.getRecentPhoneRecipients(limit)

    fun getRecentUpiRecipients(limit: Int = 5): Flow<List<RecipientEntity>> =
        recipientDao.getRecentUpiRecipients(limit)

    fun searchRecipients(query: String): Flow<List<RecipientEntity>> =
        recipientDao.searchRecipients(query)

    suspend fun getRecipientById(id: Long): RecipientEntity? =
        recipientDao.getRecipientById(id)

    suspend fun insertRecipient(recipient: RecipientEntity): Long =
        recipientDao.insertRecipient(recipient)

    suspend fun updateRecipient(recipient: RecipientEntity) =
        recipientDao.updateRecipient(recipient)

    suspend fun deleteRecipient(recipient: RecipientEntity) =
        recipientDao.deleteRecipient(recipient)

    suspend fun toggleFavorite(id: Long, isFavorite: Boolean) =
        recipientDao.setFavorite(id, isFavorite)

    suspend fun recordUsage(id: Long) =
        recipientDao.recordUsage(id)
}
