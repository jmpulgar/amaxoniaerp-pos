package com.amaxonia.pos.data.printer.panama

import com.amaxonia.pos.domain.model.creditnote.CreditNoteDetailDto
import com.amaxonia.pos.domain.model.printer.TicketAlign
import com.amaxonia.pos.domain.model.printer.TicketDocument
import com.amaxonia.pos.domain.model.printer.TicketElement
import com.amaxonia.pos.domain.model.sales.EmpresaPrintDto
import java.util.Locale

class PanamaCreditNoteTicketFormatter {
    fun format(
        detail: CreditNoteDetailDto,
        company: EmpresaPrintDto? = null,
        cajaName: String? = null,
    ): TicketDocument =
        TicketDocument(
            elements =
                buildList {
                    addHeader(company)
                    addCreditNoteMetadata(detail, cajaName)
                    addClient(detail)
                    addProducts(detail)
                    addTotals(detail)
                    addFiscalFooter(detail)
                },
        )

    private fun MutableList<TicketElement>.addHeader(company: EmpresaPrintDto?) {
        add(TicketElement.Text("DGI", TicketAlign.CENTER, bold = true))
        add(TicketElement.Text("Comprobante Auxiliar de Factura", TicketAlign.CENTER))
        add(TicketElement.Text("Electrónica", TicketAlign.CENTER))
        add(TicketElement.Text("NOTA DE CRÉDITO ELECTRÓNICA", TicketAlign.CENTER, bold = true))
        company?.nombre?.takeIfNotBlank()?.let {
            add(TicketElement.Text(it.uppercase(Locale.ROOT), TicketAlign.CENTER, bold = true))
        }
        company?.ruc?.takeIfNotBlank()?.let { add(TicketElement.Text("RUC: $it", TicketAlign.CENTER)) }
        company?.direccion?.takeIfNotBlank()?.let { add(TicketElement.Text(it, TicketAlign.CENTER)) }
        company?.telefono?.takeIfNotBlank()?.let { add(TicketElement.Text("Teléfono: $it", TicketAlign.CENTER)) }
        add(TicketElement.Feed(SINGLE_FEED))
    }

    private fun MutableList<TicketElement>.addCreditNoteMetadata(
        detail: CreditNoteDetailDto,
        cajaName: String?,
    ) {
        add(labelValue("Fecha:", detail.fecha))
        if (detail.fiscalNumber.isNotBlank()) {
            add(labelValue("N. Doc Fiscal:", detail.fiscalNumber))
        }
        add(labelValue("Nota Crédito:", detail.codigo))
        add(labelValue("Factura Afectada:", detail.facturaCodigo))
        detail.fiscalDocument?.originalFiscalNumber?.takeIfNotBlank()?.let {
            add(labelValue("N. Doc Factura:", it))
        }
        cajaName?.takeIfNotBlank()?.let { add(labelValue("Caja:", it)) }
        add(TicketElement.Divider)
    }

    private fun MutableList<TicketElement>.addClient(detail: CreditNoteDetailDto) {
        if (detail.clienteNombre.isNotBlank()) {
            add(TicketElement.Text("Cliente Fiscal", TicketAlign.LEFT, bold = true))
            add(labelValue("Nombre:", detail.clienteNombre))
            detail.clienteIdentificacion.takeIfNotBlank()?.let { add(labelValue("RUC/Cédula:", it)) }
            add(TicketElement.Divider)
        }
    }

    private fun MutableList<TicketElement>.addProducts(detail: CreditNoteDetailDto) {
        add(
            TicketElement.Columns(
                values = listOf("Descripción", "Cant", "Precio", "Total"),
                widths = PRODUCT_COLUMN_WIDTHS,
                aligns = PRODUCT_COLUMN_ALIGNS,
            ),
        )
        detail.lines.forEach { line ->
            val taxLabel = if (line.pIva > 0) " (${line.pIva.toInt()}%)" else ""
            add(TicketElement.Text("${line.descripcion}$taxLabel", TicketAlign.LEFT, bold = true))
            add(
                TicketElement.Columns(
                    values =
                        listOf(
                            line.codigo.ifBlank { "-" },
                            formatDecimals(line.cantidad, QUANTITY_DECIMALS),
                            formatDecimals(line.precioSinIva, AMOUNT_DECIMALS),
                            formatDecimals(line.totalConIva, AMOUNT_DECIMALS),
                        ),
                    widths = PRODUCT_COLUMN_WIDTHS,
                    aligns = PRODUCT_COLUMN_ALIGNS,
                ),
            )
        }
        add(TicketElement.Divider)
    }

    private fun MutableList<TicketElement>.addTotals(detail: CreditNoteDetailDto) {
        add(totalsRow("Subtotal:", formatDecimals(detail.subtotal, AMOUNT_DECIMALS)))
        add(totalsRow("Total Impuesto:", formatDecimals(detail.impuesto, AMOUNT_DECIMALS)))
        add(totalsRow("Total Devolución:", formatDecimals(detail.total, AMOUNT_DECIMALS)))
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
            printerWidth = PANAMA_PRINTER_WIDTH,
        )

    private fun MutableList<TicketElement>.addFiscalFooter(detail: CreditNoteDetailDto) {
        val cufe = detail.cufe.takeIfNotBlank()
        val qrUrl = detail.qr.takeIfNotBlank()
        val fechaDgi = detail.fechaRecepcionDGI.takeIfNotBlank()
        val protocolo = detail.nroProtocoloAutorizacion.takeIfNotBlank()

        if (listOfNotNull(cufe, qrUrl, fechaDgi, protocolo).isEmpty()) {
            add(TicketElement.Feed(FOOTER_FEED))
            return
        }

        add(TicketElement.Divider)
        add(TicketElement.Text("Consulte por la clave de acceso en:", TicketAlign.CENTER))
        add(TicketElement.Text(DGI_ACCESS_URL, TicketAlign.CENTER))
        cufe?.let { add(wrapFiscalText(it)) }
        qrUrl?.let {
            add(TicketElement.Feed(SINGLE_FEED))
            add(TicketElement.Qr(it, size = PANAMA_QR_SIZE))
        }
        fechaDgi?.let {
            add(TicketElement.Text("CAFE de emisión previa, transmisión a la", TicketAlign.CENTER))
            add(TicketElement.Text("DIRECCIÓN GENERAL DE INGRESOS hasta", TicketAlign.CENTER))
            add(TicketElement.Text(it, TicketAlign.CENTER))
        }
        protocolo?.let {
            add(TicketElement.Text("Protocolo de autorización:", TicketAlign.CENTER))
            add(wrapFiscalText(it))
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

    private fun formatDecimals(value: Double, decimals: Int): String =
        String.format(Locale.US, "%.${decimals}f", value)

    private companion object {
        const val SINGLE_FEED = 1
        const val FOOTER_FEED = 4
        const val PANAMA_QR_SIZE = 3
        const val LABEL_WIDTH = 14
        const val VALUE_WIDTH = 18
        const val FISCAL_TEXT_WIDTH = 32
        const val PANAMA_PRINTER_WIDTH = 32
        const val TOTALS_LABEL_WIDTH = 18
        const val DGI_ACCESS_URL = "https://dgi-fep.mef.gob.pa/Consultas/FacturasPorCUFE"
        const val QUANTITY_DECIMALS = 3
        const val AMOUNT_DECIMALS = 2

        val PRODUCT_COLUMN_WIDTHS = listOf(12, 5, 7, 8)
        val PRODUCT_COLUMN_ALIGNS =
            listOf(
                TicketAlign.LEFT,
                TicketAlign.CENTER,
                TicketAlign.RIGHT,
                TicketAlign.RIGHT,
            )
    }
}

private fun String.takeIfNotBlank(): String? = trim().takeIf { it.isNotBlank() }
