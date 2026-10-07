package com.amaxonia.erp.ui.offlinesettings

import com.amaxonia.erp.data.remote.dto.DepartmentDto
import com.amaxonia.erp.domain.repository.OfflineCatalogEntry
import com.amaxonia.erp.domain.repository.OfflineSettingsStatus
import com.amaxonia.erp.domain.repository.OfflineSettingsUiModel
import com.amaxonia.erp.domain.repository.OfflineSyncSettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class OfflineSettingsViewModelTest {

    private class FakeOfflineSyncSettingsRepository : OfflineSyncSettingsRepository {
        val _uiState = MutableStateFlow(
            OfflineSettingsUiModel(
                status = OfflineSettingsStatus.IDLE,
                syncEnabled = false,
                productModeAll = true,
                clientModeAll = true,
                selectedDepartmentIds = emptySet(),
                selectedSucursalIds = emptySet(),
                departments = listOf(
                    DepartmentDto(id = 1, name = "Bebidas"),
                    DepartmentDto(id = 2, name = "Comestibles"),
                ),
                sucursales = listOf(
                    OfflineCatalogEntry(id = "1", nombre = "Principal"),
                    OfflineCatalogEntry(id = "2", nombre = "Sucursal Norte"),
                ),
            )
        )
        override val uiState: StateFlow<OfflineSettingsUiModel> = _uiState.asStateFlow()

        var started = false
        var applied = false
        var previewRefreshed = false

        override fun start() {
            started = true
        }

        override fun setSyncEnabled(enabled: Boolean) {
            _uiState.value = _uiState.value.copy(syncEnabled = enabled)
        }

        override fun setProductModeAll(all: Boolean) {
            _uiState.value = _uiState.value.copy(
                productModeAll = all,
                selectedDepartmentIds = if (all) emptySet() else _uiState.value.selectedDepartmentIds,
            )
        }

        override fun toggleDepartment(id: Int) {
            val current = _uiState.value.selectedDepartmentIds
            val updated = if (id in current) current - id else current + id
            _uiState.value = _uiState.value.copy(selectedDepartmentIds = updated, productModeAll = false)
        }

        override fun selectAllDepartments(ids: Set<Int>) {
            _uiState.value = _uiState.value.copy(selectedDepartmentIds = ids, productModeAll = false)
        }

        override fun clearDepartments() {
            _uiState.value = _uiState.value.copy(selectedDepartmentIds = emptySet(), productModeAll = false)
        }

        override fun setClientModeAll(all: Boolean) {
            _uiState.value = _uiState.value.copy(
                clientModeAll = all,
                selectedSucursalIds = if (all) emptySet() else _uiState.value.selectedSucursalIds,
            )
        }

        override fun toggleSucursal(id: String) {
            val current = _uiState.value.selectedSucursalIds
            val updated = if (id in current) current - id else current + id
            _uiState.value = _uiState.value.copy(selectedSucursalIds = updated, clientModeAll = false)
        }

        override fun selectAllSucursales(ids: Set<String>) {
            _uiState.value = _uiState.value.copy(selectedSucursalIds = ids, clientModeAll = false)
        }

        override fun clearSucursales() {
            _uiState.value = _uiState.value.copy(selectedSucursalIds = emptySet(), clientModeAll = false)
        }

        override fun refreshPreview() {
            previewRefreshed = true
        }

        var reloaded = false

        override fun reload() {
            reloaded = true
        }

        override fun apply() {
            applied = true
        }
    }

    private lateinit var fakeRepository: FakeOfflineSyncSettingsRepository
    private lateinit var viewModel: OfflineSettingsViewModel

    @Before
    fun setup() {
        fakeRepository = FakeOfflineSyncSettingsRepository()
        viewModel = OfflineSettingsViewModel(fakeRepository)
    }

    @Test
    fun `reload delegates to repository`() {
        assertFalse(fakeRepository.reloaded)
        viewModel.reload()
        assertTrue(fakeRepository.reloaded)
    }

    @Test
    fun `start delegates to repository`() {
        assertFalse(fakeRepository.started)
        viewModel.start()
        assertTrue(fakeRepository.started)
    }

    @Test
    fun `setSyncEnabled updates master state`() {
        assertFalse(viewModel.uiState.value.syncEnabled)
        viewModel.setSyncEnabled(true)
        assertTrue(viewModel.uiState.value.syncEnabled)
    }

    @Test
    fun `toggleDepartment changes selection and turns off mode all`() {
        assertTrue(viewModel.uiState.value.productModeAll)
        viewModel.toggleDepartment(1)
        assertFalse(viewModel.uiState.value.productModeAll)
        assertTrue(1 in viewModel.uiState.value.selectedDepartmentIds)

        viewModel.toggleDepartment(1)
        assertFalse(1 in viewModel.uiState.value.selectedDepartmentIds)
    }

    @Test
    fun `selectAllDepartments selects all provided ids`() {
        viewModel.selectAllDepartments(setOf(1, 2))
        assertEquals(setOf(1, 2), viewModel.uiState.value.selectedDepartmentIds)
        assertFalse(viewModel.uiState.value.productModeAll)

        viewModel.clearDepartments()
        assertTrue(viewModel.uiState.value.selectedDepartmentIds.isEmpty())
    }

    @Test
    fun `toggleSucursal changes sucursal selection and turns off clientModeAll`() {
        assertTrue(viewModel.uiState.value.clientModeAll)
        viewModel.toggleSucursal("1")
        assertFalse(viewModel.uiState.value.clientModeAll)
        assertTrue("1" in viewModel.uiState.value.selectedSucursalIds)

        viewModel.selectAllSucursales(setOf("1", "2"))
        assertEquals(setOf("1", "2"), viewModel.uiState.value.selectedSucursalIds)

        viewModel.clearSucursales()
        assertTrue(viewModel.uiState.value.selectedSucursalIds.isEmpty())
    }

    @Test
    fun `apply and refreshPreview delegate to repository`() {
        assertFalse(fakeRepository.applied)
        assertFalse(fakeRepository.previewRefreshed)

        viewModel.refreshPreview()
        assertTrue(fakeRepository.previewRefreshed)

        viewModel.apply()
        assertTrue(fakeRepository.applied)
    }
}
