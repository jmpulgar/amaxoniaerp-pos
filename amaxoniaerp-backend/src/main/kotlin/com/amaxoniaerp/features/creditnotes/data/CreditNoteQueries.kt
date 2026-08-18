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
import org.jetbrains.exposed.sql.andWhere
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.selectAll
import java.math.BigDecimal

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
): Pair<List<CreditNoteSourceInvoiceSummary>, Long> {
    val query =
        CreditNoteFacturaTable
            .join(ClientsTable, JoinType.LEFT, CreditNoteFacturaTable.idCliente, ClientsTable.idCliente)
            .selectAll()

    if (!countryCode.equals("PA", ignoreCase = true)) {
        query.andWhere { CreditNoteFacturaTable.codEstatus neq ANNULLED_INVOICE_STATUS }
    }
    query.andWhere { CreditNoteFacturaTable.totalTotalFactura greater BigDecimal.ZERO }

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

    val summaries =
        invoiceRows.mapNotNull { row ->
            val invoiceId = row[CreditNoteFacturaTable.idFactura]
            val source = buildSourceInvoiceDetail(invoiceId, countryCode) ?: return@mapNotNull null
            if (source.remainingAmount <= 0.0) {
                null
            } else {
                CreditNoteSourceInvoiceSummary(
                    id = source.id,
                    codigo = source.codigo,
                    codigoFiscal = source.codigoFiscal,
                    numeroDocumentoFiscal = source.numeroDocumentoFiscal,
                    fecha = source.fecha,
                    clienteNombre = source.clienteNombre,
                    clienteIdentificacion = source.clienteIdentificacion,
                    total = source.totalOriginal,
                    remainingAmount = source.remainingAmount,
                    items = source.lines.count { it.cantidadDisponible > 0.0 },
                    moneda = source.moneda,
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
