package com.amaxonia.pos.ui.payment

import com.amaxonia.pos.domain.model.caja.Caja
import com.amaxonia.pos.domain.model.caja.CurrencyConfig
import com.amaxonia.pos.domain.model.payment.FormaPago
import com.amaxonia.pos.domain.repository.ActiveCajaReader
import com.amaxonia.pos.domain.repository.FormaPagoRepository
import com.amaxonia.pos.domain.repository.PaymentCountryReader
import com.amaxonia.pos.domain.usecase.payment.BuildPaymentDetailsUseCase
import com.amaxonia.pos.domain.usecase.payment.LoadPaymentContextUseCase
import com.amaxonia.pos.domain.usecase.payment.LoadPaymentCountryUseCase
import com.amaxonia.pos.domain.usecase.payment.PaymentFlowExecutor
import com.amaxonia.pos.domain.usecase.payment.PaymentFlowResult
import com.amaxonia.pos.domain.usecase.payment.ValidatePaymentUseCase
import com.amaxonia.pos.test.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PaymentViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `initialization exposes ordered methods and legacy currency configuration`() =
        runTest(mainDispatcherRule.dispatcher) {
            val viewModel =
                viewModel(
                    caja = caja(currency("SI", 36.5)),
                    methods = Result.success(listOf(method(2, 20), method(1, 10))),
                )

            advanceUntilIdle()

            assertEquals(
                listOf(1, 2),
                viewModel.state.value.formasPago
                    .map { it.idFormaPago },
            )
            assertEquals(36.5, viewModel.state.value.tasa, 0.0)
            assertEquals("Bs.", viewModel.state.value.abrMonedaSecundaria)
            assertTrue(viewModel.state.value.isMultiCurrency)
            assertFalse(viewModel.state.value.isLoadingFormasPago)
        }

    @Test
    fun `insufficient payment is rejected before executing payment flow`() =
        runTest(mainDispatcherRule.dispatcher) {
            var flowCalls = 0
            val viewModel =
                viewModel(
                    caja = null,
                    methods = Result.success(listOf(method(1, 1))),
                    executor =
                        PaymentFlowExecutor { _, _ ->
                            flowCalls += 1
                            error("must not execute")
                        },
                )
            runCurrent()

            viewModel.onAction(PaymentUiAction.SetTotalAmount(10.0))
            viewModel.onAction(PaymentUiAction.ProcessPayment)
            runCurrent()

            assertEquals(0, flowCalls)
            assertTrue(viewModel.state.value.showInsufficientReminder)
            assertFalse(viewModel.state.value.isProcessingPayment)
        }

    @Test
    fun `cash keypad accepts decimal amounts`() =
        runTest(mainDispatcherRule.dispatcher) {
            val viewModel = viewModel(caja = null, methods = Result.success(listOf(method(1, 1))))
            runCurrent()

            viewModel.onAction(PaymentUiAction.KeyPadInput("."))
            viewModel.onAction(PaymentUiAction.KeyPadInput("2"))
            viewModel.onAction(PaymentUiAction.KeyPadInput("5"))

            assertEquals("0.25", viewModel.state.value.tenderedAmountInput)
            assertEquals(
                0.25,
                viewModel.state.value.tenderedAmountMoney
                    .toDouble(),
                0.0,
            )
        }

    @Test
    fun `cash and multiple other methods are processed as one payment`() =
        runTest(mainDispatcherRule.dispatcher) {
            var capturedAmounts = emptyList<Double>()
            val methods =
                listOf(
                    method(1, 1, "CASH"),
                    method(2, 2, "TDD"),
                    method(3, 3, "TR"),
                )
            val viewModel =
                viewModel(
                    caja = null,
                    methods = Result.success(methods),
                    executor =
                        PaymentFlowExecutor { input, _ ->
                            capturedAmounts =
                                input.paymentDetails.payload.detalle
                                    .map { it.monto }
                            PaymentFlowResult.Failure("test stop")
                        },
                )
            runCurrent()

            viewModel.onAction(PaymentUiAction.SetTotalAmount(10.0))
            listOf("2", ".", "2", "5").forEach { viewModel.onAction(PaymentUiAction.KeyPadInput(it)) }
            viewModel.onAction(PaymentUiAction.SetNonCashAmount(2, "3.25"))
            viewModel.onAction(PaymentUiAction.SetNonCashAmount(3, "4.50"))
            viewModel.onAction(PaymentUiAction.ProcessPayment)
            advanceUntilIdle()

            assertEquals(listOf(2.25, 3.25, 4.5), capturedAmounts)
        }

    private fun viewModel(
        caja: Caja?,
        methods: Result<List<FormaPago>>,
        executor: PaymentFlowExecutor = PaymentFlowExecutor { _, _ -> error("not used") },
    ): PaymentViewModel {
        val cajaReader =
            object : ActiveCajaReader {
                override val activeCaja = MutableStateFlow(caja)
            }
        val methodRepository =
            object : FormaPagoRepository {
                override suspend fun getFormasPago(cajaId: String?): Result<List<FormaPago>> = methods
            }
        val countryReader =
            object : PaymentCountryReader {
                override suspend fun currentCountryCode(): String = "VE"
            }
        return PaymentViewModel(
            loadPaymentContext = LoadPaymentContextUseCase(cajaReader, methodRepository),
            loadPaymentCountry = LoadPaymentCountryUseCase(countryReader),
            validatePayment = ValidatePaymentUseCase(),
            buildPaymentDetails = BuildPaymentDetailsUseCase(),
            executePaymentFlow = executor,
        )
    }

    private fun method(
        id: Int,
        order: Int,
        siglas: String = "CASH",
    ) = FormaPago(id, siglas = siglas, activo = 1, pos = 1, grupo = 1, orden = order, tipoMoneda = "BASE")

    private fun caja(currency: CurrencyConfig?) =
        Caja(
            idCaja = "caja-1",
            codCaja = "1",
            descripcion = "Caja",
            estatus = 1,
            idSucursal = 1,
            currency = currency,
            serieCaja = "A",
        )

    private fun currency(
        multiCurrency: String,
        rate: Double,
    ) = CurrencyConfig(multiCurrency, rate, 1, 1, "USD", 2, "Bs.")
}
