package com.amaxonia.pos.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.amaxonia.pos.data.local.LocalStore
import com.amaxonia.pos.data.remote.ApiConfigManager
import com.amaxonia.pos.data.remote.UnauthorizedException
import com.amaxonia.pos.domain.model.ServerCountry
import com.amaxonia.pos.domain.repository.AuthRepository
import com.amaxonia.pos.ui.common.DependencyContainer
import io.ktor.client.network.sockets.SocketTimeoutException
import java.io.IOException
import java.net.ConnectException
import java.net.UnknownHostException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class LoginViewModel(
    private val authRepository: AuthRepository,
    private val apiConfigManager: ApiConfigManager,
    private val localStore: LocalStore
) : ViewModel() {
    private val _state = MutableStateFlow(LoginState())
    val state: StateFlow<LoginState> = _state.asStateFlow()

    fun onUsernameChange(username: String) {
        _state.update { it.copy(username = username, errorMessage = null) }
    }

    fun onPasswordChange(password: String) {
        _state.update { it.copy(password = password, errorMessage = null) }
    }

    fun onTogglePasswordVisibility() {
        _state.update { it.copy(isPasswordVisible = !it.isPasswordVisible) }
    }

    /**
     * Cambia el país seleccionado y actualiza la URL base de la API
     */
    fun onCountryChange(country: ServerCountry) {
        _state.update { it.copy(selectedCountry = country, errorMessage = null) }
        apiConfigManager.updateBaseUrl(country)
        // Recrear el cliente HTTP con la nueva URL base
        DependencyContainer.apiClient.recreateClient()
    }

    /**
     * Carga el país guardado o aplica el por defecto (Venezuela) y sincroniza la URL base.
     */
    fun loadSavedCountry() {
        viewModelScope.launch {
            val savedCountry = localStore.readSelectedCountry()
            val countryToUse = savedCountry ?: _state.value.selectedCountry
            _state.update { it.copy(selectedCountry = countryToUse) }
            apiConfigManager.updateBaseUrl(countryToUse)
            DependencyContainer.apiClient.recreateClient()
        }
    }

    fun onLoginClick(onLoginSuccess: () -> Unit) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, errorMessage = null) }
            val username = _state.value.username
            val password = _state.value.password
            if (username.isBlank() || password.isBlank()) {
                _state.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "El usuario y la contrasena son obligatorios"
                    )
                }
                return@launch
            }
            authRepository.login(
                username = username.trim(),
                password = password,
                countryCode = _state.value.selectedCountry.code
            ).fold(
                onSuccess = {
                    // Guardar el país seleccionado para futuras sesiones
                    localStore.saveSelectedCountry(_state.value.selectedCountry)
                    _state.update { state -> state.copy(isLoading = false) }
                    onLoginSuccess()
                },
                onFailure = { error ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = mapLoginError(error)
                        )
                    }
                }
            )
        }
    }

    private fun mapLoginError(error: Throwable): String = when (error) {
        is UnauthorizedException -> "Usuario o contraseña incorrectos"
        is UnknownHostException,
        is ConnectException,
        is SocketTimeoutException,
        is java.net.SocketTimeoutException,
        is IOException -> "No se pudo conectar al servidor. Compruebe que el backend esté en ejecución y la URL en ApiConfig."
        else -> error.message ?: "No se pudo iniciar sesión"
    }
}
