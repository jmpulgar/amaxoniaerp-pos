package com.amaxonia.kiosk.domain.payment

import com.amaxonia.kiosk.core.money.Money

sealed interface PaymentResult {
    data class Success(
        val transactionId: String,
        val authCode: String,
        val reference: String,
        val last4: String,
        val brand: String,
        val amount: Money,
    ) : PaymentResult

    data class Declined(
        val reason: String,
    ) : PaymentResult

    data class Error(
        val message: String,
        val cause: Throwable? = null,
    ) : PaymentResult

    data object Cancelled : PaymentResult
}

interface PaymentTerminal {
    suspend fun processPayment(
        amount: Money,
        orderId: String,
    ): PaymentResult

    suspend fun cancelPayment(): Boolean
}
