package com.amaxoniaerp.features.facturas.data

import com.amaxoniaerp.core.database.dbQuery
import com.amaxoniaerp.features.facturas.domain.ConfirmFacturaFiscalRequest
import com.amaxoniaerp.features.facturas.domain.ConfirmFacturaFiscalResponse
import com.amaxoniaerp.features.facturas.domain.FacturaDetalleItem
import com.amaxoniaerp.features.facturas.domain.FacturaDetalleResponse
import com.amaxoniaerp.features.facturas.domain.FacturaSummary
import com.amaxoniaerp.features.facturas.domain.FacturasResumen
import com.amaxoniaerp.features.sales.data.SalesFacturaDetalleTable
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.SqlExpressionBuilder.like
import org.jetbrains.exposed.sql.andWhere
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.count
import org.jetbrains.exposed.sql.update
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class FacturasRepository {
    suspend fun listFacturas(
        database: Database,
        countryCode: String,
        limit: Int,
        offset: Long,
        search: String?,
        fechaInicio: LocalDate?,
        fechaFin: LocalDate?,
        estatusList: List<Int>?,
    ): Pair<List<FacturaSummary>, Long> = dbQuery(database) {
        val tabla = FacturasTableFactory.forCountry(countryCode)
        val query = tabla
            .join(FacturasClientesTable, JoinType.LEFT, tabla.idCliente, FacturasClientesTable.idCliente)
            .join(EstatusTable, JoinType.LEFT, tabla.codEstatus, EstatusTable.codEstatus)
            .selectAll()

        if (fechaInicio != null && fechaFin != null) {
            val start = fechaInicio.format(DateTimeFormatter.ISO_DATE)
            val end = fechaFin.format(DateTimeFormatter.ISO_DATE)
            query.andWhere { tabla.fechaFactura.between(start, end) }
        }

        if (!estatusList.isNullOrEmpty()) {
            query.andWhere { tabla.codEstatus inList estatusList }
        }

        if (!search.isNullOrBlank()) {
            val term = "%$search%"
            query.andWhere {
                (tabla.codFactura like term) or
                    (FacturasClientesTable.nombre like term) or
                    (FacturasClientesTable.rif like term) or
                    (EstatusTable.descripcion like term)
            }
        }

        val total = query.count()
        val data = query.orderBy(tabla.fechaFactura to SortOrder.DESC)
            .limit(limit)
            .offset(offset)
            .map { row -> mapRowToFacturaSummary(row, tabla, countryCode) }

        data to total
    }

    suspend fun getFacturaDetalle(
        database: Database,
        countryCode: String,
        facturaId: String,
    ): FacturaDetalleResponse? = dbQuery(database) {
        val tabla = FacturasTableFactory.forCountry(countryCode)
        val factura = tabla
            .selectAll()
            .where { tabla.idFactura eq facturaId }
            .limit(1)
            .firstOrNull()
            ?: return@dbQuery null

        val codFactura = factura[tabla.codFactura]

        val items = SalesFacturaDetalleTable
            .selectAll()
            .where { SalesFacturaDetalleTable.idFactura eq facturaId }
            .map { row ->
                FacturaDetalleItem(
                    id = row[SalesFacturaDetalleTable.idDetalleFactura],
                    descripcion = row[SalesFacturaDetalleTable.itemDescripcion],
                    cantidad = row[SalesFacturaDetalleTable.itemCantidadTotal].toDouble(),
                    precioUnitario = row[SalesFacturaDetalleTable.itemPrecioSinIva].toDouble(),
                    totalConIva = row[SalesFacturaDetalleTable.itemTotalConIva].toDouble(),
                    codigo = row[SalesFacturaDetalleTable.itemCodigo],
                    referencia = row[SalesFacturaDetalleTable.itemReferencia],
                )
            }

        FacturaDetalleResponse(
            idFactura = facturaId,
            codFactura = codFactura,
            items = items,
        )
    }

    suspend fun getResumen(
        database: Database,
        countryCode: String,
    ): FacturasResumen = dbQuery(database) {
        val tabla = FacturasTableFactory.forCountry(countryCode)
        val rows = tabla
            .join(EstatusTable, JoinType.LEFT, tabla.codEstatus, EstatusTable.codEstatus)
            .selectAll()
            .toList()

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

        for (row in rows) {
            val descripcionEstatus = row[EstatusTable.descripcion] ?: ""
            val total = row[tabla.totalTotalFactura].toDouble()
            val totalGeneral = row[tabla.totalizarTotalGeneral].toDouble()

            val isAnulada = descripcionEstatus.equals("Anulada", ignoreCase = true) ||
                descripcionEstatus.equals("Anulado", ignoreCase = true)

            if (tabla is FacturasTableVE) {
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
            } else {
                if (isAnulada) {
                    cancelaciones += total
                    totalAnuladas++
                } else {
                    ventasBrutas += totalGeneral
                    ventasNetas += total
                    totalPagadas++
                }
            }
        }

        val descuentos = (ventasBrutas - ventasNetas).coerceAtLeast(0.0)
        val ticketPromedio = if (totalPagadas > 0) ventasNetas / totalPagadas else 0.0
        val hasMultiCurrency = !abrMonedaSec.isNullOrBlank() && tasaGlobal != null && tasaGlobal > 0f

        FacturasResumen(
            ventasBrutas = ventasBrutas,
            ventasNetas = ventasNetas,
            descuentos = descuentos,
            cancelaciones = cancelaciones,
            totalFacturas = rows.size,
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

    suspend fun confirmFiscal(
        database: Database,
        countryCode: String,
        facturaId: String,
        request: ConfirmFacturaFiscalRequest,
    ): ConfirmFacturaFiscalResponse = dbQuery(database) {
        val tabla = FacturasTableFactory.forCountry(countryCode)
        val factura = tabla
            .selectAll()
            .where { tabla.idFactura eq facturaId }
            .limit(1)
            .firstOrNull()
            ?: throw NoSuchElementException("Factura no encontrada")

        val normalizedNumero = request.numeroDocumentoFiscal.trim()
        val normalizedCodFiscal = request.codFacturaFiscal.trim()
        val normalizedSerial = request.impresoraSerial.trim()

        tabla.update({ tabla.idFactura eq facturaId }) {
            if (normalizedNumero.isNotBlank()) it[numeroDocumentoFiscal] = normalizedNumero
            if (normalizedCodFiscal.isNotBlank()) it[codFacturaFiscal] = normalizedCodFiscal
            if (tabla is FacturasTableVE && normalizedSerial.isNotBlank()) {
                it[tabla.impresoraSerial] = normalizedSerial
            }
        }

        ConfirmFacturaFiscalResponse(
            success = true,
            id = facturaId,
            codigo = factura[tabla.codFactura],
            numeroDocumentoFiscal = normalizedNumero,
            codFacturaFiscal = normalizedCodFiscal,
            impresoraSerial = normalizedSerial,
        )
    }

    private fun mapRowToFacturaSummary(row: ResultRow, tabla: BaseFacturasTable, countryCode: String): FacturaSummary {
        val dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
        val dateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")

        val codEstatus = row[tabla.codEstatus] ?: 0
        val formaPago = row[tabla.formaPago]
        val descripcionEstatus = row[EstatusTable.descripcion]

        val estatusFinal = if (codEstatus == 1 && formaPago.equals("contado", ignoreCase = true)) {
            "En Espera"
        } else {
            descripcionEstatus
        }

        val codigoFiscalFinal = if (tabla is FacturasTablePA) {
            val cufe = row[tabla.cufe]
            val codFiscal = row[tabla.codFacturaFiscal]
            if (cufe.isNullOrBlank()) codFiscal else cufe
        } else {
            row[tabla.codFacturaFiscal]
        }

        val nombre = row[FacturasClientesTable.nombre]
        val apellido = row[FacturasClientesTable.apellido] ?: ""
        val nombreCompleto = "$nombre $apellido".trim().uppercase()

        val moneda: String
        val totalRef: Double?
        val tasa: Float?
        val abrMonedaSecundaria: String?
        val fechaDgi: String?

        if (tabla is FacturasTableVE) {
            moneda = row[tabla.abrMonedaBase]?.takeIf { it.isNotBlank() } ?: "USD"
            totalRef = row[tabla.totalRef]?.toDouble()
            tasa = row[tabla.tasa]
            abrMonedaSecundaria = row[tabla.abrMonedaSecundaria]
            fechaDgi = null
        } else {
            val tablaPA = tabla as FacturasTablePA
            moneda = "USD"
            totalRef = null
            tasa = null
            abrMonedaSecundaria = null
            fechaDgi = formatDateTime(row[tablaPA.fechaRecepcionDGI], dateTimeFormatter)
        }

        return FacturaSummary(
            id = row[tabla.idFactura],
            codigo = row[tabla.codFactura],
            codigoFiscal = codigoFiscalFinal ?: "",
            numeroDocumentoFiscal = row[tabla.numeroDocumentoFiscal] ?: "",
            fecha = formatDate(row[tabla.fechaFactura], dateFormatter),
            fechaCreacion = formatDateTime(row[tabla.fechaCreacion], dateTimeFormatter),
            fechaDgi = fechaDgi,
            clienteNombre = nombreCompleto,
            clienteIdentificacion = row[FacturasClientesTable.rif].uppercase(),
            total = row[tabla.totalTotalFactura].toDouble(),
            estatus = estatusFinal,
            formaPago = formaPago,
            moneda = moneda,
            totalRef = totalRef,
            tasa = tasa,
            abrMonedaSecundaria = abrMonedaSecundaria,
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
