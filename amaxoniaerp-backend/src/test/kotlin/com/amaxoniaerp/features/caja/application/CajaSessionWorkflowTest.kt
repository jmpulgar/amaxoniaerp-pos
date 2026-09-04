package com.amaxoniaerp.features.caja.application

import com.amaxoniaerp.features.caja.domain.AperturaRequest
import com.amaxoniaerp.features.caja.domain.CajaCierreSaveRequest
import com.amaxoniaerp.features.caja.domain.CajaSecuencia
import com.amaxoniaerp.features.caja.domain.CajaSecuenciaData
import com.amaxoniaerp.features.caja.domain.CajaSecuenciaGuard
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.Database
import org.junit.Before
import org.junit.Test
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests unitarios del workflow de sesión de caja sobre un fake del puerto
 * [CajaSessionStore]: las guardas de cierre y la lectura de estado se
 * ejercitan sin H2 con esquema ni Ktor. La conexión existe sólo para que el
 * workflow abra su fase de transacción (el fake no ejecuta SQL).
 */
class CajaSessionWorkflowTest {
    private lateinit var database: Database
    private lateinit var store: RecordingCajaSessionStore
    private lateinit var workflow: CajaSessionWorkflow

    @Before
    fun setUp() {
        database =
            Database.connect(
                "jdbc:h2:mem:caja_workflow_${System.nanoTime()};MODE=MySQL;DB_CLOSE_DELAY=-1",
                "org.h2.Driver",
            )
        store = RecordingCajaSessionStore()
        workflow = CajaSessionWorkflow(store)
    }

    @Test
    fun `close guarda el cierre con la serie de la secuencia y responde exito`() =
        runBlocking {
            store.guard = CajaSecuenciaGuard(cerrada = false, serieSucursal = "A")
            store.temporalesPendientes = 0

            val result = workflow.close(database, PA, cierreRequest(SEQ))

            val response = result.getOrThrow()
            assertTrue(response.success)
            assertEquals("Cierre de caja guardado correctamente", response.message)
            assertEquals(SEQ, response.id)

            val write = store.writtenCierres.single()
            assertEquals(SEQ, write.first.id)
            assertEquals("A", write.third)
            assertEquals(listOf("findSecuenciaGuard", "writeCierre"), store.calls)
        }

    @Test
    fun `close falla si la secuencia no existe y no escribe nada`() =
        runBlocking {
            store.guard = null

            val result = workflow.close(database, PA, cierreRequest(SEQ))

            assertIs<IllegalStateException>(result.exceptionOrNull())
            assertEquals("Secuencia de caja no encontrada", result.exceptionOrNull()!!.message)
            assertTrue("writeCierre" !in store.calls)
        }

    @Test
    fun `close falla si la secuencia ya esta cerrada y no escribe nada`() =
        runBlocking {
            store.guard = CajaSecuenciaGuard(cerrada = true, serieSucursal = "A")

            val result = workflow.close(database, PA, cierreRequest(SEQ))

            assertEquals("La secuencia de caja ya se encuentra cerrada", result.exceptionOrNull()!!.message)
            assertTrue("writeCierre" !in store.calls)
        }

    @Test
    fun `close no bloquea ni falla con facturas temporales pendientes`() =
        runBlocking {
            store.guard = CajaSecuenciaGuard(cerrada = false, serieSucursal = "A")
            store.temporalesPendientes = 1

            val result = workflow.close(database, PA, cierreRequest(SEQ))

            val response = result.getOrThrow()
            assertTrue(response.success)
            assertEquals("Cierre de caja guardado correctamente", response.message)
            assertEquals(SEQ, response.id)
            assertTrue("writeCierre" in store.calls)
            assertEquals(listOf("findSecuenciaGuard", "writeCierre"), store.calls)
        }

    @Test
    fun `status retorna la secuencia abierta mas reciente`() =
        runBlocking {
            store.openSecuencias = mutableListOf(secuencia(SEQ_LATEST), secuencia(SEQ_OLDER))

            val status = workflow.status(database, DB, CAJA)

            assertNotNull(status)
            assertEquals(SEQ_LATEST, status.idCajaSecuencia)
        }

    @Test
    fun `status retorna null sin secuencias abiertas`() =
        runBlocking {
            store.openSecuencias = mutableListOf()

            assertNull(workflow.status(database, DB, CAJA))
        }

    @Test
    fun `open sin sesion previa inserta apertura y retorna estado abierto`() =
        runBlocking {
            val result = workflow.open(database, PA, DB, apertura(), "alice")

            val secuenciaAbierta = result.getOrThrow()
            assertEquals("alice", secuenciaAbierta.usuarioApertura)
            assertEquals(1, secuenciaAbierta.estatus)
            assertNull(secuenciaAbierta.fechaCierre)
            assertEquals(
                listOf("findOpenSecuencias", "nextSecuenciaCode", "insertApertura", "findOpenSecuencias"),
                store.calls,
            )
            assertEquals(1, store.openSecuencias.size)
        }

    @Test
    fun `open con auto-close fallido falla con mensaje estable y no inserta apertura`() =
        runBlocking {
            store.openSecuencias = mutableListOf(secuencia(SEQ_OLD))
            store.readSecuenciaDataError = IllegalStateException("boom")

            val result = workflow.open(database, PA, DB, apertura(), "bob")

            val error = result.exceptionOrNull()
            assertIs<IllegalStateException>(error)
            assertEquals("No se pudo cerrar automaticamente la secuencia abierta", error.message)
            assertTrue(
                generateSequence(error as Throwable?) { it?.cause }
                    .any { it?.message == "boom" },
                "la causa original debe sobrevivir en la cadena",
            )
            assertTrue("insertApertura" !in store.calls)
        }

    @Test
    fun `open falla si no puede releer la apertura recien insertada`() =
        runBlocking {
            store.insertSinAbrir = true

            val result = workflow.open(database, PA, DB, apertura(), "carol")

            assertIs<IllegalStateException>(result.exceptionOrNull())
            assertEquals("Failed to retrieve open caja.", result.exceptionOrNull()!!.message)
        }

    private fun cierreRequest(id: String) =
        CajaCierreSaveRequest(
            id = id,
            montoEfectivoVentas = 0.0,
            montoEfectivoEntrada = 0.0,
            montoEfectivoSalida = 0.0,
            montoEfectivoTotal = 0.0,
            montoEfectivoCierre = 0.0,
            montoEfectivoDiferencia = 0.0,
            montoOtrosTotal = 0.0,
            montoOtrosCierre = 0.0,
            montoOtrosDiferencia = 0.0,
            montoTotal = 0.0,
            montoCierre = 0.0,
            montoDiferencia = 0.0,
        )

    private fun secuencia(id: String) =
        CajaSecuencia(
            idCajaSecuencia = id,
            idCaja = CAJA,
            fechaApertura = "2026-01-01 08:00:00",
            montoApertura = 10.0,
            fechaCierre = null,
            montoCierre = null,
            estatus = 1,
            usuarioApertura = "seed",
            usuarioCierre = null,
            serieSucursal = "A",
            idSucursal = 1,
        )

    private fun apertura() =
        AperturaRequest(
            idCaja = CAJA,
            montoApertura = 50.0,
            idVendedor = 1,
            serieSucursal = "A",
        )

    private companion object {
        const val PA = "PA"
        const val DB = "testdb"
        const val CAJA = "caja-1"
        const val SEQ = "seq-open"
        const val SEQ_OLD = "seq-old"
        const val SEQ_LATEST = "seq-new"
        const val SEQ_OLDER = "seq-older"
    }
}

/** Fake en memoria del puerto de persistencia de sesión: registra invocaciones. */
internal class RecordingCajaSessionStore : CajaSessionStore {
    val calls = mutableListOf<String>()
    val writtenCierres = mutableListOf<Triple<CajaCierreSaveRequest, LocalDateTime, String>>()
    var guard: CajaSecuenciaGuard? = null
    var temporalesPendientes = 0
    var openSecuencias: MutableList<CajaSecuencia> = mutableListOf()
    var readSecuenciaDataError: Throwable? = null
    var insertSinAbrir = false

    override fun findOpenSecuencias(idCaja: String): List<CajaSecuencia> {
        calls += "findOpenSecuencias"
        return openSecuencias.toList()
    }

    override fun findSecuenciaGuard(idSecuencia: String): CajaSecuenciaGuard? {
        calls += "findSecuenciaGuard"
        return guard
    }

    override fun countFacturasTemporalesPendientes(
        countryCode: String,
        idSecuencia: String,
    ): Int {
        calls += "countFacturasTemporales"
        return temporalesPendientes
    }

    override fun writeCierre(
        request: CajaCierreSaveRequest,
        now: LocalDateTime,
        serieSucursal: String,
    ) {
        calls += "writeCierre"
        writtenCierres += Triple(request, now, serieSucursal)
    }

    override fun readSecuenciaData(
        countryCode: String,
        idSecuencia: String,
        verifyFacturasTemporales: Boolean,
    ): CajaSecuenciaData {
        calls += "readSecuenciaData"
        val scripted = readSecuenciaDataError
        if (scripted != null) throw scripted
        error("Secuencia de caja no encontrada")
    }

    override fun nextSecuenciaCode(idCaja: String): String {
        calls += "nextSecuenciaCode"
        return "000001"
    }

    override fun insertApertura(
        newId: String,
        request: AperturaRequest,
        username: String,
        now: LocalDateTime,
        nextSequence: String,
    ) {
        calls += "insertApertura"
        if (insertSinAbrir) return
        openSecuencias +=
            CajaSecuencia(
                idCajaSecuencia = newId,
                idCaja = request.idCaja,
                fechaApertura = "",
                montoApertura = request.montoApertura,
                fechaCierre = null,
                montoCierre = null,
                estatus = 1,
                usuarioApertura = username,
                usuarioCierre = null,
                serieSucursal = request.serieSucursal,
                idSucursal = 1,
            )
    }
}
