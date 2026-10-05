package com.amaxonia.kiosk.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pending_payments")
data class PendingPayment(
    @PrimaryKey
    val orderId: String,
    val transactionId: String,
    val authCode: String,
    val reference: String,
    val last4: String,
    val brand: String,
    val amount: String,
    val status: String,
    val createdAt: Long = System.currentTimeMillis(),
    val attempts: Int = 0,
    val lastError: String? = null,
)
