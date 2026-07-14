package com.amaxonia.pos.domain.usecase.cart

import com.amaxonia.pos.domain.model.Product
import com.amaxonia.pos.domain.repository.CartRepository
import com.amaxonia.pos.domain.repository.ProductLotAvailability
import com.amaxonia.pos.domain.repository.ProductLotConfiguration
import com.amaxonia.pos.domain.repository.ProductLotRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class RefreshCartProductLotsUseCaseTest {
    @Test
    fun `discover policy marks configuration and assigns FEFO without exceeding quantity`() =
        runTest {
            val cart =
                CartRepository().apply {
                    addToCart(Product(id = "product"), quantity = 3)
                }
            val lots =
                listOf(
                    ProductLotAvailability(1, "OLD", "2026-01-01", 2, 10),
                    ProductLotAvailability(2, "NEW", "2027-01-01", 5, 11),
                )
            val useCase = RefreshCartProductLotsUseCase(cart, FixedProductLotRepository(lots))

            useCase("product", LotRefreshPolicy.DISCOVER_CONFIGURATION)

            val item = cart.cartItems.value.single()
            assertEquals(true, item.hasLotConfig)
            assertEquals(listOf(2, 1), item.lotAssignments.map { it.cantidad })
            assertEquals(listOf("OLD", "NEW"), item.lotAssignments.map { it.codigoLote })
        }

    @Test
    fun `known policy avoids network when item has no lot configuration`() =
        runTest {
            val cart = CartRepository().apply { addToCart(Product(id = "product")) }
            val repository = FixedProductLotRepository(emptyList())
            val useCase = RefreshCartProductLotsUseCase(cart, repository)

            useCase("product", LotRefreshPolicy.KNOWN_CONFIGURATION_ONLY)

            assertFalse(repository.wasCalled)
            assertEquals(
                emptyList<Any>(),
                cart.cartItems.value
                    .single()
                    .lotAssignments,
            )
        }

    private class FixedProductLotRepository(
        private val lots: List<ProductLotAvailability>,
    ) : ProductLotRepository {
        var wasCalled = false

        override suspend fun getForProduct(productId: String): Result<ProductLotConfiguration> {
            wasCalled = true
            return Result.success(ProductLotConfiguration(isConfigured = true, lots = lots))
        }
    }
}
