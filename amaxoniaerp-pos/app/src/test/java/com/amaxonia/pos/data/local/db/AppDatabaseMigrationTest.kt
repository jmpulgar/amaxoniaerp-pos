package com.amaxonia.pos.data.local.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Valida cada migración soportada con schema exportado (10 → 17) contra el schema
 * ACTUAL de [AppDatabase]: para cada versión inicial soportada se reconstruye la
 * base legacy desde `schemas/` (tablas + identity hash en `room_master_table`) y se
 * abre con Room, que ejecuta las migraciones restantes y valida el resultado contra
 * el schema compilado actual. Los schemas 1-9 no fueron exportados históricamente,
 * por lo que las migraciones 1→10 no son verificables de esta forma.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppDatabaseMigrationTest {
    private lateinit var context: Context
    private val usedDatabases = mutableListOf<String>()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @After
    fun tearDown() {
        usedDatabases.forEach { context.deleteDatabase(it) }
    }

    @Test
    fun `base creada en v10 migra al schema actual`() = migrateFrom(10)

    @Test
    fun `base creada en v11 migra al schema actual`() = migrateFrom(11)

    @Test
    fun `base creada en v12 migra al schema actual`() = migrateFrom(12)

    @Test
    fun `base creada en v13 migra al schema actual`() = migrateFrom(13)

    @Test
    fun `base creada en v14 migra al schema actual`() = migrateFrom(14)

    @Test
    fun `base creada en v15 migra al schema actual`() = migrateFrom(15)

    @Test
    fun `base creada en v16 migra al schema actual`() = migrateFrom(16)

    @Test
    fun `base creada en v17 migra al schema actual`() = migrateFrom(17)

    @Test
    fun `migracion 17 a 18 rellena minor units de draft_invoices y elimina total legado de pending_invoices`() {
        val db = openMigratedFrom(17)
        try {
            val sqlite = db.openHelper.readableDatabase
            // draft_invoices: columnas REAL legadas fuera, minor-units dentro.
            sqlite
                .query(
                    "SELECT subtotalGrossMinor, itemDiscountsMinor, subtotalNetMinor, taxMinor, currencyCode FROM draft_invoices LIMIT 0",
                ).use { cursor ->
                    assertEquals(5, cursor.columnCount)
                }
            assertFalse("columna legada 'total' debe desaparecer de draft_invoices", sqlite.hasColumn("draft_invoices", "total"))
            // pending_invoices: la columna legada 'total' se elimina; minor queda.
            sqlite
                .query(
                    "SELECT totalMinor, currencyCode FROM pending_invoices LIMIT 0",
                ).use { cursor ->
                    assertEquals(2, cursor.columnCount)
                }
            assertFalse("columna legada 'total' debe desaparecer de pending_invoices", sqlite.hasColumn("pending_invoices", "total"))

            val dao = db.draftInvoiceDao()
            runBlocking {
                dao.insert(
                    DraftInvoiceEntity(
                        id = "d-1",
                        itemsJson = "[]",
                        totalMinor = 1_500L,
                        itemCount = 3,
                        subtotalGrossMinor = 1_400L,
                        itemDiscountsMinor = 25L,
                        subtotalNetMinor = 1_375L,
                        taxMinor = 125L,
                    ),
                )
                val saved = dao.getAll().single()
                assertEquals(1_500L, saved.totalMinor)
                assertEquals(1_375L, saved.subtotalNetMinor)
            }
        } finally {
            db.close()
        }
    }

    @Test
    fun `tras migrar desde v10 los DAOs operan sobre el schema actual`() {
        val db = openMigratedFrom(10)
        try {
            val dao = db.transactionLogDao()
            runBlocking {
                assertTrue(dao.findFiscalConfirmableForTenant("t", now = 0L).isEmpty())
                assertTrue(db.pendingInvoiceDao().getPendingForTenant("t").isEmpty())
                assertTrue(db.clientDao().getPaged(limit = 10, offset = 0).isEmpty())
            }
        } finally {
            db.close()
        }
    }

    private fun migrateFrom(startVersion: Int) {
        val db = openMigratedFrom(startVersion)
        try {
            assertEquals(SCHEMA_VERSION, db.openHelper.readableDatabase.version)
        } finally {
            db.close()
        }
    }

    private fun androidx.sqlite.db.SupportSQLiteDatabase.hasColumn(
        table: String,
        column: String,
    ): Boolean {
        var found = false
        query("PRAGMA table_info($table)").use { cursor ->
            val nameIndex = cursor.getColumnIndex("name")
            while (cursor.moveToNext()) {
                if (cursor.getString(nameIndex) == column) found = true
            }
        }
        return found
    }

    private fun openMigratedFrom(startVersion: Int): AppDatabase {
        val name = "migration-from-$startVersion.db"
        usedDatabases += name
        createLegacyDatabase(name, startVersion)
        val db =
            Room
                .databaseBuilder(context, AppDatabase::class.java, name)
                .addMigrations(*AppDatabase.ALL_MIGRATIONS)
                .allowMainThreadQueries()
                .build()
        runBlocking { db.openHelper.writableDatabase }
        return db
    }

    /** Reconstruye la base en `version` ejecutando el DDL exportado en `schemas/`. */
    private fun createLegacyDatabase(
        name: String,
        version: Int,
    ) {
        val schemaFile = File(schemaDir(), "${AppDatabase::class.java.name}/$version.json")
        assertTrue("schema exportado no encontrado: ${schemaFile.absolutePath}", schemaFile.isFile)

        val database = JSONObject(schemaFile.readText()).getJSONObject("database")
        val identityHash = database.getString("identityHash")
        assertEquals(version, database.getInt("version"))

        context.deleteDatabase(name)
        val path = context.getDatabasePath(name)
        path.parentFile?.mkdirs()
        val sqlite = SQLiteDatabase.openOrCreateDatabase(path, null)
        try {
            sqlite.beginTransaction()
            try {
                val entities = database.getJSONArray("entities")
                for (i in 0 until entities.length()) {
                    executeEntityDdl(sqlite, entities.getJSONObject(i))
                }
                sqlite.execSQL(
                    "CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER NOT NULL, identity_hash TEXT NOT NULL, PRIMARY KEY(id))",
                )
                sqlite.execSQL("INSERT OR REPLACE INTO room_master_table (id, identity_hash) VALUES(42, '$identityHash')")
                sqlite.setTransactionSuccessful()
            } finally {
                sqlite.endTransaction()
            }
            sqlite.version = version
        } finally {
            sqlite.close()
        }
        assertFalse(identityHash.isBlank())
    }

    private fun executeEntityDdl(
        sqlite: SQLiteDatabase,
        entity: JSONObject,
    ) {
        val tableName = entity.getString("tableName")
        val createSql = entity.getString("createSql").replace("\${TABLE_NAME}", tableName)
        sqlite.execSQL(createSql)
        if (entity.has("indices")) {
            val indices = entity.getJSONArray("indices")
            for (j in 0 until indices.length()) {
                val indexSql =
                    indices
                        .getJSONObject(j)
                        .getString("createSql")
                        .replace("\${TABLE_NAME}", tableName)
                sqlite.execSQL(indexSql)
            }
        }
    }

    private fun schemaDir(): File {
        val candidates = listOf(File("schemas"), File("app/schemas"), File("../schemas"))
        val found = candidates.firstOrNull { it.isDirectory }
        assertNotNull("directorio schemas/ no encontrado desde ${File(".").absolutePath}", found)
        return found!!
    }

    private companion object {
        const val SCHEMA_VERSION = 18
    }
}
