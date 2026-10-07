package com.amaxoniaerp.features.kiosk.data

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Esquema que reutiliza el kiosco: detección defensiva de tablas/columnas opcionales del tenant
 * y mapeo Exposed de `kiosco_pedido*` (tablas creadas por la migración del administrativo).
 */
class KioskSchemaTest {
    private lateinit var dataSource: HikariDataSource
    private lateinit var database: Database
    private var nowMs = 1_000_000L
    private val inspector = KioskSchemaInspector(clock = { nowMs })

    @BeforeTest
    fun setUp() {
        dataSource =
            HikariDataSource(
                HikariConfig().apply {
                    jdbcUrl = "jdbc:h2:mem:kiosk_schema_${System.nanoTime()};MODE=MySQL;DB_CLOSE_DELAY=-1"
                    driverClassName = "org.h2.Driver"
                    maximumPoolSize = 2
                    isAutoCommit = false
                },
            )
        database = Database.connect(dataSource)
        transaction(database) {
            exec("CREATE TABLE parametros_generales (cod_empresa INT PRIMARY KEY, default_cod_cliente_factura VARCHAR(80) NOT NULL)")
            exec("INSERT INTO parametros_generales (cod_empresa, default_cod_cliente_factura) VALUES (1, 'CF')")
        }
    }

    @AfterTest
    fun tearDown() {
        dataSource.close()
    }

    @Test
    fun `columns are detected in lowercase and a missing table has none`() =
        runBlocking {
            assertEquals(setOf("cod_empresa", "default_cod_cliente_factura"), inspector.columns(database, "parametros_generales"))
            assertEquals(emptySet(), inspector.columns(database, "kiosco_pedido"))
            assertFalse(inspector.hasKioskOrderTables(database))
            assertFalse(inspector.hasComboModel(database))
        }

    @Test
    fun `detection is cached for a short time so migrations are picked up without restart`() =
        runBlocking {
            assertFalse(inspector.hasKioskOrderTables(database))
            transaction(database) { SchemaUtils.create(KioskOrderTable, KioskOrderItemTable, KioskOrderItemModifierTable) }

            // Dentro del TTL se mantiene lo detectado.
            nowMs += KioskSchemaInspector.CACHE_TTL_MS - 1
            assertFalse(inspector.hasKioskOrderTables(database))

            nowMs += 2
            assertTrue(inspector.hasKioskOrderTables(database))
        }

    @Test
    fun `combo model requires the columns of the 2026-10-01 migration`() =
        runBlocking {
            transaction(database) {
                exec("CREATE TABLE item_combos (id_item_combo INT PRIMARY KEY, id_item INT NOT NULL, id_combo INT NOT NULL)")
                exec(
                    "CREATE TABLE grupos (id_grupo INT PRIMARY KEY, id_combo INT NOT NULL, nombre_grupo VARCHAR(255) NOT NULL, " +
                        "adiciona TINYINT(1) NOT NULL, maximo_veces INT NOT NULL)",
                )
                exec("CREATE TABLE grupo_items (id_grupo_item INT PRIMARY KEY, id_grupo INT NOT NULL, id_item INT NOT NULL)")
            }
            assertFalse(inspector.hasComboModel(database))

            transaction(database) {
                exec("ALTER TABLE grupos ADD COLUMN tipo VARCHAR(12) NOT NULL DEFAULT 'COMBO'")
                exec("ALTER TABLE grupos ADD COLUMN minimo INT NOT NULL DEFAULT 0")
                exec("ALTER TABLE grupo_items ADD COLUMN nombre VARCHAR(150) NULL")
                exec("ALTER TABLE grupo_items ADD COLUMN precio DECIMAL(12,2) NULL")
                exec("ALTER TABLE grupo_items ADD COLUMN cantidad DECIMAL(12,2) NOT NULL DEFAULT 1.00")
                exec("ALTER TABLE grupo_items ADD COLUMN por_defecto TINYINT(1) NOT NULL DEFAULT 0")
                exec("ALTER TABLE grupo_items ADD COLUMN orden INT NOT NULL DEFAULT 0")
            }
            inspector.invalidate(database)
            assertTrue(inspector.hasComboModel(database))
            // grupos.orden es opcional.
            assertEquals(KioskComboSchema(hasGroupOrder = false), KioskComboRepository(inspector).schema(database))
        }

    @Test
    fun `settings default every optional parametros_generales column`() =
        runBlocking {
            val settings = KioskSettingsRepository(inspector).load(database)

            assertEquals(KioskSettings(defaultCodClienteFactura = "CF"), settings)
        }

    @Test
    fun `settings read the optional columns that exist`() =
        runBlocking {
            transaction(database) {
                exec("ALTER TABLE parametros_generales ADD COLUMN kiosco_impresora_cocina_ip VARCHAR(45) NULL")
                exec("ALTER TABLE parametros_generales ADD COLUMN menu_1 VARCHAR(255) NULL")
                exec("ALTER TABLE parametros_generales ADD COLUMN clave_kiosko VARCHAR(255) NULL")
                exec("UPDATE parametros_generales SET kiosco_impresora_cocina_ip = '10.0.0.9', menu_1 = ' menu.jpg ', clave_kiosko = ''")
            }
            val settings = KioskSettingsRepository(inspector).load(database)

            assertEquals("10.0.0.9", settings.kitchenPrinterIp)
            assertEquals(listOf("menu.jpg"), settings.mediaFiles)
            assertEquals(null, settings.claveKiosko)
            assertEquals(KioskSettings.DEFAULT_DISPATCH, settings.dispatch)
        }

    @Test
    fun `kiosk order tables map to the admin migration definitions`() {
        transaction(database) {
            SchemaUtils.create(KioskOrderTable, KioskOrderItemTable, KioskOrderItemModifierTable)
            val now = LocalDateTime.now()
            KioskOrderTable.insert {
                it[id] = "ORDER-1"
                it[idDispositivo] = "CAJA-01"
                it[numeroPedidoDiario] = 1
                it[codigoPedido] = "K1-001"
                it[fecha] = LocalDate.now()
                it[estado] = "COTIZADO"
                it[modalidad] = "COMER_AQUI"
                it[idCliente] = "CF"
                it[total] = BigDecimal("5.8900")
                it[quoteExpiraEn] = now.plusMinutes(10)
                it[creadoEn] = now
                it[actualizadoEn] = now
            }
            KioskOrderItemTable.insert {
                it[idPedido] = "ORDER-1"
                it[linea] = 1
                it[idItem] = 101
                it[cantidad] = BigDecimal("1.0000")
                it[precioUnitario] = BigDecimal("5.5000")
            }
            KioskOrderItemModifierTable.insert {
                it[idPedido] = "ORDER-1"
                it[linea] = 1
                it[idModificador] = 121
                it[nombre] = "TOCINETA"
                it[precioAdicional] = BigDecimal("1.0000")
            }

            assertEquals("CAJA-01", KioskOrderTable.selectAll().single()[KioskOrderTable.idDispositivo].trim())
            assertEquals(121, KioskOrderItemModifierTable.selectAll().single()[KioskOrderItemModifierTable.idModificador])
        }
    }
}
