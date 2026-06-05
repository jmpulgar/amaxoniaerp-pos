package com.amaxonia.pos.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class LotAssignment(
    val idLoteItem: String,
    val codigoLote: String,
    val vencimiento: String? = null,
    val cantidad: Int,
    val almacen: Int = 0
)

data class CartItem(
    val product: Product,
    val quantity: Int = 1,
    val codVendedor: Int = 0,
    val unitPriceWithTax: Double = product.prices.firstOrNull()?.pricePlusTax ?: 0.0,
    val quantityDecimal: Double = quantity.toDouble(),
    val itemUnitPackage: String = if (product.bulkQuantity > 1.0) "EMPAQUE" else "UNIDAD",
    val discountPercent: Double = 0.0,
    val hasLotConfig: Boolean = false,
    val lotAssignments: List<LotAssignment> = emptyList(),
    val promocionId: String? = null,
    val promocionCodigo: String = "",
    val promocionNombre: String = "",
    val promocionTipo: String = "",
    val promocionGrupo: String = "",
    val promocionDetalleId: String = "",
) {
    val isPromotionLine: Boolean get() = !promocionId.isNullOrBlank()

    val taxRate: Double
        get() = if (product.isExempt) 0.0 else product.taxRate.coerceAtLeast(0.0)

    val bulkQuantity: Double
        get() = product.bulkQuantity.takeIf { it > 0.0 } ?: 1.0

    val quantityTotal: Double
        get() = if (itemUnitPackage == "EMPAQUE") quantityDecimal * bulkQuantity else quantityDecimal

    val displayUnitLabel: String
        get() = if (itemUnitPackage == "EMPAQUE") product.packageLabel else "UNIDAD"

    val unitPriceWithoutTax: Double
        get() = if (taxRate <= 0.0) unitPriceWithTax else unitPriceWithTax / (1.0 + (taxRate / 100.0))

    val subtotalWithoutTax: Double
        get() = unitPriceWithoutTax * quantityDecimal

    val discountAmountWithoutTax: Double
        get() = subtotalWithoutTax * (discountPercent.coerceIn(0.0, 100.0) / 100.0)

    val totalWithoutTax: Double
        get() = (subtotalWithoutTax - discountAmountWithoutTax).coerceAtLeast(0.0)

    val totalWithTax: Double
        get() = if (taxRate <= 0.0) totalWithoutTax else totalWithoutTax * (1.0 + (taxRate / 100.0))

    val total: Double
        get() = totalWithTax
}
