package com.amaxonia.pos.domain.util

import java.time.LocalDate
import java.time.temporal.ChronoUnit

object DateRangeValidator {
    const val MAX_RANGE_DAYS = 31L

    /**
     * Valida que [fechaInicio] y [fechaFin] cumplan con:
     * 1. Formato ISO válido (AAAA-MM-DD) si están presentes.
     * 2. Hasta >= Desde.
     * 3. Rango máximo permitido de 1 mes (31 días).
     *
     * Retorna mensaje descriptivo en caso de error, o `null` si el rango es válido.
     */
    fun validate(
        fechaInicio: String?,
        fechaFin: String?,
    ): String? {
        val startStr = fechaInicio?.trim()?.takeIf(String::isNotEmpty)
        val endStr = fechaFin?.trim()?.takeIf(String::isNotEmpty)
        if (startStr == null || endStr == null) return null

        val start = runCatching { LocalDate.parse(startStr) }.getOrNull()
        val end = runCatching { LocalDate.parse(endStr) }.getOrNull()

        return when {
            start == null -> "La fecha 'Desde' tiene un formato inválido (use AAAA-MM-DD)"
            end == null -> "La fecha 'Hasta' tiene un formato inválido (use AAAA-MM-DD)"
            end.isBefore(start) -> "La fecha 'Hasta' ($endStr) no puede ser anterior a 'Desde' ($startStr)"
            ChronoUnit.DAYS.between(start, end) > MAX_RANGE_DAYS -> {
                val daysBetween = ChronoUnit.DAYS.between(start, end)
                "El período máximo de consulta permitido es de 1 mes (31 días). Rango seleccionado: $daysBetween días"
            }
            else -> null
        }
    }
}
