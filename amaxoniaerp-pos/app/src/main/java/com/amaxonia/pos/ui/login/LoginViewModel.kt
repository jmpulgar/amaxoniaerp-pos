package com.amaxonia.pos.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.amaxonia.pos.domain.usecase.auth.AuthenticateUserUseCase
import com.amaxonia.pos.domain.usecase.auth.ConfigureLoginCountryUseCase
import com.amaxonia.pos.domain.usecase.auth.LoginCredentials
import com.amaxonia.pos.domain.usecase.auth.LoginError
import com.amaxonia.pos.domain.usecase.auth.LoginResult
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
    private val configureCountry: ConfigureLoginCountryUseCase,
) : ViewModel() {
    private val mutableState = MutableStateFlow(LoginState())
    val state: StateFlow<LoginState> = mutableState.asStateFlow()

    private val mutableEffects = MutableSharedFlow<LoginUiEffect>(extraBufferCapacity = 1)
    val effects: SharedFlow<LoginUiEffect> = mutableEffects.asSharedFlow()

    fun onAction(action: LoginUiAction) {
        when (action) {
            is LoginUiAction.UsernameChanged ->
                mutableState.update { it.copy(username = action.value, errorMessage = null) }
            is LoginUiAction.PasswordChanged ->
                mutableState.update { it.copy(password = action.value, errorMessage = null) }
            LoginUiAction.TogglePasswordVisibility ->
                mutableState.update { it.copy(isPasswordVisible = !it.isPasswordVisible) }
            is LoginUiAction.CountryChanged -> {
                mutableState.update { it.copy(selectedCountry = action.country, errorMessage = null) }
                configureCountry.select(action.country)
            }
            LoginUiAction.LoadSavedCountry -> restoreCountry()
            LoginUiAction.Submit -> submit()
        }
    }

    private fun restoreCountry() {
        viewModelScope.launch {
            val restored = configureCountry.restore(mutableState.value.selectedCountry)
            mutableState.update { it.copy(selectedCountry = restored) }
        }
    }

    private fun submit() {
        viewModelScope.launch {
            mutableState.update { it.copy(isLoading = true, errorMessage = null) }
            val current = mutableState.value
            when (
                val result =
                    authenticateUser(
                        LoginCredentials(
                            username = current.username,
                            password = current.password,
                            country = current.selectedCountry,
                        ),
                    )
            ) {
                is LoginResult.Success -> {
                    mutableState.update { it.copy(isLoading = false) }
                    mutableEffects.emit(LoginUiEffect.LoginSucceeded)
                }
                is LoginResult.Failure ->
                    mutableState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = result.error.toUserMessage(),
                        )
                    }
            }
        }
    }
}

private fun LoginError.toUserMessage(): String =
    when (this) {
        LoginError.MissingCredentials -> "El usuario y la contrasena son obligatorios"
        LoginError.Unauthorized -> "Usuario o contraseña incorrectos"
        LoginError.Connectivity ->
            "No se pudo conectar al servidor. Compruebe que el backend esté en ejecución y la URL en ApiConfig."
        is LoginError.Unexpected -> message ?: "No se pudo iniciar sesión"
    }
