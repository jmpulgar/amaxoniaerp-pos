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
                        is TicketElement.TotalsRow -> element.label
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

    /**
     * 10. Panamá ticket prints Subtotal / Descuento / Impuesto / Total using TotalsRow so the
     *     SUNMI driver never wraps a single trailing label character to the next physical line.
     */
    @Test
    fun totalsAreRenderedAsSinglePhysicalLines() {
        val ticket = PanamaInvoiceTicketFormatter().format(payload())
        val totalsRows = ticket.elements.filterIsInstance<TicketElement.TotalsRow>()

        // Subtotal / Monto Exento / Descuento / Total Impuesto / Total
        assertEquals(5, totalsRows.size)
        val labels = totalsRows.map { it.label }
        assertTrue("Subtotal Items:", labels.contains("Subtotal Items:"))
        assertTrue("Descuento:", labels.contains("Descuento:"))
        assertTrue("Total Impuesto:", labels.contains("Total Impuesto:"))
        assertTrue("Total:", labels.contains("Total:"))

        // Each TotalsRow targets the 32-column Panamá printer width, so the printer emits a single
        // physical line and no label can spill a trailing character.
        totalsRows.forEach { row ->
            assertEquals(32, row.printerWidth)
            // Label + value together must fit in the physical width when value is the typical
            // money string, so no driver-level wrapping is triggered.
            assertTrue(
                "Label '${row.label}' must fit in labelWidth ${row.labelWidth}",
                row.label.length <= row.labelWidth,
            )
        }
    }

    /**
     * 11. Descuento line is always shown (even when "0.00") so the customer sees the same
     *     breakdown the cashier saw on the Cobro screen.
     */
    @Test
    fun discountLineIsAlwaysShownEvenWhenZero() {
        val payload = payload().copy(descuento = "0.00")
        val ticket = PanamaInvoiceTicketFormatter().format(payload)
        val totals = ticket.elements.filterIsInstance<TicketElement.TotalsRow>()

        val descuento = totals.first { it.label == "Descuento:" }
        assertEquals("0.00", descuento.value)
    }

    @Test
    fun discountLineReflectsBackendDiscount() {
        val payload = payload().copy(descuento = "12.34")
        val ticket = PanamaInvoiceTicketFormatter().format(payload)
        val totals = ticket.elements.filterIsInstance<TicketElement.TotalsRow>()

        val descuento = totals.first { it.label == "Descuento:" }
        assertEquals("12.34", descuento.value)
    }

    @Test
    fun discountFallsBackToZeroWhenBackendDidNotExposeIt() {
        val payload = payload().copy(descuento = null)
        val ticket = PanamaInvoiceTicketFormatter().format(payload)
        val totals = ticket.elements.filterIsInstance<TicketElement.TotalsRow>()

        val descuento = totals.first { it.label == "Descuento:" }
        assertEquals("0.00", descuento.value)
    }

    /**
     * 12. The four canonical totals labels must fit verbatim within [TotalsRow.labelWidth] — no
     *     ellipsis, no truncation. Guards the physical SUNMI layout against future regressions.
     */
    @Test
    fun canonical_totals_labels_fit_without_ellipsis_or_truncation() {
        val ticket = PanamaInvoiceTicketFormatter().format(payload())
        val totals = ticket.elements.filterIsInstance<TicketElement.TotalsRow>()

        // The four canonical labels that the brief requires, complete:
        val canonical = setOf("Subtotal Items:", "Descuento:", "Total Impuesto:", "Total:")
        totals.filter { it.label in canonical }.forEach { row ->
            assertTrue(
                "Label '${row.label}' must fit in labelWidth ${row.labelWidth} without truncation",
                row.label.length <= row.labelWidth,
            )
        }
        // Hard-contract: the formatter never emits the Unicode ellipsis glyph (some SUNMI
        // firmware renders it as "?").
        totals.forEach { row ->
            assertFalse("Label must not contain ellipsis: ${row.label}", row.label.contains("…"))
        }
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
            descuento = "0.00",
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
            is TicketElement.TotalsRow ->
                "TOTALS|${element.bold}|${element.labelWidth}|${element.printerWidth}|${element.label}|${element.value}"
            is TicketElement.Qr -> "QR|${element.size}|${element.value}"
            is TicketElement.Feed -> "FEED|${element.lines}"
            TicketElement.Divider -> "DIVIDER"
        }
}
