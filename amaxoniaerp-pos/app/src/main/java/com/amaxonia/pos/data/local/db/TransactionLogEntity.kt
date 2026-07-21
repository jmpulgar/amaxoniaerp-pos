package com.amaxonia.pos.data.local.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import com.amaxonia.pos.domain.model.sales.FiscalState

/**
 * Local ledger of every sale attempt started on this terminal. One row is
 * created the moment a new payment operation begins, before any network call
 * is issued, so the operation can be reconciled after process death, crashes
 * or duplicate submissions.
 *
 * The [clientCorrelationId] is the UUID minted on-device and sent to the
 * backend as `idFactura` so the backend can deduplicate identical retries of
 * the same operation (HTTP 409 Conflict on resubmit).
 *
 * Schema v14 adds (auditoría ítems 1, 3, 5, 8):
 * - **Tenant identity columns** ([tenantId], [tenantCompanyId], [tenantAdminDb],
 *   [tenantContableDb], [tenantNominaDb], [tenantLabel]) so a pending row is
 *   never processed with another tenant's session (TEN-001). [tenantId] is
 *   the canonical key; the rest are informative snapshots.
 * - **Canonical total in minor-units** ([totalAmountMinor] + [currencyCode])
 *   complementing the legacy [totalAmount] Double. The Double column is kept
 *   readable during the ítem-8 migration window; new code writes BOTH and
 *   reads minor-units.
 * - **Explicit fiscal lifecycle** ([fiscalState]) mapped from the legacy
 *   `fiscalConfirmationStatus` during migration; never reimprimir on retry.
 */
@Entity(tableName = "transaction_log")
data class TransactionLogEntity(
    @PrimaryKey val clientCorrelationId: String,
    val idCaja: String,
    val idCajaSecuencia: String,
    val totalAmount: Double,
    val currency: String,
    val clientName: String,
    val status: String,
    val remoteInvoiceId: String? = null,
    val remoteInvoiceNumber: String? = null,
    val lastError: String? = null,
    val fiscalNumber: String? = null,
    val printerSerial: String? = null,
    val fiscalConfirmationStatus: String = "PENDING",
    val fiscalConfirmationRetryCount: Int = 0,
    val fiscalConfirmationNextAttemptAt: Long = 0L,
    val fiscalConfirmationLeasedUntil: Long = 0L,
    // Gateway callback lifecycle (FASE 3). The encrypted gateway command itself
    // is NEVER persisted — only the lifecycle status, retry counters and the
    // short user-facing response code/message returned by HKA.
    val gatewayCallbackStatus: String = "IGNORED",
    val gatewayCallbackRetryCount: Int = 0,
    val gatewayCallbackNextAttemptAt: Long = 0L,
    val gatewayCallbackLeasedUntil: Long = 0L,
    /**
     * Full JSON returned by HKA in the `resultRapidPay` callback extra, kept
     * verbatim for audit and reconciliation (auditoría ítem 6). NEVER holds
     * the encrypted command or card number — those are redacted upstream.
     */
    val gatewayRawResponse: String? = null,
    /**
     * Short `codeRapidPay` value ("200" approved / "400" rejected) returned
     * in the callback. Mirrors [gatewayRawResponse] in cases where HKA sends
     * no JSON body (auditía ítem 6). The legacy semantics (RESOLVED for any
     * code) are preserved.
     */
    val gatewayResultCode: String? = null,
    /**
     * User-facing message returned by HKA in `messageRapidPay` or extracted
     * from the JSON body. Redacted of any card data upstream.
     */
    val gatewayResultMessage: String? = null,
    // --- v14 (auditoría ítems 1, 3, 5, 8) ---
    /** Canonical `t$<companyId>` key. Workers resolve ownership by this column only. */
    val tenantId: String = "",
    /** Raw company id. Informative; never used to route. */
    val tenantCompanyId: Int = 0,
    /** Snapshot of admin DB. Informative; never used to route. */
    val tenantAdminDb: String = "",
    /** Snapshot of contabilidad DB. Informative; never used to route. */
    val tenantContableDb: String = "",
    /** Snapshot of nómina DB. Informative; never used to route. */
    val tenantNominaDb: String = "",
    /** Snapshot of company name. Informative; never used to route. */
    val tenantLabel: String = "",
    /** Canonical total in minor-units (cents). Item 8 — Double-free persistence. */
    val totalAmountMinor: Long = 0L,
    /** ISO-4217-ish currency code tied to [totalAmountMinor]. */
    val currencyCode: String = "USD",
    /** Explicit fiscal lifecycle (ítem 5). Defaults to NOT_APPLICABLE for backfill. */
    val fiscalState: FiscalState = FiscalState.NOT_APPLICABLE,
    val createdAt: Long,
    val updatedAt: Long,
)

@Dao
@Suppress("TooManyFunctions")
interface TransactionLogDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: TransactionLogEntity)

    @Query("SELECT * FROM transaction_log WHERE clientCorrelationId = :id LIMIT 1")
    suspend fun findById(id: String): TransactionLogEntity?

    @Query(
        "UPDATE transaction_log SET status = :status, remoteInvoiceId = :remoteInvoiceId, " +
            "remoteInvoiceNumber = :remoteInvoiceNumber, lastError = NULL, updatedAt = :updatedAt " +
            "WHERE clientCorrelationId = :id",
    )
    suspend fun markConfirmed(
        id: String,
        status: String,
        remoteInvoiceId: String?,
        remoteInvoiceNumber: String?,
        updatedAt: Long = System.currentTimeMillis(),
    )

    @Query(
        "UPDATE transaction_log SET status = :status, lastError = :message, updatedAt = :updatedAt " +
            "WHERE clientCorrelationId = :id",
    )
    suspend fun markFailed(
        id: String,
        status: String,
        message: String?,
        updatedAt: Long = System.currentTimeMillis(),
    )

    @Query("SELECT * FROM transaction_log WHERE status IN ('SENDING', 'PENDING') ORDER BY createdAt DESC")
    suspend fun listUnfinished(): List<TransactionLogEntity>

    @Query(
        "SELECT * FROM transaction_log WHERE fiscalConfirmationStatus IN ('PENDING', 'RETRYABLE_PENDING') " +
            "AND fiscalConfirmationNextAttemptAt <= :now " +
            "AND fiscalConfirmationLeasedUntil <= :now " +
            "ORDER BY fiscalConfirmationNextAttemptAt ASC " +
            "LIMIT :limit",
    )
    suspend fun findFiscalConfirmable(
        now: Long,
        limit: Int = 25,
    ): List<TransactionLogEntity>

    @Query(
        "UPDATE transaction_log SET fiscalConfirmationLeasedUntil = :leasedUntil, updatedAt = :updatedAt " +
            "WHERE clientCorrelationId = :id",
    )
    suspend fun leaseFiscal(
        id: String,
        leasedUntil: Long,
        updatedAt: Long = System.currentTimeMillis(),
    )

    @Query(
        "UPDATE transaction_log SET fiscalConfirmationStatus = :status, fiscalNumber = :fiscalNumber, " +
            "printerSerial = :printerSerial, lastError = NULL, updatedAt = :updatedAt " +
            "WHERE clientCorrelationId = :id",
    )
    suspend fun markFiscalConfirmed(
        id: String,
        status: String,
        fiscalNumber: String?,
        printerSerial: String?,
        updatedAt: Long = System.currentTimeMillis(),
    )

    @Query(
        "UPDATE transaction_log SET fiscalConfirmationStatus = :status, remoteInvoiceId = :remoteInvoiceId, " +
            "fiscalNumber = :fiscalNumber, printerSerial = :printerSerial, " +
            "fiscalConfirmationRetryCount = fiscalConfirmationRetryCount + 1, " +
            "fiscalConfirmationNextAttemptAt = :nextAttemptAt, lastError = :message, updatedAt = :updatedAt " +
            "WHERE clientCorrelationId = :id",
    )
    suspend fun markFiscalRetriable(
        id: String,
        status: String,
        remoteInvoiceId: String,
        fiscalNumber: String?,
        printerSerial: String?,
        nextAttemptAt: Long,
        message: String?,
        updatedAt: Long = System.currentTimeMillis(),
    )

    @Query(
        "UPDATE transaction_log SET fiscalConfirmationStatus = :status, lastError = :message, " +
            "fiscalConfirmationNextAttemptAt = 0, fiscalConfirmationLeasedUntil = 0, updatedAt = :updatedAt " +
            "WHERE clientCorrelationId = :id",
    )
    suspend fun markFiscalTerminal(
        id: String,
        status: String,
        message: String?,
        updatedAt: Long = System.currentTimeMillis(),
    )

    /**
     * Atomic transition for the explicit fiscal lifecycle (ítem 5 / FIS-001).
     * Moves the row to [newState] ONLY if its current `fiscalState` is in
     * [expectedFrom]. Returns the number of affected rows so the caller can
     * detect a lost race (a parallel update already moved it forward).
     */
    @Query(
        "UPDATE transaction_log SET fiscalState = :newState, updatedAt = :updatedAt " +
            "WHERE clientCorrelationId = :id AND fiscalState IN (:expectedFrom)",
    )
    suspend fun transitionFiscalState(
        id: String,
        newState: FiscalState,
        expectedFrom: List<FiscalState>,
        updatedAt: Long = System.currentTimeMillis(),
    ): Int

    // ---- Gateway callback lifecycle (FASE 3) ----

    @Query(
        "UPDATE transaction_log SET gatewayCallbackStatus = :status, " +
            "gatewayCallbackNextAttemptAt = :nextAttemptAt, updatedAt = :updatedAt " +
            "WHERE clientCorrelationId = :id",
    )
    suspend fun markGatewayAwaiting(
        id: String,
        status: String,
        nextAttemptAt: Long,
        updatedAt: Long = System.currentTimeMillis(),
    )

    @Query(
        "UPDATE transaction_log SET gatewayCallbackLeasedUntil = :leasedUntil, updatedAt = :updatedAt " +
            "WHERE clientCorrelationId = :id",
    )
    suspend fun leaseGateway(
        id: String,
        leasedUntil: Long,
        updatedAt: Long = System.currentTimeMillis(),
    )

    /**
     * Marks the gateway callback as RESOLVED when MainActivity lands the HKA
     * Intent extras (auditoría ítem 6 / HKA-001 + HKA-002).
     *
     * The update ALWAYS writes the audit fields
     * ([gatewayResultCode], [gatewayRawResponse], [gatewayResultMessage]) so
     * a callback arriving after the watchdog escalated to
     * `TERMINAL_AWAITING` still leaves the full HKA response on disk for the
     * cashier/support to reconcile manually. The Status is only advanced
     * when the row is still awaiting — a callback arriving after the user
     * already marked the dispute terminal does NOT silently resurrect the
     * sale.
     */
    @Query(
        "UPDATE transaction_log SET " +
            "gatewayCallbackStatus = CASE WHEN gatewayCallbackStatus IN ('AWAITING','RETRYABLE_AWAITING') " +
            "  THEN :status ELSE gatewayCallbackStatus END, " +
            "gatewayCallbackNextAttemptAt = CASE WHEN gatewayCallbackStatus IN ('AWAITING','RETRYABLE_AWAITING') " +
            "  THEN 0 ELSE gatewayCallbackNextAttemptAt END, " +
            "gatewayCallbackLeasedUntil = CASE WHEN gatewayCallbackStatus IN ('AWAITING','RETRYABLE_AWAITING') " +
            "  THEN 0 ELSE gatewayCallbackLeasedUntil END, " +
            "gatewayResultCode = :resultCode, " +
            "gatewayRawResponse = :rawResponse, " +
            "gatewayResultMessage = :message, updatedAt = :updatedAt " +
            "WHERE clientCorrelationId = :id",
    )
    suspend fun markGatewayResolved(
        id: String,
        status: String,
        resultCode: String?,
        rawResponse: String?,
        message: String?,
        updatedAt: Long = System.currentTimeMillis(),
    )

    @Query(
        "UPDATE transaction_log SET gatewayCallbackStatus = :status, " +
            "gatewayCallbackRetryCount = gatewayCallbackRetryCount + 1, " +
            "gatewayCallbackNextAttemptAt = :nextAttemptAt, " +
            "gatewayCallbackLeasedUntil = 0, " +
            "gatewayRawResponse = :rawResponse, lastError = :message, updatedAt = :updatedAt " +
            "WHERE clientCorrelationId = :id",
    )
    suspend fun markGatewayRetriable(
        id: String,
        status: String,
        nextAttemptAt: Long,
        rawResponse: String?,
        message: String?,
        updatedAt: Long = System.currentTimeMillis(),
    )

    @Query(
        "UPDATE transaction_log SET gatewayCallbackStatus = :status, " +
            "gatewayCallbackNextAttemptAt = 0, gatewayCallbackLeasedUntil = 0, " +
            "gatewayRawResponse = :rawResponse, lastError = :message, updatedAt = :updatedAt " +
            "WHERE clientCorrelationId = :id",
    )
    suspend fun markGatewayTerminal(
        id: String,
        status: String,
        rawResponse: String?,
        message: String?,
        updatedAt: Long = System.currentTimeMillis(),
    )

    @Query(
        "SELECT * FROM transaction_log WHERE gatewayCallbackStatus IN ('AWAITING','RETRYABLE_AWAITING') " +
            "AND gatewayCallbackNextAttemptAt <= :now " +
            "AND gatewayCallbackLeasedUntil <= :now " +
            "ORDER BY gatewayCallbackNextAttemptAt ASC " +
            "LIMIT :limit",
    )
    suspend fun findGatewayReconcilable(
        now: Long,
        limit: Int = 10,
    ): List<TransactionLogEntity>

    // --- v14 (auditoría ítems 3 + 4): tenant isolation + atomic claims ---

    /**
     * Lists rows whose fiscal confirmation is pending, **restricted to the
     * caller's tenant**. Workers calling this with the active session's
     * [SaleTenant.tenantId] will only ever see rows they are allowed to
     * process; rows of a different tenant stay invisible and therefore
     * pending until their owner's session resumes.
     */
    @Query(
        "SELECT * FROM transaction_log WHERE tenantId = :tenantId " +
            "AND fiscalConfirmationStatus IN ('PENDING', 'RETRYABLE_PENDING') " +
            "AND fiscalConfirmationNextAttemptAt <= :now " +
            "AND fiscalConfirmationLeasedUntil <= :now " +
            "ORDER BY fiscalConfirmationNextAttemptAt ASC " +
            "LIMIT :limit",
    )
    suspend fun findFiscalConfirmableForTenant(
        tenantId: String,
        now: Long,
        limit: Int = 25,
    ): List<TransactionLogEntity>

    @Query(
        "SELECT * FROM transaction_log WHERE tenantId = :tenantId " +
            "AND gatewayCallbackStatus IN ('AWAITING','RETRYABLE_AWAITING') " +
            "AND gatewayCallbackNextAttemptAt <= :now " +
            "AND gatewayCallbackLeasedUntil <= :now " +
            "ORDER BY gatewayCallbackNextAttemptAt ASC " +
            "LIMIT :limit",
    )
    suspend fun findGatewayReconcilableForTenant(
        tenantId: String,
        now: Long,
        limit: Int = 10,
    ): List<TransactionLogEntity>

    /**
     * Atomic CAS used by workers to claim a row (ítem 4 / CON-001). Only
     * succeeds if the row's leasedUntil has expired (≤ :now), in which case
     * it is advanced to `:leasedUntil`. Returns the number of rows claimed
     * (0 = someone else owns it, or the row no longer matches the filter).
     *
     * The update is bounded to a single [id] so two concurrent workers
     * cannot both flip the same row: SQLite serialises the write and the
     * WHERE clause is re-evaluated under the row lock.
     */
    @Query(
        "UPDATE transaction_log SET fiscalConfirmationLeasedUntil = :leasedUntil, updatedAt = :updatedAt " +
            "WHERE clientCorrelationId = :id AND fiscalConfirmationLeasedUntil <= :now",
    )
    suspend fun tryClaimFiscal(
        id: String,
        now: Long,
        leasedUntil: Long,
        updatedAt: Long = System.currentTimeMillis(),
    ): Int

    @Query(
        "UPDATE transaction_log SET gatewayCallbackLeasedUntil = :leasedUntil, updatedAt = :updatedAt " +
            "WHERE clientCorrelationId = :id AND gatewayCallbackLeasedUntil <= :now",
    )
    suspend fun tryClaimGateway(
        id: String,
        now: Long,
        leasedUntil: Long,
        updatedAt: Long = System.currentTimeMillis(),
    ): Int

    /**
     * Looks up a transaction by its canonical correlation id across tenants.
     * Used by MainActivity's gateway-callback handler to resolve the AWAITING
     * row regardless of session; the callback is cross-tenant safe only when
     * the result is validated against the persisted `tenantId` of the row.
     */
    @Query("SELECT tenantId FROM transaction_log WHERE clientCorrelationId = :id LIMIT 1")
    suspend fun tenantIdOf(id: String): String?
}
