package com.amaxonia.pos.domain.repository

import com.amaxonia.pos.domain.model.PriceLevel
import com.amaxonia.pos.domain.model.Product
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class CartCorrelationIdTest {
    private lateinit var cartRepository: CartRepository

    private val sampleProduct =
        Product(
            id = "prod-1",
            code = "P001",
            description = "Test Product",
            taxRate = 16.0,
            isExempt = false,
            prices = listOf(PriceLevel(label = "A", price = 10.0, pricePlusTax = 11.60)),
        )

    @Before
    fun setUp() {
        cartRepository = CartRepository()
    }

    @Test
    fun `getOrCreateCheckoutCorrelationId mints ID and reuses it on subsequent calls`() {
        assertNull(cartRepository.checkoutCorrelationId.value)

        var counter = 0
        val id1 = cartRepository.getOrCreateCheckoutCorrelationId { "custom-id-${++counter}" }
        val id2 = cartRepository.getOrCreateCheckoutCorrelationId { "custom-id-${++counter}" }

        assertEquals("custom-id-1", id1)
        assertEquals("custom-id-1", id2)
        assertEquals("custom-id-1", cartRepository.checkoutCorrelationId.value)
    }

    @Test
    fun `clearCheckoutCorrelationId resets the correlation ID`() {
        val id1 = cartRepository.getOrCreateCheckoutCorrelationId { "uuid-123" }
        assertEquals("uuid-123", id1)

        cartRepository.clearCheckoutCorrelationId()
        assertNull(cartRepository.checkoutCorrelationId.value)

        val id2 = cartRepository.getOrCreateCheckoutCorrelationId { "uuid-456" }
        assertEquals("uuid-456", id2)
    }

    @Test
    fun `modifying cart items invalidates checkoutCorrelationId`() {
        cartRepository.addToCart(sampleProduct)
        val initialId = cartRepository.getOrCreateCheckoutCorrelationId { "cart-attempt-1" }
        assertEquals("cart-attempt-1", initialId)

        // Adding another item invalidates the correlation ID
        cartRepository.addToCart(sampleProduct)
        assertNull(cartRepository.checkoutCorrelationId.value)

        val newIdAfterAdd = cartRepository.getOrCreateCheckoutCorrelationId { "cart-attempt-2" }
        assertNotEquals(initialId, newIdAfterAdd)

        // Updating item quantity invalidates correlation ID
        cartRepository.updateItemQuantity("prod-1", 5)
        assertNull(cartRepository.checkoutCorrelationId.value)

        val newIdAfterQuantity = cartRepository.getOrCreateCheckoutCorrelationId { "cart-attempt-3" }
        assertNotEquals(newIdAfterAdd, newIdAfterQuantity)

        // Removing item invalidates correlation ID
        cartRepository.removeItem("prod-1")
        assertNull(cartRepository.checkoutCorrelationId.value)
    }

    @Test
    fun `clearCart and clearItemsOnly reset checkoutCorrelationId`() {
        cartRepository.addToCart(sampleProduct)
        cartRepository.getOrCreateCheckoutCorrelationId { "id-to-clear" }
        assertEquals("id-to-clear", cartRepository.checkoutCorrelationId.value)

        cartRepository.clearCart()
        assertNull(cartRepository.checkoutCorrelationId.value)

        cartRepository.addToCart(sampleProduct)
        cartRepository.getOrCreateCheckoutCorrelationId { "id-to-clear-2" }
        assertEquals("id-to-clear-2", cartRepository.checkoutCorrelationId.value)

        cartRepository.clearItemsOnly()
        assertNull(cartRepository.checkoutCorrelationId.value)
    }
}
