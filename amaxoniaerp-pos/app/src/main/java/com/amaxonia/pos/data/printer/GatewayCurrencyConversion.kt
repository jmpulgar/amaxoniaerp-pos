package com.amaxonia.pos.data.printer

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Auditoría ítem 8 (MONEY-001). Pure-JVM extract of the conversion that the
 * HKA gateway applies before crossing the legacy Java SDK boundary (which
 * only accepts `Double`).
 *
 * Keeping this in a stateless helper lets us unit-test the numeric
 * invariant without instantiating `TheFactoryRapidPayClient`/`LocalStore`,
 * and centralizes the rounding policy so every multi-currency conversion
 * in the gateway uses the exact same [BigDecimal] × HALF_EVEN rule that
 * the `Money` value object applies to printed receipts and to idFactura
 * amounts. `Double` lives ONLY at the boundary, never inside the math.
 */
internal object GatewayCurrencyConversion {
    /**
     * Returns the amount to send to the HKA SDK, computed via [BigDecimal]
     * with [Money]'s scale=2 HALF_EVEN rule.
     *
     * Throws [IllegalArgumentException] if [amount] is negative or if the
     * caller requests multi-currency conversion with a non-positive rate.
     */
    fun apply(
        amount: Double,
        exchangeRate: Double,
        isMultiCurrency: Boolean,
    ): Double {
        if (amount < 0.0) error("El monto del cobro no puede ser negativo")
        if (!isMultiCurrency) return amount
        if (exchangeRate <= 0.0) error("No se encontro una tasa valida para enviar el cobro a la pasarela")
        val amountBd = amount.toBigDecimal()
        val rateBd = exchangeRate.toBigDecimal()
        return amountBd
            .multiply(rateBd)
            .setScale(2, RoundingMode.HALF_EVEN)
            .toDouble()
    }
}
