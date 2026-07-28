package com.amaxonia.pos.data.local.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query

/**
 * Offline queue of sales that could not reach the backend. Schema v14 adds
 * the same tenant identity columns as [TransactionLogEntity] plus the
 * canonical total in minor-units, and a per-row lease so a single
 * [PendingInvoiceSyncWorker] cannot double-submit a row when relaunched
 * (auditoría ítems 3, 4, 8).
 */
@Entity(tableName = "pending_invoices")
data class PendingInvoiceEntity(
    @PrimaryKey val id: String,
    val countryCode: String,
    val payloadJson: String,
    val localInvoiceNumber: String,
    val total: Double,
    val clientName: String,
    val status: String = "PENDING",
    val retryCount: Int = 0,
    val lastError: String? = null,
    val remoteInvoiceId: String? = null,
    val remoteInvoiceNumber: String? = null,
    // --- v14 (auditoría ítems 3, 4, 8) ---
    val tenantId: String = "",
    val tenantCompanyId: Int = 0,
    val tenantAdminDb: String = "",
    val tenantContableDb: String = "",
    val tenantNominaDb: String = "",
    val tenantLabel: String = "",
    val totalMinor: Long = 0L,
    val currencyCode: String = "USD",
    /** Lease epoch-millis; 0 means "not currently claimed". See [tryClaim]. */
    val leasedUntil: Long = 0L,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

@Dao
interface PendingInvoiceDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(invoice: PendingInvoiceEntity)

    @Query("SELECT * FROM pending_invoices WHERE status IN ('PENDING', 'FAILED') ORDER BY createdAt ASC LIMIT :limit")
    suspend fun getPending(limit: Int = 25): List<PendingInvoiceEntity>

    /**
     * Pending rows for a specific tenant. Workers call this with the active
     * session tenant so rows from another tenant are never picked up.
     */
    @Query(
        "SELECT * FROM pending_invoices WHERE tenantId = :tenantId " +
            "AND status IN ('PENDING', 'FAILED') " +
            "ORDER BY createdAt ASC LIMIT :limit",
    )
    suspend fun getPendingForTenant(
        tenantId: String,
        limit: Int = 25,
    ): List<PendingInvoiceEntity>

    @Query("SELECT * FROM pending_invoices WHERE createdAt BETWEEN :fromMillis AND :toMillis ORDER BY createdAt ASC")
    suspend fun getCreatedBetween(
        fromMillis: Long,
        toMillis: Long,
    ): List<PendingInvoiceEntity>

    @Query("SELECT COUNT(*) FROM pending_invoices WHERE status IN ('PENDING', 'FAILED')")
    suspend fun countPending(): Int

    @Query("UPDATE pending_invoices SET status = 'SENDING', updatedAt = :updatedAt WHERE id = :id")
    suspend fun markSending(
        id: String,
        updatedAt: Long = System.currentTimeMillis(),
    )

    @Query(
        "UPDATE pending_invoices SET status = 'SENT', remoteInvoiceId = :remoteInvoiceId, " +
            "remoteInvoiceNumber = :remoteInvoiceNumber, lastError = NULL, updatedAt = :updatedAt WHERE id = :id",
    )
    suspend fun markSent(
        id: String,
        remoteInvoiceId: String,
        remoteInvoiceNumber: String,
        updatedAt: Long = System.currentTimeMillis(),
    )

    @Query(
        "UPDATE pending_invoices SET status = 'FAILED', retryCount = retryCount + 1, " +
            "lastError = :message, updatedAt = :updatedAt WHERE id = :id",
    )
    suspend fun markFailed(
        id: String,
        message: String,
        updatedAt: Long = System.currentTimeMillis(),
    )

    @Query("UPDATE pending_invoices SET status = 'INVALID', lastError = :message, updatedAt = :updatedAt WHERE id = :id")
    suspend fun markInvalid(
        id: String,
        message: String,
        updatedAt: Long = System.currentTimeMillis(),
    )

    @Query(
        "UPDATE pending_invoices SET status = 'FAILED', retryCount = retryCount + 1, " +
            "lastError = 'Interrupted synchronization recovered', updatedAt = :updatedAt " +
            "WHERE status = 'SENDING' AND updatedAt <= :staleBefore",
    )
    suspend fun recoverInterrupted(
        staleBefore: Long,
        updatedAt: Long = System.currentTimeMillis(),
    )

    /**
     * Atomic claim for the offline-upload worker (ítem 4 / CON-001). Returns
     * the number of rows claimed (1 = success, 0 = someone else owns it).
     * The accompanying `leasedUntil` is checked in the WHERE clause so two
     * concurrent workers cannot both submit the same row.
     */
    @Query(
        "UPDATE pending_invoices SET leasedUntil = :leasedUntil, updatedAt = :updatedAt " +
            "WHERE id = :id AND leasedUntil <= :now",
    )
    suspend fun tryClaim(
        id: String,
        now: Long,
        leasedUntil: Long,
        updatedAt: Long = System.currentTimeMillis(),
    ): Int
}
