package com.amaxonia.pos.domain.usecase.mesas

import com.amaxonia.pos.domain.model.CartItem
import com.amaxonia.pos.domain.model.mesas.CrearPedidoMesaItemRequest
import com.amaxonia.pos.domain.model.money.Money
import java.math.BigDecimal

data class BuildPedidoMesaItemsInput(
    val cartItems: List<CartItem>,
    val warehouseId: Int,
    val sellerId: Int,
    val defaultTaxRate: Double,
)

/**
 * Convierte [CartItem] del carrito compartido a [CrearPedidoMesaItemRequest]. Replica el mapping
 * de [com.amaxonia.pos.domain.usecase.payment.BuildSaleItemsUseCase] porque el backend valida los
 * mismos campos (`_item_*`, `_cantidad_bulto`, `unidad_empaque`, promocion_*). No se duplica el
 * carrito: solo se transforma su contenido para serializarlo como líneas de pedido de mesa.
 *
 * Notas fiscales del dominio:
 * - El pedido se almacena con `precioSinIva` y se recalcula en facturación; aquí usamos el mismo
 *   redondeo a escala 2 que usa el flujo de venta para evitar residuos IEEE-754.
 * - El almacén (`itemAlmacen`) y el vendedor provienen de la caja activa: el backend no los acepta
 *   nulos y se reproducen tal cual SaleItemDto.
 */
class BuildPedidoMesaItemsUseCase {
    operator fun invoke(input: BuildPedidoMesaItemsInput): List<CrearPedidoMesaItemRequest> =
        input.cartItems.map { item -> item.toPedidoItem(input) }

    private fun CartItem.toPedidoItem(input: BuildPedidoMesaItemsInput): CrearPedidoMesaItemRequest {
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

        return CrearPedidoMesaItemRequest(
            productoId = product.id.toInt(),
            itemAlmacen = input.warehouseId,
            itemCodigo = product.code,
            itemDescripcion = product.description,
            itemCantidad = quantityDecimal,
            itemPrecioSinIva = unitWithoutTax.toDouble(),
            itemDescuento = discount,
            itemMontoDescuento = discountAmount.toDouble(),
            itemPIva = tax,
            itemTotalSinIva = totalWithoutTax.toDouble(),
            itemTotalConIva = totalWithTax.toDouble(),
            cantidadBulto = bulkQuantity.toInt().coerceAtLeast(1),
            unidadEmpaque = itemUnitPackage,
            notas = null,
            promocionId = promocionId,
            promocionTipo = promocionTipo.ifBlank { null },
            promocionDetalleId = promocionDetalleId.ifBlank { null },
        )
    }

    private companion object {
        const val MATH_SCALE = 6
        private val PERCENT_BASE: BigDecimal = BigDecimal.valueOf(100.0)
        private const val PERCENT_BASE_D: Double = 100.0
    }
}
