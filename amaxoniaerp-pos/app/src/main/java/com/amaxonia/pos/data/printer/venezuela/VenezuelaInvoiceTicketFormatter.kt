package com.amaxonia.pos.data.printer.venezuela

import com.amaxonia.pos.domain.model.printer.TicketAlign
import com.amaxonia.pos.domain.model.printer.TicketDocument
import com.amaxonia.pos.domain.model.printer.TicketElement
import com.amaxonia.pos.domain.model.sales.FacturaPrintPayloadDto
import com.amaxonia.pos.domain.model.sales.PagoPrintDto

/**
 * Formatea el ticket térmico Sunmi (58mm, ancho **40** caracteres) para una
 * factura electrónica **Venezuela** emitida por The Factory HKA vía modo SUNMI_V2.
 *
 * # FASE 2 — Ticket digital Venezuela
 *
 * Ancho **40 columnas** conforme a la referencia funcional `ticket_builder.php`.
 * Las columnas de producto mantienen la estructura comercial existente:
 *
 * ```
 * Código(10) + Cant(5) + Und(5) + Precio(8) + Importe(12) = 40
 * ```
 *
 * ## Reglas contractuales (FASE 2 brief — corrección Puntos 1+2)
 *
 * 1. **Ancho de 40 columnas** — idéntico a la referencia `ticket_builder.php`.
 *    NO usar 32 (ese ancho es exclusivo del formatter Panamá).
 * 2. **Campos fiscales propios de Venezuela**, nunca reutilizados de PA:
 *    - `factura.numeroDocumentoFiscal`
 *    - `factura.numero_control_thka`
 *
 *    **NO CAFE, NO CUFE, NO QR, NO DGI, NO fechaRecepcionDGI, NO serie, NO
 *    imprentaDigital, NO nroProtocoloAutorizacion**: son conceptos exclusivos
 *    de Panamá y se omiten totalmente en VE.
 * 3. **Bloque fiscal digital** únicamente cuando los dos campos persistidos
 *    están presentes. Si falta alguno, NO se inventa.
 * 4. **IGTF** se imprime únicamente cuando `igtfMonto` está presente y > 0.
 * 5. **Multimoneda** se imprime únicamente cuando `tasaCambioBs` y `totalDivisa`
 *    están presentes.
 * 6. **Reimpresión/recarga** nunca debe llamar al PAC. El formatter lee solo
 *    lo que ya está persistido en `factura`.
 */
class VenezuelaInvoiceTicketFormatter {
    /**
     * Construye el [TicketDocument] a partir de un [FacturaPrintPayloadDto]
     * previamente cargado desde el backend (`/facturas/{id}/print-payload`).
     */
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

    // ───────────────────────────────────────────────────────────────────────
    // Secciones
    // ───────────────────────────────────────────────────────────────────────

    private fun MutableList<TicketElement>.addHeader(payload: FacturaPrintPayloadDto) {
        // Cabecera: en Venezuela NO se imprime "DGI Comprobante Auxiliar". El
        // documento es una factura electrónica emitida por The Factory HKA
        // para el SENIAT; el bloque fiscal digital aparece al final del ticket.
        add(TicketElement.Text(TICKET_TITLE, TicketAlign.CENTER, bold = true))
        add(TicketElement.Text(payload.empresa.nombre.uppercase(), TicketAlign.CENTER, bold = true))
        payload.empresa.ruc?.takeIfNotBlank()?.let { add(TicketElement.Text("$RIF_LABEL: $it", TicketAlign.CENTER)) }
        payload.empresa.direccion?.takeIfNotBlank()?.let { add(TicketElement.Text(it, TicketAlign.CENTER)) }
        payload.empresa.telefono?.takeIfNotBlank()?.let { add(TicketElement.Text("Teléfono: $it", TicketAlign.CENTER)) }
        add(TicketElement.Feed(SINGLE_FEED))
    }

    private fun MutableList<TicketElement>.addInvoiceMetadata(payload: FacturaPrintPayloadDto) {
        addDateAndTime(payload.fecha)
        payload.empresa.tienda?.takeIfNotBlank()?.let { add(labelValue("Sucursal:", it)) }
        payload.empresa.caja?.takeIfNotBlank()?.let { add(labelValue("Caja:", it)) }
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

    private fun MutableList<TicketElement>.addClient(client: com.amaxonia.pos.domain.model.sales.ClientePrintDto) {
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
        add(labelValue("Subtotal Items:", payload.subtotal))
        payload.montoExento?.takeIfNotBlank()?.let { add(labelValue("Monto Exento:", it)) }
        add(labelValue("$TAX_ABBR_TOTAL_LABEL", payload.totalImpuesto))
        add(labelValue("Total:", payload.total))
        add(TicketElement.Divider)
    }

    /**
     * IGTF: **solo** cuando el tenant lo aplica (pago en divisa) y el monto
     * cargado es distinto de cero. Si no aplica, no se imprime línea alguna.
     */
    private fun MutableList<TicketElement>.addIgtfIfApplicable(payload: FacturaPrintPayloadDto) {
        val monto = payload.igtfMonto?.takeIfNotBlank() ?: return
        if (monto.isZeroAmount()) return
        add(labelValue("$IGTF_LABEL ($monto):", payload.igtfBaseImponible.orEmpty()))
        payload.igtfTasa?.takeIfNotBlank()?.let { add(labelValue("Tasa IGTF:", it)) }
    }

    /**
     * Multimoneda: si el pago está en divisa y se dispone de la tasa de cambio
     * y el total convertido, se imprime la equivalencia en bolívares.
     */
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

    /**
     * **Pie fiscal digital** — Pie obligatorio de la factura electrónica VE.
     * Se imprime **únicamente** si ambos persistidos están presentes.
     *
     * En caso de reintentos/recargas no se llama al PAC: este formatter lee
     * exclusivamente lo que la BD ya persistió.
     */
    private fun MutableList<TicketElement>.addDigitalFiscalFooter(payload: FacturaPrintPayloadDto) {
        val numDoc = payload.numeroDocumentoFiscal?.takeIfNotBlank()
        val numCtrl = payload.numeroControlThka?.takeIfNotBlank()
        if (numDoc == null && numCtrl == null) {
            // No hay datos fiscales persistidos: no se inventa nada. El ticket
            // termina con el feed estándar.
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

    // ───────────────────────────────────────────────────────────────────────
    // Helpers
    // ───────────────────────────────────────────────────────────────────────

    private fun formatPaymentLine(payment: PagoPrintDto): TicketElement.Columns =
        labelValue(payment.metodo.uppercase(), payment.monto)

    private fun labelValue(
        label: String,
        value: String,
    ): TicketElement.Columns =
        TicketElement.Columns(
            values = listOf(label, value),
            widths = listOf(LABEL_WIDTH, VALUE_WIDTH),
            aligns = listOf(TicketAlign.LEFT, TicketAlign.RIGHT),
        )


    /**
     * Indica si un monto en string (p.ej. "0", "0.00", "0,00") representa cero.
     * Usado para omitir el IGTF cuando el pago no lo aplica.
     */
    private fun String.isZeroAmount(): Boolean {
        val normalized = trim().replace(',', '.')
        return normalized.toDoubleOrNull()?.let { it == 0.0 } ?: false
    }

    private fun String.takeIfNotBlank(): String? = trim().takeIf { it.isNotBlank() }

    private companion object {
        // ── Anchos (FASE 2 Punto 2: 40 cols — referencia ticket_builder.php)
        const val LABEL_WIDTH = 18
        const val VALUE_WIDTH = 22
        const val TIME_TEXT_LENGTH = 8
        const val DIVIDER_LENGTH = 40

        // ── Feeds ──────────────────────────────────────────────────────────
        const val SINGLE_FEED = 1
        const val FOOTER_FEED = 4

        // ── Venezuela: etiquetas ───────────────────────────────────────────
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

        // ── Pie fiscal digital Venezuela ───────────────────────────────────
        const val DIGITAL_HEADER = "FACTURA DIGITAL"
        const val NUM_DOC_LABEL = "Nro. documento:"
        const val NUM_CTRL_LABEL = "Nro. control:"
        const val AUTHORIZED_BY_NOTE = "Documento autorizado por The Factory HKA"

        // ── Columnas de producto (10+5+5+8+12 = 40) — ticket_builder.php
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
