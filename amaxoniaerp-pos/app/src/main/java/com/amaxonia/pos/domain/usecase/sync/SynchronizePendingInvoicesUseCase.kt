package com.amaxonia.pos.domain.usecase.sync

import com.amaxonia.pos.domain.model.sales.ProcessSaleRequestDto
import com.amaxonia.pos.domain.system.AppClock
import java.time.Duration

data class PendingInvoiceRecord(
    val id: String,
    val localInvoiceNumber: String,
    val payloadJson: String,
)

data class SynchronizedInvoice(
    val remoteId: String,
    val remoteNumber: String,
)

interface PendingInvoiceQueue {
    suspend fun recoverInterrupted(
        staleBeforeEpochMillis: Long,
        nowEpochMillis: Long,
    )

    /**
     * Rows still awaiting synchronisation. When [tenantId] is non-null, only
     * rows belonging to that tenant are returned; a null [tenantId] (meaning
     * "no active company session") returns an empty list so a worker can never
     * accidentally send a row of tenant A using credentials of tenant B.
     */
    suspend fun pending(tenantId: String?): List<PendingInvoiceRecord>

    /**
     * Atomic claim before processing (ítem 4 / CON-001). Returns the number
     * of rows affected: 1 means this caller now owns the lease; 0 means
     * another worker instance holds it and the row must be skipped.
     */
    suspend fun tryClaim(
        id: String,
        now: Long,
        leasedUntil: Long,
    ): Int

    suspend fun markSending(
        id: String,
        nowEpochMillis: Long,
    )

    suspend fun markSent(
        id: String,
        result: SynchronizedInvoice,
        nowEpochMillis: Long,
    )

    suspend fun markRecoverableFailure(
        id: String,
        message: String,
        nowEpochMillis: Long,
    )

    suspend fun markPermanentFailure(
        id: String,
        message: String,
        nowEpochMillis: Long,
    )
}

fun interface PendingSaleDecoder {
    fun decode(payloadJson: String): Result<ProcessSaleRequestDto>
}

interface PendingSaleGateway {
    suspend fun submit(request: ProcessSaleRequestDto): Result<SynchronizedInvoice>
}

sealed interface PendingInvoiceSyncResult {
    data object Success : PendingInvoiceSyncResult

    data object Retry : PendingInvoiceSyncResult
}

class SynchronizePendingInvoicesUseCase(
    private val queue: PendingInvoiceQueue,
    private val decoder: PendingSaleDecoder,
    private val gateway: PendingSaleGateway,
    private val clock: AppClock,
    private val interruptedLease: Duration = Duration.ofMinutes(15),
    private val claimLease: Duration = Duration.ofMinutes(2),
) {
    /**
     * @param tenantId the canonical tenant id of the currently active session.
     * Rows whose `tenantId` column differs are skipped. When null (no active
     * session) the use case is a no-op so workers cannot drift rows across
     * tenants when the user has not yet re-logged into a company.
     */
    suspend operator fun invoke(tenantId: String?): PendingInvoiceSyncResult {
        if (tenantId == null) return PendingInvoiceSyncResult.Success
        val now = clock.now().toEpochMilli()
        queue.recoverInterrupted(now - interruptedLease.toMillis(), now)

        var requiresRetry = false
        queue.pending(tenantId).forEach { invoice ->
            // Item 4 / CON-001. Atomic claim: if another worker instance already
            // holds the lease on this row, affectedRows == 0 and we skip so the
            // same invoice is never submitted twice by concurrent dispatch.
            val claimDeadline = now + claimLease.toMillis()
            val claimed = queue.tryClaim(invoice.id, now = now, leasedUntil = claimDeadline)
            if (claimed == 0) return@forEach
            queue.markSending(invoice.id, clock.now().toEpochMilli())
            val decoded =
                decoder.decode(invoice.payloadJson).getOrElse { error ->
                    queue.markPermanentFailure(
                        invoice.id,
                        error.message ?: "Payload local inválido",
                        clock.now().toEpochMilli(),
                    )
                    return@forEach
                }
            val idempotentRequest =
                decoded.copy(
                    idFactura = decoded.idFactura ?: invoice.id,
                    codFactura = decoded.codFactura ?: invoice.localInvoiceNumber,
                )
            gateway.submit(idempotentRequest).fold(
                onSuccess = { result ->
                    queue.markSent(invoice.id, result, clock.now().toEpochMilli())
                },
                onFailure = { error ->
                    requiresRetry = true
                    queue.markRecoverableFailure(
                        invoice.id,
                        error.message ?: "No se pudo reenviar la factura",
                        clock.now().toEpochMilli(),
                    )
                },
            )
        }

        return if (requiresRetry) PendingInvoiceSyncResult.Retry else PendingInvoiceSyncResult.Success
    }
}
