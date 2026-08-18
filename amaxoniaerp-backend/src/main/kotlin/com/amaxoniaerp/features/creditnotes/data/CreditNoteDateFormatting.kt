package com.amaxoniaerp.features.creditnotes.data

import java.time.LocalDate
import java.time.LocalDateTime

internal fun formatDate(value: LocalDate?): String {
    if (value == null) return ""
    return value.format(DATE_FORMATTER)
}

internal fun formatDateTime(value: LocalDateTime?): String {
    if (value == null) return ""
    return value.format(DATE_TIME_FORMATTER)
}
