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
    val unitPackage: String = "",
    val bulkQuantity: Double = 1.0,
    val portionUnit: String? = null,
    val unitOrPackage: String = "UNIDAD",
    val isService: Boolean = false,
    val prices: List<PriceLevel> = generateDefaultPrices(),
    val poseeProductoCombo: Boolean = false,
    val idCombo: String? = null,
    val tipoArticulo: String? = null,
    val poseeVariantes: Boolean = false,
    val variantesTotal: Int = 0,
) {
    val canSwitchUnit: Boolean
        get() = bulkQuantity > 1.0

    val packageLabel: String
        get() = unitPackage.ifBlank { unitOrPackage.ifBlank { "EMPAQUE" } }

    val hasOptions: Boolean
        get() = poseeProductoCombo || tipoArticulo.equals("K", ignoreCase = true) || tipoArticulo.equals("C", ignoreCase = true)
}

@Serializable
data class PriceLevel(
    val label: String,
    val price: Double = 0.0,
    val utilityPercent: Double = 0.0,
    val pricePlusUtility: Double = 0.0,
    val pricePlusTax: Double = 0.0,
    val unitPrice: Double = 0.0,
    val unitPricePlusTax: Double = 0.0,
    val discountPercent: Double = 0.0,
)

fun generateDefaultPrices(): List<PriceLevel> =
    listOf("A", "B", "C", "D", "E").map {
        PriceLevel(label = it)
    }
