package com.amaxonia.pos.data.printer.panama

import com.amaxonia.pos.domain.model.printer.TicketAlign
import com.amaxonia.pos.domain.model.printer.TicketDocument
import com.amaxonia.pos.domain.model.printer.TicketElement
import com.amaxonia.pos.domain.model.sales.ClientePrintDto
import com.amaxonia.pos.domain.model.sales.EmpresaPrintDto
import com.amaxonia.pos.domain.model.sales.FacturaPrintPayloadDto

class PanamaInvoiceTicketFormatter {
    fun format(payload: FacturaPrintPayloadDto): TicketDocument =
        TicketDocument(
            elements =
                buildList {
                    addHeader(payload.empresa)
                    addInvoiceMetadata(payload)
                    payload.cliente?.let { addClient(it) }
                    addProducts(payload)
                    addTotals(payload)
                    addPayments(payload)
                    addFiscalFooter(payload)
                },
        )

    private fun MutableList<TicketElement>.addHeader(company: EmpresaPrintDto) {
        add(TicketElement.Text("Comprobante Auxiliar de", TicketAlign.CENTER))
        add(TicketElement.Text("Facturación Electrónica", TicketAlign.CENTER))
        add(TicketElement.Feed(SINGLE_FEED))
        add(TicketElement.Text(company.nombre.uppercase(), TicketAlign.CENTER, bold = true))
        company.ruc?.takeIfNotBlank()?.let { add(TicketElement.Text("RUC: $it", TicketAlign.CENTER)) }
        company.direccion?.takeIfNotBlank()?.let { add(TicketElement.Text(it, TicketAlign.CENTER)) }
        company.telefono?.takeIfNotBlank()?.let { add(TicketElement.Text("Tel: $it", TicketAlign.CENTER)) }
        add(TicketElement.Text("PANAMÁ", TicketAlign.CENTER))
        add(TicketElement.Divider)
    }

    private fun MutableList<TicketElement>.addInvoiceMetadata(payload: FacturaPrintPayloadDto) {
        payload.empresa.tienda
            ?.takeIfNotBlank()
            ?.let { add(labelValue("Tienda:", it)) }
        payload.empresa.caja
            ?.takeIfNotBlank()
            ?.let { add(labelValue("Caja:", it)) }
        add(labelValue("Fecha:", payload.fecha))
        add(labelValue("Factura:", payload.numeroFactura))
        payload.vendedor?.takeIfNotBlank()?.let { add(labelValue("Vendedor:", it)) }
        add(TicketElement.Divider)
    }

    private fun MutableList<TicketElement>.addClient(client: ClientePrintDto) {
        add(TicketElement.Text("Cliente Fiscal", TicketAlign.LEFT, bold = true))
        add(labelValue("Nombre:", client.nombre))
        client.documento?.takeIfNotBlank()?.let { add(labelValue("RUC:", it)) }
        client.sucursal?.takeIfNotBlank()?.let { add(labelValue("Sucursal:", it)) }
        client.sucursalDireccion?.takeIfNotBlank()?.let { add(labelValue("Dir. sucursal:", it)) }
        add(TicketElement.Divider)
    }

    private fun MutableList<TicketElement>.addProducts(payload: FacturaPrintPayloadDto) {
        add(TicketElement.Text("Productos", TicketAlign.LEFT, bold = true))
        add(
            TicketElement.Columns(
                values = listOf("Articulo", "Cant", "Monto"),
                widths = listOf(PRODUCT_NAME_WIDTH, PRODUCT_QUANTITY_WIDTH, PRODUCT_AMOUNT_WIDTH),
                aligns = listOf(TicketAlign.LEFT, TicketAlign.CENTER, TicketAlign.RIGHT),
            ),
        )
        payload.productos.forEach { product ->
            add(
                TicketElement.Columns(
                    values = listOf(product.nombre.take(PRODUCT_NAME_WIDTH), product.cantidad, product.total),
                    widths = listOf(PRODUCT_NAME_WIDTH, PRODUCT_QUANTITY_WIDTH, PRODUCT_AMOUNT_WIDTH),
                    aligns = listOf(TicketAlign.LEFT, TicketAlign.CENTER, TicketAlign.RIGHT),
                ),
            )
        }
        add(TicketElement.Divider)
    }

    private fun MutableList<TicketElement>.addTotals(payload: FacturaPrintPayloadDto) {
        add(labelValue("Subtotal Items:", payload.subtotal))
        payload.montoExento?.takeIfNotBlank()?.let { add(labelValue("Monto Exento:", it)) }
        add(labelValue("Total impuestos:", payload.totalImpuesto))
        add(labelValue("TOTAL:", payload.total))
        add(TicketElement.Divider)
    }

    private fun MutableList<TicketElement>.addPayments(payload: FacturaPrintPayloadDto) {
        add(TicketElement.Text("Métodos de pago", TicketAlign.LEFT, bold = true))
        payload.pagos.forEach { payment -> add(labelValue(payment.metodo, payment.monto)) }
        payload.cambio?.takeIfNotBlank()?.let { add(labelValue("CAMBIO", it)) }
    }

    private fun MutableList<TicketElement>.addFiscalFooter(payload: FacturaPrintPayloadDto) {
        add(TicketElement.Divider)
        LEGAL_TEXTS.forEach { add(TicketElement.Text(it, TicketAlign.CENTER)) }
        payload.qrUrl?.takeIfNotBlank()?.let {
            add(TicketElement.Feed(SINGLE_FEED))
            add(TicketElement.Qr(it, size = PANAMA_QR_SIZE))
        }
        payload.cufe?.takeIfNotBlank()?.let { add(wrapFiscalText("CUFE: $it")) }
        payload.fechaRecepcionDgi?.takeIfNotBlank()?.let {
            add(TicketElement.Text("Fecha recepción DGI: $it", TicketAlign.CENTER))
        }
        payload.proveedorAutorizado?.takeIfNotBlank()?.let {
            add(TicketElement.Text("Proveedor autorizado: $it", TicketAlign.CENTER))
        }
        add(TicketElement.Feed(FOOTER_FEED))
    }

    private fun labelValue(
        label: String,
        value: String,
    ): TicketElement.Columns =
        TicketElement.Columns(
            values = listOf(label, value),
            widths = listOf(LABEL_WIDTH, VALUE_WIDTH),
            aligns = listOf(TicketAlign.LEFT, TicketAlign.RIGHT),
        )

    private fun wrapFiscalText(value: String): TicketElement.Text =
        TicketElement.Text(value.chunked(FISCAL_TEXT_WIDTH).joinToString("\n"), TicketAlign.CENTER)

    private companion object {
        const val SINGLE_FEED = 1
        const val FOOTER_FEED = 4
        const val PANAMA_QR_SIZE = 3
        const val PRODUCT_NAME_WIDTH = 16
        const val PRODUCT_QUANTITY_WIDTH = 6
        const val PRODUCT_AMOUNT_WIDTH = 10
        const val LABEL_WIDTH = 14
        const val VALUE_WIDTH = 18
        const val FISCAL_TEXT_WIDTH = 32

        val LEGAL_TEXTS =
            listOf(
                "Documento validado por proveedor autorizado calificado.",
                "Consulte su documento en el portal de la DGI.",
            )
    }
}

private fun String.takeIfNotBlank(): String? = trim().takeIf { it.isNotBlank() }
