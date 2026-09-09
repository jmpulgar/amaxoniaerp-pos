package com.amaxonia.pos.ui.offlinesettings

import androidx.lifecycle.ViewModel
import com.amaxonia.pos.domain.repository.OfflineSettingsStatus
import com.amaxonia.pos.domain.repository.OfflineSettingsUiModel
import com.amaxonia.pos.domain.repository.OfflineSyncSettingsRepository
import kotlinx.coroutines.flow.StateFlow

class OfflineSettingsViewModel(
    private val repository: OfflineSyncSettingsRepository,
) : ViewModel() {

    val uiState: StateFlow<OfflineSettingsUiModel> = repository.uiState

    fun start() = repository.start()

    fun setProductModeAll(all: Boolean) = repository.setProductModeAll(all)

    fun toggleDepartment(id: Int) = repository.toggleDepartment(id)

    fun setClientModeAll(all: Boolean) = repository.setClientModeAll(all)

    fun toggleSucursal(id: String) = repository.toggleSucursal(id)

    fun refreshPreview() = repository.refreshPreview()

    fun apply() = repository.apply()
}
