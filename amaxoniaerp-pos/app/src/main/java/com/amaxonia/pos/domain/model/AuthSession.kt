package com.amaxonia.pos.domain.model

data class AuthUser(
    val id: Int,
    val username: String,
    val role: String,
)

data class CompanySummary(
    val id: Int,
    val name: String,
)

data class AuthSession(
    val token: String,
    val user: AuthUser,
    val companies: List<CompanySummary>,
    val isOffline: Boolean = false,
)

data class SelectedCompany(
    val id: Int,
    val name: String,
    val adminDb: String,
    val accountingDb: String,
    val payrollDb: String,
    val rif: String = "",
)

data class CompanySession(
    val token: String,
    val company: SelectedCompany,
    val isOffline: Boolean = false,
)
