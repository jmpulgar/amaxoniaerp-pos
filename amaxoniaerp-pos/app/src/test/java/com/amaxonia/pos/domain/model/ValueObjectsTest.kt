package com.amaxonia.pos.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.math.BigDecimal

class ValueObjectsTest {
    @Test
    fun taxRateHasExplicitScaleAndMultiplier() {
        val rate = TaxRate.fromDouble(16.0)

        assertEquals(BigDecimal("16.0000"), rate.percentage)
        assertEquals(0, BigDecimal("1.1600").compareTo(rate.multiplier()))
    }

    @Test
    fun invalidTaxAndQuantityValuesAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { TaxRate.fromDouble(-0.01) }
        assertThrows(IllegalArgumentException::class.java) { TaxRate.fromDouble(100.01) }
        assertThrows(IllegalArgumentException::class.java) { Quantity.fromDouble(0.0) }
    }

    @Test
    fun quantityUsesThreeDecimalHalfEvenPrecision() {
        assertEquals(BigDecimal("1.234"), Quantity.of(BigDecimal("1.2344")).value)
        assertEquals(BigDecimal("1.234"), Quantity.of(BigDecimal("1.2345")).value)
        assertEquals(BigDecimal("1.236"), Quantity.of(BigDecimal("1.2355")).value)
    }

    @Test
    fun identifiersCannotBeBlank() {
        assertThrows(IllegalArgumentException::class.java) { InvoiceId(" ") }
        assertEquals("invoice-1", InvoiceId("invoice-1").value)
    }
}
