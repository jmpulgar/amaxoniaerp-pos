package com.amaxonia.pos.domain.model

data class CartItem(
    val product: Product,
    val quantity: Int = 1,
    val codVendedor: Int = 0,
) {
    val total: Double
        get() = (product.prices.firstOrNull()?.pricePlusTax ?: 0.0) * quantity
}
