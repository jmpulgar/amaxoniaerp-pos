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
import com.amaxonia.kiosk.domain.payment.PaymentMethod
import com.amaxonia.kiosk.domain.payment.PaymentResult
import com.amaxonia.kiosk.domain.payment.PaymentTerminal
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

sealed interface CheckoutResult {
    data class Success(
        val quote: KioskQuoteResponse,
        val payment: PaymentResult.Success,
        val paymentResponse: KioskPaymentResponse,
    ) : CheckoutResult

    /** The customer was charged but the backend could not register it yet; the outbox retries later. */
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
}

data class SyncPendingResult(
    val orderId: String,
    val success: Boolean,
    val error: String? = null,
)

/**
 * Checkout is split in two steps so the server quote happens exactly once per order:
 * 1. [quote] — the server prices the cart and returns `quote.total`.
 * 2. A payment method charges exactly that total ([payWithCard] or the Yappy flow) and the
 *    approved payment is registered with [registerPayment] (outbox first, then `POST /pay`).
 */
class CheckoutOrderUseCase(
    private val apiClient: KioskApiClient,
    private val paymentTerminal: PaymentTerminal,
    private val pendingPaymentDao: PendingPaymentDao,
) {
    private val outboxMutex = Mutex()

    suspend fun quote(
        request: KioskQuoteRequest,
        idempotencyKey: String,
    ): Result<KioskQuoteResponse> = apiClient.quoteOrder(idempotencyKey, request)

    /** Charges exactly `quote.total` on the card terminal. [onApproved] fires before the backend registration. */
    suspend fun payWithCard(
        quote: KioskQuoteResponse,
        onApproved: () -> Unit = {},
    ): CheckoutResult =
        when (val payment = paymentTerminal.processPayment(Money.fromString(quote.total), quote.orderId)) {
            is PaymentResult.Success -> {
                onApproved()
                registerPayment(quote, payment, PaymentMethod.CARD)
            }
            is PaymentResult.Declined -> CheckoutResult.PaymentDeclined(payment.reason)
            is PaymentResult.Cancelled -> CheckoutResult.PaymentCancelled
            is PaymentResult.Error -> CheckoutResult.PaymentTerminalError(payment.message)
        }

    /**
     * Persists the approved payment to the outbox and registers it with the backend. Runs
     * non-cancellable: once money moved, leaving the screen must never lose the record.
     */
    suspend fun registerPayment(
        quote: KioskQuoteResponse,
        payment: PaymentResult.Success,
        method: PaymentMethod,
    ): CheckoutResult =
        withContext(NonCancellable) {
            outboxMutex.withLock {
                val pendingPayment = createPendingPayment(quote, payment, method)
                pendingPaymentDao.insert(pendingPayment)

                apiClient.payOrder(quote.orderId, pendingPayment.toRequest()).fold(
                    onSuccess = { response ->
                        pendingPaymentDao.update(pendingPayment.copy(status = PendingPayment.STATUS_SYNCED))
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
        }

    /** Retries every paid-but-unregistered order in the outbox, sending the method it was paid with. */
    suspend fun syncPendingPayments(): List<SyncPendingResult> =
        outboxMutex.withLock {
            pendingPaymentDao.getByStatus(PendingPayment.STATUS_PENDING).map { pending ->
                apiClient.payOrder(pending.orderId, pending.toRequest()).fold(
                    onSuccess = {
                        pendingPaymentDao.update(pending.copy(status = PendingPayment.STATUS_SYNCED, lastError = null))
                        SyncPendingResult(orderId = pending.orderId, success = true)
                    },
                    onFailure = { error ->
                        val errorMsg = error.message ?: "Error al reintentar pago pendiente"
                        pendingPaymentDao.update(
                            pending.copy(
                                attempts = pending.attempts + 1,
                                lastError = errorMsg,
                            ),
                        )
                        SyncPendingResult(orderId = pending.orderId, success = false, error = errorMsg)
                    },
                )
            }
        }

    private fun createPendingPayment(
        quote: KioskQuoteResponse,
        payment: PaymentResult.Success,
        method: PaymentMethod,
    ) = PendingPayment(
        orderId = quote.orderId,
        transactionId = payment.transactionId,
        authCode = payment.authCode,
        reference = payment.reference,
        last4 = payment.last4,
        brand = payment.brand,
        amount = quote.total,
        status = PendingPayment.STATUS_PENDING,
        method = method.wireName,
    )

    private fun PendingPayment.toRequest() =
        KioskPaymentRequest(
            transactionId = transactionId,
            authCode = authCode,
            reference = reference,
            last4 = last4,
            brand = brand,
            amount = amount,
            method = method,
        )

    companion object {
        fun buildQuoteRequest(orderGraph: OrderGraph) =
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
    }
}
