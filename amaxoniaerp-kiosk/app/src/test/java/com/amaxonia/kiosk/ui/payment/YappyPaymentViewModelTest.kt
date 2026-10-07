package com.amaxonia.kiosk.ui.payment

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.amaxonia.kiosk.core.money.Money
import com.amaxonia.kiosk.core.network.KioskPaymentResponse
import com.amaxonia.kiosk.core.network.KioskQuoteResponse
import com.amaxonia.kiosk.domain.cart.OrderGraph
import com.amaxonia.kiosk.domain.checkout.CheckoutResult
import com.amaxonia.kiosk.domain.checkout.CheckoutSession
import com.amaxonia.kiosk.domain.checkout.QuoteOrderUseCase
import com.amaxonia.kiosk.domain.checkout.YappyCharge
import com.amaxonia.kiosk.domain.checkout.YappyChargeResult
import com.amaxonia.kiosk.domain.checkout.YappyCheckoutUseCase
import com.amaxonia.kiosk.domain.checkout.YappyFailure
import com.amaxonia.kiosk.domain.checkout.YappyOutcome
import com.amaxonia.kiosk.domain.checkout.YappyStatus
import com.amaxonia.kiosk.testutil.FakePaymentTerminal
import com.amaxonia.kiosk.testutil.approvedCard
import com.amaxonia.kiosk.testutil.payResponseJson
import com.amaxonia.kiosk.testutil.quoteJson
import com.amaxonia.kiosk.testutil.sampleItem
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class YappyPaymentViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val json = Json { ignoreUnknownKeys = true }
    private val quote: KioskQuoteResponse = json.decodeFromString(quoteJson())
    private val paymentResponse: KioskPaymentResponse = json.decodeFromString(payResponseJson)
    private val charge = YappyCharge(quote.orderId, "YP-1", "qr-hash", Money.fromString("9.10"), 180)

    private lateinit var yappy: YappyCheckoutUseCase
    private lateinit var quoteOrder: QuoteOrderUseCase
    private val orderGraph = OrderGraph().apply { addLine(sampleItem) }

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        yappy = mockk()
        quoteOrder = mockk()
        coEvery { quoteOrder(any()) } returns Result.success(quote)
        coEvery { yappy.createCharge(quote) } returns YappyChargeResult.Created(charge)
        coEvery { yappy.cancelOrSettle(quote, charge) } returns null
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.session() = CheckoutSession(FakePaymentTerminal(), yappy, backgroundScope)

    private fun TestScope.viewModel(
        session: CheckoutSession = session(),
        canChangeMethod: Boolean = true,
    ) = YappyPaymentViewModel(yappy, quoteOrder, session, orderGraph, "RETIRO_MOSTRADOR", canChangeMethod)

    private fun waitForever() = coEvery { yappy.awaitPayment(quote, charge, any()) } coAnswers { awaitCancellation() }

    @Test
    fun `shows the QR with amount and a running countdown while waiting`() =
        runTest(dispatcher) {
            waitForever()

            val vm = viewModel()
            runCurrent()
            assertEquals(YappyStep.AwaitingScan("qr-hash", 180, 180), vm.uiState.value.step)
            assertEquals(Money.fromString("9.10"), vm.uiState.value.totalAmount)

            advanceTimeBy(10_001)
            assertEquals(170, (vm.uiState.value.step as YappyStep.AwaitingScan).secondsRemaining)
            assertTrue(vm.uiState.value.isPaymentInFlight)
        }

    @Test
    fun `confirmed payment goes to success with the order number`() =
        runTest(dispatcher) {
            coEvery { yappy.awaitPayment(quote, charge, any()) } returns
                YappyOutcome.Paid(CheckoutResult.Success(quote, approvedCard(), paymentResponse))

            val vm = viewModel()
            advanceUntilIdle()

            assertEquals("K1-042", (vm.uiState.value.step as YappyStep.Success).info.orderNumber)
        }

    @Test
    fun `declined and expired outcomes show their errors and release the idle timer`() =
        runTest(dispatcher) {
            coEvery { yappy.awaitPayment(quote, charge, any()) } returns YappyOutcome.Rejected(YappyStatus.DECLINED)
            val declined = viewModel()
            advanceUntilIdle()
            assertEquals(YappyErrorKind.DECLINED, (declined.uiState.value.step as YappyStep.Error).kind)
            assertFalse(declined.uiState.value.isPaymentInFlight)

            coEvery { yappy.awaitPayment(quote, charge, any()) } returns YappyOutcome.Expired
            val expired = viewModel()
            advanceUntilIdle()
            assertEquals(YappyStep.Error(YappyErrorKind.EXPIRED), expired.uiState.value.step)
        }

    @Test
    fun `expired order invalidates the session quote so a new code re-quotes`() =
        runTest(dispatcher) {
            coEvery { yappy.createCharge(quote) } returns YappyChargeResult.Failed(YappyFailure.ORDER_EXPIRED, "expired")
            val session = mockk<CheckoutSession>(relaxed = true)

            val vm = viewModel(session = session)
            advanceUntilIdle()

            assertEquals(YappyErrorKind.ORDER_EXPIRED, (vm.uiState.value.step as YappyStep.Error).kind)
            verify { session.invalidateQuote() }
        }

    @Test
    fun `cancel deletes the pending charge before leaving`() =
        runTest(dispatcher) {
            waitForever()
            val vm = viewModel()
            runCurrent()

            var left = false
            vm.leave { left = true }
            advanceUntilIdle()

            assertTrue(left)
            coVerify(exactly = 1) { yappy.cancelOrSettle(quote, charge) }
        }

    @Test
    fun `cancel after the customer already paid shows success instead of leaving`() =
        runTest(dispatcher) {
            waitForever()
            coEvery { yappy.cancelOrSettle(quote, charge) } returns CheckoutResult.Success(quote, approvedCard(), paymentResponse)
            val vm = viewModel()
            runCurrent()

            var left = false
            vm.leave { left = true }
            advanceUntilIdle()

            assertFalse(left)
            assertTrue(vm.uiState.value.step is YappyStep.Success)
        }

    @Test
    fun `new code cancels the previous charge and creates another`() =
        runTest(dispatcher) {
            waitForever()
            val vm = viewModel()
            runCurrent()

            vm.generateCode()
            runCurrent()

            coVerify(exactly = 1) { yappy.cancelOrSettle(quote, charge) }
            coVerify(exactly = 2) { yappy.createCharge(quote) }
        }

    @Test
    fun `clearing the ViewModel while waiting deletes the charge (idle reset)`() =
        runTest(dispatcher) {
            waitForever()
            val session = session()
            val store = ViewModelStore()
            ViewModelProvider.create(store, viewModelFactory { initializer { viewModel(session) } })[YappyPaymentViewModel::class]
            runCurrent()

            store.clear()
            runCurrent()

            coVerify(exactly = 1) { yappy.cancelOrSettle(quote, charge) }
        }
}
