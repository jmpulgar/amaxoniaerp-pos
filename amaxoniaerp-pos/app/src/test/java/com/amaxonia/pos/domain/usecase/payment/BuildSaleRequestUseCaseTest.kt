package com.amaxonia.pos.domain.usecase.payment

import com.amaxonia.pos.data.local.AppJson
import com.amaxonia.pos.domain.model.printer.PrinterType
import com.amaxonia.pos.domain.model.sales.SaleCurrencyDto
import com.amaxonia.pos.domain.model.sales.SaleInvoiceDto
import com.amaxonia.pos.domain.model.sales.SaleItemDto
import com.amaxonia.pos.domain.model.sales.SalePaymentDto
import com.amaxonia.pos.domain.model.sales.SalePaymentSummaryDto
import com.amaxonia.pos.domain.model.sales.SaleTaxDto
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class BuildSaleRequestUseCaseTest {
    @Test
    fun `preserves backend field names and nested values`() {
        val request =
            BuildSaleRequestUseCase()(
                BuildSaleRequestInput(
                    invoice = invoice(),
                    items = listOf(item()),
                    taxes = listOf(SaleTaxDto(10.0, 1, 1.6)),
                    paymentSummary = SalePaymentSummaryDto(11.6, 20.0, 8.4, 0.0, mapOf("CASH" to 11.6)),
                    payments = listOf(SalePaymentDto(1, "CASH", 11.6, 20.0, 8.4)),
                    currency = SaleCurrencyDto("NO", 1.0, 0, 1, "USD", 1, "USD", 11.6),
                ),
            )

        val json = AppJson.parseToJsonElement(AppJson.encodeToString(request)).jsonObject
        assertEquals(
            "caja",
            json
                .getValue("factura")
                .jsonObject
                .getValue("idCaja")
                .jsonPrimitive.content,
        )
        assertEquals(
            42,
            json
                .getValue("items")
                .jsonArray
                .single()
                .jsonObject
                .getValue("idItem")
                .jsonPrimitive.int,
        )
        assertEquals(
            11.6,
            json
                .getValue("pagos")
                .jsonArray
                .single()
                .jsonObject
                .getValue("monto")
                .jsonPrimitive.double,
            0.0,
        )
        assertFalse(json.containsKey("idFactura"))
    }

    private fun invoice() =
        SaleInvoiceDto(
            idCliente = "client",
            codCliente = "code",
            codVendedor = 7,
            idShop = 1,
            idSucursal = 1,
            idCaja = "caja",
            codigoCaja = "CJ",
            idCajaSecuencia = "sequence",
            serieSucursal = "A",
            formaPago = "contado",
            codEstatus = 2,
            subtotal = 10.0,
            ivaTotalFactura = 1.6,
            totalTotalFactura = 11.6,
            montoItemsFactura = 10.0,
            totalizarBaseImponible = 10.0,
            totalizarMontoIva = 1.6,
            totalizarTotalGeneral = 11.6,
            usuarioCreacion = "POS",
            facturarA = "CLIENTE",
            facturarARuc = "ID",
            facturarADireccion = "DIR",
            facturarATelefono = "TEL",
        )

    private fun item() =
        SaleItemDto(
            idItem = 42,
            itemAlmacen = 1,
            itemDescripcion = "ITEM",
            itemCantidad = 1.0,
            itemPrecioSinIva = 10.0,
            itemPIva = 16.0,
            itemTotalSinIva = 10.0,
            itemTotalConIva = 11.6,
            itemCantidadTotal = 1.0,
        )

    // ─── FASE 1.1: propagación de la selección HKA20 al DTO ─────────────────

    @Test
    fun `THE_FACTORY_HKA produce useHka20=true en el DTO de venta`() {
        val request =
            BuildSaleRequestUseCase()(
                BuildSaleRequestInput(
                    invoice = invoice(),
                    items = listOf(item()),
                    taxes = emptyList(),
                    paymentSummary = SalePaymentSummaryDto(11.6, 20.0, 8.4, 0.0, mapOf("CASH" to 11.6)),
                    payments = listOf(SalePaymentDto(1, "CASH", 11.6, 20.0, 8.4)),
                    currency = SaleCurrencyDto("NO", 1.0, 0, 1, "USD", 1, "USD", 11.6),
                    printerType = PrinterType.THE_FACTORY_HKA,
                ),
            )

        assertEquals(true, request.useHka20)
    }

    @Test
    fun `cualquier otro printerType produce useHka20=false en el DTO de venta`() {
        PrinterType.entries
            .filter { it != PrinterType.THE_FACTORY_HKA }
            .forEach { printer ->
                val request =
                    BuildSaleRequestUseCase()(
                        BuildSaleRequestInput(
                            invoice = invoice(),
                            items = listOf(item()),
                            taxes = emptyList(),
                            paymentSummary = SalePaymentSummaryDto(11.6, 20.0, 8.4, 0.0, mapOf("CASH" to 11.6)),
                            payments = listOf(SalePaymentDto(1, "CASH", 11.6, 20.0, 8.4)),
                            currency = SaleCurrencyDto("NO", 1.0, 0, 1, "USD", 1, "USD", 11.6),
                            printerType = printer,
                        ),
                    )

                assertEquals(
                    "PrinterType=$printer no debe setear useHka20",
                    false,
                    request.useHka20,
                )
            }
    }

    @Test
    fun `sin indicar printerType el default es useHka20=false`() {
        val request =
            BuildSaleRequestUseCase()(
                BuildSaleRequestInput(
                    invoice = invoice(),
                    items = listOf(item()),
                    taxes = emptyList(),
                    paymentSummary = SalePaymentSummaryDto(11.6, 20.0, 8.4, 0.0, mapOf("CASH" to 11.6)),
                    payments = listOf(SalePaymentDto(1, "CASH", 11.6, 20.0, 8.4)),
                    currency = SaleCurrencyDto("NO", 1.0, 0, 1, "USD", 1, "USD", 11.6),
                ),
            )

        assertEquals(false, request.useHka20)
    }
}
