package com.amaxonia.pos.domain.usecase.payment

import com.amaxonia.pos.data.local.db.TransactionLogDao
import com.amaxonia.pos.domain.system.AppClock

/**
 * Enqueues a fiscal-confirmation retry for a sale whose invoice was created
 * (POST /ventas/procesar returned 2xx) but whose PATCH
 * /facturas/{id}/confirmacion-fiscal failed (printer offline, network blip,
 * backend 5xx). The sale is already PAID, so we must not lose the
 * (remoteInvoiceId, fiscalNumber, printerSerial) tuple — the worker replays
 * it until the backend acknowledges the fiscal document.
 *
 * The retry uses bounded exponential backoff:
 * - attempts 1-5:  FIXED_LADDER (15s, 30s, 1m, 5m, 15m).
 * - attempts 6+:   exponential, capped at MAX_INTERVAL.
 * After MAX_RETRIES the row is marked TERMINAL_FAILED for manual review.
 */
class QueueFiscalConfirmationUseCase(
    private val dao: TransactionLogDao,
    private val clock: AppClock,
) {
    suspend fun enqueue(
        clientCorrelationId: String,
        remoteInvoiceId: String,
        fiscalNumber: String,
        printerSerial: String,
        failureMessage: String,
    ) {
        val now = clock.now().toEpochMilli()
        dao.markFiscalRetriable(
            id = clientCorrelationId,
            status = STATUS_RETRYABLE_PENDING,
            remoteInvoiceId = remoteInvoiceId,
            fiscalNumber = fiscalNumber,
            printerSerial = printerSerial,
            nextAttemptAt = now,
            message = failureMessage,
        )
    }

    companion object {
        const val STATUS_PENDING = "PENDING"
        const val STATUS_RETRYABLE_PENDING = "RETRYABLE_PENDING"
        const val STATUS_CONFIRMED = "CONFIRMED"
        const val STATUS_TERMINAL_FAILED = "TERMINAL_FAILED"
        const val STATUS_IN_FLIGHT = "IN_FLIGHT"

        /**
         * Bounded backoff ladder (milliseconds). After the ladder is
         * exhausted, attempts continue with exponential growth capped at
         * [MAX_INTERVAL].
         */
        val BACKOFF_LADDER_MS: LongArray = longArrayOf(15_000L, 30_000L, 60_000L, 300_000L, 900_000L)
        const val MAX_INTERVAL_MS: Long = 3_600_000L
        const val MAX_RETRIES: Int = 10
        const val LEASE_DURATION_MS: Long = 60_000L
        const val OVERSHOOT_BITSHIFT_CAP: Int = 10

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
