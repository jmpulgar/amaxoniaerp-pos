package com.amaxoniaerp.features.items.domain

import kotlinx.serialization.Serializable

@Serializable
data class ItemStockByWarehouse(
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
data class ItemStockResponse(
    val itemId: Int,
    val stockTotalDisponible: Double,
    val almacenes: List<ItemStockByWarehouse>,
)
