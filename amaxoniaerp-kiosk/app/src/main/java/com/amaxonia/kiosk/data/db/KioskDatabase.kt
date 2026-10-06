package com.amaxonia.kiosk.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [PendingPayment::class], version = 2, exportSchema = false)
abstract class KioskDatabase : RoomDatabase() {
    abstract fun pendingPaymentDao(): PendingPaymentDao

    companion object {
        const val DATABASE_NAME = "kiosk_database.db"

        /** v1 → v2: additive `method` column on the payment outbox; existing rows were card payments. */
        val MIGRATION_1_2: Migration =
            object : Migration(1, 2) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE `pending_payments` ADD COLUMN `method` TEXT NOT NULL DEFAULT 'CARD'")
                }
            }

        /** Registers every schema migration; Room is never allowed to fall back to a destructive rebuild. */
        fun withMigrations(builder: RoomDatabase.Builder<KioskDatabase>): RoomDatabase.Builder<KioskDatabase> =
            builder.addMigrations(MIGRATION_1_2)

        fun build(context: Context): KioskDatabase {
            return withMigrations(
                Room.databaseBuilder(context.applicationContext, KioskDatabase::class.java, DATABASE_NAME),
            ).build()
        }
    }
}
