package com.amaxonia.pos.domain.usecase.payment

import com.amaxonia.pos.data.local.db.TransactionLogDao
import com.amaxonia.pos.domain.system.AppClock

/**
 * Pins a transaction row into the gateway-callback reconciliation queue.
 *
 * HKA POS sends its result back via an Android Intent that re-launches our
 * MainActivity. If our process is dead when HKA returns (low-memory kill,
 * battery optimiser, user navigated away), the in-memory
 * [com.amaxonia.pos.data.printer.RapidPayBridge] has no suspending
 * coroutine to deliver to — the result is silently dropped. This queue
 * makes the wait durable: as soon as the payment flow fires the HKA Intent
 * it persists an AWAITING row; the reconciler worker keeps the row alive
 * until MainActivity lands the callback and flips it to RESOLVED, or until
 * the LEASE expires and a manual reconciliation prompt is required.
 *
 * Backoff is intentionally shorter than the fiscal ladder because a human
 * is standing at the terminal — after MAX_RETRIES the row goes to
 * TERMINAL_AWAITING so the cashier can ask the customer to retry or void.
 */
class QueueGatewayCallbackUseCase(
    private val dao: TransactionLogDao,
    private val clock: AppClock,
) {
    suspend fun markAwaiting(clientCorrelationId: String) {
        val now = clock.now().toEpochMilli()
        dao.markGatewayAwaiting(
            id = clientCorrelationId,
            status = STATUS_AWAITING,
            nextAttemptAt = now,
        )
    }

    suspend fun markResolved(
        clientCorrelationId: String,
        responseCode: String,
        rawResponse: String? = null,
        message: String? = null,
    ) {
        dao.markGatewayResolved(
            id = clientCorrelationId,
            status = STATUS_RESOLVED,
            resultCode = responseCode,
            rawResponse = rawResponse,
            message = message,
        )
    }

    companion object {
        const val STATUS_IGNORED = "IGNORED"
        const val STATUS_AWAITING = "AWAITING"
        const val STATUS_RESOLVED = "RESOLVED"
        const val STATUS_RETRYABLE_AWAITING = "RETRYABLE_AWAITING"
        const val STATUS_TERMINAL_AWAITING = "TERMINAL_AWAITING"

        /**
         * Short backoff ladder (milliseconds). A cashier is waiting: 30s
         * gives HKA time to land a slow card-flow callback; 2m is the last
         * rung before escalation to manual dispute.
         */
        val BACKOFF_LADDER_MS: LongArray = longArrayOf(30_000L, 60_000L, 120_000L, 300_000L)
        const val MAX_INTERVAL_MS: Long = 600_000L
        const val MAX_RETRIES: Int = 4
        const val LEASE_DURATION_MS: Long = 90_000L
        const val OVERSHOOT_BITSHIFT_CAP: Int = 4

        fun nextAttempt(retryCount: Int): Long {
            val index = retryCount.coerceAtLeast(0)
            return if (index < BACKOFF_LADDER_MS.size) {
                BACKOFF_LADDER_MS[index]
            } else {
                val overshoot = index - BACKOFF_LADDER_MS.size
                val grown = BACKOFF_LADDER_MS.last() * (1L shl overshoot.coerceAtMost(OVERSHOOT_BITSHIFT_CAP))
                grown.coerceAtMost(MAX_INTERVAL_MS)
            }
        }
    }
}
