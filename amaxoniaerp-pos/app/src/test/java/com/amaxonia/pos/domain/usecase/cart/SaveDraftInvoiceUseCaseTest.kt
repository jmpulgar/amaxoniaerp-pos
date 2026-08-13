package com.amaxonia.pos.domain.usecase.cart

import com.amaxonia.pos.domain.model.CartItem
import com.amaxonia.pos.domain.model.Client
import com.amaxonia.pos.domain.model.DraftInvoice
import com.amaxonia.pos.domain.model.Product
import com.amaxonia.pos.domain.model.financialSnapshot
import com.amaxonia.pos.domain.model.seller.Seller
import com.amaxonia.pos.domain.repository.DraftInvoiceRepository
import com.amaxonia.pos.domain.system.AppClock
import com.amaxonia.pos.domain.system.IdGenerator
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class SaveDraftInvoiceUseCaseTest {
    @Test
    fun `persists characterized draft with deterministic id clock and json`() =
        runTest {
            val repository = CapturingDraftRepository()
            val useCase =
                SaveDraftInvoiceUseCase(
                    repository = repository,
                    idGenerator = IdGenerator { "draft-id" },
                    clock = AppClock { Instant.ofEpochMilli(1_234L) },
                )
            val item =
                CartItem(
                    product =
                        Product(
                            id = "42",
                            code = "P1",
                            description = "A \"quoted\" product",
                            barcode1 = "B1",
                            taxRate = 7.0,
                            unitPackage = "BOX",
                            bulkQuantity = 2.0,
                            portionUnit = "EA",
                        ),
                    quantity = 2,
                    codVendedor = 7,
                    unitPriceWithTax = 3.5,
                    itemUnitPackage = "UNIDAD",
                    discountPercent = 5.0,
                )

            val result =
                useCase(
                    SaveDraftInvoiceInput(
                        items = listOf(item),
                        client = Client(id = "client", firstName = "Ada", lastName = "Lovelace"),
                        seller = Seller(id = 7, nombre = "Seller"),
                        total = 6.65,
                    ),
                )

            assertEquals("draft-id", result.id)
            assertEquals(1_234L, result.createdAt)
            assertEquals(2, result.itemCount)
            assertEquals(result, repository.saved)
            assertEquals(EXPECTED_ITEMS_JSON, result.itemsJson)
            assertEquals(6.54, result.subtotalGross, 0.0)
            assertEquals(0.33, result.itemDiscounts, 0.0)
            assertEquals(6.21, result.subtotalNet, 0.0)
            assertEquals(0.44, result.tax, 0.0)
            assertEquals(6.65, result.financialSnapshot.total, 0.0)
        }

    private class CapturingDraftRepository : DraftInvoiceRepository {
        var saved: DraftInvoice? = null

        override suspend fun all(): List<DraftInvoice> = emptyList()

        override suspend fun save(draft: DraftInvoice) {
            saved = draft
        }

        override suspend fun delete(id: String) = Unit
    }

    private companion object {
        const val EXPECTED_ITEMS_JSON =
            "[{\"productId\":\"42\",\"description\":\"A \\\"quoted\\\" product\",\"quantity\":2," +
                "\"unitPriceWithTax\":3.5,\"itemUnitPackage\":\"UNIDAD\",\"unitPackage\":\"BOX\"," +
                "\"bulkQuantity\":2.0,\"portionUnit\":\"EA\",\"discountPercent\":5.0," +
                "\"codVendedor\":7,\"taxRate\":7.0,\"isExempt\":false,\"code\":\"P1\",\"barcode1\":\"B1\"}]"
    }
}
