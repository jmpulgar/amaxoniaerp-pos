package com.amaxonia.pos.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class CartItemMoneyCharacterizationTest {
    @Test
    fun taxableLinePreservesCurrentTaxInclusiveCalculations() {
        val item =
            CartItem(
                product = Product(id = "1", taxRate = 16.0, isExempt = false),
                quantityDecimal = 3.0,
                unitPriceWithTax = 11.60,
                discountPercent = 10.0,
            )

        assertEquals(10.0, item.unitPriceWithoutTax, EPSILON)
        assertEquals(30.0, item.subtotalWithoutTax, EPSILON)
        assertEquals(3.0, item.discountAmountWithoutTax, EPSILON)
        assertEquals(27.0, item.totalWithoutTax, EPSILON)
        assertEquals(31.32, item.totalWithTax, EPSILON)
    }

    @Test
    fun exemptAndDecimalQuantityLineHasNoTax() {
        val item =
            CartItem(
                product = Product(id = "2", taxRate = 16.0, isExempt = true),
                quantityDecimal = 0.125,
                unitPriceWithTax = 8.0,
            )

        assertEquals(0.0, item.taxRate, EPSILON)
        assertEquals(1.0, item.totalWithTax, EPSILON)
    }

    @Test
    fun discountIsClampedToCurrentZeroToHundredRange() {
        val product = Product(id = "3", isExempt = true)

        assertEquals(
            0.0,
            CartItem(product, unitPriceWithTax = 10.0, discountPercent = 150.0).total,
            EPSILON,
        )
        assertEquals(
            10.0,
            CartItem(product, unitPriceWithTax = 10.0, discountPercent = -10.0).total,
            EPSILON,
        )
    }

    @Test
    fun packageQuantityUsesBulkMultiplier() {
        val item =
            CartItem(
                product = Product(id = "4", bulkQuantity = 12.0, unitPackage = "CAJA"),
                quantityDecimal = 2.5,
                itemUnitPackage = "EMPAQUE",
            )

        assertEquals(30.0, item.quantityTotal, EPSILON)
        assertEquals("CAJA", item.displayUnitLabel)
    }

    private companion object {
        const val EPSILON = 0.0000001
    }
}
