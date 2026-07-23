package com.amaxonia.pos.data.printer.panama

import com.amaxonia.pos.domain.model.caja.CashCloseTicketFormatter
import com.amaxonia.pos.domain.model.caja.CashCloseTicketPayload
import com.amaxonia.pos.domain.model.printer.TicketAlign
import com.amaxonia.pos.domain.model.printer.TicketDocument
import com.amaxonia.pos.domain.model.printer.TicketElement
import java.util.Locale

class PanamaCashCloseTicketFormatter : CashCloseTicketFormatter {
    override val paymentLabels: List<String> = PAYMENT_LABELS

    override fun format(payload: CashCloseTicketPayload): TicketDocument = format(payload, PANAMA_CODE)

    override fun format(
        payload: CashCloseTicketPayload,
        countryCode: String,
    ): TicketDocument =
        TicketDocument(
            elements =
                buildList {
                    addHeader(payload, countryCode)
                    addSummary(payload)
                    addPromotions(payload)
                    addInventory(payload)
                    addSales(payload)
                    addFooter(payload)
                },
        )

    private fun MutableList<TicketElement>.addHeader(
        payload: CashCloseTicketPayload,
        countryCode: String,
    ) {
        addCentered(payload.companyName.uppercase(), bold = true)
        val taxIdLabel = if (countryCode.equals(VENEZUELA_CODE, ignoreCase = true)) "RIF" else "RUC"
        payload.companyRuc.takeIfNotBlank()?.let { addCentered("$taxIdLabel: $it") }
        payload.companyAddress.takeIfNotBlank()?.let { addCentered(it) }
        payload.companyPhone.takeIfNotBlank()?.let { addCentered("Tel: $it") }
        payload.sellerName.takeIfNotBlank()?.let { addCentered("Vendedor: $it") }
        payload.cashRegisterName.takeIfNotBlank()?.let { addCentered(it) }
        payload.branchName.takeIfNotBlank()?.let { addCentered(it) }
        add(TicketElement.Feed(SINGLE_FEED))
    }

    private fun MutableList<TicketElement>.addSummary(payload: CashCloseTicketPayload) {
        addSectionTitle("RESUMEN CIERRE DE CAJA")
        paymentLabels.forEach { label ->
            addMoney(label, payload.paymentAmounts[label].orZero())
        }
        add(TicketElement.Divider)
        addMoney("ENTRADAS", payload.summary.montoEfectivoEntrada)
        addMoney("SALIDAS", payload.summary.montoEfectivoSalida)
        addMoney("ABONOS GENERALES", payload.generalPayments)
        add(TicketElement.Divider)
        addMoney("CIERRE ESPERADO", payload.summary.expectedClose)
        addMoney("MONTO CIERRE", payload.summary.montoCierre)
        addMoney("DIFERENCIA", payload.summary.montoDiferencia)
    }

    private fun MutableList<TicketElement>.addPromotions(payload: CashCloseTicketPayload) {
        addSectionTitle("PROMOCIONES VENDIDAS")
        if (payload.promotionLines.isEmpty()) {
            add(TicketElement.Text("Sin promociones registradas", TicketAlign.LEFT))
        } else {
            payload.promotionLines.forEach { line ->
                add(
                    TicketElement.Columns(
                        values = listOf(line.promotionName.take(PROMOTION_NAME_WIDTH), formatQty(line.soldTimes)),
                        widths = listOf(PROMOTION_NAME_WIDTH, PROMOTION_QUANTITY_WIDTH),
                        aligns = listOf(TicketAlign.LEFT, TicketAlign.RIGHT),
                    ),
                )
            }
        }
    }

    private fun MutableList<TicketElement>.addInventory(payload: CashCloseTicketPayload) {
        addSectionTitle("INVENTARIO / PRODUCTOS")
        if (payload.inventoryLines.isEmpty()) {
            add(TicketElement.Text("Sin productos vendidos en el periodo", TicketAlign.LEFT))
        } else {
            payload.inventoryLines.forEach { line ->
                val productLabel = "${line.productCode} - ${line.productName}".take(TICKET_LINE_WIDTH)
                add(TicketElement.Text(productLabel, TicketAlign.LEFT, bold = true))
                addQuantity("Stock inicial", line.initialQuantity)
                addQuantity("Productos vendidos", line.soldQuantity)
                addQuantity("Stock disponible", line.realQuantity)
                add(TicketElement.Feed(SINGLE_FEED))
            }
        }
    }

    private fun MutableList<TicketElement>.addSales(payload: CashCloseTicketPayload) {
        addSectionTitle("VENTAS")
        addMoney("VENTAS REALIZADAS", payload.summary.totalSales)
        add(
            TicketElement.Columns(
                values = listOf("TRANSACCIONES", payload.summary.transactionCount.toString()),
                widths = listOf(MONEY_LABEL_WIDTH, MONEY_VALUE_WIDTH),
                aligns = listOf(TicketAlign.LEFT, TicketAlign.RIGHT),
            ),
        )
    }

    private fun MutableList<TicketElement>.addFooter(payload: CashCloseTicketPayload) {
        add(TicketElement.Divider)
        addCentered("Codigo fiscal de cierre")
        add(TicketElement.Feed(SINGLE_FEED))
        add(TicketElement.Qr(payload.qrPayload, size = PANAMA_CLOSE_QR_SIZE))
        add(TicketElement.Feed(FOOTER_FEED))
    }

    private fun MutableList<TicketElement>.addSectionTitle(title: String) {
        add(TicketElement.Divider)
        addCentered(title, bold = true)
        add(TicketElement.Divider)
    }

    private fun MutableList<TicketElement>.addCentered(
        value: String,
        bold: Boolean = false,
    ) {
        add(TicketElement.Text(value, TicketAlign.CENTER, bold))
    }

    private fun MutableList<TicketElement>.addMoney(
        label: String,
        amount: Double,
    ) {
        add(
            TicketElement.Columns(
                values = listOf(label.take(MONEY_LABEL_WIDTH), formatMoney(amount)),
                widths = listOf(MONEY_LABEL_WIDTH, MONEY_VALUE_WIDTH),
                aligns = listOf(TicketAlign.LEFT, TicketAlign.RIGHT),
            ),
        )
    }

    private fun MutableList<TicketElement>.addQuantity(
        label: String,
        quantity: Double,
    ) {
        add(
            TicketElement.Columns(
                values = listOf(label.take(QUANTITY_LABEL_WIDTH), formatQty(quantity)),
                widths = listOf(QUANTITY_LABEL_WIDTH, QUANTITY_VALUE_WIDTH),
                aligns = listOf(TicketAlign.LEFT, TicketAlign.RIGHT),
            ),
        )
    }

    companion object {
        private const val SINGLE_FEED = 1
        private const val FOOTER_FEED = 3
        private const val PANAMA_CLOSE_QR_SIZE = 3
        private const val TICKET_LINE_WIDTH = 32
        private const val PROMOTION_NAME_WIDTH = 24
        private const val PROMOTION_QUANTITY_WIDTH = 8
        private const val MONEY_LABEL_WIDTH = 20
        private const val MONEY_VALUE_WIDTH = 12
        private const val QUANTITY_LABEL_WIDTH = 24
        private const val QUANTITY_VALUE_WIDTH = 8
        private const val PANAMA_CODE = "PA"
        private const val VENEZUELA_CODE = "VE"

        private val PAYMENT_LABELS =
            listOf(
                "EFECTIVO",
                "ACH / IBAN",
                "REFERENCIA",
                "DEPOSITO",
                "CUENTAS POR COBRAR",
                "NOTAS DE CREDITO / DEVOLUCIONES",
                "CERTIFICADO DE REGALO",
                "PUNTOS",
                "TARJETA DE DEBITO",
                "VISA",
                "MASTERCARD",
                "YAPPY",
                "ABONOS APLICADOS",
            )
    }
}

private fun formatMoney(value: Double): String = "$" + String.format(Locale.US, "%.2f", value)

private fun formatQty(value: Double): String {
    val whole = value.toLong()
    return if (value == whole.toDouble()) whole.toString() else String.format(Locale.US, "%.2f", value)
}

private fun String.takeIfNotBlank(): String? = trim().takeIf { it.isNotBlank() }

private fun Double?.orZero(): Double = this ?: 0.0
