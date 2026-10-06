package com.amaxonia.erp.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class ClientDto(
    val id: String? = null,
    val code: String? = null,
    val identification: String? = null,
    val dv: String? = null,
    val name: String? = null,
    val lastName: String? = null,
    val address: String? = null,
    val phone: String? = null,
    val email: String? = null,
    val status: Boolean? = null,
    val taxpayerTypeId: Int? = null,
    val foreignAuthTypeId: String? = null,
    val countryId: Int? = null,
    val clientTypeId: Int? = null,
    val codTipoPrecio: Int? = null,
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
    val phone: String = "",
    val email: String = "",
    val clientTypeId: Int = 1,
    val taxpayerTypeId: Int = 1,
    val foreignAuthTypeId: String? = null,
    val countryId: Int = 170,
)

@Serializable
data class ClientSucursalDto(
    val sucursalId: Int,
    val clienteCodigo: String,
    val nombreSucursal: String,
    val nombreContacto: String? = null,
    val telefonoContacto: String? = null,
    val correoContacto: String? = null,
    val direccion: String? = null,
    val observaciones: String? = null,
)

