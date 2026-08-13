package com.amaxonia.pos.data.repository

import com.amaxonia.pos.domain.model.DraftInvoice
import com.amaxonia.pos.domain.repository.CartRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JsonDraftInvoiceRestorerTest {
    @Test
    fun draftRestoreKeepsSnapshotUntilCartChanges() {
        val cart = CartRepository()
        val draft =
            DraftInvoice(
                id = "draft-1",
                itemsJson =
                    "[{\"productId\":\"42\",\"description\":\"Product\",\"quantity\":1," +
                        "\"unitPriceWithTax\":10.0,\"itemUnitPackage\":\"UNIDAD\",\"unitPackage\":\"UNIDAD\"," +
                        "\"bulkQuantity\":1.0,\"portionUnit\":\"\",\"discountPercent\":0.0," +
                        "\"codVendedor\":1,\"taxRate\":0.0,\"isExempt\":true,\"code\":\"P42\",\"barcode1\":\"\"}]",
                total = 10.01,
                itemCount = 1,
                createdAt = 1L,
                subtotalGross = 9.38,
                itemDiscounts = 0.11,
                subtotalNet = 9.27,
                tax = 0.74,
            )

        val result = JsonDraftInvoiceRestorer(cart).restore(draft)

        assertTrue(result.isSuccess)
        assertEquals(10.01, cart.financialSnapshot.value?.total ?: -1.0, 0.0)
        assertEquals(9.38, cart.financialSnapshot.value?.subtotalGross ?: -1.0, 0.0)

        cart.updateItemPrice("42", 11.0)

        assertNull(cart.financialSnapshot.value)
    }
}
