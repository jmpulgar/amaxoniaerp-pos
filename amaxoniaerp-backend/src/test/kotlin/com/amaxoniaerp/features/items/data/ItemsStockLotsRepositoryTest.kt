package com.amaxoniaerp.features.items.data

import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDate
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests de integración H2 para el hueco de STOCK de TASK-087 en
 * [ItemsRepository]: consolidación por almacén con precompromiso y la regla
 * `stockTotalDisponible` que SOLO suma almacenes de venta (isSaleWarehouse),
 * más la consulta de lotes FEFO.
 */
class ItemsStockLotsRepositoryTest {
    private val repository = ItemsRepository()
    private lateinit var database: Database

    @BeforeTest
    fun setUp() {
        database =
            Database.connect(
                "jdbc:h2:mem:items_stock_${System.nanoTime()};MODE=MySQL;DB_CLOSE_DELAY=-1",
                "org.h2.Driver",
            )
        transaction(database) {
            SchemaUtils.create(
                AlmacenTable,
                ItemExistenciaAlmacenTable,
                ItemPrecompromisoTable,
                ConfiguracionLoteTable,
                ItemLoteTable,
            )
        }
    }

    @AfterTest
    fun tearDown() {
        transaction(database) {
            SchemaUtils.drop(
                ItemLoteTable,
                ConfiguracionLoteTable,
                ItemPrecompromisoTable,
                ItemExistenciaAlmacenTable,
                AlmacenTable,
            )
        }
    }

    @Test
    fun `stock total disponible solo suma almacenes de venta`() =
        runBlocking {
            seedWarehousesAndStock()

            val response = repository.getItemStockByWarehouse(database, ITEM)

            assertEquals(3, response.almacenes.size)
            // Venta (10 - 4 precomprometidas) + merma (50) + no_venta (30):
            // el total SOLO considera el almacén de venta.
            assertEquals(6.0, response.stockTotalDisponible)
            val principal = response.almacenes.single { it.almacenId == 1 }
            assertEquals(10.0, principal.cantidad)
            assertEquals(4.0, principal.cantidadPrecomprometida)
            assertEquals(6.0, principal.cantidadDisponible)
        }

    @Test
    fun `lotes se ordenan FEFO y excluyen disponibilidad cero`() =
        runBlocking {
            transaction(database) {
                ConfiguracionLoteTable.insert {
                    it[idItem] = ITEM
                    it[habilitado] = 1
                }
                // Insertados a propósito en orden NO ascendente de vencimiento.
                ItemLoteTable.insert {
                    it[idItem] = ITEM
                    it[codAlmacen] = 1
                    it[codigoLoteItem] = "LOTE-DIC"
                    it[vencimiento] = LocalDate.parse("2026-12-01")
                    it[disponibilidad] = java.math.BigDecimal("5")
                }
                ItemLoteTable.insert {
                    it[idItem] = ITEM
                    it[codAlmacen] = 1
                    it[codigoLoteItem] = "LOTE-SEP"
                    it[vencimiento] = LocalDate.parse("2026-09-01")
                    it[disponibilidad] = java.math.BigDecimal("3")
                }
                ItemLoteTable.insert {
                    it[idItem] = ITEM
                    it[codAlmacen] = 1
                    it[codigoLoteItem] = "LOTE-AGOTADO"
                    it[vencimiento] = LocalDate.parse("2026-08-01")
                    it[disponibilidad] = java.math.BigDecimal("0")
                }
            }

            val response = repository.getItemLots(database, ITEM)

            assertTrue(response.poseeConfiguracionLote)
            assertEquals(listOf("LOTE-SEP", "LOTE-DIC"), response.lotes.map { it.codigoLoteItem })
            assertEquals(3, response.lotes.first().disponibilidad)
        }

    @Test
    fun `item sin configuracion de lote retorna bandera falsa`() =
        runBlocking {
            val response = repository.getItemLots(database, SIN_LOTES)

            assertFalse(response.poseeConfiguracionLote)
            assertEquals(emptyList<kotlin.String>(), response.lotes.map { it.codigoLoteItem })
        }

    private fun seedWarehousesAndStock() {
        transaction(database) {
            AlmacenTable.insert {
                it[codAlmacen] = 1
                it[descripcion] = "Principal"
                it[tipo] = null
                it[orden] = 1
            }
            AlmacenTable.insert {
                it[codAlmacen] = 2
                it[descripcion] = "Mermas"
                it[tipo] = "MERMA"
                it[orden] = 2
            }
            AlmacenTable.insert {
                it[codAlmacen] = 3
                it[descripcion] = "Reserva"
                it[tipo] = "NO_VENTA"
                it[orden] = 3
            }
            ItemExistenciaAlmacenTable.insert {
                it[idItem] = ITEM
                it[codAlmacen] = 1
                it[cantidad] = java.math.BigDecimal("10")
            }
            ItemExistenciaAlmacenTable.insert {
                it[idItem] = ITEM
                it[codAlmacen] = 2
                it[cantidad] = java.math.BigDecimal("50")
            }
            ItemExistenciaAlmacenTable.insert {
                it[idItem] = ITEM
                it[codAlmacen] = 3
                it[cantidad] = java.math.BigDecimal("30")
            }
            ItemPrecompromisoTable.insert {
                it[idItem] = ITEM
                it[idAlmacen] = 1
                it[cantidad] = java.math.BigDecimal("4")
            }
        }
    }

    private companion object {
        const val ITEM = 7
        const val SIN_LOTES = 8
    }
}
