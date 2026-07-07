package com.amaxonia.pos.data.printer.panama

import com.amaxonia.pos.domain.model.caja.CierreCajaSummary
import com.amaxonia.pos.domain.model.printer.TicketAlign
import com.amaxonia.pos.domain.model.printer.TicketDocument
import com.amaxonia.pos.domain.model.printer.TicketElement
import java.util.Locale

class PanamaCashCloseTicketFormatter {
    fun format(payload: PanamaCashCloseTicketPayload): TicketDocument {
        return TicketDocument(
            elements = buildList {
                addCentered(payload.companyName.uppercase(), bold = true)
                payload.companyRuc.takeIfNotBlank()?.let { addCentered("RUC: $it") }
                payload.companyAddress.takeIfNotBlank()?.let { addCentered(it) }
                payload.companyPhone.takeIfNotBlank()?.let { addCentered("Tel: $it") }
                payload.sellerName.takeIfNotBlank()?.let { addCentered("Vendedor: $it") }
                payload.cashRegisterName.takeIfNotBlank()?.let { addCentered(it) }
                payload.branchName.takeIfNotBlank()?.let { addCentered(it) }
                add(TicketElement.Feed(1))

                addSectionTitle("RESUMEN CIERRE DE CAJA")
                paymentLabels.forEach { label ->
                    addMoney(label, payload.paymentAmounts[label].orZero())
                }
                add(TicketElement.Divider)
                addMoney("ENTRADAS", payload.summary.montoEfectivoEntrada)
                addMoney("SALIDAS", payload.summary.montoEfectivoSalida)
                addMoney("ABONOS GENERALES", payload.generalPayments)
                add(TicketElement.Divider)
                addMoney("TOTAL VENTAS", payload.summary.totalSales, bold = true)
                addMoney("CIERRE ESPERADO", payload.summary.expectedClose, bold = true)
                addMoney("MONTO CIERRE", payload.summary.montoCierre, bold = true)
                addMoney("DIFERENCIA", payload.summary.montoDiferencia, bold = true)

                addSectionTitle("PROMOCIONES VENDIDAS")
                if (payload.promotionLines.isEmpty()) {
                    add(TicketElement.Text("Sin promociones registradas", TicketAlign.LEFT))
                } else {
                    payload.promotionLines.forEach { line ->
                        add(
                            TicketElement.Columns(
                                values = listOf(line.promotionName.take(24), formatQty(line.soldTimes)),
                                widths = listOf(24, 8),
                                aligns = listOf(TicketAlign.LEFT, TicketAlign.RIGHT),
                            )
                        )
                    }
                }

                addSectionTitle("INVENTARIO DE CIERRE DE RUTA")
                if (payload.inventoryLines.isEmpty()) {
                    add(TicketElement.Text("Sin movimientos locales de inventario", TicketAlign.LEFT))
                } else {
                    payload.inventoryLines.forEach { line ->
                        add(TicketElement.Text("${line.productCode} - ${line.productName}".take(32), TicketAlign.LEFT, bold = true))
                        add(
                            TicketElement.Text(
                                "Cant. Inicial: ${formatQty(line.initialQuantity)}  |  Vendidos: ${formatQty(line.soldQuantity)}",
                                TicketAlign.LEFT
                            )
                        )
                        add(TicketElement.Text("Existencia Real: ${formatQty(line.realQuantity)}", TicketAlign.LEFT))
                        add(TicketElement.Feed(1))
                    }
                }

                add(TicketElement.Divider)
                addCentered("Codigo fiscal de cierre")
                add(TicketElement.Feed(1))
                add(TicketElement.Qr(payload.qrPayload, size = PANAMA_CLOSE_QR_SIZE))
                add(TicketElement.Feed(3))
            }
        )
    }

    private fun MutableList<TicketElement>.addSectionTitle(title: String) {
        add(TicketElement.Divider)
        addCentered(title, bold = true)
        add(TicketElement.Divider)
    }

    private fun MutableList<TicketElement>.addCentered(value: String, bold: Boolean = false) {
        add(TicketElement.Text(value, TicketAlign.CENTER, bold))
    }

    private fun MutableList<TicketElement>.addMoney(label: String, amount: Double, bold: Boolean = false) {
        add(
            TicketElement.Columns(
                values = listOf(label.take(20), formatMoney(amount)),
                widths = listOf(20, 12),
                aligns = listOf(TicketAlign.LEFT, TicketAlign.RIGHT),
            )
        )
    }

    private fun formatMoney(value: Double): String {
        return "$" + String.format(Locale.US, "%.2f", value)
    }

    private fun formatQty(value: Double): String {
        val whole = value.toLong()
        return if (value == whole.toDouble()) whole.toString() else String.format(Locale.US, "%.2f", value)
    }

    private fun String.takeIfNotBlank(): String? = trim().takeIf { it.isNotBlank() }

    private fun Double?.orZero(): Double = this ?: 0.0

    companion object {
        private const val PANAMA_CLOSE_QR_SIZE = 3

        val paymentLabels = listOf(
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

data class PanamaCashCloseTicketPayload(
    val companyName: String,
    val companyRuc: String,
    val companyAddress: String,
    val companyPhone: String,
    val sellerName: String,
    val cashRegisterName: String,
    val branchName: String,
    val summary: CierreCajaSummary,
    val paymentAmounts: Map<String, Double>,
    val generalPayments: Double,
    val promotionLines: List<CashClosePromotionLine>,
    val inventoryLines: List<CashCloseInventoryLine>,
    val qrPayload: String,
)

data class CashClosePromotionLine(
    val promotionCode: String,
    val promotionName: String,
    val soldTimes: Double,
)

data class CashCloseInventoryLine(
    val productCode: String,
    val productName: String,
    val initialQuantity: Double,
    val soldQuantity: Double,
    val realQuantity: Double,
)
