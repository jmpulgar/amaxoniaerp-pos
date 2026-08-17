package com.amaxoniaerp.features.facturas.data

import com.amaxoniaerp.features.facturas.domain.FacturaSummary
import org.jetbrains.exposed.sql.ResultRow
import java.time.format.DateTimeFormatter

/** Campos monetarios/fiscales del summary que difieren entre PA y VE. */
private data class SummaryMonetaryFields(
    val moneda: String,
    val totalRef: Double?,
    val tasa: Float?,
    val abrMonedaSecundaria: String?,
    val fechaDgi: String?,
)

internal fun mapRowToFacturaSummary(
    row: ResultRow,
    tabla: BaseFacturasTable,
): FacturaSummary {
    val dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
    val dateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")

    val codEstatus = row[tabla.codEstatus] ?: 0
    val formaPago = row[tabla.formaPago]
    val descripcionEstatus = row[EstatusTable.descripcion]

    val estatusFinal =
        if (codEstatus == 1 && formaPago.equals("contado", ignoreCase = true)) {
            "En Espera"
        } else {
            descripcionEstatus
        }

    val codigoFiscalFinal = resolveCodigoFiscal(row, tabla)

    val nombre = row[FacturasClientesTable.nombre]
    val apellido = row[FacturasClientesTable.apellido] ?: ""
    val nombreCompleto = "$nombre $apellido".trim().uppercase()

    val monetary = resolveSummaryMonetaryFields(row, tabla, dateTimeFormatter)

    return FacturaSummary(
        id = row[tabla.idFactura],
        codigo = row[tabla.codFactura],
        codigoFiscal = codigoFiscalFinal ?: "",
        numeroDocumentoFiscal = row[tabla.numeroDocumentoFiscal] ?: "",
        fecha = formatDate(row[tabla.fechaFactura], dateFormatter),
        fechaCreacion = formatDateTime(row[tabla.fechaCreacion], dateTimeFormatter),
        fechaDgi = monetary.fechaDgi,
        clienteNombre = nombreCompleto,
        clienteIdentificacion = row[FacturasClientesTable.rif].uppercase(),
        total = row[tabla.totalTotalFactura].toDouble(),
        estatus = estatusFinal,
        formaPago = formaPago,
        moneda = monetary.moneda,
        totalRef = monetary.totalRef,
        tasa = monetary.tasa,
        abrMonedaSecundaria = monetary.abrMonedaSecundaria,
    )
}

private fun resolveCodigoFiscal(
    row: ResultRow,
    tabla: BaseFacturasTable,
): String? =
    if (tabla is FacturasTablePA) {
        val cufe = row[tabla.cufe]
        val codFiscal = row[tabla.codFacturaFiscal]
        if (cufe.isNullOrBlank()) codFiscal else cufe
    } else {
        row[tabla.codFacturaFiscal]
    }

private fun resolveSummaryMonetaryFields(
    row: ResultRow,
    tabla: BaseFacturasTable,
    dateTimeFormatter: java.time.format.DateTimeFormatter,
): SummaryMonetaryFields =
    if (tabla is FacturasTableVE) {
        SummaryMonetaryFields(
            moneda = row[tabla.abrMonedaBase]?.takeIf { it.isNotBlank() } ?: "USD",
            totalRef = row[tabla.totalRef]?.toDouble(),
            tasa = row[tabla.tasa],
            abrMonedaSecundaria = row[tabla.abrMonedaSecundaria],
            fechaDgi = null,
        )
    } else {
        val tablaPA = tabla as FacturasTablePA
        SummaryMonetaryFields(
            moneda = "USD",
            totalRef = null,
            tasa = null,
            abrMonedaSecundaria = null,
            fechaDgi = formatDateTime(row[tablaPA.fechaRecepcionDGI], dateTimeFormatter),
        )
    }
