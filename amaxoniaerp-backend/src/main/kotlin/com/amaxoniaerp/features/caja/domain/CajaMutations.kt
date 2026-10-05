package com.amaxoniaerp.features.caja.domain

import kotlinx.serialization.Serializable

@Serializable
data class SaveCajaRequest(
    val id: String? = null,
    val codigo: String? = null,
    val caja: String,
    val descripcion: String? = null,
    val idSucursal: Int? = null,
    val serieCaja: String = "1",
    val fondoApertura: Double? = null,
    val impresoraModelo: String? = null,
    val codigoSucursalEmisor: String? = null,
    val puntoFacturacionFiscal: String? = null,
    val codAlmacen: Int? = null,
    val activo: Int = 1,
)

@Serializable
data class SaveCajaResponse(
    val success: Boolean = true,
    val data: Caja,
    val message: String? = null,
)
