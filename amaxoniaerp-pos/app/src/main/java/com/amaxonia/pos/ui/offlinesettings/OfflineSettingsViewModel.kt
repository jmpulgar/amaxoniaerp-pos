package com.amaxonia.pos.ui.offlinesettings

import androidx.lifecycle.ViewModel
import com.amaxonia.pos.domain.model.offline.ClientFilterMode
import com.amaxonia.pos.domain.repository.OfflineSettingsUiModel
import com.amaxonia.pos.domain.repository.OfflineSyncSettingsRepository
import kotlinx.coroutines.flow.StateFlow

class OfflineSettingsViewModel(
    private val repository: OfflineSyncSettingsRepository,
) : ViewModel() {
    val uiState: StateFlow<OfflineSettingsUiModel> = repository.uiState

    fun start() = repository.start()

    fun setSyncEnabled(enabled: Boolean) = repository.setSyncEnabled(enabled)

    fun setProductModeAll(all: Boolean) = repository.setProductModeAll(all)

    fun toggleDepartment(id: Int) = repository.toggleDepartment(id)

    fun selectAllDepartments(ids: Set<Int>) = repository.selectAllDepartments(ids)

    fun clearDepartments() = repository.clearDepartments()

    fun setClientModeAll(all: Boolean) = repository.setClientModeAll(all)

    fun setClientFilterMode(mode: ClientFilterMode) = repository.setClientFilterMode(mode)

    fun toggleSucursal(id: String) = repository.toggleSucursal(id)

    fun selectAllSucursales(ids: Set<String>) = repository.selectAllSucursales(ids)

    fun clearSucursales() = repository.clearSucursales()

    fun toggleZone(code: String) = repository.toggleZone(code)

    fun selectAllZones(codes: Set<String>) = repository.selectAllZones(codes)

    fun clearZones() = repository.clearZones()

    fun refreshPreview() = repository.refreshPreview()

    fun apply() = repository.apply()
}
