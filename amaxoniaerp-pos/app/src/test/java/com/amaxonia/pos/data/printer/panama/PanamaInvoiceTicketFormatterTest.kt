package com.amaxonia.pos.data.printer.panama

import com.amaxonia.pos.domain.model.printer.TicketElement
import com.amaxonia.pos.domain.model.sales.EmpresaPrintDto
import com.amaxonia.pos.domain.model.sales.FacturaPrintPayloadDto
import com.amaxonia.pos.domain.model.sales.PagoPrintDto
import com.amaxonia.pos.domain.model.sales.ProductoPrintDto
import org.junit.Assert.assertTrue
import org.junit.Test

class PanamaInvoiceTicketFormatterTest {
    @Test
    fun includesRequiredFiscalData() {
        val ticket = PanamaInvoiceTicketFormatter().format(payload())
        val text = ticket.elements.joinToString("\n") { element ->
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
    }

    @Test
    fun supportsNullableOptionalFields() {
        val payload = payload().copy(cliente = null, vendedor = null, qrUrl = null, cufe = null)
        val ticket = PanamaInvoiceTicketFormatter().format(payload)
        assertTrue(ticket.elements.isNotEmpty())
    }

    private fun payload(): FacturaPrintPayloadDto {
        return FacturaPrintPayloadDto(
            facturaId = "1",
            numeroFactura = "F001-0001",
            fecha = "2026-06-01T13:45:00",
            empresa = EmpresaPrintDto(nombre = "MI EMPRESA S.A.", ruc = "123"),
            cliente = null,
            vendedor = null,
            productos = listOf(
                ProductoPrintDto("Producto A", "2", "UND", "5.00", "0.00", "0.70", "10.70")
            ),
            subtotal = "10.00",
            montoExento = "0.00",
            totalImpuesto = "0.70",
            total = "10.70",
            pagos = listOf(PagoPrintDto("EFECTIVO", "20.00")),
            cambio = "9.30",
            qrUrl = "https://fe.dgi",
            cufe = "CUFE123",
            fechaRecepcionDgi = "2026-06-01T13:45:10",
            proveedorAutorizado = "The Factory HKA Corp.",
        )
    }
}
