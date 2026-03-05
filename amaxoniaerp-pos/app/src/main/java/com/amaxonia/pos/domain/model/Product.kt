package com.amaxonia.pos.domain.model

import kotlinx.serialization.Serializable
import java.util.UUID

data class Product(
    val id: String = UUID.randomUUID().toString(),
    val code: String = "",
    val reference: String = "",
    val description: String = "",
    val barcode1: String = "",
    val barcode2: String = "",
    val barcode3: String = "",
    val photoUrl: String = "",
    val department: String = "",
    val section: String = "",
    val family: String = "",
    val subFamily: String = "",
    val brand: String = "",
    val line: String = "",
    val gobSegment: String = "",
    val gobFamily: String = "",
    val isExempt: Boolean = false,
    val taxRate: Double = 0.0,
    val costActual: Double = 0.0,
    val costAverage: Double = 0.0,
    val costPrevious: Double = 0.0,
    val costCIF: Double = 0.0,
    val costFOB: Double = 0.0,
    val costProcessed: Double = 0.0,
    val commissionPercent: Double = 0.0,
    val costEuroOrigin: Double = 0.0,
    val costFranco: Double = 0.0,
    val prices: List<PriceLevel> = generateDefaultPrices()
)

@Serializable
data class PriceLevel(
    val label: String,
    val price: Double = 0.0,
    val utilityPercent: Double = 0.0,
    val pricePlusUtility: Double = 0.0,
    val pricePlusTax: Double = 0.0,
    val discountPercent: Double = 0.0
)

fun generateDefaultPrices(): List<PriceLevel> {
    return listOf("A", "B", "C", "D", "E").map {
        PriceLevel(label = it)
    }
}
