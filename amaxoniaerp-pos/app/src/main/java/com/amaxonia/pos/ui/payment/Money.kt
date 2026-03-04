package com.amaxonia.pos.ui.payment

import java.math.BigDecimal
import java.math.RoundingMode

object Money {
    private const val SCALE = 2

    fun fromDouble(value: Double): BigDecimal {
        return BigDecimal.valueOf(value).setScale(SCALE, RoundingMode.HALF_EVEN)
    }

    fun parse(input: String?): BigDecimal {
        if (input.isNullOrBlank()) return BigDecimal.ZERO.setScale(SCALE, RoundingMode.HALF_EVEN)
        val normalized = normalizeInput(input)
        if (normalized.isBlank()) return BigDecimal.ZERO.setScale(SCALE, RoundingMode.HALF_EVEN)
        return normalized.toBigDecimalOrNull()?.setScale(SCALE, RoundingMode.HALF_EVEN)
            ?: BigDecimal.ZERO.setScale(SCALE, RoundingMode.HALF_EVEN)
    }

    fun normalizeInput(rawInput: String): String {
        val raw = rawInput.replace(',', '.').filter { it.isDigit() || it == '.' }
        if (raw.isBlank()) return ""

        val firstDotIndex = raw.indexOf('.')
        val sanitized = if (firstDotIndex == -1) {
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

    fun format(amount: BigDecimal): String {
        return amount.setScale(SCALE, RoundingMode.HALF_EVEN).toPlainString()
    }

    fun format(input: String?): String {
        return format(parse(input))
    }

    fun toDouble(amount: BigDecimal): Double {
        return amount.setScale(SCALE, RoundingMode.HALF_EVEN).toDouble()
    }
}
