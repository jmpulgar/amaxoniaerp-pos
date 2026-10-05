package com.amaxonia.kiosk.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [PendingPayment::class], version = 1, exportSchema = false)
abstract class KioskDatabase : RoomDatabase() {
    abstract fun pendingPaymentDao(): PendingPaymentDao

    companion object {
        fun build(context: Context): KioskDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                KioskDatabase::class.java,
                "kiosk_database.db",
            ).build()
        }
    }
}
