package com.amaxonia.pos.data.local.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        ClientEntity::class,
        ClientSucursalEntity::class,
        ProductEntity::class,
        CountryEntity::class,
        AddressLevel1Entity::class,
        AddressLevel2Entity::class,
        AddressLevel3Entity::class,
        ClientTypeEntity::class,
        DraftInvoiceEntity::class,
        PendingInvoiceEntity::class,
        PromocionEntity::class,
        PromocionDetalleEntity::class,
        TransactionLogEntity::class,
    ],
    version = 13,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun clientDao(): ClientDao

    abstract fun clientSucursalDao(): ClientSucursalDao

    abstract fun productDao(): ProductDao

    abstract fun countryDao(): CountryDao

    abstract fun addressLevel1Dao(): AddressLevel1Dao

    abstract fun addressLevel2Dao(): AddressLevel2Dao

    abstract fun addressLevel3Dao(): AddressLevel3Dao

    abstract fun clientTypeDao(): ClientTypeDao

    abstract fun draftInvoiceDao(): DraftInvoiceDao

    abstract fun pendingInvoiceDao(): PendingInvoiceDao

    abstract fun promocionDao(): PromocionDao

    abstract fun transactionLogDao(): TransactionLogDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null
        internal val MIGRATION_1_2 =
            object : Migration(1, 2) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE clients ADD COLUMN addressLevel1 TEXT NOT NULL DEFAULT ''")
                    db.execSQL("ALTER TABLE clients ADD COLUMN addressLevel2 TEXT NOT NULL DEFAULT ''")
                    db.execSQL("ALTER TABLE clients ADD COLUMN addressLevel3 TEXT NOT NULL DEFAULT ''")
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS countries (" +
                            "id INTEGER NOT NULL, " +
                            "iso TEXT NOT NULL, " +
                            "name TEXT NOT NULL, " +
                            "PRIMARY KEY(id)" +
                            ")",
                    )
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS address_level1 (" +
                            "countryCode TEXT NOT NULL, " +
                            "code TEXT NOT NULL, " +
                            "name TEXT NOT NULL, " +
                            "PRIMARY KEY(countryCode, code)" +
                            ")",
                    )
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS address_level2 (" +
                            "countryCode TEXT NOT NULL, " +
                            "code TEXT NOT NULL, " +
                            "name TEXT NOT NULL, " +
                            "PRIMARY KEY(countryCode, code)" +
                            ")",
                    )
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS address_level3 (" +
                            "countryCode TEXT NOT NULL, " +
                            "code TEXT NOT NULL, " +
                            "name TEXT NOT NULL, " +
                            "PRIMARY KEY(countryCode, code)" +
                            ")",
                    )
                }
            }
        internal val MIGRATION_2_3 =
            object : Migration(2, 3) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE clients ADD COLUMN clientTypeId INTEGER NOT NULL DEFAULT 1")
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS client_types (" +
                            "id INTEGER NOT NULL, " +
                            "name TEXT NOT NULL, " +
                            "PRIMARY KEY(id)" +
                            ")",
                    )
                }
            }
        internal val MIGRATION_3_4 =
            object : Migration(3, 4) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE products ADD COLUMN barcode2 TEXT NOT NULL DEFAULT ''")
                    db.execSQL("ALTER TABLE products ADD COLUMN barcode3 TEXT NOT NULL DEFAULT ''")
                }
            }
        internal val MIGRATION_4_5 =
            object : Migration(4, 5) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE products ADD COLUMN isExempt INTEGER NOT NULL DEFAULT 0")
                    db.execSQL("UPDATE products SET isExempt = CASE WHEN IFNULL(taxRate, 0) <= 0 THEN 1 ELSE 0 END")
                }
            }
        internal val MIGRATION_5_6 =
            object : Migration(5, 6) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS draft_invoices (" +
                            "id TEXT NOT NULL, " +
                            "clientId TEXT, " +
                            "clientFirstName TEXT, " +
                            "clientLastName TEXT, " +
                            "sellerId INTEGER NOT NULL DEFAULT 0, " +
                            "sellerName TEXT, " +
                            "itemsJson TEXT NOT NULL, " +
                            "total REAL NOT NULL, " +
                            "itemCount INTEGER NOT NULL, " +
                            "createdAt INTEGER NOT NULL, " +
                            "PRIMARY KEY(id)" +
                            ")",
                    )
                }
            }
        internal val MIGRATION_6_7 =
            object : Migration(6, 7) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS pending_invoices (" +
                            "id TEXT NOT NULL, " +
                            "countryCode TEXT NOT NULL, " +
                            "payloadJson TEXT NOT NULL, " +
                            "localInvoiceNumber TEXT NOT NULL, " +
                            "total REAL NOT NULL, " +
                            "clientName TEXT NOT NULL, " +
                            "status TEXT NOT NULL, " +
                            "retryCount INTEGER NOT NULL, " +
                            "lastError TEXT, " +
                            "remoteInvoiceId TEXT, " +
                            "remoteInvoiceNumber TEXT, " +
                            "createdAt INTEGER NOT NULL, " +
                            "updatedAt INTEGER NOT NULL, " +
                            "PRIMARY KEY(id)" +
                            ")",
                    )
                }
            }
        internal val MIGRATION_7_8 =
            object : Migration(7, 8) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS promociones (" +
                            "id TEXT NOT NULL, " +
                            "codigo TEXT NOT NULL, " +
                            "inicio TEXT, " +
                            "fin TEXT, " +
                            "nombre TEXT NOT NULL, " +
                            "imagen TEXT NOT NULL, " +
                            "descuentoGlobal REAL NOT NULL, " +
                            "idItem TEXT NOT NULL, " +
                            "activo INTEGER NOT NULL, " +
                            "PRIMARY KEY(id)" +
                            ")",
                    )
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS promocion_detalles (" +
                            "id TEXT NOT NULL, " +
                            "promocionId TEXT NOT NULL, " +
                            "idItem TEXT NOT NULL, " +
                            "idTipoPrecio TEXT NOT NULL, " +
                            "cantidad REAL NOT NULL, " +
                            "cantidadTotal REAL NOT NULL, " +
                            "unidadEmpaque TEXT NOT NULL, " +
                            "descuento REAL NOT NULL, " +
                            "descuentoMonto REAL NOT NULL, " +
                            "precio REAL NOT NULL, " +
                            "impuesto REAL NOT NULL, " +
                            "impuestoPorcentaje REAL NOT NULL, " +
                            "importe REAL NOT NULL, " +
                            "grupo TEXT NOT NULL, " +
                            "PRIMARY KEY(id)" +
                            ")",
                    )
                }
            }

        internal val MIGRATION_8_9 =
            object : Migration(8, 9) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE products ADD COLUMN unitPackage TEXT NOT NULL DEFAULT ''")
                    db.execSQL("ALTER TABLE products ADD COLUMN bulkQuantity REAL NOT NULL DEFAULT 1.0")
                    db.execSQL("ALTER TABLE products ADD COLUMN portionUnit TEXT")
                    db.execSQL("ALTER TABLE products ADD COLUMN unitOrPackage TEXT NOT NULL DEFAULT 'UNIDAD'")
                }
            }

        internal val MIGRATION_9_10 =
            object : Migration(9, 10) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS client_sucursales (" +
                            "sucursalId INTEGER NOT NULL, " +
                            "clienteCodigo TEXT NOT NULL, " +
                            "nombreSucursal TEXT NOT NULL, " +
                            "nombreContacto TEXT, " +
                            "telefonoContacto TEXT, " +
                            "correoContacto TEXT, " +
                            "direccion TEXT, " +
                            "observaciones TEXT, " +
                            "PRIMARY KEY(sucursalId)" +
                            ")",
                    )
                }
            }

        internal val MIGRATION_10_11 =
            object : Migration(10, 11) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS transaction_log (" +
                            "clientCorrelationId TEXT NOT NULL, " +
                            "idCaja TEXT NOT NULL, " +
                            "idCajaSecuencia TEXT NOT NULL, " +
                            "totalAmount REAL NOT NULL, " +
                            "currency TEXT NOT NULL, " +
                            "clientName TEXT NOT NULL, " +
                            "status TEXT NOT NULL, " +
                            "remoteInvoiceId TEXT, " +
                            "remoteInvoiceNumber TEXT, " +
                            "lastError TEXT, " +
                            "createdAt INTEGER NOT NULL, " +
                            "updatedAt INTEGER NOT NULL, " +
                            "PRIMARY KEY(clientCorrelationId)" +
                            ")",
                    )
                }
            }

        internal val MIGRATION_11_12 =
            object : Migration(11, 12) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE transaction_log ADD COLUMN fiscalNumber TEXT")
                    db.execSQL("ALTER TABLE transaction_log ADD COLUMN printerSerial TEXT")
                    db.execSQL(
                        "ALTER TABLE transaction_log ADD COLUMN fiscalConfirmationStatus TEXT NOT NULL DEFAULT 'PENDING'",
                    )
                    db.execSQL(
                        "ALTER TABLE transaction_log ADD COLUMN fiscalConfirmationRetryCount INTEGER NOT NULL DEFAULT 0",
                    )
                    db.execSQL(
                        "ALTER TABLE transaction_log ADD COLUMN fiscalConfirmationNextAttemptAt INTEGER NOT NULL DEFAULT 0",
                    )
                    db.execSQL(
                        "ALTER TABLE transaction_log ADD COLUMN fiscalConfirmationLeasedUntil INTEGER NOT NULL DEFAULT 0",
                    )
                }
            }

        internal val MIGRATION_12_13 =
            object : Migration(12, 13) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    // Gateway callback lifecycle (FASE 3). DEFAULT 'IGNORED' so
                    // pre-existing rows are not treated as a pending callback.
                    db.execSQL(
                        "ALTER TABLE transaction_log ADD COLUMN gatewayCallbackStatus TEXT NOT NULL DEFAULT 'IGNORED'",
                    )
                    db.execSQL(
                        "ALTER TABLE transaction_log ADD COLUMN gatewayCallbackRetryCount INTEGER NOT NULL DEFAULT 0",
                    )
                    db.execSQL(
                        "ALTER TABLE transaction_log ADD COLUMN gatewayCallbackNextAttemptAt INTEGER NOT NULL DEFAULT 0",
                    )
                    db.execSQL(
                        "ALTER TABLE transaction_log ADD COLUMN gatewayCallbackLeasedUntil INTEGER NOT NULL DEFAULT 0",
                    )
                    db.execSQL("ALTER TABLE transaction_log ADD COLUMN gatewayRawResponse TEXT")
                }
            }

        internal val ALL_MIGRATIONS =
            arrayOf(
                MIGRATION_1_2,
                MIGRATION_2_3,
                MIGRATION_3_4,
                MIGRATION_4_5,
                MIGRATION_5_6,
                MIGRATION_6_7,
                MIGRATION_7_8,
                MIGRATION_8_9,
                MIGRATION_9_10,
                MIGRATION_10_11,
                MIGRATION_11_12,
                MIGRATION_12_13,
            )

        fun getInstance(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room
                    .databaseBuilder(
                        context.applicationContext,
                        AppDatabase::class.java,
                        "amaxonia_pos.db",
                    ).addMigrations(
                        MIGRATION_1_2,
                        MIGRATION_2_3,
                        MIGRATION_3_4,
                        MIGRATION_4_5,
                        MIGRATION_5_6,
                        MIGRATION_6_7,
                        MIGRATION_7_8,
                        MIGRATION_8_9,
                        MIGRATION_9_10,
                        MIGRATION_10_11,
                        MIGRATION_11_12,
                        MIGRATION_12_13,
                    ).build()
                    .also { instance = it }
            }
    }
}
