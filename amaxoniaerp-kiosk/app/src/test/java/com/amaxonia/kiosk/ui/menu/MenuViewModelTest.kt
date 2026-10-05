package com.amaxonia.kiosk.ui.menu

import com.amaxonia.kiosk.core.money.Money
import com.amaxonia.kiosk.core.network.KioskApiClient
import com.amaxonia.kiosk.core.network.KioskHttpClientFactory
import com.amaxonia.kiosk.core.network.KioskTokenStorage
import com.amaxonia.kiosk.domain.cart.OrderGraph
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MenuViewModelTest {
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val catalogJson =
        """
        {
            "categories": [
                {"id": 1, "name": "Hamburguesas", "order": 1},
                {"id": 2, "name": "Bebidas", "order": 2}
            ],
            "items": [
                {
                    "id": 101,
                    "categoryId": 1,
                    "name": "Hamburguesa Clásica",
                    "description": "Carne 100% res",
                    "price": "6.50",
                    "taxRate": "7.00",
                    "imageUrl": null,
                    "soldOut": false,
                    "modifierGroups": [
                        {
                            "id": 1,
                            "name": "Término",
                            "min": 1,
                            "max": 1,
                            "isMandatory": true,
                            "isCombo": false,
                            "options": [
                                {"id": 11, "name": "Bien cocido", "extraPrice": "0.00", "soldOut": false}
                            ]
                        }
                    ]
                },
                {
                    "id": 102,
                    "categoryId": 1,
                    "name": "Papas Fritas",
                    "description": "Crujientes",
                    "price": "2.50",
                    "taxRate": "7.00",
                    "imageUrl": null,
                    "soldOut": false,
                    "modifierGroups": []
                },
                {
                    "id": 103,
                    "categoryId": 2,
                    "name": "Soda",
                    "description": "Refrescante",
                    "price": "1.75",
                    "taxRate": "7.00",
                    "imageUrl": null,
                    "soldOut": true,
                    "modifierGroups": []
                }
            ]
        }
        """.trimIndent()

    @Test
    fun `loadCatalog populates categories and selects first category`() =
        runTest(testDispatcher) {
            val storage = KioskTokenStorage(context = null)
            val engine =
                MockEngine {
                    respond(
                        content = catalogJson,
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
            val client = KioskApiClient(KioskHttpClientFactory.create(storage, engine), storage)
            val orderGraph = OrderGraph()
            val viewModel = MenuViewModel(client, storage, orderGraph)
            viewModel.loadCatalogJob?.join()

            val state = viewModel.uiState.value
            assertFalse(state.isLoading)
            assertNull(state.errorMessage)
            assertEquals(2, state.categories.size)
            assertEquals(1, state.selectedCategoryId)
            assertEquals(2, state.filteredItems.size)
        }

    @Test
    fun `selectCategory filters items to selected category`() =
        runTest(testDispatcher) {
            val storage = KioskTokenStorage(context = null)
            val engine =
                MockEngine {
                    respond(
                        content = catalogJson,
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
            val client = KioskApiClient(KioskHttpClientFactory.create(storage, engine), storage)
            val orderGraph = OrderGraph()
            val viewModel = MenuViewModel(client, storage, orderGraph)
            viewModel.loadCatalogJob?.join()

            viewModel.selectCategory(2)

            val state = viewModel.uiState.value
            assertEquals(2, state.selectedCategoryId)
            assertEquals(1, state.filteredItems.size)
            assertEquals(103, state.filteredItems.first().id)
        }

    @Test
    fun `product with modifiers opens customizer and does not add directly`() =
        runTest(testDispatcher) {
            val storage = KioskTokenStorage(context = null)
            val engine =
                MockEngine {
                    respond(
                        content = catalogJson,
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
            val client = KioskApiClient(KioskHttpClientFactory.create(storage, engine), storage)
            val orderGraph = OrderGraph()
            val viewModel = MenuViewModel(client, storage, orderGraph)
            viewModel.loadCatalogJob?.join()

            val burgerItem = viewModel.uiState.value.items.first { it.id == 101 }
            var openedCustomizerItemId: Int? = null

            viewModel.onProductClicked(burgerItem, onOpenCustomizer = { openedCustomizerItemId = it })

            assertEquals(101, openedCustomizerItemId)
            assertEquals(0, orderGraph.totalItemCount)
            assertEquals(0, viewModel.uiState.value.cartItemCount)
        }

    @Test
    fun `product without modifiers adds directly to cart`() =
        runTest(testDispatcher) {
            val storage = KioskTokenStorage(context = null)
            val engine =
                MockEngine {
                    respond(
                        content = catalogJson,
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
            val client = KioskApiClient(KioskHttpClientFactory.create(storage, engine), storage)
            val orderGraph = OrderGraph()
            val viewModel = MenuViewModel(client, storage, orderGraph)
            viewModel.loadCatalogJob?.join()

            val friesItem = viewModel.uiState.value.items.first { it.id == 102 }
            var openedCustomizer = false

            viewModel.onProductClicked(friesItem, onOpenCustomizer = { openedCustomizer = true })
            testScheduler.advanceUntilIdle()

            assertFalse(openedCustomizer)
            assertEquals(1, orderGraph.totalItemCount)
            assertEquals(1, viewModel.uiState.value.cartItemCount)
            assertEquals(Money.fromString("2.50"), viewModel.uiState.value.cartSubtotal)
        }

    @Test
    fun `sold out product does nothing when clicked`() =
        runTest(testDispatcher) {
            val storage = KioskTokenStorage(context = null)
            val engine =
                MockEngine {
                    respond(
                        content = catalogJson,
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
            val client = KioskApiClient(KioskHttpClientFactory.create(storage, engine), storage)
            val orderGraph = OrderGraph()
            val viewModel = MenuViewModel(client, storage, orderGraph)
            viewModel.loadCatalogJob?.join()

            val soldOutItem = viewModel.uiState.value.items.first { it.id == 103 }
            var customizerCalled = false

            viewModel.onProductClicked(soldOutItem, onOpenCustomizer = { customizerCalled = true })

            assertFalse(customizerCalled)
            assertEquals(0, orderGraph.totalItemCount)
        }
}
