package com.amaxonia.erp.domain.model

data class Product(
    val id: String = "",
    val code: String = "",
    val description: String = "",
    val reference: String = "",
    val barcode1: String = "",
    val photoUrl: String = "",
    val department: String = "",
    val isExempt: Boolean = false,
    val taxRate: Double = 0.0,
    val costActual: Double = 0.0,
    val unitPackage: String = "UNIDAD",
    val prices: List<PriceLevel> = emptyList(),
) {
    val mainPrice: Double
        get() = prices.firstOrNull()?.pricePlusTax
            ?: prices.firstOrNull()?.price
            ?: 0.0
}

data class PriceLevel(
    val label: String,
    val price: Double = 0.0,
    val utilityPercent: Double = 0.0,
    val pricePlusUtility: Double = 0.0,
    val pricePlusTax: Double = 0.0,
)
