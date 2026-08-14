package com.amaxoniaerp.features.facturas.data

import com.amaxoniaerp.core.database.dbQuery
import com.amaxoniaerp.features.facturas.domain.ConfirmFacturaFiscalRequest
import com.amaxoniaerp.features.facturas.domain.ConfirmFacturaFiscalResponse
import com.amaxoniaerp.features.facturas.domain.FacturaDetalleItem
import com.amaxoniaerp.features.facturas.domain.FacturaDetalleResponse
import com.amaxoniaerp.features.facturas.domain.FacturaReconciliadaResponse
import com.amaxoniaerp.features.facturas.domain.ClientePrintResponse
import com.amaxoniaerp.features.facturas.domain.EmpresaPrintResponse
import com.amaxoniaerp.features.facturas.domain.FacturaPrintPayloadResponse
import com.amaxoniaerp.features.facturas.domain.FacturaSummary
import com.amaxoniaerp.features.facturas.domain.FacturasResumen
import com.amaxoniaerp.features.facturas.domain.PagoPrintResponse
import com.amaxoniaerp.features.facturas.domain.ProductoPrintResponse
import com.amaxoniaerp.features.sales.data.SalesFacturaDetalleTable
import com.amaxoniaerp.features.mesas.data.CuentaMesaTable
import com.amaxoniaerp.features.mesas.data.SesionMesaTable
import com.amaxoniaerp.features.mesas.domain.EstadoSesionMesa
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.SqlExpressionBuilder.like
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greaterEq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.less
import org.jetbrains.exposed.sql.Query
import org.jetbrains.exposed.sql.andWhere
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.count
import org.jetbrains.exposed.sql.update
import org.jetbrains.exposed.sql.transactions.TransactionManager
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.math.BigDecimal
import java.math.RoundingMode

data class FacturasFilter(
    val search: String? = null,
    val usuario: String? = null,
    val sucursalId: Int? = null,
    val fechaInicio: LocalDate? = null,
    val fechaFin: LocalDate? = null,
    val estatusList: List<Int>? = null,
)

private val FACTURA_DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

class FacturasRepository {
    suspend fun findByCorrelationId(
        database: Database,
        countryCode: String,
        idFactura: String,
    ): FacturaReconciliadaResponse? = dbQuery(database) {
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
                )
                .select(SesionMesaTable.estado)
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
    ): Pair<List<FacturaSummary>, Long> = dbQuery(database) {
        val tabla = FacturasTableFactory.forCountry(countryCode)
        val query = tabla
            .join(FacturasClientesTable, JoinType.LEFT, tabla.idCliente, FacturasClientesTable.idCliente)
            .join(EstatusTable, JoinType.LEFT, tabla.codEstatus, EstatusTable.codEstatus)
            .selectAll()

        query.applyInvoiceFilters(tabla, filter)

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
        filter: FacturasFilter = FacturasFilter(),
    ): FacturasResumen = dbQuery(database) {
        val tabla = FacturasTableFactory.forCountry(countryCode)
        val query = tabla
            .join(EstatusTable, JoinType.LEFT, tabla.codEstatus, EstatusTable.codEstatus)
            .selectAll()
        query.applyInvoiceFilters(tabla, filter)
        val rows = query.toList()

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

    suspend fun getPrintPayload(
        database: Database,
        countryCode: String,
        facturaId: String,
        companyNameFallback: String,
    ): FacturaPrintPayloadResponse? = dbQuery(database) {
        val isPanama = countryCode.equals("PA", ignoreCase = true)
        val isVenezuela = countryCode.equals("VE", ignoreCase = true)
        if (!isPanama && !isVenezuela) {
            throw IllegalArgumentException("El payload de impresión solo está disponible para Panamá y Venezuela")
        }
        val countrySpecificFields =
            if (isPanama) {
                """
                cs.nombre_sucursal AS cliente_sucursal_nombre,
                cs.direccion AS cliente_sucursal_direccion,
                f.cufe,
                f.qr,
                f.fechaRecepcionDGI,
                f.puntoFacturacionFiscal,
                f.nroProtocoloAutorizacion,
                s.codigo_sucursal_emisor AS sucursal_codigo,
                NULL AS numero_control_thka,
                """.trimIndent()
            } else {
                """
                NULL AS cliente_sucursal_nombre,
                NULL AS cliente_sucursal_direccion,
                NULL AS cufe,
                NULL AS qr,
                NULL AS fechaRecepcionDGI,
                NULL AS puntoFacturacionFiscal,
                NULL AS nroProtocoloAutorizacion,
                NULL AS sucursal_codigo,
                f.numero_control_thka AS numero_control_thka,
                """.trimIndent()
            }
        val clientBranchJoin =
            if (isPanama) {
                "LEFT JOIN cliente_sucursal cs ON cs.sucursal_id = f.cliente_sucursal_id"
            } else {
                ""
            }

        val factura = queryOne(
            """
            SELECT
                f.id_factura,
                f.cod_factura,
                f.numeroDocumentoFiscal,
                f.fechaFactura,
                f.facturar_a,
                f.facturar_a_ruc,
                f.facturar_a_direccion,
                f.facturar_a_telefono,
                f.usuario_creacion,
                f.subtotal,
                f.totalizar_base_imponible,
                f.totalizar_monto_iva,
                f.TotalTotalFactura,
                f.totalizar_total_general,
                f.formapago,
                $countrySpecificFields
                pg.rif AS empresa_ruc,
                c.descripcion AS caja_descripcion,
                c.codigo AS caja_codigo,
                s.sucursal AS sucursal_nombre,
                s.descripcion AS sucursal_descripcion
            FROM factura f
            LEFT JOIN parametros_generales pg ON 1 = 1
            LEFT JOIN caja c ON c.id = f.id_caja
            LEFT JOIN sucursal s ON s.id = f.id_sucursal
            $clientBranchJoin
            WHERE f.id_factura = '${facturaId.sqlLiteral()}'
            LIMIT 1
            """.trimIndent(),
        ) ?: return@dbQuery null

        val productos = queryMany(
            """
            SELECT
                _item_descripcion,
                _item_codigo,
                _item_cantidad_total,
                _item_preciosiniva,
                _item_montodescuento,
                _item_piva,
                _item_totalsiniva,
                _item_totalconiva,
                _item_unidad_empaque
            FROM factura_detalle
            WHERE id_factura = '${facturaId.sqlLiteral()}'
            ORDER BY fecha_creacion ASC
            """.trimIndent(),
        ).map { row ->
            val totalSinIva = row.decimal("_item_totalsiniva")
            val taxRate = row.decimal("_item_piva")
            val impuesto = totalSinIva.multiply(taxRate).divide(BigDecimal("100"), 2, RoundingMode.HALF_UP)
            ProductoPrintResponse(
                nombre = row.string("_item_descripcion"),
                cantidad = row.decimal("_item_cantidad_total").toMoneyString(trimZeros = true),
                unidad = row.stringOrNull("_item_unidad_empaque"),
                precioUnitario = row.decimal("_item_preciosiniva").toMoneyString(),
                descuento = row.decimal("_item_montodescuento").toMoneyString(),
                impuesto = impuesto.toMoneyString(),
                total = row.decimal("_item_totalconiva").toMoneyString(),
                codigo = row.stringOrNull("_item_codigo"),
                tasaImpuesto = taxRate.toMoneyString(trimZeros = true),
            )
        }

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
                }
                .setScale(2, RoundingMode.HALF_UP)
        val pagos =
            queryMany(
                """
                SELECT
                    cfp.descripcion AS metodo,
                    cnd.monto
                FROM caja_nueva cn
                INNER JOIN caja_nueva_detalle cnd ON cnd.caja_id = cn.caja_id
                LEFT JOIN caja_forma_pago cfp ON cfp.id_forma_pago = cnd.id_forma_pago
                WHERE cn.id_factura = '${facturaId.sqlLiteral()}'
                  AND cnd.monto > 0
                ORDER BY cnd.caja_detalle_id ASC
                """.trimIndent(),
            ).map { row ->
                PagoPrintResponse(
                    metodo = row.stringOrNull("metodo").orEmpty().ifBlank { "PAGO" },
                    monto = row.decimal("monto").toMoneyString(),
                )
            }.ifEmpty {
                listOf(
                    PagoPrintResponse(
                        metodo = factura.stringOrNull("formapago").orEmpty().ifBlank { "PAGO" },
                        monto = total.toMoneyString(),
                    ),
                )
            }
        val cambio =
            queryOne(
                """
                SELECT totalizar_cambio
                FROM factura_detalle_formapago
                WHERE id_factura = '${facturaId.sqlLiteral()}'
                LIMIT 1
                """.trimIndent(),
            )?.decimalOrNull("totalizar_cambio")

        FacturaPrintPayloadResponse(
            facturaId = factura.string("id_factura"),
            numeroFactura = factura.string("cod_factura"),
            fecha = factura.stringOrNull("fechaFactura").orEmpty(),
            empresa = EmpresaPrintResponse(
                nombre = companyNameFallback.ifBlank { "Amaxonia ERP" },
                ruc = factura.stringOrNull("empresa_ruc"),
                direccion = null,
                telefono = null,
                tienda = factura.stringOrNull("sucursal_nombre") ?: factura.stringOrNull("sucursal_descripcion"),
                caja = factura.stringOrNull("caja_descripcion") ?: factura.stringOrNull("caja_codigo"),
            ),
            cliente = ClientePrintResponse(
                nombre = factura.stringOrNull("facturar_a").orEmpty().ifBlank { "Cliente General" },
                documento = factura.stringOrNull("facturar_a_ruc"),
                sucursal = factura.stringOrNull("cliente_sucursal_nombre"),
                sucursalDireccion = factura.stringOrNull("cliente_sucursal_direccion"),
            ),
            vendedor = factura.stringOrNull("usuario_creacion"),
            productos = productos,
            subtotal = subtotal.toMoneyString(),
            montoExento = montoExento.toMoneyString(),
            // Total line-item discount aggregated in BigDecimal above. Always present in the
            // payload (never null) so the Android formatter can render the row unconditionally
            // and stay coherent with the on-screen Cobro.
            descuento = descuentoTotal.toMoneyString(),
            totalImpuesto = totalImpuesto.toMoneyString(),
            total = total.toMoneyString(),
            pagos = pagos,
            cambio = cambio?.toMoneyString(),
            qrUrl = factura.stringOrNull("qr"),
            cufe = factura.stringOrNull("cufe"),
            fechaRecepcionDgi = factura.stringOrNull("fechaRecepcionDGI"),
            proveedorAutorizado = if (isPanama) "The Factory HKA Corp." else null,
            numeroDocumentoFiscal = factura.stringOrNull("numeroDocumentoFiscal"),
            puntoFacturacionFiscal = factura.stringOrNull("puntoFacturacionFiscal"),
            codigoSucursal = factura.stringOrNull("sucursal_codigo"),
            protocoloAutorizacion = factura.stringOrNull("nroProtocoloAutorizacion"),
            // FASE 2.3b — Exponer número de control HKA persistido (Venezuela digital).
            // En Panamá el SELECT emite NULL; aquí nunca se inventa un valor.
            numeroControlThka = factura.stringOrNull("numero_control_thka"),
        )
    }

    private fun queryOne(sql: String): SqlRow? = queryMany(sql).firstOrNull()

    private fun queryMany(sql: String): List<SqlRow> {
        return TransactionManager.current().exec(sql) { rs ->
            val meta = rs.metaData
            val columns = (1..meta.columnCount).map { index -> meta.getColumnLabel(index) }
            val rows = mutableListOf<SqlRow>()
            while (rs.next()) {
                rows += SqlRow(columns.associateWith { column -> rs.getObject(column) })
            }
            rows
        } ?: emptyList()
    }

    private data class SqlRow(private val values: Map<String, Any?>) {
        fun string(column: String): String = stringOrNull(column).orEmpty()
        fun stringOrNull(column: String): String? = values[column]?.toString()?.takeIf { it.isNotBlank() }
        fun decimal(column: String): BigDecimal = decimalOrNull(column) ?: BigDecimal.ZERO
        fun decimalOrNull(column: String): BigDecimal? = when (val value = values[column]) {
            null -> null
            is BigDecimal -> value
            is Number -> BigDecimal.valueOf(value.toDouble())
            else -> value.toString().toBigDecimalOrNull()
        }
    }

    private fun BigDecimal.toMoneyString(trimZeros: Boolean = false): String {
        val scaled = setScale(2, RoundingMode.HALF_UP)
        return if (trimZeros) scaled.stripTrailingZeros().toPlainString() else scaled.toPlainString()
    }

    private fun String.sqlLiteral(): String = replace("'", "''")

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
