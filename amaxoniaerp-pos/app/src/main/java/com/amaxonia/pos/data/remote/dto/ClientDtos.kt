package com.amaxonia.pos.data.remote.dto

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
    // --- CORRECCIONES ---
    // 1. Cambiamos String -> Int y el nombre para coincidir con Entities y Backend
    val taxpayerTypeId: Int? = null,
    // 2. Agregamos el campo para el tipo de identificación extranjera (01, 02)
    val foreignAuthTypeId: String? = null,
    // --------------------
    val countryId: Int? = null,
    val clientTypeId: Int? = null,
    val addressLevel1: String? = null,
    val addressLevel2: String? = null,
    val addressLevel3: String? = null,
    val photoFilename: String? = null,
)

@Serializable
data class CreateClientRequest(
    val identification: String,
    val name: String,
    val lastName: String,
    val address: String,
    val phone: String,
    val email: String,
    val clientTypeId: Int,
    // Aseguramos que al crear también se envíe como ID numérico
    val taxpayerTypeId: Int,
    // Agregamos el campo opcional para cuando creas un extranjero
    val foreignAuthTypeId: String? = null,
    val countryId: Int,
    val addressLevel1: String,
    val addressLevel2: String,
    val addressLevel3: String,
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
