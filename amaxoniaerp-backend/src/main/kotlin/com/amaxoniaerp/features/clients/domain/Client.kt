package com.amaxoniaerp.features.clients.domain

import kotlinx.serialization.Serializable

@Serializable
data class Client(
    val id: String,
    val code: String,
    val identification: String,
    val dv: String,
    val name: String,
    val lastName: String,
    val address: String,
    val addressLevel1: String = "",
    val addressLevel2: String = "",
    val addressLevel3: String = "",
    val phone: String,
    val email: String,
    val status: Boolean,
    val clientTypeId: Int,
    val taxpayerTypeId: Int,
    val foreignAuthTypeId: String? = null,
    val countryId: Int,
    val photoFilename: String? = null,
    val permiteCredito: Boolean = false,
    val diasCredito: Int = 0,
)

@Serializable
data class CreateClientRequest(
    val identification: String,
    val name: String,
    val lastName: String = "",
    val address: String = "",
    val addressLevel1: String = "",
    val addressLevel2: String = "",
    val addressLevel3: String = "",
    val phone: String = "",
    val email: String = "",
    val clientTypeId: Int = 2,
    val taxpayerTypeId: Int = 2,
    val foreignAuthTypeId: String? = null,
    val countryId: Int = 170,
)

@Serializable
data class ClientsListResponse(
    val data: List<Client>,
    val total: Long,
)

@Serializable
data class ClientSucursal(
    val sucursalId: Int,
    val clienteCodigo: String,
    val nombreSucursal: String,
    val nombreContacto: String? = null,
    val telefonoContacto: String? = null,
    val correoContacto: String? = null,
    val direccion: String? = null,
    val observaciones: String? = null,
)
