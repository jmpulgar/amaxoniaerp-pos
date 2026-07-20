package com.amaxonia.pos.data.local.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query

/**
 * Local ledger of every sale attempt started on this terminal. One row is
 * created the moment a new payment operation begins, before any network call
 * is issued, so the operation can be reconciled after process death, crashes
 * or duplicate submissions.
 *
 * The [clientCorrelationId] is the UUID minted on-device and sent to the
 * backend as `idFactura` so the backend can deduplicate identical retries of
 * the same operation (HTTP 409 Conflict on resubmit).
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
    val gatewayRawResponse: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
)

@Dao
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

    @Query(
        "UPDATE transaction_log SET gatewayCallbackStatus = :status, " +
            "gatewayCallbackNextAttemptAt = 0, gatewayCallbackLeasedUntil = 0, " +
            "gatewayRawResponse = :rawResponse, updatedAt = :updatedAt " +
            "WHERE clientCorrelationId = :id",
    )
    suspend fun markGatewayResolved(
        id: String,
        status: String,
        rawResponse: String?,
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
}
