package com.amaxonia.pos.domain.usecase.payment

import com.amaxonia.pos.domain.model.CartItem
import com.amaxonia.pos.domain.model.money.Money
import com.amaxonia.pos.domain.model.sales.SaleItemDto
import com.amaxonia.pos.domain.model.sales.SaleLotDto
import java.math.BigDecimal

data class BuildSaleItemsInput(
    val cartItems: List<CartItem>,
    val warehouseId: Int,
    val sellerId: Int,
    val defaultTaxRate: Double,
)

/**
 * Per-line sale math, performed in [BigDecimal] at the same [Money.SCALE]
 * used by the rest of the money flow. Keeping these intermediate values
 * exact prevents the kind of upstream IEEE-754 residue (e.g.
 * `6.8999999999999995` for a $6.90 line) that previously propagated into
 * [CalculateSaleTotalsUseCase] and crashed [MinorUnitMoney] at persistence
 * time.
 *
 * Monetary outputs (precio, descuento monto, total sin/con iva) are emitted
 * as `Double` because [SaleItemDto] is the wire contract of an installed
 * backend; each Double is the lossless representation of a scale-2
 * BigDecimal. Quantity and tax-rate fields stay `Double` because they are
 * not monetary magnitudes.
 */
class BuildSaleItemsUseCase {
    operator fun invoke(input: BuildSaleItemsInput): List<SaleItemDto> = input.cartItems.map { item -> item.toSaleItem(input) }

    private fun CartItem.toSaleItem(input: BuildSaleItemsInput): SaleItemDto {
        val scale = Money.SCALE
        val mode = Money.ROUNDING_MODE

        fun bd(value: Double): BigDecimal = BigDecimal.valueOf(value).setScale(scale, mode)

        val tax = if (product.isExempt) 0.0 else product.taxRate.takeIf { it > 0.0 } ?: input.defaultTaxRate
        val divisor = BigDecimal.ONE.add(bd(tax).divide(PERCENT_BASE, MATH_SCALE, mode))
        val unitWithTax = bd(unitPriceWithTax)
        val unitWithoutTax =
            if (tax <= 0.0) {
                unitWithTax
            } else {
                unitWithTax.divide(divisor, scale, mode)
            }
        val quantity = bd(quantityDecimal)
        val subtotalWithoutTax = unitWithoutTax.multiply(quantity).setScale(scale, mode)
        val discount = discountPercent.coerceIn(0.0, PERCENT_BASE_D)
        val discountAmount = subtotalWithoutTax.multiply(bd(discount).divide(PERCENT_BASE, MATH_SCALE, mode)).setScale(scale, mode)
        val totalWithoutTax = (subtotalWithoutTax - discountAmount).setScale(scale, mode).max(BigDecimal.ZERO)
        val totalWithTax =
            if (tax <= 0.0) {
                totalWithoutTax
            } else {
                totalWithoutTax.multiply(divisor).setScale(scale, mode)
            }
        return SaleItemDto(
            idItem = product.id.toInt(),
            codVendedor = codVendedor.takeIf { it > 0 } ?: input.sellerId,
            itemAlmacen = input.warehouseId,
            itemDescripcion = product.description,
            itemCantidad = quantityDecimal,
            itemPrecioSinIva = unitWithoutTax.toDouble(),
            itemDescuento = discount,
            itemMontoDescuento = discountAmount.toDouble(),
            itemPIva = tax,
            itemTotalSinIva = totalWithoutTax.toDouble(),
            itemTotalConIva = totalWithTax.toDouble(),
            itemCantidadTotal = quantityTotal,
            cantidadBulto = bulkQuantity.toInt().coerceAtLeast(1),
            unidadEmpaque = product.packageLabel,
            itemUnidadEmpaque = itemUnitPackage,
            esProductoFisico = true,
            itemCodigo = product.code,
            itemReferencia = product.reference,
            idSegmento = product.gobSegment.toIntOrNull(),
            idFamilia = product.gobFamily.toIntOrNull(),
            poseeConfiguracionLote = if (hasLotConfig) "si" else "no",
            codigosLote =
                lotAssignments.map { lot ->
                    SaleLotDto(
                        idLoteItem = lot.idLoteItem.toIntOrNull() ?: 0,
                        codigoLoteItem = lot.codigoLote,
                        cantidad = lot.cantidad,
                        idAlmacen = lot.almacen,
                    )
                },
            promocionTipo = promocionTipo,
            promocionId = promocionId.orEmpty(),
            promocionCantidad = if (isPromotionLine) promocionVeces.toDouble() else 0.0,
            promocionCodigo = promocionCodigo,
            promocionNombre = promocionNombre,
            promocionGrupo = promocionGrupo,
            promocionDetalleId = promocionDetalleId,
        )
    }

    private companion object {
        // Tax percentage division needs extra precision to avoid rounding
        // the divisor (1 + tax/100) before multiplying back. 6 fractional
        // digits on a percent is sub-microcent and never affects a scale-2
        // monetary output.
        const val MATH_SCALE = 6
        private val PERCENT_BASE: BigDecimal = BigDecimal.valueOf(100.0)
        private const val PERCENT_BASE_D: Double = 100.0
    }
}
