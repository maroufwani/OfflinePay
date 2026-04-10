package com.mw.offlineupi.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.mw.offlineupi.data.local.entity.RecipientEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RecipientDao {
    @Query("SELECT * FROM recipients ORDER BY lastUsedAt DESC")
    fun getAllRecipients(): Flow<List<RecipientEntity>>

    @Query("SELECT * FROM recipients WHERE isFavorite = 1 ORDER BY name ASC")
    fun getFavorites(): Flow<List<RecipientEntity>>

    @Query("SELECT * FROM recipients ORDER BY usageCount DESC LIMIT :limit")
    fun getFrequentRecipients(limit: Int = 5): Flow<List<RecipientEntity>>

    @Query("SELECT * FROM recipients WHERE phoneNumber != '' AND id IN (SELECT id FROM recipients WHERE phoneNumber != '' GROUP BY phoneNumber HAVING id = MAX(id)) ORDER BY lastUsedAt DESC LIMIT :limit")
    fun getRecentPhoneRecipients(limit: Int = 5): Flow<List<RecipientEntity>>

    @Query("SELECT * FROM recipients WHERE upiId != '' AND id IN (SELECT id FROM recipients WHERE upiId != '' GROUP BY upiId HAVING id = MAX(id)) ORDER BY lastUsedAt DESC LIMIT :limit")
    fun getRecentUpiRecipients(limit: Int = 5): Flow<List<RecipientEntity>>

    @Query("SELECT * FROM recipients WHERE name LIKE '%' || :query || '%' OR phoneNumber LIKE '%' || :query || '%' OR upiId LIKE '%' || :query || '%'")
    fun searchRecipients(query: String): Flow<List<RecipientEntity>>

    @Query("SELECT * FROM recipients WHERE id = :id")
    suspend fun getRecipientById(id: Long): RecipientEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecipient(recipient: RecipientEntity): Long

    @Update
    suspend fun updateRecipient(recipient: RecipientEntity)

    @Delete
    suspend fun deleteRecipient(recipient: RecipientEntity)

    @Query("UPDATE recipients SET isFavorite = :isFavorite WHERE id = :id")
    suspend fun setFavorite(id: Long, isFavorite: Boolean)

    @Query("UPDATE recipients SET lastUsedAt = :timestamp, usageCount = usageCount + 1 WHERE id = :id")
    suspend fun recordUsage(id: Long, timestamp: Long = System.currentTimeMillis())
}
