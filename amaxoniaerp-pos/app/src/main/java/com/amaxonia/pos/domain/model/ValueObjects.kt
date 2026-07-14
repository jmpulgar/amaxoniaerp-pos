package com.amaxonia.pos.domain.model

import java.math.BigDecimal
import java.math.RoundingMode

@JvmInline
value class InvoiceId(
    val value: String,
) {
    init {
        require(value.isNotBlank()) { "InvoiceId cannot be blank" }
    }
}

@JvmInline
value class PaymentId(
    val value: String,
) {
    init {
        require(value.isNotBlank()) { "PaymentId cannot be blank" }
    }
}

@JvmInline
value class CajaId(
    val value: String,
) {
    init {
        require(value.isNotBlank()) { "CajaId cannot be blank" }
    }
}

class TaxRate private constructor(
    val percentage: BigDecimal,
) {
    fun multiplier(): BigDecimal = BigDecimal.ONE + percentage.movePointLeft(2)

    override fun equals(other: Any?): Boolean = other is TaxRate && percentage == other.percentage

    override fun hashCode(): Int = percentage.hashCode()

    override fun toString(): String = "TaxRate(${percentage.toPlainString()}%)"

    companion object {
        val ZERO = of(BigDecimal.ZERO)

        fun of(value: BigDecimal): TaxRate {
            require(value >= BigDecimal.ZERO && value <= BigDecimal("100")) {
                "Tax rate must be between 0 and 100"
            }
            return TaxRate(value.setScale(4, RoundingMode.HALF_EVEN))
        }

        fun fromDouble(value: Double): TaxRate = of(BigDecimal.valueOf(value))
    }
}

class Quantity private constructor(
    val value: BigDecimal,
) {
    init {
        require(value > BigDecimal.ZERO) { "Quantity must be greater than zero" }
    }

    override fun equals(other: Any?): Boolean = other is Quantity && value == other.value

    override fun hashCode(): Int = value.hashCode()

    override fun toString(): String = "Quantity(${value.toPlainString()})"

    companion object {
        const val SCALE = 3

        fun of(value: BigDecimal): Quantity = Quantity(value.setScale(SCALE, RoundingMode.HALF_EVEN))

        fun fromDouble(value: Double): Quantity = of(BigDecimal.valueOf(value))
    }
}

sealed interface DomainFailure {
    val message: String

    data class Validation(
        override val message: String,
    ) : DomainFailure

    data class Recoverable(
        override val message: String,
    ) : DomainFailure

    data class Permanent(
        override val message: String,
    ) : DomainFailure
}
