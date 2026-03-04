package com.amaxoniaerp.features.geography.domain

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class CatalogListResponse(
    val data: List<JsonObject>,
    val total: Long,
)

@Serializable
data class AddressLevelCatalog(
    val countryCode: String,
    val code: String,
    val name: String,
)

@Serializable
data class AddressLevelsListResponse(
    val data: List<AddressLevelCatalog>,
    val total: Long,
)
