package com.amaxonia.kiosk.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface PendingPaymentDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(payment: PendingPayment)

    @Update
    suspend fun update(payment: PendingPayment)

    @Query("SELECT * FROM pending_payments WHERE status = :status ORDER BY createdAt ASC")
    suspend fun getByStatus(status: String): List<PendingPayment>

    @Query("SELECT * FROM pending_payments WHERE orderId = :orderId")
    suspend fun getByOrderId(orderId: String): PendingPayment?

    @Query("DELETE FROM pending_payments WHERE orderId = :orderId")
    suspend fun delete(orderId: String)
}
