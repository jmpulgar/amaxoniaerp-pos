package com.amaxonia.erp.data.local.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query

@Entity(
    tableName = "draft_invoices",
    indices = [
        Index("tenantId"),
    ],
)
data class DraftInvoiceEntity(
    @PrimaryKey val id: String,
    val localInvoiceNumber: String,
    val clientName: String,
    val clientId: String? = null,
    val sellerId: Int? = null,
    val total: Double = 0.0,
    val itemCount: Int = 0,
    val payloadJson: String,
    val tenantId: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

@Dao
interface DraftInvoiceDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(draft: DraftInvoiceEntity)

    @Query("SELECT * FROM draft_invoices WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): DraftInvoiceEntity?

    @Query("SELECT * FROM draft_invoices WHERE tenantId = :tenantId ORDER BY updatedAt DESC")
    suspend fun getByTenant(tenantId: String): List<DraftInvoiceEntity>

    @Query("SELECT * FROM draft_invoices ORDER BY updatedAt DESC")
    suspend fun getAll(): List<DraftInvoiceEntity>

    @Query("DELETE FROM draft_invoices WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM draft_invoices")
    suspend fun clearAll()
}
