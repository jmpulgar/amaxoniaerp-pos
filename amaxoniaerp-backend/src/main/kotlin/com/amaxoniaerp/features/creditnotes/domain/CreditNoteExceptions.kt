package com.amaxoniaerp.features.creditnotes.domain

import com.amaxoniaerp.core.error.ApiException
import com.amaxoniaerp.core.error.ErrorCategory

class CreditNoteNotFoundException(
    message: String,
) : ApiException(ErrorCategory.NotFound, message)

class CreditNoteValidationException(
    message: String,
) : ApiException(ErrorCategory.Validation, message)
