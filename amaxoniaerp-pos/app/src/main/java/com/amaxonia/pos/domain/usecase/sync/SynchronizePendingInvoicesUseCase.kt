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

    suspend fun pending(): List<PendingInvoiceRecord>

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
) {
    suspend operator fun invoke(): PendingInvoiceSyncResult {
        val now = clock.now().toEpochMilli()
        queue.recoverInterrupted(now - interruptedLease.toMillis(), now)

        var requiresRetry = false
        queue.pending().forEach { invoice ->
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
