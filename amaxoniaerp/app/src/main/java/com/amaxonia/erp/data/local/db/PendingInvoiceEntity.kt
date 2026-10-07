package com.amaxonia.erp.data.local.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query

@Entity(
    tableName = "pending_invoices",
    indices = [
        Index("tenantId"),
    ],
)
data class PendingInvoiceEntity(
    @PrimaryKey val id: String,
    val countryCode: String,
    val payloadJson: String,
    val localInvoiceNumber: String,
    val clientName: String,
    val status: String = "PENDING",
    val retryCount: Int = 0,
    val lastError: String? = null,
    val remoteInvoiceId: String? = null,
    val remoteInvoiceNumber: String? = null,
    val tenantId: String = "",
    val total: Double = 0.0,
    val leasedUntil: Long = 0,
    val createdAt: Long,
    val updatedAt: Long,
)

@Dao
interface PendingInvoiceDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(invoice: PendingInvoiceEntity)

    @Query("SELECT * FROM pending_invoices WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): PendingInvoiceEntity?

    @Query("SELECT * FROM pending_invoices WHERE status IN ('PENDING', 'FAILED') ORDER BY createdAt ASC LIMIT :limit")
    suspend fun getPending(limit: Int = 50): List<PendingInvoiceEntity>

    @Query(
        "SELECT * FROM pending_invoices WHERE tenantId = :tenantId " +
            "AND status IN ('PENDING', 'FAILED') " +
            "ORDER BY createdAt ASC LIMIT :limit",
    )
    suspend fun getPendingForTenant(
        tenantId: String,
        limit: Int = 50,
    ): List<PendingInvoiceEntity>

    @Query("SELECT * FROM pending_invoices ORDER BY createdAt DESC LIMIT :limit")
    suspend fun getAllRecent(limit: Int = 50): List<PendingInvoiceEntity>

    @Query("SELECT COUNT(*) FROM pending_invoices WHERE status IN ('PENDING', 'FAILED')")
    suspend fun getPendingCount(): Int

    @Query("UPDATE pending_invoices SET status = 'SENDING', updatedAt = :updatedAt WHERE id = :id")
    suspend fun markSending(
        id: String,
        updatedAt: Long,
    )

    @Query(
        "UPDATE pending_invoices SET status = 'SENT', remoteInvoiceId = :remoteInvoiceId, " +
            "remoteInvoiceNumber = :remoteInvoiceNumber, updatedAt = :updatedAt WHERE id = :id",
    )
    suspend fun markSent(
        id: String,
        remoteInvoiceId: String,
        remoteInvoiceNumber: String,
        updatedAt: Long,
    )

    @Query(
        "UPDATE pending_invoices SET status = 'FAILED', retryCount = retryCount + 1, " +
            "lastError = :lastError, updatedAt = :updatedAt WHERE id = :id",
    )
    suspend fun markFailed(
        id: String,
        lastError: String,
        updatedAt: Long,
    )

    @Query("UPDATE pending_invoices SET status = 'REJECTED', lastError = :message, updatedAt = :updatedAt WHERE id = :id")
    suspend fun markRejected(
        id: String,
        message: String,
        updatedAt: Long,
    )

    @Query("UPDATE pending_invoices SET status = 'INVALID', lastError = :message, updatedAt = :updatedAt WHERE id = :id")
    suspend fun markInvalid(
        id: String,
        message: String,
        updatedAt: Long,
    )

    @Query(
        "UPDATE pending_invoices SET leasedUntil = :leasedUntil, updatedAt = :updatedAt " +
            "WHERE id = :id AND (leasedUntil <= :now OR status = 'FAILED')",
    )
    suspend fun tryClaim(
        id: String,
        now: Long,
        leasedUntil: Long,
        updatedAt: Long,
    ): Int

    @Query(
        "UPDATE pending_invoices SET status = 'PENDING', leasedUntil = 0, updatedAt = :now " +
            "WHERE status = 'SENDING' AND leasedUntil <= :staleBefore",
    )
    suspend fun recoverInterrupted(
        staleBefore: Long,
        now: Long,
    ): Int

    @Query("DELETE FROM pending_invoices WHERE id = :id")
    suspend fun deleteById(id: String)
}
