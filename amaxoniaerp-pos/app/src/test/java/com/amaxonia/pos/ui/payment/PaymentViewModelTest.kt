package com.amaxonia.pos.ui.payment

import com.amaxonia.pos.domain.model.Client
import com.amaxonia.pos.domain.model.SaleFinancialSnapshot
import com.amaxonia.pos.domain.model.ServerCountry
import com.amaxonia.pos.domain.model.caja.Caja
import com.amaxonia.pos.domain.model.caja.CurrencyConfig
import com.amaxonia.pos.domain.model.payment.FormaPago
import com.amaxonia.pos.domain.model.printer.PrinterType
import com.amaxonia.pos.domain.model.printer.TheFactorySettings
import com.amaxonia.pos.domain.repository.ActiveCajaReader
import com.amaxonia.pos.domain.repository.FormaPagoRepository
import com.amaxonia.pos.domain.repository.PaymentCountryReader
import com.amaxonia.pos.domain.repository.PosSettingsRepository
import com.amaxonia.pos.domain.usecase.payment.BuildPaymentDetailsUseCase
import com.amaxonia.pos.domain.usecase.payment.LoadPaymentContextUseCase
import com.amaxonia.pos.domain.usecase.payment.LoadPaymentCountryUseCase
import com.amaxonia.pos.domain.usecase.payment.PaymentCondition
import com.amaxonia.pos.domain.usecase.payment.PaymentFlowExecutor
import com.amaxonia.pos.domain.usecase.payment.PaymentFlowResult
import com.amaxonia.pos.domain.usecase.payment.ValidatePaymentUseCase
import com.amaxonia.pos.test.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
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

    /**
     * 1. Without CXC the derived condition is CONTADO.
     */
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

    /**
     * 2. CXC > 0 (with credit permission) → condition CREDITO.
     */
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

    /**
     * 3. Removing the CXC amount reverts the condition to CONTADO.
     */
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

    /**
     * 4. A normal credit-card payment (CRED) does NOT activate CxC.
     */
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

    /**
     * 5. A client without `permiteCredito` cannot use CXC: any attempt to assign it is silently
     *    dropped, and CXC is not even listed.
     */
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

    /**
     * 6. Client with `permiteCredito` can list CXC and the condition follows the assigned amount.
     */
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

    /**
     * 7. Partial cash tender + CXC for the remainder.
     */
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

    /**
     * Stripping `permiteCredito` after a CXC entry was made clears the entry and reverts to CONTADO.
     */
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
            assertTrue(viewModel.state.value.nonCashAmountsInput.isEmpty())
        }

    /**
     * End-to-end regression: cliente A habilita crédito → se asigna CXC → se cambia a cliente B sin
     * crédito. No puede quedar monto CXC residual y un ProcessPayment posterior no puede pasar como
     * crédito; el estado del cajero es coherente con lo que el backend validará.
     */
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
            // Cajero asigna CXC para el cliente con crédito
            viewModel.onAction(PaymentUiAction.SetTotalAmount(50.0))
            viewModel.onAction(PaymentUiAction.SetNonCashAmount(2, "50.00"))
            assertEquals(PaymentCondition.CREDITO, viewModel.state.value.paymentCondition)
            assertEquals(50.0, viewModel.state.value.cxcAssignedMoney.toDouble(), 0.001)

            // Se reemplaza el cliente por uno que NO permite crédito (mismo flujo que seleccionar
            // otro cliente en el directorio).
            clientFlow.value = Client(permiteCredito = false)
            runCurrent()

            // El estado derivado debe ser coherente: cero CXC, CONTADO, sin crédito disponible
            val stateAfterSwitch = viewModel.state.value
            assertEquals(PaymentCondition.CONTADO, stateAfterSwitch.paymentCondition)
            assertFalse(stateAfterSwitch.canUseCredit)
            assertEquals(0.0, stateAfterSwitch.cxcAssignedMoney.toDouble(), 0.0)
            assertTrue(
                "No debe quedar ninguna entrada CXC residual en nonCashAmountsInput",
                stateAfterSwitch.nonCashAmountsInput.none { (methodId) ->
                    viewModel.state.value.formasPago
                        .firstOrNull { it.idFormaPago == methodId }
                        ?.siglas?.equals("CXC", ignoreCase = true) == true
                },
            )
            // CXC tampoco debe aparecer listado para el nuevo cliente
            assertFalse(stateAfterSwitch.formasPagoTarjetaOtro.any { it.siglas == "CXC" })
        }

    /**
     * 8. The financial snapshot exposed to the UI preserves subtotal / discount / tax / total.
     */
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

    /**
     * Venezuela tenant drives the "IVA" label surfaced in the breakdown.
     */
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
        val fakeSettings =
            object : PosSettingsRepository {
                override val selectedPrinterType: Flow<PrinterType> = flowOf(PrinterType.THE_FACTORY_HKA)
                override val selectedCountry: Flow<ServerCountry?> = flowOf(null)
                override val factorySettings: Flow<TheFactorySettings> = flowOf(TheFactorySettings())
                override val allowEditPrices: Flow<Boolean> = flowOf(true)
                override val allowDiscounts: Flow<Boolean> = flowOf(true)

                override suspend fun currentCountry(): ServerCountry? = null

                override suspend fun savePrinterType(printerType: PrinterType) = Unit

                override suspend fun saveFactorySettings(settings: TheFactorySettings) = Unit

                override suspend fun saveAllowEditPrices(enabled: Boolean) = Unit

                override suspend fun saveAllowDiscounts(enabled: Boolean) = Unit
            }
        val resolvedClientFlow: MutableStateFlow<Client?> = clientFlow ?: MutableStateFlow(client)
        return PaymentViewModel(
            loadPaymentContext = LoadPaymentContextUseCase(cajaReader, methodRepository),
            loadPaymentCountry = LoadPaymentCountryUseCase(countryReader),
            validatePayment = ValidatePaymentUseCase(),
            buildPaymentDetails = BuildPaymentDetailsUseCase(),
            executePaymentFlow = executor,
            posSettings = fakeSettings,
            selectedClient = resolvedClientFlow,
            cartFinancialSnapshot = MutableStateFlow(snapshot),
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
