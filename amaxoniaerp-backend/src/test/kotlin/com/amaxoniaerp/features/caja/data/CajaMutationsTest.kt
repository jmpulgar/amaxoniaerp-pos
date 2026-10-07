package com.amaxoniaerp.features.caja.data

import com.amaxoniaerp.features.caja.domain.SaveCajaRequest
import com.amaxoniaerp.features.companies.data.ParametrosGeneralesTablePA
import com.amaxoniaerp.features.companies.data.ParametrosGeneralesTableVE
import com.amaxoniaerp.features.companies.data.TasasCambioTableVE
import com.amaxoniaerp.features.items.data.AlmacenTable
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CajaMutationsTest {
    private lateinit var databasePA: Database
    private lateinit var databaseVE: Database
    private val repository = CajaRepository()

    @Before
    fun setUp() {
        databasePA =
            Database.connect(
                "jdbc:h2:mem:caja_mutations_pa_${System.nanoTime()};MODE=MySQL;DB_CLOSE_DELAY=-1",
                "org.h2.Driver",
            )
        transaction(databasePA) {
            SchemaUtils.create(
                CajaTable,
                SucursalTable,
                SucursalAlmacenTable,
                VendedorTable,
                ParametrosGeneralesTablePA,
                AlmacenTable,
            )
            SucursalTable.insert {
                it[idSucursal] = 1
                it[codigo] = "SUC1"
                it[serie] = "001"
                it[sucursal] = "Sucursal Central"
            }
            VendedorTable.insert {
                it[idVendedor] = 10
                it[codVendedor] = 10
                it[nombre] = "Vendedor Test"
                it[codUsuarios] = "1"
                it[idCajas] = "CJ-ASIGNADA"
                it[idTiendas] = "1"
                it[activo] = 1
            }
        }

        databaseVE =
            Database.connect(
                "jdbc:h2:mem:caja_mutations_ve_${System.nanoTime()};MODE=MySQL;DB_CLOSE_DELAY=-1",
                "org.h2.Driver",
            )
        transaction(databaseVE) {
            SchemaUtils.create(
                CajaTable,
                SucursalTable,
                SucursalAlmacenTable,
                VendedorTable,
                ParametrosGeneralesTableVE,
                TasasCambioTableVE,
                AlmacenTable,
            )
            SucursalTable.insert {
                it[idSucursal] = 1
                it[codigo] = "SUC1"
                it[serie] = "001"
                it[sucursal] = "Sucursal VE"
            }
        }
    }

    @After
    fun tearDown() {
        transaction(databasePA) {
            SchemaUtils.drop(
                AlmacenTable,
                ParametrosGeneralesTablePA,
                VendedorTable,
                SucursalAlmacenTable,
                SucursalTable,
                CajaTable,
            )
        }
        transaction(databaseVE) {
            SchemaUtils.drop(
                AlmacenTable,
                TasasCambioTableVE,
                ParametrosGeneralesTableVE,
                VendedorTable,
                SucursalAlmacenTable,
                SucursalTable,
                CajaTable,
            )
        }
    }

    @Test
    fun `crear caja en Panama con campos fiscales y consultar por id`() =
        runBlocking {
            val request =
                SaveCajaRequest(
                    codigo = "CJ-01",
                    caja = "Caja Mostrador",
                    descripcion = "Mostrador Principal PB",
                    idSucursal = 1,
                    serieCaja = "1",
                    fondoApertura = 150.00,
                    impresoraModelo = "SUNMI",
                    codigoSucursalEmisor = "0000",
                    puntoFacturacionFiscal = "001",
                    activo = 1,
                )

            val created = repository.createCaja(databasePA, "PA", request)
            assertNotNull(created.idCaja)
            assertEquals("CJ-01", created.codCaja)
            assertEquals("Caja Mostrador", created.caja)
            assertEquals("Mostrador Principal PB", created.descripcion)
            assertEquals(1, created.idSucursal)
            assertEquals("1", created.serieCaja)
            assertEquals(1, created.estatus)

            val retrieved = repository.getCajaById(databasePA, "PA", created.idCaja)
            assertNotNull(retrieved)
            assertEquals(created.idCaja, retrieved.idCaja)
            assertEquals("Caja Mostrador", retrieved.caja)
            assertEquals("Sucursal Central", retrieved.sucursalNombre)
        }

    @Test
    fun `crear caja en Venezuela con codAlmacen y actualizarla`() =
        runBlocking {
            val request =
                SaveCajaRequest(
                    id = "CJ-VE-01",
                    codigo = "CJ-VE-01",
                    caja = "Caja Caracas",
                    descripcion = "Caja Principal Caracas",
                    idSucursal = 1,
                    serieCaja = "01",
                    codAlmacen = 3,
                    activo = 1,
                )

            val created = repository.createCaja(databaseVE, "VE", request)
            assertEquals("CJ-VE-01", created.idCaja)
            assertEquals(3, created.codAlmacen)

            val updateRequest =
                SaveCajaRequest(
                    caja = "Caja Caracas Modificada",
                    descripcion = "Caja Reubicada",
                    idSucursal = 1,
                    serieCaja = "02",
                    codAlmacen = 4,
                    activo = 0,
                )

            val updated = repository.updateCaja(databaseVE, "VE", "CJ-VE-01", updateRequest)
            assertNotNull(updated)
            assertEquals("Caja Caracas Modificada", updated.caja)
            assertEquals("Caja Reubicada", updated.descripcion)
            assertEquals("02", updated.serieCaja)
            assertEquals(4, updated.codAlmacen)
            assertEquals(0, updated.estatus)
        }

    @Test
    fun `listar cajas con all=true ignora filtro de vendedor`() =
        runBlocking {
            repository.createCaja(
                databasePA,
                "PA",
                SaveCajaRequest(
                    id = "CJ-ASIGNADA",
                    caja = "Caja Asignada",
                    serieCaja = "1",
                    idSucursal = 1,
                ),
            )
            repository.createCaja(
                databasePA,
                "PA",
                SaveCajaRequest(
                    id = "CJ-LIBRE",
                    caja = "Caja Libre",
                    serieCaja = "2",
                    idSucursal = 1,
                ),
            )

            // Con userId=1, el vendedor 10 solo tiene asignada "CJ-ASIGNADA"
            val userCajas = repository.getCajas(databasePA, "PA", userId = 1, all = false)
            assertEquals(1, userCajas.size)
            assertEquals("CJ-ASIGNADA", userCajas[0].idCaja)

            // Con all=true (administrador/ERP), debe retornar ambas cajas
            val allCajas = repository.getCajas(databasePA, "PA", userId = 1, all = true)
            assertEquals(2, allCajas.size)
            val ids = allCajas.map { it.idCaja }.toSet()
            assertTrue("CJ-ASIGNADA" in ids)
            assertTrue("CJ-LIBRE" in ids)
        }
}
