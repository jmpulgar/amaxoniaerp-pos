package com.amaxonia.pos.domain.repository

import com.amaxonia.pos.domain.model.offline.OfflineCatalogEntry
import com.amaxonia.pos.domain.model.offline.OfflineScopeSelection
import kotlinx.coroutines.flow.StateFlow

enum class OfflineSettingsStatus {
    IDLE,
    LOADING,
    APPLYING,
}

data class OfflineSettingsUiModel(
    val status: OfflineSettingsStatus = OfflineSettingsStatus.IDLE,
    val productModeAll: Boolean = true,
    val clientModeAll: Boolean = true,
    val departments: List<Department> = emptyList(),
    val selectedDepartmentIds: Set<Int> = emptySet(),
    val sucursales: List<OfflineCatalogEntry> = emptyList(),
    val selectedSucursalIds: Set<String> = emptySet(),
    val productPreview: Long? = null,
    val clientPreview: Long? = null,
    val message: String? = null,
)

/**
 * Contrato de dominio para "Ajustes Offline" (ADR-008, PLAN §18).
 * La implementación vive en data/sync y el VM solo consume esta interfaz.
 */
interface OfflineSyncSettingsRepository {
    val uiState: StateFlow<OfflineSettingsUiModel>

    fun start()

    fun setProductModeAll(all: Boolean)

    fun toggleDepartment(id: Int)

    fun setClientModeAll(all: Boolean)

    fun toggleSucursal(id: String)

    fun refreshPreview()

    fun apply()
}

/** Carga los catálogos para los pickers de "Ajustes Offline". */
interface OfflineSettingsCatalogSource {
    suspend fun getDepartments(): Result<List<Department>>

    suspend fun getSucursales(): Result<List<OfflineCatalogEntry>>

    suspend fun getProductPreview(departmentIds: List<Int>): Result<Long>

    suspend fun getClientPreview(sucursalIds: List<String>): Result<Long>
}
