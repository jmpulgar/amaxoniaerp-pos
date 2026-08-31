package com.amaxoniaerp.features.creditnotes.data

import com.amaxoniaerp.features.clients.data.ClientsTable
import com.amaxoniaerp.features.creditnotes.domain.CreditNoteDetailResponse
import com.amaxoniaerp.features.creditnotes.domain.CreditNoteSourceInvoiceSummary
import com.amaxoniaerp.features.creditnotes.domain.CreditNoteSummary
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greater
import org.jetbrains.exposed.sql.SqlExpressionBuilder.like
import org.jetbrains.exposed.sql.SqlExpressionBuilder.neq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greaterEq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.less
import org.jetbrains.exposed.sql.SqlExpressionBuilder.lessEq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.andWhere
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

fun CreditNoteRepository.listCreditNotes(
    countryCode: String,
    query: CreditNoteListQuery,
): Pair<List<CreditNoteSummary>, Long> {
    val headerTable = CreditNoteHeaderTableFactory.forCountry(countryCode)
    val select =
        headerTable
            .join(ClientsTable, JoinType.LEFT, headerTable.idCliente, ClientsTable.idCliente)
            .join(CreditNoteFacturaTable, JoinType.LEFT, headerTable.codFactura, CreditNoteFacturaTable.idFactura)
            .selectAll()

    if (query.fechaInicio != null && query.fechaFin != null) {
        select.andWhere { headerTable.fechaDevolucion.between(query.fechaInicio, query.fechaFin) }
    } else if (query.fechaInicio != null) {
        select.andWhere { headerTable.fechaDevolucion greaterEq query.fechaInicio }
    } else if (query.fechaFin != null) {
        select.andWhere { headerTable.fechaDevolucion lessEq query.fechaFin }
    }

    if (!query.search.isNullOrBlank()) {
        val term = "%${query.search}%"
        select.andWhere {
            (headerTable.codDevolucion like term) or
                (CreditNoteFacturaTable.codFactura like term) or
                (ClientsTable.nombre like term) or
                (ClientsTable.apellido like term) or
                (ClientsTable.rif like term)
        }
    }

    val total = select.count()
    val data =
        select
            .orderBy(headerTable.fechaCreacion to SortOrder.DESC)
            .limit(query.limit)
            .offset(query.offset)
            .map { mapSummaryRow(it, countryCode) }

    return data to total
}

fun CreditNoteRepository.getCreditNoteDetail(
    id: String,
    countryCode: String,
): CreditNoteDetailResponse? {
    val headerTable = CreditNoteHeaderTableFactory.forCountry(countryCode)
    val headerRow =
        headerTable
            .join(ClientsTable, JoinType.LEFT, headerTable.idCliente, ClientsTable.idCliente)
            .join(CreditNoteFacturaTable, JoinType.LEFT, headerTable.codFactura, CreditNoteFacturaTable.idFactura)
            .selectAll()
            .where { headerTable.idDevolucion eq id }
            .limit(1)
            .firstOrNull()
            ?: return null

    val detailRows =
        CreditNoteDetailTable
            .join(
                CreditNoteFacturaDetalleTable,
                JoinType.LEFT,
                CreditNoteDetailTable.idDetalleFactura,
                CreditNoteFacturaDetalleTable.idDetalleFactura,
            ).selectAll()
            .where { CreditNoteDetailTable.idDevolucion eq id }
            .toList()

    val lines = detailRows.map(::mapDetailLine)
    val header = mapHeaderContext(headerRow, countryCode)
    return buildDetailResponse(header, lines)
}

fun CreditNoteRepository.listEligibleInvoices(
    countryCode: String,
    limit: Int,
    offset: Long,
    search: String?,
    fechaInicio: LocalDate? = null,
    fechaFin: LocalDate? = null,
): Pair<List<CreditNoteSourceInvoiceSummary>, Long> {
    val query =
        CreditNoteFacturaTable
            .join(ClientsTable, JoinType.LEFT, CreditNoteFacturaTable.idCliente, ClientsTable.idCliente)
            .select(
                CreditNoteFacturaTable.idFactura,
                CreditNoteFacturaTable.codFactura,
                CreditNoteFacturaTable.codFacturaFiscal,
                CreditNoteFacturaTable.numeroDocumentoFiscal,
                CreditNoteFacturaTable.fechaFactura,
                CreditNoteFacturaTable.fechaCreacion,
                CreditNoteFacturaTable.totalTotalFactura,
                CreditNoteFacturaTable.cantidadItems,
                CreditNoteFacturaTable.facturarA,
                CreditNoteFacturaTable.facturarARuc,
                CreditNoteFacturaTable.abrMonedaBase,
                ClientsTable.nombre,
                ClientsTable.apellido,
                ClientsTable.rif,
            )

    if (!countryCode.equals("PA", ignoreCase = true)) {
        query.andWhere { CreditNoteFacturaTable.codEstatus neq ANNULLED_INVOICE_STATUS }
    }
    query.andWhere { CreditNoteFacturaTable.totalTotalFactura greater BigDecimal.ZERO }

    if (fechaInicio != null && fechaFin != null) {
        query.andWhere {
            (CreditNoteFacturaTable.fechaFactura.between(fechaInicio, fechaFin)) or
                (CreditNoteFacturaTable.fechaFactura.isNull() and CreditNoteFacturaTable.fechaCreacion.between(
                    fechaInicio.atStartOfDay(),
                    fechaFin.plusDays(1).atStartOfDay().minusNanos(1),
                ))
        }
    } else if (fechaInicio != null) {
        query.andWhere {
            (CreditNoteFacturaTable.fechaFactura greaterEq fechaInicio) or
                (CreditNoteFacturaTable.fechaFactura.isNull() and (CreditNoteFacturaTable.fechaCreacion greaterEq fechaInicio.atStartOfDay()))
        }
    } else if (fechaFin != null) {
        query.andWhere {
            (CreditNoteFacturaTable.fechaFactura lessEq fechaFin) or
                (CreditNoteFacturaTable.fechaFactura.isNull() and (CreditNoteFacturaTable.fechaCreacion less fechaFin.plusDays(1).atStartOfDay()))
        }
    }

    if (!search.isNullOrBlank()) {
        val term = "%$search%"
        query.andWhere {
            (CreditNoteFacturaTable.codFactura like term) or
                (CreditNoteFacturaTable.numeroDocumentoFiscal like term) or
                (ClientsTable.nombre like term) or
                (ClientsTable.apellido like term) or
                (ClientsTable.rif like term)
        }
    }

    val invoiceRows =
        query
            .orderBy(CreditNoteFacturaTable.fechaCreacion to SortOrder.DESC)
            .limit(limit)
            .offset(offset)
            .toList()

    if (invoiceRows.isEmpty()) {
        return emptyList<CreditNoteSourceInvoiceSummary>() to 0L
    }

    val invoiceIds = invoiceRows.map { it[CreditNoteFacturaTable.idFactura] }

    val detailRows =
        CreditNoteFacturaDetalleTable
            .select(
                CreditNoteFacturaDetalleTable.idFactura,
                CreditNoteFacturaDetalleTable.idDetalleFactura,
            ).where { CreditNoteFacturaDetalleTable.idFactura inList invoiceIds }
            .toList()

    val detailToInvoice = detailRows.associate {
        it[CreditNoteFacturaDetalleTable.idDetalleFactura] to it[CreditNoteFacturaDetalleTable.idFactura]
    }
    val detailIds = detailToInvoice.keys.toList()

    val itemCountsByInvoice = detailRows
        .groupBy { it[CreditNoteFacturaDetalleTable.idFactura] }
        .mapValues { (_, rows) -> rows.size }

    val returnedByInvoice = if (detailIds.isNotEmpty()) {
        val returnedRows = CreditNoteDetailTable
            .select(CreditNoteDetailTable.idDetalleFactura, CreditNoteDetailTable.itemTotalConIva)
            .where { CreditNoteDetailTable.idDetalleFactura inList detailIds }
            .toList()

        returnedRows
            .groupBy { detailToInvoice[it[CreditNoteDetailTable.idDetalleFactura]] }
            .mapValues { (_, rows) ->
                rows.fold(BigDecimal.ZERO) { acc, r -> acc + r[CreditNoteDetailTable.itemTotalConIva] }
            }
    } else {
        emptyMap()
    }

    val summaries =
        invoiceRows.mapNotNull { row ->
            val invoiceId = row[CreditNoteFacturaTable.idFactura]
            val totalOriginal = row[CreditNoteFacturaTable.totalTotalFactura]
            val returnedAmount = returnedByInvoice[invoiceId] ?: BigDecimal.ZERO
            val remaining = totalOriginal - returnedAmount
            val remainingDouble = remaining.setScale(2, RoundingMode.HALF_UP).toDouble()
            if (remainingDouble <= 0.0) {
                null
            } else {
                val clientNombre = row.getOrNull(ClientsTable.nombre).orEmpty()
                val clientApellido = row.getOrNull(ClientsTable.apellido).orEmpty()
                val clientFull = "$clientNombre $clientApellido".trim().ifBlank {
                    row[CreditNoteFacturaTable.facturarA].ifBlank { "CONSUMIDOR FINAL" }
                }
                val clientRif = row.getOrNull(ClientsTable.rif).orEmpty().ifBlank {
                    row[CreditNoteFacturaTable.facturarARuc].ifBlank { "CF" }
                }
                val fecha = formatDate(row[CreditNoteFacturaTable.fechaFactura] ?: row[CreditNoteFacturaTable.fechaCreacion]?.toLocalDate())
                CreditNoteSourceInvoiceSummary(
                    id = invoiceId,
                    codigo = row[CreditNoteFacturaTable.codFactura],
                    codigoFiscal = row[CreditNoteFacturaTable.codFacturaFiscal].orEmpty(),
                    numeroDocumentoFiscal = row[CreditNoteFacturaTable.numeroDocumentoFiscal].orEmpty(),
                    fecha = fecha,
                    clienteNombre = clientFull,
                    clienteIdentificacion = clientRif,
                    total = totalOriginal.toDouble(),
                    remainingAmount = remainingDouble,
                    items = itemCountsByInvoice[invoiceId] ?: row[CreditNoteFacturaTable.cantidadItems],
                    moneda = row[CreditNoteFacturaTable.abrMonedaBase].orEmpty().ifBlank { "USD" },
                )
            }
        }

    return summaries to summaries.size.toLong()
}

fun CreditNoteRepository.getSourceInvoiceDetail(
    invoiceId: String,
    countryCode: String = "VE",
): com.amaxoniaerp.features.creditnotes.domain.CreditNoteSourceInvoiceDetailResponse? =
    buildSourceInvoiceDetail(invoiceId, countryCode)
