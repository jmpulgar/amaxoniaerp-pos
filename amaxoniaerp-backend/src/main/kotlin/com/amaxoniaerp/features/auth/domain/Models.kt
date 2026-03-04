package com.amaxoniaerp.features.auth.domain

import com.amaxoniaerp.features.companies.domain.CompanyResponse
import kotlinx.serialization.Serializable

@Serializable
data class LoginRequest(
    val username: String,
    val password: String,
)

@Serializable
data class LoginResponse(
    val token: String,
    val user: UserResponse,
    val companies: List<CompanyResponse>,
    val countryCode: String,
    val schemaType: String,
)

@Serializable
data class UserResponse(
    val id: Int,
    val username: String,
    val role: String?,
)

data class UserRecord(
    val id: Int,
    val username: String,
    val companyCodesRaw: String?,
    val role: String?,
    val levelId: Int?,
)
