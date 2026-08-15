package com.amaxonia.pos.domain.usecase.payment

import com.amaxonia.pos.domain.model.money.Money
import com.amaxonia.pos.domain.model.printer.PrinterType
import com.amaxonia.pos.domain.repository.TableAccountPaymentReader
import java.math.BigDecimal

/** Canonical implementation behind the [PaymentOperation] external seam. */
internal class DefaultPaymentOperation(
    private val executeFlow:
        suspend (
            ExecutePaymentFlowInput,
            suspend (PaymentFlowEvent) -> Unit,
        ) -> PaymentFlowResult,
    private val printerTypeProvider: suspend () -> PrinterType,
    private val tableAccountPaymentReader: TableAccountPaymentReader? = null,
) : PaymentOperation {
    override suspend fun execute(
        request: PaymentOperationRequest,
        onEvent: suspend (PaymentFlowEvent) -> Unit,
    ): PaymentFlowResult {
        val tablePayment =
            when (val source = request.source) {
                is PaymentSource.CurrentCart -> tableAccountPaymentReader?.current?.value
                is PaymentSource.TableAccount -> source.payment
            }
        val financialSnapshot =
            tablePayment?.financialSnapshot
                ?: (request.source as? PaymentSource.CurrentCart)?.financialSnapshot
        val input =
            ExecutePaymentFlowInput(
                countryCode = request.context.countryCode,
                paymentDetails = request.payment.details,
                totalAmount = request.payment.totalAmount,
                tenderedAmount = request.payment.tenderedAmount,
                changeDue = request.payment.changeDue.toDouble(),
                totalAmountBs = request.context.secondaryAmount(request.payment.totalAmount),
                changeDueBs = request.context.secondaryAmount(request.payment.changeDue),
                exchangeRate = request.context.exchangeRate,
                secondaryCurrency = request.context.secondaryCurrency,
                isMultiCurrency = request.context.isMultiCurrency,
                availableMethods = request.context.availableMethods,
                correlationCarryOver = tablePayment?.correlationId,
                preferredCorrelationId = tablePayment?.correlationId,
                saleItemsOverride = tablePayment?.saleItems,
                financialSnapshotOverride = financialSnapshot,
                cuentaMesa = tablePayment?.saleContext,
                printerType = printerTypeProvider(),
                paymentCondition = request.payment.condition,
            )
        return executeFlow(input, onEvent)
    }

    private fun PaymentExecutionContext.secondaryAmount(amount: Money): Double {
        if (!isMultiCurrency || exchangeRate <= 0.0) return 0.0
        return amount.times(BigDecimal.valueOf(exchangeRate)).toDouble()
    }
}
