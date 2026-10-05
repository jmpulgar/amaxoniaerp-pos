package com.amaxonia.kiosk.domain.checkout

import com.amaxonia.kiosk.core.money.Money
import com.amaxonia.kiosk.core.network.KioskApiClient
import com.amaxonia.kiosk.core.network.KioskPaymentRequest
import com.amaxonia.kiosk.core.network.KioskPaymentResponse
import com.amaxonia.kiosk.core.network.KioskQuoteLineRequest
import com.amaxonia.kiosk.core.network.KioskQuoteRequest
import com.amaxonia.kiosk.core.network.KioskQuoteResponse
import com.amaxonia.kiosk.data.db.PendingPayment
import com.amaxonia.kiosk.data.db.PendingPaymentDao
import com.amaxonia.kiosk.domain.cart.OrderGraph
import com.amaxonia.kiosk.domain.payment.PaymentResult
import com.amaxonia.kiosk.domain.payment.PaymentTerminal
import java.util.UUID

sealed interface CheckoutResult {
    data class Success(
        val quote: KioskQuoteResponse,
        val payment: PaymentResult.Success,
        val paymentResponse: KioskPaymentResponse,
    ) : CheckoutResult

    data class PaidPendingSync(
        val quote: KioskQuoteResponse,
        val payment: PaymentResult.Success,
        val error: String,
    ) : CheckoutResult

    data class PaymentDeclined(
        val reason: String,
    ) : CheckoutResult

    data object PaymentCancelled : CheckoutResult

    data class PaymentTerminalError(
        val message: String,
    ) : CheckoutResult

    data class QuoteFailed(
        val message: String,
    ) : CheckoutResult
}

data class SyncPendingResult(
    val orderId: String,
    val success: Boolean,
    val error: String? = null,
)

class CheckoutOrderUseCase(
    private val apiClient: KioskApiClient,
    private val paymentTerminal: PaymentTerminal,
    private val pendingPaymentDao: PendingPaymentDao,
) {
    suspend fun execute(
        orderGraph: OrderGraph,
        idempotencyKey: String = UUID.randomUUID().toString(),
    ): CheckoutResult {
        val quoteResult = apiClient.quoteOrder(idempotencyKey, buildQuoteRequest(orderGraph))
        val quote =
            quoteResult.getOrElse {
                return CheckoutResult.QuoteFailed(it.message ?: "Error al cotizar orden en el servidor")
            }

        val totalMoney = Money.fromString(quote.total)
        return when (val payment = paymentTerminal.processPayment(totalMoney, quote.orderId)) {
            is PaymentResult.Success -> handlePaymentSuccess(quote, payment)
            is PaymentResult.Declined -> CheckoutResult.PaymentDeclined(payment.reason)
            is PaymentResult.Cancelled -> CheckoutResult.PaymentCancelled
            is PaymentResult.Error -> CheckoutResult.PaymentTerminalError(payment.message)
        }
    }

    private suspend fun handlePaymentSuccess(
        quote: KioskQuoteResponse,
        payment: PaymentResult.Success,
    ): CheckoutResult {
        val pendingPayment = createPendingPayment(quote, payment)
        pendingPaymentDao.insert(pendingPayment)

        val payRequest =
            KioskPaymentRequest(
                transactionId = payment.transactionId,
                authCode = payment.authCode,
                reference = payment.reference,
                last4 = payment.last4,
                brand = payment.brand,
                amount = quote.total,
            )

        return apiClient.payOrder(quote.orderId, payRequest).fold(
            onSuccess = { response ->
                pendingPaymentDao.update(pendingPayment.copy(status = "SYNCED"))
                CheckoutResult.Success(quote = quote, payment = payment, paymentResponse = response)
            },
            onFailure = { error ->
                val errorMsg = error.message ?: "Fallo de conexión al registrar pago fiscal"
                pendingPaymentDao.update(
                    pendingPayment.copy(
                        attempts = pendingPayment.attempts + 1,
                        lastError = errorMsg,
                    ),
                )
                CheckoutResult.PaidPendingSync(quote = quote, payment = payment, error = errorMsg)
            },
        )
    }

    private fun createPendingPayment(
        quote: KioskQuoteResponse,
        payment: PaymentResult.Success,
    ) = PendingPayment(
        orderId = quote.orderId,
        transactionId = payment.transactionId,
        authCode = payment.authCode,
        reference = payment.reference,
        last4 = payment.last4,
        brand = payment.brand,
        amount = quote.total,
        status = "PENDING",
    )

    private fun buildQuoteRequest(orderGraph: OrderGraph) =
        KioskQuoteRequest(
            diningMode = orderGraph.diningMode.value,
            tableTent = orderGraph.tableTent.value,
            customerId = orderGraph.customerId.value,
            lines =
                orderGraph.lines.value.map { line ->
                    KioskQuoteLineRequest(
                        itemId = line.item.id,
                        qty = line.quantity,
                        note = line.note,
                        modifiers = line.selectedModifiers.map { it.optionId },
                    )
                },
        )

    suspend fun syncPendingPayments(): List<SyncPendingResult> {
        val pendingList = pendingPaymentDao.getByStatus("PENDING")
        val results = mutableListOf<SyncPendingResult>()

        for (pending in pendingList) {
            val payRequest =
                KioskPaymentRequest(
                    transactionId = pending.transactionId,
                    authCode = pending.authCode,
                    reference = pending.reference,
                    last4 = pending.last4,
                    brand = pending.brand,
                    amount = pending.amount,
                )

            val result = apiClient.payOrder(pending.orderId, payRequest)
            result.fold(
                onSuccess = {
                    pendingPaymentDao.update(pending.copy(status = "SYNCED", lastError = null))
                    results.add(SyncPendingResult(orderId = pending.orderId, success = true))
                },
                onFailure = { error ->
                    val errorMsg = error.message ?: "Error al reintentar pago pendiente"
                    pendingPaymentDao.update(
                        pending.copy(
                            attempts = pending.attempts + 1,
                            lastError = errorMsg,
                        ),
                    )
                    results.add(SyncPendingResult(orderId = pending.orderId, success = false, error = errorMsg))
                },
            )
        }
        return results
    }
}
