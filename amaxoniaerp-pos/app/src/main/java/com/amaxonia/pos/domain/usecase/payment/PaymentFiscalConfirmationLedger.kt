package com.amaxonia.pos.domain.usecase.payment

/**
 * Domain port for persisting fiscal-confirmation state on the local ledger.
 *
 * Implementations wire this to [QueueFiscalConfirmationUseCase] (Room-backed)
 * and schedule a [com.amaxonia.pos.data.sync.FiscalConfirmationWorker]
 * replay. The payment flow only needs two operations: record a success
 * (so the worker does not retry it), or record a failure (so the worker
 * picks it up on its next tick under the lease semantics documented in
 * [QueueFiscalConfirmationUseCase]).
 */
fun interface PaymentFiscalConfirmationLedger {
    suspend fun recordOutcome(outcome: FiscalConfirmationOutcome)

    suspend fun enqueueRetry(
        correlationId: String,
        remoteInvoiceId: String,
        fiscalNumber: String,
        printerSerial: String,
        failureMessage: String,
    ) = recordOutcome(
        FiscalConfirmationOutcome.Retryable(
            correlationId = correlationId,
            remoteInvoiceId = remoteInvoiceId,
            fiscalNumber = fiscalNumber,
            printerSerial = printerSerial,
            failureMessage = failureMessage,
        ),
    )

    suspend fun markConfirmed(
        correlationId: String,
        fiscalNumber: String,
        printerSerial: String,
    ) = recordOutcome(
        FiscalConfirmationOutcome.Confirmed(
            correlationId = correlationId,
            fiscalNumber = fiscalNumber,
            printerSerial = printerSerial,
        ),
    )
}

sealed interface FiscalConfirmationOutcome {
    data class Confirmed(
        val correlationId: String,
        val fiscalNumber: String,
        val printerSerial: String,
    ) : FiscalConfirmationOutcome

    data class Retryable(
        val correlationId: String,
        val remoteInvoiceId: String,
        val fiscalNumber: String,
        val printerSerial: String,
        val failureMessage: String,
    ) : FiscalConfirmationOutcome
}
