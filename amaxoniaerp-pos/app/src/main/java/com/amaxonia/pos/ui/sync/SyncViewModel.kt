package com.amaxonia.pos.ui.sync

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.amaxonia.pos.data.sync.CatalogSyncer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SyncViewModel(
    private val catalogSyncer: CatalogSyncer
) : ViewModel() {
    private val _state = MutableStateFlow(SyncState())
    val state: StateFlow<SyncState> = _state.asStateFlow()

    fun startSyncIfNeeded(onCompleted: () -> Unit) {
        viewModelScope.launch {
            if (catalogSyncer.isInitialSyncCompleted()) {
                _state.update { it.copy(isCompleted = true) }
                onCompleted()
                return@launch
            }
            _state.update { it.copy(isLoading = true, error = null) }
            catalogSyncer.syncAll().fold(
                onSuccess = {
                    _state.update { it.copy(isLoading = false, isCompleted = true) }
                    onCompleted()
                },
                onFailure = { error ->
                    _state.update {
                        it.copy(isLoading = false, error = error.message ?: "Error al sincronizar")
                    }
                }
            )
        }
    }

    fun retry(onCompleted: () -> Unit) {
        startSyncIfNeeded(onCompleted)
    }
}
