package com.amaxoniaerp.features.facturas.data

import com.amaxoniaerp.features.facturas.domain.FacturasResumen
import org.jetbrains.exposed.sql.ResultRow
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private val ZERO_MONEY = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)

private fun Double.toMoney(): BigDecimal = BigDecimal.valueOf(this).setScale(2, RoundingMode.HALF_UP)

/** Acumulador mutable de totales del resumen de facturas (PA y VE). */
private class ResumenAccumulator {
    var ventasBrutas = ZERO_MONEY
    var ventasNetas = ZERO_MONEY
    var cancelaciones = ZERO_MONEY
    var totalPagadas = 0
    var totalAnuladas = 0
    var moneda = "USD"
    var abrMonedaSec: String? = null
    var tasaGlobal: Float? = null
    var ventasBrutasRef = ZERO_MONEY
    var ventasNetasRef = ZERO_MONEY
    var cancelacionesRef = ZERO_MONEY

    fun add(
        row: ResultRow,
        tabla: BaseFacturasTable,
    ) {
        val descripcionEstatus = row[EstatusTable.descripcion] ?: ""
        val codEstatus = row[tabla.codEstatus] ?: 0
        val total = row[tabla.totalTotalFactura].setScale(2, RoundingMode.HALF_UP)
        val totalGeneral = row[tabla.totalizarTotalGeneral].setScale(2, RoundingMode.HALF_UP)
        val isAnulada =
            descripcionEstatus.equals("Anulada", ignoreCase = true) ||
                descripcionEstatus.equals("Anulado", ignoreCase = true) ||
                codEstatus == 3
        val isPagada =
            descripcionEstatus.equals("Pagada", ignoreCase = true) ||
                descripcionEstatus.equals("Pagado", ignoreCase = true) ||
                codEstatus == 2

        if (tabla is FacturasTableVE) {
            addVE(row, tabla, isAnulada, isPagada, total, totalGeneral)
        } else {
            addGeneric(isAnulada, isPagada, total, totalGeneral)
        }
    }

    private fun addVE(
        row: ResultRow,
        tabla: FacturasTableVE,
        isAnulada: Boolean,
        isPagada: Boolean,
        total: BigDecimal,
        totalGeneral: BigDecimal,
    ) {
        val tasaRow = row[tabla.tasa]
        val totalRefRow = (row[tabla.totalRef]?.toDouble() ?: 0.0).toMoney()
        val abrSecRow = row[tabla.abrMonedaSecundaria]

        if (isAnulada) {
            cancelaciones = (cancelaciones + total).setScale(2, RoundingMode.HALF_UP)
            cancelacionesRef = (cancelacionesRef + totalRefRow).setScale(2, RoundingMode.HALF_UP)
            totalAnuladas++
        } else if (isPagada) {
            ventasBrutas = (ventasBrutas + totalGeneral).setScale(2, RoundingMode.HALF_UP)
            ventasNetas = (ventasNetas + total).setScale(2, RoundingMode.HALF_UP)
            ventasBrutasRef = (ventasBrutasRef + totalRefRow).setScale(2, RoundingMode.HALF_UP)
            ventasNetasRef = (ventasNetasRef + totalRefRow).setScale(2, RoundingMode.HALF_UP)
            totalPagadas++
        }

        updateCurrencyMetadata(row, tabla, abrSecRow, tasaRow)
    }

    private fun updateCurrencyMetadata(
        row: ResultRow,
        tabla: FacturasTableVE,
        abrSecRow: String?,
        tasaRow: Float?,
    ) {
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
        isPagada: Boolean,
        total: BigDecimal,
        totalGeneral: BigDecimal,
    ) {
        if (isAnulada) {
            cancelaciones = (cancelaciones + total).setScale(2, RoundingMode.HALF_UP)
            totalAnuladas++
        } else if (isPagada) {
            ventasBrutas = (ventasBrutas + totalGeneral).setScale(2, RoundingMode.HALF_UP)
            ventasNetas = (ventasNetas + total).setScale(2, RoundingMode.HALF_UP)
            totalPagadas++
        }
    }

    fun build(totalFacturas: Int): FacturasResumen {
        val diff = (ventasBrutas - ventasNetas).setScale(2, RoundingMode.HALF_UP)
        val descuentos = if (diff < ZERO_MONEY) ZERO_MONEY else diff
        val ticketPromedio =
            if (totalPagadas > 0) {
                ventasNetas.divide(BigDecimal.valueOf(totalPagadas.toLong()), 2, RoundingMode.HALF_UP)
            } else {
                ZERO_MONEY
            }
        val tasa = tasaGlobal
        val hasMultiCurrency = !abrMonedaSec.isNullOrBlank() && tasa != null && tasa > 0f
        val ticketPromedioRef =
            if (hasMultiCurrency && totalPagadas > 0) {
                ventasNetasRef.divide(BigDecimal.valueOf(totalPagadas.toLong()), 2, RoundingMode.HALF_UP)
            } else {
                null
            }

        return FacturasResumen(
            ventasBrutas = ventasBrutas.toDouble(),
            ventasNetas = ventasNetas.toDouble(),
            descuentos = descuentos.toDouble(),
            cancelaciones = cancelaciones.toDouble(),
            totalFacturas = totalFacturas,
            totalFacturasPagadas = totalPagadas,
            totalFacturasAnuladas = totalAnuladas,
            ticketPromedio = ticketPromedio.toDouble(),
            moneda = moneda,
            ventasBrutasRef = if (hasMultiCurrency) ventasBrutasRef.toDouble() else null,
            ventasNetasRef = if (hasMultiCurrency) ventasNetasRef.toDouble() else null,
            cancelacionesRef = if (hasMultiCurrency) cancelacionesRef.toDouble() else null,
            ticketPromedioRef = ticketPromedioRef?.toDouble(),
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
