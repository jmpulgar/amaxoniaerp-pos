package com.amaxonia.pos.ui.payment

import com.amaxonia.pos.domain.model.Client
import com.amaxonia.pos.domain.model.SaleFinancialSnapshot
import com.amaxonia.pos.domain.model.caja.Caja
import com.amaxonia.pos.domain.model.caja.CurrencyConfig
import com.amaxonia.pos.domain.model.payment.FormaPago
import com.amaxonia.pos.domain.model.payment.GatewayLaunchPayload
import com.amaxonia.pos.domain.model.payment.PaymentSuccessPayload
import com.amaxonia.pos.domain.repository.ActiveCajaReader
import com.amaxonia.pos.domain.repository.FormaPagoRepository
import com.amaxonia.pos.domain.repository.PaymentCountryReader
import com.amaxonia.pos.domain.usecase.payment.BuildPaymentDetailsUseCase
import com.amaxonia.pos.domain.usecase.payment.LoadPaymentContextUseCase
import com.amaxonia.pos.domain.usecase.payment.LoadPaymentCountryUseCase
import com.amaxonia.pos.domain.usecase.payment.PaymentCondition
import com.amaxonia.pos.domain.usecase.payment.PaymentFlowEvent
import com.amaxonia.pos.domain.usecase.payment.PaymentFlowResult
import com.amaxonia.pos.domain.usecase.payment.PaymentOperation
import com.amaxonia.pos.domain.usecase.payment.ValidatePaymentUseCase
import com.amaxonia.pos.test.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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
    fun `without CXC amount the condition is CONTADO`() =
        runTest(mainDispatcherRule.dispatcher) {
            val viewModel =
                viewModel(
                    caja = null,
                    methods =
                        Result.success(
                            listOf(
                                method(1, 1, "CASH"),
                                method(2, 2, "CXC"),
                                method(3, 3, "CRED"),
                            ),
                        ),
                    client = Client(permiteCredito = true),
                )

            runCurrent()

            assertEquals(PaymentCondition.CONTADO, viewModel.state.value.paymentCondition)
        }

    @Test
    fun `CXC amount greater than zero derives CREDITO condition`() =
        runTest(mainDispatcherRule.dispatcher) {
            val viewModel =
                viewModel(
                    caja = null,
                    methods = Result.success(listOf(method(1, 1, "CASH"), method(2, 2, "CXC"))),
                    client = Client(permiteCredito = true),
                )

            runCurrent()
            viewModel.onAction(PaymentUiAction.SetNonCashAmount(2, "10.00"))

            assertEquals(PaymentCondition.CREDITO, viewModel.state.value.paymentCondition)
            assertEquals(mapOf(2 to "10.00"), viewModel.state.value.nonCashAmountsInput)
        }

    @Test
    fun `removing CXC amount reverts the condition to CONTADO`() =
        runTest(mainDispatcherRule.dispatcher) {
            val viewModel =
                viewModel(
                    caja = null,
                    methods = Result.success(listOf(method(1, 1, "CASH"), method(2, 2, "CXC"))),
                    client = Client(permiteCredito = true),
                )

            runCurrent()
            viewModel.onAction(PaymentUiAction.SetNonCashAmount(2, "10.00"))
            assertEquals(PaymentCondition.CREDITO, viewModel.state.value.paymentCondition)

            viewModel.onAction(PaymentUiAction.SetNonCashAmount(2, ""))
            assertEquals(PaymentCondition.CONTADO, viewModel.state.value.paymentCondition)
            assertTrue(
                viewModel.state.value.nonCashAmountsInput
                    .isEmpty(),
            )
        }

    @Test
    fun `credit card does not activate CXC condition`() =
        runTest(mainDispatcherRule.dispatcher) {
            val viewModel =
                viewModel(
                    caja = null,
                    methods = Result.success(listOf(method(1, 1, "CASH"), method(2, 2, "CRED"))),
                    client = Client(permiteCredito = true),
                )

            runCurrent()
            viewModel.onAction(PaymentUiAction.SetNonCashAmount(2, "10.00"))

            assertEquals(PaymentCondition.CONTADO, viewModel.state.value.paymentCondition)
            assertEquals(
                listOf(2),
                viewModel.state.value.formasPagoTarjetaOtro
                    .map { it.idFormaPago },
            )
        }

    @Test
    fun `client without credit permission cannot use CXC`() =
        runTest(mainDispatcherRule.dispatcher) {
            val viewModel =
                viewModel(
                    caja = null,
                    methods = Result.success(listOf(method(1, 1, "CASH"), method(2, 2, "CXC"))),
                    client = Client(permiteCredito = false),
                )

            runCurrent()
            viewModel.onAction(PaymentUiAction.SetNonCashAmount(2, "10.00"))

            assertEquals(PaymentCondition.CONTADO, viewModel.state.value.paymentCondition)
            assertFalse(
                viewModel.state.value.formasPagoTarjetaOtro
                    .any { it.siglas == "CXC" },
            )
            assertTrue(
                viewModel.state.value.nonCashAmountsInput
                    .isEmpty(),
            )
        }

    @Test
    fun `client with credit permission can list CXC and the condition follows the amount`() =
        runTest(mainDispatcherRule.dispatcher) {
            val viewModel =
                viewModel(
                    caja = null,
                    methods = Result.success(listOf(method(1, 1, "CASH"), method(2, 2, "CXC"))),
                    client = Client(permiteCredito = true),
                )

            runCurrent()
            assertTrue(
                viewModel.state.value.formasPagoTarjetaOtro
                    .any { it.siglas == "CXC" },
            )
            assertEquals(PaymentCondition.CONTADO, viewModel.state.value.paymentCondition)

            viewModel.onAction(PaymentUiAction.SetNonCashAmount(2, "5.00"))
            assertEquals(PaymentCondition.CREDITO, viewModel.state.value.paymentCondition)
        }

    @Test
    fun `partial cash plus CXC remainder derives CREDITO condition`() =
        runTest(mainDispatcherRule.dispatcher) {
            val viewModel =
                viewModel(
                    caja = null,
                    methods = Result.success(listOf(method(1, 1, "CASH"), method(2, 2, "CXC"))),
                    client = Client(permiteCredito = true),
                )

            runCurrent()
            viewModel.onAction(PaymentUiAction.SetTotalAmount(100.0))
            viewModel.onAction(PaymentUiAction.KeyPadInput("4"))
            viewModel.onAction(PaymentUiAction.KeyPadInput("0"))
            viewModel.onAction(PaymentUiAction.SetNonCashAmount(2, "60.00"))

            assertEquals(PaymentCondition.CREDITO, viewModel.state.value.paymentCondition)
            assertEquals(
                40.0,
                viewModel.state.value.tenderedAmountMoney
                    .toDouble(),
                0.001,
            )
            assertEquals(
                60.0,
                viewModel.state.value.cxcAssignedMoney
                    .toDouble(),
                0.001,
            )
        }

    @Test
    fun `losing credit permission mid-flow clears CXC amount and reverts condition`() =
        runTest(mainDispatcherRule.dispatcher) {
            val clientFlow = MutableStateFlow<Client?>(Client(permiteCredito = true))
            val viewModel =
                viewModel(
                    caja = null,
                    methods = Result.success(listOf(method(1, 1, "CASH"), method(2, 2, "CXC"))),
                    client = null,
                    clientFlow = clientFlow,
                )

            runCurrent()
            clientFlow.value = Client(permiteCredito = true)
            runCurrent()
            viewModel.onAction(PaymentUiAction.SetNonCashAmount(2, "10.00"))
            assertEquals(PaymentCondition.CREDITO, viewModel.state.value.paymentCondition)

            clientFlow.value = Client(permiteCredito = false)
            runCurrent()

            assertEquals(PaymentCondition.CONTADO, viewModel.state.value.paymentCondition)
            assertTrue(
                viewModel.state.value.nonCashAmountsInput
                    .isEmpty(),
            )
        }

    @Test
    fun `switching to a non-credit client after assigning CXC leaves no residual and blocks credit`() =
        runTest(mainDispatcherRule.dispatcher) {
            val clientFlow = MutableStateFlow<Client?>(Client(permiteCredito = true))
            val viewModel =
                viewModel(
                    caja = null,
                    methods = Result.success(listOf(method(1, 1, "CASH"), method(2, 2, "CXC"))),
                    client = null,
                    clientFlow = clientFlow,
                )

            runCurrent()
            viewModel.onAction(PaymentUiAction.SetTotalAmount(50.0))
            viewModel.onAction(PaymentUiAction.SetNonCashAmount(2, "50.00"))
            assertEquals(PaymentCondition.CREDITO, viewModel.state.value.paymentCondition)
            assertEquals(
                50.0,
                viewModel.state.value.cxcAssignedMoney
                    .toDouble(),
                0.001,
            )

            clientFlow.value = Client(permiteCredito = false)
            runCurrent()

            val stateAfterSwitch = viewModel.state.value
            assertEquals(PaymentCondition.CONTADO, stateAfterSwitch.paymentCondition)
            assertFalse(stateAfterSwitch.canUseCredit)
            assertEquals(0.0, stateAfterSwitch.cxcAssignedMoney.toDouble(), 0.0)
            assertTrue(
                "No debe quedar ninguna entrada CXC residual en nonCashAmountsInput",
                stateAfterSwitch.nonCashAmountsInput.none { (methodId) ->
                    viewModel.state.value.formasPago
                        .firstOrNull { it.idFormaPago == methodId }
                        ?.siglas
                        ?.equals("CXC", ignoreCase = true) == true
                },
            )
            assertFalse(stateAfterSwitch.formasPagoTarjetaOtro.any { it.siglas == "CXC" })
        }

    @Test
    fun `financial snapshot is exposed in state for the breakdown`() =
        runTest(mainDispatcherRule.dispatcher) {
            val snapshot =
                SaleFinancialSnapshot(
                    subtotalGross = 100.0,
                    itemDiscounts = 5.0,
                    subtotalNet = 95.0,
                    tax = 15.2,
                    total = 110.2,
                )
            val viewModel =
                viewModel(
                    caja = null,
                    methods = Result.success(listOf(method(1, 1, "CASH"))),
                    snapshot = snapshot,
                )

            runCurrent()

            val exposed = viewModel.state.value.financialSnapshot
            assertEquals(100.0, exposed?.subtotalGross ?: 0.0, 0.001)
            assertEquals(5.0, exposed?.itemDiscounts ?: 0.0, 0.001)
            assertEquals(95.0, exposed?.subtotalNet ?: 0.0, 0.001)
            assertEquals(15.2, exposed?.tax ?: 0.0, 0.001)
            assertEquals(110.2, exposed?.total ?: 0.0, 0.001)
        }

    @Test
    fun `tax label adapts to country code`() =
        runTest(mainDispatcherRule.dispatcher) {
            val viewModel =
                viewModel(
                    caja = null,
                    methods = Result.success(listOf(method(1, 1, "CASH"))),
                    countryCode = "PA",
                )

            runCurrent()
            assertEquals("ITBMS", viewModel.state.value.taxLabel)
        }

    @Test
    fun `insufficient payment is rejected before executing payment operation`() =
        runTest(mainDispatcherRule.dispatcher) {
            var operationCalls = 0
            val viewModel =
                viewModel(
                    caja = null,
                    methods = Result.success(listOf(method(1, 1))),
                    operation =
                        PaymentOperation { _, _ ->
                            operationCalls += 1
                            error("must not execute")
                        },
                )
            runCurrent()

            viewModel.onAction(PaymentUiAction.SetTotalAmount(10.0))
            viewModel.onAction(PaymentUiAction.ProcessPayment)
            runCurrent()

            assertEquals(0, operationCalls)
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
    fun `cash and multiple other methods are expressed as one payment operation request`() =
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
                    operation =
                        PaymentOperation { request, _ ->
                            capturedAmounts =
                                request.payment.details.payload.detalle
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

    @Test
    fun `success result updates ui state with payload and receipt message`() =
        runTest(mainDispatcherRule.dispatcher) {
            val payload =
                PaymentSuccessPayload(
                    changeDue = 0.0,
                    paymentMethodsLabel = "Efectivo",
                    codFactura = "INV-1",
                    transactionId = "remote-1",
                    receiptPrintMessage = "Impresa",
                )
            val viewModel =
                viewModel(
                    caja = null,
                    methods = Result.success(listOf(method(1, 1))),
                    operation = PaymentOperation { _, _ -> PaymentFlowResult.Success(payload, receiptPrintMessage = "Factura impresa") },
                )
            runCurrent()

            payExactCash(viewModel)
            advanceUntilIdle()

            val state = viewModel.state.value
            assertTrue(state.isSuccess)
            assertFalse(state.isProcessingPayment)
            assertEquals(payload, state.successPayload)
            assertEquals("Factura impresa", state.receiptPrintMessage)
            assertEquals(null, state.paymentError)
            assertEquals(null, state.gatewayStatusMessage)
        }

    @Test
    fun `failure result surfaces the payment error and stops processing`() =
        runTest(mainDispatcherRule.dispatcher) {
            val viewModel =
                viewModel(
                    caja = null,
                    methods = Result.success(listOf(method(1, 1))),
                    operation = PaymentOperation { _, _ -> PaymentFlowResult.Failure("backend unavailable") },
                )
            runCurrent()

            payExactCash(viewModel)
            advanceUntilIdle()

            val state = viewModel.state.value
            assertFalse(state.isSuccess)
            assertFalse(state.isProcessingPayment)
            assertEquals("backend unavailable", state.paymentError)
            assertEquals(null, state.gatewayStatusMessage)
            assertEquals(null, state.successPayload)
        }

    @Test
    fun `duplicate invoice result prompts with correlation id and reason`() =
        runTest(mainDispatcherRule.dispatcher) {
            val viewModel =
                viewModel(
                    caja = null,
                    methods = Result.success(listOf(method(1, 1))),
                    operation =
                        PaymentOperation { _, _ ->
                            PaymentFlowResult.DuplicateInvoice(
                                clientCorrelationId = "flow-id",
                                reason = "Factura duplicada y no se pudo reconciliar con el backend",
                            )
                        },
                )
            runCurrent()

            payExactCash(viewModel)
            advanceUntilIdle()

            val state = viewModel.state.value
            assertFalse(state.isProcessingPayment)
            assertEquals(
                DuplicateInvoicePrompt(
                    clientCorrelationId = "flow-id",
                    reason = "Factura duplicada y no se pudo reconciliar con el backend",
                ),
                state.duplicateInvoice,
            )
            assertEquals(null, state.gatewayStatusMessage)
        }

    @Test
    fun `gateway launch event emits the launch effect`() =
        runTest(mainDispatcherRule.dispatcher) {
            val payload = gatewayPayload()
            val effects = mutableListOf<PaymentUiEffect>()
            val viewModel =
                viewModel(
                    caja = null,
                    methods = Result.success(listOf(method(1, 1))),
                    operation =
                        PaymentOperation { _, onEvent ->
                            onEvent(PaymentFlowEvent.LaunchGateway(payload))
                            PaymentFlowResult.Failure("gateway stop")
                        },
                )
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                viewModel.effects.collect { effects += it }
            }
            runCurrent()

            payExactCash(viewModel)
            advanceUntilIdle()

            assertEquals(listOf(PaymentUiEffect.LaunchGateway(payload)), effects)
        }

    @Test
    fun `fiscal confirmation failure event keeps the successful payment state`() =
        runTest(mainDispatcherRule.dispatcher) {
            val payload =
                PaymentSuccessPayload(
                    changeDue = 0.0,
                    paymentMethodsLabel = "Efectivo",
                    codFactura = "INV-1",
                    transactionId = "remote-1",
                )
            val viewModel =
                viewModel(
                    caja = null,
                    methods = Result.success(listOf(method(1, 1))),
                    operation =
                        PaymentOperation { _, onEvent ->
                            onEvent(PaymentFlowEvent.FiscalConfirmationFailed)
                            PaymentFlowResult.Success(payload, receiptPrintMessage = null)
                        },
                )
            runCurrent()

            payExactCash(viewModel)
            advanceUntilIdle()

            val state = viewModel.state.value
            assertTrue(state.isSuccess)
            assertFalse(state.isProcessingPayment)
            assertEquals(null, state.paymentError)
            assertEquals(payload, state.successPayload)
        }

    private fun viewModel(
        caja: Caja?,
        methods: Result<List<FormaPago>>,
        operation: PaymentOperation = PaymentOperation { _, _ -> error("not used") },
        client: Client? = null,
        clientFlow: MutableStateFlow<Client?>? = null,
        snapshot: SaleFinancialSnapshot? = null,
        countryCode: String = "VE",
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
                override suspend fun currentCountryCode(): String = countryCode
            }
        val resolvedClientFlow: MutableStateFlow<Client?> = clientFlow ?: MutableStateFlow(client)
        return PaymentViewModel(
            loadPaymentContext = LoadPaymentContextUseCase(cajaReader, methodRepository),
            loadPaymentCountry = LoadPaymentCountryUseCase(countryReader),
            validatePayment = ValidatePaymentUseCase(),
            buildPaymentDetails = BuildPaymentDetailsUseCase(),
            paymentOperation = operation,
            selectedClient = resolvedClientFlow,
            cartFinancialSnapshot = MutableStateFlow(snapshot),
        )
    }

    private fun payExactCash(viewModel: PaymentViewModel) {
        viewModel.onAction(PaymentUiAction.SetTotalAmount(10.0))
        listOf("1", "0").forEach { viewModel.onAction(PaymentUiAction.KeyPadInput(it)) }
        viewModel.onAction(PaymentUiAction.ProcessPayment)
    }

    private fun gatewayPayload() =
        GatewayLaunchPayload(
            packageName = "gateway.package",
            activityClassName = "GatewayActivity",
            encryptedCommand = byteArrayOf(1),
            backgroundColor = "background",
            textColor = "text",
            message = "processing",
        )

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
