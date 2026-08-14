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
import com.amaxonia.pos.domain.model.printer.PrinterType
import com.amaxonia.pos.domain.model.sales.CuentaMesaVentaDto
import com.amaxonia.pos.domain.model.sales.ProcessSaleResponseDto
import com.amaxonia.pos.domain.model.sales.SaleItemDto
import com.amaxonia.pos.domain.repository.PaymentSessionReader
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
    /**
     * Canonical `idFactura` persisted in a PRIOR attempt of the same
     * operation (e.g. after a timeout/crash). When non-null and the row is
     * still in `SENDING` state on the local ledger,
     * [StartTransactionUseCase.recoverOrStart] reuses it so the backend
     * dedup (HTTP 409) detects the retry and the sale converges to a single
     * invoice. Required for auditoría ítem 1 — "timeout + restart + retry
     * never create another sale nor another id".
     *
     * null/blank for brand-new operations → fresh UUID is minted.
     */
    val correlationCarryOver: String? = null,
    val preferredCorrelationId: String? = null,
    val saleItemsOverride: List<SaleItemDto>? = null,
    val financialSnapshotOverride: com.amaxonia.pos.domain.model.SaleFinancialSnapshot? = null,
    val cuentaMesa: CuentaMesaVentaDto? = null,
    /**
     * Configuración de impresora seleccionada por el usuario en Settings (única
     * fuente de verdad para HKA20 vs facturación digital en Venezuela).
     * Se propaga hasta el backend como `ProcessSaleRequestDto.useHka20`.
     */
    val printerType: PrinterType = PrinterType.NONE,
    val paymentCondition: PaymentCondition = PaymentCondition.CONTADO,
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

    /**
     * The backend rejected the submission with HTTP 409 because the same
     * [DuplicateInvoice.clientCorrelationId] was already processed. The user
     * is reconciled through the canonical invoice lookup. This result remains
     * for the ambiguous case where that lookup is unavailable.
     */
    data class DuplicateInvoice(
        val clientCorrelationId: String,
        val reason: String,
    ) : PaymentFlowResult
}

class PaymentExecutionOperations(
    val queueOfflineInvoice: QueueOfflineInvoiceUseCase,
    val printInvoice: PrintInvoiceUseCase,
    val confirmFiscalDocument: ConfirmFiscalDocumentUseCase,
    val executeGatewayPayment: ExecuteGatewayPaymentUseCase,
    val handlePaymentFailure: HandlePaymentFailureUseCase,
)

class ExecutePaymentFlowUseCase(
    private val operations: PaymentExecutionOperations,
    private val prepareSale: PrepareSaleUseCase,
    private val completeSale: CompletePaymentSaleUseCase,
    private val startTransaction: StartTransactionUseCase? = null,
    private val gatewayCallbackLedger: GatewayCallbackLedger? = null,
    private val sessionReader: PaymentSessionReader? = null,
) {
    suspend operator fun invoke(
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
        val tenant = sessionReader?.currentTenant()
        val correlationId =
            startTransaction
                ?.recoverOrStart(
                    StartTransactionCommand(
                        carryOverId = sale.correlationCarryOver,
                        preferredId = input.preferredCorrelationId,
                        idCaja = sale.request.factura.idCaja,
                        idCajaSecuencia = sale.request.factura.idCajaSecuencia,
                        totalAmount = sale.financials.total,
                        currency = sale.request.moneda?.abrMonedaBase ?: DEFAULT_CURRENCY,
                        clientName = sale.client.paymentFullName(),
                        tenant = tenant,
                    ),
                )?.clientCorrelationId
        val stampedSale = sale.withCorrelationId(correlationId)
        val gatewayFailure = executeGatewayIfRequired(input, stampedSale, correlationId, onEvent)
        return if (gatewayFailure != null) {
            correlationId?.let { startTransaction?.markFailed(it, "Gateway: ${gatewayFailure.message}") }
            gatewayFailure
        } else {
            onEvent(PaymentFlowEvent.Progress("Generando factura..."))
            val result = completeSale(input, stampedSale, correlationId, onEvent)
            markLedgerFromResult(correlationId, result)
            result
        }
    }

    private suspend fun markLedgerFromResult(
        correlationId: String?,
        result: PaymentFlowResult,
    ) {
        if (correlationId == null) return
        when (result) {
            is PaymentFlowResult.Success ->
                startTransaction?.markConfirmed(
                    clientCorrelationId = correlationId,
                    remoteInvoiceId = result.payload.transactionId.takeIf { it.isNotBlank() },
                    remoteInvoiceNumber = result.payload.codFactura.takeIf { it.isNotBlank() },
                )
            is PaymentFlowResult.DuplicateInvoice ->
                startTransaction?.markFailed(
                    clientCorrelationId = correlationId,
                    message = result.reason,
                    status = StartTransactionUseCase.STATUS_DUPLICATE,
                )
            is PaymentFlowResult.Failure ->
                startTransaction?.markFailed(
                    clientCorrelationId = correlationId,
                    message = result.message,
                )
        }
    }

    private suspend fun executeGatewayIfRequired(
        input: ExecutePaymentFlowInput,
        sale: PreparedSale,
        correlationId: String?,
        onEvent: suspend (PaymentFlowEvent) -> Unit,
    ): PaymentFlowResult.Failure? {
        if (input.countryCode != VENEZUELA_CODE) return null
        if (correlationId != null) {
            gatewayCallbackLedger?.markAwaiting(
                correlationId = correlationId,
                nextAttemptAt = 0L,
            )
            com.amaxonia.pos.data.printer.RapidPayBridge.setPendingCorrelationId(correlationId)
            com.amaxonia.pos.core.telemetry.SaleTelemetry.record(
                event = com.amaxonia.pos.core.telemetry.SaleEvent.GATEWAY_AWAITING,
                idFactura = correlationId,
            )
        }
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
        if (result.isFailure && correlationId != null) {
            gatewayCallbackLedger?.markResolved(
                correlationId = correlationId,
                responseCode = "ERROR_LOCAL",
            )
        }
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
    private val fiscalConfirmationLedger: PaymentFiscalConfirmationLedger? = null,
    private val sessionReader: PaymentSessionReader? = null,
) {
    internal suspend operator fun invoke(
        input: ExecutePaymentFlowInput,
        sale: PreparedSale,
        correlationId: String?,
        onEvent: suspend (PaymentFlowEvent) -> Unit,
    ): PaymentFlowResult =
        if (sale.isOnline) {
            processOnline(input, sale, correlationId, onEvent)
        } else {
            processOffline(input, sale)
        }

    @Suppress("ReturnCount")
    private suspend fun processOffline(
        input: ExecutePaymentFlowInput,
        sale: PreparedSale,
    ): PaymentFlowResult {
        val tenant =
            sessionReader?.currentTenant()
                ?: return operations.failure(IllegalStateException("No hay sesión activa"), "Sin sesión de empresa activa")
        val queued =
            operations.queueOfflineInvoice(
                countryCode = input.countryCode,
                request = sale.request,
                total = sale.financials.total,
                clientName = sale.client.paymentDisplayName(),
                tenant = tenant,
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
        correlationId: String?,
        onEvent: suspend (PaymentFlowEvent) -> Unit,
    ): PaymentFlowResult =
        repositories.runtime.sales.processSale(sale.request).fold(
            onFailure = { error ->
                if (error is DuplicateInvoiceException) {
                    reconcileDuplicate(error.clientCorrelationId, input, sale, correlationId, onEvent)
                } else {
                    com.amaxonia.pos.core.telemetry.SaleTelemetry.record(
                        event = com.amaxonia.pos.core.telemetry.SaleEvent.SALE_REJECTED_BACKEND,
                        idFactura = correlationId ?: sale.request.idFactura.orEmpty(),
                        "error" to (error::class.simpleName ?: error::class.java.simpleName),
                    )
                    operations.failure(error, "No se pudo procesar la venta. Intenta nuevamente")
                }
            },
            onSuccess = { response -> processAcceptedOnlineSale(input, sale, correlationId, response, onEvent) },
        )

    private suspend fun reconcileDuplicate(
        clientCorrelationId: String,
        input: ExecutePaymentFlowInput,
        sale: PreparedSale,
        correlationId: String?,
        onEvent: suspend (PaymentFlowEvent) -> Unit,
    ): PaymentFlowResult {
        val lookup = repositories.runtime.sales.findByCorrelationId(clientCorrelationId)
        val reconciled = lookup.getOrNull()
        if (lookup.isFailure || reconciled == null) {
            com.amaxonia.pos.core.telemetry.SaleTelemetry.record(
                event = com.amaxonia.pos.core.telemetry.SaleEvent.SALE_AMBIGUOUS,
                idFactura = clientCorrelationId,
                "reason" to "unreconciled_409",
            )
            return PaymentFlowResult.DuplicateInvoice(
                clientCorrelationId = clientCorrelationId,
                reason =
                    if (lookup.isFailure) {
                        "Factura duplicada y no se pudo reconciliar con el backend"
                    } else {
                        "Factura duplicada: el backend ya la procesó pero no la expone para reconciliación"
                    },
            )
        }
        com.amaxonia.pos.core.telemetry.SaleTelemetry.record(
            event = com.amaxonia.pos.core.telemetry.SaleEvent.SALE_DUPLICATE,
            idFactura = reconciled.idFactura,
            "resolved" to true,
        )
        val synthetic =
            ProcessSaleResponseDto(
                success = true,
                idFactura = reconciled.idFactura,
                codFactura = reconciled.codFactura,
                codEstatus = reconciled.codEstatus,
                sesionMesaCerrada = reconciled.sesionMesaCerrada,
            )
        return processAcceptedOnlineSale(input, sale, correlationId, synthetic, onEvent)
    }

    private suspend fun processAcceptedOnlineSale(
        input: ExecutePaymentFlowInput,
        sale: PreparedSale,
        correlationId: String?,
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
        val saveError = repositories.state.transaction.saveTransaction(transaction).exceptionOrNull()
        return if (saveError != null) {
            operations.failure(saveError, "La venta se proceso, pero no se pudo guardar la transaccion local")
        } else {
            val (result, outcome) =
                finishOnlineSale(
                    OnlineSaleRequest(input, sale, correlationId, response, transaction),
                    onEvent,
                )
            outcome?.let { fiscalConfirmationLedger?.recordOutcome(it) }
            result
        }
    }

    private data class OnlineSaleRequest(
        val input: ExecutePaymentFlowInput,
        val sale: PreparedSale,
        val correlationId: String?,
        val response: ProcessSaleResponseDto,
        val transaction: Transaction,
    )

    private suspend fun finishOnlineSale(
        request: OnlineSaleRequest,
        onEvent: suspend (PaymentFlowEvent) -> Unit,
    ): Pair<PaymentFlowResult.Success, FiscalConfirmationOutcome?> {
        val input = request.input
        val sale = request.sale
        val response = request.response
        val transaction = request.transaction
        onEvent(PaymentFlowEvent.Progress("Imprimiendo factura..."))
        val printResult = operations.printInvoice(input.countryCode, transaction, response.idFactura)
        val fiscalNumber = printResult?.fiscalNumber?.takeIf(String::isNotBlank).orEmpty()
        val printerSerial = printResult?.printerSerial.orEmpty()
        com.amaxonia.pos.core.telemetry.SaleTelemetry.record(
            event = com.amaxonia.pos.core.telemetry.SaleEvent.SALE_CONFIRMED,
            idFactura = response.idFactura,
        )
        if (fiscalNumber.isNotBlank()) {
            com.amaxonia.pos.core.telemetry.SaleTelemetry.record(
                event = com.amaxonia.pos.core.telemetry.SaleEvent.FISCAL_PRINTED,
                idFactura = response.idFactura,
                "fiscalNumber" to fiscalNumber,
            )
        }
        val outcome =
            buildFiscalOutcome(
                request = request,
                fiscalNumber = fiscalNumber,
                printerSerial = printerSerial,
                onEvent = onEvent,
            )
        val success =
            PaymentFlowResult.Success(
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
                                tableSessionClosed = response.sesionMesaCerrada,
                            ),
                    ),
                receiptPrintMessage = printResult?.displayMessage,
            )
        return success to outcome
    }

    private suspend fun buildFiscalOutcome(
        request: OnlineSaleRequest,
        fiscalNumber: String,
        printerSerial: String,
        onEvent: suspend (PaymentFlowEvent) -> Unit,
    ): FiscalConfirmationOutcome? {
        val countryCode = request.input.countryCode
        val correlationId = request.correlationId
        val invoiceId = request.response.idFactura
        if (countryCode != VENEZUELA_CODE || fiscalNumber.isBlank()) return null
        val confirmation =
            operations.confirmFiscalDocument(
                invoiceId = invoiceId,
                fiscalNumber = fiscalNumber,
                printerSerial = printerSerial,
            )
        return confirmation.fold(
            onFailure = { error ->
                onEvent(PaymentFlowEvent.FiscalConfirmationFailed)
                if (correlationId != null && invoiceId.isNotBlank()) {
                    FiscalConfirmationOutcome.Retryable(
                        correlationId = correlationId,
                        remoteInvoiceId = invoiceId,
                        fiscalNumber = fiscalNumber,
                        printerSerial = printerSerial,
                        failureMessage = error.message ?: "Confirmacion fiscal fallida",
                    )
                } else {
                    null
                }
            },
            onSuccess = {
                if (correlationId != null) {
                    FiscalConfirmationOutcome.Confirmed(
                        correlationId = correlationId,
                        fiscalNumber = fiscalNumber,
                        printerSerial = printerSerial,
                    )
                } else {
                    null
                }
            },
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
            tableSessionClosed = completion.tableSessionClosed,
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
    val tableSessionClosed: Boolean = false,
)

private const val VENEZUELA_CODE = "VE"
private const val DEFAULT_CURRENCY = "USD"
private val TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("hh:mm a")
private val DATE_HEADER_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE, dd MMMM yyyy")
