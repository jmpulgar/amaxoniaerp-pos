package com.amaxonia.pos.ui.payment

import com.amaxonia.pos.domain.model.money.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MoneyCharacterizationTest {
    @Test
    fun parsesAndNormalizesCurrentInputFormats() {
        assertEquals("12.34", Money.format("00012,349"))
        assertEquals("1.23", Money.format("1.2.3"))
        assertEquals("0.00", Money.format("not-a-number"))
        assertEquals("0.10", Money.format(Money.fromDouble(0.1 + 0.2 - 0.2)))
    }

    @Test
    fun usesHalfEvenAtTwoDecimals() {
        assertEquals("1.00", Money.format(Money.fromDouble(1.005)))
        assertEquals("1.02", Money.format(Money.fromDouble(1.015)))
        assertEquals("999999999.99", Money.format("999999999.99"))
    }

    @Test
    fun cashPaymentCalculatesChangeAndInsufficientState() {
        val sufficient = PaymentState(totalAmount = 10.10, tenderedAmountInput = "20.00")
        val insufficient = sufficient.copy(tenderedAmountInput = "10.09")

        assertEquals("9.90", sufficient.changeDueText)
        assertTrue(sufficient.isPaymentEnough)
        assertEquals("0.00", insufficient.changeDueText)
        assertFalse(insufficient.isPaymentEnough)
    }

    @Test
    fun splitNonCashPaymentSumsCentValuesWithoutBinaryDrift() {
        val state =
            PaymentState(
                totalAmount = 0.30,
                selectedMethod = PaymentMethod.NON_CASH,
                nonCashAmountsInput = mapOf(1 to "0.10", 2 to "0.20"),
            )

        assertEquals("0.30", state.nonCashAssignedText)
        assertEquals("0.00", state.nonCashPendingText)
        assertTrue(state.isPaymentEnough)
    }

    @Test
    fun currentMultiCurrencyDisplayRoundsToTwoDecimals() {
        val state =
            PaymentState(
                totalAmount = 10.01,
                tenderedAmountInput = "20.00",
                isMultiCurrency = true,
                tasa = 36.5,
                abrMonedaSecundaria = "VES",
            )

        assertEquals("365.37", state.totalAmountBsText)
        assertEquals("364.64", state.changeDueBsText)
        assertEquals("Bs.", state.monedaSecundariaLabel)
    }
}
