package com.amaxonia.kiosk.core.money

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class MoneyTest {
    @Test
    fun `plus adds amounts correctly in same currency`() {
        val m1 = Money.fromString("10.50")
        val m2 = Money.fromString("5.25")
        val result = m1 + m2
        assertEquals(Money.fromString("15.75"), result)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `plus throws on distinct currencies`() {
        val m1 = Money(BigDecimal("10.00"), "USD")
        val m2 = Money(BigDecimal("10.00"), "VED")
        m1 + m2
    }

    @Test
    fun `minus subtracts amounts correctly`() {
        val m1 = Money.fromString("20.00")
        val m2 = Money.fromString("7.35")
        val result = m1 - m2
        assertEquals(Money.fromString("12.65"), result)
    }

    @Test
    fun `times multiplies by int and bigdecimal`() {
        val base = Money.fromString("4.50")
        val resultInt = base * 3
        assertEquals(Money.fromString("13.50"), resultInt)

        val resultDec = base * BigDecimal("1.5")
        assertEquals(Money.fromString("6.75"), resultDec)
    }

    @Test
    fun `toDisplayString formats dollar amount with two decimals and comma`() {
        val money = Money.fromString("1250.50")
        assertEquals("$1,250.50", money.toDisplayString())
    }

    @Test
    fun `toSecondaryCurrency converts using given rate`() {
        val money = Money.fromString("10.00")
        val rate = BigDecimal("36.50")
        assertEquals("Ref. Bs 365.00", money.toSecondaryCurrency(rate, "Bs"))
    }

    @Test
    fun `comparison orders money amounts correctly`() {
        val m1 = Money.fromString("5.00")
        val m2 = Money.fromString("10.00")
        val m3 = Money.fromString("5.00")

        assertTrue(m1 < m2)
        assertTrue(m2 > m1)
        assertEquals(0, m1.compareTo(m3))
    }

    @Test
    fun `serialization and deserialization works with kotlinx serialization`() {
        val original = Money.fromString("42.99")
        val json = Json.encodeToString(Money.serializer(), original)
        assertEquals("\"42.99\"", json)

        val restored = Json.decodeFromString(Money.serializer(), json)
        assertEquals(original, restored)
    }
}
