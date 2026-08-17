package com.amaxoniaerp.features.facturas.data

import com.amaxoniaerp.core.database.dbQuery
import com.amaxoniaerp.features.facturas.domain.ConfirmFacturaFiscalRequest
import com.amaxoniaerp.features.facturas.domain.ConfirmFacturaFiscalResponse
import com.amaxoniaerp.features.facturas.domain.FacturaDetalleItem
import com.amaxoniaerp.features.facturas.domain.FacturaDetalleResponse
import com.amaxoniaerp.features.facturas.domain.FacturaPrintPayloadResponse
import com.amaxoniaerp.features.facturas.domain.FacturaReconciliadaResponse
import com.amaxoniaerp.features.facturas.domain.FacturaSummary
import com.amaxoniaerp.features.facturas.domain.FacturasResumen
import com.amaxoniaerp.features.mesas.data.CuentaMesaTable
import com.amaxoniaerp.features.mesas.data.SesionMesaTable
import com.amaxoniaerp.features.mesas.domain.EstadoSesionMesa
import com.amaxoniaerp.features.sales.data.SalesFacturaDetalleTable
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.Query
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greaterEq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.SqlExpressionBuilder.less
import org.jetbrains.exposed.sql.SqlExpressionBuilder.like
import org.jetbrains.exposed.sql.andWhere
import org.jetbrains.exposed.sql.count
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.format.DateTimeFormatter

data class FacturasFilter(
    val search: String? = null,
    val usuario: String? = null,
    val sucursalId: Int? = null,
    val fechaInicio: LocalDate? = null,
    val fechaFin: LocalDate? = null,
    val estatusList: List<Int>? = null,
)

private val FACTURA_DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

/**
 * Repositorio de facturas. El resumen vive en FacturasResumenCalculator.kt,
 * el mapeo de summaries en FacturasSummaryMapper.kt y el payload de
 * impresión en FacturasPrintPayload.kt / FacturasPrintSql.kt.
 */
class FacturasRepository {
    suspend fun findByCorrelationId(
        database: Database,
        countryCode: String,
        idFactura: String,
    ): FacturaReconciliadaResponse? =
        dbQuery(database) {
            val tabla = FacturasTableFactory.forCountry(countryCode)
            val factura =
                tabla
                    .selectAll()
                    .where { tabla.idFactura eq idFactura }
                    .limit(1)
                    .singleOrNull()
                    ?: return@dbQuery null
            val sesionCerrada =
                CuentaMesaTable
                    .join(
                        SesionMesaTable,
                        JoinType.INNER,
                        additionalConstraint = { CuentaMesaTable.sesionMesaId eq SesionMesaTable.id },
                    ).select(SesionMesaTable.estado)
                    .where { CuentaMesaTable.idFactura eq idFactura }
                    .limit(1)
                    .singleOrNull()
                    ?.get(SesionMesaTable.estado) == EstadoSesionMesa.CERRADA_PAGADA.codigo
            FacturaReconciliadaResponse(
                idFactura = factura[tabla.idFactura],
                codFactura = factura[tabla.codFactura],
                codEstatus = factura[tabla.codEstatus] ?: 0,
                sesionMesaCerrada = sesionCerrada,
            )
        }

    suspend fun listFacturas(
        database: Database,
        countryCode: String,
        limit: Int,
        offset: Long,
        filter: FacturasFilter,
    ): Pair<List<FacturaSummary>, Long> =
        dbQuery(database) {
            val tabla = FacturasTableFactory.forCountry(countryCode)
            val query =
                tabla
                    .join(FacturasClientesTable, JoinType.LEFT, tabla.idCliente, FacturasClientesTable.idCliente)
                    .join(EstatusTable, JoinType.LEFT, tabla.codEstatus, EstatusTable.codEstatus)
                    .selectAll()

            query.applyInvoiceFilters(tabla, filter)

            val total = query.count()
            val data =
                query
                    .orderBy(tabla.fechaFactura to SortOrder.DESC)
                    .limit(limit)
                    .offset(offset)
                    .map { row -> mapRowToFacturaSummary(row, tabla) }

            data to total
        }

    suspend fun getFacturaDetalle(
        database: Database,
        countryCode: String,
        facturaId: String,
    ): FacturaDetalleResponse? =
        dbQuery(database) {
            val tabla = FacturasTableFactory.forCountry(countryCode)
            val factura =
                tabla
                    .selectAll()
                    .where { tabla.idFactura eq facturaId }
                    .limit(1)
                    .firstOrNull()
                    ?: return@dbQuery null

            val codFactura = factura[tabla.codFactura]

            val items =
                SalesFacturaDetalleTable
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
        filter: FacturasFilter = FacturasFilter(),
    ): FacturasResumen =
        dbQuery(database) {
            val tabla = FacturasTableFactory.forCountry(countryCode)
            val query =
                tabla
                    .join(EstatusTable, JoinType.LEFT, tabla.codEstatus, EstatusTable.codEstatus)
                    .selectAll()
            query.applyInvoiceFilters(tabla, filter)
            val rows = query.toList()

            buildFacturasResumen(rows, tabla)
        }

    private fun Query.applyInvoiceFilters(
        tabla: BaseFacturasTable,
        filter: FacturasFilter,
    ) {
        filter.usuario?.takeIf(String::isNotBlank)?.let { usuario ->
            andWhere { tabla.usuarioCreacion eq usuario }
        }
        filter.sucursalId?.let { sucursalId ->
            andWhere { tabla.idSucursal eq sucursalId }
        }
        filter.fechaInicio?.let { fechaInicio ->
            val start = fechaInicio.atStartOfDay().format(FACTURA_DATE_TIME_FORMAT)
            andWhere { tabla.fechaCreacion greaterEq start }
        }
        filter.fechaFin?.let { fechaFin ->
            val endExclusive =
                fechaFin
                    .plusDays(1)
                    .atStartOfDay()
                    .format(FACTURA_DATE_TIME_FORMAT)
            andWhere { tabla.fechaCreacion less endExclusive }
        }
        filter.estatusList?.takeIf(List<Int>::isNotEmpty)?.let { estatusList ->
            andWhere { tabla.codEstatus inList estatusList }
        }
        filter.search?.takeIf(String::isNotBlank)?.let { search ->
            val term = "%$search%"
            andWhere {
                (tabla.codFactura like term) or
                    (FacturasClientesTable.nombre like term) or
                    (FacturasClientesTable.rif like term) or
                    (EstatusTable.descripcion like term)
            }
        }
    }

    suspend fun confirmFiscal(
        database: Database,
        countryCode: String,
        facturaId: String,
        request: ConfirmFacturaFiscalRequest,
    ): ConfirmFacturaFiscalResponse =
        dbQuery(database) {
            val tabla = FacturasTableFactory.forCountry(countryCode)
            val factura =
                tabla
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

    suspend fun getPrintPayload(
        database: Database,
        countryCode: String,
        facturaId: String,
        companyNameFallback: String,
    ): FacturaPrintPayloadResponse? =
        dbQuery(database) {
            val isPanama = countryCode.equals("PA", ignoreCase = true)
            val isVenezuela = countryCode.equals("VE", ignoreCase = true)
            require(isPanama || isVenezuela) {
                "El payload de impresión solo está disponible para Panamá y Venezuela"
            }

            val factura = queryOne(printFacturaSql(facturaId, isPanama)) ?: return@dbQuery null

            val productos = printProductos(facturaId)

            val subtotal = factura.decimal("subtotal")
            val baseImponible = factura.decimal("totalizar_base_imponible")
            val totalImpuesto = factura.decimal("totalizar_monto_iva")
            val montoExento = (subtotal - baseImponible).coerceAtLeast(BigDecimal.ZERO)
            val total = factura.decimal("TotalTotalFactura")
            // Total discount aggregated from the per-line `_item_montodescuento`. Computed entirely in
            // BigDecimal (no Double arithmetic) so the printed value matches the on-screen Cobro and
            // the line on the physical receipt. The receipt always prints this row even when zero so
            // customers/cashiers see the same breakdown regardless of cart contents.
            val descuentoTotal =
                productos
                    .fold(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)) { acc, producto ->
                        acc + (producto.descuento.toBigDecimalOrNull() ?: BigDecimal.ZERO)
                    }.setScale(2, RoundingMode.HALF_UP)
            val pagos = printPagos(facturaId, factura, total)
            val cambio = printCambio(facturaId)

            buildPrintResponse(
                PrintBuildContext(
                    factura = factura,
                    productos = productos,
                    pagos = pagos,
                    cambio = cambio,
                    subtotal = subtotal,
                    montoExento = montoExento,
                    totalImpuesto = totalImpuesto,
                    total = total,
                    descuentoTotal = descuentoTotal,
                    isPanama = isPanama,
                    companyNameFallback = companyNameFallback,
                ),
            )
        }
}
