package com.amaxonia.pos.domain.usecase.payment

import com.amaxonia.pos.domain.model.sales.SaleItemDto
import com.amaxonia.pos.domain.model.sales.SaleTaxDto

/**
 * Characterized aggregation of the existing backend-bound sale values.
 * Item values are already calculated at the legacy DTO boundary; this use case deliberately
 * preserves their IEEE-754 aggregation order until the backend contract is versioned.
 */
class CalculateSaleTotalsUseCase {
    operator fun invoke(items: List<SaleItemDto>): SaleTotals {
        val subtotalGross = items.sumOf { it.itemPrecioSinIva * it.itemCantidad }
        val itemDiscounts = items.sumOf { it.itemMontoDescuento }
        val subtotalNet = items.sumOf { it.itemTotalSinIva }
        val total = items.sumOf { it.itemTotalConIva }
        val tax = total - subtotalNet
        val taxLines =
            items
                .groupBy { it.itemPIva }
                .filterKeys { it > 0.0 }
                .map { (_, lines) ->
                    SaleTaxDto(
                        totalizarBaseRetencion = lines.sumOf { it.itemTotalSinIva },
                        codImpuestoIva = 1,
                        totalizarMontoIva2 = lines.sumOf { it.itemTotalConIva - it.itemTotalSinIva },
                    )
                }
        return SaleTotals(
            subtotalGross = subtotalGross,
            itemDiscounts = itemDiscounts,
            subtotalNet = subtotalNet,
            tax = tax,
            total = total,
            taxLines = taxLines,
        )
    }
}

data class SaleTotals(
    val subtotalGross: Double,
    val itemDiscounts: Double,
    val subtotalNet: Double,
    val tax: Double,
    val total: Double,
    val taxLines: List<SaleTaxDto>,
)
