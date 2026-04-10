package com.mw.offlineupi.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "transactions")
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String, // SEND, RECEIVE, REQUEST
    val status: String, // PENDING, SUCCESS, FAILED
    val amount: Double,
    val recipientName: String,
    val recipientId: String, // phone or UPI ID
    val referenceId: String = "",
    val note: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val ussdSessionId: String = "",
    val failureReason: String = ""
)
