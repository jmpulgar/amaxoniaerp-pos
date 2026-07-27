package com.amaxonia.pos.ui.mesas

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.amaxonia.pos.domain.model.mesas.Area
import com.amaxonia.pos.domain.model.mesas.Lienzo
import com.amaxonia.pos.domain.model.mesas.SalonDistribucion
import com.amaxonia.pos.domain.model.mesas.SelectedTable
import com.amaxonia.pos.domain.repository.ActiveCajaReader
import com.amaxonia.pos.domain.repository.AreaRepository
import com.amaxonia.pos.domain.repository.ConnectivityStatus
import com.amaxonia.pos.domain.repository.SelectedTableHolder
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Áreas y mesas de la sucursal de la caja activa.
 *
 * Alcance de esta fase: **solo consulta**. No hay ninguna llamada de escritura, no se crean
 * ventas ni pedidos y no se modifica el estado de ninguna mesa. La selección de mesa vive solo
 * en memoria y se limpia al cambiar área, caja o empresa, o cuando la mesa deja de estar
 * disponible en el área.
 *
 * El plano visual reacciona al contrato de mesas: cuando el área trae [Lienzo] y mesas con
 * geometría válida, se ofrece el modo plano; si no, se cae a la vista de lista.
 */
class AreasMesasViewModel(
    private val areaRepository: AreaRepository,
    private val activeCajaReader: ActiveCajaReader,
    private val connectivity: ConnectivityStatus,
    private val selectedTableHolder: SelectedTableHolder,
) : ViewModel() {
    private val _state = MutableStateFlow(AreasMesasState())
    val state: StateFlow<AreasMesasState> = _state.asStateFlow()

    /** Carga de mesas en vuelo; se cancela al cambiar de área para no pintar datos obsoletos. */
    private var mesasJob: Job? = null
    private var areasJob: Job? = null

    init {
        observeCaja()
        restoreSelection()
        loadAreas()
    }

    /** Refresco manual desde la barra superior. */
    fun onRefresh() {
        loadAreas(reselectArea = _state.value.selectedAreaId)
    }

    fun onRetryAreas() {
        loadAreas(reselectArea = _state.value.selectedAreaId)
    }

    fun onRetryMesas() {
        _state.value.selectedAreaId?.let { loadMesas(it) }
    }

    /**
     * Cambio de área: limpia la mesa seleccionada (la selección es por área), pide las mesas
     * nuevas y calcula si esa área permite plano. El modo de vista solo se resetea si la nueva
     * área no soporta plano; si lo soporta se respeta [SalonViewMode.PLANO] por defecto.
     */
    fun onAreaSelected(areaId: Int) {
        val current = _state.value
        if (current.selectedAreaId == areaId && current.mesasError == null) return
        if (!current.areAreaChipsEnabled) return

        // La selección de mesa pertenece al contexto del área anterior: al cambiar, se borra.
        selectedTableHolder.clear()
        _state.update {
            it.copy(
                selectedAreaId = areaId,
                selectedMesaId = null,
                mesas = emptyList(),
                lienzo = Lienzo(),
                imagenUrl = null,
                hasDistribucionValida = false,
                viewMode = SalonViewMode.PLANO,
            )
        }
        loadMesas(areaId)
    }

    /**
     * Cambia el modo de visualización del área actual. Si el áreano tiene distribución válida
     * no se permite cambiar a plano: el selector de modo solo aparece en ese caso con lista.
     */
    fun onViewModeChanged(mode: SalonViewMode) {
        val current = _state.value
        if (mode == SalonViewMode.PLANO && !current.hasDistribucionValida) return
        _state.update { it.copy(viewMode = mode) }
    }

    /**
     * Guarda la mesa elegida en memoria y nada más: sin apertura, sin venta y sin escrituras.
     */
    fun onMesaSelected(mesaId: Int) {
        val current = _state.value
        val selection =
            current.sucursalId?.let { sucursalId ->
                val area = current.selectedArea
                val mesa = current.mesas.firstOrNull { it.id == mesaId }
                if (area != null && mesa != null) {
                    SelectedTable(sucursalId = sucursalId, area = area, mesa = mesa)
                } else {
                    null
                }
            } ?: return

        _state.update { it.copy(selectedMesaId = mesaId) }
        selectedTableHolder.select(selection)
    }

    fun onClearSelection() {
        _state.update { it.copy(selectedMesaId = null) }
        selectedTableHolder.clear()
    }

    private fun restoreSelection() {
        val selected = selectedTableHolder.selectedTable.value ?: return
        _state.update {
            it.copy(selectedAreaId = selected.area.id, selectedMesaId = selected.mesa.id)
        }
    }

    /**
     * Cuando la caja activa cambia o se quita, la selección de mesa deja de ser válida: la
     * nueva caja puede estar en otra sucursal y la configuración de áreas cambia. Se limpia sin
     * persistir nada.
     */
    private var lastCajaId: String? = null
    private fun observeCaja() {
        viewModelScope.launch {
            activeCajaReader.activeCaja.collect { caja ->
                val newId = caja?.idCaja
                if (lastCajaId != null && lastCajaId != newId) {
                    selectedTableHolder.clear()
                    _state.update {
                        it.copy(
                            selectedAreaId = null,
                            selectedMesaId = null,
                            mesas = emptyList(),
                            lienzo = Lienzo(),
                            imagenUrl = null,
                            hasDistribucionValida = false,
                        )
                    }
                }
                lastCajaId = newId
            }
        }
    }

    private fun loadAreas(reselectArea: Int? = null) {
        // Anti doble pulsación: si ya hay una carga de áreas en vuelo se ignora la nueva.
        if (_state.value.isLoadingAreas) return

        val caja = activeCajaReader.activeCaja.value
        val cajaId = caja?.idCaja
        if (cajaId.isNullOrBlank()) {
            markCajaRequired()
            return
        }

        val branchName = caja.sucursalNombre?.takeIf { it.isNotBlank() }.orEmpty()

        areasJob?.cancel()
        areasJob =
            viewModelScope.launch {
                _state.update {
                    it.copy(
                        requiresCaja = false,
                        isLoadingAreas = true,
                        areasError = null,
                        isOffline = !connectivity.isOnline(),
                        sucursalNombre = branchName,
                    )
                }

                areaRepository.getAreas(cajaId).fold(
                    onSuccess = { result ->
                        val targetArea = resolveTargetArea(result.areas, reselectArea)
                        _state.update {
                            it.copy(
                                isLoadingAreas = false,
                                areas = result.areas,
                                sucursalId = result.sucursalId,
                                selectedAreaId = targetArea?.id,
                                showingCachedData = result.fromCache,
                                areasError = null,
                                mesas = if (targetArea == null) emptyList() else it.mesas,
                            )
                        }
                        if (targetArea != null) {
                            loadMesas(targetArea.id)
                        }
                    },
                    onFailure = { error ->
                        _state.update {
                            it.copy(
                                isLoadingAreas = false,
                                areasError = error.message ?: "No se pudieron consultar las áreas",
                            )
                        }
                    },
                )
            }
    }

    /** Sin caja no hay sucursal: se limpia todo y la pantalla invita a seleccionar una caja. */
    private fun markCajaRequired() {
        selectedTableHolder.clear()
        _state.update {
            it.copy(
                requiresCaja = true,
                isLoadingAreas = false,
                areas = emptyList(),
                mesas = emptyList(),
                selectedAreaId = null,
                selectedMesaId = null,
                areasError = null,
                sucursalNombre = "",
                sucursalId = null,
                lienzo = Lienzo(),
                imagenUrl = null,
                hasDistribucionValida = false,
            )
        }
    }

    /** Conserva el área abierta si sigue existiendo tras refrescar; si no, cae en la primera. */
    private fun resolveTargetArea(
        areas: List<Area>,
        reselectArea: Int?,
    ): Area? = areas.firstOrNull { it.id == reselectArea } ?: areas.firstOrNull()

    private fun loadMesas(areaId: Int) {
        val cajaId = activeCajaReader.activeCaja.value?.idCaja
        if (cajaId.isNullOrBlank()) return

        mesasJob?.cancel()
        mesasJob =
            viewModelScope.launch {
                _state.update {
                    it.copy(isLoadingMesas = true, mesasError = null, isOffline = !connectivity.isOnline())
                }

                areaRepository.getMesas(cajaId, areaId).fold(
                    onSuccess = { result ->
                        _state.update { current ->
                            // Descarta una respuesta tardía de un área que ya no es la seleccionada.
                            if (current.selectedAreaId != areaId) {
                                current
                            } else {
                                val seleccionSigueVisible =
                                    current.selectedMesaId?.let { selected ->
                                        result.mesas.any { it.id == selected }
                                    } ?: true
                                if (!seleccionSigueVisible) {
                                    // La mesa se desactivó en el administrativo: deixar de ser selec.
                                    selectedTableHolder.clear()
                                }
                                val distribucionValida =
                                    SalonDistribucion.esValida(result.mesas, result.lienzo)
                                val viewMode =
                                    when {
                                        !distribucionValida -> SalonViewMode.LISTA
                                        // Si el área no soporta plano, fuerzo LISTA; si ya está en
                                        // LISTA por elección del usuario, lo respeto (mientras el
                                        // área soporte plano).
                                        else ->
                                            if (current.viewMode == SalonViewMode.LISTA &&
                                                distribucionValida
                                            ) {
                                                SalonViewMode.LISTA
                                            } else {
                                                SalonViewMode.PLANO
                                            }
                                    }
                                current.copy(
                                    isLoadingMesas = false,
                                    mesas = result.mesas,
                                    lienzo = result.lienzo,
                                    imagenUrl = result.imagenUrl,
                                    hasDistribucionValida = distribucionValida,
                                    viewMode = viewMode,
                                    showingCachedData = current.showingCachedData || result.fromCache,
                                    mesasError = null,
                                    // Una mesa que desaparece del área deja de estar seleccionada.
                                    selectedMesaId =
                                        current.selectedMesaId?.takeIf { selected ->
                                            result.mesas.any { it.id == selected }
                                        },
                                )
                            }
                        }
                    },
                    onFailure = { error ->
                        _state.update { current ->
                            if (current.selectedAreaId != areaId) {
                                current
                            } else {
                                current.copy(
                                    isLoadingMesas = false,
                                    mesasError = error.message ?: "No se pudieron consultar las mesas",
                                )
                            }
                        }
                    },
                )
            }
    }
}
