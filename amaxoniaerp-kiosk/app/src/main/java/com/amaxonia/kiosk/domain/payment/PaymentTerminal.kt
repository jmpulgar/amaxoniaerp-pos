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
    /** False when no card terminal is installed/configured; CARD is then not offered to the customer. */
    val isAvailable: Boolean

    suspend fun processPayment(
        amount: Money,
        orderId: String,
    ): PaymentResult

    suspend fun cancelPayment(): Boolean
}

/** Used when the build has no card terminal adapter: reports itself unavailable and never charges. */
class UnavailablePaymentTerminal : PaymentTerminal {
    override val isAvailable: Boolean = false

    override suspend fun processPayment(
        amount: Money,
        orderId: String,
    ): PaymentResult = PaymentResult.Error(message = "Terminal de pago no disponible")

    override suspend fun cancelPayment(): Boolean = false
}
