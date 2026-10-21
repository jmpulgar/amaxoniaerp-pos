package com.amaxonia.pos.data.printer.panama

import com.amaxonia.pos.domain.model.creditnote.CreditNoteDetailDto
import com.amaxonia.pos.domain.model.creditnote.CreditNoteDetailLineDto
import com.amaxonia.pos.domain.model.creditnote.CreditNoteFiscalDocumentDto
import com.amaxonia.pos.domain.model.creditnote.CreditNoteFiscalStatusDto
import com.amaxonia.pos.domain.model.printer.TicketElement
import com.amaxonia.pos.domain.model.sales.EmpresaPrintDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PanamaCreditNoteTicketFormatterTest {
    @Test
    fun includesRequiredCreditNoteFiscalData() {
        val detail = createCreditNoteDetail()
        val company =
            EmpresaPrintDto(
                nombre = "TORREFACTORA VALLE CHIRIQUI, S. A.",
                ruc = "155673336-2-2018 DV95",
                direccion = "EL PORVENIR, BUGABA",
                telefono = "9999-9999",
            )
        val ticket = PanamaCreditNoteTicketFormatter().format(detail, company, cajaName = "Caja 001")
        val text =
            ticket.elements.joinToString("\n") { element ->
                when (element) {
                    is TicketElement.Text -> element.value
                    is TicketElement.Columns -> element.values.joinToString(" ")
                    is TicketElement.TotalsRow -> "${element.label} ${element.value}"
                    is TicketElement.Qr -> element.value
                    else -> ""
                }
            }

        assertTrue(text.contains("NOTA DE CRÉDITO ELECTRÓNICA"))
        assertTrue(text.contains("TORREFACTORA VALLE CHIRIQUI, S. A."))
        assertTrue(text.contains("155673336-2-2018 DV95"))
        assertTrue(text.contains("NC-001-00001"))
        assertTrue(text.contains("001-00001"))
        assertTrue(text.contains("CAFE ARABICO 100% PURO 200G"))
        assertTrue(text.contains("131.60"))
        assertTrue(text.contains("FE0420000123456789"))
        assertTrue(text.contains("PROTOCOLO-DGI-999"))
        assertTrue(text.contains("https://dgi-fep.mef.gob.pa/Consultas/FacturasPorQR?chFE=FE04"))
    }

    @Test
    fun usesCompactFiscalQrSize() {
        val detail = createCreditNoteDetail()
        val ticket = PanamaCreditNoteTicketFormatter().format(detail)
        val qr = ticket.elements.filterIsInstance<TicketElement.Qr>().single()

        assertEquals("https://dgi-fep.mef.gob.pa/Consultas/FacturasPorQR?chFE=FE04", qr.value)
        assertEquals(3, qr.size)
    }

    @Test
    fun handlesEmptyCompanyAndFiscalFootersGracefully() {
        val detail =
            createCreditNoteDetail().copy(
                cufe = "",
                qr = "",
                nroProtocoloAutorizacion = "",
                fechaRecepcionDGI = "",
            )
        val ticket = PanamaCreditNoteTicketFormatter().format(detail, null)
        assertTrue(ticket.elements.isNotEmpty())
    }

    private fun createCreditNoteDetail() =
        CreditNoteDetailDto(
            id = "nc-id-1",
            codigo = "NC-001-00001",
            facturaId = "factura-id-1",
            facturaCodigo = "001-00001",
            fecha = "2026-10-21",
            periodo = "2026-10",
            observacion = "Devolucion total",
            clienteNombre = "CONSUMIDOR FINAL",
            clienteIdentificacion = "910837884",
            subtotal = 131.60,
            impuesto = 0.00,
            total = 131.60,
            fiscalStatus = CreditNoteFiscalStatusDto.CONFIRMADA,
            fiscalNumber = "0000003475",
            anulaFacturaCompleta = true,
            cufe = "FE0420000123456789",
            qr = "https://dgi-fep.mef.gob.pa/Consultas/FacturasPorQR?chFE=FE04",
            nroProtocoloAutorizacion = "PROTOCOLO-DGI-999",
            fechaRecepcionDGI = "2026-10-21 08:00:00",
            lines =
                listOf(
                    CreditNoteDetailLineDto(
                        id = "line-1",
                        idDetalleFactura = "det-1",
                        idItem = 8,
                        descripcion = "CAFE ARABICO 100% PURO 200G",
                        codigo = "7451106580133",
                        referencia = "",
                        cantidad = 24.0,
                        precioSinIva = 2.15,
                        descuentoPorcentaje = 0.0,
                        descuentoMonto = 0.0,
                        pIva = 0.0,
                        totalSinIva = 51.60,
                        totalConIva = 51.60,
                    ),
                ),
            fiscalDocument =
                CreditNoteFiscalDocumentDto(
                    creditNoteId = "nc-id-1",
                    creditNoteCode = "NC-001-00001",
                    date = "2026-10-21",
                    customerName = "CONSUMIDOR FINAL",
                    customerIdentifier = "910837884",
                    customerAddress = "PANAMA",
                    customerPhone = "9999-9999",
                    originalInvoiceCode = "001-00001",
                    originalFiscalNumber = "3438",
                    originalInvoiceDate = "2026-10-20",
                    printerSerial = "",
                    comment = "Devolucion",
                    lines = emptyList(),
                ),
        )
}
