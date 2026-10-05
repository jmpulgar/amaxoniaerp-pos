package com.amaxoniaerp.features.sucursales.domain

import kotlinx.serialization.Serializable

@Serializable
data class SucursalDto(
    val id: Int,
    val codigo: String? = null,
    val serie: String? = null,
    val codigoSucursalEmisor: String? = null,
    val sucursal: String? = null,
    val descripcion: String? = null,
    val defaultWarehouseId: Int? = null,
)

@Serializable
data class SaveSucursalRequest(
    val codigo: String? = null,
    val serie: String? = null,
    val codigoSucursalEmisor: String? = null,
    val sucursal: String,
    val descripcion: String? = null,
    val defaultWarehouseId: Int? = null,
)

@Serializable
data class SucursalesListResponse(
    val success: Boolean = true,
    val data: List<SucursalDto>,
)

@Serializable
data class SucursalDetailResponse(
    val success: Boolean = true,
    val data: SucursalDto,
)
