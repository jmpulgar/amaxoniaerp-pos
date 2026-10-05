package com.amaxonia.kiosk.ui.payment

import com.amaxonia.kiosk.core.money.Money
import com.amaxonia.kiosk.core.network.KioskItemDto
import com.amaxonia.kiosk.core.network.KioskPaymentResponse
import com.amaxonia.kiosk.core.network.KioskQuoteResponse
import com.amaxonia.kiosk.core.network.KioskReceipt
import com.amaxonia.kiosk.domain.cart.OrderGraph
import com.amaxonia.kiosk.domain.checkout.CheckoutOrderUseCase
import com.amaxonia.kiosk.domain.checkout.CheckoutResult
import com.amaxonia.kiosk.domain.payment.DevMockPaymentTerminal
import com.amaxonia.kiosk.domain.payment.PaymentResult
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PaymentViewModelTest {
    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var checkoutUseCase: CheckoutOrderUseCase
    private lateinit var terminal: DevMockPaymentTerminal
    private lateinit var orderGraph: OrderGraph

    private val sampleItem =
        KioskItemDto(
            id = 1,
            categoryId = 1,
            name = "Hamburguesa",
            description = "Sencilla",
            price = "5.00",
            taxRate = "7.00",
            imageUrl = null,
            soldOut = false,
            modifierGroups = emptyList(),
        )

    private val mockQuote =
        KioskQuoteResponse(
            orderId = "ord-1",
            formattedOrderNumber = "K1-001",
            subtotal = "5.00",
            tax = "0.35",
            total = "5.35",
            expiresAt = "2026-10-05T18:00:00Z",
            diningMode = "COMER_AQUI",
            tableTent = null,
            customerId = "CF",
            lines = emptyList(),
        )

    private val mockPaymentResponse =
        KioskPaymentResponse(
            orderNumber = "K1-001",
            status = "FISCAL_SUCCESS",
            dispatch = "RETIRO_MOSTRADOR",
            receipt =
                KioskReceipt(
                    companyName = "Amaxonia",
                    orderNumber = "K1-001",
                    diningMode = "COMER_AQUI",
                    customerName = "Consumidor Final",
                    customerId = "CF",
                    date = "2026-10-05",
                    lines = emptyList(),
                    subtotal = "5.00",
                    tax = "0.35",
                    total = "5.35",
                    paymentBrand = "VISA",
                    paymentLast4 = "4242",
                    paymentAuthCode = "AUT123",
                    paymentReference = "REF123",
                ),
        )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        checkoutUseCase = mockk()
        terminal = DevMockPaymentTerminal(shouldSucceed = true, simulatedDelayMs = 0)
        orderGraph = OrderGraph()
        orderGraph.addLine(sampleItem, qty = 1)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `successful checkout invokes onOrderSuccess callback`() {
        var completedInfo: CompletedOrderInfo? = null
        coEvery { checkoutUseCase.execute(orderGraph, any()) } returns
            CheckoutResult.Success(
                quote = mockQuote,
                payment =
                    PaymentResult.Success(
                        transactionId = "TXN-1",
                        authCode = "AUT1",
                        reference = "REF1",
                        last4 = "4242",
                        brand = "VISA",
                        amount = Money.fromString("5.35"),
                    ),
                paymentResponse = mockPaymentResponse,
            )

        val viewModel =
            PaymentViewModel(
                checkoutUseCase = checkoutUseCase,
                paymentTerminal = terminal,
                orderGraph = orderGraph,
                onOrderSuccess = { completedInfo = it },
            )

        assertNotNull(completedInfo)
        assertEquals("K1-001", completedInfo?.orderNumber)
        assertEquals(Money.fromString("5.35"), completedInfo?.total)
    }

    @Test
    fun `declined payment updates UI state with reason`() {
        coEvery { checkoutUseCase.execute(orderGraph, any()) } returns
            CheckoutResult.PaymentDeclined("FONDOS INSUFICIENTES")

        val viewModel =
            PaymentViewModel(
                checkoutUseCase = checkoutUseCase,
                paymentTerminal = terminal,
                orderGraph = orderGraph,
                onOrderSuccess = {},
            )

        val state = viewModel.uiState.value
        assertTrue(state.step is PaymentStep.Declined)
        assertEquals("FONDOS INSUFICIENTES", (state.step as PaymentStep.Declined).reason)
    }

    @Test
    fun `quote or terminal error updates UI state to Failed`() {
        coEvery { checkoutUseCase.execute(orderGraph, any()) } returns
            CheckoutResult.QuoteFailed("Servidor no disponible")

        val viewModel =
            PaymentViewModel(
                checkoutUseCase = checkoutUseCase,
                paymentTerminal = terminal,
                orderGraph = orderGraph,
                onOrderSuccess = {},
            )

        val state = viewModel.uiState.value
        assertTrue(state.step is PaymentStep.Failed)
        assertEquals("Servidor no disponible", (state.step as PaymentStep.Failed).message)
    }

    @Test
    fun `cancelPayment delegates to paymentTerminal and calls callback`() {
        coEvery { checkoutUseCase.execute(orderGraph, any()) } returns
            CheckoutResult.PaymentCancelled

        val viewModel =
            PaymentViewModel(
                checkoutUseCase = checkoutUseCase,
                paymentTerminal = terminal,
                orderGraph = orderGraph,
                onOrderSuccess = {},
            )

        var cancelledCalled = false
        viewModel.cancelPayment(onCancelled = { cancelledCalled = true })

        assertTrue(cancelledCalled)
    }
}
