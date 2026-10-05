package com.amaxonia.erp.domain.model

data class LoginCredentials(
    val username: String,
    val password: String,
    val country: ServerCountry,
)

data class AuthUser(
    val id: Int,
    val username: String,
    val role: String,
)

data class CompanySummary(
    val id: Int,
    val name: String,
    val rif: String? = null,
)

data class AuthSession(
    val token: String,
    val user: AuthUser,
    val companies: List<CompanySummary>,
)

data class SelectedCompany(
    val id: Int,
    val name: String,
    val adminDb: String = "",
    val accountingDb: String = "",
    val payrollDb: String = "",
    val rif: String? = null,
    val countryCode: String? = null,
)

data class CompanySession(
    val token: String,
    val company: SelectedCompany,
    val user: AuthUser,
)

sealed interface LoginResult {
    data class Success(val session: AuthSession) : LoginResult
    data class Failure(val error: LoginError) : LoginResult
}

sealed interface LoginError {
    data object MissingCredentials : LoginError
    data object Unauthorized : LoginError
    data object Connectivity : LoginError
    data class Unexpected(val message: String?) : LoginError
}
