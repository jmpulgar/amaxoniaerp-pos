package com.amaxonia.pos.domain.model

data class ClientBranch(
    val sucursalId: Int,
    val clienteCodigo: String,
    val nombreSucursal: String,
    val nombreContacto: String? = null,
    val telefonoContacto: String? = null,
    val correoContacto: String? = null,
    val direccion: String? = null,
    val observaciones: String? = null,
)
