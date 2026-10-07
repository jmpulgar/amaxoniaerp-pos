package com.amaxonia.kiosk.core.money

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

object MoneySerializer : KSerializer<Money> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("Money", PrimitiveKind.STRING)

    override fun serialize(
        encoder: Encoder,
        value: Money,
    ) {
        encoder.encodeString(value.amount.setScale(2, RoundingMode.HALF_UP).toPlainString())
    }

    override fun deserialize(decoder: Decoder): Money {
        val stringValue = decoder.decodeString()
        return Money.fromString(stringValue)
    }
}

@Serializable(with = MoneySerializer::class)
data class Money(
    val amount: BigDecimal,
    val currency: String = "USD",
) : Comparable<Money> {
    operator fun plus(other: Money): Money {
        require(currency == other.currency) { "No se pueden sumar distintas monedas: $currency y ${other.currency}" }
        return Money(amount.add(other.amount).setScale(2, RoundingMode.HALF_UP), currency)
    }

    operator fun minus(other: Money): Money {
        require(currency == other.currency) { "No se pueden restar distintas monedas: $currency y ${other.currency}" }
        return Money(amount.subtract(other.amount).setScale(2, RoundingMode.HALF_UP), currency)
    }

    operator fun times(factor: BigDecimal): Money {
        return Money(amount.multiply(factor).setScale(2, RoundingMode.HALF_UP), currency)
    }

    operator fun times(factor: Int): Money {
        return times(BigDecimal(factor))
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Money) return false
        return currency == other.currency && amount.compareTo(other.amount) == 0
    }

    override fun hashCode(): Int {
        var result = amount.stripTrailingZeros().hashCode()
        result = 31 * result + currency.hashCode()
        return result
    }

    fun toDisplayString(symbol: String = "$"): String {
        val scaled = amount.setScale(2, RoundingMode.HALF_UP)
        val symbols =
            DecimalFormatSymbols(Locale.US).apply {
                groupingSeparator = ','
                decimalSeparator = '.'
            }
        val formatter = DecimalFormat("#,##0.00", symbols)
        return "$symbol${formatter.format(scaled)}"
    }

    fun toSecondaryCurrency(
        rate: BigDecimal,
        symbol: String = "Bs",
    ): String {
        if (rate <= BigDecimal.ZERO) return ""
        val converted = amount.multiply(rate).setScale(2, RoundingMode.HALF_UP)
        val symbols =
            DecimalFormatSymbols(Locale.US).apply {
                groupingSeparator = ','
                decimalSeparator = '.'
            }
        val formatter = DecimalFormat("#,##0.00", symbols)
        return "Ref. $symbol ${formatter.format(converted)}"
    }

    override fun compareTo(other: Money): Int {
        require(currency == other.currency) { "No se pueden comparar distintas monedas: $currency y ${other.currency}" }
        return amount.compareTo(other.amount)
    }

    override fun toString(): String = toDisplayString()

    companion object {
        val ZERO = Money(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP))

        fun fromDouble(
            value: Double,
            currency: String = "USD",
        ): Money {
            return Money(BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP), currency)
        }

        fun fromString(
            value: String,
            currency: String = "USD",
        ): Money {
            val clean = value.replace(",", "").trim()
            val parsed = if (clean.isBlank()) BigDecimal.ZERO else BigDecimal(clean)
            return Money(parsed.setScale(2, RoundingMode.HALF_UP), currency)
        }

        private const val CENTS_FACTOR = 100L

        fun fromCents(
            cents: Long,
            currency: String = "USD",
        ): Money {
            return Money(BigDecimal(cents).divide(BigDecimal(CENTS_FACTOR), 2, RoundingMode.HALF_UP), currency)
        }
    }
}
