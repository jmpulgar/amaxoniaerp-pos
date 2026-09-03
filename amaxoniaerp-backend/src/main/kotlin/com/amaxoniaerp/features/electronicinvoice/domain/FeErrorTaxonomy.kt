package com.amaxoniaerp.features.electronicinvoice.domain

import kotlinx.serialization.Serializable

private val CODIGOS_TRANSPORTE_REINTENTABLES = setOf("100", "101", "201", "202", "300")
private val CODIGOS_QUE_REQUIEREN_CONCILIACION = setOf("102", "201", "202")
private const val CODIGO_FISCAL_DUPLICADO = "1513"

private val INCIDENCIA_FISCAL_REGEX = Regex("""^\s*(\d{4})\s*-\s*(.+?)\s*$""")

/**
 * Una validación fiscal DGI (código de 4 dígitos, catálogo 1000-4999)
 * embebida en el mensaje combinado que retorna The Factory HKA.
 */
@Serializable
data class IncidenciaFiscal(
    val codigo: String,
    val mensaje: String,
)

/**
 * Taxonomía de un fallo FE en memoria (Q4): separa el código de transporte
 * TFHKA (100-300) de las incidencias fiscales DGI (4 dígitos) y clasifica el
 * fallo para que ningún cliente reintente a ciegas.
 *
 * - [reintentable]: true sólo para fallos de conexión/transporte o códigos de
 *   procesamiento del PAC (100/101/201/202/300). Los rechazos fiscales
 *   (ej. 1508, 2007) y las validaciones de datos (109) o duplicados (102)
 *   requieren corrección o conciliación antes de otro envío.
 * - [requiereConciliacion]: true cuando hay que consultar EstadoDocumento
 *   antes de decidir (transporte incierto, 102/201/202, o el duplicado DGI 1513).
 */
@Serializable
data class AnalisisFalloPac(
    val codigoTransporte: String,
    val incidenciasFiscales: List<IncidenciaFiscal> = emptyList(),
    val reintentable: Boolean = false,
    val requiereConciliacion: Boolean = false,
)

/**
 * Analiza la respuesta de error del PAC. Los mensajes llegan combinados, por
 * ejemplo: "1513-Número del documento fiscal duplicado. | 1508-Tiempo
 * excesivo... | 2007-Item 1: ...", antecedidos de un código de transporte
 * propio de TFHKA ([codigo]).
 */
fun analizarFalloPac(
    codigo: String?,
    mensaje: String?,
    falloDeTransporte: Boolean = false,
): AnalisisFalloPac {
    val transporte = codigo?.trim().orEmpty()
    val incidencias = extraerIncidenciasFiscales(mensaje)
    val reintentable =
        when {
            incidencias.isNotEmpty() -> false
            falloDeTransporte -> true
            transporte in CODIGOS_TRANSPORTE_REINTENTABLES -> true
            else -> false
        }
    val requiereConciliacion =
        falloDeTransporte ||
            transporte in CODIGOS_QUE_REQUIEREN_CONCILIACION ||
            incidencias.any { it.codigo == CODIGO_FISCAL_DUPLICADO }
    return AnalisisFalloPac(
        codigoTransporte = transporte.ifEmpty { if (falloDeTransporte) "TRANSPORTE" else "" },
        incidenciasFiscales = incidencias,
        reintentable = reintentable,
        requiereConciliacion = requiereConciliacion,
    )
}

private fun extraerIncidenciasFiscales(mensaje: String?): List<IncidenciaFiscal> {
    if (mensaje.isNullOrBlank()) return emptyList()
    return mensaje
        .split('|')
        .mapNotNull { segmento ->
            INCIDENCIA_FISCAL_REGEX.find(segmento.trim())?.let { match ->
                IncidenciaFiscal(codigo = match.groupValues[1], mensaje = match.groupValues[2])
            }
        }
}
