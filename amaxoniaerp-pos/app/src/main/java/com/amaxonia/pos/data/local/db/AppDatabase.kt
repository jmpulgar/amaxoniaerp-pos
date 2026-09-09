package com.amaxonia.pos.data.local.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.amaxonia.pos.domain.model.sales.FiscalStateConverter

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
        SyncStateEntity::class,
        PaymentMethodEntity::class,
        CajaPaymentMethodEntity::class,
        CajaSesionEntity::class,
    ],
    version = 20,
    exportSchema = true,
)
@TypeConverters(Converters::class, FiscalStateConverter::class)
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

    abstract fun syncStateDao(): SyncStateDao

    abstract fun paymentMethodDao(): PaymentMethodDao

    abstract fun cajaSesionDao(): CajaSesionDao

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

        /**
         * Auditoría docs/auditoria-produccion-pos-2026-07-20.md — single
         * foundation migration for ítems 1, 3, 4, 5, 8.
         *
         * Adds to `transaction_log` and `pending_invoices`:
         *  - Tenant identity columns (canonical `tenantId` + informative
         *    `tenantCompanyId` + snapshot DBs/label).
         *  - Canonical total in minor-units (`totalAmountMinor`/`totalMinor`)
         *    and `currencyCode`, alongside the legacy `Double` columns kept
         *    readable during the ítem-8 migration window.
         *  - Explicit fiscal lifecycle ([FiscalState]) on `transaction_log`,
         *    backfilled from the legacy `fiscalConfirmationStatus`.
         *  - Sub-second `LEASED_UNTIL` column on `pending_invoices` for atomic
         *    worker claims (ítem 4).
         *  - Index on `tenantId` for both tables so the worker tenant filter
         *    is index-backed.
         *
         * Defaults are intentionally neutral/empty so the migration is
         * idempotent and never aborts the batch. Pre-existing rows are
         * tagged `tenantId=''` which `SaleTenant.UNKNOWN_TENANT_ID` would
         * normally reject, but the worker treats '' as "needs attribution"
         * and leaves such rows pending until they gain a tenantId.
         *
         * Backfill of `totalAmountMinor` uses SQLite round-to-int on the
         * legacy Double. Values that would overflow Int64 are extraordinarily
         * unlikely at the POS scale (|total| > 92 trillion USD) but the
         * conversion is still guarded at runtime by `MinorUnitMoney.fromBigDecimalAsMinor`.
         *
         * IMPORTANT: This migration uses TABLE REBUILDS (`CREATE new → INSERT
         * SELECT → DROP old → RENAME`) instead of `ALTER TABLE ... ADD COLUMN
         * ... DEFAULT` for all tenant/minor-unit/lease columns. Reason: Room
         * validates the post-migration schema byte-for-byte against the
         * exported `14.json`, and our Kotlin entities declare these columns
         * WITHOUT `@ColumnInfo(defaultValue=...)`, so Room expects the SQLite
         * columns to have NO `DEFAULT` clause. SQLite forbids `ADD COLUMN
         * NOT NULL` without a DEFAULT on a table containing rows, so the only
         * way to satisfy both Room and SQLite is to recreate the table.
         * This also lets us seed the backfilled columns (totalAmountMinor,
         * fiscalState, currencyCode) atomically in the same INSERT statement.
         */
        internal val MIGRATION_13_14 =
            object : Migration(13, 14) {
                // Secuencia SQL debe permanecer indivisible y auditable para migración Room.
                @Suppress("LongMethod")
                override fun migrate(db: SupportSQLiteDatabase) {
                    // ============================================================
                    // transaction_log: full rebuild adding tenant identity,
                    // canonical total, fiscal lifecycle and gateway extras.
                    // ============================================================
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS transaction_log_new (" +
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
                            "fiscalNumber TEXT, " +
                            "printerSerial TEXT, " +
                            "fiscalConfirmationStatus TEXT NOT NULL, " +
                            "fiscalConfirmationRetryCount INTEGER NOT NULL, " +
                            "fiscalConfirmationNextAttemptAt INTEGER NOT NULL, " +
                            "fiscalConfirmationLeasedUntil INTEGER NOT NULL, " +
                            "gatewayCallbackStatus TEXT NOT NULL, " +
                            "gatewayCallbackRetryCount INTEGER NOT NULL, " +
                            "gatewayCallbackNextAttemptAt INTEGER NOT NULL, " +
                            "gatewayCallbackLeasedUntil INTEGER NOT NULL, " +
                            "gatewayRawResponse TEXT, " +
                            "gatewayResultCode TEXT, " +
                            "gatewayResultMessage TEXT, " +
                            "tenantId TEXT NOT NULL, " +
                            "tenantCompanyId INTEGER NOT NULL, " +
                            "tenantAdminDb TEXT NOT NULL, " +
                            "tenantContableDb TEXT NOT NULL, " +
                            "tenantNominaDb TEXT NOT NULL, " +
                            "tenantLabel TEXT NOT NULL, " +
                            "totalAmountMinor INTEGER NOT NULL, " +
                            "currencyCode TEXT NOT NULL, " +
                            "fiscalState TEXT NOT NULL, " +
                            "createdAt INTEGER NOT NULL, " +
                            "updatedAt INTEGER NOT NULL, " +
                            "PRIMARY KEY(clientCorrelationId))",
                    )
                    // Seed minor-unit total from legacy Double (round-half-away-from-zero
                    // is close enough to HALF_EVEN at 2-decimal POS scale) and
                    // backfill fiscalState from the legacy fiscal confirm status.
                    db.execSQL(
                        "INSERT INTO transaction_log_new (" +
                            "clientCorrelationId, idCaja, idCajaSecuencia, totalAmount, currency, " +
                            "clientName, status, remoteInvoiceId, remoteInvoiceNumber, lastError, " +
                            "fiscalNumber, printerSerial, fiscalConfirmationStatus, " +
                            "fiscalConfirmationRetryCount, fiscalConfirmationNextAttemptAt, " +
                            "fiscalConfirmationLeasedUntil, gatewayCallbackStatus, " +
                            "gatewayCallbackRetryCount, gatewayCallbackNextAttemptAt, " +
                            "gatewayCallbackLeasedUntil, gatewayRawResponse, gatewayResultCode, " +
                            "gatewayResultMessage, tenantId, tenantCompanyId, tenantAdminDb, " +
                            "tenantContableDb, tenantNominaDb, tenantLabel, totalAmountMinor, " +
                            "currencyCode, fiscalState, createdAt, updatedAt) " +
                            "SELECT " +
                            "clientCorrelationId, idCaja, idCajaSecuencia, totalAmount, currency, " +
                            "clientName, status, remoteInvoiceId, remoteInvoiceNumber, lastError, " +
                            "fiscalNumber, printerSerial, fiscalConfirmationStatus, " +
                            "fiscalConfirmationRetryCount, fiscalConfirmationNextAttemptAt, " +
                            "fiscalConfirmationLeasedUntil, gatewayCallbackStatus, " +
                            "gatewayCallbackRetryCount, gatewayCallbackNextAttemptAt, " +
                            "gatewayCallbackLeasedUntil, gatewayRawResponse, NULL, NULL, " +
                            // tenant identity: empty/0 — workers tag rows as they go.
                            "'', 0, '', '', '', '', " +
                            // Canonical total from legacy Double cents.
                            "CAST(ROUND(totalAmount * 100.0) AS INTEGER), " +
                            "IFNULL(currency, 'USD'), " +
                            // fiscalState backfill from legacy status enum.
                            "CASE " +
                            "WHEN fiscalConfirmationStatus IN ('PENDING','RETRYABLE_PENDING','IN_FLIGHT') THEN 'PRINTED_PENDING_CONFIRM' " +
                            "WHEN fiscalConfirmationStatus = 'CONFIRMED' THEN 'CONFIRMED' " +
                            "WHEN fiscalConfirmationStatus = 'TERMINAL_FAILED' THEN 'FAILED' " +
                            "ELSE 'NOT_APPLICABLE' END, " +
                            "createdAt, updatedAt " +
                            "FROM transaction_log",
                    )
                    db.execSQL("DROP TABLE transaction_log")
                    db.execSQL("ALTER TABLE transaction_log_new RENAME TO transaction_log")
                    db.execSQL("CREATE INDEX IF NOT EXISTS index_transaction_log_tenantId ON transaction_log(tenantId)")

                    // ============================================================
                    // pending_invoices: full rebuild adding tenant identity,
                    // canonical total and per-row lease.
                    // ============================================================
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS pending_invoices_new (" +
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
                            "tenantId TEXT NOT NULL, " +
                            "tenantCompanyId INTEGER NOT NULL, " +
                            "tenantAdminDb TEXT NOT NULL, " +
                            "tenantContableDb TEXT NOT NULL, " +
                            "tenantNominaDb TEXT NOT NULL, " +
                            "tenantLabel TEXT NOT NULL, " +
                            "totalMinor INTEGER NOT NULL, " +
                            "currencyCode TEXT NOT NULL, " +
                            "leasedUntil INTEGER NOT NULL, " +
                            "createdAt INTEGER NOT NULL, " +
                            "updatedAt INTEGER NOT NULL, " +
                            "PRIMARY KEY(id))",
                    )
                    db.execSQL(
                        "INSERT INTO pending_invoices_new (" +
                            "id, countryCode, payloadJson, localInvoiceNumber, total, clientName, " +
                            "status, retryCount, lastError, remoteInvoiceId, remoteInvoiceNumber, " +
                            "tenantId, tenantCompanyId, tenantAdminDb, tenantContableDb, tenantNominaDb, " +
                            "tenantLabel, totalMinor, currencyCode, leasedUntil, createdAt, updatedAt) " +
                            "SELECT " +
                            "id, countryCode, payloadJson, localInvoiceNumber, total, clientName, " +
                            "status, retryCount, lastError, remoteInvoiceId, remoteInvoiceNumber, " +
                            "'', 0, '', '', '', '', " +
                            "CAST(ROUND(total * 100.0) AS INTEGER), 'USD', 0, " +
                            "createdAt, updatedAt " +
                            "FROM pending_invoices",
                    )
                    db.execSQL("DROP TABLE pending_invoices")
                    db.execSQL("ALTER TABLE pending_invoices_new RENAME TO pending_invoices")
                    db.execSQL("CREATE INDEX IF NOT EXISTS index_pending_invoices_tenantId ON pending_invoices(tenantId)")
                }
            }

        /**
         * Recovery migration for installations that already applied the LITE
         * `MIGRATION_13_14` whose columns ended up with unwanted `DEFAULT ''`
         * / `DEFAULT 0` clauses in `tenantId`, `tenantAdminDb`, `tenantLabel`,
         * `totalMinor`, `leasedUntil`, etc. Room's schema validator rejects
         * those because the Kotlin entities declare no `@ColumnInfo(defaultValue)`
         * and expect columns with NO default.
         *
         * Strategy: table rebuild to the exact `createSql` of schema 14 (and,
         * because v14 == v15 in terms of columns, to v15 as well). Data is
         * preserved verbatim via INSERT...SELECT; the only change is that
         * the rebuilt columns no longer carry a DEFAULT clause.
         */
        internal val MIGRATION_14_15 =
            object : Migration(14, 15) {
                // Secuencia SQL debe permanecer indivisible y auditable para migración Room.
                @Suppress("LongMethod")
                override fun migrate(db: SupportSQLiteDatabase) {
                    // Rebuild transaction_log stripping DEFAULT clauses.
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS transaction_log_v15 (" +
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
                            "fiscalNumber TEXT, " +
                            "printerSerial TEXT, " +
                            "fiscalConfirmationStatus TEXT NOT NULL, " +
                            "fiscalConfirmationRetryCount INTEGER NOT NULL, " +
                            "fiscalConfirmationNextAttemptAt INTEGER NOT NULL, " +
                            "fiscalConfirmationLeasedUntil INTEGER NOT NULL, " +
                            "gatewayCallbackStatus TEXT NOT NULL, " +
                            "gatewayCallbackRetryCount INTEGER NOT NULL, " +
                            "gatewayCallbackNextAttemptAt INTEGER NOT NULL, " +
                            "gatewayCallbackLeasedUntil INTEGER NOT NULL, " +
                            "gatewayRawResponse TEXT, " +
                            "gatewayResultCode TEXT, " +
                            "gatewayResultMessage TEXT, " +
                            "tenantId TEXT NOT NULL, " +
                            "tenantCompanyId INTEGER NOT NULL, " +
                            "tenantAdminDb TEXT NOT NULL, " +
                            "tenantContableDb TEXT NOT NULL, " +
                            "tenantNominaDb TEXT NOT NULL, " +
                            "tenantLabel TEXT NOT NULL, " +
                            "totalAmountMinor INTEGER NOT NULL, " +
                            "currencyCode TEXT NOT NULL, " +
                            "fiscalState TEXT NOT NULL, " +
                            "createdAt INTEGER NOT NULL, " +
                            "updatedAt INTEGER NOT NULL, " +
                            "PRIMARY KEY(clientCorrelationId))",
                    )
                    db.execSQL(
                        "INSERT INTO transaction_log_v15 SELECT " +
                            "clientCorrelationId, idCaja, idCajaSecuencia, totalAmount, currency, " +
                            "clientName, status, remoteInvoiceId, remoteInvoiceNumber, lastError, " +
                            "fiscalNumber, printerSerial, fiscalConfirmationStatus, " +
                            "fiscalConfirmationRetryCount, fiscalConfirmationNextAttemptAt, " +
                            "fiscalConfirmationLeasedUntil, gatewayCallbackStatus, " +
                            "gatewayCallbackRetryCount, gatewayCallbackNextAttemptAt, " +
                            "gatewayCallbackLeasedUntil, gatewayRawResponse, gatewayResultCode, " +
                            "gatewayResultMessage, tenantId, tenantCompanyId, tenantAdminDb, " +
                            "tenantContableDb, tenantNominaDb, tenantLabel, totalAmountMinor, " +
                            "currencyCode, fiscalState, createdAt, updatedAt " +
                            "FROM transaction_log",
                    )
                    db.execSQL("DROP TABLE transaction_log")
                    db.execSQL("ALTER TABLE transaction_log_v15 RENAME TO transaction_log")
                    db.execSQL("CREATE INDEX IF NOT EXISTS index_transaction_log_tenantId ON transaction_log(tenantId)")

                    // Rebuild pending_invoices stripping DEFAULT clauses.
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS pending_invoices_v15 (" +
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
                            "tenantId TEXT NOT NULL, " +
                            "tenantCompanyId INTEGER NOT NULL, " +
                            "tenantAdminDb TEXT NOT NULL, " +
                            "tenantContableDb TEXT NOT NULL, " +
                            "tenantNominaDb TEXT NOT NULL, " +
                            "tenantLabel TEXT NOT NULL, " +
                            "totalMinor INTEGER NOT NULL, " +
                            "currencyCode TEXT NOT NULL, " +
                            "leasedUntil INTEGER NOT NULL, " +
                            "createdAt INTEGER NOT NULL, " +
                            "updatedAt INTEGER NOT NULL, " +
                            "PRIMARY KEY(id))",
                    )
                    db.execSQL(
                        "INSERT INTO pending_invoices_v15 SELECT " +
                            "id, countryCode, payloadJson, localInvoiceNumber, total, clientName, " +
                            "status, retryCount, lastError, remoteInvoiceId, remoteInvoiceNumber, " +
                            "tenantId, tenantCompanyId, tenantAdminDb, tenantContableDb, tenantNominaDb, " +
                            "tenantLabel, totalMinor, currencyCode, leasedUntil, createdAt, updatedAt " +
                            "FROM pending_invoices",
                    )
                    db.execSQL("DROP TABLE pending_invoices")
                    db.execSQL("ALTER TABLE pending_invoices_v15 RENAME TO pending_invoices")
                    db.execSQL("CREATE INDEX IF NOT EXISTS index_pending_invoices_tenantId ON pending_invoices(tenantId)")
                }
            }

        internal val MIGRATION_15_16 =
            object : Migration(15, 16) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE clients ADD COLUMN permiteCredito INTEGER NOT NULL DEFAULT 0")
                    db.execSQL("ALTER TABLE clients ADD COLUMN diasCredito INTEGER NOT NULL DEFAULT 0")
                }
            }

        internal val MIGRATION_16_17 =
            object : Migration(16, 17) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "CREATE TABLE draft_invoices_v17 (" +
                            "id TEXT NOT NULL, " +
                            "clientId TEXT, " +
                            "clientFirstName TEXT, " +
                            "clientLastName TEXT, " +
                            "sellerId INTEGER NOT NULL, " +
                            "sellerName TEXT, " +
                            "itemsJson TEXT NOT NULL, " +
                            "total REAL NOT NULL, " +
                            "itemCount INTEGER NOT NULL, " +
                            "createdAt INTEGER NOT NULL, " +
                            "subtotalGross REAL NOT NULL, " +
                            "itemDiscounts REAL NOT NULL, " +
                            "subtotalNet REAL NOT NULL, " +
                            "tax REAL NOT NULL, " +
                            "PRIMARY KEY(id))",
                    )
                    db.execSQL(
                        "INSERT INTO draft_invoices_v17 (" +
                            "id, clientId, clientFirstName, clientLastName, sellerId, sellerName, " +
                            "itemsJson, total, itemCount, createdAt, subtotalGross, itemDiscounts, subtotalNet, tax) " +
                            "SELECT id, clientId, clientFirstName, clientLastName, sellerId, sellerName, " +
                            "itemsJson, total, itemCount, createdAt, total, 0.0, total, 0.0 " +
                            "FROM draft_invoices",
                    )
                    db.execSQL("DROP TABLE draft_invoices")
                    db.execSQL("ALTER TABLE draft_invoices_v17 RENAME TO draft_invoices")
                }
            }

        /**
         * MONEY-001 completion (FASE 14 / TASK-150): `draft_invoices` y
         * `pending_invoices` convergen a persistencia canónica minor-units.
         *
         * - draft_invoices: las columnas legadas REAL (`total`,
         *   `subtotalGross`, `itemDiscounts`, `subtotalNet`, `tax`) se
         *   sustituyen por sus contrapartes `*Minor` INTEGER + `currencyCode`.
         *   Backfill con `CAST(ROUND(x * 100.0) AS INTEGER)` — misma convención
         *   del backfill 13→14 (round-half-away-from-zero ≈ HALF_EVEN a escala
         *   POS de 2 decimales).
         * - pending_invoices: se elimina la columna legada `total REAL`
         *   (escrita pero jamás leída); `totalMinor`/`currencyCode` (v14)
         *   permanecen como canónicos.
         *
         * Rebuild de tabla en lugar de ALTER: Room valida el schema
         * post-migración contra el 18.json exportado y las entidades no
         * declaran `@ColumnInfo(defaultValue)`, así que las columnas no deben
         * llevar cláusula DEFAULT (misma razón documentada en 13→14).
         */
        internal val MIGRATION_17_18 =
            object : Migration(17, 18) {
                // Secuencia SQL debe permanecer indivisible y auditable para migración Room.
                @Suppress("LongMethod")
                override fun migrate(db: SupportSQLiteDatabase) {
                    // ============================================================
                    // draft_invoices: rebuild reemplazando REAL -> minor-units.
                    // ============================================================
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS draft_invoices_v18 (" +
                            "id TEXT NOT NULL, " +
                            "clientId TEXT, " +
                            "clientFirstName TEXT, " +
                            "clientLastName TEXT, " +
                            "sellerId INTEGER NOT NULL, " +
                            "sellerName TEXT, " +
                            "itemsJson TEXT NOT NULL, " +
                            "totalMinor INTEGER NOT NULL, " +
                            "itemCount INTEGER NOT NULL, " +
                            "createdAt INTEGER NOT NULL, " +
                            "subtotalGrossMinor INTEGER NOT NULL, " +
                            "itemDiscountsMinor INTEGER NOT NULL, " +
                            "subtotalNetMinor INTEGER NOT NULL, " +
                            "taxMinor INTEGER NOT NULL, " +
                            "currencyCode TEXT NOT NULL, " +
                            "PRIMARY KEY(id))",
                    )
                    db.execSQL(
                        "INSERT INTO draft_invoices_v18 (" +
                            "id, clientId, clientFirstName, clientLastName, sellerId, sellerName, " +
                            "itemsJson, totalMinor, itemCount, createdAt, subtotalGrossMinor, " +
                            "itemDiscountsMinor, subtotalNetMinor, taxMinor, currencyCode) " +
                            "SELECT " +
                            "id, clientId, clientFirstName, clientLastName, sellerId, sellerName, " +
                            "itemsJson, CAST(ROUND(total * 100.0) AS INTEGER), itemCount, createdAt, " +
                            "CAST(ROUND(subtotalGross * 100.0) AS INTEGER), " +
                            "CAST(ROUND(itemDiscounts * 100.0) AS INTEGER), " +
                            "CAST(ROUND(subtotalNet * 100.0) AS INTEGER), " +
                            "CAST(ROUND(tax * 100.0) AS INTEGER), " +
                            "'USD' " +
                            "FROM draft_invoices",
                    )
                    db.execSQL("DROP TABLE draft_invoices")
                    db.execSQL("ALTER TABLE draft_invoices_v18 RENAME TO draft_invoices")

                    // ============================================================
                    // pending_invoices: rebuild eliminando la columna legada
                    // `total`; el resto de columnas v17 se preserva verbatim.
                    // ============================================================
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS pending_invoices_v18 (" +
                            "id TEXT NOT NULL, " +
                            "countryCode TEXT NOT NULL, " +
                            "payloadJson TEXT NOT NULL, " +
                            "localInvoiceNumber TEXT NOT NULL, " +
                            "clientName TEXT NOT NULL, " +
                            "status TEXT NOT NULL, " +
                            "retryCount INTEGER NOT NULL, " +
                            "lastError TEXT, " +
                            "remoteInvoiceId TEXT, " +
                            "remoteInvoiceNumber TEXT, " +
                            "tenantId TEXT NOT NULL, " +
                            "tenantCompanyId INTEGER NOT NULL, " +
                            "tenantAdminDb TEXT NOT NULL, " +
                            "tenantContableDb TEXT NOT NULL, " +
                            "tenantNominaDb TEXT NOT NULL, " +
                            "tenantLabel TEXT NOT NULL, " +
                            "totalMinor INTEGER NOT NULL, " +
                            "currencyCode TEXT NOT NULL, " +
                            "leasedUntil INTEGER NOT NULL, " +
                            "createdAt INTEGER NOT NULL, " +
                            "updatedAt INTEGER NOT NULL, " +
                            "PRIMARY KEY(id))",
                    )
                    db.execSQL(
                        "INSERT INTO pending_invoices_v18 (" +
                            "id, countryCode, payloadJson, localInvoiceNumber, clientName, " +
                            "status, retryCount, lastError, remoteInvoiceId, remoteInvoiceNumber, " +
                            "tenantId, tenantCompanyId, tenantAdminDb, tenantContableDb, tenantNominaDb, " +
                            "tenantLabel, totalMinor, currencyCode, leasedUntil, createdAt, updatedAt) " +
                            "SELECT " +
                            "id, countryCode, payloadJson, localInvoiceNumber, clientName, " +
                            "status, retryCount, lastError, remoteInvoiceId, remoteInvoiceNumber, " +
                            "tenantId, tenantCompanyId, tenantAdminDb, tenantContableDb, tenantNominaDb, " +
                            "tenantLabel, totalMinor, currencyCode, leasedUntil, createdAt, updatedAt " +
                            "FROM pending_invoices",
                    )
                    db.execSQL("DROP TABLE pending_invoices")
                    db.execSQL("ALTER TABLE pending_invoices_v18 RENAME TO pending_invoices")
                    db.execSQL("CREATE INDEX IF NOT EXISTS index_pending_invoices_tenantId ON pending_invoices(tenantId)")
                }
            }
        internal val MIGRATION_18_19 =
            object : Migration(18, 19) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE clients ADD COLUMN codTipoPrecio INTEGER NOT NULL DEFAULT 2")
                }
            }

        /**
         * v20 — Sync offline (ADR-007/008, PLAN §5.1):
         * `estatus` en products (D4), índices de barcode/descripción,
         * `sync_state` (checkpoint transaccional), `payment_methods` +
         * `caja_payment_methods` (formas de pago a Room) y `caja_sesion`
         * (sesión persistente que sobrevive reinicios offline).
         */
        internal val MIGRATION_19_20 =
            object : Migration(19, 20) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE products ADD COLUMN estatus TEXT NOT NULL DEFAULT 'A'")
                    db.execSQL("ALTER TABLE clients ADD COLUMN idSucursal INTEGER")
                    db.execSQL("CREATE INDEX IF NOT EXISTS index_products_barcode1 ON products(barcode1)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS index_products_barcode2 ON products(barcode2)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS index_products_barcode3 ON products(barcode3)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS index_products_department ON products(department)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS index_products_description ON products(description)")
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS sync_state (" +
                            "tenantId TEXT NOT NULL, " +
                            "scope TEXT NOT NULL, " +
                            "cursor INTEGER NOT NULL DEFAULT 0, " +
                            "snapshotId INTEGER NOT NULL DEFAULT 0, " +
                            "afterId TEXT, " +
                            "status TEXT NOT NULL, " +
                            "updatedAt INTEGER NOT NULL, " +
                            "PRIMARY KEY(tenantId, scope)" +
                            ")",
                    )
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS payment_methods (" +
                            "idFormaPago INTEGER NOT NULL, " +
                            "siglas TEXT, " +
                            "codigo INTEGER, " +
                            "descripcion TEXT, " +
                            "idCajaTpConcepto INTEGER, " +
                            "cuentaContable TEXT, " +
                            "idCajaTpRegistro INTEGER, " +
                            "formaPagoFact TEXT, " +
                            "activo INTEGER NOT NULL, " +
                            "pos INTEGER NOT NULL, " +
                            "imagen TEXT NOT NULL, " +
                            "grupo INTEGER NOT NULL, " +
                            "orden INTEGER NOT NULL, " +
                            "idBancoCuenta INTEGER NOT NULL, " +
                            "idBancoOperacion INTEGER NOT NULL, " +
                            "tipoMoneda TEXT, " +
                            "PRIMARY KEY(idFormaPago)" +
                            ")",
                    )
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS caja_payment_methods (" +
                            "idCaja TEXT NOT NULL, " +
                            "idFormaPago INTEGER NOT NULL, " +
                            "activo INTEGER, " +
                            "PRIMARY KEY(idCaja, idFormaPago)" +
                            ")",
                    )
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS caja_sesion (" +
                            "localId TEXT NOT NULL, " +
                            "cajaId TEXT NOT NULL, " +
                            "serverSecuenciaId TEXT, " +
                            "estado TEXT NOT NULL, " +
                            "openedAt INTEGER NOT NULL, " +
                            "closedAt INTEGER, " +
                            "userId TEXT NOT NULL, " +
                            "tenantId TEXT NOT NULL, " +
                            "secuenciaJson TEXT NOT NULL DEFAULT '', " +
                            "PRIMARY KEY(localId)" +
                            ")",
                    )
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
                MIGRATION_13_14,
                MIGRATION_14_15,
                MIGRATION_15_16,
                MIGRATION_16_17,
                MIGRATION_17_18,
                MIGRATION_18_19,
                MIGRATION_19_20,
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
                        MIGRATION_13_14,
                        MIGRATION_14_15,
                        MIGRATION_15_16,
                        MIGRATION_16_17,
                        MIGRATION_17_18,
                        MIGRATION_18_19,
                        MIGRATION_19_20,
                    ).build()
                    .also { instance = it }
            }
    }
}
