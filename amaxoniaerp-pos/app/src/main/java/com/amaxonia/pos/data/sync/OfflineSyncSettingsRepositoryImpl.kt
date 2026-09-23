package com.amaxonia.pos.data.sync

import com.amaxonia.pos.data.local.LocalStore
import com.amaxonia.pos.data.local.db.AppDatabase
import com.amaxonia.pos.data.local.readCompanySession
import com.amaxonia.pos.data.remote.SyncApi
import com.amaxonia.pos.data.remote.SyncScopeQuery
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
            _uiState.update {
                it.copy(
                    status = OfflineSettingsStatus.IDLE,
                    syncEnabled = saved.enabled,
                    productModeAll = saved.allProducts,
                    clientModeAll = saved.allClients,
                    selectedDepartmentIds = saved.departmentIds,
                    selectedSucursalIds = saved.branchIds.map { id -> id.toString() }.toSet(),
                    departments = departments,
                    sucursales = sucursales,
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
        _uiState.update { current ->
            current.copy(
                clientModeAll = all,
                selectedSucursalIds = if (all) emptySet() else current.selectedSucursalIds,
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
                clientModeAll = false,
            )
        }
        refreshPreview()
    }

    override fun selectAllSucursales(ids: Set<String>) {
        _uiState.update { current ->
            current.copy(
                selectedSucursalIds = ids,
                clientModeAll = false,
            )
        }
        refreshPreview()
    }

    override fun clearSucursales() {
        _uiState.update { current ->
            current.copy(
                selectedSucursalIds = emptySet(),
                clientModeAll = false,
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
            val branchIds = if (current.clientModeAll) null else current.selectedSucursalIds.mapNotNull { it.toIntOrNull() }
            val productPreview =
                if (current.productModeAll) {
                    null
                } else {
                    runCatching { syncApi.scopePreview(token, "PRODUCT", SyncScopeQuery(deptIds, branchIds)) }.getOrNull()?.count
                }
            val clientPreview =
                if (current.clientModeAll) {
                    null
                } else {
                    runCatching { syncApi.scopePreview(token, "CLIENT", SyncScopeQuery(deptIds, branchIds)) }.getOrNull()?.count
                }
            _uiState.update { it.copy(productPreview = productPreview, clientPreview = clientPreview) }
        }
    }

    override fun apply() {
        val current = _uiState.value
        if (current.status == OfflineSettingsStatus.APPLYING) return
        _uiState.update { it.copy(status = OfflineSettingsStatus.APPLYING) }
        val departmentIds = if (current.productModeAll) emptySet() else current.selectedDepartmentIds
        val branchIds = if (current.clientModeAll) emptySet() else current.selectedSucursalIds.mapNotNull { it.toIntOrNull() }.toSet()
        val newScope =
            OfflineSyncScope(
                enabled = current.syncEnabled,
                departmentIds = departmentIds,
                branchIds = branchIds,
            )
        ioScope.launch {
            scopeStore.save(newScope)
            if (current.syncEnabled) {
                val purge = syncEngine.purgeForScope(newScope)
                if (purge is SyncEngine.Outcome.Error) {
                    _uiState.update {
                        it.copy(
                            status = OfflineSettingsStatus.IDLE,
                            message = purge.message,
                        )
                    }
                    return@launch
                }
                _uiState.update {
                    it.copy(message = "Sincronizando catálogo offline...")
                }
                val syncResult = syncEngine.runBootstrap()
                bootstrapEnqueuer()
                _uiState.update {
                    it.copy(
                        status = OfflineSettingsStatus.IDLE,
                        message =
                            when (syncResult) {
                                is SyncEngine.Outcome.Success ->
                                    "Ajustes de visibilidad guardados y catálogo sincronizado (${syncResult.changesApplied} elementos descargados)."
                                is SyncEngine.Outcome.Error ->
                                    "Ajustes de visibilidad guardados. Error al descargar: ${syncResult.message}. Sincronización re-encolada en segundo plano."
                            },
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
}
