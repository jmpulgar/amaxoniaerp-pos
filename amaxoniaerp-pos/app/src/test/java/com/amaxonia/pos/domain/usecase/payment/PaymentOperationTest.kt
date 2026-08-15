package com.amaxonia.pos.domain.usecase.payment

import com.amaxonia.pos.domain.model.SaleFinancialSnapshot
import com.amaxonia.pos.domain.model.mesas.CuentaMesaResponse
import com.amaxonia.pos.domain.model.money.Money
import com.amaxonia.pos.domain.model.payment.FormaPago
import com.amaxonia.pos.domain.model.payment.FormaPagoDetalle
import com.amaxonia.pos.domain.model.payment.FormapagoDetallePayload
import com.amaxonia.pos.domain.model.printer.PrinterType
import com.amaxonia.pos.domain.repository.TableAccountPayment
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class PaymentOperationTest {
    @Test
    fun `canonical request maps to internal implementation without changing observable behavior`() =
        runTest {
            val method =
                FormaPago(
                    idFormaPago = 1,
                    siglas = "CASH",
                    descripcion = "Efectivo",
                    activo = 1,
                    pos = 1,
                    grupo = 1,
                    orden = 1,
                    tipoMoneda = "USD",
                )
            val details = cashDetails()
            val snapshot =
                SaleFinancialSnapshot(
                    subtotalGross = 10.0,
                    itemDiscounts = 0.0,
                    subtotalNet = 10.0,
                    tax = 0.0,
                    total = 10.0,
                )
            val request =
                PaymentOperationRequest(
                    payment = cashIntent(details),
                    source = PaymentSource.CurrentCart(snapshot),
                    context =
                        PaymentExecutionContext(
                            countryCode = "VE",
                            availableMethods = listOf(method),
                            exchangeRate = 2.0,
                            secondaryCurrency = "Bs.",
                            isMultiCurrency = true,
                        ),
                )
            var captured: ExecutePaymentFlowInput? = null
            val expectedResult = PaymentFlowResult.Failure("characterized failure")
            val events = mutableListOf<PaymentFlowEvent>()
            val operation =
                DefaultPaymentOperation(
                    executeFlow = { input, onEvent ->
                        captured = input
                        onEvent(PaymentFlowEvent.Progress("mapped"))
                        expectedResult
                    },
                    printerTypeProvider = { PrinterType.THE_FACTORY_HKA },
                )

            val result = operation.execute(request) { events += it }
            val input = checkNotNull(captured)

            assertSame(expectedResult, result)
            assertEquals(listOf(PaymentFlowEvent.Progress("mapped")), events)
            assertEquals("VE", input.countryCode)
            assertSame(details, input.paymentDetails)
            assertEquals(Money.parse("10.00"), input.totalAmount)
            assertEquals(Money.parse("12.50"), input.tenderedAmount)
            assertEquals(2.5, input.changeDue, 0.0)
            assertEquals(20.0, input.totalAmountBs, 0.0)
            assertEquals(5.0, input.changeDueBs, 0.0)
            assertEquals(2.0, input.exchangeRate, 0.0)
            assertEquals("Bs.", input.secondaryCurrency)
            assertEquals(true, input.isMultiCurrency)
            assertEquals(listOf(method), input.availableMethods)
            assertSame(snapshot, input.financialSnapshotOverride)
            assertEquals(PrinterType.THE_FACTORY_HKA, input.printerType)
            assertEquals(PaymentCondition.CONTADO, input.paymentCondition)
        }

    @Test
    fun `table account source keeps correlation snapshot and sale context behind the seam`() =
        runTest {
            val tablePayment =
                TableAccountPayment(
                    areaId = 4,
                    mesaId = 5,
                    sesionId = 6,
                    cuenta =
                        CuentaMesaResponse(
                            id = 7,
                            subtotal = 10.0,
                            total = 10.0,
                        ),
                )
            val request =
                PaymentOperationRequest(
                    payment = cashIntent(cashDetails()),
                    source = PaymentSource.TableAccount(tablePayment),
                    context =
                        PaymentExecutionContext(
                            countryCode = "VE",
                            availableMethods = emptyList(),
                            exchangeRate = 1.0,
                            secondaryCurrency = "Bs.",
                            isMultiCurrency = false,
                        ),
                )
            var captured: ExecutePaymentFlowInput? = null
            val operation =
                DefaultPaymentOperation(
                    executeFlow = { input, _ ->
                        captured = input
                        PaymentFlowResult.Failure("stop")
                    },
                    printerTypeProvider = { PrinterType.NONE },
                )

            operation.execute(request) { }
            val input = checkNotNull(captured)

            assertEquals("mesa-6-cuenta-7", input.correlationCarryOver)
            assertEquals("mesa-6-cuenta-7", input.preferredCorrelationId)
            assertSame(tablePayment.financialSnapshot, input.financialSnapshotOverride)
            assertEquals(tablePayment.saleItems, input.saleItemsOverride)
            assertEquals(tablePayment.saleContext, input.cuentaMesa)
        }

    private fun cashDetails(): PaymentDetails =
        PaymentDetails(
            payload =
                FormapagoDetallePayload(
                    totalizarMontoEfectivo = 10.0,
                    totalizarMontoCredito = 0.0,
                    totalizarMontoOtros = 0.0,
                    detalle = listOf(FormaPagoDetalle(idFormaPago = 1, sigla = "CASH", monto = 10.0)),
                ),
            transactionMethods = emptyList(),
        )

    private fun cashIntent(details: PaymentDetails): PaymentIntent =
        PaymentIntent(
            details = details,
            totalAmount = Money.parse("10.00"),
            tenderedAmount = Money.parse("12.50"),
            changeDue = Money.parse("2.50"),
            condition = PaymentCondition.CONTADO,
        )
}
