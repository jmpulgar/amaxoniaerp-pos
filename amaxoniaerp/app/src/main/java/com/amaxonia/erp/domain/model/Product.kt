package com.amaxonia.erp.domain.model

import kotlinx.serialization.Serializable

data class Product(
    val id: String = "",
    val code: String = "",
    val description: String = "",
    val reference: String = "",
    val barcode1: String = "",
    val barcode2: String = "",
    val barcode3: String = "",
    val photoUrl: String = "",
    val department: String = "",
    val isExempt: Boolean = false,
    val taxRate: Double = 0.0,
    val costActual: Double = 0.0,
    val unitPackage: String = "UNIDAD",
    val bulkQuantity: Double = 1.0,
    val portionUnit: String? = null,
    val unitOrPackage: String = "UNIDAD",
    val isService: Boolean = false,
    val prices: List<PriceLevel> = emptyList(),
    val hasOptions: Boolean = false,
    val idCombo: String? = null,
    val itemType: String = "",
    val hasVariants: Boolean = false,
    val variantsTotal: Int = 0,
) {
    val isComboArticle: Boolean
        get() = itemType.equals("C", ignoreCase = true) || itemType.equals("K", ignoreCase = true)

    val requiresCompositionSelection: Boolean
        get() = isComboArticle || hasOptions

    val mainPrice: Double
        get() = prices.firstOrNull()?.pricePlusTax
            ?: prices.firstOrNull()?.price
            ?: 0.0
}

@Serializable
data class PriceLevel(
    val label: String,
    val name: String = "",
    val price: Double = 0.0,
    val utilityPercent: Double = 0.0,
    val pricePlusUtility: Double = 0.0,
    val pricePlusTax: Double = 0.0,
    val unitPrice: Double = 0.0,
    val unitPricePlusTax: Double = 0.0,
    val discountPercent: Double = 0.0,
) {
    val displayName: String
        get() = name.ifBlank { defaultPriceName(label) }
}

fun defaultPriceName(label: String): String = when (label.trim().uppercase()) {
    "A", "1", "PRECIO_A", "PRECIO 1" -> "Detal"
    "B", "2", "PRECIO_B", "PRECIO 2" -> "Mayor"
    "C", "3", "PRECIO_C", "PRECIO 3" -> "Distribuidor"
    "D", "4", "PRECIO_D", "PRECIO 4" -> "Especial"
    "E", "5", "PRECIO_E", "PRECIO 5" -> "Mínimo"
    "F", "6", "PRECIO_F", "PRECIO 6" -> "Precio 6"
    "LIBRE", "PRECIO LIBRE", "8" -> "Precio Libre"
    else -> if (label.isNotBlank()) "Lista $label" else "Detal"
}
