package com.amaxonia.pos.data.sync

import com.amaxonia.pos.data.local.LocalStore
import com.amaxonia.pos.data.local.db.AppDatabase
import com.amaxonia.pos.data.local.readCompanySession
import com.amaxonia.pos.data.remote.SyncApi
import com.amaxonia.pos.data.remote.SyncScopeQuery
import com.amaxonia.pos.domain.model.offline.ClientFilterMode
import com.amaxonia.pos.domain.model.offline.OfflineCatalogEntry
import com.amaxonia.pos.domain.model.tenant.SaleTenant
import com.amaxonia.pos.domain.repository.OfflineSettingsStatus
import com.amaxonia.pos.domain.repository.OfflineSettingsUiModel
import com.amaxonia.pos.domain.repository.OfflineSyncSettingsRepository
import com.amaxonia.pos.domain.repository.ProductRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Implementación de [OfflineSyncSettingsRepository] que orquesta el motor
 * de sync, el store de alcance y la API del backend.
 */
class OfflineSyncSettingsRepositoryImpl(
    private val database: AppDatabase,
    private val syncApi: SyncApi,
    private val localStore: LocalStore,
    private val productRepository: ProductRepository,
    private val scopeStore: OfflineSyncSettingsStore,
    private val bootstrapEnqueuer: () -> Unit,
) : OfflineSyncSettingsRepository {
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val syncEngine =
        SyncEngine(
            database = database,
            api = syncApi,
            scopeProvider = { scopeStore.load() },
            tenantProvider = { localStore.readCompanySession()?.let { SaleTenant.idFor(it.company.id) } },
            tokenProvider = { localStore.readCompanySession()?.token },
        )

    private val _uiState = MutableStateFlow(OfflineSettingsUiModel(status = OfflineSettingsStatus.LOADING))
    override val uiState: StateFlow<OfflineSettingsUiModel> = _uiState

    private var initialized = false

    override fun start() {
        if (initialized) return
        initialized = true
        ioScope.launch {
            val saved = scopeStore.load()
            val departments = productRepository.getDepartments().getOrDefault(emptyList())
            val token = localStore.readCompanySession()?.token.orEmpty()
            val sucursales =
                runCatching { syncApi.sucursales(token) }
                    .getOrDefault(emptyList())
                    .map { entry -> OfflineCatalogEntry(id = entry.id, nombre = entry.nombre) }
            val zonas =
                runCatching { syncApi.zonas(token) }
                    .getOrDefault(emptyList())
                    .map { entry -> OfflineCatalogEntry(id = entry.id, nombre = entry.nombre) }
            _uiState.update {
                it.copy(
                    status = OfflineSettingsStatus.IDLE,
                    syncEnabled = saved.enabled,
                    productModeAll = saved.allProducts,
                    clientFilterMode = saved.clientFilterMode,
                    selectedDepartmentIds = saved.departmentIds,
                    selectedSucursalIds = saved.branchIds.map { id -> id.toString() }.toSet(),
                    selectedZoneCodes = saved.zoneCodes,
                    departments = departments,
                    sucursales = sucursales,
                    zonas = zonas,
                )
            }
        }
    }

    override fun setSyncEnabled(enabled: Boolean) {
        _uiState.update { it.copy(syncEnabled = enabled) }
    }

    override fun setProductModeAll(all: Boolean) {
        _uiState.update { current ->
            current.copy(
                productModeAll = all,
                selectedDepartmentIds = if (all) emptySet() else current.selectedDepartmentIds,
            )
        }
        refreshPreview()
    }

    override fun toggleDepartment(id: Int) {
        _uiState.update { current ->
            val selected =
                if (id in current.selectedDepartmentIds) {
                    current.selectedDepartmentIds - id
                } else {
                    current.selectedDepartmentIds + id
                }
            current.copy(
                selectedDepartmentIds = selected,
                productModeAll = false,
            )
        }
        refreshPreview()
    }

    override fun selectAllDepartments(ids: Set<Int>) {
        _uiState.update { current ->
            current.copy(
                selectedDepartmentIds = ids,
                productModeAll = false,
            )
        }
        refreshPreview()
    }

    override fun clearDepartments() {
        _uiState.update { current ->
            current.copy(
                selectedDepartmentIds = emptySet(),
                productModeAll = false,
            )
        }
        refreshPreview()
    }

    override fun setClientModeAll(all: Boolean) {
        setClientFilterMode(if (all) ClientFilterMode.ALL else ClientFilterMode.SUCURSAL)
    }

    override fun setClientFilterMode(mode: ClientFilterMode) {
        _uiState.update { current ->
            current.copy(
                clientFilterMode = mode,
                selectedSucursalIds = if (mode == ClientFilterMode.ALL) emptySet() else current.selectedSucursalIds,
                selectedZoneCodes = if (mode == ClientFilterMode.ALL) emptySet() else current.selectedZoneCodes,
            )
        }
        refreshPreview()
    }

    override fun toggleSucursal(id: String) {
        _uiState.update { current ->
            val selected =
                if (id in current.selectedSucursalIds) {
                    current.selectedSucursalIds - id
                } else {
                    current.selectedSucursalIds + id
                }
            current.copy(
                selectedSucursalIds = selected,
                clientFilterMode = ClientFilterMode.SUCURSAL,
            )
        }
        refreshPreview()
    }

    override fun selectAllSucursales(ids: Set<String>) {
        _uiState.update { current ->
            current.copy(
                selectedSucursalIds = ids,
                clientFilterMode = ClientFilterMode.SUCURSAL,
            )
        }
        refreshPreview()
    }

    override fun clearSucursales() {
        _uiState.update { current ->
            current.copy(
                selectedSucursalIds = emptySet(),
                clientFilterMode = ClientFilterMode.SUCURSAL,
            )
        }
        refreshPreview()
    }

    override fun toggleZone(code: String) {
        _uiState.update { current ->
            val selected =
                if (code in current.selectedZoneCodes) {
                    current.selectedZoneCodes - code
                } else {
                    current.selectedZoneCodes + code
                }
            current.copy(
                selectedZoneCodes = selected,
                clientFilterMode = ClientFilterMode.ZONE,
            )
        }
        refreshPreview()
    }

    override fun selectAllZones(codes: Set<String>) {
        _uiState.update { current ->
            current.copy(
                selectedZoneCodes = codes,
                clientFilterMode = ClientFilterMode.ZONE,
            )
        }
        refreshPreview()
    }

    override fun clearZones() {
        _uiState.update { current ->
            current.copy(
                selectedZoneCodes = emptySet(),
                clientFilterMode = ClientFilterMode.ZONE,
            )
        }
        refreshPreview()
    }

    override fun refreshPreview() {
        val current = _uiState.value
        if (current.productModeAll && current.clientModeAll) return
        ioScope.launch {
            val token = localStore.readCompanySession()?.token ?: return@launch
            val deptIds = if (current.productModeAll) null else current.selectedDepartmentIds.toList()
            val branchIds =
                if (current.clientFilterMode == ClientFilterMode.SUCURSAL) {
                    current.selectedSucursalIds.mapNotNull { it.toIntOrNull() }
                } else {
                    null
                }
            val zoneCodes =
                if (current.clientFilterMode == ClientFilterMode.ZONE) {
                    current.selectedZoneCodes.toList()
                } else {
                    null
                }
            val productPreview =
                if (current.productModeAll) {
                    null
                } else {
                    runCatching {
                        syncApi.scopePreview(token, "PRODUCT", SyncScopeQuery(deptIds, branchIds, zoneCodes))
                    }.getOrNull()?.count
                }
            val clientPreview =
                if (current.clientFilterMode == ClientFilterMode.ALL) {
                    null
                } else {
                    runCatching {
                        syncApi.scopePreview(token, "CLIENT", SyncScopeQuery(deptIds, branchIds, zoneCodes))
                    }.getOrNull()?.count
                }
            _uiState.update { it.copy(productPreview = productPreview, clientPreview = clientPreview) }
        }
    }

    override fun apply() {
        val current = _uiState.value
        if (current.status == OfflineSettingsStatus.APPLYING) return
        _uiState.update { it.copy(status = OfflineSettingsStatus.APPLYING) }
        val newScope = buildNewScope(current)
        ioScope.launch {
            scopeStore.save(newScope)
            if (current.syncEnabled) {
                val purge = syncEngine.purgeForScope(newScope)
                if (purge is SyncEngine.Outcome.Error) {
                    _uiState.update { it.copy(status = OfflineSettingsStatus.IDLE, message = purge.message) }
                    return@launch
                }
                _uiState.update { it.copy(message = "Sincronizando catálogo offline...") }
                val syncResult = syncEngine.runBootstrap()
                bootstrapEnqueuer()
                _uiState.update {
                    it.copy(
                        status = OfflineSettingsStatus.IDLE,
                        message = syncResultMessage(syncResult),
                    )
                }
            } else {
                syncEngine.purgeForScope(OfflineSyncScope(enabled = false))
                _uiState.update {
                    it.copy(
                        status = OfflineSettingsStatus.IDLE,
                        message = "Ajustes de visibilidad guardados. Modo offline desactivado (los datos no se descargarán al terminal).",
                    )
                }
            }
        }
    }

    private fun buildNewScope(current: OfflineSettingsUiModel): OfflineSyncScope {
        val departmentIds = if (current.productModeAll) emptySet() else current.selectedDepartmentIds
        val branchIds =
            if (current.clientFilterMode == ClientFilterMode.SUCURSAL) {
                current.selectedSucursalIds.mapNotNull { it.toIntOrNull() }.toSet()
            } else {
                emptySet()
            }
        val zoneCodes =
            if (current.clientFilterMode == ClientFilterMode.ZONE) {
                current.selectedZoneCodes
            } else {
                emptySet()
            }
        return OfflineSyncScope(
            enabled = current.syncEnabled,
            departmentIds = departmentIds,
            branchIds = branchIds,
            zoneCodes = zoneCodes,
            clientFilterMode = current.clientFilterMode,
        )
    }

    private fun syncResultMessage(syncResult: SyncEngine.Outcome): String =
        when (syncResult) {
            is SyncEngine.Outcome.Success ->
                "Ajustes de visibilidad guardados y catálogo sincronizado " +
                    "(${syncResult.changesApplied} elementos descargados)."
            is SyncEngine.Outcome.Error ->
                "Ajustes de visibilidad guardados. Error al descargar: " +
                    "${syncResult.message}. Sincronización re-encolada en segundo plano."
        }
}
