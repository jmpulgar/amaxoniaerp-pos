package com.amaxonia.pos.ui.login

import com.amaxonia.pos.domain.model.ServerCountries
import com.amaxonia.pos.domain.model.ServerCountry

data class LoginState(
    val selectedCountry: ServerCountry = ServerCountries.AVAILABLE[0],
    val username: String = "",
    val password: String = "",
    val isPasswordVisible: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
)

sealed interface LoginUiAction {
    data class UsernameChanged(
        val value: String,
    ) : LoginUiAction

    data class PasswordChanged(
        val value: String,
    ) : LoginUiAction

    data object TogglePasswordVisibility : LoginUiAction

    data class CountryChanged(
        val country: ServerCountry,
    ) : LoginUiAction

    data object LoadSavedCountry : LoginUiAction

    data object Submit : LoginUiAction
}

sealed interface LoginUiEffect {
    data object LoginSucceeded : LoginUiEffect
}
