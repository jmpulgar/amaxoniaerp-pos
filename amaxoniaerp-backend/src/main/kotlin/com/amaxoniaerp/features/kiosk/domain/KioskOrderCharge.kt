package com.amaxoniaerp.features.kiosk.domain

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Desglose del monto a cobrar de un pedido cotizado, con la misma regla que el recibo de
 * `PlaceKioskOrderService`: subtotal = Σ round2(precioUnitario × cantidad), total = total
 * cotizado del pedido y tax = total − subtotal (nunca negativo). Así `subTotal + tax == total`
 * siempre, que es lo que exige el cobro externo (Yappy).
 */
data class KioskOrderCharge(
    val subTotal: BigDecimal,
    val tax: BigDecimal,
    val total: BigDecimal,
) {
    companion object {
        private const val MONEY_SCALE = 2

        fun from(order: KioskOrderRecord): KioskOrderCharge {
            val total = order.total.setScale(MONEY_SCALE, RoundingMode.HALF_UP)
            val linesSubtotal =
                order.items
                    .fold(BigDecimal.ZERO.setScale(MONEY_SCALE)) { acc, item ->
                        acc + (item.precioUnitario * item.cantidad).setScale(MONEY_SCALE, RoundingMode.HALF_UP)
                    }
            val subTotal = linesSubtotal.min(total)
            val tax = (total - subTotal).setScale(MONEY_SCALE, RoundingMode.HALF_UP)
            return KioskOrderCharge(subTotal = subTotal, tax = tax, total = total)
        }
    }
}
