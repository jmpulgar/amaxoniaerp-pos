package com.amaxonia.pos.domain.usecase.payment

import com.amaxonia.pos.data.local.db.TransactionLogDao
import com.amaxonia.pos.data.local.db.TransactionLogEntity
import com.amaxonia.pos.domain.model.tenant.SaleTenant
import com.amaxonia.pos.domain.system.AppClock
import com.amaxonia.pos.domain.system.IdGenerator

/**
 * Mints the client-side correlation id (`idFactura`) exactly once per new
 * payment operation and persists a `SENDING` row in the on-device ledger so
 * the operation survives process death.
 *
 * Recovery rule: if [recoverOrStart] is invoked with a [carryOverId] that
 * already has a row in `SENDING` status (e.g. the app crashed mid-submission
 * and the user re-triggered the same operation), the existing correlation id
 * is reused so the backend deduplication (HTTP 409) kicks in. If the carried
 * id has no ledger row, or is blank, a fresh id is minted — correlation is
 * never re-derived from carrito contents (it is one-per-operation, not
 * one-per-carrito-similarity).
 *
 * Tenant rule (auditoría ítem 3 / TEN-001): every newly-opened row is stamped
 * with the active [SaleTenant]. Workers querying via the tenant-scoped DAO
 * methods will only ever see rows they are allowed to process; a row of an
 * inactive tenant stays pending until that tenant's session resumes. If
 * [tenant] is null (no company session active), the use case refuses to open
 * a row because the row could otherwise be later processed under the wrong
 * tenant.
 */
class StartTransactionUseCase(
    private val dao: TransactionLogDao,
    private val idGenerator: IdGenerator,
    private val clock: AppClock,
) {
    suspend fun recoverOrStart(command: StartTransactionCommand): StartedTransaction? {
        val now = clock.now().toEpochMilli()
        val existing = command.carryOverId?.takeIf(String::isNotBlank)?.let { dao.findById(it) }
        // If the carry-over id resolves to a row owned by a DIFFERENT tenant,
        // refuse to resume it: that would mix two operations under one id.
        val resumable =
            existing
                ?.takeIf { it.status == STATUS_SENDING }
                ?.takeIf { command.tenant == null || it.tenantId == command.tenant.tenantId }
        val completedPreferred =
            existing
                ?.takeIf { it.clientCorrelationId == command.preferredId }
                ?.takeIf { it.status == STATUS_CONFIRMED || it.status == STATUS_DUPLICATE }
                ?.takeIf { command.tenant == null || it.tenantId == command.tenant.tenantId }
        return if (resumable != null || completedPreferred != null) {
            val recovered = resumable ?: completedPreferred!!
            StartedTransaction(clientCorrelationId = recovered.clientCorrelationId, resumed = true)
        } else {
            val tenant = command.tenant ?: return null
            val id = command.preferredId?.takeIf(String::isNotBlank) ?: idGenerator.nextId()
            // Auditoría ítem 10 (OBS-001): emit the structured sale-started
            // event before any network or printer call so every observable
            // flow has a unique correlated id.
            com.amaxonia.pos.core.telemetry.SaleTelemetry.record(
                event = com.amaxonia.pos.core.telemetry.SaleEvent.SALE_STARTED,
                idFactura = id,
                "tenant" to tenant.tenantId,
                "total" to command.totalAmount,
                "currency" to command.currency,
            )
            // Auditoría ítem 8 (MONEY-001): persist the canonical total in
            // minor-units via BigDecimal. The legacy `totalAmount` Double is
            // still written for the migration window but is no longer the
            // source of truth for money calculations.
            val totalMinor =
                com.amaxonia.pos.domain.model.money.MinorUnitMoney
                    .fromDoubleAsMinor(command.totalAmount)
            dao.upsert(
                TransactionLogEntity(
                    clientCorrelationId = id,
                    idCaja = command.idCaja,
                    idCajaSecuencia = command.idCajaSecuencia,
                    totalAmount = command.totalAmount,
                    currency = command.currency,
                    clientName = command.clientName,
                    status = STATUS_SENDING,
                    tenantId = tenant.tenantId,
                    tenantCompanyId = tenant.companyId,
                    tenantAdminDb = tenant.adminDb,
                    tenantContableDb = tenant.contableDb,
                    tenantNominaDb = tenant.nominaDb,
                    tenantLabel = tenant.label,
                    totalAmountMinor = totalMinor,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
            StartedTransaction(clientCorrelationId = id, resumed = false)
        }
    }

    suspend fun markConfirmed(
        clientCorrelationId: String,
        remoteInvoiceId: String?,
        remoteInvoiceNumber: String?,
    ) {
        dao.markConfirmed(
            id = clientCorrelationId,
            status = STATUS_CONFIRMED,
            remoteInvoiceId = remoteInvoiceId,
            remoteInvoiceNumber = remoteInvoiceNumber,
        )
    }

    suspend fun markFailed(
        clientCorrelationId: String,
        message: String?,
        status: String = STATUS_FAILED,
    ) {
        dao.markFailed(
            id = clientCorrelationId,
            status = status,
            message = message,
        )
    }

    companion object {
        const val STATUS_SENDING = "SENDING"
        const val STATUS_CONFIRMED = "CONFIRMED"
        const val STATUS_FAILED = "FAILED"
        const val STATUS_DUPLICATE = "DUPLICATE"
    }
}

data class StartedTransaction(
    val clientCorrelationId: String,
    val resumed: Boolean,
)

/**
 * Carries the fields needed to open a row in the transaction_log. The
 * [carryOverId] is the only optional field: when present and still in SENDING
 * status, the existing correlation id is reused; otherwise a fresh UUID is
 * minted. [tenant] is REQUIRED for new rows and the row is refused if absent.
 */
data class StartTransactionCommand(
    val carryOverId: String?,
    /** Canonical id for a newly-created operation whose aggregate already owns an id (mesa). */
    val preferredId: String? = null,
    val idCaja: String,
    val idCajaSecuencia: String,
    val totalAmount: Double,
    val currency: String,
    val clientName: String,
    val tenant: SaleTenant?,
)
