package com.amaxonia.pos.data.local.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query

@Entity(tableName = "product_options")
data class ProductOptionsEntity(
    @PrimaryKey val productId: String,
    val optionsJson: String,
    val updatedAt: Long = 0,
)

@Dao
interface ProductOptionsDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(options: ProductOptionsEntity)

    @Query("SELECT * FROM product_options WHERE productId = :productId LIMIT 1")
    suspend fun getByProductId(productId: String): ProductOptionsEntity?

    @Query("DELETE FROM product_options")
    suspend fun clearAll()
}
