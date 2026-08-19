package com.amaxonia.pos.data.printer

import com.amaxonia.pos.domain.model.Transaction
import com.amaxonia.pos.domain.model.TransactionFiscalItem
import com.amaxonia.pos.domain.model.TransactionPaymentMethod
import com.amaxonia.pos.domain.model.creditnote.CreditNoteFiscalDocumentDto
import com.amaxonia.pos.domain.model.creditnote.CreditNoteFiscalLineDto
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Constructor de comandos del protocolo fiscal The Factory HKA (TASK-074).
 * Lógica pura, sin sockets ni Android: congelada por characterization tests
 * (`TheFactoryFiscalCommandBuilderTest`).
 *
 * Protocol commands:
 * - "iR*{clientId}" — customer tax id / identification
 * - "iS*{clientName}" — customer name
 * - "@{text}"      — free text / comment line (non-fiscal)
 * - "{taxPrefix}{price}{qty}{description}" — item line
 *     - price: monto con 2 decimales implícitos (x100), padded a 10 dígitos
 *     - qty: cantidad con 3 decimales implícitos (x1000), padded a 8 dígitos
 *     - description: hasta 30 chars
 *     - taxPrefix: ' ' exento, '!' IVA general, '"' IVA reducido, '#' IVA adicional
 * - "3"   — subtotal
 * - "101" — cierre pago efectivo / "102" débito / "103" crédito / "104" otros / "199" sin especificar
 */
class TheFactoryFiscalCommandBuilder {
    fun buildFiscalCommands(
        transaction: Transaction,
        brandReceiptName: String,
    ): List<String> {
        val invoice = sanitizeText(transaction.invoiceNumber, maxLength = INVOICE_NUMBER_MAX_LENGTH).ifBlank { "SINFACTURA" }
        val description = sanitizeText("VENTA $invoice", maxLength = DESCRIPTION_MAX_LENGTH)
        val fiscalItems =
            transaction.fiscalItems
                .filter { it.quantity > 0.0 && it.unitPriceWithoutTax > 0.0 }

        val lines = mutableListOf<String>()

        // 1. Customer identification (if available)
        val clientId = sanitizeText(transaction.clienteIdentificacion, maxLength = CUSTOMER_ID_MAX_LENGTH)
        if (clientId.isNotBlank()) {
            lines += "iR*$clientId"
        }

        // 2. Customer name (if available)
        val clientName = sanitizeText(transaction.clienteNombre, maxLength = DESCRIPTION_MAX_LENGTH)
        if (clientName.isNotBlank()) {
            lines += "iS*$clientName"
        }

        // 3. Comment lines
        lines += "@$brandReceiptName"
        lines += "@$description"

        // 4. Item lines (using item IVA code and unit amount WITHOUT tax)
        if (fiscalItems.isNotEmpty()) {
            fiscalItems.forEach { item ->
                lines += buildFiscalItemLine(item)
            }
        } else {
            // Fallback for legacy transactions that do not have item details.
            val fallbackAmount = (transaction.fiscalAmount ?: transaction.amount).coerceAtLeast(MIN_PRINTABLE_AMOUNT)
            val priceField = formatPrinterPrice(fallbackAmount)
            val quantityField = formatPrinterQty(1.0)
            lines += " $priceField$quantityField$description"
        }

        // 5. Subtotal
        lines += SUBTOTAL_COMMAND

        // 6. Payment close command
        lines += resolvePaymentCommand(transaction.paymentMethods, transaction.formaPago)

        return lines
    }

    fun buildCreditNoteCommands(
        document: CreditNoteFiscalDocumentDto,
        printerSerial: String,
    ): List<String> {
        val lines = mutableListOf<String>()
        val fiscalRef = document.originalFiscalNumber.trim()
        if (fiscalRef.isBlank()) {
            error(
                "La factura original no tiene número de documento fiscal en el sistema. " +
                    "El comando iF* de la nota de crédito debe usar el número fiscal de la factura impresa " +
                    "(no el código interno tipo 018-00015). " +
                    "Confirma que la venta quedó con número fiscal guardado en el ERP o vuelve a emitir/consultar la factura.",
            )
        }
        val referenceNumber = normalizeOriginalFiscalReference(fiscalRef)
        val referenceDate = normalizePrinterDate(document.originalInvoiceDate.ifBlank { document.date })
        val normalizedPrinterSerial = sanitizeText(printerSerial, maxLength = PRINTER_SERIAL_MAX_LENGTH)
        val taxCodes = resolveCreditNoteTaxCodes(document.lines)

        // Reference data for the affected invoice first (NC fiscal flow).
        lines += "iF*$referenceNumber"
        lines += "iD*$referenceDate"
        lines += "iI*$normalizedPrinterSerial"

        val customerId = sanitizeText(document.customerIdentifier, maxLength = CUSTOMER_ID_MAX_LENGTH)
        if (customerId.isNotBlank()) {
            lines += "iR*$customerId"
        }

        val customerName = sanitizeText(document.customerName, maxLength = DESCRIPTION_MAX_LENGTH)
        if (customerName.isNotBlank()) {
            lines += "iS*$customerName"
        }

        val address = sanitizeText(document.customerAddress, maxLength = DESCRIPTION_MAX_LENGTH)
        if (address.isNotBlank()) {
            lines += "i01$address"
        }

        val phone = sanitizeText(document.customerPhone, maxLength = DESCRIPTION_MAX_LENGTH)
        if (phone.isNotBlank()) {
            lines += "i02$phone"
        }

        val comment = sanitizeText(document.comment.ifBlank { "NC ${document.creditNoteCode}" }, maxLength = DESCRIPTION_MAX_LENGTH)
        lines += "A$comment"

        document.lines.forEach { line ->
            val taxCode = taxCodes[line.taxRate] ?: EXEMPT_TAX_CODE
            lines += buildCreditNoteItemLine(line, taxCode)
        }

        lines += SUBTOTAL_COMMAND
        lines += CLOSE_DOCUMENT_COMMAND_199
        return lines
    }

    /** Mapea la forma de pago de la app al comando de cierre fiscal HKA. */
    fun mapPaymentCommand(formaPago: String): String {
        val normalized = formaPago.lowercase().trim()
        return when {
            isCashForm(normalized) -> CLOSE_DOCUMENT_COMMAND_101
            isDebitForm(normalized) -> CLOSE_DOCUMENT_COMMAND_102
            isCreditForm(normalized) -> CLOSE_DOCUMENT_COMMAND_103
            isOtherForm(normalized) -> CLOSE_DOCUMENT_COMMAND_104
            normalized.isBlank() -> CLOSE_DOCUMENT_COMMAND_101 // Default to cash
            else -> CLOSE_DOCUMENT_COMMAND_199 // Unspecified
        }
    }

    /**
     * Parsea la respuesta `S1` del estado de la impresora. Formato extendido
     * (>= 16 campos) y formato corto con posiciones históricas distintas.
     */
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

    fun padFiscalNumber(value: Int): String? =
        value
            .takeIf { it > 0 }
            ?.toString()
            ?.padStart(FISCAL_NUMBER_LENGTH, '0')

    private fun buildFiscalItemLine(item: TransactionFiscalItem): String {
        val taxPrefix = resolveFiscalTaxPrefix(item.iva)
        val priceField = formatPrinterPrice(item.unitPriceWithoutTax.coerceAtLeast(MIN_PRINTABLE_AMOUNT))
        val quantityField = formatPrinterQty(item.quantity)
        val description = sanitizeText(item.description, maxLength = DESCRIPTION_MAX_LENGTH).ifBlank { "ITEM" }
        return "$taxPrefix$priceField$quantityField$description"
    }

    private fun resolveFiscalTaxPrefix(iva: Double): Char {
        val normalizedIva = iva.coerceAtLeast(0.0)
        return when {
            normalizedIva <= 0.0 -> ' ' // Exento
            abs(normalizedIva - REDUCED_TAX_RATE) <= TAX_RATE_TOLERANCE -> '"' // IVA reducido
            abs(normalizedIva - ADDITIONAL_TAX_RATE) <= TAX_RATE_TOLERANCE -> '#' // IVA adicional
            else -> '!' // IVA general (16% y fallback para otras tasas > 0)
        }
    }

    private fun buildCreditNoteItemLine(
        line: CreditNoteFiscalLineDto,
        taxCode: Int,
    ): String {
        val quantity = line.quantity.coerceAtLeast(MIN_CREDIT_NOTE_QUANTITY)
        // Backward compatibility when backend payload does not include unitPriceWithoutTax yet.
        val unitAmount =
            if (line.unitPriceWithoutTax > 0.0) {
                line.unitPriceWithoutTax
            } else {
                val taxDivisor = 1 + (line.taxRate.coerceAtLeast(0.0) / PERCENT_DIVISOR)
                (line.totalWithTax / taxDivisor / quantity).coerceAtLeast(MIN_PRINTABLE_AMOUNT)
            }
        val priceField = formatPrinterPrice(unitAmount)
        val quantityField = formatPrinterQty(quantity)
        val description = sanitizeText(line.description, maxLength = DESCRIPTION_MAX_LENGTH).ifBlank { "DEVOLUCION" }
        return "d$taxCode$priceField$quantityField$description"
    }

    private fun resolveCreditNoteTaxCodes(lines: List<CreditNoteFiscalLineDto>): Map<Double, Int> =
        lines
            .map { it.taxRate }
            .distinct()
            .associateWith { taxRate ->
                when {
                    taxRate <= 0.0 -> EXEMPT_TAX_CODE
                    taxRate >= GENERAL_TAX_RATE_THRESHOLD -> GENERAL_TAX_CODE
                    taxRate <= REDUCED_TAX_RATE -> REDUCED_TAX_CODE
                    else -> STANDARD_TAX_CODE
                }
            }

    private fun resolvePaymentCommand(
        paymentMethods: List<TransactionPaymentMethod>,
        formaPago: String,
    ): String {
        paymentMethods
            .maxByOrNull { it.amount }
            ?.fiscalCode
            ?.takeIf { it.isNotBlank() }
            ?.let { return it }

        return mapPaymentCommand(formaPago)
    }

    private fun isCashForm(normalized: String): Boolean = CASH_KEYWORDS.any(normalized::contains)

    private fun isDebitForm(normalized: String): Boolean = DEBIT_KEYWORDS.any(normalized::contains)

    private fun isCreditForm(normalized: String): Boolean = CREDIT_KEYWORDS.any(normalized::contains)

    private fun isOtherForm(normalized: String): Boolean = OTHER_KEYWORDS.any(normalized::contains)

    private fun formatPrinterPrice(amount: Double): String =
        ((amount.coerceAtLeast(0.0) * PRICE_SCALE).roundToInt())
            .toString()
            .padStart(PRICE_FIELD_LENGTH, '0')

    private fun formatPrinterQty(quantity: Double): String =
        ((quantity.coerceAtLeast(0.0) * QUANTITY_SCALE).roundToInt())
            .toString()
            .padStart(QUANTITY_FIELD_LENGTH, '0')

    private fun normalizeOriginalFiscalReference(value: String): String {
        val digits = value.filter(Char::isDigit)
        return when {
            digits.length >= LONG_FISCAL_REF_LENGTH -> digits.takeLast(LONG_FISCAL_REF_LENGTH)
            digits.length >= SHORT_FISCAL_REF_LENGTH -> digits.takeLast(SHORT_FISCAL_REF_LENGTH)
            else -> digits.padStart(SHORT_FISCAL_REF_LENGTH, '0')
        }
    }

    private fun normalizePrinterDate(value: String): String =
        runCatching {
            LocalDate.parse(value).format(PRINTER_DATE_FORMATTER)
        }.getOrDefault(value.takeIf { it.matches(PRINTER_DATE_REGEX) } ?: LocalDate.now().format(PRINTER_DATE_FORMATTER))

    fun sanitizeText(
        value: String,
        maxLength: Int,
    ): String =
        value
            .uppercase()
            .filter { it.isLetterOrDigit() || it == ' ' || it == '-' || it == '_' }
            .trim()
            .take(maxLength)

    private companion object {
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
        const val MIN_CREDIT_NOTE_QUANTITY = 0.001
        const val REDUCED_TAX_RATE = 8.0
        const val ADDITIONAL_TAX_RATE = 31.0
        const val GENERAL_TAX_RATE_THRESHOLD = 20.0
        const val TAX_RATE_TOLERANCE = 0.01
        const val PERCENT_DIVISOR = 100.0
        const val PRICE_SCALE = 100
        const val PRICE_FIELD_LENGTH = 10
        const val QUANTITY_SCALE = 1000
        const val QUANTITY_FIELD_LENGTH = 8
        const val LONG_FISCAL_REF_LENGTH = 11
        const val SHORT_FISCAL_REF_LENGTH = 8
        const val S1_EXTENDED_FIELD_COUNT = 16
        const val S1_EXTENDED_MACHINE_NUMBER_INDEX = 13
        const val S1_SHORT_MACHINE_NUMBER_INDEX = 9
        const val S1_INVOICE_NUMBER_INDEX = 2
        const val S1_EXTENDED_CREDIT_NOTE_INDEX = 6
        const val S1_SHORT_CREDIT_NOTE_INDEX = 12
        const val EXEMPT_TAX_CODE = 0
        const val REDUCED_TAX_CODE = 2
        const val STANDARD_TAX_CODE = 1
        const val GENERAL_TAX_CODE = 3
        val CASH_KEYWORDS = listOf("efectivo", "contado", "divisa")
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
                "solutech",
                "sunmi",
            )
        val PRINTER_DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
        val PRINTER_DATE_REGEX = Regex("\\d{2}/\\d{2}/\\d{4}")
    }
}

/** Estado relevante extraído de la respuesta `S1` de la impresora. */
data class PrinterStateSnapshot(
    val registeredMachineNumber: String,
    val lastInvoiceNumber: Int,
    val lastCreditNoteNumber: Int,
)
