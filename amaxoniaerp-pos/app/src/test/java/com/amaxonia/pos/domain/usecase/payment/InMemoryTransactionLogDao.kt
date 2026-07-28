package com.amaxonia.pos.domain.usecase.payment

import com.amaxonia.pos.data.local.db.TransactionLogDao
import com.amaxonia.pos.data.local.db.TransactionLogEntity
import com.amaxonia.pos.domain.model.sales.FiscalState

/**
 * In-memory implementation of [TransactionLogDao] for unit tests (JVM only —
 * no Android/Room runtime needed). Mirrors the SQL semantics that matter for
 * the auditoría tests:
 *
 * - `tryClaimFiscal` / `tryClaimGateway` / `tryClaim` (pending_invoice via
 *   PendingInvoiceEntity) honour the `leasedUntil <= now` filter so concurrent
 *   claims are exclusive.
 * - `findById` returns the current row so callers can simulate a process
 *   death / restart sequence in the same test coroutine.
 *
 * Not a substitute for the Room Migration instrumented test
 * (AppDatabaseMigrationInstrumentedTest): the SQL DDL itself is validated there
 * against real SQLite.
 */
internal class InMemoryTransactionLogDao : TransactionLogDao {
    val rows = mutableMapOf<String, TransactionLogEntity>()

    override suspend fun upsert(entry: TransactionLogEntity) {
        rows[entry.clientCorrelationId] = entry
    }

    override suspend fun findById(id: String): TransactionLogEntity? = rows[id]

    override suspend fun markConfirmed(
        id: String,
        status: String,
        remoteInvoiceId: String?,
        remoteInvoiceNumber: String?,
        updatedAt: Long,
    ) {
        rows[id] =
            rows[id]!!.copy(
                status = status,
                remoteInvoiceId = remoteInvoiceId,
                remoteInvoiceNumber = remoteInvoiceNumber,
                lastError = null,
                updatedAt = updatedAt,
            )
    }

    override suspend fun markFailed(
        id: String,
        status: String,
        message: String?,
        updatedAt: Long,
    ) {
        rows[id] = rows[id]!!.copy(status = status, lastError = message, updatedAt = updatedAt)
    }

    override suspend fun listUnfinished(): List<TransactionLogEntity> = rows.values.filter { it.status in listOf("SENDING", "PENDING") }

    override suspend fun findFiscalConfirmable(
        now: Long,
        limit: Int,
    ): List<TransactionLogEntity> =
        rows.values
            .filter {
                it.fiscalConfirmationStatus in listOf("PENDING", "RETRYABLE_PENDING") &&
                    it.fiscalConfirmationNextAttemptAt <= now &&
                    it.fiscalConfirmationLeasedUntil <= now
            }.sortedBy { it.fiscalConfirmationNextAttemptAt }
            .take(limit)

    override suspend fun leaseFiscal(
        id: String,
        leasedUntil: Long,
        updatedAt: Long,
    ) {
        rows[id] = rows[id]!!.copy(fiscalConfirmationLeasedUntil = leasedUntil, updatedAt = updatedAt)
    }

    override suspend fun markFiscalConfirmed(
        id: String,
        status: String,
        fiscalNumber: String?,
        printerSerial: String?,
        updatedAt: Long,
    ) {
        rows[id] =
            rows[id]!!.copy(
                fiscalConfirmationStatus = status,
                fiscalNumber = fiscalNumber,
                printerSerial = printerSerial,
                lastError = null,
                updatedAt = updatedAt,
            )
    }

    override suspend fun markFiscalRetriable(
        id: String,
        status: String,
        remoteInvoiceId: String,
        fiscalNumber: String?,
        printerSerial: String?,
        nextAttemptAt: Long,
        message: String?,
        updatedAt: Long,
    ) {
        rows[id] =
            rows[id]!!.copy(
                fiscalConfirmationStatus = status,
                fiscalNumber = fiscalNumber,
                printerSerial = printerSerial,
                fiscalConfirmationRetryCount = rows[id]!!.fiscalConfirmationRetryCount + 1,
                fiscalConfirmationNextAttemptAt = nextAttemptAt,
                lastError = message,
                updatedAt = updatedAt,
            )
    }

    override suspend fun markFiscalTerminal(
        id: String,
        status: String,
        message: String?,
        updatedAt: Long,
    ) {
        rows[id] =
            rows[id]!!.copy(
                fiscalConfirmationStatus = status,
                lastError = message,
                fiscalConfirmationNextAttemptAt = 0L,
                fiscalConfirmationLeasedUntil = 0L,
                updatedAt = updatedAt,
            )
    }

    override suspend fun markGatewayAwaiting(
        id: String,
        status: String,
        nextAttemptAt: Long,
        updatedAt: Long,
    ) {
        rows[id] =
            rows[id]!!.copy(
                gatewayCallbackStatus = status,
                gatewayCallbackNextAttemptAt = nextAttemptAt,
                updatedAt = updatedAt,
            )
    }

    override suspend fun leaseGateway(
        id: String,
        leasedUntil: Long,
        updatedAt: Long,
    ) {
        rows[id] = rows[id]!!.copy(gatewayCallbackLeasedUntil = leasedUntil, updatedAt = updatedAt)
    }

    override suspend fun markGatewayResolved(
        id: String,
        status: String,
        resultCode: String?,
        rawResponse: String?,
        message: String?,
        updatedAt: Long,
    ) {
        val current = rows[id] ?: return
        // Mirrors the SQL: only advance status + clear lease fields when the
        // row is still awaiting. Always write the audit fields so a late
        // callback arriving after TERMINAL_AWAITING still leaves the HKA
        // response on disk for manual reconciliation.
        val stillAwaiting = current.gatewayCallbackStatus in listOf("AWAITING", "RETRYABLE_AWAITING")
        rows[id] =
            current.copy(
                gatewayCallbackStatus = if (stillAwaiting) status else current.gatewayCallbackStatus,
                gatewayCallbackNextAttemptAt = if (stillAwaiting) 0L else current.gatewayCallbackNextAttemptAt,
                gatewayCallbackLeasedUntil = if (stillAwaiting) 0L else current.gatewayCallbackLeasedUntil,
                gatewayResultCode = resultCode,
                gatewayRawResponse = rawResponse,
                gatewayResultMessage = message,
                updatedAt = updatedAt,
            )
    }

    override suspend fun markGatewayRetriable(
        id: String,
        status: String,
        nextAttemptAt: Long,
        rawResponse: String?,
        message: String?,
        updatedAt: Long,
    ) {
        rows[id] =
            rows[id]!!.copy(
                gatewayCallbackStatus = status,
                gatewayCallbackRetryCount = rows[id]!!.gatewayCallbackRetryCount + 1,
                gatewayCallbackNextAttemptAt = nextAttemptAt,
                gatewayCallbackLeasedUntil = 0L,
                gatewayRawResponse = rawResponse,
                lastError = message,
                updatedAt = updatedAt,
            )
    }

    override suspend fun markGatewayTerminal(
        id: String,
        status: String,
        rawResponse: String?,
        message: String?,
        updatedAt: Long,
    ) {
        rows[id] =
            rows[id]!!.copy(
                gatewayCallbackStatus = status,
                gatewayCallbackNextAttemptAt = 0L,
                gatewayCallbackLeasedUntil = 0L,
                gatewayRawResponse = rawResponse,
                lastError = message,
                updatedAt = updatedAt,
            )
    }

    override suspend fun findGatewayReconcilable(
        now: Long,
        limit: Int,
    ): List<TransactionLogEntity> =
        rows.values
            .filter {
                it.gatewayCallbackStatus in listOf("AWAITING", "RETRYABLE_AWAITING") &&
                    it.gatewayCallbackNextAttemptAt <= now &&
                    it.gatewayCallbackLeasedUntil <= now
            }.sortedBy { it.gatewayCallbackNextAttemptAt }
            .take(limit)

    // --- v14 additions (tenant-aware queries + atomic claims) ---

    override suspend fun findFiscalConfirmableForTenant(
        tenantId: String,
        now: Long,
        limit: Int,
    ): List<TransactionLogEntity> = findFiscalConfirmable(now, limit).filter { it.tenantId == tenantId }

    override suspend fun findGatewayReconcilableForTenant(
        tenantId: String,
        now: Long,
        limit: Int,
    ): List<TransactionLogEntity> = findGatewayReconcilable(now, limit).filter { it.tenantId == tenantId }

    /**
     * Atomic CAS — only flips the lease when the current lease has expired.
     * Returns 1 on success (mirrors Room's affected-row count).
     */
    @Suppress("ReturnCount")
    override suspend fun tryClaimFiscal(
        id: String,
        now: Long,
        leasedUntil: Long,
        updatedAt: Long,
    ): Int {
        val current = rows[id] ?: return 0
        if (current.fiscalConfirmationLeasedUntil > now) return 0
        rows[id] = current.copy(fiscalConfirmationLeasedUntil = leasedUntil, updatedAt = updatedAt)
        return 1
    }

    @Suppress("ReturnCount")
    override suspend fun tryClaimGateway(
        id: String,
        now: Long,
        leasedUntil: Long,
        updatedAt: Long,
    ): Int {
        val current = rows[id] ?: return 0
        if (current.gatewayCallbackLeasedUntil > now) return 0
        rows[id] = current.copy(gatewayCallbackLeasedUntil = leasedUntil, updatedAt = updatedAt)
        return 1
    }

    override suspend fun tenantIdOf(id: String): String? = rows[id]?.tenantId

    @Suppress("ReturnCount")
    override suspend fun transitionFiscalState(
        id: String,
        newState: FiscalState,
        expectedFrom: List<FiscalState>,
        updatedAt: Long,
    ): Int {
        val current = rows[id] ?: return 0
        if (current.fiscalState !in expectedFrom) return 0
        rows[id] = current.copy(fiscalState = newState, updatedAt = updatedAt)
        return 1
    }
}

/** Convenience accessor for tests: `dao.row("id-orThrow")`. */
internal fun <V> Map<String, V>.row(key: String): V = getValue(key)

/** Convenience to set/replace a row by id in tests. */
internal fun FiscalState.toDisplayString(): String = name
