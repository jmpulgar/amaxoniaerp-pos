package com.amaxonia.erp.ui.caja

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.amaxonia.erp.domain.model.AperturaRequest
import com.amaxonia.erp.domain.model.Caja
import com.amaxonia.erp.domain.model.CajaSecuencia
import com.amaxonia.erp.domain.model.CierreCajaFormaPagoItem
import com.amaxonia.erp.domain.model.CierreCajaRequest
import com.amaxonia.erp.domain.model.CierreCajaSummary
import com.amaxonia.erp.domain.model.SaveCajaRequest
import com.amaxonia.erp.domain.model.Sucursal
import com.amaxonia.erp.domain.repository.CajaRepository
import com.amaxonia.erp.domain.repository.SucursalRepository
import com.amaxonia.erp.domain.util.CajaDateParser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CajaStatusBadgeInfo(
    val isOpen: Boolean,
    val isDiaAnterior: Boolean,
)

data class CajasOverviewState(
    val cajas: List<Caja> = emptyList(),
    val activeCaja: Caja? = null,
    val activeSecuencia: CajaSecuencia? = null,
    val isCajaOpen: Boolean = false,
    val isLoading: Boolean = false,
    val isAperturaDialogOpen: Boolean = false,
    val isCierreDialogOpen: Boolean = false,
    val cierreSummary: CierreCajaSummary? = null,
    val isLoadingSummary: Boolean = false,
    val isCajaDiaAnterior: Boolean = false,
    val showAvisoCajaAnterior: Boolean = false,
    val cajaFechaApertura: String? = null,
    val isRenovandoCaja: Boolean = false,
    val error: String? = null,
    val showCajaFormDialog: Boolean = false,
    val editingCaja: Caja? = null,
    val isSavingCaja: Boolean = false,
    val cajaFormError: String? = null,
    val sucursales: List<Sucursal> = emptyList(),
    val cajasStatusMap: Map<String, CajaStatusBadgeInfo> = emptyMap(),
)

class CajasOverviewViewModel(
    private val cajaRepository: CajaRepository,
    private val sucursalRepository: SucursalRepository? = null,
) : ViewModel() {
    private val _state = MutableStateFlow(CajasOverviewState())
    val state: StateFlow<CajasOverviewState> = _state.asStateFlow()

    init {
        loadCajas()
        loadSucursales()
    }

    fun loadCajas() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            val savedActive = cajaRepository.getActiveCaja()
            cajaRepository.getCajas().fold(
                onSuccess = { list ->
                    val statusMap = mutableMapOf<String, CajaStatusBadgeInfo>()
                    for (caja in list) {
                        val status = cajaRepository.checkCajaStatus(caja.idCaja).getOrNull()
                        if (status != null) {
                            val rawFecha = status.cajaSecuencia?.fechaApertura
                            val isDiaAnterior = status.cajaSecuencia != null && CajaDateParser.isFromPreviousDay(rawFecha)
                            statusMap[caja.idCaja] = CajaStatusBadgeInfo(
                                isOpen = status.isOpen,
                                isDiaAnterior = isDiaAnterior,
                            )
                        }
                    }

                    var chosen = list.firstOrNull { it.idCaja == savedActive?.first }
                    if (chosen == null) {
                        chosen = list.firstOrNull { statusMap[it.idCaja]?.isOpen == true } ?: list.firstOrNull()
                    }

                    _state.update {
                        it.copy(
                            isLoading = false,
                            cajas = list,
                            activeCaja = chosen,
                            cajasStatusMap = statusMap,
                        )
                    }
                    if (chosen != null) {
                        cajaRepository.setActiveCaja(chosen)
                        checkStatus(chosen)
                    }
                },
                onFailure = { err ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            error = err.message ?: "No se pudieron cargar las cajas",
                        )
                    }
                },
            )
        }
    }

    fun loadSucursales() {
        if (sucursalRepository == null) return
        viewModelScope.launch {
            sucursalRepository.getSucursales().fold(
                onSuccess = { list ->
                    _state.update { it.copy(sucursales = list) }
                },
                onFailure = {
                    // Ignorar error secundario de carga de sucursales
                },
            )
        }
    }

    fun openCreateCajaDialog() {
        loadSucursales()
        _state.update { it.copy(showCajaFormDialog = true, editingCaja = null, cajaFormError = null) }
    }

    fun openEditCajaDialog(caja: Caja) {
        loadSucursales()
        _state.update { it.copy(showCajaFormDialog = true, editingCaja = caja, cajaFormError = null) }
    }

    fun dismissCajaFormDialog() {
        _state.update { it.copy(showCajaFormDialog = false, editingCaja = null, cajaFormError = null) }
    }

    fun saveCaja(request: SaveCajaRequest) {
        val editing = _state.value.editingCaja
        viewModelScope.launch {
            _state.update { it.copy(isSavingCaja = true, cajaFormError = null) }
            val result =
                if (editing != null) {
                    cajaRepository.updateCaja(editing.idCaja, request)
                } else {
                    cajaRepository.createCaja(request)
                }

            result.fold(
                onSuccess = {
                    _state.update {
                        it.copy(
                            isSavingCaja = false,
                            showCajaFormDialog = false,
                            editingCaja = null,
                        )
                    }
                    loadCajas()
                },
                onFailure = { err ->
                    _state.update {
                        it.copy(
                            isSavingCaja = false,
                            cajaFormError = err.message ?: "Error al guardar la caja",
                        )
                    }
                },
            )
        }
    }

    fun selectCaja(caja: Caja) {
        viewModelScope.launch {
            cajaRepository.setActiveCaja(caja)
            _state.update {
                it.copy(
                    activeCaja = caja,
                    isCajaDiaAnterior = false,
                    showAvisoCajaAnterior = false,
                    cajaFechaApertura = null,
                )
            }
            checkStatus(caja)
        }
    }

    fun openAperturaDialog() {
        _state.update { it.copy(isAperturaDialogOpen = true) }
    }

    fun closeAperturaDialog() {
        _state.update { it.copy(isAperturaDialogOpen = false) }
    }

    fun dismissAvisoCajaAnterior() {
        _state.update { it.copy(showAvisoCajaAnterior = false) }
    }

    fun openCierreDialog() {
        _state.update { it.copy(isCierreDialogOpen = true, isLoadingSummary = true, cierreSummary = null) }
        viewModelScope.launch {
            cajaRepository.getCierreSummary().fold(
                onSuccess = { summary ->
                    _state.update {
                        it.copy(
                            isLoadingSummary = false,
                            cierreSummary = summary,
                        )
                    }
                },
                onFailure = { err ->
                    _state.update {
                        it.copy(
                            isLoadingSummary = false,
                            error = "No se pudo cargar el resumen de caja: ${err.message}",
                        )
                    }
                },
            )
        }
    }

    fun closeCierreDialog() {
        _state.update { it.copy(isCierreDialogOpen = false, cierreSummary = null) }
    }

    fun aperturarCaja(monto: Double) {
        val active = _state.value.activeCaja ?: return
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            val nextSequence = cajaRepository.getNextSecuenciaCodigo(active.idCaja).getOrNull()
            val request = AperturaRequest(
                idCaja = active.idCaja,
                montoApertura = monto,
                secuencia = nextSequence,
                serieSucursal = active.serieSucursal ?: active.serieCaja,
                idSucursal = active.idSucursal,
            )
            cajaRepository.openCaja(request).fold(
                onSuccess = { res ->
                    closeAperturaDialog()
                    val rawFecha = res.cajaSecuencia?.fechaApertura
                    val formattedFecha = rawFecha?.let(CajaDateParser::formatDisplayDate)
                    val isDiaAnterior = res.cajaSecuencia != null && CajaDateParser.isFromPreviousDay(rawFecha)
                    _state.update {
                        it.copy(
                            isLoading = false,
                            isCajaOpen = res.isOpen,
                            activeSecuencia = res.cajaSecuencia,
                            cajaFechaApertura = formattedFecha,
                            isCajaDiaAnterior = isDiaAnterior,
                            showAvisoCajaAnterior = isDiaAnterior,
                            error = null,
                        )
                    }
                    loadCajas()
                },
                onFailure = { err ->
                    _state.update { it.copy(isLoading = false, error = err.message ?: "Error al aperturar caja") }
                },
            )
        }
    }

    fun renovarCajaDiaAnterior() {
        val active = _state.value.activeCaja ?: return
        viewModelScope.launch {
            _state.update {
                it.copy(
                    showAvisoCajaAnterior = false,
                    isRenovandoCaja = true,
                    error = null,
                )
            }
            val nextSequence = cajaRepository.getNextSecuenciaCodigo(active.idCaja).getOrNull()
            val request = AperturaRequest(
                idCaja = active.idCaja,
                montoApertura = 0.0,
                secuencia = nextSequence,
                serieSucursal = active.serieSucursal ?: active.serieCaja,
                idSucursal = active.idSucursal,
            )
            cajaRepository.openCaja(request).fold(
                onSuccess = { res ->
                    val rawFecha = res.cajaSecuencia?.fechaApertura
                    val formattedFecha = rawFecha?.let(CajaDateParser::formatDisplayDate)
                    _state.update {
                        it.copy(
                            isRenovandoCaja = false,
                            isCajaOpen = res.isOpen,
                            activeSecuencia = res.cajaSecuencia,
                            cajaFechaApertura = formattedFecha,
                            isCajaDiaAnterior = false,
                            showAvisoCajaAnterior = false,
                            error = null,
                        )
                    }
                    loadCajas()
                },
                onFailure = { err ->
                    _state.update {
                        it.copy(
                            isRenovandoCaja = false,
                            error = "Error al renovar caja: ${err.message}",
                        )
                    }
                },
            )
        }
    }

    fun cerrarCaja(montoEfectivoContado: Double, observaciones: String) {
        val active = _state.value.activeCaja ?: return
        val secuencia = _state.value.activeSecuencia ?: return
        val summary = _state.value.cierreSummary

        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }

            val efectivoVentas = summary?.montoEfectivoVentas ?: 0.0
            val efectivoApertura = summary?.openAmount ?: secuencia.montoApertura
            val efectivoEntrada = summary?.montoEfectivoEntrada ?: 0.0
            val efectivoSalida = summary?.montoEfectivoSalida ?: 0.0
            val efectivoTotal = summary?.montoEfectivoTotal ?: (efectivoApertura + efectivoVentas + efectivoEntrada - efectivoSalida)
            val efectivoDiferencia = montoEfectivoContado - efectivoTotal

            val otrosTotal = summary?.montoOtrosTotal ?: 0.0
            val otrosCierre = otrosTotal
            val otrosDiferencia = 0.0

            val montoTotal = summary?.montoTotal ?: (efectivoTotal + otrosTotal)
            val montoCierre = montoEfectivoContado + otrosCierre
            val montoDiferencia = montoCierre - montoTotal

            val detalleFormaPago = summary?.paymentLines?.map { line ->
                CierreCajaFormaPagoItem(
                    id_forma_pago = line.idFormaPago,
                    monto = line.amount,
                    monto_cierre = line.amount,
                    monto_diferencia = 0.0,
                )
            } ?: emptyList()

            val request = CierreCajaRequest(
                id = secuencia.idCajaSecuencia,
                monto_efectivo_ventas = efectivoVentas,
                monto_efectivo_entrada = efectivoEntrada,
                monto_efectivo_salida = efectivoSalida,
                monto_efectivo_total = efectivoTotal,
                monto_efectivo_cierre = montoEfectivoContado,
                monto_efectivo_diferencia = efectivoDiferencia,
                monto_otros_total = otrosTotal,
                monto_otros_cierre = otrosCierre,
                monto_otros_diferencia = otrosDiferencia,
                monto_total = montoTotal,
                monto_cierre = montoCierre,
                monto_diferencia = montoDiferencia,
                detalle_formapago = detalleFormaPago,
                observacion_cierre = observaciones.trim().ifBlank { null },
            )

            cajaRepository.closeCaja(request).fold(
                onSuccess = {
                    cajaRepository.markSequenceClosed()
                    closeCierreDialog()
                    _state.update {
                        it.copy(
                            isLoading = false,
                            isCajaOpen = false,
                            activeSecuencia = null,
                            isCajaDiaAnterior = false,
                            showAvisoCajaAnterior = false,
                            error = null,
                        )
                    }
                    loadCajas()
                },
                onFailure = { err ->
                    _state.update { it.copy(isLoading = false, error = err.message ?: "Error al cerrar caja") }
                },
            )
        }
    }

    private fun checkStatus(caja: Caja) {
        viewModelScope.launch {
            cajaRepository.checkCajaStatus(caja.idCaja).onSuccess { res ->
                val rawFecha = res.cajaSecuencia?.fechaApertura
                val formattedFecha = rawFecha?.let(CajaDateParser::formatDisplayDate)
                val isDiaAnterior = res.cajaSecuencia != null && CajaDateParser.isFromPreviousDay(rawFecha)
                _state.update {
                    val updatedMap = it.cajasStatusMap.toMutableMap()
                    updatedMap[caja.idCaja] = CajaStatusBadgeInfo(
                        isOpen = res.isOpen,
                        isDiaAnterior = isDiaAnterior,
                    )
                    it.copy(
                        isCajaOpen = res.isOpen,
                        activeSecuencia = res.cajaSecuencia,
                        cajaFechaApertura = formattedFecha,
                        isCajaDiaAnterior = isDiaAnterior,
                        showAvisoCajaAnterior = isDiaAnterior,
                        cajasStatusMap = updatedMap,
                    )
                }
            }
        }
    }
}
