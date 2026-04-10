package com.mw.offlineupi.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "recipients")
data class RecipientEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val phoneNumber: String = "",
    val upiId: String = "",
    val nickname: String = "",
    val isFavorite: Boolean = false,
    val lastUsedAt: Long = 0,
    val usageCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)
