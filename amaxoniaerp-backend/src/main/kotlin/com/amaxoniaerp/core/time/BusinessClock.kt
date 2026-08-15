package com.amaxoniaerp.core.time

import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.Year
import java.time.ZoneId

/**
 * Única fuente de verdad para fecha/hora de negocio por país (reloj de pared local).
 *
 * No usar [LocalDateTime.now] ni [LocalDate.now] en repositorios: siempre pasar [countryCode] explícito
 * desde la capa HTTP (JWT / contexto de tenant).
 *
 * **JDBC `serverTimezone=UTC`:** los valores son [LocalDateTime] sin offset; el driver MySQL suele
 * persistir los componentes tal cual en columnas `DATETIME`. Conviene validar en staging que lo
 * guardado coincide con el valor generado aquí (VE/PA sin DST reducen sorpresas).
 */
object BusinessClock {
    fun zoneForCountry(countryCode: String): ZoneId =
        when (countryCode.uppercase()) {
            "VE" -> ZoneId.of("America/Caracas")
            "PA" -> ZoneId.of("America/Panama")
            else -> throw IllegalArgumentException("País no soportado para zona horaria: $countryCode")
        }

    fun nowForCountry(countryCode: String): LocalDateTime = LocalDateTime.now(Clock.system(zoneForCountry(countryCode)))

    fun todayForCountry(countryCode: String): LocalDate = nowForCountry(countryCode).toLocalDate()

    /** Año de dos dígitos (p. ej. 26) según la fecha civil en la zona del país. */
    fun yearTwoDigitsForCountry(countryCode: String): Int = Year.from(todayForCountry(countryCode)).value % 100
}
