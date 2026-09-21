package com.amaxonia.pos.domain.util

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

object CajaDateParser {
    private val DISPLAY_DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.getDefault())

    private val DATE_TIME_FORMATTERS =
        listOf(
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"),
            DateTimeFormatter.ISO_LOCAL_DATE_TIME,
        )

    private val DATE_FORMATTERS =
        listOf(
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),  
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ISO_LOCAL_DATE,
        )

    fun parseLocalDate(raw: String?): LocalDate? {
        val trimmed = raw?.trim()?.takeIf(String::isNotEmpty) ?: return null

        DATE_TIME_FORMATTERS.firstNotNullOfOrNull { formatter ->
            runCatching { LocalDateTime.parse(trimmed, formatter).toLocalDate() }.getOrNull()
        }?.let { return it }

        DATE_FORMATTERS.firstNotNullOfOrNull { formatter ->
            runCatching { LocalDate.parse(trimmed, formatter) }.getOrNull()
        }?.let { return it }

        if (trimmed.length >= 10 && trimmed[4] == '-' && trimmed[7] == '-') {
            runCatching { LocalDate.parse(trimmed.substring(0, 10)) }.getOrNull()?.let { return it }
        }

        return null
    }

    fun formatDisplayDate(raw: String?): String {
        val localDate = parseLocalDate(raw)
        return localDate?.format(DISPLAY_DATE_FORMATTER) ?: raw?.trim().orEmpty()
    }

    fun isFromPreviousDay(raw: String?, today: LocalDate = LocalDate.now()): Boolean {
        val localDate = parseLocalDate(raw) ?: return false
        return localDate.isBefore(today)
    }
}
