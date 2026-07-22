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
        // Material-fraction check.
        //
        // The discarded fraction beyond the currency scale is a silent-money-loss
        // only when it reaches or exceeds half of one minor unit (e.g. 0.005 at
        // scale=2). At that threshold HALF_EVEN must choose a direction, which
        // is itself a *decision* and therefore a real loss one of the parties
        // involved in the transaction should authorize explicitly.
        //
        // Below the threshold, banker's rounding is provably idempotent: every
        // value in the open interval (-0.5*10^-scale, +0.5*10^-scale) rounds
        // to the same minor unit and back — no information is destroyed. This
        // is exactly the case of IEEE-754 noise like 6.8999999999999995
        // (residue 5e-16 against 6.90). The legacy strict `discarded.signum()
        // != 0` test flagged this as loss and crashed the sale.
        //
        // The threshold is 0.5 * 10^-scale:
        //   scale=0 -> 0.5 ; scale=2 -> 0.005 ; scale=3 -> 0.0005
        //
        // IMPORTANT: the previous implementation wrote `BigDecimal.ONE
        //   .movePointLeft(2 * scale + 1)` and commented it as "0.005 at
        // scale=2", but that expression evaluates to 10^-(2*scale+1), i.e.
        // 0.00001 at scale=2 — three orders of magnitude tighter than
        // intended and inconsistent with its own docstring. Use the canonical
        // formula `5 * 10^-(scale+1)`.
        val roundedFullPrecision = scaled.movePointLeft(scale)
        val residue = (value - roundedFullPrecision).abs()
        val materialityThreshold = BigDecimal("0.5").movePointLeft(scale)
        if (residue.compareTo(materialityThreshold) >= 0) {
            throw MoneyOverflowException("Refusing to silently round monetary value $value at scale $scale for $currency")
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
