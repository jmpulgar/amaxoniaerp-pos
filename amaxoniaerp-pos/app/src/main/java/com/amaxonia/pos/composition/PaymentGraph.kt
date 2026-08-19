package com.amaxonia.pos.composition

import com.amaxonia.pos.data.printer.panama.PanamaInvoiceTicketFormatter
import com.amaxonia.pos.data.printer.venezuela.VenezuelaInvoiceTicketFormatter
import com.amaxonia.pos.domain.model.payment.PaymentSuccessPayload
import com.amaxonia.pos.domain.model.printer.PrintResult
import com.amaxonia.pos.domain.model.printer.PrinterType
import com.amaxonia.pos.domain.usecase.payment.LoadPaymentContextUseCase
import com.amaxonia.pos.domain.usecase.payment.LoadPaymentCountryUseCase
import com.amaxonia.pos.ui.payment.PaymentSuccessViewModel
import com.amaxonia.pos.ui.payment.PaymentViewModel

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
            DependencyContainer.cartRepository.clearCart()
        }
        DependencyContainer.localStore.saveLastPaymentSuccess(payload)
    }

    /**
     * Reimpresión del recibo de una venta. Selector por país para SUNMI_V2
     * (VE → formatter Venezuela; PA/otros → formatter Panamá) e impresora
     * genérica para el resto. Suspend: los gateways de impresión son async.
     */
    suspend fun printSuccessReceipt(transactionId: String): Result<String> =
        if (DependencyContainer.localStore.readSelectedPrinterType() == PrinterType.SUNMI_V2) {
            printSunmiTicket(transactionId)
        } else {
            printGenericReceipt(transactionId)
        }

    private suspend fun printSunmiTicket(transactionId: String): Result<String> {
        val ticketPrinter = DependencyContainer.printerFactory.getActiveTicketPrinter()
        return when {
            ticketPrinter == null -> Result.failure(IllegalStateException("Impresora SUNMI no disponible"))
            else -> {
                val payload =
                    DependencyContainer.salesRepository.getPrintPayload(transactionId).getOrElse { error ->
                        return Result.failure(error)
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
        val transaction =
            DependencyContainer.transactionRepository.getTransactionById(transactionId).getOrElse { error ->
                return Result.failure(error)
            }
        val printer = DependencyContainer.printerFactory.getActivePrinter()
        return when {
            printer == null -> Result.failure(IllegalStateException("No hay impresora configurada"))
            else ->
                printer.printReceipt(transaction).fold(
                    onSuccess = { Result.success("Imprimiendo recibo...") },
                    onFailure = { Result.failure(it) },
                )
        }
    }
}
