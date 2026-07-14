package com.amaxonia.pos.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CountryDto(
    val id: Int,
    val iso: String,
    // Este lo dejamos igual porque dijiste que el País SÍ carga bien.
    // (Tu JSON de países anterior traía "nombre")
    @SerialName("nombre") val name: String,
)

@Serializable
data class AddressLevelDto(
    // CAMBIO: Tu JSON trae "countryCode", no "codigo_pais"
    @SerialName("countryCode") val countryCode: String,
    // CAMBIO: Tu JSON trae "code", no "codigo"
    @SerialName("code") val code: String,
    // CAMBIO: Tu JSON trae "name", no "denominacion"
    @SerialName("name") val name: String,
)

@Serializable
data class ClientTypeDto(
    val id: Int,
    // CAMBIO CRÍTICO: Tu JSON trae "description", la App espera cargar esto en "name"
    @SerialName("description") val name: String,
    // Agregamos este opcional por si lo necesitas a futuro (vi que viene en el JSON como "01", "02")
    val feCode: String? = null,
)
