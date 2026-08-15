package com.amaxoniaerp.features.creditnotes.domain

class CreditNoteNotFoundException(
    message: String,
) : RuntimeException(message)

class CreditNoteValidationException(
    message: String,
) : RuntimeException(message)
