package com.amaxonia.pos.data.local.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query

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
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

@Dao
interface PendingInvoiceDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(invoice: PendingInvoiceEntity)

    @Query("SELECT * FROM pending_invoices WHERE status IN ('PENDING', 'FAILED') ORDER BY createdAt ASC LIMIT :limit")
    suspend fun getPending(limit: Int = 25): List<PendingInvoiceEntity>

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
}
