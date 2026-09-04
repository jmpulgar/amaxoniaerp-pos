package com.amaxonia.pos.data.printer

import com.amaxonia.pos.data.local.CompanyDetailsSnapshot
import com.amaxonia.pos.data.printer.panama.PanamaInvoiceTicketFormatter
import com.amaxonia.pos.data.printer.venezuela.VenezuelaInvoiceTicketFormatter
import com.amaxonia.pos.domain.model.Transaction
import com.amaxonia.pos.domain.model.TransactionFiscalItem
import com.amaxonia.pos.domain.model.TransactionPaymentMethod
import com.amaxonia.pos.domain.model.TransactionStatus
import com.amaxonia.pos.domain.model.caja.Caja
import com.amaxonia.pos.domain.model.printer.TicketElement
import com.amaxonia.pos.domain.model.sales.ProcessSaleRequestDto
import com.amaxonia.pos.domain.model.sales.SaleCurrencyDto
import com.amaxonia.pos.domain.model.sales.SaleInvoiceDto
import com.amaxonia.pos.domain.model.sales.SaleItemDto
import com.amaxonia.pos.domain.model.sales.SalePaymentDto
import com.amaxonia.pos.domain.model.sales.SalePaymentSummaryDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalInvoicePrintPayloadMapperTest {
    private val testCompany =
        CompanyDetailsSnapshot(
            id = 1,
            name = "EMPRESA LOCAL S.A.",
            adminDb = "admin_db",
            accountingDb = "acc_db",
            payrollDb = "pay_db",
            rif = "J-99887766-5",
        )

    private val testCaja =
        Caja(
            idCaja = "1",
            codCaja = "01",
            descripcion = "Caja Principal",
            estatus = 1,
            idSucursal = 1,
            serieCaja = "P01",
            sucursalNombre = "Sucursal Central",
            sucursalCodigo = "SUC01",
            codigoSucursalEmisor = "EMISOR-01",
            defaultSellerName = "Vendedor Test",
        )

    @Test
    fun mapsProcessSaleRequestToPrintPayloadForPanama() {
        val request =
            createTestRequest(
                idFactura = "OFF-12345",
                codFactura = "OFF-0001",
                isMultiCurrency = false,
            )

        val payload =
            LocalInvoicePrintPayloadMapper.fromRequest(
                request = request,
                company = testCompany,
                caja = testCaja,
            )

        assertEquals("OFF-12345", payload.facturaId)
        assertEquals("OFF-0001", payload.numeroFactura)
        assertEquals("EMPRESA LOCAL S.A.", payload.empresa.nombre)
        assertEquals("J-99887766-5", payload.empresa.ruc)
        assertEquals("Sucursal Central", payload.empresa.tienda)
        assertEquals("Caja Principal", payload.empresa.caja)
        assertEquals("Juan Perez", payload.cliente?.nombre)
        assertEquals("8-123-456", payload.cliente?.documento)
        assertEquals(2, payload.productos.size)
        assertEquals("Hamburguesa Clasica", payload.productos[0].nombre)
        assertEquals("2", payload.productos[0].cantidad)
        assertEquals("10.00", payload.productos[0].precioUnitario)
        assertEquals("21.40", payload.productos[0].total)
        assertEquals("7", payload.productos[0].tasaImpuesto)
        assertEquals("P001", payload.productos[0].codigo)

        assertEquals("20.00", payload.subtotal)
        assertEquals("2.00", payload.descuento)
        assertEquals("1.40", payload.totalImpuesto)
        assertEquals("21.40", payload.total)

        assertEquals(1, payload.pagos.size)
        assertEquals("CASH", payload.pagos[0].metodo)
        assertEquals("25.00", payload.pagos[0].monto)
        assertEquals("3.60", payload.cambio)

        // Offline fields must be null
        assertNull(payload.cufe)
        assertNull(payload.qrUrl)
        assertNull(payload.fechaRecepcionDgi)
        assertNull(payload.numeroDocumentoFiscal)
        assertNull(payload.numeroControlThka)
    }

    @Test
    fun formatsSunmiTicketForPanamaOfflineWithoutQrOrCufe() {
        val request = createTestRequest(idFactura = "OFF-999", codFactura = "OFF-999")
        val payload = LocalInvoicePrintPayloadMapper.fromRequest(request, testCompany, testCaja)

        val ticket = PanamaInvoiceTicketFormatter().format(payload, "PA")
        assertTrue(ticket.elements.isNotEmpty())

        val hasQr = ticket.elements.any { it is TicketElement.Qr }
        assertFalse("El ticket offline no debe contener código QR", hasQr)

        val texts = ticket.elements.filterIsInstance<TicketElement.Text>().map { it.value }
        assertFalse(texts.any { it.contains("Consulte por la clave de acceso") })
    }

    @Test
    fun formatsSunmiTicketForVenezuelaOfflineWithoutDigitalFiscalFooter() {
        val request =
            createTestRequest(
                idFactura = "OFF-VE-01",
                codFactura = "OFF-VE-01",
                isMultiCurrency = true,
                rate = 40.0,
            )
        val payload = LocalInvoicePrintPayloadMapper.fromRequest(request, testCompany, testCaja)

        val ticket = VenezuelaInvoiceTicketFormatter().format(payload)
        assertTrue(ticket.elements.isNotEmpty())

        val texts = ticket.elements.filterIsInstance<TicketElement.Text>().map { it.value }
        assertTrue(texts.contains("FACTURA"))
        assertFalse("El ticket offline VE no debe incluir bloque FACTURA DIGITAL", texts.contains("FACTURA DIGITAL"))

        val columns = ticket.elements.filterIsInstance<TicketElement.Columns>().map { it.values }
        assertFalse(columns.any { it.first() == "Nro. documento:" })
        assertFalse(columns.any { it.first() == "Nro. control:" })
    }

    @Test
    fun mapsTransactionToPrintPayloadProperly() {
        val transaction =
            Transaction(
                id = "TRX-001",
                invoiceNumber = "INV-001",
                time = "10:30 AM",
                amount = 15.00,
                currency = "USD",
                status = TransactionStatus.PENDING,
                dateHeader = "Lunes, 26 Agosto 2026",
                clienteNombre = "Maria Lopez",
                clienteIdentificacion = "V-20111222",
                formaPago = "EFECTIVO",
                paymentMethods =
                    listOf(
                        TransactionPaymentMethod(description = "Efectivo", sigla = "CASH", amount = 15.00),
                    ),
                fiscalItems =
                    listOf(
                        TransactionFiscalItem(description = "Cafe Latte", quantity = 3.0, unitPriceWithoutTax = 5.00, iva = 0.0),
                    ),
            )

        val payload = LocalInvoicePrintPayloadMapper.fromTransaction(transaction, testCompany, testCaja)

        assertEquals("TRX-001", payload.facturaId)
        assertEquals("INV-001", payload.numeroFactura)
        assertEquals("Maria Lopez", payload.cliente?.nombre)
        assertEquals("V-20111222", payload.cliente?.documento)
        assertEquals("15.00", payload.total)
        assertEquals(1, payload.productos.size)
        assertEquals("Cafe Latte", payload.productos[0].nombre)
        assertEquals("3", payload.productos[0].cantidad)
        assertEquals("5.00", payload.productos[0].precioUnitario)
    }

    @Suppress("LongMethod")
    private fun createTestRequest(
        idFactura: String,
        codFactura: String,
        isMultiCurrency: Boolean = false,
        rate: Double = 1.0,
    ): ProcessSaleRequestDto =
        ProcessSaleRequestDto(
            idFactura = idFactura,
            codFactura = codFactura,
            factura =
                SaleInvoiceDto(
                    idCliente = "1",
                    codCliente = "C001",
                    codVendedor = 1,
                    idShop = 1,
                    idSucursal = 1,
                    idCaja = "1",
                    codigoCaja = "01",
                    idCajaSecuencia = "SEC-01",
                    serieSucursal = "SUC-01",
                    formaPago = "CONTADO",
                    codEstatus = 2,
                    subtotal = 20.00,
                    descuentosItemFactura = 2.00,
                    ivaTotalFactura = 1.40,
                    totalTotalFactura = 21.40,
                    montoItemsFactura = 20.00,
                    totalizarBaseImponible = 20.00,
                    totalizarMontoIva = 1.40,
                    totalizarTotalGeneral = 21.40,
                    usuarioCreacion = "admin",
                    facturarA = "Juan Perez",
                    facturarARuc = "8-123-456",
                    facturarADireccion = "Calle 50, Panama",
                    facturarATelefono = "6000-0000",
                ),
            items =
                listOf(
                    SaleItemDto(
                        idItem = 101,
                        itemAlmacen = 1,
                        itemDescripcion = "Hamburguesa Clasica",
                        itemCantidad = 2.0,
                        itemCantidadTotal = 2.0,
                        itemPrecioSinIva = 10.00,
                        itemDescuento = 0.0,
                        itemMontoDescuento = 2.00,
                        itemPIva = 7.0,
                        itemTotalSinIva = 20.00,
                        itemTotalConIva = 21.40,
                        itemCodigo = "P001",
                    ),
                    SaleItemDto(
                        idItem = 102,
                        itemAlmacen = 1,
                        itemDescripcion = "Papas Fritas",
                        itemCantidad = 1.0,
                        itemCantidadTotal = 1.0,
                        itemPrecioSinIva = 3.00,
                        itemDescuento = 0.0,
                        itemMontoDescuento = 0.0,
                        itemPIva = 0.0,
                        itemTotalSinIva = 3.00,
                        itemTotalConIva = 3.00,
                        itemCodigo = "P002",
                    ),
                ),
            pagoResumen =
                SalePaymentSummaryDto(
                    totalizarMontoCancelar = 21.40,
                    totalizarMontoEfectivo = 25.00,
                    totalizarCambio = 3.60,
                    totalizarSaldoPendiente = 0.0,
                    montosPorTipo = mapOf("CASH" to 21.40),
                ),
            pagos =
                listOf(
                    SalePaymentDto(
                        idFormaPago = 1,
                        tipoMovimiento = "CASH",
                        monto = 25.00,
                        montoRecibido = 25.00,
                        efectivoCambio = 3.60,
                    ),
                ),
            moneda =
                if (isMultiCurrency) {
                    SaleCurrencyDto(
                        multiMoneda = "SI",
                        tasa = rate,
                        idTasa = 1,
                        monedaBase = 1,
                        abrMonedaBase = "Bs",
                        monedaSecundaria = 2,
                        abrMonedaSecundaria = "USD",
                        totalRef = 21.40,
                    )
                } else {
                    null
                },
        )
}
