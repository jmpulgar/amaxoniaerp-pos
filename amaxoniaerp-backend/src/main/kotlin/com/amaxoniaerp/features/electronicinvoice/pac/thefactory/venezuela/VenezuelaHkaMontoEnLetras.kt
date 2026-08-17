package com.amaxoniaerp.features.electronicinvoice.pac.thefactory.venezuela

import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode

private const val HUNDRED = 100
private const val TWENTY = 20
private const val TWENTY_ONE = 21
private const val TWENTY_NINE = 29
private const val DECIMAL_BASE = 10

private val UNIDADES =
    arrayOf(
        "",
        "UNO",
        "DOS",
        "TRES",
        "CUATRO",
        "CINCO",
        "SEIS",
        "SIETE",
        "OCHO",
        "NUEVE",
        "DIEZ",
        "ONCE",
        "DOCE",
        "TRECE",
        "CATORCE",
        "QUINCE",
        "DIECISÉIS",
        "DIECISIETE",
        "DIECIOCHO",
        "DIECINUEVE",
    )
private val DECENAS =
    arrayOf(
        "",
        "",
        "VEINTI",
        "TREINTA",
        "CUARENTA",
        "CINCUENTA",
        "SESENTA",
        "SETENTA",
        "OCHENTA",
        "NOVENTA",
    )
private val CENTENAS =
    arrayOf(
        "",
        "CIENTO",
        "DOSCIENTOS",
        "TRESCIENTOS",
        "CUATROCIENTOS",
        "QUINIENTOS",
        "SEISCIENTOS",
        "SETECIENTOS",
        "OCHOCIENTOS",
        "NOVECIENTOS",
    )

private val MILLONES = BigInteger("1000000")
private val MIL = BigInteger("1000")
private val CIEN = BigInteger("100")

/**
 * Conversión del monto a palabras en español (convención venezolana).
 *
 * El nodo HKA `montoEnLetras` es **obligatorio** según el DTO
 * `VenezuelaHkaTotalesSubTotales`. Esta implementación cubre:
 *
 *   - Enteros 0..999 999 999 999 (billones no soportados; el PAC VE no los
 *     admite con la escala monetaria usada).
 *   - Centavos siempre con "/100" (formato venezolano usado por SENIAT).
 *   - Singular/plural de "millón/millones", "mil" (invariable), "bolívares"
 *     (la moneda siempre se expresa en plural, salvo "UN BOLÍVAR" exacto).
 *
 * Ejemplos:
 *   -    0.00 → "CERO BOLÍVARES CON 00/100"
 *   -    1.00 → "UN BOLÍVAR CON 00/100"
 *   -   16.50 → "DIECISÉIS BOLÍVARES CON 50/100"
 *   -  100.00 → "CIEN BOLÍVARES CON 00/100"
 *   -  116.00 → "CIENTO DIECISÉIS BOLÍVARES CON 00/100"
 *   - 1000.00 → "MIL BOLÍVARES CON 00/100"
 *   - 1500000.00 → "UN MILLÓN QUINIENTOS MIL BOLÍVARES CON 00/100"
 */
internal fun montoEnLetras(total: BigDecimal): String {
    val escala = total.setScale(MONEY_SCALE, RoundingMode.HALF_UP)
    val partes = escala.toPlainString().split('.')
    val entero = partes[0].toBigInteger()
    val centavos = partes.getOrElse(1) { "00" }

    val enteroLetras =
        when {
            entero == BigInteger.ZERO -> "CERO"
            entero == BigInteger.ONE -> "UN"
            else -> enterosALetras(entero)
        }
    val moneda = if (entero == BigInteger.ONE) "BOLÍVAR" else "BOLÍVARES"
    return "$enteroLetras $moneda CON $centavos/100"
}

/** Conversión de enteros (1..999 999 999 999) a palabras en español (VE). */
internal fun enterosALetras(n: BigInteger): String {
    require(n >= BigInteger.ZERO) { "Solo se soportan enteros no negativos" }
    return when {
        n == BigInteger.ZERO -> "CERO"
        n == CIEN -> "CIEN" // excepción: 100 → "CIEN", no "CIENTO"
        else -> {
            // Billones no soportados: si n >= 10^12, dejamos al PAC que lo rechace;
            // no ocurre con escala monetaria VE (DECIMAL 20,2).
            val parts = mutableListOf<String>()
            parts += millonesEnLetras(n)
            val restoTrasMillon = n.mod(MILLONES)
            parts += milesEnLetras(restoTrasMillon)
            parts += unidadFinalEnLetras(restoTrasMillon)
            parts.filter { it.isNotEmpty() }.joinToString(" ").trim()
        }
    }
}

private fun millonesEnLetras(n: BigInteger): String =
    when {
        n < MILLONES -> ""
        else -> {
            val mm = n.divide(MILLONES).mod(MILLONES)
            when {
                mm.signum() <= 0 -> ""
                mm == BigInteger.ONE -> "UN MILLÓN"
                else -> "${grupoTres(mm.toInt())} MILLONES"
            }
        }
    }

private fun milesEnLetras(restoTrasMillon: BigInteger): String {
    val miles = restoTrasMillon.divide(MIL)
    if (miles.signum() <= 0) return ""
    // "MIL" es invariable; "UN MIL" es preferible evitar (se omite "UN").
    return if (miles == BigInteger.ONE) {
        "MIL"
    } else {
        "${grupoTres(miles.toInt())} MIL"
    }
}

private fun unidadFinalEnLetras(restoTrasMillon: BigInteger): String {
    val unidad = restoTrasMillon.mod(MIL)
    return if (unidad.signum() > 0) grupoTres(unidad.toInt()) else ""
}

/** Convierte un bloque de 3 dígitos (0..999) a letras. */
private fun grupoTres(n: Int): String =
    when {
        n == 0 -> ""
        n == HUNDRED -> "CIEN"
        else -> grupoTresCompuesto(n)
    }

private fun grupoTresCompuesto(n: Int): String {
    val sb = StringBuilder()
    var resto = n
    if (resto >= HUNDRED) {
        sb.append(CENTENAS[resto / HUNDRED]).append(' ')
        resto %= HUNDRED
    }
    when {
        resto < TWENTY -> sb.append(UNIDADES[resto])
        resto == TWENTY -> sb.append("VEINTE")
        // VEINTIUNO..VEINTINUEVE
        resto in TWENTY_ONE..TWENTY_NINE -> sb.append(DECENAS[2]).append(UNIDADES[resto - TWENTY])
        else -> {
            val d = resto / DECIMAL_BASE
            val u = resto % DECIMAL_BASE
            sb.append(DECENAS[d])
            if (u > 0) sb.append(" Y ").append(UNIDADES[u])
        }
    }
    return sb.toString().trim()
}
