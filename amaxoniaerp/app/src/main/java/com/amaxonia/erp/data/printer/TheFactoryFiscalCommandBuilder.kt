package com.amaxonia.erp.data.printer

import com.amaxonia.erp.data.remote.dto.FacturaPrintPayloadDto
import com.amaxonia.erp.data.remote.dto.ProductoPrintDto
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

class TheFactoryFiscalCommandBuilder {

    fun buildFiscalCommandsFromPayload(
        payload: FacturaPrintPayloadDto,
        brandReceiptName: String,
    ): List<String> {
        val invoice = sanitizeText(payload.numeroFactura, maxLength = INVOICE_NUMBER_MAX_LENGTH).ifBlank { "SINFACTURA" }
        val description = sanitizeText("VENTA $invoice", maxLength = DESCRIPTION_MAX_LENGTH)

        val lines = mutableListOf<String>()

        // 1. Customer identification
        val clientId = sanitizeText(payload.cliente?.documento.orEmpty(), maxLength = CUSTOMER_ID_MAX_LENGTH)
        if (clientId.isNotBlank()) {
            lines += "iR*$clientId"
        }

        // 2. Customer name
        val clientName = sanitizeText(payload.cliente?.nombre.orEmpty(), maxLength = DESCRIPTION_MAX_LENGTH)
        if (clientName.isNotBlank()) {
            lines += "iS*$clientName"
        }

        // 3. Comment lines
        lines += "@$brandReceiptName"
        lines += "@$description"

        // 4. Item lines
        if (payload.productos.isNotEmpty()) {
            payload.productos.forEach { product ->
                lines += buildProductLine(product)
            }
        } else {
            val totalAmount = payload.total.replace(',', '.').toDoubleOrNull() ?: MIN_PRINTABLE_AMOUNT
            val priceField = formatPrinterPrice(totalAmount)
            val quantityField = formatPrinterQty(1.0)
            lines += " $priceField$quantityField$description"
        }

        // 5. Subtotal
        lines += SUBTOTAL_COMMAND

        // 6. Payment close command
        val paymentMethod = payload.pagos.firstOrNull()?.metodo.orEmpty()
        lines += mapPaymentCommand(paymentMethod)

        return lines
    }

    private fun buildProductLine(product: ProductoPrintDto): String {
        val qty = product.cantidad.replace(',', '.').toDoubleOrNull() ?: 1.0
        val price = product.precioUnitario.replace(',', '.').toDoubleOrNull() ?: 0.0
        val taxRate = product.tasaImpuesto?.replace(',', '.')?.toDoubleOrNull() ?: 0.0

        val taxPrefix =
            when {
                taxRate <= 0.0 -> " " // Exento
                taxRate >= GENERAL_TAX_RATE_THRESHOLD -> "!" // General
                taxRate <= REDUCED_TAX_RATE -> "\"" // Reducido
                else -> "#" // Adicional
            }

        val priceField = formatPrinterPrice(price)
        val quantityField = formatPrinterQty(qty)
        val description = sanitizeText(product.nombre, maxLength = DESCRIPTION_MAX_LENGTH).ifBlank { "PRODUCTO" }

        return "$taxPrefix$priceField$quantityField$description"
    }

    fun mapPaymentCommand(formaPago: String): String {
        val normalized = formaPago.lowercase().trim()
        return when {
            isCashForm(normalized) -> CLOSE_DOCUMENT_COMMAND_101
            isDebitForm(normalized) -> CLOSE_DOCUMENT_COMMAND_102
            isCreditForm(normalized) -> CLOSE_DOCUMENT_COMMAND_103
            isOtherForm(normalized) -> CLOSE_DOCUMENT_COMMAND_104
            normalized.isBlank() -> CLOSE_DOCUMENT_COMMAND_101
            else -> CLOSE_DOCUMENT_COMMAND_199
        }
    }

    private fun isCashForm(normalized: String): Boolean = CASH_KEYWORDS.any(normalized::contains)
    private fun isDebitForm(normalized: String): Boolean = DEBIT_KEYWORDS.any(normalized::contains)
    private fun isCreditForm(normalized: String): Boolean = CREDIT_KEYWORDS.any(normalized::contains)
    private fun isOtherForm(normalized: String): Boolean = OTHER_KEYWORDS.any(normalized::contains)

    fun formatPrinterPrice(amount: Double): String =
        ((amount.coerceAtLeast(0.0) * PRICE_SCALE).roundToInt())
            .toString()
            .padStart(PRICE_FIELD_LENGTH, '0')

    fun formatPrinterQty(quantity: Double): String =
        ((quantity.coerceAtLeast(0.0) * QUANTITY_SCALE).roundToInt())
            .toString()
            .padStart(QUANTITY_FIELD_LENGTH, '0')

    fun sanitizeText(
        value: String,
        maxLength: Int,
    ): String =
        value
            .uppercase()
            .filter { it.isLetterOrDigit() || it == ' ' || it == '-' || it == '_' }
            .trim()
            .take(maxLength)

    fun parsePrinterState(rawState: String): PrinterStateSnapshot {
        val parts =
            rawState
                .split('\n')
                .map { it.trim() }
                .filter { it.isNotBlank() }

        if (parts.size >= S1_EXTENDED_FIELD_COUNT) {
            return PrinterStateSnapshot(
                registeredMachineNumber = parts.getOrNull(S1_EXTENDED_MACHINE_NUMBER_INDEX).orEmpty(),
                lastInvoiceNumber = parts.getOrNull(S1_INVOICE_NUMBER_INDEX)?.toIntOrNull() ?: 0,
                lastCreditNoteNumber = parts.getOrNull(S1_EXTENDED_CREDIT_NOTE_INDEX)?.toIntOrNull() ?: 0,
            )
        }

        return PrinterStateSnapshot(
            registeredMachineNumber = parts.getOrNull(S1_SHORT_MACHINE_NUMBER_INDEX).orEmpty(),
            lastInvoiceNumber = parts.getOrNull(S1_INVOICE_NUMBER_INDEX)?.toIntOrNull() ?: 0,
            lastCreditNoteNumber = parts.getOrNull(S1_SHORT_CREDIT_NOTE_INDEX)?.toIntOrNull() ?: 0,
        )
    }

    fun padFiscalNumber(number: Int): String? =
        if (number <= 0) null else number.toString().padStart(FISCAL_NUMBER_LENGTH, '0')

    companion object {
        const val SUBTOTAL_COMMAND = "3"
        const val CLOSE_DOCUMENT_COMMAND_101 = "101"
        const val CLOSE_DOCUMENT_COMMAND_102 = "102"
        const val CLOSE_DOCUMENT_COMMAND_103 = "103"
        const val CLOSE_DOCUMENT_COMMAND_104 = "104"
        const val CLOSE_DOCUMENT_COMMAND_199 = "199"
        const val INVOICE_NUMBER_MAX_LENGTH = 12
        const val DESCRIPTION_MAX_LENGTH = 30
        const val CUSTOMER_ID_MAX_LENGTH = 20
        const val PRINTER_SERIAL_MAX_LENGTH = 10
        const val FISCAL_NUMBER_LENGTH = 8
        const val MIN_PRINTABLE_AMOUNT = 0.01
        const val REDUCED_TAX_RATE = 8.0
        const val GENERAL_TAX_RATE_THRESHOLD = 20.0
        const val PRICE_SCALE = 100
        const val PRICE_FIELD_LENGTH = 10
        const val QUANTITY_SCALE = 1000
        const val QUANTITY_FIELD_LENGTH = 8
        const val S1_EXTENDED_FIELD_COUNT = 16
        const val S1_EXTENDED_MACHINE_NUMBER_INDEX = 13
        const val S1_SHORT_MACHINE_NUMBER_INDEX = 9
        const val S1_INVOICE_NUMBER_INDEX = 2
        const val S1_EXTENDED_CREDIT_NOTE_INDEX = 6
        const val S1_SHORT_CREDIT_NOTE_INDEX = 12

        val CASH_KEYWORDS = listOf("efectivo", "contado", "divisa", "cash")
        val DEBIT_KEYWORDS = listOf("punto de venta", "debito", "debit")
        val CREDIT_KEYWORDS = listOf("credito", "credit", "tarjeta")
        val OTHER_KEYWORDS =
            listOf(
                "transfer",
                "cheque",
                "deposito",
                "zelle",
                "pago movil",
                "yappy",
                "nequi",
            )
    }
}

data class PrinterStateSnapshot(
    val registeredMachineNumber: String,
    val lastInvoiceNumber: Int,
    val lastCreditNoteNumber: Int,
)
