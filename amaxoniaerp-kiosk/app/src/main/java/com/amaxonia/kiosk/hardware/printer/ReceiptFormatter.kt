package com.amaxonia.kiosk.hardware.printer

import com.amaxonia.kiosk.core.network.KioskPaymentResponse

object ReceiptFormatter {
    private const val LINE_WIDTH = 48

    fun formatCustomerReceipt(payment: KioskPaymentResponse): String {
        val sb = StringBuilder()
        val receipt = payment.receipt

        // Header
        sb.appendLine(center(receipt.companyName))
        if (!receipt.ruc.isNullOrBlank()) {
            val rucText = if (!receipt.dv.isNullOrBlank()) "RUC: ${receipt.ruc}-${receipt.dv}" else "RUC: ${receipt.ruc}"
            sb.appendLine(center(rucText))
        }
        if (!receipt.address.isNullOrBlank()) {
            sb.appendLine(center(receipt.address))
        }
        sb.appendLine("=".repeat(LINE_WIDTH))

        // Order Info
        sb.appendLine(center("*** ORDEN: ${receipt.orderNumber} ***"))
        sb.appendLine("MODALIDAD: ${receipt.diningMode}")
        if (!receipt.tableTent.isNullOrBlank()) {
            sb.appendLine("PORTAMESA / MESA: #${receipt.tableTent}")
        }
        sb.appendLine("CLIENTE: ${receipt.customerName}")
        sb.appendLine("ID / RUC: ${receipt.customerId}")
        sb.appendLine("FECHA: ${receipt.date}")
        sb.appendLine("-".repeat(LINE_WIDTH))

        // Items
        sb.appendLine(twoColumns("DESCRIPCION", "TOTAL"))
        sb.appendLine("-".repeat(LINE_WIDTH))
        receipt.lines.forEach { line ->
            val desc = "${line.qty}x ${line.description}"
            sb.appendLine(twoColumns(desc, line.total))
            line.modifiers.forEach { mod ->
                sb.appendLine("   + $mod")
            }
        }
        sb.appendLine("-".repeat(LINE_WIDTH))

        // Totals
        sb.appendLine(twoColumns("SUBTOTAL:", receipt.subtotal))
        sb.appendLine(twoColumns("IMPUESTO:", receipt.tax))
        sb.appendLine(twoColumns("TOTAL A PAGAR:", receipt.total))
        sb.appendLine("-".repeat(LINE_WIDTH))

        // Payment Info
        sb.appendLine("METODO DE PAGO: ${receipt.paymentBrand} ****${receipt.paymentLast4}")
        sb.appendLine("AUT: ${receipt.paymentAuthCode}   REF: ${receipt.paymentReference}")

        // Fiscal Data
        sb.appendLine("-".repeat(LINE_WIDTH))
        if (payment.invoice != null) {
            val invoice = payment.invoice
            sb.appendLine(center("FACTURA FISCAL: ${invoice.codFactura}"))
            if (!invoice.cufe.isNullOrBlank()) {
                sb.appendLine("CUFE: ${invoice.cufe}")
            }
            if (!invoice.fechaRecepcionDGI.isNullOrBlank()) {
                sb.appendLine("FECHA DGI: ${invoice.fechaRecepcionDGI}")
            }
            if (!invoice.numeroControl.isNullOrBlank()) {
                sb.appendLine("N° CONTROL: ${invoice.numeroControl}")
            }
        } else if (payment.status == "PAID_PENDING_INVOICE") {
            sb.appendLine(center("*** PAGO PENDIENTE DE FACTURAR ***"))
            sb.appendLine(center("Conserve este comprobante para su factura."))
        }

        sb.appendLine("=".repeat(LINE_WIDTH))
        sb.appendLine(center("¡Gracias por su visita!"))
        return sb.toString()
    }

    fun formatKitchenTicket(payment: KioskPaymentResponse): String {
        val sb = StringBuilder()
        val receipt = payment.receipt

        sb.appendLine("=".repeat(LINE_WIDTH))
        sb.appendLine(center("*** TICKET DE COCINA ***"))
        sb.appendLine("=".repeat(LINE_WIDTH))
        sb.appendLine(center("ORDEN: ${receipt.orderNumber}"))
        sb.appendLine("MODALIDAD: ${receipt.diningMode}")
        if (!receipt.tableTent.isNullOrBlank()) {
            sb.appendLine("MESA: #${receipt.tableTent}")
        }
        sb.appendLine("HORA: ${receipt.date}")
        sb.appendLine("-".repeat(LINE_WIDTH))

        receipt.lines.forEach { line ->
            sb.appendLine("${line.qty}x  ${line.description}")
            line.modifiers.forEach { mod ->
                sb.appendLine("   * $mod")
            }
        }

        sb.appendLine("=".repeat(LINE_WIDTH))
        return sb.toString()
    }

    fun center(
        text: String,
        width: Int = LINE_WIDTH,
    ): String {
        if (text.length >= width) return text.take(width)
        val left = (width - text.length) / 2
        val right = width - text.length - left
        return " ".repeat(left) + text + " ".repeat(right)
    }

    fun twoColumns(
        left: String,
        right: String,
        width: Int = LINE_WIDTH,
    ): String {
        val availableLeft = width - right.length - 1
        val truncatedLeft = if (left.length > availableLeft) left.take(availableLeft) else left
        val padding = width - truncatedLeft.length - right.length
        return truncatedLeft + " ".repeat(maxOf(1, padding)) + right
    }
}
