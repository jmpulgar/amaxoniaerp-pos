package com.amaxonia.kiosk.ui.paymentmethod

import com.amaxonia.kiosk.core.money.Money
import com.amaxonia.kiosk.core.network.KioskCardOption
import com.amaxonia.kiosk.core.network.KioskQuoteResponse
import com.amaxonia.kiosk.domain.cart.OrderGraph
import com.amaxonia.kiosk.domain.checkout.QuoteOrderUseCase
import com.amaxonia.kiosk.domain.payment.PaymentMethod
import com.amaxonia.kiosk.testutil.quoteJson
import com.amaxonia.kiosk.testutil.sampleItem
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PaymentMethodViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val quote: KioskQuoteResponse = Json { ignoreUnknownKeys = true }.decodeFromString(quoteJson(total = "12.50"))
    private val orderGraph = OrderGraph().apply { addLine(sampleItem) }
    private lateinit var quoteOrder: QuoteOrderUseCase

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        quoteOrder = mockk()
        coEvery { quoteOrder(any()) } returns Result.success(quote)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `quotes once and shows the quote total with every method`() =
        runTest(dispatcher) {
            val vm = PaymentMethodViewModel(quoteOrder, orderGraph, listOf(PaymentMethod.CARD, PaymentMethod.YAPPY))
            assertEquals(PaymentMethodStep.Quoting, vm.uiState.value.step)
            advanceUntilIdle()

            assertEquals(PaymentMethodStep.Ready, vm.uiState.value.step)
            assertEquals(Money.fromString("12.50"), vm.uiState.value.total)
            assertNull(vm.uiState.value.autoSelectedMethod)
        }

    @Test
    fun `single method is auto-selected after the quote (screen skipped)`() =
        runTest(dispatcher) {
            val vm = PaymentMethodViewModel(quoteOrder, orderGraph, listOf(PaymentMethod.YAPPY))
            assertNull(vm.uiState.value.autoSelectedMethod)
            advanceUntilIdle()

            assertEquals(PaymentMethod.YAPPY, vm.uiState.value.autoSelectedMethod)
        }

    @Test
    fun `several card methods are listed instead of auto-selecting CARD`() =
        runTest(dispatcher) {
            val cards = listOf(KioskCardOption(49, "VISA", "TDC"), KioskCardOption(50, "MASTERCARD", "TDC"))
            val vm = PaymentMethodViewModel(quoteOrder, orderGraph, listOf(PaymentMethod.CARD), cards)
            advanceUntilIdle()

            assertEquals(cards, vm.uiState.value.cardOptions)
            assertNull(vm.uiState.value.autoSelectedMethod)
        }

    @Test
    fun `a single card method is auto-selected with its option`() =
        runTest(dispatcher) {
            val visa = KioskCardOption(49, "VISA", "TDC")
            val vm = PaymentMethodViewModel(quoteOrder, orderGraph, listOf(PaymentMethod.CARD), listOf(visa))
            advanceUntilIdle()

            assertEquals(PaymentMethod.CARD, vm.uiState.value.autoSelectedMethod)
            assertEquals(visa, vm.uiState.value.autoSelectedCardOption)
        }

    @Test
    fun `card options are dropped when CARD is not available`() =
        runTest(dispatcher) {
            val vm = PaymentMethodViewModel(quoteOrder, orderGraph, listOf(PaymentMethod.YAPPY), listOf(KioskCardOption(49, "VISA")))
            advanceUntilIdle()

            assertEquals(emptyList<KioskCardOption>(), vm.uiState.value.cardOptions)
            assertEquals(PaymentMethod.YAPPY, vm.uiState.value.autoSelectedMethod)
            assertNull(vm.uiState.value.autoSelectedCardOption)
        }

    @Test
    fun `no usable method shows the counter message without quoting`() =
        runTest(dispatcher) {
            val vm = PaymentMethodViewModel(quoteOrder, orderGraph, emptyList())
            advanceUntilIdle()

            assertEquals(PaymentMethodStep.NoMethodsAvailable, vm.uiState.value.step)
            coVerify(exactly = 0) { quoteOrder(any()) }
        }

    @Test
    fun `quote failure can be retried`() =
        runTest(dispatcher) {
            coEvery { quoteOrder(any()) } returns Result.failure(IllegalStateException("Stock insuficiente"))
            val vm = PaymentMethodViewModel(quoteOrder, orderGraph, listOf(PaymentMethod.CARD))
            advanceUntilIdle()
            assertEquals(PaymentMethodStep.QuoteFailed("Stock insuficiente"), vm.uiState.value.step)

            coEvery { quoteOrder(any()) } returns Result.success(quote)
            vm.ensureQuote()
            advanceUntilIdle()
            assertEquals(PaymentMethodStep.Ready, vm.uiState.value.step)
        }
}
