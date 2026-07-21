package com.amaxonia.pos.domain.model.money

import java.math.BigDecimal
import java.math.RoundingMode

enum class Currency {
    USD,
    VES,
    PAB,
}

class Money private constructor(
    private val amount: BigDecimal,
    val currency: Currency,
) : Comparable<Money> {
    operator fun plus(other: Money): Money {
        requireSameCurrency(other)
        return of(amount + other.amount, currency)
    }

    operator fun minus(other: Money): Money {
        requireSameCurrency(other)
        return of(amount - other.amount, currency)
    }

    operator fun times(multiplier: BigDecimal): Money = of(amount * multiplier, currency)

    fun coerceAtLeastZero(): Money = if (amount.signum() < 0) zero(currency) else this

    override fun compareTo(other: Money): Int {
        requireSameCurrency(other)
        return amount.compareTo(other.amount)
    }

    fun toDouble(): Double = amount.toDouble()

    fun toPlainString(): String = amount.toPlainString()

    /**
     * Canonical [BigDecimal] backing this [Money]. Read-only accessor for
     * boundary conversions (see [MinorUnitMoney]); the scale is fixed at
     * [SCALE] so callers receive a stable representation.
     */
    fun toBigDecimal(): BigDecimal = amount

    override fun equals(other: Any?): Boolean = other is Money && amount == other.amount && currency == other.currency

    override fun hashCode(): Int = 31 * amount.hashCode() + currency.hashCode()

    override fun toString(): String = "Money(amount=${amount.toPlainString()}, currency=$currency)"

    private fun requireSameCurrency(other: Money) {
        require(currency == other.currency) {
            "Cannot operate with different currencies: $currency and ${other.currency}"
        }
    }

    companion object {
        const val SCALE = 2
        val ROUNDING_MODE: RoundingMode = RoundingMode.HALF_EVEN
        val ZERO: Money = zero(Currency.USD)

        fun zero(currency: Currency = Currency.USD): Money = of(BigDecimal.ZERO, currency)

        fun of(
            value: BigDecimal,
            currency: Currency = Currency.USD,
        ): Money = Money(value.setScale(SCALE, ROUNDING_MODE), currency)

        fun fromDouble(
            value: Double,
            currency: Currency = Currency.USD,
        ): Money = of(BigDecimal.valueOf(value), currency)

        fun parse(
            input: String?,
            currency: Currency = Currency.USD,
        ): Money {
            val normalized = input?.takeUnless(String::isBlank)?.let(::normalizeInput).orEmpty()
            return normalized
                .takeUnless(String::isBlank)
                ?.toBigDecimalOrNull()
                ?.let { of(it, currency) }
                ?: zero(currency)
        }

        fun normalizeInput(rawInput: String): String {
            val raw = rawInput.replace(',', '.').filter { it.isDigit() || it == '.' }
            if (raw.isBlank()) return ""

            val firstDotIndex = raw.indexOf('.')
            val sanitized =
                if (firstDotIndex == -1) {
                    raw
                } else {
                    val integer = raw.substring(0, firstDotIndex)
                    val decimals = raw.substring(firstDotIndex + 1).replace(".", "").take(SCALE)
                    "$integer.$decimals"
                }

            val hasDot = sanitized.contains('.')
            val integerPart = sanitized.substringBefore('.').trimStart('0').ifBlank { "0" }
            val decimalPart = if (hasDot) sanitized.substringAfter('.', "") else ""
            return if (hasDot) "$integerPart.$decimalPart" else integerPart
        }

        fun format(amount: Money): String = amount.toPlainString()

        fun format(input: String?): String = format(parse(input))
    }
}
