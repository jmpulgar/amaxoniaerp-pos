package com.amaxonia.pos.domain.repository

import com.amaxonia.pos.domain.model.offline.ClientFilterMode
import com.amaxonia.pos.domain.model.offline.OfflineCatalogEntry
import kotlinx.coroutines.flow.StateFlow

enum class OfflineSettingsStatus {
    IDLE,
    LOADING,
    APPLYING,
}

data class OfflineSettingsUiModel(
    val status: OfflineSettingsStatus = OfflineSettingsStatus.IDLE,
    val syncEnabled: Boolean = false,
    val productModeAll: Boolean = true,
    val clientFilterMode: ClientFilterMode = ClientFilterMode.ALL,
    val departments: List<Department> = emptyList(),
    val selectedDepartmentIds: Set<Int> = emptySet(),
    val sucursales: List<OfflineCatalogEntry> = emptyList(),
    val selectedSucursalIds: Set<String> = emptySet(),
    val zonas: List<OfflineCatalogEntry> = emptyList(),
    val selectedZoneCodes: Set<String> = emptySet(),
    val productPreview: Long? = null,
    val clientPreview: Long? = null,
    val message: String? = null,
) {
    val clientModeAll: Boolean get() = clientFilterMode == ClientFilterMode.ALL
}

/**
 * Contrato de dominio para "Ajustes Offline" (ADR-008, PLAN §18).
 * La implementación vive en data/sync y el VM solo consume esta interfaz.
 */
interface OfflineSyncSettingsRepository {
    val uiState: StateFlow<OfflineSettingsUiModel>

    fun start()

    fun setSyncEnabled(enabled: Boolean)

    fun setProductModeAll(all: Boolean)

    fun toggleDepartment(id: Int)

    fun selectAllDepartments(ids: Set<Int>)

    fun clearDepartments()

    fun setClientModeAll(all: Boolean)

    fun setClientFilterMode(mode: ClientFilterMode)

    fun toggleSucursal(id: String)

    fun selectAllSucursales(ids: Set<String>)

    fun clearSucursales()

    fun toggleZone(code: String)

    fun selectAllZones(codes: Set<String>)

    fun clearZones()

    fun refreshPreview()

    fun apply()
}

/** Carga los catálogos para los pickers de "Ajustes Offline". */
interface OfflineSettingsCatalogSource {
    suspend fun getDepartments(): Result<List<Department>>

    suspend fun getSucursales(): Result<List<OfflineCatalogEntry>>

    suspend fun getZonas(): Result<List<OfflineCatalogEntry>> = Result.success(emptyList())

    suspend fun getProductPreview(departmentIds: List<Int>): Result<Long>

    suspend fun getClientPreview(sucursalIds: List<String>): Result<Long>
}
