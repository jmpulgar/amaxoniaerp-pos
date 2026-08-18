package com.amaxoniaerp.features.creditnotes.data

import com.amaxoniaerp.features.creditnotes.domain.CreditNoteValidationException

internal fun requireRefundPaymentForm(idFormaPago: Int?): Int =
    idFormaPago ?: throw CreditNoteValidationException("Forma de pago de reintegro requerida")
