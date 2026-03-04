package com.amaxonia.pos.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class ProductWarehouseStockDto(
    val almacenId: Int,
    val almacenNombre: String,
    val almacenTipo: String,
    val cantidad: Double,
    val cantidadMuestra: Double,
    val cantidadPrecomprometida: Double,
    val cantidadDisponible: Double,
    val stockMinimo: Double,
    val stockMaximo: Double,
)

@Serializable
data class ProductStockResponseDto(
    val itemId: Int,
    val stockTotalDisponible: Double,
    val almacenes: List<ProductWarehouseStockDto>
)
