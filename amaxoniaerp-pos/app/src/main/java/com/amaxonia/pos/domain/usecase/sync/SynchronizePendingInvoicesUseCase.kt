package com.amaxonia.pos.domain.usecase.sync

import com.amaxonia.pos.domain.model.sales.ProcessSaleRequestDto
import com.amaxonia.pos.domain.system.AppClock
import java.time.Duration

data class PendingInvoiceRecord(
    val id: String,
    val localInvoiceNumber: String,
    val payloadJson: String,
    val retryCount: Int = 0,
    val countryCode: String = "PA",
)

data class SynchronizedInvoice(
    val remoteId: String,
    val remoteNumber: String,
)

/**
 * Rechazo por regla de dominio del ERP (stock insuficiente, crédito revocado,
 * lote agotado, almacén inválido). Estado TERMINAL: la factura jamás se
 * reenvía automáticamente — requiere acción del cajero/supervisor (PLAN §11.2).
 */
class InvoiceDomainRejectedException(message: String) : RuntimeException(message)

/**
 * Reconcilia una factura que el servidor ya conoce (HTTP 409) consultándola
 * por su clave de idempotencia: convierte el 409 en un envío resuelto.
 */
fun interface InvoiceReconciler {
    suspend fun fetchByCorrelationId(correlationId: String): Result<SynchronizedInvoice>
}

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
     * Filas con retryCount >= tope quedan excluidas (SUSPENDED) y requieren
     * intervención manual.
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

    /** Rechazo terminal de dominio: visible con motivo, jamás se reenvía. */
    suspend fun markRejected(
        id: String,
        message: String,
        nowEpochMillis: Long,
    )

    /** Tope de reintentos alcanzado: requiere intervención humana. */
    suspend fun markSuspended(
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

data class SyncRetryConfig(
    val interruptedLease: Duration = Duration.ofMinutes(15),
    val claimLease: Duration = Duration.ofMinutes(2),
    val reconciler: InvoiceReconciler? = null,
    val maxSubmitAttempts: Int = 20,
)


class SynchronizePendingInvoicesUseCase(
    private val queue: PendingInvoiceQueue,
    private val decoder: PendingSaleDecoder,
    private val gateway: PendingSaleGateway,
    private val clock: AppClock,
    private val config: SyncRetryConfig = SyncRetryConfig(),
) {
    /**
     * @param tenantId the canonical tenant id of the currently active session.
     * Rows whose `tenantId` column differs are skipped. When null (no active
     * session) the use case is a no-op so workers cannot drift rows across
     * tenants when the user has not yet re-logged into a company.
     */
    suspend operator fun invoke(tenantId: String?): PendingInvoiceSyncResult {
        var requiresRetry = false
        var stopProcessing = false
        if (tenantId == null) return PendingInvoiceSyncResult.Success
        val now = clock.now().toEpochMilli()
        queue.recoverInterrupted(now - config.interruptedLease.toMillis(), now)

        queue.pending(tenantId).forEach { invoice ->
            if (stopProcessing) return@forEach
            if (!invoice.countryCode.equals("PA", ignoreCase = true)) {
                return@forEach
            }
            val claimDeadline = now + config.claimLease.toMillis()
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
            val sanitizedFactura =
                if (decoded.factura.idCajaSecuencia.length > 36 || decoded.factura.idCajaSecuencia.startsWith("OFFLINE-")) {
                    decoded.factura.copy(
                        idCajaSecuencia = decoded.factura.idCajaSecuencia.removePrefix("OFFLINE-").take(36),
                    )
                } else {
                    decoded.factura
                }
            val idempotentRequest =
                decoded.copy(
                    idFactura = decoded.idFactura ?: invoice.id,
                    codFactura = decoded.codFactura ?: invoice.localInvoiceNumber,
                    factura = sanitizedFactura,
                )
            gateway.submit(idempotentRequest).fold(
                onSuccess = { result ->
                    queue.markSent(invoice.id, result, clock.now().toEpochMilli())
                },
                onFailure = { error ->
                    when (handleSubmissionFailure(invoice, error, now)) {
                        FailureOutcome.StopAndRetry -> {
                            requiresRetry = true
                            stopProcessing = true
                        }
                        FailureOutcome.Continue -> Unit
                    }
                },
            )
        }

        return if (requiresRetry) PendingInvoiceSyncResult.Retry else PendingInvoiceSyncResult.Success
    }

    private enum class FailureOutcome {
        Continue,
        StopAndRetry,
    }

    private suspend fun handleSubmissionFailure(
        invoice: PendingInvoiceRecord,
        error: Throwable,
        now: Long,
    ): FailureOutcome =
        when (error) {
            // 409: ya existe en el servidor (respuesta perdida o reenvío) →
            // reconciliar por idFactura y marcar como enviada (PLAN §11.2).
            is com.amaxonia.pos.domain.usecase.payment.DuplicateInvoiceException -> {
                val reconciled = config.reconciler?.fetchByCorrelationId(error.clientCorrelationId)
                val settled = reconciled?.getOrNull()
                if (settled != null) {
                    queue.markSent(invoice.id, settled, clock.now().toEpochMilli())
                    FailureOutcome.Continue
                } else {
                    queue.markRecoverableFailure(
                        invoice.id,
                        "Conflicto de factura pendiente de reconciliar",
                        clock.now().toEpochMilli(),
                    )
                    FailureOutcome.StopAndRetry
                }
            }
            // 400 por regla de dominio (stock, crédito, lote, almacén):
            // rechazo terminal visible con motivo — jamás se reintenta (§13).
            is InvoiceDomainRejectedException -> {
                queue.markRejected(invoice.id, error.message ?: "Rechazada por el ERP", now)
                FailureOutcome.Continue
            }
            // Red/5xx: transitorio → backoff; al agotar el tope queda SUSPENDED.
            else -> {
                if (invoice.retryCount + 1 >= config.maxSubmitAttempts) {
                    queue.markSuspended(
                        invoice.id,
                        "SUSPENDIDA tras ${invoice.retryCount + 1} intentos: ${error.message}",
                        now,
                    )
                    FailureOutcome.Continue
                } else {
                    queue.markRecoverableFailure(
                        invoice.id,
                        error.message ?: "No se pudo reenviar la factura",
                        now,
                    )
                    FailureOutcome.StopAndRetry
                }
            }
        }

    private companion object {
        /** Leases de la cola de reenvío: recuperación de envíos interrumpidos y claim atómico por factura. */
        val DEFAULT_INTERRUPTED_LEASE: Duration = Duration.ofMinutes(15)
        val DEFAULT_CLAIM_LEASE: Duration = Duration.ofMinutes(2)

        /** Tope de reintentos transitorios antes de SUSPENDED (debe coincidir con el filtro del DAO). */
        const val DEFAULT_MAX_SUBMIT_ATTEMPTS = 20
    }
}
