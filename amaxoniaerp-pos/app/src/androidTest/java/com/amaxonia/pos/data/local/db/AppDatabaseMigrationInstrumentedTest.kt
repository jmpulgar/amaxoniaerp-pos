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
            } finally {
                database.close()
            }
        }
    }

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

    private companion object {
        const val CURRENT_VERSION = 10
    }
}
