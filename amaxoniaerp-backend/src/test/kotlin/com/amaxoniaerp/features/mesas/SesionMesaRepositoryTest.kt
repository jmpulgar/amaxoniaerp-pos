package com.amaxoniaerp.features.mesas

import com.amaxoniaerp.features.auth.data.UsersTable
import com.amaxoniaerp.features.caja.data.CajaTable
import com.amaxoniaerp.features.caja.data.SucursalTable
import com.amaxoniaerp.features.caja.data.VendedorTable
import com.amaxoniaerp.features.mesas.data.AbrirSesionScope
import com.amaxoniaerp.features.mesas.data.MesasRepository
import com.amaxoniaerp.features.mesas.data.MesasTable
import com.amaxoniaerp.features.mesas.data.PlantasTable
import com.amaxoniaerp.features.mesas.data.SesionMesaRepository
import com.amaxoniaerp.features.mesas.data.SesionMesaTable
import com.amaxoniaerp.features.mesas.domain.EstadoMesaOperativo
import com.amaxoniaerp.features.mesas.domain.EstadoSesionMesa
import com.amaxoniaerp.features.mesas.domain.SesionMesaResult
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Cobertura de sesión de mesa contra H2 en modo MySQL: apertura, duplicidad, aislamiento
 * entre sucursales/cajas, recuperación de sesión activa y cierre/cancelación de sesión vacía.
 *
 * Datos sembrados:
 * - sucursal 1 con áreas 100 (con mesas 1001, 1002) y 103 (sin mesas)
 * - sucursal 2 con área 200 y mesa 2001
 * - caja-a -> suc 1; caja-b -> suc 2; usuario 10 con sesión 10:10 en `usuarios`
 */
class SesionMesaRepositoryTest {
    private lateinit var database: Database
    private val repository = SesionMesaRepository()
    private val mesasRepository = MesasRepository()

    @BeforeTest
    fun setUp() {
        database = Database.connect("jdbc:h2:mem:sesion_${System.nanoTime()};MODE=MySQL;DB_CLOSE_DELAY=-1", "org.h2.Driver")
        transaction(database) {
            SchemaUtils.create(
                SucursalTable,
                CajaTable,
                VendedorTable,
                UsersTable,
                PlantasTable,
                MesasTable,
                SesionMesaTable,
            )
            seedSucursales()
            seedCajas()
            seedUsuarios()
            seedAreas()
            seedMesas()
        }
    }

    @AfterTest
    fun tearDown() {
        transaction(database) {
            SchemaUtils.drop(
                SesionMesaTable,
                MesasTable,
                PlantasTable,
                UsersTable,
                VendedorTable,
                CajaTable,
                SucursalTable,
            )
        }
    }

    // ---------- Apertura ----------

    @Test
    fun `abrir persiste sesion ABIERTA y la marca como ocupada en el area`() =
        runBlocking {
            val scope = abrirScope(cajaId = CAJA_A, mesaId = 1001, cantidadPersonas = 4)
            val result = repository.abrir(database, scope)

            assertTrue(result is SesionMesaResult.Opened)
            val sesion = result.sesion
            assertEquals(1, sesion.sucursalId)
            assertEquals(CAJA_A, sesion.cajaId)
            assertEquals(100, sesion.areaId)
            assertEquals(1001, sesion.mesaId)
            assertEquals(4, sesion.cantidadPersonas)
            assertEquals(EstadoSesionMesa.ABIERTA.codigo, sesion.estado)
            assertEquals("u10", sesion.usuario)
            assertTrue(sesion.activo)

            // Lista de estados del área: 1001 aparece ocupada.
            val estados = repository.listarEstados(database, sucursalId = 1, areaId = 100)
            assertTrue(estados is SesionMesaResult.States)
            val byMesa = estados.estados.associateBy { it.mesaId }
            assertEquals(EstadoMesaOperativo.OCUPADA.name, byMesa.getValue(1001).estado)
            assertEquals(EstadoMesaOperativo.DISPONIBLE.name, byMesa.getValue(1002).estado)
            assertEquals(1001, byMesa.getValue(1001).sesion?.mesaId)
        }

    @Test
    fun `abrir con cantidad invalida devuelve CantidadPersonasInvalida`() =
        runBlocking {
            val scope = abrirScope(cajaId = CAJA_A, mesaId = 1001, cantidadPersonas = 0)
            assertEquals(SesionMesaResult.CantidadPersonasInvalida, repository.abrir(database, scope))
        }

    @Test
    fun `abrir sobre una mesa ya ocupada devuelve SesionYaAbierta`() =
        runBlocking {
            abrirMesa(cajaId = CAJA_A, mesaId = 1001)
            val segunda = repository.abrir(database, abrirScope(cajaId = CAJA_A, mesaId = 1001))
            assertEquals(SesionMesaResult.SesionYaAbierta, segunda)
        }

    @Test
    fun `abrir rechaza mesa inactiva o ajena al area`() =
        runBlocking {
            // Mesa 1003 está inactiva en el área 100.
            assertEquals(
                SesionMesaResult.MesaInactiva,
                repository.abrir(database, abrirScope(cajaId = CAJA_A, mesaId = 1003)),
            )
            // Mesa 2001 pertenece a otra área (200) no al área 100.
            assertEquals(
                SesionMesaResult.MesaNoPerteneceArea,
                repository.abrir(database, abrirScope(cajaId = CAJA_A, areaId = 100, mesaId = 2001)),
            )
        }

    @Test
    fun `abrir sobre area ajena a la sucursal de la caja devuelve AreaNoPerteneceSucursal`() =
        runBlocking {
            // Caja B está en sucursal 2; el área 100 es de la sucursal 1.
            assertEquals(
                SesionMesaResult.AreaNoPerteneceSucursal,
                repository.abrir(database, abrirScope(cajaId = CAJA_B, areaId = 100, mesaId = 1001)),
            )
        }

    // ---------- Aislamiento entre sucursales ----------

    @Test
    fun `estados de sucursal A no muestran ocupadas de sucursal B`() =
        runBlocking {
            abrirMesa(cajaId = CAJA_B, mesaId = 2001)
            val estadosA = repository.listarEstados(database, sucursalId = 1, areaId = 100)
            assertTrue(estadosA is SesionMesaResult.States)
            // El área 100 sigue sin sesiones: la apertura ocurrió en sucursal 2.
            assertTrue(estadosA.estados.all { it.estado == EstadoMesaOperativo.DISPONIBLE.name })
        }

    @Test
    fun `abrir la misma mesa en dos sucursales distintas no colisiona`() =
        runBlocking {
            // Mesa 1001 (sucursal 1) y mesa 2001 (sucursal 2) son distintas por id: nunca colisionan.
            assertTrue(abrirMesa(cajaId = CAJA_A, mesaId = 1001) is SesionMesaResult.Opened)
            assertTrue(
                repository.abrir(
                    database,
                    abrirScope(cajaId = CAJA_B, areaId = 200, mesaId = 2001),
                ) is SesionMesaResult.Opened,
            )
        }

    @Test
    fun `listar estados de un area ajena devuelve AreaNoPerteneceSucursal`() =
        runBlocking {
            assertEquals(
                SesionMesaResult.AreaNoPerteneceSucursal,
                repository.listarEstados(database, sucursalId = 2, areaId = 100),
            )
        }

    // ---------- Recuperación de sesión activa ----------

    @Test
    fun `sesion activa devuelve la sesion ABIERTA si existe`() =
        runBlocking {
            abrirMesa(cajaId = CAJA_A, mesaId = 1002, cantidadPersonas = 2)

            val result = repository.sesionActiva(database, mesaId = 1002)
            val sesion = (result as SesionMesaResult.Found).sesion
            assertEquals(1002, sesion?.mesaId)
            assertEquals(EstadoSesionMesa.ABIERTA.codigo, sesion?.estado)
            assertEquals(2, sesion?.cantidadPersonas)
        }

    @Test
    fun `sesion activa devuelve null si no hay sesion abierta`() =
        runBlocking {
            val result = repository.sesionActiva(database, mesaId = 1001)
            assertNull((result as SesionMesaResult.Found).sesion)
        }

    // ---------- Cerrar y cancelar ----------

    @Test
    fun `cerrar deja la sesion como CERRADA e inactiva`() =
        runBlocking {
            val abierta = abrirMesa(cajaId = CAJA_A, mesaId = 1001) as SesionMesaResult.Opened
            val cerrada = repository.cerrar(database, abierta.sesion.id) as SesionMesaResult.Closed
            val sesionCerrada = cerrada.sesion

            assertEquals(EstadoSesionMesa.CERRADA.codigo, sesionCerrada.estado)
            assertFalse(sesionCerrada.activo)
            assertTrue(sesionCerrada.fechaCierre != null)

            // La sesión ya no aparece como activa.
            val sinSesion = repository.sesionActiva(database, mesaId = 1001) as SesionMesaResult.Found
            assertNull(sinSesion.sesion)
        }

    @Test
    fun `cerrar deja la mesa disponible de nuevo`() =
        runBlocking {
            val abierta = abrirMesa(cajaId = CAJA_A, mesaId = 1001) as SesionMesaResult.Opened
            repository.cerrar(database, abierta.sesion.id)

            val estados = repository.listarEstados(database, sucursalId = 1, areaId = 100)
            val byMesa = (estados as SesionMesaResult.States).estados.associateBy { it.mesaId }
            assertEquals(EstadoMesaOperativo.DISPONIBLE.name, byMesa.getValue(1001).estado)
        }

    @Test
    fun `cancelar elimina la sesion y permite reabrir`() =
        runBlocking {
            val primera = abrirMesa(cajaId = CAJA_A, mesaId = 1001) as SesionMesaResult.Opened
            val cancelada = repository.cancelar(database, primera.sesion.id)
            assertTrue(cancelada is SesionMesaResult.Cancelled)
            assertFalse(cancelada.sesion.activo)

            // Tras cancelar, la mesa vuelve a estar disponible y se puede abrir de nuevo.
            val segunda = repository.abrir(database, abrirScope(cajaId = CAJA_A, mesaId = 1001))
            assertTrue(segunda is SesionMesaResult.Opened)
        }

    @Test
    fun `cerrar una sesion inexistente devuelve SesionNoEncontrada`() =
        runBlocking {
            assertEquals(SesionMesaResult.SesionNoEncontrada, repository.cerrar(database, sesionId = 9999))
        }

    @Test
    fun `cerrar dos veces la misma sesion devuelve SesionYaFinalizada`() =
        runBlocking {
            val abierta = abrirMesa(cajaId = CAJA_A, mesaId = 1001) as SesionMesaResult.Opened
            repository.cerrar(database, abierta.sesion.id)
            assertEquals(SesionMesaResult.SesionYaFinalizada, repository.cerrar(database, abierta.sesion.id))
        }

    // ---------- helpers ----------

    private suspend fun abrirMesa(
        cajaId: String,
        mesaId: Int,
        cantidadPersonas: Int = 4,
    ): SesionMesaResult =
        repository.abrir(database, abrirScope(cajaId = cajaId, mesaId = mesaId, cantidadPersonas = cantidadPersonas))

    private fun abrirScope(
        cajaId: String,
        areaId: Int = 100,
        mesaId: Int,
        cantidadPersonas: Int = 4,
    ) = AbrirSesionScope(
        cajaId = cajaId,
        areaId = areaId,
        mesaId = mesaId,
        usuarioId = 10,
        cantidadPersonas = cantidadPersonas,
    )

    private fun seedSucursales() {
        listOf(1 to "Sucursal A", 2 to "Sucursal B").forEach { (id, nombre) ->
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
    }

    private fun insertCaja(
        id: String,
        sucursal: Int,
    ) {
        CajaTable.insert {
            it[idCaja] = id
            it[codCaja] = id
            it[caja] = id
            it[idSucursal] = sucursal
            it[serieCaja] = "1"
        }
    }

    private fun seedUsuarios() {
        UsersTable.insert {
            it[codUsuario] = 10
            it[usuario] = "u10"
            it[clave] = "x"
            it[status] = "1"
        }
    }

    private fun seedAreas() {
        PlantasTable.insert {
            it[PlantasTable.id] = 100
            it[sucursalId] = 1
            it[PlantasTable.nombre] = "Salón principal"
            it[PlantasTable.orden] = 1
            it[activo] = 1
        }
        PlantasTable.insert {
            it[PlantasTable.id] = 200
            it[sucursalId] = 2
            it[PlantasTable.nombre] = "Patio"
            it[PlantasTable.orden] = 1
            it[activo] = 1
        }
    }

    private fun seedMesas() {
        insertMesa(1001, area = 100, activa = true)
        insertMesa(1002, area = 100, activa = true)
        insertMesa(1003, area = 100, activa = false)
        insertMesa(2001, area = 200, activa = true)
    }

    private fun insertMesa(
        id: Int,
        area: Int,
        activa: Boolean,
    ) {
        MesasTable.insert {
            it[MesasTable.id] = id
            it[plantaId] = area
            it[MesasTable.codigo] = "M$id"
            it[MesasTable.nombre] = "Mesa $id"
            it[capacidad] = 4
            it[forma] = "rectangular"
            it[activo] = if (activa) 1 else 0
        }
    }

    private companion object {
        const val CAJA_A = "caja-a"
        const val CAJA_B = "caja-b"
    }
}
