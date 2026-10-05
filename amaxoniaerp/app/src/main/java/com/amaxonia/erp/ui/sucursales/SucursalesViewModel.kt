package com.amaxonia.erp.ui.sucursales

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.amaxonia.erp.data.remote.SaveSucursalRequest
import com.amaxonia.erp.domain.model.Sucursal
import com.amaxonia.erp.domain.repository.SucursalRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SucursalesState(
    val sucursales: List<Sucursal> = emptyList(),
    val activeSucursalId: String? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val showFormDialog: Boolean = false,
    val editingSucursal: Sucursal? = null,
    val isSaving: Boolean = false,
    val formError: String? = null,
)

class SucursalesViewModel(
    private val sucursalRepository: SucursalRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(SucursalesState())
    val state: StateFlow<SucursalesState> = _state.asStateFlow()

    init {
        loadSucursales()
    }

    fun loadSucursales() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            val active = sucursalRepository.getActiveSucursal()
            sucursalRepository.getSucursales().fold(
                onSuccess = { list ->
                    val activeId = active?.first ?: list.firstOrNull()?.id
                    _state.update {
                        it.copy(
                            isLoading = false,
                            sucursales = list,
                            activeSucursalId = activeId,
                        )
                    }
                },
                onFailure = { err ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            error = err.message ?: "No se pudieron cargar las sucursales",
                        )
                    }
                },
            )
        }
    }

    fun selectActiveSucursal(sucursal: Sucursal) {
        viewModelScope.launch {
            sucursalRepository.setActiveSucursal(sucursal.id, sucursal.nombre)
            _state.update { it.copy(activeSucursalId = sucursal.id) }
        }
    }

    fun openCreateDialog() {
        _state.update { it.copy(showFormDialog = true, editingSucursal = null, formError = null) }
    }

    fun openEditDialog(sucursal: Sucursal) {
        _state.update { it.copy(showFormDialog = true, editingSucursal = sucursal, formError = null) }
    }

    fun dismissDialog() {
        _state.update { it.copy(showFormDialog = false, editingSucursal = null, formError = null) }
    }

    fun saveSucursal(request: SaveSucursalRequest) {
        val editing = _state.value.editingSucursal
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true, formError = null) }
            val result =
                if (editing != null) {
                    val idInt = editing.id.toIntOrNull() ?: 0
                    sucursalRepository.updateSucursal(idInt, request)
                } else {
                    sucursalRepository.createSucursal(request)
                }

            result.fold(
                onSuccess = {
                    _state.update {
                        it.copy(
                            isSaving = false,
                            showFormDialog = false,
                            editingSucursal = null,
                        )
                    }
                    loadSucursales()
                },
                onFailure = { err ->
                    _state.update {
                        it.copy(
                            isSaving = false,
                            formError = err.message ?: "Error al guardar la sucursal",
                        )
                    }
                },
            )
        }
    }
}
