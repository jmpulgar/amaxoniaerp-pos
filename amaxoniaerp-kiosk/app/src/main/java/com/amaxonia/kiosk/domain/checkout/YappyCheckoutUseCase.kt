package com.amaxonia.kiosk.domain.checkout

import com.amaxonia.kiosk.core.money.Money
import com.amaxonia.kiosk.core.network.KioskApiClient
import com.amaxonia.kiosk.core.network.KioskApiException
import com.amaxonia.kiosk.core.network.KioskQuoteResponse
import com.amaxonia.kiosk.core.network.KioskYappyChargeResponse
import com.amaxonia.kiosk.domain.payment.PaymentMethod
import com.amaxonia.kiosk.domain.payment.PaymentResult
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

const val YAPPY_POLL_INTERVAL_MS = 3_000L
private const val MILLIS_PER_SECOND = 1_000L
private const val HTTP_CONFLICT = 409
private const val HTTP_SERVICE_UNAVAILABLE = 503
private const val YAPPY_BRAND = "YAPPY"

data class YappyCharge(
    val orderId: String,
    val transactionId: String,
    val qrHash: String,
    val amount: Money,
    val expiresInSec: Int,
)

enum class YappyStatus {
    PENDING,
    COMPLETED,
    FAILED,
    DECLINED,
    EXPIRED,
    CANCELLED,
    ;

    val isTerminal: Boolean
        get() = this != PENDING

    companion object {
        fun fromWire(value: String): YappyStatus = entries.firstOrNull { it.name.equals(value.trim(), ignoreCase = true) } ?: PENDING
    }
}

enum class YappyFailure {
    /** 409: the quote expired or the order is no longer payable — a new quote is needed. */
    ORDER_EXPIRED,

    /** 503: Yappy is not configured for this company. */
    NOT_CONFIGURED,

    /** 502 or inconsistent data from Yappy. */
    UPSTREAM,

    /** No connectivity / unexpected error. */
    NETWORK,
}

sealed interface YappyChargeResult {
    data class Created(val charge: YappyCharge) : YappyChargeResult

    data class Failed(val reason: YappyFailure, val message: String) : YappyChargeResult
}

sealed interface YappyOutcome {
    /** Yappy confirmed the payment; [result] is [CheckoutResult.Success] or [CheckoutResult.PaidPendingSync]. */
    data class Paid(val result: CheckoutResult) : YappyOutcome

    /** The customer (or Yappy) rejected/cancelled the charge. */
    data class Rejected(val status: YappyStatus) : YappyOutcome

    /** The QR expired without a confirmed payment. */
    data object Expired : YappyOutcome
}

/**
 * Yappy checkout: create a charge for the quoted order → poll its status every [pollIntervalMs]
 * until a terminal state or the charge's own expiry → on COMPLETED register the payment through
 * [CheckoutOrderUseCase.registerPayment] (outbox first, then `POST /pay` with method YAPPY).
 */
class YappyCheckoutUseCase(
    private val apiClient: KioskApiClient,
    private val checkoutUseCase: CheckoutOrderUseCase,
    private val pollIntervalMs: Long = YAPPY_POLL_INTERVAL_MS,
) {
    suspend fun createCharge(quote: KioskQuoteResponse): YappyChargeResult =
        apiClient.createYappyCharge(quote.orderId).fold(
            onSuccess = { response -> validateCharge(quote, response) },
            onFailure = { error -> YappyChargeResult.Failed(classify(error), error.message.orEmpty()) },
        )

    /** Suspends until the charge reaches a terminal state or expires. Cancellation stops polling. */
    suspend fun awaitPayment(
        quote: KioskQuoteResponse,
        charge: YappyCharge,
        onConfirmed: () -> Unit = {},
    ): YappyOutcome {
        val polled = withTimeoutOrNull(charge.expiresInSec * MILLIS_PER_SECOND) { pollUntilTerminal(charge) }
        val status = polled ?: fetchStatus(charge) ?: YappyStatus.EXPIRED
        return when {
            status == YappyStatus.COMPLETED -> {
                onConfirmed()
                YappyOutcome.Paid(settle(quote, charge))
            }
            status == YappyStatus.EXPIRED || !status.isTerminal -> {
                apiClient.cancelYappyCharge(charge.orderId, charge.transactionId)
                YappyOutcome.Expired
            }
            else -> YappyOutcome.Rejected(status)
        }
    }

    /**
     * Customer abandoned the QR. If Yappy already confirmed the payment we must not lose it, so the
     * payment is registered and returned; otherwise the charge is cancelled (best effort) and null is returned.
     */
    suspend fun cancelOrSettle(
        quote: KioskQuoteResponse,
        charge: YappyCharge,
    ): CheckoutResult? =
        if (fetchStatus(charge) == YappyStatus.COMPLETED) {
            settle(quote, charge)
        } else {
            apiClient.cancelYappyCharge(charge.orderId, charge.transactionId)
            null
        }

    private suspend fun pollUntilTerminal(charge: YappyCharge): YappyStatus {
        var status = YappyStatus.PENDING
        while (!status.isTerminal) {
            delay(pollIntervalMs)
            status = fetchStatus(charge) ?: YappyStatus.PENDING
        }
        return status
    }

    private suspend fun fetchStatus(charge: YappyCharge): YappyStatus? =
        apiClient
            .getYappyStatus(charge.orderId, charge.transactionId)
            .getOrNull()
            ?.let { YappyStatus.fromWire(it.status) }

    private suspend fun settle(
        quote: KioskQuoteResponse,
        charge: YappyCharge,
    ): CheckoutResult =
        checkoutUseCase.registerPayment(
            quote = quote,
            payment =
                PaymentResult.Success(
                    transactionId = charge.transactionId,
                    authCode = "",
                    reference = charge.transactionId,
                    last4 = "",
                    brand = YAPPY_BRAND,
                    amount = Money.fromString(quote.total),
                ),
            method = PaymentMethod.YAPPY,
        )

    private suspend fun validateCharge(
        quote: KioskQuoteResponse,
        response: KioskYappyChargeResponse,
    ): YappyChargeResult {
        val amount = Money.fromString(response.amount)
        return if (amount == Money.fromString(quote.total) && response.qrHash.isNotBlank()) {
            YappyChargeResult.Created(
                YappyCharge(
                    orderId = quote.orderId,
                    transactionId = response.transactionId,
                    qrHash = response.qrHash,
                    amount = amount,
                    expiresInSec = response.expiresInSec,
                ),
            )
        } else {
            apiClient.cancelYappyCharge(quote.orderId, response.transactionId)
            YappyChargeResult.Failed(YappyFailure.UPSTREAM, "Cobro Yappy inconsistente con la cotización")
        }
    }

    private fun classify(error: Throwable): YappyFailure =
        when ((error as? KioskApiException)?.statusCode) {
            HTTP_CONFLICT -> YappyFailure.ORDER_EXPIRED
            HTTP_SERVICE_UNAVAILABLE -> YappyFailure.NOT_CONFIGURED
            null -> YappyFailure.NETWORK
            else -> YappyFailure.UPSTREAM
        }
}
