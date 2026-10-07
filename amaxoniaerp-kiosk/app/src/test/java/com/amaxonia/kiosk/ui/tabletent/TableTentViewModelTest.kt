package com.amaxonia.kiosk.ui.tabletent

import com.amaxonia.kiosk.domain.cart.OrderGraph
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TableTentViewModelTest {
    private lateinit var orderGraph: OrderGraph
    private lateinit var viewModel: TableTentViewModel

    @Before
    fun setUp() {
        orderGraph = OrderGraph()
        viewModel = TableTentViewModel(orderGraph)
    }

    @Test
    fun `initializes empty and invalid`() {
        assertEquals("", viewModel.uiState.value.tentNumber)
        assertFalse(viewModel.uiState.value.isValid)
    }

    @Test
    fun `onDigit appends digits up to 3 and ignores leading zero`() {
        // Leading zero ignored
        viewModel.onDigit('0')
        assertEquals("", viewModel.uiState.value.tentNumber)

        // Valid digits
        viewModel.onDigit('4')
        viewModel.onDigit('2')
        assertEquals("42", viewModel.uiState.value.tentNumber)
        assertTrue(viewModel.uiState.value.isValid)

        // 3rd digit
        viewModel.onDigit('5')
        assertEquals("425", viewModel.uiState.value.tentNumber)

        // 4th digit ignored (max 3)
        viewModel.onDigit('9')
        assertEquals("425", viewModel.uiState.value.tentNumber)
    }

    @Test
    fun `onBackspace removes last digit and onClear empties string`() {
        viewModel.onDigit('1')
        viewModel.onDigit('2')
        viewModel.onDigit('3')
        assertEquals("123", viewModel.uiState.value.tentNumber)

        viewModel.onBackspace()
        assertEquals("12", viewModel.uiState.value.tentNumber)

        viewModel.onClear()
        assertEquals("", viewModel.uiState.value.tentNumber)
        assertFalse(viewModel.uiState.value.isValid)
    }

    @Test
    fun `confirm sets table tent in orderGraph`() {
        viewModel.onDigit('7')
        viewModel.onDigit('8')

        var confirmed = false
        viewModel.confirm(onSuccess = { confirmed = true })

        assertTrue(confirmed)
        assertEquals("78", orderGraph.tableTent.value)
    }

    @Test
    fun `skip sets null table tent in orderGraph`() {
        orderGraph.setTableTent("99")
        var skipped = false
        viewModel.skip(onSuccess = { skipped = true })

        assertTrue(skipped)
        assertNull(orderGraph.tableTent.value)
    }
}
