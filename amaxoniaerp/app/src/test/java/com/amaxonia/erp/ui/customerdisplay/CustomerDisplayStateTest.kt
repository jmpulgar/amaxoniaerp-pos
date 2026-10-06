package com.amaxonia.erp.ui.customerdisplay

import com.amaxonia.erp.domain.model.PriceLevel
import com.amaxonia.erp.domain.model.Product
import com.amaxonia.erp.ui.pos.CartItem
import com.amaxonia.erp.ui.pos.CompletedSaleInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomerDisplayStateTest {

    @Test
    fun defaultState_isIdle() {
        val state = CustomerDisplayState()
        assertTrue(state.isIdle)
        assertFalse(state.isSuccess)
        assertEquals("PA", state.countryCode)
    }

    @Test
    fun activeCart_isNotIdle() {
        val product = Product(
            id = "1",
            code = "P1",
            description = "Soda",
            prices = listOf(PriceLevel(label = "General", price = 2.0, pricePlusTax = 2.0)),
        )
        val state = CustomerDisplayState(
            cartItems = listOf(CartItem(product = product, quantity = 2.0)),
            subtotal = 4.0,
            total = 4.0,
        )
        assertFalse(state.isIdle)
        assertFalse(state.isSuccess)
        assertEquals(1, state.cartItems.size)
    }

    @Test
    fun paymentProcessing_isNotIdle() {
        val state = CustomerDisplayState(
            isPaymentProcessing = true,
            paymentProcessingMessage = "Procesando pago fiscal...",
        )
        assertFalse(state.isIdle)
        assertFalse(state.isSuccess)
    }

    @Test
    fun completedSale_isSuccess() {
        val completedInfo = CompletedSaleInfo(
            facturaId = "101",
            numeroFactura = "FAC-00101",
            clientName = "Juan Perez",
            total = 25.50,
            receivedAmount = 30.0,
            changeAmount = 4.50,
            paymentMethodName = "Efectivo",
        )
        val state = CustomerDisplayState(completedSaleInfo = completedInfo)
        assertFalse(state.isIdle)
        assertTrue(state.isSuccess)
        assertEquals("FAC-00101", state.completedSaleInfo?.numeroFactura)
        assertEquals(4.50, state.completedSaleInfo?.changeAmount ?: 0.0, 0.001)
    }
}
