package com.amaxoniaerp.features.companies.domain

import kotlinx.serialization.Serializable

@Serializable
data class CompanyResponse(
    val id: Int,
    val name: String,
    val rif: String? = null,
)

@Serializable
data class CompanySelectRequest(
    val companyId: Int,
)

@Serializable
data class CompanySelectResponse(
    val success: Boolean,
    val token: String,
    val currentCompany: CompanyDetailResponse,
    val countryCode: String,
    val schemaType: String,
)

@Serializable
data class CompanyDetailResponse(
    val id: Int,
    val name: String,
    val adminDb: String?,
    val accountingDb: String?,
    val payrollDb: String?,
)

data class CompanyConfig(
    val id: Int,
    val name: String,
    val adminDb: String?,
    val accountingDb: String?,
    val payrollDb: String?,
    val admisActivo: Boolean,
)
