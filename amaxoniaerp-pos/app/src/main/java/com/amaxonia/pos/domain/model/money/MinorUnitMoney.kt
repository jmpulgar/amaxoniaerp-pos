package com.amaxonia.pos.domain.model.money

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Boundary conversions between [Money] (domain, BigDecimal, scale-per-currency)
 * and the durable [Long] minor-units representation stored in Room.
 *
 * Required by docs/auditoria-produccion-pos-2026-07-20.md ítem 8 (MONEY-001):
 * persistence is canonical minor-units, domain is [Money], conversion happens
 * ONLY at the boundary, with explicit rounding per currency. Double must
 * never be used for monetary calculations, comparisons or persistence.
 */
object MinorUnitMoney {
    /**
     * Decimal scale assumed for the currently supported currencies (USD, VES,
     * PAB). 2 = cents. The plan explicitly forbids assuming scale 2 for
     * unsupported currencies; [scaleFor] is the single point future currencies
     * must extend.
     */
    const val DEFAULT_SCALE: Int = 2

    @Suppress("UnusedParameter")
    fun scaleFor(currency: Currency): Int = DEFAULT_SCALE

    /**
     * Converts a Double (legacy persistence representation) to minor-units
     * without ever using [Double] arithmetic on the monetary value beyond the
     * initial [BigDecimal.valueOf] boxing.
     *
     * Throws [MoneyOverflowException] when the value cannot fit in a signed
     * 64-bit minor-unit (|amount| > ~92 trillion in base units), or when the
     * decimal precision exceeds the scale and the discarded fraction is
     * material — i.e. rounding would silently lose money.
     */
    fun fromDoubleAsMinor(
        value: Double?,
        currency: Currency = Currency.USD,
        roundingMode: RoundingMode = RoundingMode.HALF_EVEN,
    ): Long {
        if (value == null || value.isNaN() || value.isInfinite()) {
            return 0L
        }
        val bd = BigDecimal.valueOf(value)
        return fromBigDecimalAsMinor(bd, currency, roundingMode)
    }

    fun fromBigDecimalAsMinor(
        value: BigDecimal,
        currency: Currency = Currency.USD,
        roundingMode: RoundingMode = RoundingMode.HALF_EVEN,
    ): Long {
        val scale = scaleFor(currency)
        val scaled =
            value
                .setScale(scale, roundingMode)
                .movePointRight(scale)
        // material-fraction check: any non-zero digit beyond the currency scale
        // is a silent-money-loss and must abort, not silently round. Computes the
        // discarded fraction as a BigDecimal (mod 10**trailing) so the unscaled
        // BigInteger is only used for the zero-test, never for arithmetic.
        val stripped = value.stripTrailingZeros()
        val trailing = stripped.scale() - scale
        if (trailing > 0) {
            val modulus = BigDecimal.TEN.pow(trailing)
            val discarded = BigDecimal(stripped.unscaledValue()).remainder(modulus)
            if (discarded.signum() != 0) {
                throw MoneyOverflowException("Refusing to silently round monetary value $value at scale $scale for $currency")
            }
        }
        try {
            return scaled.longValueExact()
        } catch (e: ArithmeticException) {
            throw MoneyOverflowException("Monetary value $value minor-units does not fit in Int64 for $currency", e)
        }
    }

    fun toMoney(
        minor: Long,
        currency: Currency = Currency.USD,
    ): Money {
        val scale = scaleFor(currency)
        val bd = BigDecimal.valueOf(minor).movePointLeft(scale)
        return Money.of(bd, currency)
    }

    fun toMinor(money: Money): Long = fromBigDecimalAsMinor(money.toBigDecimal(), money.currency)
}

/** Raised by [MinorUnitMoney] when a monetary conversion would lose precision or overflow Int64. */
class MoneyOverflowException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
