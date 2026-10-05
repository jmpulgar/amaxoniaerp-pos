package com.amaxonia.erp.data.printer.venezuela

import com.amaxonia.erp.data.remote.dto.ClientePrintDto
import com.amaxonia.erp.data.remote.dto.FacturaPrintPayloadDto
import com.amaxonia.erp.data.remote.dto.PagoPrintDto
import com.amaxonia.erp.domain.model.printer.TicketAlign
import com.amaxonia.erp.domain.model.printer.TicketDocument
import com.amaxonia.erp.domain.model.printer.TicketElement

class VenezuelaInvoiceTicketFormatter {
    fun format(payload: FacturaPrintPayloadDto): TicketDocument =
        TicketDocument(
            elements =
                buildList {
                    addHeader(payload)
                    addInvoiceMetadata(payload)
                    payload.cliente?.let { addClient(it) }
                    addProducts(payload)
                    addTotals(payload)
                    addIgtfIfApplicable(payload)
                    addMulticurrencyIfApplicable(payload)
                    addPayments(payload)
                    addDigitalFiscalFooter(payload)
                },
        )

    private fun MutableList<TicketElement>.addHeader(payload: FacturaPrintPayloadDto) {
        add(TicketElement.Text(TICKET_TITLE, TicketAlign.CENTER, bold = true))
        add(TicketElement.Text(payload.empresa.nombre.uppercase(), TicketAlign.CENTER, bold = true))
        payload.empresa.ruc
            ?.takeIfNotBlank()
            ?.let { add(TicketElement.Text("$RIF_LABEL: $it", TicketAlign.CENTER)) }
        payload.empresa.direccion
            ?.takeIfNotBlank()
            ?.let { add(TicketElement.Text(it, TicketAlign.CENTER)) }
        payload.empresa.telefono
            ?.takeIfNotBlank()
            ?.let { add(TicketElement.Text("Teléfono: $it", TicketAlign.CENTER)) }
        add(TicketElement.Feed(SINGLE_FEED))
    }

    private fun MutableList<TicketElement>.addInvoiceMetadata(payload: FacturaPrintPayloadDto) {
        addDateAndTime(payload.fecha)
        payload.empresa.tienda
            ?.takeIfNotBlank()
            ?.let { add(labelValue("Sucursal:", it)) }
        payload.empresa.caja
            ?.takeIfNotBlank()
            ?.let { add(labelValue("Caja:", it)) }
        add(labelValue("Factura:", payload.numeroFactura))
        payload.vendedor?.takeIfNotBlank()?.let { add(labelValue("Vendedor:", it)) }
        add(TicketElement.Divider)
    }

    private fun MutableList<TicketElement>.addDateAndTime(rawDate: String) {
        val parts = rawDate.trim().replace('T', ' ').split(' ', limit = 2)
        add(labelValue("Fecha:", parts.firstOrNull().orEmpty()))
        parts
            .getOrNull(1)
            ?.takeIfNotBlank()
            ?.let { add(labelValue("Hora:", it.take(TIME_TEXT_LENGTH))) }
    }

    private fun MutableList<TicketElement>.addClient(client: ClientePrintDto) {
        add(TicketElement.Text(CLIENT_HEADER, TicketAlign.LEFT, bold = true))
        add(labelValue("Nombre:", client.nombre))
        client.documento?.takeIfNotBlank()?.let { add(labelValue(CLIENT_DOC_LABEL, it)) }
        client.digitoVerificador?.takeIfNotBlank()?.let { add(labelValue("DV:", it)) }
        client.sucursal?.takeIfNotBlank()?.let { add(labelValue("Sucursal:", it)) }
        client.sucursalDireccion?.takeIfNotBlank()?.let { add(labelValue("Dir. sucursal:", it)) }
        add(TicketElement.Divider)
    }

    private fun MutableList<TicketElement>.addProducts(payload: FacturaPrintPayloadDto) {
        add(
            TicketElement.Columns(
                values = listOf("Código", "Cant", "Und", "Valor", "Importe"),
                widths = PRODUCT_COLUMN_WIDTHS,
                aligns = PRODUCT_COLUMN_ALIGNS,
            ),
        )
        payload.productos.forEach { product ->
            val taxLabel =
                product.tasaImpuesto
                    ?.takeIfNotBlank()
                    ?.let { rate -> "($TAX_ABBR $rate%)" }
                    .orEmpty()
            add(TicketElement.Text("${product.nombre} $taxLabel".trim(), TicketAlign.LEFT, bold = true))
            add(
                TicketElement.Columns(
                    values =
                        listOf(
                            product.codigo.orEmpty(),
                            product.cantidad,
                            product.unidad.orEmpty(),
                            product.precioUnitario,
                            product.total,
                        ),
                    widths = PRODUCT_COLUMN_WIDTHS,
                    aligns = PRODUCT_COLUMN_ALIGNS,
                ),
            )
            product.descuento.takeIfNotBlank()?.let { add(labelValue("PROMO:", it)) }
        }
        add(TicketElement.Divider)
    }

    private fun MutableList<TicketElement>.addTotals(payload: FacturaPrintPayloadDto) {
        add(totalsRow("Subtotal Items:", payload.subtotal))
        payload.montoExento?.takeIfNotBlank()?.let { add(totalsRow("Monto Exento:", it)) }
        add(totalsRow("Descuento:", payload.descuento ?: DEFAULT_DISCOUNT))
        add(totalsRow(TAX_ABBR_TOTAL_LABEL, payload.totalImpuesto))
        add(totalsRow("Total:", payload.total))
        add(TicketElement.Divider)
    }

    private fun totalsRow(
        label: String,
        value: String,
    ): TicketElement.TotalsRow =
        TicketElement.TotalsRow(
            label = label,
            value = value,
            labelWidth = TOTALS_LABEL_WIDTH,
            printerWidth = VENEZUELA_PRINTER_WIDTH,
        )

    private fun MutableList<TicketElement>.addIgtfIfApplicable(payload: FacturaPrintPayloadDto) {
        val monto = payload.igtfMonto?.takeIfNotBlank() ?: return
        if (monto.isZeroAmount()) return
        add(labelValue("$IGTF_LABEL ($monto):", payload.igtfBaseImponible.orEmpty()))
        payload.igtfTasa?.takeIfNotBlank()?.let { add(labelValue("Tasa IGTF:", it)) }
    }

    private fun MutableList<TicketElement>.addMulticurrencyIfApplicable(payload: FacturaPrintPayloadDto) {
        val tasa = payload.tasaCambioBs?.takeIfNotBlank() ?: return
        val totalDivisa = payload.totalDivisa?.takeIfNotBlank() ?: return
        val baseAbr = payload.abrMonedaBase ?: DEFAULT_BASE_CURRENCY_ABBR
        val secAbr = payload.abrMonedaSecundaria ?: DEFAULT_SECONDARY_CURRENCY_ABBR
        add(labelValue("Tasa ($secAbr→$baseAbr):", tasa))
        add(labelValue("Total $secAbr:", totalDivisa))
    }

    private fun MutableList<TicketElement>.addPayments(payload: FacturaPrintPayloadDto) {
        add(TicketElement.Text(PAYMENTS_HEADER, TicketAlign.LEFT, bold = true))
        add(TicketElement.Text(PAYMENT_DIVIDER, TicketAlign.LEFT))
        payload.pagos.forEach { payment -> add(formatPaymentLine(payment)) }
        add(TicketElement.Text(PAYMENT_DIVIDER, TicketAlign.LEFT))
        payload.cambio?.takeIfNotBlank()?.let { add(labelValue("CAMBIO", it)) }
    }

    private fun MutableList<TicketElement>.addDigitalFiscalFooter(payload: FacturaPrintPayloadDto) {
        val numDoc = payload.numeroDocumentoFiscal?.takeIfNotBlank()
        val numCtrl = payload.numeroControlThka?.takeIfNotBlank()
        if (numDoc == null && numCtrl == null) {
            add(TicketElement.Feed(FOOTER_FEED))
            return
        }
        add(TicketElement.Divider)
        add(TicketElement.Text(DIGITAL_HEADER, TicketAlign.CENTER, bold = true))
        numDoc?.let { add(labelValue(NUM_DOC_LABEL, it)) }
        numCtrl?.let { add(labelValue(NUM_CTRL_LABEL, it)) }
        add(TicketElement.Divider)
        add(TicketElement.Text(AUTHORIZED_BY_NOTE, TicketAlign.CENTER))
        add(TicketElement.Feed(FOOTER_FEED))
    }

    private fun formatPaymentLine(payment: PagoPrintDto): TicketElement.Columns = labelValue(payment.metodo.uppercase(), payment.monto)

    private fun labelValue(
        label: String,
        value: String,
    ): TicketElement.Columns =
        TicketElement.Columns(
            values = listOf(label, value),
            widths = listOf(LABEL_WIDTH, VALUE_WIDTH),
            aligns = listOf(TicketAlign.LEFT, TicketAlign.RIGHT),
        )

    private fun String.isZeroAmount(): Boolean {
        val normalized = trim().replace(',', '.')
        return normalized.toDoubleOrNull()?.let { it == 0.0 } ?: false
    }

    private fun String.takeIfNotBlank(): String? = trim().takeIf { it.isNotBlank() }

    private companion object {
        const val LABEL_WIDTH = 18
        const val VALUE_WIDTH = 22
        const val TIME_TEXT_LENGTH = 8
        const val VENEZUELA_PRINTER_WIDTH = 40
        const val TOTALS_LABEL_WIDTH = 20
        const val DEFAULT_DISCOUNT = "0.00"
        const val SINGLE_FEED = 1
        const val FOOTER_FEED = 4
        const val TICKET_TITLE = "FACTURA"
        const val RIF_LABEL = "RIF"
        const val TAX_ABBR = "IVA"
        const val TAX_ABBR_TOTAL_LABEL = "Total IVA:"
        const val CLIENT_HEADER = "Datos del Cliente"
        const val CLIENT_DOC_LABEL = "RIF/CI:"
        const val PAYMENTS_HEADER = "MÉTODOS DE PAGO:"
        const val PAYMENT_DIVIDER = "........................................"
        const val DEFAULT_BASE_CURRENCY_ABBR = "Bs"
        const val DEFAULT_SECONDARY_CURRENCY_ABBR = "USD"
        const val IGTF_LABEL = "Base IGTF"
        const val DIGITAL_HEADER = "FACTURA DIGITAL"
        const val NUM_DOC_LABEL = "Nro. documento:"
        const val NUM_CTRL_LABEL = "Nro. control:"
        const val AUTHORIZED_BY_NOTE = "Documento autorizado por The Factory HKA"

        val PRODUCT_COLUMN_WIDTHS = listOf(10, 5, 5, 8, 12)
        val PRODUCT_COLUMN_ALIGNS =
            listOf(
                TicketAlign.LEFT,
                TicketAlign.CENTER,
                TicketAlign.CENTER,
                TicketAlign.RIGHT,
                TicketAlign.RIGHT,
            )
    }
}
