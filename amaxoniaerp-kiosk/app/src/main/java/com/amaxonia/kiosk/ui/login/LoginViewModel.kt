package com.amaxonia.kiosk.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.amaxonia.kiosk.core.network.KioskApiClient
import com.amaxonia.kiosk.core.network.KioskApiException
import com.amaxonia.kiosk.core.network.KioskCompanyDto
import com.amaxonia.kiosk.core.network.KioskConnectivityException
import com.amaxonia.kiosk.core.network.KioskInvalidCredentialsException
import com.amaxonia.kiosk.core.network.KioskLoginResponse
import com.amaxonia.kiosk.core.network.KioskSession
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

/** Countries served by the backend (`X-Country-Code`), same as the POS. */
val LOGIN_COUNTRIES = listOf("PA", "VE")

enum class LoginStep { CREDENTIALS, COMPANY }

/** Login failures; the screen maps them to localized strings (mirrors the POS `LoginError`). */
sealed interface LoginError {
    data object MissingCredentials : LoginError

    data object MissingServerUrl : LoginError

    data object InvalidCredentials : LoginError

    data object Connectivity : LoginError

    data object NoCompanies : LoginError

    data class Unexpected(val message: String?) : LoginError
}

data class LoginUiState(
    val step: LoginStep = LoginStep.CREDENTIALS,
    val username: String = "",
    val password: String = "",
    val isPasswordVisible: Boolean = false,
    val countryCode: String = LOGIN_COUNTRIES.first(),
    val serverUrl: String = "",
    val showAdvanced: Boolean = false,
    val isLoading: Boolean = false,
    /** Company being selected (spinner on its card). */
    val selectingCompanyId: Int? = null,
    val companies: List<KioskCompanyDto> = emptyList(),
    val error: LoginError? = null,
)

sealed interface LoginEvent {
    /** Session saved (company token); go to caja setup. */
    data object LoggedIn : LoginEvent
}

/**
 * System login exactly like the POS: `auth/login` (identity token + companies) and then
 * `auth/company` (company token). With a single company the selection step is skipped.
 */
class LoginViewModel(
    private val apiClient: KioskApiClient,
    private val tokenStorage: KioskTokenStorage,
    defaultServerUrl: String,
    defaultCountryCode: String,
) : ViewModel() {
    private val _uiState =
        MutableStateFlow(
            LoginUiState(
                serverUrl = tokenStorage.serverUrl?.takeIf { it.isNotBlank() } ?: defaultServerUrl,
                countryCode =
                    (tokenStorage.countryCode ?: defaultCountryCode)
                        .uppercase()
                        .takeIf { it in LOGIN_COUNTRIES } ?: LOGIN_COUNTRIES.first(),
                username = tokenStorage.username.orEmpty(),
            ),
        )
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    // Channel: a navigation event is never lost if the screen is not collecting at that instant.
    private val _events = Channel<LoginEvent>(Channel.BUFFERED)
    val events: Flow<LoginEvent> = _events.receiveAsFlow()

    /** Identity token + user from `auth/login`, kept in memory only until a company is chosen. */
    private var pendingLogin: KioskLoginResponse? = null

    fun onUsernameChanged(value: String) = _uiState.update { it.copy(username = value, error = null) }

    fun onPasswordChanged(value: String) = _uiState.update { it.copy(password = value, error = null) }

    fun onTogglePasswordVisibility() = _uiState.update { it.copy(isPasswordVisible = !it.isPasswordVisible) }

    fun onCountrySelected(code: String) {
        if (code in LOGIN_COUNTRIES) _uiState.update { it.copy(countryCode = code, error = null) }
    }

    fun onServerUrlChanged(value: String) = _uiState.update { it.copy(serverUrl = value, error = null) }

    fun onToggleAdvanced() = _uiState.update { it.copy(showAdvanced = !it.showAdvanced) }

    /** Back from the company list to the credentials form. */
    fun onBackToCredentials() {
        pendingLogin = null
        _uiState.update {
            it.copy(step = LoginStep.CREDENTIALS, companies = emptyList(), password = "", selectingCompanyId = null, error = null)
        }
    }

    fun submit(): Job? {
        val current = _uiState.value
        val error =
            when {
                current.username.isBlank() || current.password.isBlank() -> LoginError.MissingCredentials
                current.serverUrl.isBlank() -> LoginError.MissingServerUrl
                else -> null
            }
        return when {
            current.isLoading -> null
            error != null -> {
                _uiState.update { it.copy(error = error, showAdvanced = it.showAdvanced || error == LoginError.MissingServerUrl) }
                null
            }
            else -> {
                _uiState.update { it.copy(isLoading = true, error = null) }
                viewModelScope.launch {
                    apiClient
                        .login(
                            serverUrl = current.serverUrl,
                            countryCode = current.countryCode,
                            username = current.username,
                            password = current.password,
                        ).onSuccess { response -> onLoggedIn(response) }
                        .onFailure { failure -> fail(failure) }
                }
            }
        }
    }

    fun selectCompany(company: KioskCompanyDto): Job? {
        val login = pendingLogin?.takeIf { _uiState.value.selectingCompanyId == null } ?: return null
        _uiState.update { it.copy(isLoading = true, selectingCompanyId = company.id, error = null) }
        return viewModelScope.launch { completeLogin(login, company) }
    }

    private suspend fun onLoggedIn(response: KioskLoginResponse) {
        pendingLogin = response
        when (response.companies.size) {
            0 -> fail(null, LoginError.NoCompanies)
            1 -> completeLogin(response, response.companies.single())
            else ->
                _uiState.update {
                    it.copy(isLoading = false, step = LoginStep.COMPANY, companies = response.companies, error = null)
                }
        }
    }

    private suspend fun completeLogin(
        login: KioskLoginResponse,
        company: KioskCompanyDto,
    ) {
        val state = _uiState.value
        val serverUrl = KioskApiClient.normalizeServerUrl(state.serverUrl)
        apiClient
            .selectCompany(serverUrl = serverUrl, identityToken = login.token, companyId = company.id)
            .onSuccess { selected ->
                tokenStorage.saveSession(
                    KioskSession(
                        token = selected.token,
                        userId = login.user.id,
                        username = login.user.username,
                        companyId = selected.currentCompany.id,
                        companyName = selected.currentCompany.name.ifBlank { company.name },
                        companyDb = selected.currentCompany.adminDb,
                        countryCode = (selected.countryCode ?: login.countryCode ?: state.countryCode).uppercase(),
                        serverUrl = serverUrl,
                    ),
                )
                pendingLogin = null
                _uiState.update { it.copy(isLoading = false, selectingCompanyId = null, password = "") }
                _events.send(LoginEvent.LoggedIn)
            }.onFailure { failure -> fail(failure) }
    }

    private fun fail(
        failure: Throwable?,
        error: LoginError = failure.toLoginError(),
    ) {
        _uiState.update { it.copy(isLoading = false, selectingCompanyId = null, error = error) }
    }

    private fun Throwable?.toLoginError(): LoginError =
        when (this) {
            is KioskInvalidCredentialsException -> LoginError.InvalidCredentials
            is KioskConnectivityException -> LoginError.Connectivity
            is KioskApiException -> LoginError.Unexpected(serverMessage ?: message)
            else -> LoginError.Unexpected(this?.message)
        }
}
