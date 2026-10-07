package com.amaxonia.kiosk.ui.customer

import com.amaxonia.kiosk.domain.cart.OrderGraph
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CustomerIdViewModelTest {
    private lateinit var orderGraph: OrderGraph
    private lateinit var viewModel: CustomerIdViewModel

    @Before
    fun setUp() {
        orderGraph = OrderGraph()
        viewModel = CustomerIdViewModel(orderGraph)
    }

    @Test
    fun `initializes with CF default state`() {
        val state = viewModel.uiState.value
        assertFalse(state.isCustomBilling)
        assertEquals("CEDULA", state.docType)
        assertEquals("", state.docNumber)
        assertEquals("", state.name)
        assertTrue(state.isValid)
    }

    @Test
    fun `selectConsumidorFinal sets CF customer and invokes success`() {
        var successCalled = false
        viewModel.selectConsumidorFinal(onSuccess = { successCalled = true })

        assertTrue(successCalled)
        assertEquals("CF", orderGraph.customerId.value)
        assertEquals("Consumidor Final", orderGraph.customerName.value)
    }

    @Test
    fun `custom billing validation blocks submission when empty`() {
        viewModel.toggleCustomBilling(true)
        assertFalse(viewModel.uiState.value.isValid)

        var successCalled = false
        viewModel.submitCustomCustomer(onSuccess = { successCalled = true })

        assertFalse(successCalled)
        assertNotNull(viewModel.uiState.value.errorMessage)
    }

    @Test
    fun `submitCustomCustomer formats RUC with DV and updates orderGraph`() {
        viewModel.toggleCustomBilling(true)
        viewModel.onDocTypeChanged("RUC")
        viewModel.onDocNumberChanged("155688941-2-2018")
        viewModel.onDvChanged("42")
        viewModel.onNameChanged("Empresa Panameña S.A.")

        assertTrue(viewModel.uiState.value.isValid)

        var successCalled = false
        viewModel.submitCustomCustomer(onSuccess = { successCalled = true })

        assertTrue(successCalled)
        assertNull(viewModel.uiState.value.errorMessage)
        assertEquals("155688941-2-2018-42", orderGraph.customerId.value)
        assertEquals("Empresa Panameña S.A.", orderGraph.customerName.value)
    }

    @Test
    fun `submitCustomCustomer without DV uses document number directly`() {
        viewModel.toggleCustomBilling(true)
        viewModel.onDocTypeChanged("CEDULA")
        viewModel.onDocNumberChanged("8-765-4321")
        viewModel.onNameChanged("Carlos Gonzalez")

        var successCalled = false
        viewModel.submitCustomCustomer(onSuccess = { successCalled = true })

        assertTrue(successCalled)
        assertEquals("8-765-4321", orderGraph.customerId.value)
        assertEquals("Carlos Gonzalez", orderGraph.customerName.value)
    }

    @Test
    fun `limits input field lengths`() {
        viewModel.onDocNumberChanged("1".repeat(50))
        assertEquals(20, viewModel.uiState.value.docNumber.length)

        viewModel.onDvChanged("9".repeat(10))
        assertEquals(4, viewModel.uiState.value.dv.length)

        viewModel.onNameChanged("A".repeat(120))
        assertEquals(80, viewModel.uiState.value.name.length)
    }
}
