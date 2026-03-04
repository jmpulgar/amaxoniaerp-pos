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

@Serializable
data class CierreCajaRequest(
    val idCajaSecuencia: String,
    val idCaja: String,
    val montoCierre: Double,
    val totalEfectivo: Double = 0.0,
    val totalTarjeta: Double = 0.0,
    val totalOtros: Double = 0.0,
    val totalVentas: Double = 0.0,
    val cantidadTransacciones: Int = 0
)

@Serializable
data class CierreCajaResponse(
    val success: Boolean = true,
    val message: String? = null,
    val error: String? = null
)

/** Summary shown in the close-register UI. */
data class CierreCajaSummary(
    val cajaName: String = "",
    val openedAt: String = "",
    val openAmount: Double = 0.0,
    val totalSales: Double = 0.0,
    val totalCash: Double = 0.0,
    val totalCard: Double = 0.0,
    val totalOther: Double = 0.0,
    val transactionCount: Int = 0,
    val expectedClose: Double = 0.0
)
