package com.amaxonia.pos.data.printer

import android.content.Context
import android.content.Intent
import com.amaxonia.pos.domain.model.Transaction
import com.amaxonia.pos.domain.repository.PrinterRepository
import com.thefactoryhka.hkacryptolib.MainFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import kotlin.math.roundToInt

class TheFactoryPrinterImpl(
    context: Context
) : PrinterRepository {

    private val appContext = context.applicationContext
    private val cryptography = MainFactory().createInstance(appContext)

    override suspend fun printReceipt(transaction: Transaction): Result<Boolean> {
        return withContext(Dispatchers.IO) {
            runCatching {
                val packageName = resolveInstalledPackage()
                    ?: throw IllegalStateException("No se encontro la app fiscal The Factory HKA instalada")

                val encryptedCommand = encryptCommand(buildCommandEnvelope(transaction))
                val printIntent = Intent().apply {
                    // La app fiscal espera que se invoque directamente su HomeActivity
                    // (com.thefactory.hkapos.ui.main.HomeActivity) y no solo el launcher.
                    setClassName(packageName, HOME_ACTIVITY_CLASS)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    putExtra(EXTRA_COMMAND_RAPID_PAY, encryptedCommand)
                    putExtra(EXTRA_COLOR_BACKGROUND_LOADING, COLOR_PRIMARY)
                    putExtra(EXTRA_COLOR_TEXT, COLOR_WHITE)
                    putExtra(EXTRA_MESSAGE_PROGRESS, PRINTING_MESSAGE)
                }

                withContext(Dispatchers.Main) {
                    appContext.startActivity(printIntent)
                }
                true
            }
        }
    }

    private fun buildCommandEnvelope(transaction: Transaction): String {
        val command = buildFiscalCommand(transaction)
        return JSONObject()
            .put("cmd", command)
            .toString()
    }

    /**
     * Builds the fiscal command string for The Factory HKA protocol.
     *
     * Protocol commands:
     * - "iF*{invoice}" — fiscal invoice header (sets invoice number)
     * - "i0{clientId}" — customer identification (RIF/cedula)
     * - "@{text}"      — free text / comment line (non-fiscal)
     * - " {amount}{qty}{description}" — item line (space prefix = taxable item)
     *     - amount: price in cents, padded to 8 digits
     *     - qty: quantity * 1000, padded to 10 digits
     *     - description: up to 30 chars
     * - "3"   — subtotal
     * - "101" — close with cash payment
     * - "102" — close with debit card payment
     * - "103" — close with credit card payment
     * - "104" — close with other payment method
     * - "199" — close without specifying payment method
     */
    private fun buildFiscalCommand(transaction: Transaction): String {
        val invoice = sanitizeText(transaction.invoiceNumber, maxLength = 12).ifBlank { "SINFACTURA" }
        val description = sanitizeText("VENTA $invoice", maxLength = 30)
        val amountField = formatAmount(transaction.amount.coerceAtLeast(0.01))
        val quantityField = "0000001000" // qty=1.000
        val itemLine = " $amountField$quantityField$description"

        val lines = mutableListOf<String>()

        // 1. Invoice header
        lines += "iF*$invoice"

        // 2. Customer identification (if available)
        val clientId = sanitizeText(transaction.clienteIdentificacion, maxLength = 20)
        if (clientId.isNotBlank()) {
            lines += "i0$clientId"
        }

        // 3. Comment lines
        lines += "@AMAXONIA POS"
        val clientName = sanitizeText(transaction.clienteNombre, maxLength = 30)
        if (clientName.isNotBlank()) {
            lines += "@CLIENTE $clientName"
        }

        // 4. Item line
        lines += itemLine

        // 5. Subtotal
        lines += "3"

        // 6. Payment close command
        lines += mapPaymentCommand(transaction.formaPago)

        return lines.joinToString(separator = "\n")
    }

    /**
     * Maps the app's formaPago string to the HKA fiscal payment close command.
     *
     * 101 = Cash (Efectivo / Contado)
     * 102 = Debit card
     * 103 = Credit card
     * 104 = Other (transfer, check, etc.)
     * 199 = Unspecified
     */
    private fun mapPaymentCommand(formaPago: String): String {
        val normalized = formaPago.lowercase().trim()
        return when {
            normalized.contains("efectivo") || normalized.contains("contado") -> "101"
            normalized.contains("debito") || normalized.contains("debit") -> "102"
            normalized.contains("credito") || normalized.contains("credit") || normalized.contains("tarjeta") -> "103"
            normalized.contains("transfer") || normalized.contains("cheque") || normalized.contains("deposito") -> "104"
            normalized.isBlank() -> "101" // Default to cash
            else -> "199" // Unspecified
        }
    }

    /**
     * Formats a monetary amount as cents padded to 8 digits.
     * e.g., 12.50 -> "00001250"
     */
    private fun formatAmount(amount: Double): String {
        return ((amount * 100).roundToInt())
            .toString()
            .padStart(8, '0')
    }

    private fun encryptCommand(jsonCommand: String): ByteArray {
        val response = cryptography.encryptString(jsonCommand)
        if (response.isError) {
            throw IllegalStateException(response.message ?: "No se pudo encriptar el comando para impresion")
        }
        return response.bytes ?: throw IllegalStateException("Respuesta de cifrado invalida")
    }

    private fun sanitizeText(value: String, maxLength: Int): String {
        return value
            .uppercase()
            .filter { it.isLetterOrDigit() || it == ' ' || it == '-' || it == '_' }
            .trim()
            .take(maxLength)
    }

    private fun resolveInstalledPackage(): String? {
        return PACKAGE_CANDIDATES.firstOrNull { packageName ->
            appContext.packageManager.getLaunchIntentForPackage(packageName) != null
        }
    }

    private companion object {
        val PACKAGE_CANDIDATES = listOf(
            "com.thefactory.hkapos.fiscal.demo",
            "com.thefactory.hkapos.fiscal",
            "com.thefactory.hkapos.fiscal.release",
            "com.thefactory.hkapos.fiscal.demo.demo"
        )

        const val HOME_ACTIVITY_CLASS = "com.thefactory.hkapos.ui.main.HomeActivity"

        const val EXTRA_COMMAND_RAPID_PAY = "commandRapidPay"
        const val EXTRA_COLOR_BACKGROUND_LOADING = "colorBackgroundLoading"
        const val EXTRA_COLOR_TEXT = "colorText"
        const val EXTRA_MESSAGE_PROGRESS = "messageRapidPay"

        const val COLOR_PRIMARY = "#1565C0"
        const val COLOR_WHITE = "#FFFFFFFF"
        const val PRINTING_MESSAGE = "Imprimiendo recibo..."
    }
}
