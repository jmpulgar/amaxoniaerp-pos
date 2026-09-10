package com.amaxonia.pos.domain.usecase.sync

import com.amaxonia.pos.domain.model.sales.ProcessSaleRequestDto
import com.amaxonia.pos.domain.system.AppClock
import java.time.Duration

private const val SEQUENCE_ID_MAX_LENGTH = 36
private const val DEFAULT_INTERRUPTED_LEASE_MINUTES = 15L
private const val DEFAULT_CLAIM_LEASE_MINUTES = 2L
private const val DEFAULT_MAX_SUBMIT_ATTEMPTS_COUNT = 20

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
class InvoiceDomainRejectedException(
    message: String,
) : RuntimeException(message)

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
    val interruptedLease: Duration = Duration.ofMinutes(DEFAULT_INTERRUPTED_LEASE_MINUTES),
    val claimLease: Duration = Duration.ofMinutes(DEFAULT_CLAIM_LEASE_MINUTES),
    val reconciler: InvoiceReconciler? = null,
    val maxSubmitAttempts: Int = DEFAULT_MAX_SUBMIT_ATTEMPTS_COUNT,
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
        if (tenantId == null) return PendingInvoiceSyncResult.Success
        var requiresRetry = false
        val now = clock.now().toEpochMilli()
        queue.recoverInterrupted(now - config.interruptedLease.toMillis(), now)

        for (invoice in queue.pending(tenantId)) {
            when (syncInvoice(invoice, now)) {
                FailureOutcome.StopAndRetry -> {
                    requiresRetry = true
                    break
                }
                FailureOutcome.Continue -> Unit
            }
        }

        return if (requiresRetry) PendingInvoiceSyncResult.Retry else PendingInvoiceSyncResult.Success
    }

    private fun sanitizeFactura(factura: ProcessSaleFacturaPayload): ProcessSaleFacturaPayload =
        if (factura.idCajaSecuencia.length > SEQUENCE_ID_MAX_LENGTH ||
            factura.idCajaSecuencia.startsWith("OFFLINE-")
        ) {
            factura.copy(
                idCajaSecuencia =
                    factura.idCajaSecuencia
                        .removePrefix("OFFLINE-")
                        .take(SEQUENCE_ID_MAX_LENGTH),
            )
        } else {
            factura
        }

    private suspend fun claimAndDecode(
        invoice: PendingInvoiceRecord,
        now: Long,
    ): ProcessSaleRequestDto? {
        val claimDeadline = now + config.claimLease.toMillis()
        if (!invoice.countryCode.equals("PA", ignoreCase = true) ||
            queue.tryClaim(invoice.id, now = now, leasedUntil = claimDeadline) == 0
        ) {
            return null
        }
        queue.markSending(invoice.id, clock.now().toEpochMilli())
        val decodedResult = decoder.decode(invoice.payloadJson)
        val decoded = decodedResult.getOrNull()
        return if (decoded != null) {
            decoded.copy(
                idFactura = decoded.idFactura ?: invoice.id,
                codFactura = decoded.codFactura ?: invoice.localInvoiceNumber,
                factura = sanitizeFactura(decoded.factura),
            )
        } else {
            val errorMsg = decodedResult.exceptionOrNull()?.message ?: "Payload local inválido"
            queue.markPermanentFailure(invoice.id, errorMsg, clock.now().toEpochMilli())
            null
        }
    }

    private suspend fun syncInvoice(
        invoice: PendingInvoiceRecord,
        now: Long,
    ): FailureOutcome {
        val request = claimAndDecode(invoice, now) ?: return FailureOutcome.Continue
        var outcome = FailureOutcome.Continue
        gateway.submit(request).fold(
            onSuccess = { result ->
                queue.markSent(invoice.id, result, clock.now().toEpochMilli())
            },
            onFailure = { error ->
                outcome = handleSubmissionFailure(invoice, error, now)
            },
        )
        return outcome
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
        val DEFAULT_INTERRUPTED_LEASE: Duration = Duration.ofMinutes(DEFAULT_INTERRUPTED_LEASE_MINUTES)
        val DEFAULT_CLAIM_LEASE: Duration = Duration.ofMinutes(DEFAULT_CLAIM_LEASE_MINUTES)
        const val DEFAULT_MAX_SUBMIT_ATTEMPTS = DEFAULT_MAX_SUBMIT_ATTEMPTS_COUNT
    }
}
