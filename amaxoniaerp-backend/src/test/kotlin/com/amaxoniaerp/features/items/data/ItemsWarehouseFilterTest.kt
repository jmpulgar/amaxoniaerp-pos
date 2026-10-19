package com.amaxoniaerp.features.items.data

import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import java.math.BigDecimal
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ItemsWarehouseFilterTest {
    private val repository = ItemsRepository()
    private lateinit var database: Database

    @BeforeTest
    fun setUp() {
        database =
            Database.connect(
                "jdbc:h2:mem:items_warehouse_filter_${System.nanoTime()};MODE=MySQL;DB_CLOSE_DELAY=-1",
                "org.h2.Driver",
            )
        transaction(database) {
            SchemaUtils.create(ItemsTablePA, ItemExistenciaAlmacenTable)

            // Item 1: Producto físico con stock en almacén 1
            ItemsTablePA.insert {
                it[idItem] = 1
                it[codItem] = "PROD-1"
                it[descripcion1] = "Producto Almacen 1"
                it[tipoProd] = 2
                it[codItemForma] = 1
                it[estatus] = "A"
            }
            ItemExistenciaAlmacenTable.insert {
                it[idItem] = 1
                it[codAlmacen] = 1
                it[cantidad] = BigDecimal.valueOf(10.0)
            }

            // Item 2: Producto físico con stock en almacén 2
            ItemsTablePA.insert {
                it[idItem] = 2
                it[codItem] = "PROD-2"
                it[descripcion1] = "Producto Almacen 2"
                it[tipoProd] = 2
                it[codItemForma] = 1
                it[estatus] = "A"
            }
            ItemExistenciaAlmacenTable.insert {
                it[idItem] = 2
                it[codAlmacen] = 2
                it[cantidad] = BigDecimal.valueOf(5.0)
            }

            // Item 3: Producto físico sin stock (stock 0 en almacen 1)
            ItemsTablePA.insert {
                it[idItem] = 3
                it[codItem] = "PROD-3"
                it[descripcion1] = "Producto Agotado"
                it[tipoProd] = 2
                it[codItemForma] = 1
                it[estatus] = "A"
            }
            ItemExistenciaAlmacenTable.insert {
                it[idItem] = 3
                it[codAlmacen] = 1
                it[cantidad] = BigDecimal.ZERO
            }

            // Item 4: Servicio (sin registro de inventario)
            ItemsTablePA.insert {
                it[idItem] = 4
                it[codItem] = "SERV-1"
                it[descripcion1] = "Servicio Flete"
                it[tipoProd] = 1
                it[codItemForma] = 2
                it[estatus] = "A"
            }
        }
    }

    @AfterTest
    fun tearDown() {
        transaction(database) {
            SchemaUtils.drop(ItemExistenciaAlmacenTable, ItemsTablePA)
        }
    }

    @Test
    fun `sin filtro de almacenes retorna todos los productos`() =
        runBlocking {
            val (items, total) =
                repository.listItems(
                    database = database,
                    countryCode = "PA",
                    query =
                        ItemsListQuery(
                            limit = 50,
                            offset = 0,
                            search = null,
                            includeTotal = true,
                            warehouseIds = null,
                        ),
                )

            assertEquals(4L, total)
            assertEquals(4, items.size)
        }

    @Test
    fun `con filtro de almacen 1 retorna producto con stock y servicios`() =
        runBlocking {
            val (items, total) =
                repository.listItems(
                    database = database,
                    countryCode = "PA",
                    query =
                        ItemsListQuery(
                            limit = 50,
                            offset = 0,
                            search = null,
                            includeTotal = true,
                            warehouseIds = listOf(1),
                        ),
                )

            val codes = items.map { it.code }.toSet()
            assertEquals(2L, total)
            assertEquals(2, items.size)
            assertTrue("PROD-1" in codes, "Debe incluir producto con stock en almacen 1")
            assertTrue("SERV-1" in codes, "Debe incluir servicios siempre")
            assertTrue("PROD-2" !in codes, "No debe incluir producto de almacen 2")
            assertTrue("PROD-3" !in codes, "No debe incluir producto agotado")
        }

    @Test
    fun `con filtro de almacenes 1 y 2 retorna productos de ambos almacenes mas servicios`() =
        runBlocking {
            val (items, total) =
                repository.listItems(
                    database = database,
                    countryCode = "PA",
                    query =
                        ItemsListQuery(
                            limit = 50,
                            offset = 0,
                            search = null,
                            includeTotal = true,
                            warehouseIds = listOf(1, 2),
                        ),
                )

            val codes = items.map { it.code }.toSet()
            assertEquals(3L, total)
            assertEquals(3, items.size)
            assertTrue("PROD-1" in codes)
            assertTrue("PROD-2" in codes)
            assertTrue("SERV-1" in codes)
            assertTrue("PROD-3" !in codes)
        }
}
