package com.amaxonia.pos.domain.usecase.payment

import com.amaxonia.pos.domain.model.Client
import com.amaxonia.pos.domain.model.ClientBranch
import com.amaxonia.pos.domain.model.SaleFinancialSnapshot
import com.amaxonia.pos.domain.model.TransactionStatus
import com.amaxonia.pos.domain.model.caja.CurrencyConfig
import com.amaxonia.pos.domain.model.money.Money
import com.amaxonia.pos.domain.model.payment.FormaPagoDetalle
import com.amaxonia.pos.domain.model.payment.GatewayApproval
import com.amaxonia.pos.domain.model.sales.SaleItemDto
import com.amaxonia.pos.domain.model.seller.Seller
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExecutePaymentFlowUseCaseTest {
    @Test
    fun `offline flow queues an idempotent invoice and pending transaction`() =
        runTest {
            val fixture = fixture(FixtureOptions(isOnline = false))

            val result = fixture.useCase(input()) {}

            assertTrue(result is PaymentFlowResult.Success)
            assertNotNull(fixture.offlineWriter.written)
            assertEquals("flow-id", fixture.offlineWriter.written?.id)
            assertEquals(TransactionStatus.PENDING, fixture.transactions.saved?.status)
            assertEquals("OFF-1000", fixture.transactions.saved?.invoiceNumber)
        }

    @Test
    fun `online flow sends the characterized request and stores a paid transaction`() =
        runTest {
            val fixture = fixture(FixtureOptions(isOnline = true))
            val events = mutableListOf<PaymentFlowEvent>()

            val result = fixture.useCase(input()) { events += it }

            assertTrue(result is PaymentFlowResult.Success)
            assertEquals("remote-invoice", (result as PaymentFlowResult.Success).payload.transactionId)
            assertEquals(
                10.0,
                fixture.sales.request
                    ?.factura
                    ?.totalTotalFactura ?: -1.0,
                0.0,
            )
            assertEquals(TransactionStatus.PAID, fixture.transactions.saved?.status)
            assertEquals("INV-1", fixture.transactions.saved?.invoiceNumber)
            assertTrue(events.any { it is PaymentFlowEvent.Progress })
        }

    @Test
    fun preloadedFinancialSnapshotRemainsAuthoritativeInRequest() =
        runTest {
            val fixture = fixture(FixtureOptions(isOnline = true))
            val snapshot =
                SaleFinancialSnapshot(
                    subtotalGross = 9.38,
                    itemDiscounts = 0.11,
                    subtotalNet = 9.27,
                    tax = 0.74,
                    total = 10.01,
                )
            val preloadedItem =
                SaleItemDto(
                    idItem = 42,
                    itemAlmacen = 1,
                    itemDescripcion = "Reserved line",
                    itemCantidad = 1.0,
                    itemPrecioSinIva = 9.38,
                    itemPIva = 8.0,
                    itemTotalSinIva = 9.27,
                    itemTotalConIva = 10.01,
                    itemCantidadTotal = 1.0,
                )

            val result =
                fixture.useCase(
                    input(
                        amount = 10.01,
                        saleItemsOverride = listOf(preloadedItem),
                        financialSnapshotOverride = snapshot,
                    ),
                ) {}

            assertTrue(result is PaymentFlowResult.Success)
            val request = fixture.sales.request
            assertEquals(10.01, request?.factura?.totalTotalFactura ?: -1.0, 0.0)
            assertEquals(9.38, request?.factura?.subtotal ?: -1.0, 0.0)
            assertEquals(0.11, request?.factura?.descuentosItemFactura ?: -1.0, 0.0)
            assertEquals(0.74, request?.factura?.ivaTotalFactura ?: -1.0, 0.0)
            assertEquals(9.27, request?.factura?.montoItemsFactura ?: -1.0, 0.0)
            assertEquals(preloadedItem, request?.items?.single())
        }

    @Test
    fun `credit condition sends credito and full CXC as pending balance`() =
        runTest {
            val fixture = fixture(FixtureOptions(isOnline = true))
            val cxc = paymentMethod(2, "CXC", "Cuenta por cobrar")

            val result =
                fixture.useCase(
                    input(
                        paymentDetails = listOf(FormaPagoDetalle(idFormaPago = 2, sigla = "CXC", monto = 10.0)),
                        methods = listOf(cxc),
                        tenderedAmount = Money.ZERO,
                        paymentCondition = PaymentCondition.CREDITO,
                    ),
                ) {}

            assertTrue(result is PaymentFlowResult.Success)
            assertEquals(
                "credito",
                fixture.sales.request
                    ?.factura
                    ?.formaPago,
            )
            assertEquals(
                10.0,
                fixture.sales.request
                    ?.pagoResumen
                    ?.totalizarSaldoPendiente ?: -1.0,
                0.0,
            )
        }

    @Test
    fun `credit condition sends partial payment and CXC remainder`() =
        runTest {
            val fixture = fixture(FixtureOptions(isOnline = true))
            val cash = paymentMethod(1, "CASH", "Efectivo")
            val cxc = paymentMethod(2, "CXC", "Cuenta por cobrar")

            val result =
                fixture.useCase(
                    input(
                        paymentDetails =
                            listOf(
                                FormaPagoDetalle(idFormaPago = 1, sigla = "CASH", monto = 4.0),
                                FormaPagoDetalle(idFormaPago = 2, sigla = "CXC", monto = 6.0),
                            ),
                        methods = listOf(cash, cxc),
                        tenderedAmount = Money.parse("4.00"),
                        paymentCondition = PaymentCondition.CREDITO,
                    ),
                ) {}

            assertTrue(result is PaymentFlowResult.Success)
            assertEquals(
                "credito",
                fixture.sales.request
                    ?.factura
                    ?.formaPago,
            )
            assertEquals(
                6.0,
                fixture.sales.request
                    ?.pagoResumen
                    ?.totalizarSaldoPendiente ?: -1.0,
                0.0,
            )
            assertEquals(
                mapOf("CASH" to 4.0, "CXC" to 6.0),
                fixture.sales.request
                    ?.pagoResumen
                    ?.montosPorTipo,
            )
        }

    @Test
    fun `normal credit card keeps contado and zero CXC balance`() =
        runTest {
            val fixture = fixture(FixtureOptions(isOnline = true))
            val card = paymentMethod(2, "CRED", "Tarjeta de crédito")

            val result =
                fixture.useCase(
                    input(
                        paymentDetails = listOf(FormaPagoDetalle(idFormaPago = 2, sigla = "CRED", monto = 10.0)),
                        methods = listOf(card),
                    ),
                ) {}

            assertTrue(result is PaymentFlowResult.Success)
            assertEquals(
                "contado",
                fixture.sales.request
                    ?.factura
                    ?.formaPago,
            )
            assertEquals(
                0.0,
                fixture.sales.request
                    ?.pagoResumen
                    ?.totalizarSaldoPendiente ?: -1.0,
                0.0,
            )
        }

    @Test
    fun `gateway configuration failure stops before mutating sale state`() =
        runTest {
            val fixture = fixture(FixtureOptions(isOnline = true, gatewayFailure = IllegalArgumentException("invalid gateway")))

            val result = fixture.useCase(input()) {}

            assertEquals("invalid gateway", (result as PaymentFlowResult.Failure).message)
            assertEquals(null, fixture.sales.request)
            assertEquals(null, fixture.transactions.saved)
        }

    @Test
    fun `online backend failure is explicit and does not persist a transaction`() =
        runTest {
            val fixture =
                fixture(
                    FixtureOptions(
                        isOnline = true,
                        processSaleFailure = IllegalStateException("backend unavailable"),
                    ),
                )

            val result = fixture.useCase(input()) {}

            assertEquals("backend unavailable", (result as PaymentFlowResult.Failure).message)
            assertNotNull(fixture.sales.request)
            assertEquals(null, fixture.transactions.saved)
        }

    @Test
    fun `offline queue remains visible when local transaction persistence fails`() =
        runTest {
            val fixture =
                fixture(
                    FixtureOptions(
                        isOnline = false,
                        transactionSaveFailure = IllegalStateException("database full"),
                    ),
                )

            val result = fixture.useCase(input()) {}

            assertEquals("database full", (result as PaymentFlowResult.Failure).message)
            assertNotNull(fixture.offlineWriter.written)
            assertEquals(TransactionStatus.PENDING, fixture.transactions.saved?.status)
        }

    @Test
    fun `Venezuela gateway rejection stops before sending the sale`() =
        runTest {
            val fixture =
                fixture(
                    FixtureOptions(
                        isOnline = true,
                        gatewayLaunch = gatewayLaunch(),
                        gatewayApproval = GatewayApproval(approved = false, message = "declined"),
                    ),
                )
            val events = mutableListOf<PaymentFlowEvent>()

            val result = fixture.useCase(input(countryCode = "VE", gatewayPayment = true)) { events += it }

            assertEquals("declined", (result as PaymentFlowResult.Failure).message)
            assertEquals(null, fixture.sales.request)
            assertTrue(events.any { it is PaymentFlowEvent.LaunchGateway })
        }

    @Test
    fun `Venezuela fiscal confirmation failure emits an explicit recoverable effect`() =
        runTest {
            val fixture =
                fixture(
                    FixtureOptions(
                        isOnline = true,
                        printFeedback = InvoicePrintFeedback("printed", "FISCAL-9", "SERIAL-2"),
                        confirmationFailure = IllegalStateException("confirmation unavailable"),
                    ),
                )
            val events = mutableListOf<PaymentFlowEvent>()

            val result = fixture.useCase(input(countryCode = "VE")) { events += it }

            assertTrue(result is PaymentFlowResult.Success)
            assertEquals("remote-invoice", fixture.sales.confirmedInvoiceId)
            assertEquals("FISCAL-9", fixture.sales.confirmation?.numeroDocumentoFiscal)
            assertEquals("SERIAL-2", fixture.sales.confirmation?.impresoraSerial)
            assertTrue(events.any { it is PaymentFlowEvent.FiscalConfirmationFailed })
        }

    @Test
    fun `mixed payment request preserves cash received change grouping and labels`() =
        runTest {
            val fixture = fixture(FixtureOptions(isOnline = true))
            val cash = paymentMethod(1, "CASH", "Efectivo")
            val credit = paymentMethod(2, "CRED", "Crédito")

            val result =
                fixture.useCase(
                    input(
                        paymentDetails =
                            listOf(
                                FormaPagoDetalle(idFormaPago = 1, sigla = "CASH", monto = 4.0),
                                FormaPagoDetalle(idFormaPago = 2, sigla = "CRED", monto = 6.0),
                            ),
                        methods = listOf(cash, credit),
                        tenderedAmount = Money.parse("5.00"),
                        changeDue = 1.0,
                    ),
                ) {}

            assertTrue(result is PaymentFlowResult.Success)
            assertEquals(
                listOf(5.0, 6.0),
                fixture.sales.request
                    ?.pagos
                    ?.map { it.montoRecibido },
            )
            assertEquals(
                listOf(1.0, 0.0),
                fixture.sales.request
                    ?.pagos
                    ?.map { it.efectivoCambio },
            )
            assertEquals(
                mapOf("CASH" to 4.0, "CRED" to 6.0),
                fixture.sales.request
                    ?.pagoResumen
                    ?.montosPorTipo,
            )
            assertEquals("Efectivo + Crédito", (result as PaymentFlowResult.Success).payload.paymentMethodsLabel)
        }

    @Test
    fun `multi currency caja and selected branch are mapped without changing backend contract`() =
        runTest {
            val branch =
                ClientBranch(
                    sucursalId = 4,
                    clienteCodigo = "C1",
                    nombreSucursal = "Branch",
                    telefonoContacto = "branch-phone",
                    direccion = "branch-address",
                )
            val fixture =
                fixture(
                    FixtureOptions(
                        isOnline = true,
                        client = Client(id = "client", addressDetail = "client-address", phone = "client-phone"),
                        branches = listOf(branch),
                        sellers = listOf(Seller(7, "Seller")),
                        cajaIdSucursal = null,
                        codAlmacen = 0,
                        currency =
                            CurrencyConfig(
                                multiMoneda = "SI",
                                tasa = 36.5,
                                idTasa = 8,
                                monedaBase = 2,
                                abrMonedaBase = "VES",
                                monedaSecundaria = 1,
                                abrMonedaSecundaria = "USD",
                            ),
                    ),
                )

            val result = fixture.useCase(input()) {}
            val request = fixture.sales.request

            assertTrue(result is PaymentFlowResult.Success)
            assertEquals("client", request?.factura?.codCliente)
            assertEquals(7, request?.factura?.codVendedor)
            assertEquals(1, request?.factura?.idSucursal)
            assertEquals(4, request?.factura?.clienteSucursalId)
            assertEquals("branch-address", request?.factura?.facturarADireccion)
            assertEquals("branch-phone", request?.factura?.facturarATelefono)
            assertEquals("CONSUMIDOR FINAL", request?.factura?.facturarA)
            assertEquals("CF", request?.factura?.facturarARuc)
            assertEquals("SI", request?.moneda?.multiMoneda)
            assertEquals(36.5, request?.moneda?.tasa ?: 0.0, 0.0)
            assertEquals(8, request?.moneda?.idTasa)
        }

    @Test
    fun `preparation failures stop before network or persistence`() =
        runTest {
            val multipleBranches =
                listOf(
                    ClientBranch(sucursalId = 1, clienteCodigo = "C1", nombreSucursal = "Principal"),
                    ClientBranch(sucursalId = 2, clienteCodigo = "C1", nombreSucursal = "Secundaria"),
                )
            val scenarios =
                listOf(
                    PreparationScenario(
                        FixtureOptions(isOnline = true, includeProduct = false),
                        "No hay items en el carrito",
                    ),
                    PreparationScenario(
                        FixtureOptions(isOnline = true, includeClient = false),
                        "Debes seleccionar un cliente",
                    ),
                    PreparationScenario(
                        FixtureOptions(isOnline = true, includeCaja = false),
                        "Debes seleccionar una caja",
                    ),
                    PreparationScenario(
                        FixtureOptions(isOnline = true, includeSequence = false),
                        "La caja no esta abierta o no tiene secuencia activa",
                    ),
                    PreparationScenario(
                        FixtureOptions(isOnline = true, productId = "manual-product"),
                        "Hay items manuales/no sincronizados que no se pueden facturar aun",
                    ),
                    PreparationScenario(
                        FixtureOptions(isOnline = true, branches = multipleBranches),
                        "Debes seleccionar la sucursal del cliente",
                    ),
                    PreparationScenario(
                        FixtureOptions(isOnline = true, cajaStatusFailure = IllegalStateException("caja unavailable")),
                        "caja unavailable",
                    ),
                )

            scenarios.forEach { scenario ->
                assertPreparationFailure(scenario.options, scenario.expectedMessage)
            }
        }

    private suspend fun assertPreparationFailure(
        options: FixtureOptions,
        expectedMessage: String,
    ) {
        val fixture = fixture(options)

        val result = fixture.useCase(input()) {}

        assertEquals(expectedMessage, (result as PaymentFlowResult.Failure).message)
        assertEquals(null, fixture.sales.request)
        assertEquals(null, fixture.transactions.saved)
    }

    private data class PreparationScenario(
        val options: FixtureOptions,
        val expectedMessage: String,
    )
}
