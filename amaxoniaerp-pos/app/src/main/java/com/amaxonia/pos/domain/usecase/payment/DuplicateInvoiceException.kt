package com.amaxonia.pos.domain.usecase.payment

/**
 * Raised when the backend rejects a sale submission with HTTP 409 Conflict
 * because the [clientCorrelationId] (`idFactura`) was already processed in a
 * previous attempt. The backend does not return the existing invoice body on
 * conflict, so the operation cannot be auto-reconciled; the user must choose
 * whether to look up the prior invoice or escalate to manual review.
 */
class DuplicateInvoiceException(
    val clientCorrelationId: String,
    message: String,
) : RuntimeException(message)
