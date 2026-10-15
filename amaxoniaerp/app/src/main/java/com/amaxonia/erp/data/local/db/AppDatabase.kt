package com.amaxonia.erp.data.local.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        ProductEntity::class,
        ClientEntity::class,
        ClientSucursalEntity::class,
        ClientTypeEntity::class,
        PendingInvoiceEntity::class,
        PaymentMethodEntity::class,
        CajaPaymentMethodEntity::class,
        CajaSesionEntity::class,
        SyncStateEntity::class,
        PromocionEntity::class,
        PromocionDetalleEntity::class,
        DepartmentEntity::class,
        DraftInvoiceEntity::class,
    ],
    version = 4,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun clientDao(): ClientDao
    abstract fun clientSucursalDao(): ClientSucursalDao
    abstract fun clientTypeDao(): ClientTypeDao
    abstract fun productDao(): ProductDao
    abstract fun departmentDao(): DepartmentDao
    abstract fun pendingInvoiceDao(): PendingInvoiceDao
    abstract fun draftInvoiceDao(): DraftInvoiceDao
    abstract fun promocionDao(): PromocionDao
    abstract fun syncStateDao(): SyncStateDao
    abstract fun paymentMethodDao(): PaymentMethodDao
    abstract fun cajaSesionDao(): CajaSesionDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `draft_invoices` (" +
                        "`id` TEXT NOT NULL, " +
                        "`localInvoiceNumber` TEXT NOT NULL, " +
                        "`clientName` TEXT NOT NULL, " +
                        "`clientId` TEXT, " +
                        "`sellerId` INTEGER, " +
                        "`total` REAL NOT NULL, " +
                        "`itemCount` INTEGER NOT NULL, " +
                        "`payloadJson` TEXT NOT NULL, " +
                        "`tenantId` TEXT NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL, " +
                        "`updatedAt` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`id`)" +
                        ")"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_draft_invoices_tenantId` ON `draft_invoices` (`tenantId`)")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `products` ADD COLUMN `hasOptions` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `products` ADD COLUMN `idCombo` TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE `products` ADD COLUMN `itemType` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `products` ADD COLUMN `hasVariants` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `products` ADD COLUMN `variantsTotal` INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `clients` ADD COLUMN `aplicaItbms` INTEGER NOT NULL DEFAULT 1")
            }
        }

        fun getInstance(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "amaxonia_erp.db",
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { instance = it }
            }
    }
}
