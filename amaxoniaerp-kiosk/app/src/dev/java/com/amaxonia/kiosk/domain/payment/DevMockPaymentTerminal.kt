package com.amaxonia.kiosk.domain.payment

import com.amaxonia.kiosk.core.money.Money
import kotlinx.coroutines.delay
import java.util.UUID

private const val SUBSTRING_LENGTH = 8
private const val AUTH_MIN = 100000
private const val AUTH_MAX = 999999
private const val REF_MODULO = 100000000

/** Simulated card terminal. Lives only in the `dev` flavor; production never ships a mock. */
class DevMockPaymentTerminal(
    var shouldSucceed: Boolean = true,
    var failureReason: String = "FONDOS INSUFICIENTES",
    var simulatedDelayMs: Long = 1000L,
) : PaymentTerminal {
    @Volatile
    private var isCancelled = false

    override val isAvailable: Boolean = true

    override suspend fun processPayment(
        amount: Money,
        orderId: String,
    ): PaymentResult {
        isCancelled = false
        if (simulatedDelayMs > 0) {
            delay(simulatedDelayMs)
        }
        if (isCancelled) {
            return PaymentResult.Cancelled
        }
        return if (shouldSucceed) {
            val randomSuffix = UUID.randomUUID().toString().take(SUBSTRING_LENGTH).uppercase()
            val auth = (AUTH_MIN..AUTH_MAX).random()
            val ref = System.currentTimeMillis() % REF_MODULO
            PaymentResult.Success(
                transactionId = "TXN-$randomSuffix",
                authCode = "AUT$auth",
                reference = "REF$ref",
                last4 = "4242",
                brand = "VISA",
                amount = amount,
            )
        } else {
            PaymentResult.Declined(reason = failureReason)
        }
    }

    override suspend fun cancelPayment(): Boolean {
        isCancelled = true
        return true
    }
}
