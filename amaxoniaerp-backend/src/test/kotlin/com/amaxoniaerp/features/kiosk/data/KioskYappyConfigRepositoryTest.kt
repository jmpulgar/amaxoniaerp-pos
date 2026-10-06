package com.amaxoniaerp.features.kiosk.data

import com.amaxoniaerp.features.companies.data.ParametrosGeneralesTablePA
import com.amaxoniaerp.features.kiosk.domain.yappy.YappyQrType
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.transaction
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

/** `parametros_generales.yappy_tipo_qr` es opcional: se lee solo si la columna existe. */
class KioskYappyConfigRepositoryTest {
    private lateinit var dataSource: HikariDataSource
    private lateinit var database: Database
    private var nowMs = 1_000_000L
    private val repository = KioskYappyConfigRepository(clock = { nowMs })

    @BeforeTest
    fun setUp() {
        dataSource =
            HikariDataSource(
                HikariConfig().apply {
                    jdbcUrl = "jdbc:h2:mem:kiosk_yappy_cfg_${System.nanoTime()};MODE=MySQL;DB_CLOSE_DELAY=-1"
                    driverClassName = "org.h2.Driver"
                    maximumPoolSize = 1
                    isAutoCommit = false
                },
            )
        database = Database.connect(dataSource)
        transaction(database) {
            exec("CREATE TABLE parametros_generales (cod_empresa INT PRIMARY KEY, yappy_api_key VARCHAR(255) NULL)")
            exec("INSERT INTO parametros_generales (cod_empresa) VALUES (1)")
        }
    }

    @AfterTest
    fun tearDown() {
        dataSource.close()
    }

    private fun addColumn(value: String) {
        transaction(database) {
            exec("ALTER TABLE parametros_generales ADD COLUMN IF NOT EXISTS yappy_tipo_qr VARCHAR(3) NOT NULL DEFAULT 'DYN'")
            exec("UPDATE parametros_generales SET yappy_tipo_qr = '$value'")
        }
    }

    @Test
    fun `returns null when the optional column does not exist`() =
        runBlocking {
            assertNull(repository.findQrType(database))
            // Segunda llamada usa la caché y sigue sin fallar.
            assertNull(repository.findQrType(database))
        }

    @Test
    fun `reads the QR type when the column exists`() =
        runBlocking {
            addColumn("HYB")
            assertEquals(YappyQrType.HYB, repository.findQrType(database))

            transaction(database) { exec("UPDATE parametros_generales SET yappy_tipo_qr = 'dyn'") }
            assertEquals(YappyQrType.DYN, repository.findQrType(database))
        }

    @Test
    fun `empty or invalid values return null so the caller falls back`() =
        runBlocking {
            addColumn("XYZ")
            assertNull(repository.findQrType(database))

            transaction(database) { exec("UPDATE parametros_generales SET yappy_tipo_qr = ''") }
            assertNull(repository.findQrType(database))
        }

    @Test
    fun `absence is cached and rechecked later so a column added at runtime is detected`() =
        runBlocking {
            assertNull(repository.findQrType(database))
            addColumn("HYB")

            nowMs += 60_000L
            assertNull(repository.findQrType(database), "la ausencia se cachea")

            nowMs += 10 * 60_000L
            assertEquals(YappyQrType.HYB, repository.findQrType(database))
        }

    @Test
    fun `a dropped column after being detected degrades to null without throwing`() =
        runBlocking {
            addColumn("HYB")
            assertEquals(YappyQrType.HYB, repository.findQrType(database))

            transaction(database) { exec("ALTER TABLE parametros_generales DROP COLUMN yappy_tipo_qr") }
            assertNull(repository.findQrType(database))
            assertNull(repository.findQrType(database))
        }

    @Test
    fun `the general parametros mapping never selects yappy_tipo_qr`() {
        assertFalse(ParametrosGeneralesTablePA.columns.any { it.name.equals("yappy_tipo_qr", ignoreCase = true) })
        assertFalse(KioskYappyParametrosTable.columns.any { it.name.equals("yappy_tipo_qr", ignoreCase = true) })
        assertFalse(KioskParametrosTable.columns.any { it.name.equals("yappy_tipo_qr", ignoreCase = true) })
    }
}
