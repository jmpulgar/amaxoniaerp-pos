package com.amaxonia.kiosk.ui.payment

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.amaxonia.kiosk.core.money.Money
import com.amaxonia.kiosk.core.network.KioskPaymentResponse
import com.amaxonia.kiosk.core.network.KioskQuoteResponse
import com.amaxonia.kiosk.domain.cart.OrderGraph
import com.amaxonia.kiosk.domain.checkout.CheckoutOrderUseCase
import com.amaxonia.kiosk.domain.checkout.CheckoutResult
import com.amaxonia.kiosk.domain.checkout.CheckoutSession
import com.amaxonia.kiosk.domain.checkout.QuoteOrderUseCase
import com.amaxonia.kiosk.testutil.FakePaymentTerminal
import com.amaxonia.kiosk.testutil.approvedCard
import com.amaxonia.kiosk.testutil.payResponseJson
import com.amaxonia.kiosk.testutil.quoteJson
import com.amaxonia.kiosk.testutil.sampleItem
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PaymentViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val json = Json { ignoreUnknownKeys = true }
    private val quote: KioskQuoteResponse = json.decodeFromString(quoteJson(total = "9.10"))
    private val paymentResponse: KioskPaymentResponse = json.decodeFromString(payResponseJson)

    private lateinit var checkoutUseCase: CheckoutOrderUseCase
    private lateinit var quoteOrder: QuoteOrderUseCase
    private lateinit var terminal: FakePaymentTerminal
    private lateinit var orderGraph: OrderGraph

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        checkoutUseCase = mockk()
        quoteOrder = mockk()
        terminal = FakePaymentTerminal()
        orderGraph = OrderGraph().apply { addLine(sampleItem) }
        coEvery { quoteOrder(any()) } returns Result.success(quote)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.session() = CheckoutSession(terminal, mockk(relaxed = true), backgroundScope)

    private fun TestScope.viewModel(session: CheckoutSession = session()) =
        PaymentViewModel(checkoutUseCase, quoteOrder, terminal, session, orderGraph, "RETIRO_MOSTRADOR")

    @Test
    fun `amount shown is the server quote total, not the pre-tax cart subtotal`() =
        runTest(dispatcher) {
            coEvery { checkoutUseCase.payWithCard(quote, any()) } coAnswers {
                delay(1_000_000)
                CheckoutResult.PaymentCancelled
            }

            val vm = viewModel()
            runCurrent()

            assertEquals(Money.fromString("8.50"), orderGraph.subtotal)
            assertEquals(Money.fromString("9.10"), vm.uiState.value.totalAmount)
        }

    @Test
    fun `countdown starts only after the quote returns`() =
        runTest(dispatcher) {
            val gate = CompletableDeferred<Unit>()
            coEvery { quoteOrder(any()) } coAnswers {
                gate.await()
                Result.success(quote)
            }
            coEvery { checkoutUseCase.payWithCard(quote, any()) } coAnswers {
                delay(1_000_000)
                CheckoutResult.PaymentCancelled
            }

            val vm = viewModel()
            advanceTimeBy(10_000)
            assertEquals(PaymentStep.Quoting, vm.uiState.value.step)
            assertTrue(vm.uiState.value.isPaymentInFlight)

            gate.complete(Unit)
            runCurrent()
            assertEquals(PaymentStep.AwaitingPayment(CARD_PAYMENT_TIMEOUT_SECONDS), vm.uiState.value.step)

            advanceTimeBy(5_001)
            assertEquals(PaymentStep.AwaitingPayment(CARD_PAYMENT_TIMEOUT_SECONDS - 5), vm.uiState.value.step)
        }

    @Test
    fun `approved card completes the order with the server order number`() =
        runTest(dispatcher) {
            coEvery { checkoutUseCase.payWithCard(quote, any()) } returns
                CheckoutResult.Success(quote, approvedCard(), paymentResponse)

            val vm = viewModel()
            advanceUntilIdle()

            val step = vm.uiState.value.step as PaymentStep.Completed
            assertEquals("K1-042", step.info.orderNumber)
            assertEquals(Money.fromString("9.10"), step.info.total)
        }

    @Test
    fun `paid but unsynced order still completes with the quote number and receipt lines`() =
        runTest(dispatcher) {
            coEvery { checkoutUseCase.payWithCard(quote, any()) } returns
                CheckoutResult.PaidPendingSync(quote, approvedCard(), "timeout")

            val vm = viewModel()
            advanceUntilIdle()

            val info = (vm.uiState.value.step as PaymentStep.Completed).info
            assertEquals("K1-042", info.orderNumber)
            assertEquals(STATUS_PAID_PENDING_INVOICE, info.paymentResponse.status)
            assertEquals(1, info.paymentResponse.receipt.lines.size)
        }

    @Test
    fun `declined payment updates UI state with reason`() =
        runTest(dispatcher) {
            coEvery { checkoutUseCase.payWithCard(quote, any()) } returns CheckoutResult.PaymentDeclined("FONDOS INSUFICIENTES")

            val vm = viewModel()
            advanceUntilIdle()

            assertEquals(PaymentStep.Declined("FONDOS INSUFICIENTES"), vm.uiState.value.step)
            assertTrue(!vm.uiState.value.isPaymentInFlight)
        }

    @Test
    fun `quote failure shows QUOTE_FAILED and never touches the terminal`() =
        runTest(dispatcher) {
            coEvery { quoteOrder(any()) } returns Result.failure(IllegalStateException("Servidor no disponible"))

            val vm = viewModel()
            advanceUntilIdle()

            assertEquals(PaymentStep.Failed(PaymentFailure.QUOTE_FAILED, "Servidor no disponible"), vm.uiState.value.step)
            assertEquals(0, terminal.processCalls)
        }

    @Test
    fun `timeout cancels the terminal and shows TIMEOUT`() =
        runTest(dispatcher) {
            coEvery { checkoutUseCase.payWithCard(quote, any()) } coAnswers {
                delay(1_000_000)
                CheckoutResult.PaymentCancelled
            }

            val vm = viewModel()
            advanceTimeBy(CARD_PAYMENT_TIMEOUT_SECONDS * 1000L + 1)

            assertEquals(PaymentStep.Failed(PaymentFailure.TIMEOUT), vm.uiState.value.step)
            assertEquals(1, terminal.cancelCalls)
        }

    @Test
    fun `cancelPayment cancels the terminal and calls back`() =
        runTest(dispatcher) {
            coEvery { checkoutUseCase.payWithCard(quote, any()) } coAnswers {
                delay(1_000_000)
                CheckoutResult.PaymentCancelled
            }
            val vm = viewModel()
            runCurrent()

            var cancelled = false
            vm.cancelPayment { cancelled = true }
            runCurrent()

            assertTrue(cancelled)
            assertEquals(1, terminal.cancelCalls)
        }

    @Test
    fun `clearing the ViewModel mid-payment cancels the terminal (no leaked payment)`() =
        runTest(dispatcher) {
            coEvery { checkoutUseCase.payWithCard(quote, any()) } coAnswers {
                delay(1_000_000)
                CheckoutResult.PaymentCancelled
            }
            val session = session()
            val store = ViewModelStore()
            val factory = viewModelFactory { initializer { viewModel(session) } }
            ViewModelProvider.create(store, factory)[PaymentViewModel::class]
            runCurrent()

            store.clear()
            runCurrent()

            assertEquals(1, terminal.cancelCalls)
            advanceTimeBy(CARD_PAYMENT_TIMEOUT_SECONDS * 1000L + 1)
            assertEquals("countdown job must not survive onCleared", 1, terminal.cancelCalls)
        }
}
