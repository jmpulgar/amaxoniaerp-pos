package com.amaxonia.pos.data.printer

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Auditoría ítem 8 (MONEY-001). Pure-JVM coverage of [GatewayCurrencyConversion]
 * — the numeric invariant that protects the HKA gateway from drifting Bs
 * amounts compared to the printed receipt and the backend idFactura.
 *
 * `Double` survives ONLY at the boundary (HKA's Java SDK requires `Double`)
 * but the math is performed in [java.math.BigDecimal] with HALF_EVEN and
 * clamped to scale=2, matching `Money` and `PaymentState.formatBs`.
 *
 * Locales covered: USD `en_US` (non-multi-currency passthrough), COP `es_CO`
 * (no conversion in production now, but the path is exercised by passing
 * isMultiCurrency=false), VES `es_VE` (the regression case).
 */
class GatewayCurrencyConversionTest {
    @Test
    fun multiCurrencyConversionUsesBigDecimalHalfEvenAndMatchesTheReceipt() {
        // 5.10 USD * 365.37 Bs/USD = 1863.387 → HALF_EVEN scale=2 → 1863.39
        // (legacy Double math would drift: 1863.3870000000001)
        val converted =
            GatewayCurrencyConversion.apply(
                amount = 5.10,
                exchangeRate = 365.37,
                isMultiCurrency = true,
            )
        assertEquals(1863.39, converted, 1e-9)
    }

    @Test
    fun halfEvenRoundsToEvenNeighborDigits() {
        // 0.125 scale-2 HALF_EVEN → 0.12 (rounds down to even)
        assertEquals(
            0.12,
            GatewayCurrencyConversion.apply(amount = 0.125, exchangeRate = 1.0, isMultiCurrency = true),
            1e-9,
        )
        // 0.135 scale-2 HALF_EVEN → 0.14 (rounds up to even)
        assertEquals(
            0.14,
            GatewayCurrencyConversion.apply(amount = 0.135, exchangeRate = 1.0, isMultiCurrency = true),
            1e-9,
        )
    }

    @Test(expected = IllegalStateException::class)
    fun negativeAmountIsRejectedEvenAtTheBoundary() {
        GatewayCurrencyConversion.apply(amount = -1.0, exchangeRate = 1.0, isMultiCurrency = true)
    }

    @Test(expected = IllegalStateException::class)
    fun zeroRateInMultiCurrencyFailsExplicitly() {
        GatewayCurrencyConversion.apply(amount = 1.0, exchangeRate = 0.0, isMultiCurrency = true)
    }

    @Test(expected = IllegalStateException::class)
    fun negativeRateInMultiCurrencyFailsExplicitly() {
        GatewayCurrencyConversion.apply(amount = 1.0, exchangeRate = -5.0, isMultiCurrency = true)
    }

    @Test
    fun nonMultiCurrencyPassesAmountThroughUnchangedAgainstAnyRate() {
        // Single-currency deployments (Panama "en-narrow-USD", Colombia "es_CO")
        // never multiply; the gateway sees the original amount regardless of
        // the configured exchange rate, since it's not used to charge cards.
        assertEquals(
            12.34,
            GatewayCurrencyConversion.apply(amount = 12.34, exchangeRate = 999.99, isMultiCurrency = false),
            1e-9,
        )
        // rate of zero is fine in single-currency mode (it isn't consulted).
        assertEquals(
            7.0,
            GatewayCurrencyConversion.apply(amount = 7.0, exchangeRate = 0.0, isMultiCurrency = false),
            1e-9,
        )
    }

    @Test
    fun zeroAmountIsAlwaysExplicitlyAllowed() {
        // A legitimate 0-amount HKA pre-authorization (e.g. validation only)
        // must not be misclassified as an error.
        assertEquals(
            0.0,
            GatewayCurrencyConversion.apply(amount = 0.0, exchangeRate = 365.37, isMultiCurrency = true),
            1e-9,
        )
        assertEquals(
            0.0,
            GatewayCurrencyConversion.apply(amount = 0.0, exchangeRate = 0.0, isMultiCurrency = false),
            1e-9,
        )
    }
}
