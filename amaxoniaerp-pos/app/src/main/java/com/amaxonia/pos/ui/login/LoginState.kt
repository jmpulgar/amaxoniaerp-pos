package com.amaxonia.pos.ui.login

import com.amaxonia.pos.domain.model.ServerCountries
import com.amaxonia.pos.domain.model.ServerCountry

data class LoginState(
    val selectedCountry: ServerCountry = ServerCountries.AVAILABLE[0],
    val username: String = "",
    val password: String = "",
    val isPasswordVisible: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)
