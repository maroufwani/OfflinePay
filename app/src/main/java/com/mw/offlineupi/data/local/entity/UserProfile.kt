package com.mw.offlineupi.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_profile")
data class UserProfile(
    @PrimaryKey val id: Int = 1,
    val phoneNumber: String,
    val name: String = "",
    val bankName: String = "",
    val maskedAccountNumber: String = "",
    val ifscCode: String = "",
    val upiId: String = "",
    val isOnboarded: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)
