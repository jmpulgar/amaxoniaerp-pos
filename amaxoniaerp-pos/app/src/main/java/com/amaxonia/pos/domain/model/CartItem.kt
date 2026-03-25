package com.amaxonia.pos.domain.model

data class CartItem(
    val product: Product,
    val quantity: Int = 1,
    val codVendedor: Int = 0,
    val unitPriceWithTax: Double = product.prices.firstOrNull()?.pricePlusTax ?: 0.0,
    val discountPercent: Double = 0.0,
) {
    val taxRate: Double
        get() = if (product.isExempt) 0.0 else product.taxRate.coerceAtLeast(0.0)

    val unitPriceWithoutTax: Double
        get() = if (taxRate <= 0.0) unitPriceWithTax else unitPriceWithTax / (1.0 + (taxRate / 100.0))

    val subtotalWithoutTax: Double
        get() = unitPriceWithoutTax * quantity

    val discountAmountWithoutTax: Double
        get() = subtotalWithoutTax * (discountPercent.coerceIn(0.0, 100.0) / 100.0)

    val totalWithoutTax: Double
        get() = (subtotalWithoutTax - discountAmountWithoutTax).coerceAtLeast(0.0)

    val totalWithTax: Double
        get() = if (taxRate <= 0.0) totalWithoutTax else totalWithoutTax * (1.0 + (taxRate / 100.0))

    val total: Double
        get() = totalWithTax
}
