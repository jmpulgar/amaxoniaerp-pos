package com.amaxonia.erp.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.amaxonia.erp.domain.model.LoginCredentials
import com.amaxonia.erp.domain.model.LoginError
import com.amaxonia.erp.domain.model.LoginResult
import com.amaxonia.erp.domain.model.ServerCountries
import com.amaxonia.erp.domain.repository.AuthRepository
import com.amaxonia.erp.domain.repository.CountrySelectionStore
import com.amaxonia.erp.domain.usecase.AuthenticateUserUseCase
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class LoginViewModel(
    private val authenticateUser: AuthenticateUserUseCase,
    private val countryStore: CountrySelectionStore,
    private val authRepository: AuthRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(LoginState())
    val state: StateFlow<LoginState> = _state.asStateFlow()

    private val _effects = MutableSharedFlow<LoginUiEffect>(extraBufferCapacity = 1)
    val effects: SharedFlow<LoginUiEffect> = _effects.asSharedFlow()

    init {
        viewModelScope.launch {
            countryStore.saveSelectedCountry(ServerCountries.PANAMA)
        }
    }

    fun onAction(action: LoginUiAction) {
        when (action) {
            is LoginUiAction.UsernameChanged ->
                _state.update { it.copy(username = action.value, errorMessage = null) }

            is LoginUiAction.PasswordChanged ->
                _state.update { it.copy(password = action.value, errorMessage = null) }

            LoginUiAction.TogglePasswordVisibility ->
                _state.update { it.copy(isPasswordVisible = !it.isPasswordVisible) }

            LoginUiAction.Submit -> submit()

            LoginUiAction.DismissError ->
                _state.update { it.copy(errorMessage = null) }
        }
    }

    private fun submit() {
        val current = _state.value
        if (current.isLoading) return

        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, loadingMessage = null, errorMessage = null) }

            val result =
                authenticateUser(
                    LoginCredentials(
                        username = current.username,
                        password = current.password,
                        country = current.selectedCountry,
                    ),
                )

            when (result) {
                is LoginResult.Success -> {
                    val session = result.session
                    when {
                        session.companies.size == 1 -> {
                            val singleCompany = session.companies.first()
                            _state.update {
                                it.copy(
                                    isLoading = true,
                                    loadingMessage = "Ingresando a ${singleCompany.name}...",
                                )
                            }
                            authRepository.selectCompany(singleCompany.id).fold(
                                onSuccess = { companySession ->
                                    _state.update { it.copy(isLoading = false, loadingMessage = null) }
                                    _effects.emit(LoginUiEffect.CompanySessionReady(companySession))
                                },
                                onFailure = { error ->
                                    _state.update {
                                        it.copy(
                                            isLoading = false,
                                            loadingMessage = null,
                                            errorMessage = error.message ?: "Error al acceder a la empresa",
                                        )
                                    }
                                },
                            )
                        }

                        session.companies.size > 1 -> {
                            _state.update { it.copy(isLoading = false, loadingMessage = null) }
                            _effects.emit(LoginUiEffect.RequiresCompanySelection(session))
                        }

                        else -> {
                            _state.update {
                                it.copy(
                                    isLoading = false,
                                    loadingMessage = null,
                                    errorMessage = "El usuario no tiene ninguna empresa asociada.",
                                )
                            }
                        }
                    }
                }

                is LoginResult.Failure -> {
                    _state.update {
                        it.copy(
                            isLoading = false,
                            loadingMessage = null,
                            errorMessage = result.error.toUserMessage(),
                        )
                    }
                }
            }
        }
    }

    private fun LoginError.toUserMessage(): String =
        when (this) {
            LoginError.MissingCredentials -> "El usuario y la contraseña son obligatorios."
            LoginError.Unauthorized -> "Usuario o contraseña incorrectos."
            LoginError.Connectivity ->
                "No se pudo conectar al servidor. Comprueba tu conexión a internet o el estado del backend."
            is LoginError.Unexpected -> message ?: "Ocurrió un error inesperado al iniciar sesión."
        }
}
