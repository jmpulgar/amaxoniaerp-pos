package com.amaxonia.pos.domain.usecase.payment

import com.amaxonia.pos.domain.model.money.Money
import com.amaxonia.pos.domain.model.sales.SaleItemDto
import com.amaxonia.pos.domain.model.sales.SaleTaxDto
import java.math.BigDecimal

/**
 * Rollup of the per-line sale values produced by [BuildSaleItemsUseCase].
 *
 * All intermediate arithmetic is carried in [BigDecimal] at [Money.SCALE]
 * decimals to eliminate the IEEE-754 residue that previously accumulated in
 * `Double` sums (e.g. `itemTotalConIva = 6.90` aggregated as
 * `6.8999999999999995`, which then crashed [MinorUnitMoney] downstream in
 * `StartTransactionUseCase`). The output fields remain `Double` for wire-DTO
 * compatibility — the sale endpoint receives the same numbers it always has —
 * but every Double is the *exact* representation of a scale-2 BigDecimal, so
 * the round-trip BigDecimal→Double→BigDecimal is lossless.
 *
 * HALF_EVEN (banker's rounding) is used throughout, matching [Money].
 */
class CalculateSaleTotalsUseCase {
    operator fun invoke(items: List<SaleItemDto>): SaleTotals {
        val scale = Money.SCALE
        val mode = Money.ROUNDING_MODE

        val zero = BigDecimal.ZERO.setScale(scale, mode)

        fun bd(value: Double): BigDecimal = BigDecimal.valueOf(value).setScale(scale, mode)

        val subtotalGross =
            items
                .fold(zero) { acc, item -> acc + bd(item.itemPrecioSinIva).multiply(bd(item.itemCantidad)) }
                .setScale(scale, mode)
        val itemDiscounts = items.fold(zero) { acc, item -> acc + bd(item.itemMontoDescuento) }.setScale(scale, mode)
        val subtotalNet = items.fold(zero) { acc, item -> acc + bd(item.itemTotalSinIva) }.setScale(scale, mode)
        val total = items.fold(zero) { acc, item -> acc + bd(item.itemTotalConIva) }.setScale(scale, mode)
        val tax = (total - subtotalNet).setScale(scale, mode)

        val taxLines =
            items
                .groupBy { it.itemPIva }
                .filterKeys { taxRate -> taxRate > 0.0 }
                .map { (_, lines) ->
                    val base = lines.fold(zero) { acc, item -> acc + bd(item.itemTotalSinIva) }.setScale(scale, mode)
                    val taxAmount =
                        lines
                            .fold(zero) { acc, item ->
                                acc + (bd(item.itemTotalConIva) - bd(item.itemTotalSinIva))
                            }.setScale(scale, mode)
                    SaleTaxDto(
                        totalizarBaseRetencion = base.toDouble(),
                        codImpuestoIva = 1,
                        totalizarMontoIva2 = taxAmount.toDouble(),
                    )
                }

        return SaleTotals(
            subtotalGross = subtotalGross.toDouble(),
            itemDiscounts = itemDiscounts.toDouble(),
            subtotalNet = subtotalNet.toDouble(),
            tax = tax.toDouble(),
            total = total.toDouble(),
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
