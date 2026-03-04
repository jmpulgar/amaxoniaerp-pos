package com.amaxonia.pos.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class LoginRequest(
    val username: String,
    val password: String
)

@Serializable
data class LoginResponse(
    val token: String,
    val user: AuthUserDto,
    val companies: List<CompanyDto>
)

@Serializable
data class ErrorResponse(
    val error: String? = null
)

@Serializable
data class AuthUserDto(
    val id: Int,
    val username: String,
    val role: String
)

@Serializable
data class CompanyDto(
    val id: Int,
    val name: String,
    val rif: String? = null
)

@Serializable
data class SelectCompanyRequest(
    val companyId: Int
)

@Serializable
data class SelectCompanyResponse(
    val success: Boolean,
    val token: String,
    val currentCompany: CompanyDetailsDto
)

@Serializable
data class CompanyDetailsDto(
    val id: Int,
    val name: String,
    val adminDb: String = "",
    val accountingDb: String = "",
    val payrollDb: String = ""
)
