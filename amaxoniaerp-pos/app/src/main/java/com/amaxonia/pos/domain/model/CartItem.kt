package com.amaxonia.pos.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class LotAssignment(
    val idLoteItem: String,
    val codigoLote: String,
    val vencimiento: String? = null,
    val cantidad: Int,
    val almacen: Int = 0,
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
    val promocionVeces: Int = 1,
    val selectedPriceLabel: String = "A",
    val isManualPrice: Boolean = false,
) {
    val isPromotionLine: Boolean get() = !promocionId.isNullOrBlank()

    val taxRate: Double
        get() {
            if (product.isExempt) return 0.0
            if (product.taxRate > 0.0) return product.taxRate
            val priceDerivedTax = product.prices.firstNotNullOfOrNull { p ->
                if (p.pricePlusTax > p.price && p.price > 0.0) {
                    ((p.pricePlusTax - p.price) / p.price) * 100.0
                } else null
            }
            if (priceDerivedTax != null && priceDerivedTax > 0.0) return priceDerivedTax
            return 7.0
        }

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

fun codTipoPrecioToLabel(codTipoPrecio: Int?): String =
    when (codTipoPrecio) {
        1, 2 -> "A"
        3 -> "B"
        4 -> "C"
        5 -> "D"
        6 -> "E"
        7 -> "F"
        else -> "A"
    }

fun List<CartItem>.computeFinancialSnapshot(): SaleFinancialSnapshot {
    if (isEmpty()) {
        return SaleFinancialSnapshot(0.0, 0.0, 0.0, 0.0, 0.0)
    }
    val subtotalGross =
        fold(com.amaxonia.pos.domain.model.money.Money.ZERO) { sum, item ->
            sum + com.amaxonia.pos.domain.model.money.Money.fromDouble(item.subtotalWithoutTax)
        }
    val itemDiscounts =
        fold(com.amaxonia.pos.domain.model.money.Money.ZERO) { sum, item ->
            sum + com.amaxonia.pos.domain.model.money.Money.fromDouble(item.discountAmountWithoutTax)
        }
    val subtotalNet =
        fold(com.amaxonia.pos.domain.model.money.Money.ZERO) { sum, item ->
            sum + com.amaxonia.pos.domain.model.money.Money.fromDouble(item.totalWithoutTax)
        }
    val totalMoney =
        fold(com.amaxonia.pos.domain.model.money.Money.ZERO) { sum, item ->
            sum + com.amaxonia.pos.domain.model.money.Money.fromDouble(item.totalWithTax)
        }
    val tax = (totalMoney - subtotalNet).coerceAtLeast(com.amaxonia.pos.domain.model.money.Money.ZERO)

    runCatching {
        forEachIndexed { index, item ->
            com.amaxonia.pos.core.logging.SafeLog.d(
                "POS-TOTALS",
                "Item[$index]: ${item.product.description} | Price: ${item.unitPriceWithTax} (${item.selectedPriceLabel}) | Qty: ${item.quantityDecimal} | TaxRate: ${item.taxRate}% (isExempt=${item.product.isExempt}) | Disc: ${item.discountPercent}% | SubWithoutTax: ${item.subtotalWithoutTax} | DiscAmount: ${item.discountAmountWithoutTax} | TotWithoutTax: ${item.totalWithoutTax} | TotWithTax: ${item.totalWithTax}",
            )
        }
        com.amaxonia.pos.core.logging.SafeLog.d(
            "POS-TOTALS",
            "FinancialSnapshot -> Gross: ${subtotalGross.toDouble()} | Discounts: ${itemDiscounts.toDouble()} | Net: ${subtotalNet.toDouble()} | Tax: ${tax.toDouble()} | Total: ${totalMoney.toDouble()}",
        )
    }

    return SaleFinancialSnapshot(
        subtotalGross = subtotalGross.toDouble(),
        itemDiscounts = itemDiscounts.toDouble(),
        subtotalNet = subtotalNet.toDouble(),
        tax = tax.toDouble(),
        total = totalMoney.toDouble(),
    )
}

