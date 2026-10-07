package com.amaxonia.erp.ui.caja

import com.amaxonia.erp.domain.model.AperturaRequest
import com.amaxonia.erp.domain.model.Caja
import com.amaxonia.erp.domain.model.CajaSecuencia
import com.amaxonia.erp.domain.model.CajaSessionStatus
import com.amaxonia.erp.domain.model.CajaStatusResponse
import com.amaxonia.erp.domain.model.CierreCajaPaymentLine
import com.amaxonia.erp.domain.model.CierreCajaRequest
import com.amaxonia.erp.domain.model.CierreCajaResponse
import com.amaxonia.erp.domain.model.CierreCajaSummary
import com.amaxonia.erp.domain.model.SaveCajaRequest
import com.amaxonia.erp.domain.repository.CajaRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class CajasOverviewViewModelTest {
    private val testDispatcher = StandardTestDispatcher()

    private class FakeCajaRepository : CajaRepository {
        val _activeCaja = MutableStateFlow<Caja?>(null)
        override val activeCaja: StateFlow<Caja?> = _activeCaja.asStateFlow()

        val _activeCajaName = MutableStateFlow("Caja Principal")
        override val activeCajaName: StateFlow<String> = _activeCajaName.asStateFlow()

        val _activeCajaSecuencia = MutableStateFlow<CajaSecuencia?>(null)
        override val activeCajaSecuencia: StateFlow<CajaSecuencia?> = _activeCajaSecuencia.asStateFlow()

        val _sessionStatus = MutableStateFlow(CajaSessionStatus.SIN_CAJA)
        override val sessionStatus: StateFlow<CajaSessionStatus> = _sessionStatus.asStateFlow()

        var cajasList = listOf(
            Caja(idCaja = "1", codCaja = "01", caja = "Caja Principal", descripcion = "Caja 1", estatus = 1, serieCaja = "C01"),
            Caja(idCaja = "2", codCaja = "02", caja = "Caja Secundaria", descripcion = "Caja 2", estatus = 1, serieCaja = "C02"),
        )

        var statusResponse = CajaStatusResponse(isOpen = false, cajaSecuencia = null)
        val statusResponsesByCajaId = mutableMapOf<String, CajaStatusResponse>()
        var lastAperturaRequest: AperturaRequest? = null
        var lastCierreRequest: CierreCajaRequest? = null
        var nextCodigo = "SEC-001"
        var summaryToReturn: CierreCajaSummary? = null

        override suspend fun getCajas(): Result<List<Caja>> = Result.success(cajasList)

        override suspend fun createCaja(request: SaveCajaRequest): Result<Caja> {
            val newCaja =
                Caja(
                    idCaja = request.id ?: "new-caja-id",
                    codCaja = request.codigo,
                    caja = request.caja,
                    descripcion = request.descripcion,
                    serieCaja = request.serieCaja,
                    idSucursal = request.idSucursal,
                    estatus = request.activo,
                )
            cajasList = cajasList + newCaja
            return Result.success(newCaja)
        }

        override suspend fun updateCaja(id: String, request: SaveCajaRequest): Result<Caja> {
            val updated =
                Caja(
                    idCaja = id,
                    codCaja = request.codigo,
                    caja = request.caja,
                    descripcion = request.descripcion,
                    serieCaja = request.serieCaja,
                    idSucursal = request.idSucursal,
                    estatus = request.activo,
                )
            cajasList = cajasList.map { if (it.idCaja == id) updated else it }
            return Result.success(updated)
        }

        override suspend fun getNextSecuenciaCodigo(idCaja: String): Result<String> = Result.success(nextCodigo)

        override suspend fun restoreActiveCajaIfValid() {}

        override suspend fun checkCajaStatus(cajaId: String): Result<CajaStatusResponse> =
            Result.success(statusResponsesByCajaId[cajaId] ?: statusResponse)

        override suspend fun openCaja(request: AperturaRequest): Result<CajaStatusResponse> {
            lastAperturaRequest = request
            val newSec = CajaSecuencia(
                idCajaSecuencia = "new-sec-1",
                idCaja = request.idCaja,
                fechaApertura = "${LocalDate.now()} 09:00:00",
                montoApertura = request.montoApertura,
            )
            _activeCajaSecuencia.value = newSec
            _sessionStatus.value = CajaSessionStatus.ABIERTA
            statusResponse = CajaStatusResponse(isOpen = true, cajaSecuencia = newSec)
            return Result.success(statusResponse)
        }

        override suspend fun closeCaja(request: CierreCajaRequest): Result<CierreCajaResponse> {
            lastCierreRequest = request
            _activeCajaSecuencia.value = null
            _sessionStatus.value = CajaSessionStatus.PENDIENTE_APERTURA
            statusResponse = CajaStatusResponse(isOpen = false, cajaSecuencia = null)
            return Result.success(CierreCajaResponse(success = true, message = "Cierre exitoso"))
        }

        override suspend fun getCierreSummary(): Result<CierreCajaSummary> =
            summaryToReturn?.let { Result.success(it) }
                ?: Result.failure(IllegalStateException("No summary"))

        override suspend fun getCierreSummaryForSequence(caja: Caja, sequenceId: String): Result<CierreCajaSummary> =
            summaryToReturn?.let { Result.success(it) } ?: Result.failure(IllegalStateException("No summary"))

        override suspend fun setActiveCaja(caja: Caja) {
            _activeCaja.value = caja
            _activeCajaName.value = caja.displayName
        }

        override suspend fun setActiveCaja(id: String, name: String) {
            val c = Caja(idCaja = id, caja = name)
            _activeCaja.value = c
            _activeCajaName.value = name
        }

        override suspend fun clearActiveCaja() {
            _activeCaja.value = null
            _activeCajaName.value = "Caja no seleccionada"
        }

        override suspend fun markSequenceClosed() {
            _activeCajaSecuencia.value = null
            _sessionStatus.value = CajaSessionStatus.PENDIENTE_APERTURA
        }

        override suspend fun getActiveCaja(): Pair<String, String>? =
            _activeCaja.value?.let { Pair(it.idCaja, it.displayName) } ?: Pair("1", "Caja Principal")
    }

    private lateinit var fakeRepo: FakeCajaRepository
    private lateinit var viewModel: CajasOverviewViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeRepo = FakeCajaRepository()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun loadCajas_selectsFirstAndChecksStatus() = runTest {
        viewModel = CajasOverviewViewModel(fakeRepo)
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(2, state.cajas.size)
        assertNotNull(state.activeCaja)
        assertEquals("1", state.activeCaja?.idCaja)
        assertFalse(state.isCajaOpen)
    }

    @Test
    fun openCaja_requestsCorrelativeAndOpens() = runTest {
        viewModel = CajasOverviewViewModel(fakeRepo)
        advanceUntilIdle()

        viewModel.aperturarCaja(100.0)
        advanceUntilIdle()

        assertNotNull(fakeRepo.lastAperturaRequest)
        assertEquals(100.0, fakeRepo.lastAperturaRequest?.montoApertura ?: 0.0, 0.001)
        assertEquals("SEC-001", fakeRepo.lastAperturaRequest?.secuencia)
        assertTrue(viewModel.state.value.isCajaOpen)
    }

    @Test
    fun previousDaySession_triggersAvisoCajaAnterior() = runTest {
        val yesterday = LocalDate.now().minusDays(1).toString()
        fakeRepo.statusResponse = CajaStatusResponse(
            isOpen = true,
            cajaSecuencia = CajaSecuencia(
                idCajaSecuencia = "old-sec-1",
                idCaja = "1",
                fechaApertura = "$yesterday 10:00:00",
                montoApertura = 50.0,
            ),
        )

        viewModel = CajasOverviewViewModel(fakeRepo)
        advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue(state.isCajaOpen)
        assertTrue(state.isCajaDiaAnterior)
        assertTrue(state.showAvisoCajaAnterior)
        assertNotNull(state.cajaFechaApertura)

        // Dismiss warning
        viewModel.dismissAvisoCajaAnterior()
        assertFalse(viewModel.state.value.showAvisoCajaAnterior)
        assertTrue(viewModel.state.value.isCajaDiaAnterior)
    }

    @Test
    fun renovarCajaDiaAnterior_autoClosesAndOpensToday() = runTest {
        val yesterday = LocalDate.now().minusDays(1).toString()
        fakeRepo.statusResponse = CajaStatusResponse(
            isOpen = true,
            cajaSecuencia = CajaSecuencia(
                idCajaSecuencia = "old-sec-1",
                idCaja = "1",
                fechaApertura = "$yesterday 10:00:00",
                montoApertura = 50.0,
            ),
        )

        viewModel = CajasOverviewViewModel(fakeRepo)
        advanceUntilIdle()

        viewModel.renovarCajaDiaAnterior()
        advanceUntilIdle()

        // Should have called open with amount 0.0
        assertNotNull(fakeRepo.lastAperturaRequest)
        assertEquals(0.0, fakeRepo.lastAperturaRequest?.montoApertura ?: -1.0, 0.001)
        assertFalse(viewModel.state.value.isCajaDiaAnterior)
        assertFalse(viewModel.state.value.showAvisoCajaAnterior)
        assertTrue(viewModel.state.value.isCajaOpen)
    }

    @Test
    fun cerrarCaja_buildsCorrectPayloadWithDifferences() = runTest {
        val activeSec = CajaSecuencia(
            idCajaSecuencia = "sec-10",
            idCaja = "1",
            fechaApertura = "${LocalDate.now()} 08:00:00",
            montoApertura = 50.0,
        )
        fakeRepo.statusResponse = CajaStatusResponse(isOpen = true, cajaSecuencia = activeSec)
        fakeRepo.summaryToReturn = CierreCajaSummary(
            idCajaSecuencia = "sec-10",
            idCaja = "1",
            openAmount = 50.0,
            montoEfectivoVentas = 150.0,
            montoEfectivoTotal = 200.0,
            montoOtrosTotal = 80.0,
            montoTotal = 280.0,
            paymentLines = listOf(
                CierreCajaPaymentLine(idFormaPago = 1, label = "Efectivo", siglas = "EF", amount = 150.0),
                CierreCajaPaymentLine(idFormaPago = 2, label = "Tarjeta", siglas = "TARJ", amount = 80.0),
            ),
        )

        viewModel = CajasOverviewViewModel(fakeRepo)
        advanceUntilIdle()

        viewModel.openCierreDialog()
        advanceUntilIdle()

        val stateAfterSummary = viewModel.state.value
        assertNotNull(stateAfterSummary.cierreSummary)
        assertEquals(200.0, stateAfterSummary.cierreSummary?.montoEfectivoTotal ?: 0.0, 0.001)

        // Operator counts 210 in cash (10 surplus)
        viewModel.cerrarCaja(210.0, "Cierre cuadrado con propina")
        advanceUntilIdle()

        val cierreReq = fakeRepo.lastCierreRequest
        assertNotNull(cierreReq)
        assertEquals("sec-10", cierreReq?.id)
        assertEquals(150.0, cierreReq?.monto_efectivo_ventas ?: 0.0, 0.001)
        assertEquals(200.0, cierreReq?.monto_efectivo_total ?: 0.0, 0.001)
        assertEquals(210.0, cierreReq?.monto_efectivo_cierre ?: 0.0, 0.001)
        assertEquals(10.0, cierreReq?.monto_efectivo_diferencia ?: 0.0, 0.001)
        assertEquals(2, cierreReq?.detalle_formapago?.size)
        assertEquals("Cierre cuadrado con propina", cierreReq?.observacion_cierre)
        assertFalse(viewModel.state.value.isCajaOpen)
    }

    @Test
    fun loadCajas_populatesCajasStatusMap_identifyingVencidaAndClosed() = runTest {
        val yesterday = LocalDate.now().minusDays(1).toString()
        val vencidaSec = CajaSecuencia(
            idCajaSecuencia = "old-sec-1",
            idCaja = "1",
            fechaApertura = "$yesterday 10:00:00",
            montoApertura = 50.0,
        )
        fakeRepo.statusResponsesByCajaId["1"] = CajaStatusResponse(isOpen = true, cajaSecuencia = vencidaSec)
        fakeRepo.statusResponsesByCajaId["2"] = CajaStatusResponse(isOpen = false, cajaSecuencia = null)

        viewModel = CajasOverviewViewModel(fakeRepo)
        advanceUntilIdle()

        val map = viewModel.state.value.cajasStatusMap
        assertNotNull(map["1"])
        assertTrue(map["1"]!!.isOpen)
        assertTrue(map["1"]!!.isDiaAnterior)

        assertNotNull(map["2"])
        assertFalse(map["2"]!!.isOpen)
        assertFalse(map["2"]!!.isDiaAnterior)
    }
}
