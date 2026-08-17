package com.amaxoniaerp.features.facturas.data

import com.amaxoniaerp.features.facturas.domain.FacturasResumen
import org.jetbrains.exposed.sql.ResultRow
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** Acumulador mutable de totales del resumen de facturas (PA y VE). */
private class ResumenAccumulator {
    var ventasBrutas = 0.0
    var ventasNetas = 0.0
    var cancelaciones = 0.0
    var totalPagadas = 0
    var totalAnuladas = 0
    var moneda = "USD"
    var abrMonedaSec: String? = null
    var tasaGlobal: Float? = null
    var ventasBrutasRef = 0.0
    var ventasNetasRef = 0.0
    var cancelacionesRef = 0.0

    fun add(
        row: ResultRow,
        tabla: BaseFacturasTable,
    ) {
        val descripcionEstatus = row[EstatusTable.descripcion] ?: ""
        val total = row[tabla.totalTotalFactura].toDouble()
        val totalGeneral = row[tabla.totalizarTotalGeneral].toDouble()
        val isAnulada =
            descripcionEstatus.equals("Anulada", ignoreCase = true) ||
                descripcionEstatus.equals("Anulado", ignoreCase = true)

        if (tabla is FacturasTableVE) {
            addVE(row, tabla, isAnulada, total, totalGeneral)
        } else {
            addGeneric(isAnulada, total, totalGeneral)
        }
    }

    private fun addVE(
        row: ResultRow,
        tabla: FacturasTableVE,
        isAnulada: Boolean,
        total: Double,
        totalGeneral: Double,
    ) {
        val tasaRow = row[tabla.tasa]
        val totalRefRow = row[tabla.totalRef]?.toDouble() ?: 0.0
        val abrSecRow = row[tabla.abrMonedaSecundaria]

        if (isAnulada) {
            cancelaciones += total
            cancelacionesRef += totalRefRow
            totalAnuladas++
        } else {
            ventasBrutas += totalGeneral
            ventasNetas += total
            ventasBrutasRef += totalRefRow
            ventasNetasRef += totalRefRow
            totalPagadas++
        }

        if (moneda == "USD") {
            val m = row[tabla.abrMonedaBase]?.takeIf { it.isNotBlank() }
            if (m != null) moneda = m
        }
        if (abrMonedaSec.isNullOrBlank() && !abrSecRow.isNullOrBlank()) {
            abrMonedaSec = abrSecRow
        }
        if (tasaGlobal == null && tasaRow != null && tasaRow > 0f) {
            tasaGlobal = tasaRow
        }
    }

    private fun addGeneric(
        isAnulada: Boolean,
        total: Double,
        totalGeneral: Double,
    ) {
        if (isAnulada) {
            cancelaciones += total
            totalAnuladas++
        } else {
            ventasBrutas += totalGeneral
            ventasNetas += total
            totalPagadas++
        }
    }

    fun build(totalFacturas: Int): FacturasResumen {
        val descuentos = (ventasBrutas - ventasNetas).coerceAtLeast(0.0)
        val ticketPromedio = if (totalPagadas > 0) ventasNetas / totalPagadas else 0.0
        val tasa = tasaGlobal
        val hasMultiCurrency = !abrMonedaSec.isNullOrBlank() && tasa != null && tasa > 0f

        return FacturasResumen(
            ventasBrutas = ventasBrutas,
            ventasNetas = ventasNetas,
            descuentos = descuentos,
            cancelaciones = cancelaciones,
            totalFacturas = totalFacturas,
            totalFacturasPagadas = totalPagadas,
            totalFacturasAnuladas = totalAnuladas,
            ticketPromedio = ticketPromedio,
            moneda = moneda,
            ventasBrutasRef = if (hasMultiCurrency) ventasBrutasRef else null,
            ventasNetasRef = if (hasMultiCurrency) ventasNetasRef else null,
            cancelacionesRef = if (hasMultiCurrency) cancelacionesRef else null,
            ticketPromedioRef = if (hasMultiCurrency && totalPagadas > 0) ventasNetasRef / totalPagadas else null,
            abrMonedaSecundaria = abrMonedaSec,
        )
    }
}

internal fun buildFacturasResumen(
    rows: List<ResultRow>,
    tabla: BaseFacturasTable,
): FacturasResumen {
    val acc = ResumenAccumulator()
    for (row in rows) {
        acc.add(row, tabla)
    }
    return acc.build(rows.size)
}

internal fun formatDate(
    value: String?,
    formatter: DateTimeFormatter,
): String {
    if (value.isNullOrBlank() || value.startsWith("0000-00-00")) return ""
    return runCatching { LocalDate.parse(value).format(formatter) }.getOrDefault("")
}

internal fun formatDateTime(
    value: String?,
    formatter: DateTimeFormatter,
): String {
    if (value.isNullOrBlank() || value.startsWith("0000-00-00")) return ""
    return runCatching { LocalDateTime.parse(value.replace(' ', 'T')).format(formatter) }
        .getOrDefault("")
}
