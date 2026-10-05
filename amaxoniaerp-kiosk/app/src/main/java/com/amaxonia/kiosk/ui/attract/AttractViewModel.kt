package com.amaxonia.kiosk.ui.attract

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.amaxonia.kiosk.core.network.KioskApiClient
import com.amaxonia.kiosk.core.network.KioskMediaItem
import com.amaxonia.kiosk.core.network.KioskTokenStorage
import com.amaxonia.kiosk.core.network.NetworkResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val SECRET_TAP_COUNT = 5
private const val SECRET_TAP_WINDOW_MS = 3000L

data class AttractUiState(
    val isLoading: Boolean = true,
    val brandColor: String? = null,
    val logoUrl: String? = null,
    val mediaList: List<KioskMediaItem> = emptyList(),
    val currentMediaIndex: Int = 0,
    val isOffline: Boolean = false,
    val isAdminDialogOpen: Boolean = false,
    val adminPassword: String = "",
    val adminErrorMessage: String? = null,
    val isAdminLoading: Boolean = false,
) {
    val currentMedia: KioskMediaItem?
        get() = mediaList.getOrNull(currentMediaIndex)
}

class AttractViewModel(
    private val apiClient: KioskApiClient,
    private val tokenStorage: KioskTokenStorage,
) : ViewModel() {
    private val _uiState = MutableStateFlow(AttractUiState())
    val uiState: StateFlow<AttractUiState> = _uiState.asStateFlow()

    private var lastTapTimestamp = 0L
    private var tapCount = 0

    var loadConfigJob: Job? = null
        private set

    init {
        loadConfigJob = loadConfig()
    }

    fun loadConfig(): Job {
        _uiState.update { it.copy(isLoading = true) }
        val job =
            viewModelScope.launch {
                val result = apiClient.getConfig(tokenStorage.configEtag)
                when (result) {
                    is NetworkResult.Success -> {
                        val config = result.data
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                brandColor = config.brandColor,
                                logoUrl = config.logoUrl,
                                mediaList = config.media,
                                currentMediaIndex = 0,
                                isOffline = false,
                            )
                        }
                    }
                    is NetworkResult.NotModified -> {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                isOffline = false,
                            )
                        }
                    }
                    is NetworkResult.Failure -> {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                isOffline = it.mediaList.isEmpty(),
                            )
                        }
                    }
                }
            }
        loadConfigJob = job
        return job
    }

    fun advanceToNextMedia() {
        val count = _uiState.value.mediaList.size
        if (count > 0) {
            _uiState.update {
                it.copy(currentMediaIndex = (it.currentMediaIndex + 1) % count)
            }
        }
    }

    fun onSecretTap() {
        val now = System.currentTimeMillis()
        if (now - lastTapTimestamp > SECRET_TAP_WINDOW_MS) {
            tapCount = 1
        } else {
            tapCount++
        }
        lastTapTimestamp = now

        if (tapCount >= SECRET_TAP_COUNT) {
            tapCount = 0
            _uiState.update {
                it.copy(
                    isAdminDialogOpen = true,
                    adminPassword = "",
                    adminErrorMessage = null,
                )
            }
        }
    }

    fun dismissAdminDialog() {
        _uiState.update {
            it.copy(
                isAdminDialogOpen = false,
                adminPassword = "",
                adminErrorMessage = null,
                isAdminLoading = false,
            )
        }
    }

    fun onAdminPasswordChanged(password: String) {
        _uiState.update { it.copy(adminPassword = password, adminErrorMessage = null) }
    }

    fun submitAdminUnlock(onUnlocked: () -> Unit): Job {
        val password = _uiState.value.adminPassword
        if (password.isBlank()) {
            _uiState.update { it.copy(adminErrorMessage = "La contraseña es requerida") }
            return Job().apply { complete() }
        }

        _uiState.update { it.copy(isAdminLoading = true, adminErrorMessage = null) }
        return viewModelScope.launch {
            val result = apiClient.unlock(password)
            result.fold(
                onSuccess = { success ->
                    _uiState.update { it.copy(isAdminLoading = false) }
                    if (success) {
                        dismissAdminDialog()
                        onUnlocked()
                    } else {
                        _uiState.update { it.copy(adminErrorMessage = "Contraseña incorrecta") }
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isAdminLoading = false,
                            adminErrorMessage = error.message ?: "Error al autenticar",
                        )
                    }
                },
            )
        }
    }
}
