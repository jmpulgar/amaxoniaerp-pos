package com.amaxonia.erp.domain.repository

import com.amaxonia.erp.data.remote.dto.DepartmentDto
import kotlinx.coroutines.flow.StateFlow

data class OfflineCatalogEntry(
    val id: String,
    val nombre: String? = null,
    val conteo: Long? = null,
)

enum class OfflineSettingsStatus {
    IDLE,
    LOADING,
    APPLYING,
}

data class OfflineSettingsUiModel(
    val status: OfflineSettingsStatus = OfflineSettingsStatus.IDLE,
    val syncEnabled: Boolean = false,
    val productModeAll: Boolean = true,
    val clientModeAll: Boolean = true,
    val departments: List<DepartmentDto> = emptyList(),
    val selectedDepartmentIds: Set<Int> = emptySet(),
    val sucursales: List<OfflineCatalogEntry> = emptyList(),
    val selectedSucursalIds: Set<String> = emptySet(),
    val productPreview: Long? = null,
    val clientPreview: Long? = null,
    val message: String? = null,
)

interface OfflineSyncSettingsRepository {
    val uiState: StateFlow<OfflineSettingsUiModel>

    fun start()

    fun setSyncEnabled(enabled: Boolean)

    fun setProductModeAll(all: Boolean)

    fun toggleDepartment(id: Int)

    fun selectAllDepartments(ids: Set<Int>)

    fun clearDepartments()

    fun setClientModeAll(all: Boolean)

    fun toggleSucursal(id: String)

    fun selectAllSucursales(ids: Set<String>)

    fun clearSucursales()

    fun refreshPreview()

    fun reload()

    fun apply()
}
