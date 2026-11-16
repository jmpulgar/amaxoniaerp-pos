package com.amaxonia.pos.domain.repository

import com.amaxonia.pos.domain.model.CartItem
import com.amaxonia.pos.domain.model.Product
import kotlinx.coroutines.flow.update

/** Línea de un pedido del ERP a restaurar en el carrito con sus condiciones pactadas. */
data class RestoredCartLine(
    val product: Product,
    val quantity: Double,
    val unit: String,
    val unitPriceWithTax: Double,
    val discountPercent: Double,
    val sellerId: Int,
)

/**
 * Agrega una línea de pedido al carrito conservando cantidad decimal, precio y descuento del
 * pedido (precio manual: no se recalcula al cambiar el cliente). Si el mismo producto ya está
 * con el mismo precio, unidad y descuento, se suman cantidades.
 */
fun CartRepository.restorePedidoLine(line: RestoredCartLine) {
    val quantity = line.quantity.coerceAtLeast(0.0)
    if (quantity <= 0.0) return
    cartItemsState.update { items ->
        val index =
            items.indexOfFirst {
                it.product.id == line.product.id &&
                    !it.isPromotionLine &&
                    it.composition == null &&
                    it.itemUnitPackage == line.unit &&
                    it.unitPriceWithTax == line.unitPriceWithTax &&
                    it.discountPercent == line.discountPercent
            }
        if (index >= 0) {
            items.toMutableList().also { mutable ->
                val existing = mutable[index]
                val merged = existing.quantityDecimal + quantity
                mutable[index] = existing.copy(quantity = merged.toInt().coerceAtLeast(1), quantityDecimal = merged)
            }
        } else {
            items +
                CartItem(
                    product = line.product,
                    quantity = quantity.toInt().coerceAtLeast(1),
                    quantityDecimal = quantity,
                    itemUnitPackage = line.unit,
                    codVendedor = line.sellerId,
                    unitPriceWithTax = line.unitPriceWithTax,
                    discountPercent = line.discountPercent.coerceIn(0.0, MAX_DISCOUNT),
                    isManualPrice = true,
                )
        }
    }
    invalidateFinancialSnapshot()
}

private const val MAX_DISCOUNT = 100.0
