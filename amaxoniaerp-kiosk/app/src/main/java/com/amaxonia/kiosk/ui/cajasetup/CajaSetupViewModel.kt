package com.amaxonia.kiosk.ui.cajasetup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.amaxonia.kiosk.core.network.KioskApiClient
import com.amaxonia.kiosk.core.network.KioskApiException
import com.amaxonia.kiosk.core.network.KioskCajaDto
import com.amaxonia.kiosk.core.network.KioskConnectivityException
import com.amaxonia.kiosk.core.network.KioskTokenStorage
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

const val DEFAULT_ORDER_PREFIX = "K1"
const val MAX_PREFIX_LENGTH = 5
private val PREFIX_PATTERN = Regex("^[A-Z0-9]{1,$MAX_PREFIX_LENGTH}$")

/** Keeps only A–Z / 0–9, uppercased, up to [MAX_PREFIX_LENGTH] characters. */
fun sanitizePrefix(raw: String): String = raw.uppercase().filter { it in 'A'..'Z' || it in '0'..'9' }.take(MAX_PREFIX_LENGTH)

fun isValidPrefix(prefix: String): Boolean = PREFIX_PATTERN.matches(prefix)

/** Why the caja list could not be shown; the screen maps it to localized text. */
sealed interface CajaLoadError {
    data object Connectivity : CajaLoadError

    data class Server(val message: String?) : CajaLoadError
}

data class CajaSetupUiState(
    val companyName: String = "",
    val username: String = "",
    val isLoading: Boolean = true,
    /** Active cajas only (`estatus == 1`). */
    val cajas: List<KioskCajaDto> = emptyList(),
    val selectedCajaId: String? = null,
    val prefix: String = DEFAULT_ORDER_PREFIX,
    val loadError: CajaLoadError? = null,
    /** Shown after "Comenzar" with an invalid prefix or no caja selected. */
    val showValidation: Boolean = false,
    /** The previous caja was rejected by the server (400 "Caja del kiosco no válida"). */
    val previousCajaInvalid: Boolean = false,
) {
    val selectedCaja: KioskCajaDto?
        get() = cajas.firstOrNull { it.idCaja == selectedCajaId }

    val isPrefixValid: Boolean
        get() = isValidPrefix(prefix)

    val canStart: Boolean
        get() = selectedCaja != null && isPrefixValid
}

sealed interface CajaSetupEvent {
    data object Started : CajaSetupEvent

    data object LoggedOut : CajaSetupEvent
}

/** Chooses the caja (register) and the order prefix of this kiosk; both are stored on the device. */
class CajaSetupViewModel(
    private val apiClient: KioskApiClient,
    private val tokenStorage: KioskTokenStorage,
    previousCajaInvalid: Boolean = false,
) : ViewModel() {
    private val _uiState =
        MutableStateFlow(
            CajaSetupUiState(
                companyName = tokenStorage.companyName.orEmpty(),
                username = tokenStorage.username.orEmpty(),
                prefix = sanitizePrefix(tokenStorage.prefix.orEmpty()).ifEmpty { DEFAULT_ORDER_PREFIX },
                previousCajaInvalid = previousCajaInvalid,
            ),
        )
    val uiState: StateFlow<CajaSetupUiState> = _uiState.asStateFlow()

    // Channel: a navigation event is never lost if the screen is not collecting at that instant.
    private val _events = Channel<CajaSetupEvent>(Channel.BUFFERED)
    val events: Flow<CajaSetupEvent> = _events.receiveAsFlow()

    var loadJob: Job? = null
        private set

    init {
        loadCajas()
    }

    fun loadCajas(): Job {
        _uiState.update { it.copy(isLoading = true, loadError = null) }
        return viewModelScope.launch {
            // A 401 here clears the session and the navigation goes back to login on its own.
            apiClient
                .getCajas()
                .onSuccess { all ->
                    val active = all.filter { it.isActive }
                    _uiState.update { state ->
                        val keep = state.selectedCajaId?.takeIf { id -> active.any { it.idCaja == id } }
                        state.copy(
                            isLoading = false,
                            cajas = active,
                            selectedCajaId = keep ?: active.singleOrNull()?.idCaja,
                        )
                    }
                }.onFailure { error ->
                    val loadError =
                        when (error) {
                            is KioskConnectivityException -> CajaLoadError.Connectivity
                            is KioskApiException -> CajaLoadError.Server(error.serverMessage ?: error.message)
                            else -> CajaLoadError.Server(error.message)
                        }
                    _uiState.update { it.copy(isLoading = false, loadError = loadError) }
                }
        }.also { loadJob = it }
    }

    fun onCajaSelected(cajaId: String) {
        _uiState.update { it.copy(selectedCajaId = cajaId) }
    }

    fun onPrefixChanged(value: String) {
        _uiState.update { it.copy(prefix = sanitizePrefix(value)) }
    }

    fun start() {
        val state = _uiState.value
        val caja = state.selectedCaja
        if (caja == null || !state.isPrefixValid) {
            _uiState.update { it.copy(showValidation = true) }
            return
        }
        tokenStorage.saveCaja(cajaId = caja.idCaja, cajaName = caja.displayName, prefix = state.prefix)
        _events.trySend(CajaSetupEvent.Started)
    }

    fun logout() {
        tokenStorage.clearSession()
        _events.trySend(CajaSetupEvent.LoggedOut)
    }
}
