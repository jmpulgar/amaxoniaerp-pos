package com.amaxoniaerp.features.sucursales.data

import com.amaxoniaerp.features.caja.data.SucursalAlmacenTable
import com.amaxoniaerp.features.caja.data.SucursalTable
import com.amaxoniaerp.features.sucursales.domain.SaveSucursalRequest
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class SucursalRepositoryTest {
    private lateinit var database: Database
    private val repository = SucursalRepository()

    @Before
    fun setUp() {
        database =
            Database.connect(
                "jdbc:h2:mem:sucursal_test_${System.nanoTime()};MODE=MySQL;DB_CLOSE_DELAY=-1",
                "org.h2.Driver",
            )
        transaction(database) {
            SchemaUtils.create(SucursalTable, SucursalAlmacenTable)
        }
    }

    @After
    fun tearDown() {
        transaction(database) {
            SchemaUtils.drop(SucursalAlmacenTable, SucursalTable)
        }
    }

    @Test
    fun `crear sucursal basica sin almacen por defecto`() =
        runBlocking {
            val request =
                SaveSucursalRequest(
                    codigo = "SUC01",
                    serie = "A",
                    codigoSucursalEmisor = "0000",
                    sucursal = "Sucursal Principal",
                    descripcion = "Calle 50, Ciudad de Panamá",
                    defaultWarehouseId = null,
                )

            val created = repository.createSucursal(database, request)
            assertNotNull(created.id)
            assertEquals("SUC01", created.codigo)
            assertEquals("A", created.serie)
            assertEquals("0000", created.codigoSucursalEmisor)
            assertEquals("Sucursal Principal", created.sucursal)
            assertEquals("Calle 50, Ciudad de Panamá", created.descripcion)
            assertNull(created.defaultWarehouseId)

            val retrieved = repository.getSucursalById(database, created.id)
            assertNotNull(retrieved)
            assertEquals(created.id, retrieved.id)
            assertEquals("Sucursal Principal", retrieved.sucursal)
        }

    @Test
    fun `crear y actualizar sucursal con almacen por defecto`() =
        runBlocking {
            val createRequest =
                SaveSucursalRequest(
                    codigo = "SUC02",
                    serie = "B",
                    codigoSucursalEmisor = "0001",
                    sucursal = "Sucursal Norte",
                    descripcion = "Ubicación Norte",
                    defaultWarehouseId = 10,
                )

            val created = repository.createSucursal(database, createRequest)
            assertEquals(10, created.defaultWarehouseId)

            val retrievedAfterCreate = repository.getSucursalById(database, created.id)
            assertNotNull(retrievedAfterCreate)
            assertEquals(10, retrievedAfterCreate.defaultWarehouseId)

            // Actualizar datos y cambiar almacén
            val updateRequest =
                SaveSucursalRequest(
                    codigo = "SUC02-B",
                    serie = "B2",
                    codigoSucursalEmisor = "0002",
                    sucursal = "Sucursal Norte Renovada",
                    descripcion = "Nueva Dirección Norte",
                    defaultWarehouseId = 20,
                )

            val updated = repository.updateSucursal(database, created.id, updateRequest)
            assertNotNull(updated)
            assertEquals("Sucursal Norte Renovada", updated.sucursal)
            assertEquals("SUC02-B", updated.codigo)
            assertEquals(20, updated.defaultWarehouseId)

            val retrievedAfterUpdate = repository.getSucursalById(database, created.id)
            assertNotNull(retrievedAfterUpdate)
            assertEquals("Sucursal Norte Renovada", retrievedAfterUpdate.sucursal)
            assertEquals(20, retrievedAfterUpdate.defaultWarehouseId)
        }

    @Test
    fun `listar multiples sucursales`() =
        runBlocking {
            repository.createSucursal(
                database,
                SaveSucursalRequest(sucursal = "Sucursal 1", codigo = "S1"),
            )
            repository.createSucursal(
                database,
                SaveSucursalRequest(sucursal = "Sucursal 2", codigo = "S2", defaultWarehouseId = 5),
            )

            val list = repository.listSucursales(database)
            assertEquals(2, list.size)
            val s2 = list.find { it.codigo == "S2" }
            assertNotNull(s2)
            assertEquals(5, s2.defaultWarehouseId)
        }
}
