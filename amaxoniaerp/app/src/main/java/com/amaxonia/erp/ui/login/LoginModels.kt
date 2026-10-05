package com.amaxonia.erp.ui.login

import com.amaxonia.erp.domain.model.AuthSession
import com.amaxonia.erp.domain.model.CompanySession
import com.amaxonia.erp.domain.model.ServerCountries
import com.amaxonia.erp.domain.model.ServerCountry

data class LoginState(
    val username: String = "",
    val password: String = "",
    val isPasswordVisible: Boolean = false,
    val selectedCountry: ServerCountry = ServerCountries.PANAMA,
    val isLoading: Boolean = false,
    val loadingMessage: String? = null,
    val errorMessage: String? = null,
)

sealed interface LoginUiAction {
    data class UsernameChanged(val value: String) : LoginUiAction
    data class PasswordChanged(val value: String) : LoginUiAction
    data object TogglePasswordVisibility : LoginUiAction
    data object Submit : LoginUiAction
    data object DismissError : LoginUiAction
}

sealed interface LoginUiEffect {
    data class CompanySessionReady(val companySession: CompanySession) : LoginUiEffect
    data class RequiresCompanySelection(val session: AuthSession) : LoginUiEffect
}
