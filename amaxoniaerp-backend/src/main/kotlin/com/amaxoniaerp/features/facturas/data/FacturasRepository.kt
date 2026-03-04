package com.amaxoniaerp.features.facturas.data

import com.amaxoniaerp.core.database.dbQuery
import com.amaxoniaerp.features.facturas.domain.FacturaSummary
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.SqlExpressionBuilder.like
import org.jetbrains.exposed.sql.andWhere
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.selectAll
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class FacturasRepository {
    suspend fun listFacturas(
        database: Database,
        limit: Int,
        offset: Long,
        search: String?,
        fechaInicio: LocalDate?,
        fechaFin: LocalDate?,
        estatusList: List<Int>?,
    ): Pair<List<FacturaSummary>, Long> = dbQuery(database) {
        val query = FacturasTable
            .join(FacturasClientesTable, JoinType.LEFT, FacturasTable.idCliente, FacturasClientesTable.idCliente)
            .join(EstatusTable, JoinType.LEFT, FacturasTable.codEstatus, EstatusTable.codEstatus)
            .selectAll()

        if (fechaInicio != null && fechaFin != null) {
            val start = fechaInicio.format(DateTimeFormatter.ISO_DATE)
            val end = fechaFin.format(DateTimeFormatter.ISO_DATE)
            query.andWhere { FacturasTable.fechaFactura.between(start, end) }
        }

        if (!estatusList.isNullOrEmpty()) {
            query.andWhere { FacturasTable.codEstatus inList estatusList }
        }

        if (!search.isNullOrBlank()) {
            val term = "%$search%"
            query.andWhere {
                (FacturasTable.codFactura like term) or
                    (FacturasClientesTable.nombre like term) or
                    (FacturasClientesTable.rif like term) or
                    (EstatusTable.descripcion like term)
            }
        }

        val total = query.count()
        val data = query.orderBy(FacturasTable.fechaFactura to SortOrder.DESC)
            .limit(limit)
            .offset(offset)
            .map { row -> mapRowToFacturaSummary(row) }

        data to total
    }

    private fun mapRowToFacturaSummary(row: ResultRow): FacturaSummary {
        val dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
        val dateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")

        val codEstatus = row[FacturasTable.codEstatus] ?: 0
        val formaPago = row[FacturasTable.formaPago]
        val descripcionEstatus = row[EstatusTable.descripcion]

        val estatusFinal = if (codEstatus == 1 && formaPago.equals("contado", ignoreCase = true)) {
            "En Espera"
        } else {
            descripcionEstatus
        }

        val cufe = row[FacturasTable.cufe]
        val codFiscal = row[FacturasTable.codFacturaFiscal]
        val codigoFiscalFinal = if (cufe.isNullOrBlank()) codFiscal else cufe

        val nombre = row[FacturasClientesTable.nombre]
        val apellido = row[FacturasClientesTable.apellido] ?: ""
        val nombreCompleto = "$nombre $apellido".trim().uppercase()

        return FacturaSummary(
            id = row[FacturasTable.idFactura],
            codigo = row[FacturasTable.codFactura],
            codigoFiscal = codigoFiscalFinal ?: "",
            numeroDocumentoFiscal = row[FacturasTable.numeroDocumentoFiscal] ?: "",
            fecha = formatDate(row[FacturasTable.fechaFactura], dateFormatter),
            fechaDgi = formatDateTime(row[FacturasTable.fechaRecepcionDGI], dateTimeFormatter),
            clienteNombre = nombreCompleto,
            clienteIdentificacion = row[FacturasClientesTable.rif].uppercase(),
            total = row[FacturasTable.totalTotalFactura].toDouble(),
            estatus = estatusFinal,
            formaPago = formaPago,
        )
    }

    private fun formatDate(value: String?, formatter: DateTimeFormatter): String {
        if (value.isNullOrBlank() || value.startsWith("0000-00-00")) return ""
        return runCatching { LocalDate.parse(value).format(formatter) }.getOrDefault("")
    }

    private fun formatDateTime(value: String?, formatter: DateTimeFormatter): String {
        if (value.isNullOrBlank() || value.startsWith("0000-00-00")) return ""
        return runCatching { LocalDateTime.parse(value.replace(' ', 'T')).format(formatter) }
            .getOrDefault("")
    }
}
