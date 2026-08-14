package com.amaxonia.pos.data.printer.venezuela

import com.amaxonia.pos.domain.model.printer.TicketElement
import com.amaxonia.pos.domain.model.sales.ClientePrintDto
import com.amaxonia.pos.domain.model.sales.EmpresaPrintDto
import com.amaxonia.pos.domain.model.sales.FacturaPrintPayloadDto
import com.amaxonia.pos.domain.model.sales.PagoPrintDto
import com.amaxonia.pos.domain.model.sales.ProductoPrintDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Golden + invariantes para [VenezuelaInvoiceTicketFormatter] (FASE 2 ítems 2.4 / 2.9).
 *
 * Patrón espejo de [PanamaInvoiceTicketFormatterTest]: los 15 sub-casos cubren
 * tanto el golden textual (estructura del ticket) como los invariantes de
 * negocio exigidos por el brief:
 *
 *  1.  Ancho de columnas 8+4+5+7+8 = 32.
 *  2.  Encabezado usa "FACTURA".
 *  3.  RIF en vez de RUC.
 *  4.  Etiqueta (IVA X%) en productos.
 *  5.  Total IVA en vez de Total Impuesto.
 *  6.  Pie fiscal: bloque "FACTURA DIGITAL" solo si hay datos persistidos.
 *  7.  En ausencia de datos fiscales no se imprime bloque digital.
 *  8.  Nro. documento y Nro. control provienen exclusivamente del payload.
 *  9.  IGTF: solo se imprime si monto > 0.
 * 10.  IGTF: omite la línea si el monto viene vacío.
 * 11.  Multimoneda: solo se imprime si tasa y totalDivisa están presentes.
 * 12.  Multimoneda: usa abreviaturas del payload.
 * 13.  NO contiene conceptos Panamá (DGI/CAFE/CUFE/QR).
 * 14.  NO genera QR ( Venezuela digital no tiene QR ).
 * 15.  Idempotencia: dos format con el mismo payload producen el mismo ticket.
 */
class VenezuelaInvoiceTicketFormatterTest {
    @Test
    fun `1 - ancho de columnas de producto es 40 caracteres`() {
        val ticket = VenezuelaInvoiceTicketFormatter().format(payload())

        val columns = ticket.elements.filterIsInstance<TicketElement.Columns>()
        val header = columns.first { it.values.contains("Cant") }
        assertEquals(40, header.widths.sum())
        assertEquals(listOf(10, 5, 5, 8, 12), header.widths)
    }

    @Test
    fun `2 - encabezado usa el titulo FACTURA`() {
        val ticket = VenezuelaInvoiceTicketFormatter().format(payload())
        val texts = ticket.elements.filterIsInstance<TicketElement.Text>().map { it.value }

        assertTrue("Debe incluir el título FACTURA", texts.contains("FACTURA"))
        assertFalse(
            "NO debe incluir el título Panamá (DGI/Comprobante Auxiliar)",
            texts.any { it.contains("DGI") || it.contains("Comprobante Auxiliar") },
        )
    }

    @Test
    fun `3 - cabecera usa etiqueta RIF`() {
        val ticket = VenezuelaInvoiceTicketFormatter().format(payload())
        val texts = ticket.elements.filterIsInstance<TicketElement.Text>().map { it.value }

        assertTrue(texts.any { it.startsWith("RIF:") })
        assertFalse(texts.any { it.startsWith("RUC:") })
    }

    @Test
    fun `4 - productos incluyen etiqueta IVA`() {
        val ticket = VenezuelaInvoiceTicketFormatter().format(payload())
        val texts = ticket.elements.filterIsInstance<TicketElement.Text>().map { it.value }

        assertTrue(texts.any { it.contains("(IVA 16%)") })
        assertFalse(texts.any { it.contains("CAFE") || it.contains("CUFE") })
    }

    @Test
    fun `5 - totales usan Total IVA`() {
        val ticket = VenezuelaInvoiceTicketFormatter().format(payload())
        val totals = ticket.elements.filterIsInstance<TicketElement.TotalsRow>().map { it.label }
        assertTrue("Debe mostrar 'Total IVA:'", totals.contains("Total IVA:"))
        assertFalse("NO debe mostrar 'Total Impuesto:'", totals.contains("Total Impuesto:"))
    }

    @Test
    fun `6 - bloque FACTURA DIGITAL se imprime cuando hay datos fiscales persistidos`() {
        val ticket = VenezuelaInvoiceTicketFormatter().format(payload())
        val texts = ticket.elements.filterIsInstance<TicketElement.Text>().map { it.value }
        val cols = ticket.elements.filterIsInstance<TicketElement.Columns>().map { it.values }

        assertTrue("Debe incluir el titular FACTURA DIGITAL", texts.contains("FACTURA DIGITAL"))
        assertTrue("Debe listar Nro. documento", cols.any { it.first() == "Nro. documento:" })
        assertTrue("Debe listar Nro. control", cols.any { it.first() == "Nro. control:" })
    }

    @Test
    fun `7 - sin datos fiscales persistidos no se imprime bloque digital`() {
        val payload = payload().copy(numeroDocumentoFiscal = null, numeroControlThka = null)
        val ticket = VenezuelaInvoiceTicketFormatter().format(payload)
        val texts = ticket.elements.filterIsInstance<TicketElement.Text>().map { it.value }
        val cols = ticket.elements.filterIsInstance<TicketElement.Columns>().map { it.values }

        assertFalse(
            "No debe imprimir FACTURA DIGITAL sin persistencia",
            texts.contains("FACTURA DIGITAL"),
        )
        assertFalse(
            "No debe inventar Nro. documento",
            cols.any { it.first() == "Nro. documento:" },
        )
        assertFalse(
            "No debe inventar Nro. control",
            cols.any { it.first() == "Nro. control:" },
        )
    }

    @Test
    fun `8 - nro documento y nro control provienen exclusivamente del payload`() {
        val ticket =
            VenezuelaInvoiceTicketFormatter().format(
                payload().copy(numeroDocumentoFiscal = "DOC-PAYLOAD", numeroControlThka = "CTRL-PAYLOAD"),
            )
        val cols = ticket.elements.filterIsInstance<TicketElement.Columns>().map { it.values }

        assertTrue("El nro. documento debe venir del payload", cols.any { it.contains("DOC-PAYLOAD") })
        assertTrue("El nro. control debe venir del payload", cols.any { it.contains("CTRL-PAYLOAD") })
    }

    @Test
    fun `9 - IGTF se imprime cuando el monto es positivo`() {
        val ticket =
            VenezuelaInvoiceTicketFormatter().format(
                payload().copy(
                    igtfMonto = "3.00",
                    igtfBaseImponible = "0.09",
                    igtfTasa = "3.0",
                ),
            )
        val cols = ticket.elements.filterIsInstance<TicketElement.Columns>().map { it.values }

        assertTrue("Debe imprimir la base IGTF", cols.any { it.first().contains("Base IGTF") })
        assertTrue("Debe imprimir la tasa IGTF", cols.any { it.first() == "Tasa IGTF:" })
    }

    @Test
    fun `10 - IGTF se omite cuando el monto es cero`() {
        val ticket =
            VenezuelaInvoiceTicketFormatter().format(
                payload().copy(igtfMonto = "0.00", igtfBaseImponible = "0.00", igtfTasa = "3.0"),
            )
        val cols = ticket.elements.filterIsInstance<TicketElement.Columns>().map { it.values }

        assertFalse("No debe imprimir IGTF si monto es 0", cols.any { it.first().contains("IGTF") })
    }

    @Test
    fun `11 - multimoneda se imprime solo cuando ambos campos estan presentes`() {
        // Presente
        val ticketFull =
            VenezuelaInvoiceTicketFormatter().format(
                payload().copy(tasaCambioBs = "40.00", totalDivisa = "10.00", abrMonedaBase = "Bs", abrMonedaSecundaria = "USD"),
            )
        val colsFull = ticketFull.elements.filterIsInstance<TicketElement.Columns>().map { it.values }
        assertTrue("Debe imprimir tasa", colsFull.any { it.first().startsWith("Tasa") })

        // Ausente: falta totalDivisa
        val ticketPartial =
            VenezuelaInvoiceTicketFormatter().format(
                payload().copy(tasaCambioBs = "40.00", totalDivisa = null),
            )
        val colsPartial = ticketPartial.elements.filterIsInstance<TicketElement.Columns>().map { it.values }
        assertFalse(
            "No debe imprimir tasa sin totalDivisa",
            colsPartial.any { it.first().startsWith("Tasa") },
        )
    }

    @Test
    fun `12 - multimoneda usa abreviaturas personalizadas del payload`() {
        val ticket =
            VenezuelaInvoiceTicketFormatter().format(
                payload().copy(
                    tasaCambioBs = "40.00",
                    totalDivisa = "10.00",
                    abrMonedaBase = "BsS",
                    abrMonedaSecundaria = "USDT",
                ),
            )
        val cols = ticket.elements.filterIsInstance<TicketElement.Columns>().map { it.values }

        assertTrue(cols.any { it.first().contains("USDT→BsS") })
        assertTrue(cols.any { it.first().startsWith("Total USDT") })
    }

    @Test
    fun `13 - NO contiene conceptos Panamá`() {
        val ticket = VenezuelaInvoiceTicketFormatter().format(payload())
        val joined =
            ticket.elements.joinToString("\n") { element ->
                when (element) {
                    is TicketElement.Text -> element.value
                    is TicketElement.Columns -> element.values.joinToString(" ")
                    else -> ""
                }
            }

        assertFalse(joined.contains("DGI"))
        assertFalse(joined.contains("CAFE"))
        assertFalse(joined.contains("CUFE"))
        assertFalse(joined.contains("Protocolo de autorización"))
        assertFalse(joined.contains("Punto de Facturación"))
    }

    @Test
    fun `14 - Venezuela digital nunca genera elemento QR`() {
        val ticket = VenezuelaInvoiceTicketFormatter().format(payload())
        assertTrue(ticket.elements.none { it is TicketElement.Qr })
    }

    @Test
    fun `15 - idempotencia - dos invocaciones con el mismo payload generan el mismo ticket`() {
        val payload = payload()
        val first = VenezuelaInvoiceTicketFormatter().format(payload)
        val second = VenezuelaInvoiceTicketFormatter().format(payload)

        assertEquals(
            first.elements.filterIsInstance<TicketElement.Text>().map { it.value },
            second.elements.filterIsInstance<TicketElement.Text>().map { it.value },
        )
        assertEquals(
            first.elements.filterIsInstance<TicketElement.Columns>().map { it.values },
            second.elements.filterIsInstance<TicketElement.Columns>().map { it.values },
        )
    }

    /**
     * 16. Totals (Subtotal / Descuento / IVA / Total) are emitted as TotalsRow so the SUNMI
     *     driver renders each one as a single physical line in the 40-column Venezuela layout.
     *     No label can spill a trailing character to the next row.
     */
    @Test
    fun `16 - totales se emiten como filas monoespaciadas de 40 columnas`() {
        val ticket = VenezuelaInvoiceTicketFormatter().format(payload())
        val totals = ticket.elements.filterIsInstance<TicketElement.TotalsRow>()

        // Subtotal / Monto Exento / Descuento / Total IVA / Total
        assertEquals(5, totals.size)
        val labels = totals.map { it.label }
        assertTrue(labels.contains("Subtotal Items:"))
        assertTrue(labels.contains("Descuento:"))
        assertTrue(labels.contains("Total IVA:"))
        assertTrue(labels.contains("Total:"))
        totals.forEach { row ->
            assertEquals(40, row.printerWidth)
            assertTrue(
                "Label '${row.label}' fits in labelWidth ${row.labelWidth}",
                row.label.length <= row.labelWidth,
            )
        }
    }

    /**
     * 17. Descuento is always rendered, even when zero, mirroring the Cobro breakdown.
     */
    @Test
    fun `17 - la linea de Descuento siempre se muestra incluso en cero`() {
        val ticket =
            VenezuelaInvoiceTicketFormatter().format(payload().copy(descuento = "0.00"))
        val descuento = ticket.elements.filterIsInstance<TicketElement.TotalsRow>().first { it.label == "Descuento:" }
        assertEquals("0.00", descuento.value)
    }

    @Test
    fun `18 - descuento ausente en backend se renderiza como cero sin romper el ticket`() {
        val ticket =
            VenezuelaInvoiceTicketFormatter().format(payload().copy(descuento = null))
        val descuento = ticket.elements.filterIsInstance<TicketElement.TotalsRow>().first { it.label == "Descuento:" }
        assertEquals("0.00", descuento.value)
    }

    /**
     * 19. Las cuatro etiquetas canónicas caben completas en labelWidth (sin ellipsis ni
     *     truncamiento). Garantía del contrato SUNMI物理ico.
     */
    @Test
    fun `19 - etiquetas canonicas de totales caben completas sin ellipsis`() {
        val ticket = VenezuelaInvoiceTicketFormatter().format(payload())
        val totals = ticket.elements.filterIsInstance<TicketElement.TotalsRow>()
        val canonical = setOf("Subtotal Items:", "Descuento:", "Total IVA:", "Total:")

        totals.filter { it.label in canonical }.forEach { row ->
            assertTrue(
                "Label '${row.label}' debe caber en labelWidth ${row.labelWidth}",
                row.label.length <= row.labelWidth,
            )
        }
        totals.forEach { row ->
            assertFalse("Ninguna etiqueta puede usar ellipsis: ${row.label}", row.label.contains("…"))
        }
    }

    // ─── Fixture ───────────────────────────────────────────────────────────

    private fun payload(): FacturaPrintPayloadDto =
        FacturaPrintPayloadDto(
            facturaId = "1",
            numeroFactura = "F001-0001",
            fecha = "2026-06-01T13:45:00",
            empresa =
                EmpresaPrintDto(
                    nombre = "MI EMPRESA C.A.",
                    ruc = "J-12345678-9",
                    direccion = "Av. Principal, Caracas",
                    telefono = "+58 212-5551234",
                    tienda = "Sucursal Centro",
                    caja = "Caja 01",
                ),
            cliente =
                ClientePrintDto(
                    nombre = "Cliente General",
                    documento = "V-12345678",
                ),
            vendedor = "OPERADOR-01",
            productos =
                listOf(
                    ProductoPrintDto(
                        nombre = "Producto A",
                        cantidad = "2",
                        unidad = "UND",
                        precioUnitario = "50.00",
                        descuento = "0.00",
                        impuesto = "16.00",
                        total = "116.00",
                        codigo = "P0001",
                        tasaImpuesto = "16",
                    ),
                ),
            subtotal = "100.00",
            montoExento = "0.00",
            descuento = "0.00",
            totalImpuesto = "16.00",
            total = "116.00",
            pagos =
                listOf(
                    PagoPrintDto(metodo = "EFECTIVO", monto = "116.00"),
                ),
            cambio = "0.00",
            // Proveedores de Panamá: NO deben aparecer en VE.
            qrUrl = null,
            cufe = null,
            fechaRecepcionDgi = null,
            proveedorAutorizado = null,
            puntoFacturacionFiscal = null,
            codigoSucursal = null,
            protocoloAutorizacion = null,
            // Persistencia fiscal VE:
            numeroDocumentoFiscal = "00012345",
            numeroControlThka = " 001-00001 ",
        )
}
