package com.amaxonia.pos.domain.usecase.payment

import com.amaxonia.pos.domain.model.money.Money
import com.amaxonia.pos.domain.model.payment.EXTERNAL_GATEWAY_MARKER
import com.amaxonia.pos.domain.model.payment.FormaPago
import org.junit.Assert.assertEquals
import org.junit.Test

class BuildPaymentDetailsUseCaseTest {
    private val useCase = BuildPaymentDetailsUseCase()

    @Test
    fun `cash preserves the legacy payload and fiscal mapping`() {
        val cash = paymentMethod(id = 1, sigla = "CASH", description = "Efectivo", fiscalCode = "101")

        val result =
            useCase(
                BuildPaymentDetailsInput(
                    isCash = true,
                    totalAmount = Money.parse("12.34"),
                    cashMethods = listOf(cash),
                    nonCashMethods = emptyList(),
                    allMethods = listOf(cash),
                    nonCashAmountsInput = emptyMap(),
                ),
            )

        assertEquals(12.34, result.payload.totalizarMontoEfectivo, 0.0)
        assertEquals(0.0, result.payload.totalizarMontoCredito, 0.0)
        assertEquals(0.0, result.payload.totalizarMontoOtros, 0.0)
        assertEquals("101", result.transactionMethods.single().fiscalCode)
    }

    @Test
    fun `non cash splits credit and other amounts and marks RapidPay`() {
        val credit = paymentMethod(id = 2, sigla = "CRED", description = "Crédito", fiscalCode = "103")
        val gateway = paymentMethod(id = 3, sigla = "PV", description = "PUNTO DE VENTA")

        val result =
            useCase(
                BuildPaymentDetailsInput(
                    isCash = false,
                    totalAmount = Money.parse("10.00"),
                    cashMethods = emptyList(),
                    nonCashMethods = listOf(credit, gateway),
                    allMethods = listOf(credit, gateway),
                    nonCashAmountsInput = mapOf(2 to "4.25", 3 to "5.75"),
                ),
            )

        assertEquals(4.25, result.payload.totalizarMontoCredito, 0.0)
        assertEquals(5.75, result.payload.totalizarMontoOtros, 0.0)
        assertEquals("102", result.transactionMethods.last().fiscalCode)
        assertEquals(EXTERNAL_GATEWAY_MARKER, result.transactionMethods.last().gatewayCommandPrefix)
    }

    @Test
    fun `fiscal aliases preserve every legacy payment mapping`() {
        val mappings =
            listOf(
                Triple("Punto de venta", null, "102"),
                Triple("Tarjeta debito", null, "102"),
                Triple("Tarjeta credito", null, "103"),
                Triple("Cash USD", null, "101"),
                Triple("Transferencia bancaria", null, "104"),
                // Characterizes the current accent-sensitive legacy mapping.
                Triple("Depósito", null, "199"),
                Triple("Cheque", null, "104"),
                Triple("Zelle", null, "104"),
                Triple("Pago movil", null, "104"),
                Triple("Yappy", null, "104"),
                Triple("Nequi", null, "104"),
                Triple("Solutech", null, "104"),
                Triple("Sunmi", null, "104"),
                Triple("Retencion", null, "104"),
                Triple("Puntos", null, "104"),
                Triple("Anticipo", null, "104"),
                Triple("Unmapped method", null, "199"),
                Triple("Explicit mapping", " 104 ", "104"),
                Triple("Invalid explicit mapping", "999", "199"),
            )

        mappings.forEachIndexed { index, (description, explicitCode, expected) ->
            val method = paymentMethod(index + 1, "M$index", description, explicitCode)
            val result =
                useCase(
                    BuildPaymentDetailsInput(
                        isCash = false,
                        totalAmount = Money.parse("1.00"),
                        cashMethods = emptyList(),
                        nonCashMethods = listOf(method),
                        allMethods = listOf(method),
                        nonCashAmountsInput = mapOf(method.idFormaPago to "1.00"),
                    ),
                )

            assertEquals(description, expected, result.transactionMethods.single().fiscalCode)
        }
    }

    @Test
    fun `missing cash method and invalid non cash amounts remain empty`() {
        val method = paymentMethod(7, "CXC", "Crédito")
        val cashResult =
            useCase(
                BuildPaymentDetailsInput(
                    isCash = true,
                    totalAmount = Money.parse("10.00"),
                    cashMethods = emptyList(),
                    nonCashMethods = emptyList(),
                    allMethods = emptyList(),
                    nonCashAmountsInput = emptyMap(),
                ),
            )
        val invalidResult =
            useCase(
                BuildPaymentDetailsInput(
                    isCash = false,
                    totalAmount = Money.parse("10.00"),
                    cashMethods = emptyList(),
                    nonCashMethods = listOf(method),
                    allMethods = listOf(method),
                    nonCashAmountsInput = mapOf(method.idFormaPago to "not-a-number"),
                ),
            )

        assertEquals(emptyList<Any>(), cashResult.payload.detalle)
        assertEquals(emptyList<Any>(), invalidResult.payload.detalle)
    }

    @Test
    fun `detail without matching catalog method is excluded only from printer methods`() {
        val detailOnly = paymentMethod(8, "CXC", "Cuenta por cobrar")

        val result =
            useCase(
                BuildPaymentDetailsInput(
                    isCash = false,
                    totalAmount = Money.parse("3.00"),
                    cashMethods = emptyList(),
                    nonCashMethods = listOf(detailOnly),
                    allMethods = emptyList(),
                    nonCashAmountsInput = mapOf(8 to "3.00"),
                ),
            )

        assertEquals(3.0, result.payload.totalizarMontoCredito, 0.0)
        assertEquals(emptyList<Any>(), result.transactionMethods)
    }

    private fun paymentMethod(
        id: Int,
        sigla: String,
        description: String,
        fiscalCode: String? = null,
    ): FormaPago =
        FormaPago(
            idFormaPago = id,
            siglas = sigla,
            descripcion = description,
            formaPagoFact = fiscalCode,
            activo = 1,
            pos = 1,
            grupo = 1,
            orden = id,
            tipoMoneda = "USD",
        )
}
