package com.amaxonia.pos.domain.model

data class ProductWarehouseStock(
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

data class ProductStock(
    val itemId: String,
    val stockTotalDisponible: Double,
    val almacenes: List<ProductWarehouseStock>,
)
