package com.amaxonia.pos.domain.usecase.payment

import com.amaxonia.pos.domain.model.Client
import com.amaxonia.pos.domain.model.Transaction
import com.amaxonia.pos.domain.model.TransactionFiscalItem
import com.amaxonia.pos.domain.model.TransactionStatus
import com.amaxonia.pos.domain.model.money.Money
import com.amaxonia.pos.domain.model.payment.FormaPago
import com.amaxonia.pos.domain.model.payment.GatewayLaunchPayload
import com.amaxonia.pos.domain.model.payment.GatewayPaymentRequest
import com.amaxonia.pos.domain.model.payment.PaymentSuccessPayload
import com.amaxonia.pos.domain.model.sales.ProcessSaleResponseDto
import com.amaxonia.pos.domain.system.AppClock
import com.amaxonia.pos.domain.system.IdGenerator
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class ExecutePaymentFlowInput(
    val countryCode: String,
    val paymentDetails: PaymentDetails,
    val totalAmount: Money,
    val tenderedAmount: Money,
    val changeDue: Double,
    val totalAmountBs: Double,
    val changeDueBs: Double,
    val exchangeRate: Double,
    val secondaryCurrency: String,
    val isMultiCurrency: Boolean,
    val availableMethods: List<FormaPago>,
)

sealed interface PaymentFlowEvent {
    data class Progress(
        val message: String,
    ) : PaymentFlowEvent

    data class LaunchGateway(
        val payload: GatewayLaunchPayload,
    ) : PaymentFlowEvent

    data object FiscalConfirmationFailed : PaymentFlowEvent
}

sealed interface PaymentFlowResult {
    data class Success(
        val payload: PaymentSuccessPayload,
        val receiptPrintMessage: String?,
    ) : PaymentFlowResult

    data class Failure(
        val message: String,
    ) : PaymentFlowResult
}

class PaymentExecutionOperations(
    val queueOfflineInvoice: QueueOfflineInvoiceUseCase,
    val printInvoice: PrintInvoiceUseCase,
    val confirmFiscalDocument: ConfirmFiscalDocumentUseCase,
    val executeGatewayPayment: ExecuteGatewayPaymentUseCase,
    val handlePaymentFailure: HandlePaymentFailureUseCase,
)

fun interface PaymentFlowExecutor {
    suspend operator fun invoke(
        input: ExecutePaymentFlowInput,
        onEvent: suspend (PaymentFlowEvent) -> Unit,
    ): PaymentFlowResult
}

class ExecutePaymentFlowUseCase(
    private val operations: PaymentExecutionOperations,
    private val prepareSale: PrepareSaleUseCase,
    private val completeSale: CompletePaymentSaleUseCase,
) : PaymentFlowExecutor {
    override suspend operator fun invoke(
        input: ExecutePaymentFlowInput,
        onEvent: suspend (PaymentFlowEvent) -> Unit,
    ): PaymentFlowResult {
        val configurationError =
            operations.executeGatewayPayment
                .validateConfiguration(input.paymentDetails.transactionMethods)
                .exceptionOrNull()
        return if (configurationError != null) {
            operations.failure(configurationError, "Configuración de pasarela inválida")
        } else {
            when (val preparation = prepareSale(input)) {
                is SalePreparation.Failure -> PaymentFlowResult.Failure(preparation.message)
                is SalePreparation.Success -> processPreparedSale(input, preparation.sale, onEvent)
            }
        }
    }

    private suspend fun processPreparedSale(
        input: ExecutePaymentFlowInput,
        sale: PreparedSale,
        onEvent: suspend (PaymentFlowEvent) -> Unit,
    ): PaymentFlowResult {
        val gatewayFailure = executeGatewayIfRequired(input, sale, onEvent)
        return if (gatewayFailure != null) {
            gatewayFailure
        } else {
            onEvent(PaymentFlowEvent.Progress("Generando factura..."))
            completeSale(input, sale, onEvent)
        }
    }

    private suspend fun executeGatewayIfRequired(
        input: ExecutePaymentFlowInput,
        sale: PreparedSale,
        onEvent: suspend (PaymentFlowEvent) -> Unit,
    ): PaymentFlowResult.Failure? {
        if (input.countryCode != VENEZUELA_CODE) return null
        val result =
            operations.executeGatewayPayment(
                request =
                    GatewayPaymentRequest(
                        methods = sale.details.selectedMethods,
                        customerIdentifier = sale.client.ruc.ifBlank { sale.client.cedula.ifBlank { sale.client.id } },
                        exchangeRate = sale.financials.exchangeRate,
                        isMultiCurrency = sale.financials.isMultiCurrency,
                    ),
                launch = { payload ->
                    onEvent(PaymentFlowEvent.Progress("Esperando respuesta de pasarela de pago..."))
                    onEvent(PaymentFlowEvent.LaunchGateway(payload))
                },
            )
        return result.exceptionOrNull()?.let { error ->
            operations.failure(error, "No se pudo completar el cobro en The Factory")
        }
    }
}

class CompletePaymentSaleUseCase(
    private val repositories: PaymentFlowRepositories,
    private val operations: PaymentExecutionOperations,
    private val clock: AppClock,
    private val idGenerator: IdGenerator,
) {
    internal suspend operator fun invoke(
        input: ExecutePaymentFlowInput,
        sale: PreparedSale,
        onEvent: suspend (PaymentFlowEvent) -> Unit,
    ): PaymentFlowResult =
        if (sale.isOnline) {
            processOnline(input, sale, onEvent)
        } else {
            processOffline(input, sale)
        }

    private suspend fun processOffline(
        input: ExecutePaymentFlowInput,
        sale: PreparedSale,
    ): PaymentFlowResult {
        val queued =
            operations.queueOfflineInvoice(
                countryCode = input.countryCode,
                request = sale.request,
                total = sale.financials.total,
                clientName = sale.client.paymentDisplayName(),
            )
        val transaction =
            createTransaction(
                id = queued.id,
                invoiceNumber = queued.localInvoiceNumber,
                status = TransactionStatus.PENDING,
                input = input,
                sale = sale,
            )
        repositories.state.transaction.saveTransaction(transaction).exceptionOrNull()?.let { error ->
            return operations.failure(error, "La factura quedo pendiente, pero no se pudo guardar la transaccion local")
        }
        return PaymentFlowResult.Success(
            payload =
                successPayload(
                    input = input,
                    sale = sale,
                    completion =
                        PaymentCompletion(
                            invoiceCode = queued.localInvoiceNumber,
                            transactionId = queued.id,
                            printMessage = "Factura pendiente de envio",
                        ),
                ),
            receiptPrintMessage = "Factura guardada offline. Se reenviara al recuperar internet.",
        )
    }

    private suspend fun processOnline(
        input: ExecutePaymentFlowInput,
        sale: PreparedSale,
        onEvent: suspend (PaymentFlowEvent) -> Unit,
    ): PaymentFlowResult =
        repositories.runtime.sales.processSale(sale.request).fold(
            onFailure = { error -> operations.failure(error, "No se pudo procesar la venta. Intenta nuevamente") },
            onSuccess = { response -> processAcceptedOnlineSale(input, sale, response, onEvent) },
        )

    private suspend fun processAcceptedOnlineSale(
        input: ExecutePaymentFlowInput,
        sale: PreparedSale,
        response: ProcessSaleResponseDto,
        onEvent: suspend (PaymentFlowEvent) -> Unit,
    ): PaymentFlowResult {
        val transaction =
            createTransaction(
                id = idGenerator.nextId(),
                invoiceNumber = response.codFactura,
                status = TransactionStatus.PAID,
                input = input,
                sale = sale,
            )
        val saveError =
            repositories.state.transaction
                .saveTransaction(transaction)
                .exceptionOrNull()
        return if (saveError != null) {
            operations.failure(saveError, "La venta se proceso, pero no se pudo guardar la transaccion local")
        } else {
            finishOnlineSale(input, sale, response, transaction, onEvent)
        }
    }

    private suspend fun finishOnlineSale(
        input: ExecutePaymentFlowInput,
        sale: PreparedSale,
        response: ProcessSaleResponseDto,
        transaction: Transaction,
        onEvent: suspend (PaymentFlowEvent) -> Unit,
    ): PaymentFlowResult.Success {
        onEvent(PaymentFlowEvent.Progress("Imprimiendo factura..."))
        val printResult = operations.printInvoice(input.countryCode, transaction, response.idFactura)
        val fiscalNumber = printResult?.fiscalNumber?.takeIf(String::isNotBlank).orEmpty()
        if (input.countryCode == VENEZUELA_CODE && fiscalNumber.isNotBlank()) {
            operations
                .confirmFiscalDocument(
                    invoiceId = response.idFactura,
                    fiscalNumber = fiscalNumber,
                    printerSerial = printResult?.printerSerial.orEmpty(),
                ).onFailure { onEvent(PaymentFlowEvent.FiscalConfirmationFailed) }
        }
        return PaymentFlowResult.Success(
            payload =
                successPayload(
                    input = input,
                    sale = sale,
                    completion =
                        PaymentCompletion(
                            invoiceCode = response.codFactura,
                            transactionId = response.idFactura,
                            printMessage = printResult?.displayMessage,
                            fiscalNumber = fiscalNumber,
                            fiscalError = response.feError,
                        ),
                ),
            receiptPrintMessage = printResult?.displayMessage,
        )
    }

    private fun createTransaction(
        id: String,
        invoiceNumber: String,
        status: TransactionStatus,
        input: ExecutePaymentFlowInput,
        sale: PreparedSale,
    ): Transaction {
        val now = LocalDateTime.ofInstant(clock.now(), ZoneId.systemDefault())
        return Transaction(
            id = id,
            invoiceNumber = invoiceNumber,
            time = now.format(TIME_FORMATTER),
            amount = input.totalAmount.toDouble(),
            currency = DEFAULT_CURRENCY,
            fiscalAmount =
                fiscalPrintAmount(
                    sale.financials.total,
                    sale.financials.exchangeRate,
                    sale.financials.isMultiCurrency,
                    input.countryCode,
                ),
            status = status,
            dateHeader = now.format(DATE_HEADER_FORMATTER),
            clienteNombre = sale.client.paymentFullName(),
            clienteIdentificacion = sale.client.ruc.ifBlank { sale.client.cedula },
            formaPago = sale.details.methodsLabel,
            paymentMethods = sale.details.selectedMethods,
            fiscalItems =
                sale.details.items.map { item ->
                    TransactionFiscalItem(
                        description = item.itemDescripcion,
                        quantity = item.itemCantidadTotal,
                        unitPriceWithoutTax =
                            fiscalPrintAmount(
                                item.itemPrecioSinIva,
                                sale.financials.exchangeRate,
                                sale.financials.isMultiCurrency,
                                input.countryCode,
                            ),
                        iva = item.itemPIva,
                    )
                },
        )
    }

    private fun successPayload(
        input: ExecutePaymentFlowInput,
        sale: PreparedSale,
        completion: PaymentCompletion,
    ): PaymentSuccessPayload =
        PaymentSuccessPayload(
            changeDue = input.changeDue,
            paymentMethodsLabel = sale.details.methodsLabel,
            codFactura = completion.invoiceCode,
            transactionId = completion.transactionId,
            receiptPrintMessage = completion.printMessage,
            fiscalNumber = completion.fiscalNumber,
            totalBs = input.totalAmountBs,
            changeDueBs = input.changeDueBs,
            tasa = input.exchangeRate,
            abrMonedaSecundaria = input.secondaryCurrency,
            isMultiCurrency = input.isMultiCurrency,
            feError = completion.fiscalError,
        )

    private fun fiscalPrintAmount(
        amount: Double,
        rate: Double,
        isMultiCurrency: Boolean,
        countryCode: String,
    ): Double {
        val fiscalAmount =
            if (countryCode != VENEZUELA_CODE) {
                amount
            } else {
                val normalizedAmount = amount.coerceAtLeast(0.0)
                if (isMultiCurrency) normalizedAmount * checkNotNull(rate.takeIf { it > 0.0 }) else normalizedAmount
            }
        return Money.fromDouble(fiscalAmount).toDouble()
    }
}

private fun PaymentExecutionOperations.failure(
    error: Throwable,
    fallback: String,
): PaymentFlowResult.Failure = PaymentFlowResult.Failure(handlePaymentFailure(error, fallback).message)

private fun Client.paymentFullName(): String = "$firstName $lastName".trim()

private fun Client.paymentDisplayName(): String = paymentFullName().ifBlank { "CONSUMIDOR FINAL" }

private data class PaymentCompletion(
    val invoiceCode: String,
    val transactionId: String,
    val printMessage: String?,
    val fiscalNumber: String = "",
    val fiscalError: String? = null,
)

private const val VENEZUELA_CODE = "VE"
private const val DEFAULT_CURRENCY = "USD"
private val TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("hh:mm a")
private val DATE_HEADER_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE, dd MMMM yyyy")
