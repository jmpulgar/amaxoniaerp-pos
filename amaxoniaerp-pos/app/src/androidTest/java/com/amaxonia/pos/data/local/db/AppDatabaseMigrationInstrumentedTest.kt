package com.amaxonia.pos.data.local.db

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppDatabaseMigrationInstrumentedTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val databaseNames = mutableListOf<String>()

    @After
    fun cleanDatabases() {
        databaseNames.forEach(context::deleteDatabase)
    }

    @Test
    fun everySupportedVersionMigratesToCurrentWithoutLosingSeedData() {
        (1 until CURRENT_VERSION).forEach { startVersion ->
            val name = "migration-$startVersion-$CURRENT_VERSION.db"
            databaseNames += name
            createDatabaseAtVersion(name, startVersion)

            val database =
                Room
                    .databaseBuilder(context, AppDatabase::class.java, name)
                    .addMigrations(*AppDatabase.ALL_MIGRATIONS)
                    .build()
            try {
                val sqlite = database.openHelper.writableDatabase
                sqlite.query("SELECT code, addressLevel1, clientTypeId FROM clients WHERE id = 'client-1'").use { cursor ->
                    assertTrue("client seed missing after migration from $startVersion", cursor.moveToFirst())
                    assertEquals("C001", cursor.getString(0))
                    assertEquals("", cursor.getString(1))
                    assertEquals(1, cursor.getInt(2))
                }
                sqlite.query("SELECT barcode2, isExempt, bulkQuantity, unitOrPackage FROM products WHERE id = 'product-1'").use { cursor ->
                    assertTrue("product seed missing after migration from $startVersion", cursor.moveToFirst())
                    assertEquals("", cursor.getString(0))
                    assertEquals(0, cursor.getInt(1))
                    assertEquals(1.0, cursor.getDouble(2), 0.0)
                    assertEquals("UNIDAD", cursor.getString(3))
                }
                listOf(
                    "countries",
                    "client_types",
                    "draft_invoices",
                    "pending_invoices",
                    "promociones",
                    "promocion_detalles",
                    "client_sucursales",
                ).forEach { table -> assertTrue("table $table missing", tableExists(sqlite, table)) }
                // Auditoría ítem ? fase 0 / v14: assert the new columns exist
                // post-migration for ANY start version under 14. (For versions
                // 1-13 the migration pumps through 13_14 before reaching 14.)
                assertV14TransactionLogColumnsExist(sqlite)
                assertV14PendingInvoicesColumnsExist(sqlite)
                assertV17DraftFinancialColumnsExist(sqlite)
            } finally {
                database.close()
            }
        }
    }

    @Test
    fun migrationFromV15ToV16AddsCreditConfigurationWithLegacyDefaults() {
        val name = "migration-15-16-${System.nanoTime()}.db"
        databaseNames += name
        createDatabaseAtVersion(name, 15)

        val database =
            Room
                .databaseBuilder(context, AppDatabase::class.java, name)
                .addMigrations(*AppDatabase.ALL_MIGRATIONS)
                .build()
        try {
            database.openHelper.writableDatabase
                .query("SELECT permiteCredito, diasCredito FROM clients WHERE id = 'client-1'")
                .use { cursor ->
                    assertTrue("client seed missing after migration 15→16", cursor.moveToFirst())
                    assertEquals(0, cursor.getInt(0))
                    assertEquals(0, cursor.getInt(1))
                }
        } finally {
            database.close()
        }
    }

    /**
     * Dedicated regression for MIGRATION_13_14 (auditoría fase 0). Seeds a
     * `transaction_log` and `pending_invoices` row in the v13 legacy shape
     * (Double totalAmount, legacy `fiscalConfirmationStatus`) and verifies
     * post-migration:
     *  - new columns exist with correct nullable types,
     *  - `totalAmountMinor` is `ROUND(totalAmount*100)` (cast to INTEGER),
     *  - `fiscalState` is derived per the documented CASE mapping,
     *  - `tenantId` defaults to '' (legado: fila no-etiquetada queda pendiente),
     *  - tenantId indexes exist on both tables.
     */
    @Test
    fun migrationFromV13ToV14BackfillsTenantMinorAndFiscalState() {
        val name = "migration-13-$CURRENT_VERSION-v14.db"
        databaseNames += name
        createDatabaseAtVersion(name, CURRENT_VERSION - 1)

        // Seed a v13 transaction_log row with legacy Double total + legacy
        // fiscalConfirmationStatus='PENDING' (maps to PRINTED_PENDING_CONFIRM).
        val sqlite =
            FrameworkSQLiteOpenHelperFactory()
                .create(
                    SupportSQLiteOpenHelper.Configuration
                        .builder(context)
                        .name(name)
                        .callback(
                            object : SupportSQLiteOpenHelper.Callback(CURRENT_VERSION - 1) {
                                override fun onCreate(db: SupportSQLiteDatabase) = Unit

                                override fun onUpgrade(
                                    db: SupportSQLiteDatabase,
                                    oldVersion: Int,
                                    newVersion: Int,
                                ) = Unit
                            },
                        ).build(),
                ).writableDatabase
        sqlite.execSQL(
            "INSERT INTO transaction_log (clientCorrelationId, idCaja, idCajaSecuencia, totalAmount, " +
                "currency, clientName, status, fiscalConfirmationStatus, fiscalConfirmationRetryCount, " +
                "fiscalConfirmationNextAttemptAt, fiscalConfirmationLeasedUntil, gatewayCallbackStatus, " +
                "gatewayCallbackRetryCount, gatewayCallbackNextAttemptAt, gatewayCallbackLeasedUntil, " +
                "createdAt, updatedAt) VALUES ('tlog-1','caja','OFFLINE-caja',10.50,'USD','CLIENT','SENDING', " +
                "'PENDING', 0, 0, 0, '', 0, 0, 0, 1000, 1000)",
        )
        sqlite.execSQL(
            "INSERT INTO pending_invoices (id, countryCode, payloadJson, localInvoiceNumber, total, " +
                "clientName, status, retryCount, lastError, createdAt, updatedAt) VALUES " +
                "('inv-1','VE','{}','OFF-1',5.25,'CLIENT','PENDING',0,NULL,2000,2000)",
        )
        sqlite.close()

        val database =
            Room
                .databaseBuilder(context, AppDatabase::class.java, name)
                .addMigrations(*AppDatabase.ALL_MIGRATIONS)
                .build()
        try {
            val db = database.openHelper.writableDatabase
            db
                .query(
                    "SELECT tenantId, totalAmountMinor, currencyCode, fiscalState FROM transaction_log WHERE clientCorrelationId = 'tlog-1'",
                ).use { cursor ->
                    assertTrue("tlog-1 missing after 13→14 migration", cursor.moveToFirst())
                    assertEquals("", cursor.getString(0))
                    // ROUND(10.50 * 100.0) AS INTEGER = 1050
                    assertEquals(1050, cursor.getInt(1))
                    assertEquals("USD", cursor.getString(2))
                    // 'PENDING' was mapped to PRINTED_PENDING_CONFIRM (fase-0 spec §11.4)
                    assertEquals("PRINTED_PENDING_CONFIRM", cursor.getString(3))
                }
            db
                .query(
                    "SELECT tenantId, totalMinor, leasedUntil FROM pending_invoices WHERE id = 'inv-1'",
                ).use { cursor ->
                    assertTrue("inv-1 missing after 13→14 migration", cursor.moveToFirst())
                    assertEquals("", cursor.getString(0))
                    // ROUND(5.25 * 100.0) AS INTEGER = 525
                    assertEquals(525, cursor.getInt(1))
                    assertEquals(0, cursor.getInt(2))
                }
            assertTrue(
                "index_transaction_log_tenantId missing",
                indexExists(db, "index_transaction_log_tenantId"),
            )
            assertTrue(
                "index_pending_invoices_tenantId missing",
                indexExists(db, "index_pending_invoices_tenantId"),
            )
        } finally {
            database.close()
        }
    }

    private fun assertV14TransactionLogColumnsExist(db: SupportSQLiteDatabase) {
        db
            .query(
                "SELECT tenantId, tenantCompanyId, tenantAdminDb, tenantContableDb, tenantNominaDb, tenantLabel, totalAmountMinor, currencyCode, fiscalState, gatewayResultCode, gatewayResultMessage FROM transaction_log LIMIT 0",
            ).use { cursor ->
                // Column resolution happens at compile time; just ensure it runs.
                cursor.columnCount
            }
    }

    private fun assertV14PendingInvoicesColumnsExist(db: SupportSQLiteDatabase) {
        db
            .query(
                "SELECT tenantId, tenantCompanyId, tenantAdminDb, tenantContableDb, tenantNominaDb, tenantLabel, totalMinor, currencyCode, leasedUntil FROM pending_invoices LIMIT 0",
            ).use { cursor ->
                cursor.columnCount
            }
    }

    private fun assertV17DraftFinancialColumnsExist(db: SupportSQLiteDatabase) {
        db
            .query(
                "SELECT subtotalGross, itemDiscounts, subtotalNet, tax FROM draft_invoices LIMIT 0",
            ).use { cursor ->
                cursor.columnCount
            }
    }

    private fun indexExists(
        db: SupportSQLiteDatabase,
        indexName: String,
    ): Boolean =
        db
            .query(
                "SELECT 1 FROM sqlite_master WHERE type = 'index' AND name = ?",
                arrayOf(indexName),
            ).use { cursor -> cursor.moveToFirst() }

    private fun createDatabaseAtVersion(
        name: String,
        version: Int,
    ) {
        val callback =
            object : SupportSQLiteOpenHelper.Callback(version) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    createVersionOneSchema(db)
                    seedVersionOneData(db)
                    AppDatabase.ALL_MIGRATIONS
                        .filter { migration -> migration.endVersion <= version }
                        .forEach { migration -> migration.migrate(db) }
                }

                override fun onUpgrade(
                    db: SupportSQLiteDatabase,
                    oldVersion: Int,
                    newVersion: Int,
                ) = Unit
            }
        val configuration =
            SupportSQLiteOpenHelper.Configuration
                .builder(context)
                .name(name)
                .callback(callback)
                .build()
        val helper = FrameworkSQLiteOpenHelperFactory().create(configuration)
        try {
            helper.writableDatabase
        } finally {
            helper.close()
        }
    }

    private fun createVersionOneSchema(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS clients (" +
                "id TEXT NOT NULL, code TEXT NOT NULL, identification TEXT NOT NULL, " +
                "dv TEXT NOT NULL, name TEXT NOT NULL, lastName TEXT NOT NULL, " +
                "address TEXT NOT NULL, phone TEXT NOT NULL, email TEXT NOT NULL, " +
                "status INTEGER NOT NULL, taxpayerTypeId INTEGER NOT NULL, " +
                "countryId INTEGER NOT NULL, PRIMARY KEY(id))",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS products (" +
                "id TEXT NOT NULL, code TEXT NOT NULL, description TEXT NOT NULL, " +
                "reference TEXT NOT NULL, barcode1 TEXT NOT NULL, department INTEGER NOT NULL, " +
                "taxRate REAL NOT NULL, costActual REAL NOT NULL, prices TEXT NOT NULL, " +
                "PRIMARY KEY(id))",
        )
    }

    private fun seedVersionOneData(db: SupportSQLiteDatabase) {
        db.execSQL(
            "INSERT INTO clients (id, code, identification, dv, name, lastName, address, " +
                "phone, email, status, taxpayerTypeId, countryId) " +
                "VALUES ('client-1', 'C001', 'ID001', '', 'CLIENT', 'TEST', 'ADDRESS', '0000', 'test@example.invalid', 1, 1, 1)",
        )
        db.execSQL(
            "INSERT INTO products (id, code, description, reference, barcode1, department, taxRate, costActual, prices) " +
                "VALUES ('product-1', 'P001', 'PRODUCT', 'REF', 'BAR', 1, 16.0, 5.0, '[]')",
        )
    }

    private fun tableExists(
        db: SupportSQLiteDatabase,
        table: String,
    ): Boolean =
        db
            .query(
                "SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ?",
                arrayOf(table),
            ).use { cursor -> cursor.moveToFirst() }

    /**
     * Parametric migration regression for the four flagged legacy start
     * versions (auditoria fase 0 / front-prep). Each variant seeds rows
     * representative of a legacy install, then asserts the v14 invariants
     * (tenant defaults, minor-units backfill, fiscal state mapping, indexes)
     * hold after the chain runs.
     *
     * Idempotency: never uses fallbackToDestructiveMigration. Each start
     * version is materialized through the SAME migration chain that ships
     * in production.
     */
    @Test
    fun migrationV10ToV14PreservesSeedsAndBackfillsV14Columns() {
        // v10 schema: no transaction_log table yet. The migration chain
        // creates it in v11, extends it through v12/v13, then backfills
        // with the v14 columns. We assert a single synthetic offline sale
        // inserted post-v11 survives with the canonical fields.
        runLegacyToV14Scenario(
            scenarioName = "v10-to-v14",
            startVersion = 10,
            seedAtV11 = { db ->
                db.execSQL(
                    "INSERT INTO transaction_log (clientCorrelationId, idCaja, idCajaSecuencia, " +
                        "totalAmount, currency, clientName, status, remoteInvoiceId, remoteInvoiceNumber, " +
                        "lastError, createdAt, updatedAt) VALUES " +
                        "('v10-log','caja','seq','100.00','USD','CLIENT','SENDING',NULL,NULL,NULL,1,1)",
                )
            },
            expectedClientCorrelationId = "v10-log",
            expectedTotalAmountMinor = 10_000,
        )
    }

    @Test
    fun migrationV11ToV14BackfillsMinorAndFiscalStateFromLegacyColumns() {
        runLegacyToV14Scenario(
            scenarioName = "v11-to-v14",
            startVersion = 11,
            seedAtV11 = { db ->
                db.execSQL(
                    "INSERT INTO transaction_log (clientCorrelationId, idCaja, idCajaSecuencia, " +
                        "totalAmount, currency, clientName, status, remoteInvoiceId, remoteInvoiceNumber, " +
                        "lastError, createdAt, updatedAt) VALUES " +
                        "('v11-log','caja','seq','50.10','USD','CLIENT','CONFIRMED','rid','100',NULL,1,1)",
                )
            },
            expectedClientCorrelationId = "v11-log",
            expectedTotalAmountMinor = 5_010,
            expectedRemoteInvoiceId = "rid",
        )
    }

    @Test
    fun migrationV12ToV14BackfillsFiscalStateFromFiscalConfirmationStatus() {
        runLegacyToV14Scenario(
            scenarioName = "v12-to-v14",
            startVersion = 12,
            seedAtV12 = { db ->
                db.execSQL(
                    "INSERT INTO transaction_log (clientCorrelationId, idCaja, idCajaSecuencia, totalAmount, " +
                        "currency, clientName, status, remoteInvoiceId, remoteInvoiceNumber, lastError, " +
                        "fiscalNumber, printerSerial, fiscalConfirmationStatus, fiscalConfirmationRetryCount, " +
                        "fiscalConfirmationNextAttemptAt, fiscalConfirmationLeasedUntil, createdAt, updatedAt) " +
                        "VALUES ('v12-log','caja','seq','75.00','USD','CLIENT','CONFIRMED','rid','100',NULL," +
                        "'FISC-001','PRN-001','CONFIRMED',0,0,0,1,1)",
                )
            },
            expectedClientCorrelationId = "v12-log",
            expectedTotalAmountMinor = 7_500,
            expectedFiscalState = "CONFIRMED",
        )
    }

    @Test
    fun migrationV13ToV14BackfillsGatewaySnapshotAndLeaseColumns() {
        runLegacyToV14Scenario(
            scenarioName = "v13-to-v14",
            startVersion = 13,
            seedAtV13 = { db ->
                db.execSQL(
                    "INSERT INTO transaction_log (clientCorrelationId, idCaja, idCajaSecuencia, totalAmount, " +
                        "currency, clientName, status, remoteInvoiceId, remoteInvoiceNumber, lastError, " +
                        "fiscalNumber, printerSerial, fiscalConfirmationStatus, fiscalConfirmationRetryCount, " +
                        "fiscalConfirmationNextAttemptAt, fiscalConfirmationLeasedUntil, " +
                        "gatewayCallbackStatus, gatewayCallbackRetryCount, gatewayCallbackNextAttemptAt, " +
                        "gatewayCallbackLeasedUntil, gatewayRawResponse, createdAt, updatedAt) VALUES " +
                        "('v13-log','caja','seq','12.34','VES','CLIENT','CONFIRMED','rid','100',NULL," +
                        "'FISC','PRN','CONFIRMED',0,0,0,'RESOLVED',1,2,3,'{\"code\":\"00\"}',1,1)",
                )
                db.execSQL(
                    "INSERT INTO pending_invoices (id, countryCode, payloadJson, localInvoiceNumber, " +
                        "total, clientName, status, retryCount, lastError, createdAt, updatedAt) VALUES " +
                        "('v13-pending','VE','{}','INV-1','9.99','CLIENT','PENDING',0,NULL,1,1)",
                )
            },
            expectedClientCorrelationId = "v13-log",
            expectedTotalAmountMinor = 1_234,
            expectedCurrencyCode = "VES",
            expectedGatewayRawResponse = "{\"code\":\"00\"}",
            expectedPendingInvoiceId = "v13-pending",
            expectedPendingTotalMinor = 999,
        )
    }

    /**
     * Helper that materializes a DB at [startVersion], applies the shipped
     * migration chain up to 14, lets the caller seed rows at the latest
     * schema the chain reaches BEFORE the canonical v14 backfill, then runs
     * the full v14 invariants.
     */
    private fun runLegacyToV14Scenario(
        scenarioName: String,
        startVersion: Int,
        seedAtV11: ((SupportSQLiteDatabase) -> Unit)? = null,
        seedAtV12: ((SupportSQLiteDatabase) -> Unit)? = null,
        seedAtV13: ((SupportSQLiteDatabase) -> Unit)? = null,
        expectedClientCorrelationId: String,
        expectedTotalAmountMinor: Int,
        expectedCurrencyCode: String = "USD",
        expectedFiscalState: String = "NOT_APPLICABLE",
        expectedGatewayRawResponse: String? = null,
        expectedRemoteInvoiceId: String? = null,
        expectedPendingInvoiceId: String? = null,
        expectedPendingTotalMinor: Int? = null,
    ) {
        val name = "scenario-$scenarioName-${System.nanoTime()}.db"
        databaseNames += name
        createDatabaseAtVersion(name, startVersion)

        // Move the DB forward through every migration up to 13 manually so
        // the seed matches the legacy schema the customer actually had. We
        // intentionally pass a hot SupportSQLiteOpenHelper that does nothing
        // on upgrade (we call .migrate(db) ourselves).
        val pre14Db =
            FrameworkSQLiteOpenHelperFactory()
                .create(
                    SupportSQLiteOpenHelper.Configuration
                        .builder(context)
                        .name(name)
                        .callback(
                            object : SupportSQLiteOpenHelper.Callback(13) {
                                override fun onCreate(db: SupportSQLiteDatabase) = Unit

                                override fun onUpgrade(
                                    db: SupportSQLiteDatabase,
                                    oldVersion: Int,
                                    newVersion: Int,
                                ) = Unit
                            },
                        ).build(),
                ).writableDatabase
        AppDatabase.ALL_MIGRATIONS
            .filter { it.startVersion in startVersion until CURRENT_VERSION }
            .sortedBy { it.startVersion }
            .forEach { migration -> migration.migrate(pre14Db) }
        when {
            seedAtV13 != null -> seedAtV13(pre14Db)
            seedAtV12 != null -> seedAtV12(pre14Db)
            seedAtV11 != null -> seedAtV11(pre14Db)
        }
        pre14Db.close()

        val database =
            Room
                .databaseBuilder(context, AppDatabase::class.java, name)
                .addMigrations(*AppDatabase.ALL_MIGRATIONS)
                .build()
        try {
            val db = database.openHelper.writableDatabase
            db
                .query(
                    "SELECT tenantId, totalAmountMinor, currencyCode, fiscalState, gatewayRawResponse, remoteInvoiceId " +
                        "FROM transaction_log WHERE clientCorrelationId = ?",
                    arrayOf(expectedClientCorrelationId),
                ).use { cursor ->
                    assertTrue("$scenarioName: seed row missing after migration", cursor.moveToFirst())
                    assertEquals("$scenarioName: tenantId must default to ''", "", cursor.getString(0))
                    assertEquals(
                        "$scenarioName: totalAmountMinor backfill",
                        expectedTotalAmountMinor,
                        cursor.getInt(1),
                    )
                    assertEquals(
                        "$scenarioName: currencyCode",
                        expectedCurrencyCode,
                        cursor.getString(2),
                    )
                    assertEquals(
                        "$scenarioName: fiscalState mapping",
                        expectedFiscalState,
                        cursor.getString(3),
                    )
                    if (expectedGatewayRawResponse != null) {
                        assertEquals(
                            "$scenarioName: gatewayRawResponse preserved",
                            expectedGatewayRawResponse,
                            cursor.getString(4),
                        )
                    }
                    if (expectedRemoteInvoiceId != null) {
                        assertEquals(
                            "$scenarioName: remoteInvoiceId preserved",
                            expectedRemoteInvoiceId,
                            cursor.getString(5),
                        )
                    }
                }

            if (expectedPendingInvoiceId != null) {
                db
                    .query(
                        "SELECT tenantId, totalMinor, leasedUntil FROM pending_invoices WHERE id = ?",
                        arrayOf(expectedPendingInvoiceId),
                    ).use { cursor ->
                        assertTrue("$scenarioName: pending_invoice missing", cursor.moveToFirst())
                        assertEquals("", cursor.getString(0))
                        assertEquals(
                            "$scenarioName: pending totalMinor backfill",
                            expectedPendingTotalMinor,
                            cursor.getInt(1),
                        )
                        assertEquals(
                            "$scenarioName: leasedUntil default",
                            0,
                            cursor.getInt(2),
                        )
                    }
            }

            assertTrue(
                "$scenarioName: index_transaction_log_tenantId missing",
                indexExists(db, "index_transaction_log_tenantId"),
            )
            assertTrue(
                "$scenarioName: index_pending_invoices_tenantId missing",
                indexExists(db, "index_pending_invoices_tenantId"),
            )
            assertV14TransactionLogColumnsExist(db)
            assertV14PendingInvoicesColumnsExist(db)
        } finally {
            database.close()
        }
    }

    private companion object {
        const val CURRENT_VERSION = 14
    }
}
