package com.amaxonia.pos.data.local.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query

@Entity(tableName = "draft_invoices")
data class DraftInvoiceEntity(
    @PrimaryKey val id: String,
    val clientId: String? = null,
    val clientFirstName: String? = null,
    val clientLastName: String? = null,
    val sellerId: Int = 0,
    val sellerName: String? = null,
    val itemsJson: String, // JSON serializado de los CartItem
    val total: Double,
    val itemCount: Int,
    val createdAt: Long = System.currentTimeMillis(),
)

@Dao
interface DraftInvoiceDao {
    @Query("SELECT * FROM draft_invoices ORDER BY createdAt DESC")
    suspend fun getAll(): List<DraftInvoiceEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(draft: DraftInvoiceEntity)

    @Query("DELETE FROM draft_invoices WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM draft_invoices")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM draft_invoices")
    suspend fun count(): Int
}
