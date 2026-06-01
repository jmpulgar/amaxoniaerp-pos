package com.amaxonia.pos.data.printer.panama

import com.amaxonia.pos.domain.model.printer.TicketAlign
import com.amaxonia.pos.domain.model.printer.TicketDocument
import com.amaxonia.pos.domain.model.printer.TicketElement
import com.amaxonia.pos.domain.model.sales.FacturaPrintPayloadDto

class PanamaInvoiceTicketFormatter {
    fun format(payload: FacturaPrintPayloadDto): TicketDocument {
        return TicketDocument(
            elements = buildList {
                add(TicketElement.Text("Comprobante Auxiliar de", TicketAlign.CENTER))
                add(TicketElement.Text("Facturación Electrónica", TicketAlign.CENTER))
                add(TicketElement.Feed(1))
                add(TicketElement.Text(payload.empresa.nombre.uppercase(), TicketAlign.CENTER, bold = true))
                payload.empresa.ruc?.takeIfNotBlank()?.let { add(TicketElement.Text("RUC: $it", TicketAlign.CENTER)) }
                payload.empresa.direccion?.takeIfNotBlank()?.let { add(TicketElement.Text(it, TicketAlign.CENTER)) }
                payload.empresa.telefono?.takeIfNotBlank()?.let { add(TicketElement.Text("Tel: $it", TicketAlign.CENTER)) }
                add(TicketElement.Text("PANAMÁ", TicketAlign.CENTER))
                add(TicketElement.Divider)

                payload.empresa.tienda?.takeIfNotBlank()?.let { add(labelValue("Tienda:", it)) }
                payload.empresa.caja?.takeIfNotBlank()?.let { add(labelValue("Caja:", it)) }
                add(labelValue("Fecha:", payload.fecha))
                add(labelValue("Factura:", payload.numeroFactura))
                payload.vendedor?.takeIfNotBlank()?.let { add(labelValue("Vendedor:", it)) }
                add(TicketElement.Divider)

                payload.cliente?.let { cliente ->
                    add(TicketElement.Text("Cliente Fiscal", TicketAlign.LEFT, bold = true))
                    add(labelValue("Nombre:", cliente.nombre))
                    cliente.documento?.takeIfNotBlank()?.let { add(labelValue("RUC:", it)) }
                    add(TicketElement.Divider)
                }

                add(TicketElement.Text("Productos", TicketAlign.LEFT, bold = true))
                add(TicketElement.Columns(listOf("Articulo", "Cant", "Monto"), listOf(16, 6, 10), listOf(TicketAlign.LEFT, TicketAlign.CENTER, TicketAlign.RIGHT)))
                payload.productos.forEach { producto ->
                    add(TicketElement.Columns(
                        values = listOf(producto.nombre.take(16), producto.cantidad, producto.total),
                        widths = listOf(16, 6, 10),
                        aligns = listOf(TicketAlign.LEFT, TicketAlign.CENTER, TicketAlign.RIGHT),
                    ))
                }
                add(TicketElement.Divider)

                add(labelValue("Subtotal Items:", payload.subtotal))
                payload.montoExento?.takeIfNotBlank()?.let { add(labelValue("Monto Exento:", it)) }
                add(labelValue("Total impuestos:", payload.totalImpuesto))
                add(labelValue("TOTAL:", payload.total, bold = true))
                add(TicketElement.Divider)

                add(TicketElement.Text("Métodos de pago", TicketAlign.LEFT, bold = true))
                payload.pagos.forEach { pago -> add(labelValue(pago.metodo, pago.monto)) }
                payload.cambio?.takeIfNotBlank()?.let { add(labelValue("CAMBIO", it)) }

                add(TicketElement.Divider)
                LEGAL_TEXTS.forEach { add(TicketElement.Text(it, TicketAlign.CENTER)) }
                payload.qrUrl?.takeIfNotBlank()?.let {
                    add(TicketElement.Feed(1))
                    add(TicketElement.Qr(it))
                }
                payload.cufe?.takeIfNotBlank()?.let { add(wrapFiscalText("CUFE: $it")) }
                payload.fechaRecepcionDgi?.takeIfNotBlank()?.let { add(TicketElement.Text("Fecha recepción DGI: $it", TicketAlign.CENTER)) }
                payload.proveedorAutorizado?.takeIfNotBlank()?.let { add(TicketElement.Text("Proveedor autorizado: $it", TicketAlign.CENTER)) }
                add(TicketElement.Feed(4))
            }
        )
    }

    private fun labelValue(label: String, value: String, bold: Boolean = false): TicketElement.Columns {
        return TicketElement.Columns(
            values = listOf(label, value),
            widths = listOf(14, 18),
            aligns = listOf(TicketAlign.LEFT, TicketAlign.RIGHT),
        )
    }

    private fun wrapFiscalText(value: String): TicketElement.Text {
        return TicketElement.Text(value.chunked(32).joinToString("\n"), TicketAlign.CENTER)
    }

    private fun String.takeIfNotBlank(): String? = trim().takeIf { it.isNotBlank() }

    private companion object {
        val LEGAL_TEXTS = listOf(
            "Documento validado por proveedor autorizado calificado.",
            "Consulte su documento en el portal de la DGI."
        )
    }
}
