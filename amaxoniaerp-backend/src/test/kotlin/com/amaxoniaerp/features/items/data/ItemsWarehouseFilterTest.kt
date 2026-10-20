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
            SchemaUtils.create(ItemsTablePA, ItemExistenciaAlmacenTable, ItemCombosTable)

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

            // Item 5: Combo (tipo_articulo = 'C', sin existencia directa en almacen)
            ItemsTablePA.insert {
                it[idItem] = 5
                it[codItem] = "COMBO-1"
                it[descripcion1] = "Combo Familiar"
                it[tipoProd] = 2
                it[codItemForma] = 1
                it[tipoArticulo] = "C"
                it[estatus] = "A"
            }

            // Item 6: Kit (tipo_articulo = 'K', sin existencia directa en almacen)
            ItemsTablePA.insert {
                it[idItem] = 6
                it[codItem] = "KIT-1"
                it[descripcion1] = "Kit Escolar"
                it[tipoProd] = 2
                it[codItemForma] = 1
                it[tipoArticulo] = "K"
                it[estatus] = "A"
            }

            // Item 7: Receta (tipo_articulo = 'R', sin existencia directa en almacen)
            ItemsTablePA.insert {
                it[idItem] = 7
                it[codItem] = "RECETA-1"
                it[descripcion1] = "Pizza Margarita"
                it[tipoProd] = 2
                it[codItemForma] = 1
                it[tipoArticulo] = "R"
                it[estatus] = "A"
            }

            // Item 8: Producto con Opciones/Modificadores en item_combos (sin existencia propia en almacen)
            ItemsTablePA.insert {
                it[idItem] = 8
                it[codItem] = "BURGER-01"
                it[descripcion1] = "Hamburguesa Clasica"
                it[tipoProd] = 2
                it[codItemForma] = 1
                it[tipoArticulo] = ""
                it[estatus] = "A"
            }
            ItemCombosTable.insert {
                it[idItem] = 8
                it[idCombo] = 10
            }
        }
    }

    @AfterTest
    fun tearDown() {
        transaction(database) {
            SchemaUtils.drop(ItemCombosTable, ItemExistenciaAlmacenTable, ItemsTablePA)
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

            assertEquals(8L, total)
            assertEquals(8, items.size)
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
            assertEquals(6L, total)
            assertEquals(6, items.size)
            assertTrue("PROD-1" in codes, "Debe incluir producto con stock en almacen 1")
            assertTrue("SERV-1" in codes, "Debe incluir servicios siempre")
            assertTrue("COMBO-1" in codes, "Debe incluir combos siempre (no manejan stock directo)")
            assertTrue("KIT-1" in codes, "Debe incluir kits siempre (no manejan stock directo)")
            assertTrue("RECETA-1" in codes, "Debe incluir recetas siempre (no manejan stock directo)")
            assertTrue("BURGER-01" in codes, "Debe incluir productos con opciones en item_combos")
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
            assertEquals(7L, total)
            assertEquals(7, items.size)
            assertTrue("PROD-1" in codes)
            assertTrue("PROD-2" in codes)
            assertTrue("SERV-1" in codes)
            assertTrue("COMBO-1" in codes)
            assertTrue("KIT-1" in codes)
            assertTrue("RECETA-1" in codes)
            assertTrue("BURGER-01" in codes)
            assertTrue("PROD-3" !in codes)
        }

    @Test
    fun `combo y producto con opciones preservan enriquecimiento al filtrar por almacen`() =
        runBlocking {
            val (items, _) =
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

            val comboItem = items.first { it.code == "COMBO-1" }
            assertEquals("si", comboItem.poseeProductoCombo)

            val burgerItem = items.first { it.code == "BURGER-01" }
            assertEquals("si", burgerItem.poseeProductoCombo)
            assertEquals("10", burgerItem.idCombo)
        }

    @Test
    fun `filtro de almacenes funciona correctamente sin tabla item_combos`() =
        runBlocking {
            val dbWithoutCombos =
                Database.connect(
                    "jdbc:h2:mem:no_combos_${System.nanoTime()};MODE=MySQL;DB_CLOSE_DELAY=-1",
                    "org.h2.Driver",
                )
            transaction(dbWithoutCombos) {
                SchemaUtils.create(ItemsTablePA, ItemExistenciaAlmacenTable)
                ItemsTablePA.insert {
                    it[idItem] = 100
                    it[codItem] = "P-100"
                    it[descripcion1] = "P100"
                    it[tipoProd] = 2
                    it[codItemForma] = 1
                    it[estatus] = "A"
                }
                ItemExistenciaAlmacenTable.insert {
                    it[idItem] = 100
                    it[codAlmacen] = 1
                    it[cantidad] = BigDecimal.valueOf(5.0)
                }
                ItemsTablePA.insert {
                    it[idItem] = 101
                    it[codItem] = "K-101"
                    it[descripcion1] = "K101"
                    it[tipoProd] = 2
                    it[codItemForma] = 1
                    it[tipoArticulo] = "K"
                    it[estatus] = "A"
                }
            }

            val (items, total) =
                repository.listItems(
                    database = dbWithoutCombos,
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

            assertEquals(2L, total)
            val codes = items.map { it.code }.toSet()
            assertTrue("P-100" in codes)
            assertTrue("K-101" in codes)

            transaction(dbWithoutCombos) {
                SchemaUtils.drop(ItemExistenciaAlmacenTable, ItemsTablePA)
            }
        }
}
