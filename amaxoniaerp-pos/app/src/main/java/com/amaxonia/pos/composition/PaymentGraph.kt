package com.amaxonia.pos.composition

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.amaxonia.pos.data.local.readActiveCajaForToday
import com.amaxonia.pos.data.local.readCompanySession
import com.amaxonia.pos.data.local.readNoPrinterPdfFormat
import com.amaxonia.pos.data.local.readSelectedPrinterType
import com.amaxonia.pos.data.local.saveLastPaymentSuccess
import com.amaxonia.pos.data.printer.LocalInvoicePrintPayloadMapper
import com.amaxonia.pos.data.printer.panama.PanamaInvoiceTicketFormatter
import com.amaxonia.pos.data.printer.pdf.TicketPdfGenerator
import com.amaxonia.pos.data.printer.sunmi.SunmiDeviceDetector
import com.amaxonia.pos.data.printer.venezuela.VenezuelaInvoiceTicketFormatter
import com.amaxonia.pos.domain.model.payment.PaymentSuccessPayload
import com.amaxonia.pos.domain.model.printer.NoPrinterPdfFormat
import com.amaxonia.pos.domain.model.printer.PrintResult
import com.amaxonia.pos.domain.model.printer.PrinterType
import com.amaxonia.pos.domain.usecase.payment.LoadPaymentContextUseCase
import com.amaxonia.pos.domain.usecase.payment.LoadPaymentCountryUseCase
import com.amaxonia.pos.ui.payment.PaymentSuccessViewModel
import com.amaxonia.pos.ui.payment.PaymentViewModel
import java.io.File

/**
 * Grafo del feature pago (TASK-051/052). La arquitectura de Payment está
 * cerrada: aquí sólo se mueve la construcción y la orquestación de
 * infraestructura (limpieza post-pago y reimpresión) fuera de la navegación.
 */
object PaymentGraph {
    fun paymentViewModel(): PaymentViewModel =
        PaymentViewModel(
            loadPaymentContext =
                LoadPaymentContextUseCase(
                    DependencyContainer.cajaRepository,
                    DependencyContainer.formaPagoRepository,
                ),
            loadPaymentCountry = LoadPaymentCountryUseCase(DependencyContainer.localStore),
            validatePayment = DependencyContainer.validatePaymentUseCase,
            buildPaymentDetails = DependencyContainer.buildPaymentDetailsUseCase,
            paymentOperation = DependencyContainer.paymentOperation,
            selectedClient = DependencyContainer.cartRepository.selectedClient,
            tableAccountPaymentReader = DependencyContainer.tableAccountPaymentHolder,
            cartFinancialSnapshot = DependencyContainer.cartRepository.financialSnapshot,
            onPaymentProcessingChanged = { isProcessing, message ->
                DependencyContainer.customerDisplayManager.setPaymentProcessing(isProcessing, message)
            },
        )

    fun paymentSuccessViewModel(transactionId: String): PaymentSuccessViewModel =
        PaymentSuccessViewModel(
            paymentSuccessRepository = DependencyContainer.posConfigurationRepository,
            salesRepository = DependencyContainer.salesRepository,
            transactionId = transactionId,
        )

    /** Limpieza post-pago: cuenta de mesa, mesa seleccionada y payload del éxito. */
    suspend fun handlePaymentSuccess(payload: PaymentSuccessPayload) {
        DependencyContainer.tableAccountPaymentHolder.clear()
        if (payload.tableSessionClosed) {
            DependencyContainer.selectedTableHolder.clear()
        }
        DependencyContainer.cartRepository.clearCart()
        DependencyContainer.localStore.saveLastPaymentSuccess(payload)
        DependencyContainer.customerDisplayManager.showSaleSuccess()
    }

    /**
     * Reimpresión del recibo de una venta. Selector por país para SUNMI_V2
     * (VE → formatter Venezuela; PA/otros → formatter Panamá), impresora
     * fiscal The Factory HKA, y descarga en PDF cuando no hay impresora configurada.
     */
    suspend fun printSuccessReceipt(
        context: Context,
        transactionId: String,
    ): Result<String> {
        val selectedPrinter = DependencyContainer.localStore.readSelectedPrinterType()
        val isTicketDevice =
            selectedPrinter in setOf(PrinterType.SUNMI_V2, PrinterType.IMIN_SWIFT) ||
                SunmiDeviceDetector.isSunmiDevice() ||
                com.amaxonia.pos.data.printer.imin.IminDeviceDetector
                    .isIminDevice()

        return when {
            isTicketDevice -> printSunmiTicket(transactionId)
            selectedPrinter == PrinterType.THE_FACTORY_HKA -> {
                val printer = DependencyContainer.printerFactory.getActivePrinter()
                if (printer != null) {
                    printGenericReceipt(transactionId)
                } else {
                    handleNoPrinterReceipt(context, transactionId)
                }
            }
            else -> handleNoPrinterReceipt(context, transactionId)
        }
    }

    private suspend fun handleNoPrinterReceipt(
        context: Context,
        transactionId: String,
    ): Result<String> {
        val format = DependencyContainer.localStore.readNoPrinterPdfFormat()
        return when (format) {
            NoPrinterPdfFormat.FACTURA_CARTA -> downloadAndOpenReceiptPdf(context, transactionId)
            NoPrinterPdfFormat.TICKET_TERMICO -> generateAndOpenTicketPdf(context, transactionId)
        }
    }

    private suspend fun generateAndOpenTicketPdf(
        context: Context,
        transactionId: String,
    ): Result<String> {
        val payloadResult =
            if (transactionId.isNotBlank() && !transactionId.startsWith("OFF-")) {
                DependencyContainer.salesRepository.getPrintPayload(transactionId)
            } else {
                Result.failure(IllegalStateException("Factura offline"))
            }
        val payload =
            payloadResult.getOrElse { error ->
                val company = DependencyContainer.localStore.readCompanySession()?.company
                val caja = DependencyContainer.localStore.readActiveCajaForToday()
                val transaction = DependencyContainer.transactionRepository.getTransactionById(transactionId).getOrNull()
                if (transaction != null) {
                    LocalInvoicePrintPayloadMapper.fromTransaction(transaction, company, caja)
                } else {
                    return Result.failure(error)
                }
            }
        val countryCode =
            DependencyContainer.localStore
                .readSelectedCountry()
                ?.code
                .orEmpty()
        val ticket =
            when (countryCode.uppercase()) {
                "VE" -> VenezuelaInvoiceTicketFormatter().format(payload)
                else -> PanamaInvoiceTicketFormatter().format(payload, countryCode)
            }

        return runCatching {
            val pdfBytes = TicketPdfGenerator.generatePdf(ticket)
            val invoiceNumber = payload.numeroFactura.ifBlank { transactionId }
            val cleanName = invoiceNumber.replace('/', '_').replace('\\', '_')
            val pdfFile =
                File(context.cacheDir, "ticket_$cleanName.pdf").apply {
                    writeBytes(pdfBytes)
                }
            val uri =
                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    pdfFile,
                )
            val intent =
                Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "application/pdf")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            context.startActivity(intent)
            "Ticket en PDF generado correctamente"
        }.recoverCatching { ex ->
            if (ex is ActivityNotFoundException) {
                "Ticket guardado en PDF (no se encontró visor de PDF)."
            } else {
                throw ex
            }
        }
    }

    suspend fun printSuccessReceipt(transactionId: String): Result<String> =
        if (DependencyContainer.localStore.readSelectedPrinterType() in setOf(PrinterType.SUNMI_V2, PrinterType.IMIN_SWIFT) ||
            SunmiDeviceDetector.isSunmiDevice() ||
            com.amaxonia.pos.data.printer.imin.IminDeviceDetector
                .isIminDevice()
        ) {
            printSunmiTicket(transactionId)
        } else {
            printGenericReceipt(transactionId)
        }

    private suspend fun downloadAndOpenReceiptPdf(
        context: Context,
        transactionId: String,
    ): Result<String> {
        if (transactionId.isBlank() || transactionId.startsWith("OFF-")) {
            return Result.failure(
                IllegalStateException("No hay impresora configurada. El PDF no está disponible para facturas no sincronizadas."),
            )
        }

        val pdfResult = DependencyContainer.salesRepository.getInvoicePdf(transactionId)
        return pdfResult.fold(
            onSuccess = { bytes ->
                runCatching {
                    val invoiceNumber =
                        DependencyContainer.transactionRepository
                            .getTransactionById(
                                transactionId,
                            ).getOrNull()
                            ?.invoiceNumber
                    val cleanName =
                        (invoiceNumber?.takeIf { it.isNotBlank() } ?: transactionId)
                            .replace('/', '_')
                            .replace('\\', '_')
                    val pdfFile =
                        File(context.cacheDir, "factura_$cleanName.pdf").apply {
                            writeBytes(bytes)
                        }
                    val uri =
                        FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.fileprovider",
                            pdfFile,
                        )
                    val intent =
                        Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(uri, "application/pdf")
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                    context.startActivity(intent)
                    Result.success("Sin impresora configurada. Abriendo PDF de la factura...")
                }.getOrElse { ex ->
                    if (ex is ActivityNotFoundException) {
                        Result.success("Sin impresora configurada. Factura guardada en PDF (no se encontró visor de PDF).")
                    } else {
                        Result.failure(ex)
                    }
                }
            },
            onFailure = { error ->
                Result.failure(
                    IllegalStateException(
                        "Sin impresora configurada. No se pudo descargar el PDF: ${error.message ?: "error desconocido"}",
                    ),
                )
            },
        )
    }

    private suspend fun printSunmiTicket(transactionId: String): Result<String> {
        val ticketPrinter = DependencyContainer.printerFactory.getActiveTicketPrinter()
        return when {
            ticketPrinter == null -> Result.failure(IllegalStateException("Impresora SUNMI no disponible"))
            else -> {
                val payloadResult =
                    if (transactionId.isNotBlank() && !transactionId.startsWith("OFF-")) {
                        DependencyContainer.salesRepository.getPrintPayload(transactionId)
                    } else {
                        Result.failure(IllegalStateException("Factura offline"))
                    }
                val payload =
                    payloadResult.getOrElse { error ->
                        val company = DependencyContainer.localStore.readCompanySession()?.company
                        val caja = DependencyContainer.localStore.readActiveCajaForToday()
                        val transaction = DependencyContainer.transactionRepository.getTransactionById(transactionId).getOrNull()
                        if (transaction != null) {
                            LocalInvoicePrintPayloadMapper.fromTransaction(transaction, company, caja)
                        } else {
                            return Result.failure(error)
                        }
                    }
                val countryCode =
                    DependencyContainer.localStore
                        .readSelectedCountry()
                        ?.code
                        .orEmpty()
                val ticket =
                    when (countryCode.uppercase()) {
                        "VE" -> VenezuelaInvoiceTicketFormatter().format(payload)
                        else -> PanamaInvoiceTicketFormatter().format(payload, countryCode)
                    }
                when (val printResult = ticketPrinter.printTicket(ticket)) {
                    PrintResult.Success -> Result.success("Ticket SUNMI enviado correctamente")
                    is PrintResult.Error -> Result.failure(IllegalStateException(printResult.message, printResult.cause))
                }
            }
        }
    }

    private suspend fun printGenericReceipt(transactionId: String): Result<String> {
        val printer =
            DependencyContainer.printerFactory.getActivePrinter()
                ?: return Result.failure(IllegalStateException("No hay impresora configurada"))
        val transaction =
            DependencyContainer.transactionRepository.getTransactionById(transactionId).getOrElse { error ->
                return Result.failure(error)
            }
        return printer.printReceipt(transaction).fold(
            onSuccess = { Result.success("Imprimiendo recibo...") },
            onFailure = { Result.failure(it) },
        )
    }
}
