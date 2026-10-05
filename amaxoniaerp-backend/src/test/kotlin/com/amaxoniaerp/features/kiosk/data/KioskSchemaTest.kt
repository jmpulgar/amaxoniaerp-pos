package com.amaxoniaerp.features.kiosk.data

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class KioskSchemaTest {

    private lateinit var dataSource: HikariDataSource
    private lateinit var database: Database

    @BeforeTest
    fun setUp() {
        val config = HikariConfig().apply {
            jdbcUrl = "jdbc:h2:mem:kiosk_test_${System.nanoTime()};MODE=MySQL;DB_CLOSE_DELAY=-1"
            driverClassName = "org.h2.Driver"
            maximumPoolSize = 2
            isAutoCommit = false
        }
        dataSource = HikariDataSource(config)
        database = Database.connect(dataSource)
    }

    @AfterTest
    fun tearDown() {
        dataSource.close()
    }

    @Test
    fun `exposed schema creation and CRUD on kiosk and modifier tables`() {
        transaction(database) {
            // Parametros generales base table for test
            exec("CREATE TABLE IF NOT EXISTS parametros_generales (cod_empresa INT, default_cod_cliente_factura VARCHAR(80), clave_kiosko VARCHAR(255))")

            SchemaUtils.create(
                KioskDeviceTable,
                KioskMediaTable,
                ItemModifierGroupTable,
                ItemModifierTable,
                ItemModifierRelationTable,
                KioskOrderTable,
                KioskOrderItemTable,
                KioskOrderItemModifierTable,
            )

            // 1. Device
            val deviceId = UUID.randomUUID().toString()
            KioskDeviceTable.insert {
                it[id] = deviceId
                it[nombre] = "Kiosco Entrada 1"
                it[prefijoPedido] = "K1"
                it[idCaja] = "caja-1"
                it[idSucursal] = 1
                it[idAlmacen] = 1
                it[codVendedor] = 10
                it[idClienteGenerico] = "cli-gen"
                it[creadoEn] = LocalDateTime.now()
            }
            val devices = KioskDeviceTable.selectAll().toList()
            assertEquals(1, devices.size)
            assertEquals("K1", devices.first()[KioskDeviceTable.prefijoPedido])

            // 2. Media
            KioskMediaTable.insert {
                it[tipo] = "VIDEO"
                it[archivo] = "promo_burger.mp4"
                it[orden] = 1
                it[duracionSeg] = 15
                it[updatedAt] = LocalDateTime.now()
            }
            val media = KioskMediaTable.selectAll().toList()
            assertEquals(1, media.size)
            assertEquals("VIDEO", media.first()[KioskMediaTable.tipo])

            // 3. Modifier group & options
            val groupId = ItemModifierGroupTable.insert {
                it[nombre] = "Bebidas"
                it[minSeleccion] = 1
                it[maxSeleccion] = 1
                it[esObligatorio] = true
                it[esCombo] = true
            } get ItemModifierGroupTable.id

            val modId = ItemModifierTable.insert {
                it[idGrupo] = groupId
                it[nombre] = "Coca Cola Sin Azucar"
                it[precioAdicional] = BigDecimal("0.5000")
            } get ItemModifierTable.id

            ItemModifierRelationTable.insert {
                it[idItem] = 100
                it[idGrupo] = groupId
                it[orden] = 0
            }

            assertEquals(1, ItemModifierGroupTable.selectAll().count())
            assertEquals(1, ItemModifierTable.selectAll().count())
            assertEquals(1, ItemModifierRelationTable.selectAll().count())

            // 4. Kiosk order & lines
            val orderId = UUID.randomUUID().toString()
            KioskOrderTable.insert {
                it[id] = orderId
                it[idDispositivo] = deviceId
                it[numeroPedidoDiario] = 1
                it[codigoPedido] = "K1-001"
                it[fecha] = LocalDate.now()
                it[estado] = "COTIZADO"
                it[modalidad] = "COMER_AQUI"
                it[idCliente] = "cli-gen"
                it[total] = BigDecimal("8.5000")
                it[quoteExpiraEn] = LocalDateTime.now().plusMinutes(10)
                it[creadoEn] = LocalDateTime.now()
                it[actualizadoEn] = LocalDateTime.now()
            }

            KioskOrderItemTable.insert {
                it[idPedido] = orderId
                it[linea] = 1
                it[idItem] = 100
                it[cantidad] = BigDecimal("1.0000")
                it[precioUnitario] = BigDecimal("8.0000")
                it[nota] = "Sin hielo"
            }

            KioskOrderItemModifierTable.insert {
                it[idPedido] = orderId
                it[linea] = 1
                it[idModificador] = modId
                it[nombre] = "Coca Cola Sin Azucar"
                it[precioAdicional] = BigDecimal("0.5000")
            }

            val orders = KioskOrderTable.selectAll().toList()
            assertEquals(1, orders.size)
            assertEquals("K1-001", orders.first()[KioskOrderTable.codigoPedido])

            val items = KioskOrderItemTable.selectAll().toList()
            assertEquals(1, items.size)
            assertEquals("Sin hielo", items.first()[KioskOrderItemTable.nota])

            val mods = KioskOrderItemModifierTable.selectAll().toList()
            assertEquals(1, mods.size)
            assertEquals(BigDecimal("0.5000"), mods.first()[KioskOrderItemModifierTable.precioAdicional])
        }
    }

    @Test
    fun `migration script 006 runs cleanly on PA and VE base schemas`() {
        val scriptStream = javaClass.getResourceAsStream("/migrations/006_kiosk_tables.sql")
            ?: error("006_kiosk_tables.sql not found")
        val sqlScript = scriptStream.bufferedReader().use { it.readText() }

        // Test on simulated PA schema
        testScriptAgainstSchema("PA", sqlScript)

        // Test on simulated VE schema
        testScriptAgainstSchema("VE", sqlScript)
    }

    private fun testScriptAgainstSchema(country: String, fullScript: String) {
        val testDbConfig = HikariConfig().apply {
            jdbcUrl = "jdbc:h2:mem:migration_${country}_${System.nanoTime()};MODE=MySQL;DB_CLOSE_DELAY=-1"
            driverClassName = "org.h2.Driver"
            maximumPoolSize = 1
            isAutoCommit = false
        }
        HikariDataSource(testDbConfig).use { ds ->
            val db = Database.connect(ds)
            transaction(db) {
                // Seed base parametros_generales as exists in PA/VE
                if (country == "PA") {
                    exec(
                        """
                        CREATE TABLE IF NOT EXISTS parametros_generales (
                            cod_empresa INT PRIMARY KEY,
                            default_cod_cliente_factura VARCHAR(80) NOT NULL,
                            default_id_formapago_factura INT NOT NULL,
                            porcentaje_impuesto_principal DECIMAL(10,2) NOT NULL,
                            validar_stock VARCHAR(2) NOT NULL,
                            dias_vencimiento INT NOT NULL,
                            cod_almacen INT NOT NULL,
                            rif VARCHAR(50),
                            moneda VARCHAR(50) NOT NULL,
                            moneda_base INT,
                            bloquear_itbms VARCHAR(2) DEFAULT 'NO',
                            facturar_cero BOOLEAN DEFAULT FALSE,
                            impresion_directa BOOLEAN DEFAULT FALSE,
                            tipo_facturacion INT DEFAULT 0,
                            clave_kiosko VARCHAR(255)
                        )
                        """.trimIndent(),
                    )
                } else {
                    exec(
                        """
                        CREATE TABLE IF NOT EXISTS parametros_generales (
                            cod_empresa INT PRIMARY KEY,
                            default_cod_cliente_factura VARCHAR(80) NOT NULL,
                            default_id_formapago_factura INT NOT NULL,
                            porcentaje_impuesto_principal DECIMAL(10,2) NOT NULL,
                            validar_stock VARCHAR(2) NOT NULL,
                            dias_vencimiento INT NOT NULL,
                            cod_almacen INT NOT NULL,
                            rif VARCHAR(50),
                            moneda VARCHAR(50) NOT NULL,
                            moneda_base INT,
                            multi_moneda VARCHAR(2) NOT NULL,
                            moneda_secundaria INT NOT NULL,
                            moneda_secundaria_abr VARCHAR(50) NOT NULL,
                            igtf DECIMAL(10,6),
                            impresion_directa VARCHAR(2) NOT NULL,
                            clave_kiosko VARCHAR(255)
                        )
                        """.trimIndent(),
                    )
                }

                // Execute statements from migration script
                val statements = fullScript
                    .lines()
                    .filterNot { it.trim().startsWith("--") }
                    .joinToString("\n")
                    .split(";")
                    .map { it.trim() }
                    .filter { it.isNotBlank() }

                for (stmt in statements) {
                    exec(stmt)
                }

                // Verify tables exist and alter column worked
                val tablesCheck = exec("SHOW TABLES") { rs ->
                    val names = mutableListOf<String>()
                    while (rs.next()) {
                        names.add(rs.getString(1).lowercase())
                    }
                    names
                } ?: emptyList()

                assertTrue(tablesCheck.contains("kiosco_dispositivo"))
                assertTrue(tablesCheck.contains("kiosco_media"))
                assertTrue(tablesCheck.contains("item_modificador_grupo"))
                assertTrue(tablesCheck.contains("item_modificador"))
                assertTrue(tablesCheck.contains("item_modificador_relacion"))
                assertTrue(tablesCheck.contains("kiosco_pedido"))
                assertTrue(tablesCheck.contains("kiosco_pedido_item"))
                assertTrue(tablesCheck.contains("kiosco_pedido_item_modificador"))

                // Verify columns added to parametros_generales
                val columnsCheck = exec("SHOW COLUMNS FROM parametros_generales") { rs ->
                    val cols = mutableListOf<String>()
                    while (rs.next()) {
                        cols.add(rs.getString(1).lowercase())
                    }
                    cols
                } ?: emptyList()

                assertTrue(columnsCheck.contains("kiosco_destino_pedido"))
                assertTrue(columnsCheck.contains("kiosco_impresora_cocina_ip"))
                assertTrue(columnsCheck.contains("kiosco_modalidades"))
                assertTrue(columnsCheck.contains("kiosco_color_marca"))
                assertTrue(columnsCheck.contains("kiosco_config_version"))
            }
        }
    }
}
