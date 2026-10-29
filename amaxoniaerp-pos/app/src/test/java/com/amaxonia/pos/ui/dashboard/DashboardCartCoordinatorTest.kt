package com.amaxonia.pos.ui.dashboard

import com.amaxonia.pos.domain.model.Promocion
import com.amaxonia.pos.domain.model.ServerCountry
import com.amaxonia.pos.domain.model.options.ProductComposition
import com.amaxonia.pos.domain.model.options.SelectedOption
import com.amaxonia.pos.domain.repository.CartRepository
import com.amaxonia.pos.domain.repository.DashboardSessionReader
import com.amaxonia.pos.domain.repository.ProductLotConfiguration
import com.amaxonia.pos.domain.repository.ProductLotRepository
import com.amaxonia.pos.domain.repository.PromotionRepository
import com.amaxonia.pos.domain.system.AppClock
import com.amaxonia.pos.domain.usecase.cart.RefreshCartProductLotsUseCase
import com.amaxonia.pos.domain.usecase.cart.ResolveClientBranchesUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardCartCoordinatorTest {
    @Test
    fun `manual entry keeps legacy identifier price and clearing behavior`() =
        runTest {
            val cart = CartRepository()
            val state = MutableStateFlow(DashboardState())
            val coordinator = coordinator(cart, DashboardSaleGate { _, _ -> true })

            coordinator.onAction(DashboardSaleUiAction.ManualKey("1"), this, state)
            coordinator.onAction(DashboardSaleUiAction.ManualKey("."), this, state)
            coordinator.onAction(DashboardSaleUiAction.ManualKey("2"), this, state)
            coordinator.onAction(DashboardSaleUiAction.ManualKey("."), this, state)
            coordinator.onAction(DashboardSaleUiAction.ManualSubmit, this, state)
            advanceUntilIdle()

            val item = cart.cartItems.value.single()
            assertEquals("manual_1234", item.product.id)
            assertEquals("Entrada Manual", item.product.description)
            assertEquals(1.2, item.unitPriceWithTax, 0.0)
            assertTrue(item.product.isExempt)
            assertEquals("", state.value.manualEntryValue)
        }

    @Test
    fun `sale gate prevents adding a product`() =
        runTest {
            val cart = CartRepository()
            val state = MutableStateFlow(DashboardState())
            val coordinator = coordinator(cart, DashboardSaleGate { _, _ -> false })

            coordinator.onAction(
                DashboardSaleUiAction.AddProduct(DashboardProduct(id = "blocked", name = "Blocked", price = 10.0)),
                this,
                state,
            )
            advanceUntilIdle()

            assertTrue(cart.cartItems.value.isEmpty())
        }

    @Test
    fun `caracterizacion cartTotal suma items en Double con residuo IEEE-754`() =
        runTest {
            val cart = CartRepository()
            val state = MutableStateFlow(DashboardState())
            val coordinator = coordinator(cart, DashboardSaleGate { _, _ -> true })

            cart.cartItemsState.value =
                listOf(itemWithTotal(id = "a", total = 0.1), itemWithTotal(id = "b", total = 0.2))
            // Unconfined: los colectores infinitos de start() se suscriben de
            // forma eager sin colgar el runTest.
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                coordinator.start(this, state)
            }

            // Pre-TASK-151 (sumOf Double) la salida era 0.30000000000000004.
            // Con fold Money el residuo IEEE-754 desaparece y queda 0.3 exacto
            // — única diferencia permitida, documentada en el commit TASK-151.
            assertEquals(0.3, state.value.cartTotal, 0.0)
        }

    @Test
    fun `adding product with hasOptions opens options sheet instead of cart`() =
        runTest {
            val cart = CartRepository()
            val state = MutableStateFlow(DashboardState())
            val coordinator = coordinator(cart, DashboardSaleGate { _, _ -> true })

            val comboProduct = DashboardProduct(
                id = "combo-1",
                name = "Combo Familiar",
                price = 15.0,
                hasOptions = true,
            )

            coordinator.onAction(
                DashboardSaleUiAction.AddProduct(comboProduct),
                this,
                state,
            )
            advanceUntilIdle()

            assertTrue(cart.cartItems.value.isEmpty())
            assertEquals("combo-1", state.value.productOptionsProduct?.id)
        }

    @Test
    fun `confirming product options adds to cart with composition and dismisses sheet`() =
        runTest {
            val cart = CartRepository()
            val state = MutableStateFlow(DashboardState())
            val coordinator = coordinator(cart, DashboardSaleGate { _, _ -> true })

            val comboProduct = DashboardProduct(
                id = "combo-1",
                name = "Combo Familiar",
                price = 15.0,
                hasOptions = true,
            )

            val composition = ProductComposition(
                idCombo = "c-1",
                opciones = listOf(
                    SelectedOption(
                        idGrupo = "g1",
                        grupo = "Bebida",
                        tipo = "COMBO",
                        idGrupoItem = "opt-coca",
                        nombre = "Coca Cola",
                        precio = 0.0,
                    ),
                ),
            )

            coordinator.onAction(
                DashboardSaleUiAction.ConfirmProductOptions(
                    product = comboProduct,
                    quantity = 2,
                    composition = composition,
                ),
                this,
                state,
            )
            advanceUntilIdle()

            val item = cart.cartItems.value.single()
            assertEquals("combo-1", item.product.id)
            assertEquals(2, item.quantity)
            assertEquals(composition, item.composition)
            assertEquals(null, state.value.productOptionsProduct)
        }

    @Test
    fun `dismissing product options clears sheet state`() =
        runTest {
            val cart = CartRepository()
            val state = MutableStateFlow(DashboardState(productOptionsProduct = DashboardProduct(id = "c", name = "C", price = 10.0)))
            val coordinator = coordinator(cart, DashboardSaleGate { _, _ -> true })

            coordinator.onAction(
                DashboardSaleUiAction.DismissProductOptions,
                this,
                state,
            )

            assertEquals(null, state.value.productOptionsProduct)
        }

    private fun itemWithTotal(
        id: String,
        total: Double,
    ): com.amaxonia.pos.domain.model.CartItem {
        val product =
            com.amaxonia.pos.domain.model.Product(
                id = id,
                description = "P-$id",
                code = id,
                barcode1 = "",
                taxRate = 0.0,
                isExempt = true,
                unitPackage = "UNIDAD",
                bulkQuantity = 1.0,
                portionUnit = null,
                prices =
                    listOf(
                        com.amaxonia.pos.domain.model
                            .PriceLevel(label = "A", pricePlusTax = total),
                    ),
            )
        return com.amaxonia.pos.domain.model
            .CartItem(product = product)
    }

    private fun coordinator(
        cart: CartRepository,
        gate: DashboardSaleGate,
    ) = DashboardCartCoordinator(
        dependencies =
            DashboardCartDependencies(
                promotionRepository = EmptyPromotionRepository,
                cartRepository = cart,
                resolveClientBranches = ResolveClientBranchesUseCase(EmptySessionReader) { emptyList() },
                refreshProductLots = RefreshCartProductLotsUseCase(cart, EmptyLotRepository),
            ),
        saleGate = gate,
        clock = AppClock { Instant.ofEpochMilli(1_234L) },
    )

    private object EmptyPromotionRepository : PromotionRepository {
        override suspend fun syncPromotions(): Result<Unit> = Result.success(Unit)

        override suspend fun getActivePromotionsForProduct(productId: String): Result<List<Promocion>> = Result.success(emptyList())

        override suspend fun getPromotionById(promotionId: String): Result<Promocion> = Result.failure(NoSuchElementException(promotionId))
    }

    private object EmptySessionReader : DashboardSessionReader {
        override suspend fun currentAdminDatabase(): String = ""

        override suspend fun currentCountry(): ServerCountry? = null
    }

    private object EmptyLotRepository : ProductLotRepository {
        override suspend fun getForProduct(productId: String): Result<ProductLotConfiguration> =
            Result.success(ProductLotConfiguration(isConfigured = false, lots = emptyList()))
    }
}
