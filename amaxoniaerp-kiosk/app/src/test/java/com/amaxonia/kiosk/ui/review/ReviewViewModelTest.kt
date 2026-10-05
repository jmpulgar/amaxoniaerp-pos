package com.amaxonia.kiosk.ui.review

import com.amaxonia.kiosk.core.money.Money
import com.amaxonia.kiosk.core.network.KioskCurrencyConfig
import com.amaxonia.kiosk.core.network.KioskItemDto
import com.amaxonia.kiosk.domain.cart.OrderGraph
import com.amaxonia.kiosk.domain.cart.SelectedModifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReviewViewModelTest {
    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var orderGraph: OrderGraph

    private val item1 =
        KioskItemDto(
            id = 10,
            categoryId = 1,
            name = "Hamburguesa Clásica",
            description = "Carne y queso",
            price = "4.50",
            taxRate = "7.00",
            imageUrl = null,
            soldOut = false,
            modifierGroups = emptyList(),
        )

    private val item2 =
        KioskItemDto(
            id = 20,
            categoryId = 2,
            name = "Papas Grandes",
            description = "Crujientes",
            price = "2.00",
            taxRate = "7.00",
            imageUrl = null,
            soldOut = false,
            modifierGroups = emptyList(),
        )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        orderGraph = OrderGraph()
        orderGraph.setCurrencyConfig(
            KioskCurrencyConfig(base = "USD", secondary = "Bs", rate = "40.0"),
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initializes empty cart state`() {
        val viewModel = ReviewViewModel(orderGraph)
        val state = viewModel.uiState.value

        assertTrue(state.isEmpty)
        assertEquals(0, state.totalItemCount)
        assertEquals(Money.ZERO, state.subtotal)
        assertEquals("COMER_AQUI", state.diningMode)
        assertEquals("USD", state.currency.base)
    }

    @Test
    fun `initializes with existing orderGraph lines and calculations`() {
        orderGraph.addLine(
            item = item1,
            modifiers = listOf(SelectedModifier(optionId = 1, optionName = "Extra Queso", extraPrice = Money.fromString("0.50"))),
            note = "Sin pepinillo",
            qty = 2,
        )

        val viewModel = ReviewViewModel(orderGraph)
        val state = viewModel.uiState.value

        assertFalse(state.isEmpty)
        assertEquals(2, state.totalItemCount)
        assertEquals(1, state.lines.size)

        val line = state.lines.first()
        assertEquals("Hamburguesa Clásica", line.item.name)
        assertEquals(2, line.quantity)
        assertEquals("Sin pepinillo", line.note)
        assertEquals(Money.fromString("5.00"), line.unitPrice)
        assertEquals(Money.fromString("10.00"), line.lineTotal)
        assertEquals(Money.fromString("10.00"), state.subtotal)
    }

    @Test
    fun `incrementQuantity increases line quantity and subtotal`() {
        orderGraph.addLine(item1, qty = 1)
        val viewModel = ReviewViewModel(orderGraph)

        val lineId = viewModel.uiState.value.lines.first().id
        viewModel.incrementQuantity(lineId)

        val updatedState = viewModel.uiState.value
        assertEquals(2, updatedState.totalItemCount)
        assertEquals(2, updatedState.lines.first().quantity)
        assertEquals(Money.fromString("9.00"), updatedState.subtotal)
    }

    @Test
    fun `decrementQuantity decreases quantity and removes line when zero`() {
        orderGraph.addLine(item1, qty = 2)
        val viewModel = ReviewViewModel(orderGraph)

        val lineId = viewModel.uiState.value.lines.first().id
        viewModel.decrementQuantity(lineId)

        assertEquals(1, viewModel.uiState.value.lines.first().quantity)
        assertEquals(Money.fromString("4.50"), viewModel.uiState.value.subtotal)

        // Decrementing from 1 to 0 removes line
        viewModel.decrementQuantity(lineId)
        assertTrue(viewModel.uiState.value.isEmpty)
        assertEquals(0, viewModel.uiState.value.lines.size)
        assertEquals(Money.ZERO, viewModel.uiState.value.subtotal)
    }

    @Test
    fun `removeLine removes target line leaving other items intact`() {
        orderGraph.addLine(item1, qty = 1)
        orderGraph.addLine(item2, qty = 2)
        val viewModel = ReviewViewModel(orderGraph)

        assertEquals(2, viewModel.uiState.value.lines.size)
        assertEquals(3, viewModel.uiState.value.totalItemCount)

        val line1Id = viewModel.uiState.value.lines.first { it.item.id == item1.id }.id
        viewModel.removeLine(line1Id)

        assertEquals(1, viewModel.uiState.value.lines.size)
        assertEquals(item2.id, viewModel.uiState.value.lines.first().item.id)
        assertEquals(2, viewModel.uiState.value.totalItemCount)
        assertEquals(Money.fromString("4.00"), viewModel.uiState.value.subtotal)
    }

    @Test
    fun `setDiningMode updates dining mode`() {
        val viewModel = ReviewViewModel(orderGraph)
        assertEquals("COMER_AQUI", viewModel.uiState.value.diningMode)

        viewModel.setDiningMode("PARA_LLEVAR")
        assertEquals("PARA_LLEVAR", viewModel.uiState.value.diningMode)
        assertEquals("PARA_LLEVAR", orderGraph.diningMode.value)
    }

    @Test
    fun `clearCart removes all lines and resets state`() {
        orderGraph.addLine(item1, qty = 1)
        orderGraph.addLine(item2, qty = 1)
        val viewModel = ReviewViewModel(orderGraph)

        assertFalse(viewModel.uiState.value.isEmpty)

        viewModel.clearCart()
        assertTrue(viewModel.uiState.value.isEmpty)
        assertEquals(0, viewModel.uiState.value.lines.size)
        assertEquals(Money.ZERO, viewModel.uiState.value.subtotal)
    }
}
