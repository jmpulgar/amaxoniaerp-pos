package com.amaxonia.pos.data.printer.panama

import com.amaxonia.pos.domain.model.printer.TicketAlign
import com.amaxonia.pos.domain.model.printer.TicketDocument
import com.amaxonia.pos.domain.model.printer.TicketElement
import com.amaxonia.pos.domain.model.sales.ClientePrintDto
import com.amaxonia.pos.domain.model.sales.EmpresaPrintDto
import com.amaxonia.pos.domain.model.sales.FacturaPrintPayloadDto

class PanamaInvoiceTicketFormatter {
    fun format(payload: FacturaPrintPayloadDto): TicketDocument = format(payload, PANAMA_CODE)

    fun format(
        payload: FacturaPrintPayloadDto,
        countryCode: String,
    ): TicketDocument =
        TicketDocument(
            elements =
                buildList {
                    addHeader(payload.empresa, countryCode)
                    addInvoiceMetadata(payload, countryCode)
                    payload.cliente?.let { addClient(it, countryCode) }
                    addProducts(payload, countryCode)
                    addTotals(payload, countryCode)
                    addPayments(payload)
                    addFiscalFooter(payload, countryCode)
                },
        )

    private fun MutableList<TicketElement>.addHeader(
        company: EmpresaPrintDto,
        countryCode: String,
    ) {
        if (countryCode.equals(PANAMA_CODE, ignoreCase = true)) {
            add(TicketElement.Text("DGI", TicketAlign.CENTER, bold = true))
            add(TicketElement.Text("Comprobante Auxiliar de Factura", TicketAlign.CENTER))
            add(TicketElement.Text("Electrónica", TicketAlign.CENTER))
        } else {
            add(TicketElement.Text("COMPROBANTE DE VENTA", TicketAlign.CENTER, bold = true))
        }
        add(TicketElement.Text(company.nombre.uppercase(), TicketAlign.CENTER, bold = true))
        val taxIdLabel = if (countryCode.equals(VENEZUELA_CODE, ignoreCase = true)) "RIF" else "RUC"
        company.ruc?.takeIfNotBlank()?.let { add(TicketElement.Text("$taxIdLabel: $it", TicketAlign.CENTER)) }
        company.direccion?.takeIfNotBlank()?.let { add(TicketElement.Text(it, TicketAlign.CENTER)) }
        company.telefono?.takeIfNotBlank()?.let { add(TicketElement.Text("Teléfono: $it", TicketAlign.CENTER)) }
        if (countryCode.equals(PANAMA_CODE, ignoreCase = true)) {
            add(TicketElement.Text("Factura de Operación Interna", TicketAlign.CENTER))
        }
        add(TicketElement.Feed(SINGLE_FEED))
    }

    private fun MutableList<TicketElement>.addInvoiceMetadata(
        payload: FacturaPrintPayloadDto,
        countryCode: String,
    ) {
        addDateAndTime(payload.fecha)
        if (countryCode.equals(PANAMA_CODE, ignoreCase = true)) {
            val point = payload.puntoFacturacionFiscal?.takeIfNotBlank()
            val branchCode = payload.codigoSucursal?.takeIfNotBlank()
            if (point != null || branchCode != null) {
                add(
                    TicketElement.Columns(
                        values = listOf("Pto. Fact: ${point.orEmpty()}", "Suc.: ${branchCode.orEmpty()}"),
                        widths = listOf(HALF_LINE_WIDTH, HALF_LINE_WIDTH),
                        aligns = listOf(TicketAlign.LEFT, TicketAlign.RIGHT),
                    ),
                )
            }
        }
        payload.empresa.tienda
            ?.takeIfNotBlank()
            ?.let { add(labelValue("Sucursal:", it)) }
        payload.empresa.caja
            ?.takeIfNotBlank()
            ?.let { add(labelValue("Caja:", it)) }
        add(labelValue("Factura:", payload.numeroFactura))
        payload.numeroDocumentoFiscal
            ?.takeIfNotBlank()
            ?.let { add(labelValue("N. Doc Fiscal:", it)) }
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

    private fun MutableList<TicketElement>.addClient(
        client: ClientePrintDto,
        countryCode: String,
    ) {
        add(TicketElement.Text("Cliente Fiscal", TicketAlign.LEFT, bold = true))
        client.tipoReceptor?.takeIfNotBlank()?.let { add(labelValue("Tipo receptor:", it)) }
        add(labelValue("Nombre:", client.nombre))
        val documentLabel = if (countryCode.equals(VENEZUELA_CODE, ignoreCase = true)) "RIF/CI:" else "RUC:"
        client.documento?.takeIfNotBlank()?.let { add(labelValue(documentLabel, it)) }
        client.digitoVerificador?.takeIfNotBlank()?.let { add(labelValue("DV:", it)) }
        client.sucursal?.takeIfNotBlank()?.let { add(labelValue("Sucursal:", it)) }
        client.sucursalDireccion?.takeIfNotBlank()?.let { add(labelValue("Dir. sucursal:", it)) }
        add(TicketElement.Divider)
    }

    private fun MutableList<TicketElement>.addProducts(
        payload: FacturaPrintPayloadDto,
        countryCode: String,
    ) {
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
                    ?.let { rate ->
                        if (countryCode.equals(VENEZUELA_CODE, ignoreCase = true)) {
                            "(IVA $rate%)"
                        } else {
                            "($rate%)"
                        }
                    }.orEmpty()
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
            add(labelValue("PROMO:", product.descuento))
        }
        add(TicketElement.Divider)
    }

    private fun MutableList<TicketElement>.addTotals(
        payload: FacturaPrintPayloadDto,
        countryCode: String,
    ) {
        add(labelValue("Subtotal Items:", payload.subtotal))
        payload.montoExento?.takeIfNotBlank()?.let { add(labelValue("Monto Exento:", it)) }
        val taxLabel =
            if (countryCode.equals(VENEZUELA_CODE, ignoreCase = true)) {
                "Total IVA:"
            } else {
                "Total Impuesto:"
            }
        add(labelValue(taxLabel, payload.totalImpuesto))
        add(labelValue("Total:", payload.total))
        add(TicketElement.Divider)
    }

    private fun MutableList<TicketElement>.addPayments(payload: FacturaPrintPayloadDto) {
        add(TicketElement.Text("MÉTODOS DE PAGO:", TicketAlign.LEFT, bold = true))
        add(TicketElement.Text(PAYMENT_DIVIDER, TicketAlign.LEFT))
        payload.pagos.forEach { payment -> add(labelValue(payment.metodo.uppercase(), payment.monto)) }
        add(TicketElement.Text(PAYMENT_DIVIDER, TicketAlign.LEFT))
        payload.cambio?.takeIfNotBlank()?.let { add(labelValue("CAMBIO", it)) }
    }

    private fun MutableList<TicketElement>.addFiscalFooter(
        payload: FacturaPrintPayloadDto,
        countryCode: String,
    ) {
        if (!countryCode.equals(PANAMA_CODE, ignoreCase = true)) {
            add(TicketElement.Feed(FOOTER_FEED))
            return
        }

        add(TicketElement.Divider)
        add(TicketElement.Text("Consulte por la clave de acceso en:", TicketAlign.CENTER))
        add(TicketElement.Text(DGI_ACCESS_URL, TicketAlign.CENTER))
        payload.cufe?.takeIfNotBlank()?.let { add(wrapFiscalText(it)) }
        payload.qrUrl?.takeIfNotBlank()?.let {
            add(TicketElement.Feed(SINGLE_FEED))
            add(TicketElement.Qr(it, size = PANAMA_QR_SIZE))
        }
        payload.fechaRecepcionDgi?.takeIfNotBlank()?.let {
            add(TicketElement.Text("CAFE de emisión previa, transmisión a la", TicketAlign.CENTER))
            add(TicketElement.Text("DIRECCIÓN GENERAL DE INGRESOS hasta", TicketAlign.CENTER))
            add(TicketElement.Text(it, TicketAlign.CENTER))
        }
        payload.protocoloAutorizacion?.takeIfNotBlank()?.let {
            add(TicketElement.Text("Protocolo de autorización:", TicketAlign.CENTER))
            add(wrapFiscalText(it))
        }
        payload.proveedorAutorizado?.takeIfNotBlank()?.let {
            add(TicketElement.Divider)
            add(TicketElement.Text("Documento validado por $it,", TicketAlign.CENTER))
            add(TicketElement.Text("Proveedor Autorizado Calificado.", TicketAlign.CENTER))
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
        const val LABEL_WIDTH = 14
        const val VALUE_WIDTH = 18
        const val HALF_LINE_WIDTH = 16
        const val FISCAL_TEXT_WIDTH = 32
        const val TIME_TEXT_LENGTH = 8
        const val PANAMA_CODE = "PA"
        const val VENEZUELA_CODE = "VE"
        const val DGI_ACCESS_URL = "https://dgi-fep.mef.gob.pa/Consultas/FacturasPorCUFE"
        const val PAYMENT_DIVIDER = "................................"

        val PRODUCT_COLUMN_WIDTHS = listOf(8, 4, 5, 7, 8)
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

private fun String.takeIfNotBlank(): String? = trim().takeIf { it.isNotBlank() }
