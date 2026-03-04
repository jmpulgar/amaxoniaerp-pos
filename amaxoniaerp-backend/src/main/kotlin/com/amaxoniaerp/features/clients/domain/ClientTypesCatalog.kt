package com.amaxoniaerp.features.clients.domain

import kotlinx.serialization.Serializable

@Serializable
data class ClientTypeCatalog(
    val id: Int,
    val description: String,
    val feCode: String? = null,
)

@Serializable
data class ClientTypesListResponse(
    val data: List<ClientTypeCatalog>,
    val total: Long,
)
