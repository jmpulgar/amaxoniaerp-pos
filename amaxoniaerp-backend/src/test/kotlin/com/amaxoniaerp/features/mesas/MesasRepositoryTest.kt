package com.amaxoniaerp.features.mesas

import com.amaxoniaerp.features.caja.data.CajaTable
import com.amaxoniaerp.features.caja.data.SucursalTable
import com.amaxoniaerp.features.caja.data.VendedorTable
import com.amaxoniaerp.features.mesas.data.MesasRepository
import com.amaxoniaerp.features.mesas.data.MesasTable
import com.amaxoniaerp.features.mesas.data.PlantasTable
import com.amaxoniaerp.features.mesas.domain.CajaScopeResult
import com.amaxoniaerp.features.mesas.domain.LienzoDefaults
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Aislamiento entre sucursales de áreas y mesas, contra H2 en modo MySQL.
 *
 * Datos sembrados:
 * - sucursal 1 (A) y 2 (B); sucursal 3 sin áreas
 * - cajas: `caja-a` -> suc 1, `caja-b` -> suc 2, `caja-sin-suc` -> sin sucursal, `caja-vacia` -> suc 3
 * - usuario 10 asignado solo a `caja-a` vía `vendedor`; usuario 99 sin fila en `vendedor`
 * - áreas: 100/101 activas en A (con empate en `orden`), 102 inactiva en A, 200 activa en B,
 *   103 activa en A pero sin mesas
 * - mesas: activas e inactivas en 100, mesas activas colgando del área inactiva 102
 */
class MesasRepositoryTest {
    private lateinit var database: Database
    private val repository = MesasRepository()

    @BeforeTest
    fun setUp() {
        database = Database.connect("jdbc:h2:mem:mesas_${System.nanoTime()};MODE=MySQL;DB_CLOSE_DELAY=-1", "org.h2.Driver")
        transaction(database) {
            SchemaUtils.create(SucursalTable, CajaTable, VendedorTable, PlantasTable, MesasTable)
            seedSucursales()
            seedCajas()
            seedVendedores()
            seedAreas()
            seedMesas()
        }
    }

    @AfterTest
    fun tearDown() {
        transaction(database) {
            SchemaUtils.drop(MesasTable, PlantasTable, VendedorTable, CajaTable, SucursalTable)
        }
    }

    // ---------- 1 y 2: cada caja ve solo las áreas de su sucursal ----------

    @Test
    fun `caja de sucursal A solo devuelve areas de A`() =
        runBlocking {
            val scope = allowedScope(userId = 10, cajaId = CAJA_A)
            assertEquals(1, scope.sucursalId)

            val areas = repository.listAreas(database, scope.sucursalId)

            // 100 (orden 1), y el empate en orden 2 lo desempata el nombre: Bar (103) antes que Terraza (101).
            assertEquals(listOf(100, 103, 101), areas.map { it.id })
            assertTrue(areas.all { it.activo })
        }

    @Test
    fun `caja de sucursal B solo devuelve areas de B`() =
        runBlocking {
            val scope = allowedScope(userId = 99, cajaId = CAJA_B)
            assertEquals(2, scope.sucursalId)

            val areas = repository.listAreas(database, scope.sucursalId)

            assertEquals(listOf(200), areas.map { it.id })
        }

    // ---------- 3 y 4: ids cruzados rechazados ----------

    @Test
    fun `area de otra sucursal es rechazada`() =
        runBlocking {
            val areaDeB = 200
            assertNull(repository.listMesas(database, sucursalId = 1, areaId = areaDeB))
        }

    @Test
    fun `mesas de un area ajena no se filtran por manipulacion de ids`() =
        runBlocking {
            // El área 100 existe en la sucursal 1: consultada desde la sucursal 2 no debe resolver.
            assertNull(repository.listMesas(database, sucursalId = 2, areaId = 100))
            // Y un área inexistente tampoco.
            assertNull(repository.listMesas(database, sucursalId = 1, areaId = 999_999))
        }

    // ---------- 5, 6 y 7: registros inactivos ----------

    @Test
    fun `areas inactivas no aparecen`() =
        runBlocking {
            val areas = repository.listAreas(database, sucursalId = 1)
            assertTrue(areas.none { it.id == AREA_INACTIVA })
        }

    @Test
    fun `mesas inactivas no aparecen`() =
        runBlocking {
            val mesas = repository.listMesas(database, sucursalId = 1, areaId = 100)?.mesas
            assertEquals(listOf(1001, 1002), mesas?.map { it.id })
            assertTrue(mesas.orEmpty().all { it.activo })
        }

    @Test
    fun `mesas de un area inactiva no aparecen`() =
        runBlocking {
            // El área 102 está inactiva aunque sus mesas estén activas.
            assertNull(repository.listMesas(database, sucursalId = 1, areaId = AREA_INACTIVA))
        }

    // ---------- 8: orden ----------

    @Test
    fun `areas respetan orden y desempatan por nombre`() =
        runBlocking {
            val areas = repository.listAreas(database, sucursalId = 1)

            // 100 (orden 1), luego empate en orden 2 resuelto por nombre: "Bar" antes que "Terraza".
            assertEquals(listOf("Salón principal", "Bar", "Terraza"), areas.map { it.nombre })
            assertEquals(listOf(1, 2, 2), areas.map { it.orden })
        }

    @Test
    fun `mesas se ordenan de forma estable por codigo`() =
        runBlocking {
            val mesas = repository.listMesas(database, sucursalId = 1, areaId = 100)?.mesas
            assertEquals(listOf("M01", "M02"), mesas?.map { it.codigo })
        }

    // ---------- 9 y 10: estados vacíos ----------

    @Test
    fun `sucursal sin areas devuelve lista vacia`() =
        runBlocking {
            val scope = allowedScope(userId = 99, cajaId = CAJA_SUC_VACIA)
            assertEquals(emptyList(), repository.listAreas(database, scope.sucursalId))
        }

    @Test
    fun `area sin mesas devuelve lista vacia y no null`() =
        runBlocking {
            val plan = repository.listMesas(database, sucursalId = 1, areaId = AREA_SIN_MESAS)
            assertEquals(emptyList(), plan?.mesas)
        }

    // ---------- 11: acceso a la caja y sucursal ausente ----------

    @Test
    fun `caja no asignada al usuario es rechazada`() =
        runBlocking {
            // El usuario 10 solo tiene `caja-a` en vendedor.id_cajas.
            assertEquals(CajaScopeResult.AccessDenied, repository.resolveCajaScope(database, userId = 10, cajaId = CAJA_B))
        }

    @Test
    fun `usuario sin fila en vendedor conserva el acceso permisivo heredado`() =
        runBlocking {
            val result = repository.resolveCajaScope(database, userId = 99, cajaId = CAJA_B)
            assertTrue(result is CajaScopeResult.Allowed)
        }

    @Test
    fun `caja inexistente devuelve CajaNotFound`() =
        runBlocking {
            assertEquals(
                CajaScopeResult.CajaNotFound,
                repository.resolveCajaScope(database, userId = 99, cajaId = "no-existe"),
            )
        }

    @Test
    fun `caja sin sucursal asignada devuelve SucursalNotAssigned`() =
        runBlocking {
            assertEquals(
                CajaScopeResult.SucursalNotAssigned,
                repository.resolveCajaScope(database, userId = 99, cajaId = CAJA_SIN_SUCURSAL),
            )
        }

    // ---------- 12: conteo de mesas activas ----------

    @Test
    fun `cantidad_mesas_activas cuenta solo mesas activas del area`() =
        runBlocking {
            val areas = repository.listAreas(database, sucursalId = 1).associateBy { it.id }

            assertEquals(2, areas.getValue(100).cantidadMesasActivas)
            assertEquals(0, areas.getValue(AREA_SIN_MESAS).cantidadMesasActivas)
        }

    @Test
    fun `la geometria viaja en el contrato y el plan incluye lienzo e imagen del area`() =
        runBlocking {
            val plan = repository.listMesas(database, sucursalId = 1, areaId = 100)
            val mesa = plan?.mesas?.first()

            assertEquals(4, mesa?.capacidad)
            assertEquals("rectangular", mesa?.forma)
            assertEquals(120.0, mesa?.posicionX)
            assertEquals(80.0, mesa?.posicionY)
            assertEquals(100.0, mesa?.ancho)
            assertEquals(60.0, mesa?.alto)
            assertEquals(0.0, mesa?.rotacion)

            // Lienzo contractual 2000 x 1200 publicado para que el POS no lo hardcodee.
            assertEquals(LienzoDefaults.ANCHO_LIENZO, plan?.lienzo?.anchoLienzo)
            assertEquals(LienzoDefaults.ALTO_LIENZO, plan?.lienzo?.altoLienzo)
            // El seed de áreas guarda la imagen con el slug del nombre ("salón_principal.jpg").
            assertEquals("salón_principal.jpg", plan?.imagenUrl)
        }

    @Test
    fun `area sin imagen devuelve imagen_url nula`() =
        runBlocking {
            val withBlankImage = 999
            transaction(database) {
                PlantasTable.insert {
                    it[PlantasTable.id] = withBlankImage
                    it[sucursalId] = 1
                    it[PlantasTable.nombre] = "Sin imagen"
                    it[descripcion] = "area sin imagen del plano"
                    it[imagen] = "   "
                    it[PlantasTable.orden] = 5
                    it[activo] = 1
                }
            }

            val plan = repository.listMesas(database, sucursalId = 1, areaId = withBlankImage)
            assertNull(plan?.imagenUrl)
        }

    // ---------- helpers ----------

    private suspend fun allowedScope(
        userId: Int,
        cajaId: String,
    ) = (repository.resolveCajaScope(database, userId, cajaId) as CajaScopeResult.Allowed).scope

    private fun seedSucursales() {
        listOf(1 to "Sucursal A", 2 to "Sucursal B", 3 to "Sucursal sin áreas").forEach { (id, nombre) ->
            SucursalTable.insert {
                it[idSucursal] = id
                it[codigo] = "S$id"
                it[serie] = "$id"
                it[sucursal] = nombre
            }
        }
    }

    private fun seedCajas() {
        insertCaja(CAJA_A, sucursal = 1)
        insertCaja(CAJA_B, sucursal = 2)
        insertCaja(CAJA_SUC_VACIA, sucursal = 3)
        insertCaja(CAJA_SIN_SUCURSAL, sucursal = null)
    }

    private fun insertCaja(
        id: String,
        sucursal: Int?,
    ) {
        CajaTable.insert {
            it[idCaja] = id
            it[codCaja] = id
            it[caja] = id
            it[idSucursal] = sucursal
            it[serieCaja] = "1"
        }
    }

    private fun seedVendedores() {
        VendedorTable.insert {
            it[idVendedor] = 1
            it[codVendedor] = 1
            it[nombre] = "Vendedor asignado"
            it[codUsuarios] = "10"
            it[idTiendas] = "1"
            it[idCajas] = CAJA_A
            it[activo] = 1
        }
    }

    private fun seedAreas() {
        insertArea(id = 100, sucursal = 1, nombre = "Salón principal", orden = 1)
        insertArea(id = 101, sucursal = 1, nombre = "Terraza", orden = 2)
        insertArea(id = AREA_SIN_MESAS, sucursal = 1, nombre = "Bar", orden = 2)
        insertArea(id = AREA_INACTIVA, sucursal = 1, nombre = "Salón cerrado", orden = 3, activa = false)
        insertArea(id = 200, sucursal = 2, nombre = "Patio", orden = 1)
    }

    private fun insertArea(
        id: Int,
        sucursal: Int,
        nombre: String,
        orden: Int,
        activa: Boolean = true,
    ) {
        PlantasTable.insert {
            it[PlantasTable.id] = id
            it[sucursalId] = sucursal
            it[PlantasTable.nombre] = nombre
            it[descripcion] = "Descripción de $nombre"
            it[imagen] = "${nombre.lowercase().replace(' ', '_')}.jpg"
            it[PlantasTable.orden] = orden
            it[activo] = if (activa) 1 else 0
        }
    }

    private fun seedMesas() {
        insertMesa(id = 1001, area = 100, codigo = "M01", nombre = "Mesa 01", capacidad = 4)
        insertMesa(id = 1002, area = 100, codigo = "M02", nombre = "Mesa 02", capacidad = 2)
        insertMesa(id = 1003, area = 100, codigo = "M03", nombre = "Mesa 03", capacidad = 6, activa = false)
        // Mesas activas colgando de un área inactiva: no deben ser alcanzables.
        insertMesa(id = 1004, area = AREA_INACTIVA, codigo = "C01", nombre = "Mesa cerrada", capacidad = 4)
        insertMesa(id = 2001, area = 200, codigo = "P01", nombre = "Patio 01", capacidad = 8)
    }

    private fun insertMesa(
        id: Int,
        area: Int,
        codigo: String,
        nombre: String,
        capacidad: Int,
        activa: Boolean = true,
    ) {
        MesasTable.insert {
            it[MesasTable.id] = id
            it[plantaId] = area
            it[MesasTable.codigo] = codigo
            it[MesasTable.nombre] = nombre
            it[MesasTable.capacidad] = capacidad
            it[forma] = "rectangular"
            it[posicionX] = BigDecimal("120.00")
            it[posicionY] = BigDecimal("80.00")
            it[ancho] = BigDecimal("100.00")
            it[alto] = BigDecimal("60.00")
            it[rotacion] = BigDecimal.ZERO
            it[activo] = if (activa) 1 else 0
        }
    }

    private companion object {
        const val CAJA_A = "caja-a"
        const val CAJA_B = "caja-b"
        const val CAJA_SUC_VACIA = "caja-vacia"
        const val CAJA_SIN_SUCURSAL = "caja-sin-suc"
        const val AREA_INACTIVA = 102
        const val AREA_SIN_MESAS = 103
    }
}
