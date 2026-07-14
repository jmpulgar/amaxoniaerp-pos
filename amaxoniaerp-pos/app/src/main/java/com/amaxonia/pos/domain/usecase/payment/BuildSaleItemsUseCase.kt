package com.amaxonia.pos.domain.usecase.payment

import com.amaxonia.pos.domain.model.CartItem
import com.amaxonia.pos.domain.model.sales.SaleItemDto
import com.amaxonia.pos.domain.model.sales.SaleLotDto

data class BuildSaleItemsInput(
    val cartItems: List<CartItem>,
    val warehouseId: Int,
    val sellerId: Int,
    val defaultTaxRate: Double,
)

/**
 * Compatibility mapper for the installed backend contract. Arithmetic order is
 * intentionally identical to the characterized legacy implementation.
 */
class BuildSaleItemsUseCase {
    operator fun invoke(input: BuildSaleItemsInput): List<SaleItemDto> = input.cartItems.map { item -> item.toSaleItem(input) }

    private fun CartItem.toSaleItem(input: BuildSaleItemsInput): SaleItemDto {
        val tax = if (product.isExempt) 0.0 else product.taxRate.takeIf { it > 0.0 } ?: input.defaultTaxRate
        val divisor = 1.0 + (tax / PERCENT_BASE)
        val unitWithoutTax = if (tax <= 0.0) unitPriceWithTax else unitPriceWithTax / divisor
        val subtotalWithoutTax = unitWithoutTax * quantityDecimal
        val discount = discountPercent.coerceIn(0.0, PERCENT_BASE)
        val discountAmount = subtotalWithoutTax * (discount / PERCENT_BASE)
        val totalWithoutTax = (subtotalWithoutTax - discountAmount).coerceAtLeast(0.0)
        val totalWithTax = if (tax <= 0.0) totalWithoutTax else totalWithoutTax * divisor
        return SaleItemDto(
            idItem = product.id.toInt(),
            codVendedor = codVendedor.takeIf { it > 0 } ?: input.sellerId,
            itemAlmacen = input.warehouseId,
            itemDescripcion = product.description,
            itemCantidad = quantityDecimal,
            itemPrecioSinIva = unitWithoutTax,
            itemDescuento = discount,
            itemMontoDescuento = discountAmount,
            itemPIva = tax,
            itemTotalSinIva = totalWithoutTax,
            itemTotalConIva = totalWithTax,
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
        const val PERCENT_BASE = 100.0
    }
}
