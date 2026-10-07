package com.amaxonia.erp.domain.util

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

object CajaDateParser {
    private val DISPLAY_DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.getDefault())

    private val DATE_TIME_FORMATTERS =
        listOf(
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSSSS"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.S"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss.SSS"),
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"),
            DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss"),
            DateTimeFormatter.ISO_DATE_TIME,
            DateTimeFormatter.ISO_LOCAL_DATE_TIME,
        )

    private val DATE_FORMATTERS =
        listOf(
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("dd-MM-yyyy"),
            DateTimeFormatter.ISO_LOCAL_DATE,
        )

    fun parseLocalDate(raw: String?, zoneId: ZoneId = ZoneId.systemDefault()): LocalDate? {
        val trimmed = raw?.trim()?.takeIf(String::isNotEmpty) ?: return null

        // 1. Try ISO Offset / Zoned / Instant (supports 'Z' and +/- offsets)
        runCatching {
            Instant.parse(trimmed).atZone(zoneId).toLocalDate()
        }.getOrNull()?.let { return it }

        runCatching {
            OffsetDateTime.parse(trimmed).atZoneSameInstant(zoneId).toLocalDate()
        }.getOrNull()?.let { return it }

        runCatching {
            ZonedDateTime.parse(trimmed).withZoneSameInstant(zoneId).toLocalDate()
        }.getOrNull()?.let { return it }

        // 2. Try date-time formatters
        DATE_TIME_FORMATTERS.firstNotNullOfOrNull { formatter ->
            runCatching { LocalDateTime.parse(trimmed, formatter).toLocalDate() }.getOrNull()
        }?.let { return it }

        // 3. Try date formatters
        DATE_FORMATTERS.firstNotNullOfOrNull { formatter ->
            runCatching { LocalDate.parse(trimmed, formatter) }.getOrNull()
        }?.let { return it }

        // 4. Substring fallback for yyyy-MM-dd...
        if (trimmed.length >= 10 && trimmed[4] == '-' && trimmed[7] == '-') {
            runCatching { LocalDate.parse(trimmed.substring(0, 10)) }.getOrNull()?.let { return it }
        }

        // 5. Substring fallback for dd/MM/yyyy...
        if (trimmed.length >= 10 && trimmed[2] == '/' && trimmed[5] == '/') {
            runCatching {
                val d = trimmed.substring(0, 2).toInt()
                val m = trimmed.substring(3, 5).toInt()
                val y = trimmed.substring(6, 10).toInt()
                LocalDate.of(y, m, d)
            }.getOrNull()?.let { return it }
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
