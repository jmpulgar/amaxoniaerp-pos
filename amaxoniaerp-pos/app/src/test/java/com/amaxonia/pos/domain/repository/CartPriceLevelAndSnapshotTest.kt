package com.amaxonia.pos.domain.repository

import com.amaxonia.pos.domain.model.CartItem
import com.amaxonia.pos.domain.model.Client
import com.amaxonia.pos.domain.model.PriceLevel
import com.amaxonia.pos.domain.model.Product
import com.amaxonia.pos.domain.model.codTipoPrecioToLabel
import com.amaxonia.pos.domain.model.computeFinancialSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CartPriceLevelAndSnapshotTest {
    private lateinit var cartRepository: CartRepository

    private val sampleProduct =
        Product(
            id = "prod-1",
            code = "P001",
            description = "Test Product",
            taxRate = 16.0,
            isExempt = false,
            prices =
                listOf(
                    PriceLevel(label = "A", price = 10.0, pricePlusTax = 11.60),
                    PriceLevel(label = "B", price = 8.0, pricePlusTax = 9.28),
                    PriceLevel(label = "C", price = 7.0, pricePlusTax = 8.12),
                    PriceLevel(label = "D", price = 0.0, pricePlusTax = 0.0),
                ),
        )

    @Before
    fun setUp() {
        cartRepository = CartRepository()
    }

    @Test
    fun `codTipoPrecioToLabel maps Selectra codes correctly`() {
        assertEquals("A", codTipoPrecioToLabel(2))
        assertEquals("B", codTipoPrecioToLabel(3))
        assertEquals("C", codTipoPrecioToLabel(4))
        assertEquals("D", codTipoPrecioToLabel(5))
        assertEquals("E", codTipoPrecioToLabel(6))
        assertEquals("F", codTipoPrecioToLabel(7))
        assertEquals("A", codTipoPrecioToLabel(1))
        assertEquals("A", codTipoPrecioToLabel(null))
    }

    @Test
    fun `addToCart uses client price level or defaults to A`() {
        // Without client -> List A (11.60 with tax)
        cartRepository.addToCart(sampleProduct, 1)
        val firstItem = cartRepository.cartItems.value.single()
        assertEquals("A", firstItem.selectedPriceLabel)
        assertEquals(11.60, firstItem.unitPriceWithTax, 0.001)

        cartRepository.clearCart()

        // With client having codTipoPrecio = 3 (List B -> 9.28 with tax)
        val clientB = Client(id = "cli-1", firstName = "Client B", codTipoPrecio = 3)
        cartRepository.setClient(clientB)
        cartRepository.addToCart(sampleProduct, 1)

        val itemB = cartRepository.cartItems.value.single()
        assertEquals("B", itemB.selectedPriceLabel)
        assertEquals(9.28, itemB.unitPriceWithTax, 0.001)
    }

    @Test
    fun `addToCart falls back to A if client price level is 0`() {
        // Client with codTipoPrecio = 5 (List D has 0.0) -> Fallback to List A (11.60)
        val clientD = Client(id = "cli-d", firstName = "Client D", codTipoPrecio = 5)
        cartRepository.setClient(clientD)
        cartRepository.addToCart(sampleProduct, 1)

        val item = cartRepository.cartItems.value.single()
        assertEquals("A", item.selectedPriceLabel)
        assertEquals(11.60, item.unitPriceWithTax, 0.001)
    }

    @Test
    fun `setClient recalculates prices of items already in cart`() {
        // Add item initially with default price A
        cartRepository.addToCart(sampleProduct, 2)
        assertEquals(11.60, cartRepository.cartItems.value.first().unitPriceWithTax, 0.001)

        // Select client with Price C (8.12 with tax)
        val clientC = Client(id = "cli-c", firstName = "Client C", codTipoPrecio = 4)
        cartRepository.setClient(clientC)

        val updatedItem = cartRepository.cartItems.value.first()
        assertEquals("C", updatedItem.selectedPriceLabel)
        assertEquals(8.12, updatedItem.unitPriceWithTax, 0.001)

        // Removing client resets back to Price A
        cartRepository.removeClient()
        val resetItem = cartRepository.cartItems.value.first()
        assertEquals("A", resetItem.selectedPriceLabel)
        assertEquals(11.60, resetItem.unitPriceWithTax, 0.001)
    }

    @Test
    fun `manual price is not overwritten when client changes`() {
        cartRepository.addToCart(sampleProduct, 1)
        cartRepository.updateItemPrice("prod-1", 15.0)

        val manualItem = cartRepository.cartItems.value.first()
        assertTrue(manualItem.isManualPrice)
        assertEquals(15.0, manualItem.unitPriceWithTax, 0.001)

        // Change client to C -> manual price should remain 15.0
        val clientC = Client(id = "cli-c", firstName = "Client C", codTipoPrecio = 4)
        cartRepository.setClient(clientC)

        val afterClientChange = cartRepository.cartItems.value.first()
        assertEquals(15.0, afterClientChange.unitPriceWithTax, 0.001)
        assertTrue(afterClientChange.isManualPrice)
    }

    @Test
    fun `updateItemPriceLevel changes price to selected level and clears isManualPrice`() {
        cartRepository.addToCart(sampleProduct, 1)
        cartRepository.updateItemPrice("prod-1", 15.0)
        assertTrue(cartRepository.cartItems.value.first().isManualPrice)

        // Select List B explicitly
        cartRepository.updateItemPriceLevel("prod-1", "B")
        val item = cartRepository.cartItems.value.first()
        assertEquals("B", item.selectedPriceLabel)
        assertEquals(9.28, item.unitPriceWithTax, 0.001)
        assertFalse(item.isManualPrice)
    }

    @Test
    fun `financial snapshot is automatically calculated and updated in CartRepository`() {
        // Initially empty
        cartRepository.clearCart()
        assertEquals(null, cartRepository.financialSnapshot.value)

        // Add 2 units of sampleProduct (unitPriceWithTax = 11.60, unitPriceWithoutTax = 10.00)
        cartRepository.addToCart(sampleProduct, 2)

        val snapshot1 = cartRepository.financialSnapshot.value
        assertNotNull(snapshot1)
        assertEquals(20.0, snapshot1!!.subtotalGross, 0.01)
        assertEquals(0.0, snapshot1.itemDiscounts, 0.01)
        assertEquals(20.0, snapshot1.subtotalNet, 0.01)
        assertEquals(3.20, snapshot1.tax, 0.01)
        assertEquals(23.20, snapshot1.total, 0.01)

        // Apply 10% discount
        cartRepository.updateItemDiscount("prod-1", 10.0)
        val snapshot2 = cartRepository.financialSnapshot.value
        assertNotNull(snapshot2)
        assertEquals(20.0, snapshot2!!.subtotalGross, 0.01)
        assertEquals(2.0, snapshot2.itemDiscounts, 0.01)
        assertEquals(18.0, snapshot2.subtotalNet, 0.01)
        assertEquals(2.88, snapshot2.tax, 0.01)
        assertEquals(20.88, snapshot2.total, 0.01)

        // Remove item -> snapshot becomes null
        cartRepository.removeItem("prod-1")
        assertEquals(null, cartRepository.financialSnapshot.value)
    }

    @Test
    fun `product with zero tax rate and not exempt falls back to 7 percent ITBMS`() {
        cartRepository.clearCart()
        val panamaProduct =
            Product(
                id = "prod-pa",
                code = "PA001",
                description = "Panama Product",
                taxRate = 0.0,
                isExempt = false,
                prices =
                    listOf(
                        PriceLevel(label = "A", price = 10.0, pricePlusTax = 10.70),
                    ),
            )
        cartRepository.addToCart(panamaProduct, 1)

        val item = cartRepository.cartItems.value.single()
        assertEquals(7.0, item.taxRate, 0.001)
        assertEquals(10.0, item.unitPriceWithoutTax, 0.01)
        assertEquals(10.70, item.unitPriceWithTax, 0.01)

        val snapshot = cartRepository.financialSnapshot.value
        assertNotNull(snapshot)
        assertEquals(10.0, snapshot!!.subtotalGross, 0.01)
        assertEquals(0.0, snapshot.itemDiscounts, 0.01)
        assertEquals(10.0, snapshot.subtotalNet, 0.01)
        assertEquals(0.70, snapshot.tax, 0.01)
        assertEquals(10.70, snapshot.total, 0.01)
    }

    @Test
    fun `price level with discount percent propagates to cart item and snapshot`() {
        cartRepository.clearCart()
        val discountedProduct =
            Product(
                id = "prod-disc",
                code = "DISC01",
                description = "Discounted Product",
                taxRate = 7.0,
                isExempt = false,
                prices =
                    listOf(
                        PriceLevel(label = "A", price = 10.0, pricePlusTax = 10.70, discountPercent = 10.0),
                    ),
            )
        cartRepository.addToCart(discountedProduct, 1)

        val item = cartRepository.cartItems.value.single()
        assertEquals(10.0, item.discountPercent, 0.001)

        val snapshot = cartRepository.financialSnapshot.value
        assertNotNull(snapshot)
        assertEquals(10.0, snapshot!!.subtotalGross, 0.01)
        assertEquals(1.0, snapshot.itemDiscounts, 0.01)
        assertEquals(9.0, snapshot.subtotalNet, 0.01)
        assertEquals(0.63, snapshot.tax, 0.01)
        assertEquals(9.63, snapshot.total, 0.01)
    }
}
