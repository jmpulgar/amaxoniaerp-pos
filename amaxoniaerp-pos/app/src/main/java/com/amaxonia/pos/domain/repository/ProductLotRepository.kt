package com.amaxonia.pos.domain.repository

data class ProductLotAvailability(
    val id: Int,
    val code: String,
    val expiration: String?,
    val availableQuantity: Int,
    val warehouseId: Int,
)

data class ProductLotConfiguration(
    val isConfigured: Boolean,
    val lots: List<ProductLotAvailability>,
)

interface ProductLotRepository {
    suspend fun getForProduct(productId: String): Result<ProductLotConfiguration>
}
