package com.amaxonia.pos.domain.model.caja

import kotlinx.serialization.Serializable

@Serializable
data class CajaSecuencia(
    val idCajaSecuencia: String,
    val idCaja: String,
    val fechaApertura: String,
    val montoApertura: Double,
    val fechaCierre: String? = null,
    val montoCierre: Double? = null,
    val estatus: Int,
    val usuarioApertura: String,
    val usuarioCierre: String? = null,
    val serieSucursal: String,
    val idSucursal: Int?
)

@Serializable
data class AperturaRequest(
    val idCaja: String,
    val montoApertura: Double,
    val serieSucursal: String,
    val idSucursal: Int? = null,
    val facturaInicial: Int = 0,
    val notacreditoInicial: Int = 0,
    val devolucionInicial: Int = 0,
    val zInicial: Int = 0
)
