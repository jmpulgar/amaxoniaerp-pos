package com.amaxonia.erp.data.repository

import com.amaxonia.erp.data.local.LocalStore
import com.amaxonia.erp.data.remote.ApiService
import com.amaxonia.erp.data.remote.checkCajaStatus
import com.amaxonia.erp.data.remote.closeCaja
import com.amaxonia.erp.data.remote.createCaja
import com.amaxonia.erp.data.remote.getCajaSecuencia
import com.amaxonia.erp.data.remote.getCajas
import com.amaxonia.erp.data.remote.getNextSecuenciaCodigo
import com.amaxonia.erp.data.remote.openCaja
import com.amaxonia.erp.data.remote.updateCaja
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class CajaRepositoryImpl(
    private val apiService: ApiService,
    private val localStore: LocalStore,
) : CajaRepository {

    private val _activeCaja = MutableStateFlow<Caja?>(null)
    override val activeCaja: StateFlow<Caja?> = _activeCaja.asStateFlow()

    private val _activeCajaName = MutableStateFlow("Caja no seleccionada")
    override val activeCajaName: StateFlow<String> = _activeCajaName.asStateFlow()

    private val _activeCajaSecuencia = MutableStateFlow<CajaSecuencia?>(null)
    override val activeCajaSecuencia: StateFlow<CajaSecuencia?> = _activeCajaSecuencia.asStateFlow()

    private val _sessionStatus = MutableStateFlow(CajaSessionStatus.SIN_CAJA)
    override val sessionStatus: StateFlow<CajaSessionStatus> = _sessionStatus.asStateFlow()

    private suspend fun getContext(): Pair<String, String> {
        val session = localStore.readCompanySession()
            ?: error("No hay sesión de empresa activa")
        val adminDb = session.company.adminDb.ifBlank { "default" }
        return Pair(session.token, adminDb)
    }

    override suspend fun getCajas(): Result<List<Caja>> =
        runCatching {
            val (token, adminDb) = getContext()
            apiService.getCajas(token, adminDb, all = true)
        }

    override suspend fun createCaja(request: SaveCajaRequest): Result<Caja> =
        runCatching {
            val (token, adminDb) = getContext()
            apiService.createCaja(token, adminDb, request)
        }

    override suspend fun updateCaja(id: String, request: SaveCajaRequest): Result<Caja> =
        runCatching {
            val (token, adminDb) = getContext()
            apiService.updateCaja(token, adminDb, id, request)
        }

    override suspend fun getNextSecuenciaCodigo(idCaja: String): Result<String> =
        runCatching {
            val (token, adminDb) = getContext()
            val response = apiService.getNextSecuenciaCodigo(token, adminDb, idCaja)
            response.codigo
        }

    override suspend fun restoreActiveCajaIfValid() {
        val saved = localStore.readActiveCaja()
        if (saved != null && saved.first.isNotBlank()) {
            _activeCajaName.update { saved.second.ifBlank { "Caja Principal" } }
            val placeholderCaja = Caja(idCaja = saved.first, caja = saved.second)
            _activeCaja.update { placeholderCaja }
            _sessionStatus.update { CajaSessionStatus.VERIFICANDO }
            checkCajaStatus(saved.first)
        } else {
            _activeCajaName.update { "Caja no seleccionada" }
            _activeCaja.update { null }
            _activeCajaSecuencia.update { null }
            _sessionStatus.update { CajaSessionStatus.SIN_CAJA }
        }
    }

    override suspend fun checkCajaStatus(cajaId: String): Result<CajaStatusResponse> =
        runCatching {
            val (token, adminDb) = getContext()
            val response = apiService.checkCajaStatus(token, adminDb, cajaId)
            if (_activeCaja.value?.idCaja == cajaId) {
                if (response.isOpen && response.cajaSecuencia != null && response.cajaSecuencia.idCaja == cajaId) {
                    _activeCajaSecuencia.update { response.cajaSecuencia }
                    _sessionStatus.update { CajaSessionStatus.ABIERTA }
                } else {
                    _activeCajaSecuencia.update { null }
                    _sessionStatus.update { CajaSessionStatus.PENDIENTE_APERTURA }
                }
            }
            response
        }

    override suspend fun openCaja(request: AperturaRequest): Result<CajaStatusResponse> =
        runCatching {
            val (token, adminDb) = getContext()
            val response = apiService.openCaja(token, adminDb, request)
            if (response.isOpen && response.cajaSecuencia != null) {
                _activeCajaSecuencia.update { response.cajaSecuencia }
                _sessionStatus.update { CajaSessionStatus.ABIERTA }
            }
            response
        }

    override suspend fun closeCaja(request: CierreCajaRequest): Result<CierreCajaResponse> =
        runCatching {
            val (token, adminDb) = getContext()
            val response = apiService.closeCaja(token, adminDb, request)
            if (response.success) {
                _activeCajaSecuencia.update { null }
                _sessionStatus.update {
                    if (_activeCaja.value != null) CajaSessionStatus.PENDIENTE_APERTURA else CajaSessionStatus.SIN_CAJA
                }
            }
            response
        }

    override suspend fun getCierreSummary(): Result<CierreCajaSummary> {
        val caja = _activeCaja.value
        val sequenceId = _activeCajaSecuencia.value?.idCajaSecuencia
            ?: (caja?.let { checkCajaStatus(it.idCaja).getOrNull()?.cajaSecuencia?.idCajaSecuencia })

        return when {
            caja == null -> Result.failure(IllegalStateException("No hay caja activa"))
            sequenceId.isNullOrBlank() -> Result.failure(IllegalStateException("No hay una secuencia de caja abierta"))
            else -> loadCierreSummary(caja, sequenceId, verifyPendingInvoices = true)
        }
    }

    override suspend fun getCierreSummaryForSequence(
        caja: Caja,
        sequenceId: String,
    ): Result<CierreCajaSummary> = loadCierreSummary(caja, sequenceId, verifyPendingInvoices = false)

    private suspend fun loadCierreSummary(
        caja: Caja,
        sequenceId: String,
        verifyPendingInvoices: Boolean,
    ): Result<CierreCajaSummary> =
        runCatching {
            val (token, adminDb) = getContext()
            val response = apiService.getCajaSecuencia(
                token = token,
                companyDb = adminDb,
                idSecuencia = sequenceId,
                verifyFacturasTemporales = verifyPendingInvoices,
            )
            val dto = response.data?.takeIf { response.success }
                ?: error(response.error ?: "No se pudo cargar el cierre de caja")

            val paymentLines = dto.forma_pago
                .filter { it.monto > 0.0 && it.id > 0 }
                .map {
                    CierreCajaPaymentLine(
                        idFormaPago = it.id,
                        label = it.forma_pago ?: it.siglas ?: "Forma de pago",
                        siglas = it.siglas.orEmpty(),
                        amount = it.monto,
                    )
                }

            val totalCash = paymentLines
                .filter {
                    it.siglas.equals("CASH", ignoreCase = true) ||
                        it.siglas.equals("EF", ignoreCase = true) ||
                        it.siglas.equals("EFE", ignoreCase = true) ||
                        it.siglas.equals("EFECTIVO", ignoreCase = true)
                }.sumOf { it.amount }

            val totalCard = paymentLines
                .filter {
                    it.siglas.equals("TDC", ignoreCase = true) ||
                        it.siglas.equals("TARJETA", ignoreCase = true) ||
                        it.siglas.equals("PV", ignoreCase = true) ||
                        it.siglas.equals("POS", ignoreCase = true) ||
                        it.siglas.equals("DEBITO", ignoreCase = true) ||
                        it.siglas.equals("DB", ignoreCase = true) ||
                        it.siglas.equals("CR", ignoreCase = true) ||
                        it.siglas.equals("CREDITO", ignoreCase = true)
                }.sumOf { it.amount }

            val totalOther = (paymentLines.sumOf { it.amount } - totalCash - totalCard).coerceAtLeast(0.0)

            CierreCajaSummary(
                idCajaSecuencia = dto.id,
                idCaja = dto.id_caja,
                cajaName = dto.caja?.ifBlank { null } ?: caja.caja ?: caja.descripcion ?: "Caja",
                vendedorName = dto.vendedor.orEmpty(),
                openedAt = dto.ffecha_apertura,
                openAmount = dto.monto_efectivo_apertura,
                totalSales = dto.total_ventas,
                totalCash = totalCash,
                totalCard = totalCard,
                totalOther = totalOther,
                transactionCount = dto.cantidad_transacciones,
                expectedClose = dto.monto_cierre,
                montoEfectivoVentas = dto.monto_efectivo_ventas,
                montoEfectivoEntrada = dto.monto_efectivo_entrada,
                montoEfectivoSalida = dto.monto_efectivo_salida,
                montoEfectivoTotal = dto.monto_efectivo_total,
                montoEfectivoCierre = dto.monto_efectivo_cierre,
                montoEfectivoDiferencia = dto.monto_efectivo_diferencia,
                montoOtrosTotal = dto.monto_otros_total,
                montoOtrosCierre = dto.monto_otros_cierre,
                montoOtrosDiferencia = dto.monto_otros_diferencia,
                montoTotal = dto.monto_total,
                montoCierre = dto.monto_cierre,
                montoDiferencia = dto.monto_diferencia,
                paymentLines = paymentLines,
            )
        }

    override suspend fun setActiveCaja(caja: Caja) {
        val currentCaja = _activeCaja.value
        if (currentCaja?.idCaja != caja.idCaja) {
            _activeCajaSecuencia.update { null }
        }
        _activeCaja.update { caja }
        _activeCajaName.update { caja.displayName }
        _sessionStatus.update {
            if (_activeCajaSecuencia.value != null) CajaSessionStatus.ABIERTA else CajaSessionStatus.PENDIENTE_APERTURA
        }
        localStore.saveActiveCaja(caja.idCaja, caja.displayName)
    }

    override suspend fun setActiveCaja(id: String, name: String) {
        val currentCaja = _activeCaja.value
        if (currentCaja?.idCaja != id) {
            _activeCajaSecuencia.update { null }
            _activeCaja.update { Caja(idCaja = id, caja = name, descripcion = name) }
        }
        _activeCajaName.update { name }
        _sessionStatus.update {
            if (_activeCajaSecuencia.value != null) CajaSessionStatus.ABIERTA else CajaSessionStatus.PENDIENTE_APERTURA
        }
        localStore.saveActiveCaja(id, name)
    }

    override suspend fun clearActiveCaja() {
        _activeCaja.update { null }
        _activeCajaName.update { "Caja no seleccionada" }
        _activeCajaSecuencia.update { null }
        _sessionStatus.update { CajaSessionStatus.SIN_CAJA }
        localStore.saveActiveCaja("", "")
    }

    override suspend fun markSequenceClosed() {
        _activeCajaSecuencia.update { null }
        _sessionStatus.update {
            if (_activeCaja.value != null) CajaSessionStatus.PENDIENTE_APERTURA else CajaSessionStatus.SIN_CAJA
        }
    }

    override suspend fun getActiveCaja(): Pair<String, String>? =
        localStore.readActiveCaja()
}
