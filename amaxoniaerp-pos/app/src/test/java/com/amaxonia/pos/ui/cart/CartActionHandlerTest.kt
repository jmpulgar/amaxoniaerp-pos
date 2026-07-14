package com.amaxonia.pos.ui.cart

import app.cash.turbine.test
import com.amaxonia.pos.domain.model.Client
import com.amaxonia.pos.domain.model.ClientBranch
import com.amaxonia.pos.domain.model.DraftInvoice
import com.amaxonia.pos.domain.model.PriceLevel
import com.amaxonia.pos.domain.model.Product
import com.amaxonia.pos.domain.repository.CartRepository
import com.amaxonia.pos.domain.repository.DraftInvoiceRepository
import com.amaxonia.pos.domain.repository.ProductLotConfiguration
import com.amaxonia.pos.domain.repository.ProductLotRepository
import com.amaxonia.pos.domain.system.AppClock
import com.amaxonia.pos.domain.system.IdGenerator
import com.amaxonia.pos.domain.usecase.cart.RefreshCartProductLotsUseCase
import com.amaxonia.pos.domain.usecase.cart.SaveDraftInvoiceUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class CartActionHandlerTest {
    @Test
    fun `price editing remains guarded by current setting`() =
        runTest {
            val cart = CartRepository().apply { addToCart(product()) }
            val state = MutableStateFlow(CartState(allowEditPrices = false))
            val handler = handler(cart)
            val effects = MutableSharedFlow<CartUiEffect>(extraBufferCapacity = 1)

            handler.onAction(CartUiAction.UpdateItemPrice("P-1", 20.0), this, state, effects)
            assertEquals(
                10.0,
                cart.cartItems.value
                    .single()
                    .unitPriceWithTax,
                0.0,
            )

            state.value = state.value.copy(allowEditPrices = true)
            handler.onAction(CartUiAction.UpdateItemPrice("P-1", 20.0), this, state, effects)
            assertEquals(
                20.0,
                cart.cartItems.value
                    .single()
                    .unitPriceWithTax,
                0.0,
            )
        }

    @Test
    fun `checkout blocks missing Panama branch then emits exact total when selected`() =
        runTest {
            val cart = CartRepository()
            val branch = ClientBranch(7, "C-1", "Principal")
            val state =
                MutableStateFlow(
                    CartState(
                        total = 12.34,
                        selectedClient = Client(id = "C-1"),
                        isPanama = true,
                        clientSucursales = listOf(branch),
                    ),
                )
            val effects = MutableSharedFlow<CartUiEffect>(extraBufferCapacity = 1)

            effects.asSharedFlow().test {
                handler(cart).onAction(CartUiAction.Checkout, this@runTest, state, effects)
                expectNoEvents()
                assertEquals("Selecciona la sucursal del cliente antes de cobrar.", state.value.cartActionError)

                state.value = state.value.copy(selectedClientSucursal = branch)
                handler(cart).onAction(CartUiAction.Checkout, this@runTest, state, effects)
                assertEquals(CartUiEffect.Checkout(12.34), awaitItem())
                expectNoEvents()
            }
        }

    private fun handler(cart: CartRepository) =
        CartActionHandler(
            cart,
            RefreshCartProductLotsUseCase(cart, EmptyLotRepository),
            SaveDraftInvoiceUseCase(EmptyDraftRepository, IdGenerator { "draft" }, AppClock { Instant.EPOCH }),
        )

    private fun product() =
        Product(
            id = "P-1",
            description = "Product",
            prices = listOf(PriceLevel(label = "A", pricePlusTax = 10.0)),
        )

    private object EmptyLotRepository : ProductLotRepository {
        override suspend fun getForProduct(productId: String): Result<ProductLotConfiguration> =
            Result.success(ProductLotConfiguration(false, emptyList()))
    }

    private object EmptyDraftRepository : DraftInvoiceRepository {
        override suspend fun all(): List<DraftInvoice> = emptyList()

        override suspend fun save(draft: DraftInvoice) = Unit

        override suspend fun delete(id: String) = Unit
    }
}
