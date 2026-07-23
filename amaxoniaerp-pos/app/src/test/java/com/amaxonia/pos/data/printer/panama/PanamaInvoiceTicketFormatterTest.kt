package com.amaxonia.pos.data.printer.panama

import com.amaxonia.pos.domain.model.printer.TicketElement
import com.amaxonia.pos.domain.model.sales.EmpresaPrintDto
import com.amaxonia.pos.domain.model.sales.FacturaPrintPayloadDto
import com.amaxonia.pos.domain.model.sales.PagoPrintDto
import com.amaxonia.pos.domain.model.sales.ProductoPrintDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PanamaInvoiceTicketFormatterTest {
    @Test
    fun matchesCharacterizedGolden() {
        val actual = PanamaInvoiceTicketFormatter().format(payload()).elements.joinToString("\n", transform = ::serialize)
        val uri = checkNotNull(javaClass.classLoader?.getResource("golden/panama-invoice-ticket.txt")).toURI()

        assertEquals(File(uri).readText().trimEnd(), actual)
    }

    @Test
    fun includesRequiredFiscalData() {
        val ticket = PanamaInvoiceTicketFormatter().format(payload())
        val text =
            ticket.elements.joinToString("\n") { element ->
                when (element) {
                    is TicketElement.Text -> element.value
                    is TicketElement.Columns -> element.values.joinToString(" ")
                    is TicketElement.Qr -> element.value
                    else -> ""
                }
            }

        assertTrue(text.contains("F001-0001"))
        assertTrue(text.contains("MI EMPRESA S.A."))
        assertTrue(text.contains("Producto A"))
        assertTrue(text.contains("10.70"))
        assertTrue(text.contains("https://fe.dgi"))
        assertTrue(text.contains("CUFE123"))
        assertTrue(text.contains("0000003500"))
        assertTrue(text.contains("PROTOCOLO-123"))
    }

    @Test
    fun usesCompactFiscalQrSize() {
        val ticket = PanamaInvoiceTicketFormatter().format(payload())
        val qr = ticket.elements.filterIsInstance<TicketElement.Qr>().single()

        assertEquals("https://fe.dgi", qr.value)
        assertEquals(3, qr.size)
    }

    @Test
    fun supportsNullableOptionalFields() {
        val payload = payload().copy(cliente = null, vendedor = null, qrUrl = null, cufe = null)
        val ticket = PanamaInvoiceTicketFormatter().format(payload)
        assertTrue(ticket.elements.isNotEmpty())
    }

    @Test
    fun formatsSunmiTicketForVenezuelaWithoutPanamaLegalCopy() {
        val ticket = PanamaInvoiceTicketFormatter().format(payload(), "VE")
        val text =
            ticket.elements
                .joinToString("\n") { element ->
                    when (element) {
                        is TicketElement.Text -> element.value
                        is TicketElement.Columns -> element.values.joinToString(" ")
                        else -> ""
                    }
                }

        assertTrue(text.contains("RIF: 123"))
        assertTrue(text.contains("Total IVA:"))
        assertFalse(text.contains("Total Impuesto:"))
        assertFalse(text.contains("DGI"))
        assertFalse(text.contains("Factura de Operación Interna"))
        assertFalse(text.contains("Proveedor Autorizado"))
        assertTrue(ticket.elements.none { it is TicketElement.Qr })
    }

    private fun payload(): FacturaPrintPayloadDto =
        FacturaPrintPayloadDto(
            facturaId = "1",
            numeroFactura = "F001-0001",
            fecha = "2026-06-01T13:45:00",
            empresa = EmpresaPrintDto(nombre = "MI EMPRESA S.A.", ruc = "123"),
            cliente = null,
            vendedor = null,
            productos =
                listOf(
                    ProductoPrintDto(
                        nombre = "Producto A",
                        cantidad = "2",
                        unidad = "UND",
                        precioUnitario = "5.00",
                        descuento = "0.00",
                        impuesto = "0.70",
                        total = "10.70",
                        codigo = "P0001",
                        tasaImpuesto = "7",
                    ),
                ),
            subtotal = "10.00",
            montoExento = "0.00",
            totalImpuesto = "0.70",
            total = "10.70",
            pagos =
                listOf(
                    PagoPrintDto("EFECTIVO", "5.70"),
                    PagoPrintDto("YAPPY", "5.00"),
                ),
            cambio = "9.30",
            qrUrl = "https://fe.dgi",
            cufe = "CUFE123",
            fechaRecepcionDgi = "2026-06-01T13:45:10",
            proveedorAutorizado = "The Factory HKA Corp.",
            numeroDocumentoFiscal = "0000003500",
            puntoFacturacionFiscal = "777",
            codigoSucursal = "6666",
            protocoloAutorizacion = "PROTOCOLO-123",
        )

    private fun serialize(element: TicketElement): String =
        when (element) {
            is TicketElement.Text -> "TEXT|${element.align}|${element.bold}|${element.value}"
            is TicketElement.Columns ->
                "COLUMNS|${element.values.joinToString("~")}|${element.widths.joinToString(",")}|" +
                    element.aligns.joinToString(",")
            is TicketElement.Qr -> "QR|${element.size}|${element.value}"
            is TicketElement.Feed -> "FEED|${element.lines}"
            TicketElement.Divider -> "DIVIDER"
        }
}
