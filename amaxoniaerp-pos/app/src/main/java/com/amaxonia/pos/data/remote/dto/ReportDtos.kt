package com.amaxonia.pos.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class DepartmentDto(
    val id: Int,
    val name: String,
)

@Serializable
data class DepartmentsResponse(
    val data: List<DepartmentDto>,
)

@Serializable
data class BestSellerDto(
    val id: String,
    val name: String,
    val price: Double,
    val salesCount: Int,
    val photoUrl: String = "",
)

@Serializable
data class BestSellersResponse(
    val data: List<BestSellerDto>,
)

@Serializable
data class FacturasResumenDto(
    val ventasBrutas: Double = 0.0,
    val ventasNetas: Double = 0.0,
    val descuentos: Double = 0.0,
    val cancelaciones: Double = 0.0,
    val totalFacturas: Int = 0,
    val totalFacturasPagadas: Int = 0,
    val totalFacturasAnuladas: Int = 0,
    val ticketPromedio: Double = 0.0,
    val moneda: String = "USD",
)
