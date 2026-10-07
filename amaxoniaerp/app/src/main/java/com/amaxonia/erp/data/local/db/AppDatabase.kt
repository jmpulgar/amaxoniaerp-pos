package com.amaxonia.erp.data.local.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

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
    ],
    version = 1,
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
    abstract fun promocionDao(): PromocionDao
    abstract fun syncStateDao(): SyncStateDao
    abstract fun paymentMethodDao(): PaymentMethodDao
    abstract fun cajaSesionDao(): CajaSesionDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "amaxonia_erp.db",
                ).fallbackToDestructiveMigration()
                    .build()
                    .also { instance = it }
            }
    }
}
