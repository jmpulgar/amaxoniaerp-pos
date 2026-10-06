package com.amaxonia.kiosk.domain.flow

import com.amaxonia.kiosk.core.network.KioskConfigResponse
import com.amaxonia.kiosk.core.network.KioskHttpClientFactory
import com.amaxonia.kiosk.domain.payment.PaymentMethod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CheckoutFlowPolicyTest {
    @Test
    fun `dining mode screen is skipped only when exactly one mode is enabled`() {
        assertEquals("PARA_LLEVAR", CheckoutFlowPolicy.singleDiningMode(KioskConfigResponse(diningModes = listOf("PARA_LLEVAR"))))
        assertNull(CheckoutFlowPolicy.singleDiningMode(KioskConfigResponse(diningModes = listOf("COMER_AQUI", "PARA_LLEVAR"))))
        assertNull(CheckoutFlowPolicy.singleDiningMode(KioskConfigResponse(diningModes = emptyList())))
        assertNull(CheckoutFlowPolicy.singleDiningMode(null))
    }

    @Test
    fun `table tent only for dispatch MESAS and eat-in`() {
        val mesas = KioskConfigResponse(dispatch = "MESAS")
        assertTrue(CheckoutFlowPolicy.needsTableTent(mesas, "COMER_AQUI"))
        assertFalse(CheckoutFlowPolicy.needsTableTent(mesas, "PARA_LLEVAR"))
        assertFalse(CheckoutFlowPolicy.needsTableTent(KioskConfigResponse(dispatch = "RETIRO_MOSTRADOR"), "COMER_AQUI"))
        assertFalse(CheckoutFlowPolicy.needsTableTent(null, "COMER_AQUI"))
    }

    @Test
    fun `payment methods come from config and CARD is hidden without a terminal`() {
        val both = KioskConfigResponse(paymentMethods = listOf("CARD", "YAPPY", "BITCOIN"))
        assertEquals(listOf(PaymentMethod.CARD, PaymentMethod.YAPPY), CheckoutFlowPolicy.availablePaymentMethods(both, true))
        assertEquals(listOf(PaymentMethod.YAPPY), CheckoutFlowPolicy.availablePaymentMethods(both, false))
        assertEquals(listOf(PaymentMethod.CARD), CheckoutFlowPolicy.availablePaymentMethods(null, true))
        assertEquals(emptyList<PaymentMethod>(), CheckoutFlowPolicy.availablePaymentMethods(null, false))
    }

    @Test
    fun `config without paymentMethods defaults to CARD`() {
        val config = KioskHttpClientFactory.jsonConfig.decodeFromString(KioskConfigResponse.serializer(), """{"version": 2}""")
        assertEquals(listOf("CARD"), config.paymentMethods)
    }
}
