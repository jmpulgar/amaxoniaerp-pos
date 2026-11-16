package com.amaxoniaerp.features.cobranza.data

import com.amaxoniaerp.core.database.LegacySql.columnsOf
import com.amaxoniaerp.core.database.LegacySql.execQuery
import com.amaxoniaerp.core.database.LegacySql.execUpdate
import com.amaxoniaerp.core.database.LegacySql.reserveCorrelativo
import com.amaxoniaerp.core.database.dbQuery
import com.amaxoniaerp.core.database.formatCorrelativo
import com.amaxoniaerp.core.time.BusinessClock
import com.amaxoniaerp.features.caja.data.CajaSecuenciaTable
import com.amaxoniaerp.features.caja.data.findOpenSecuenciaRows
import com.amaxoniaerp.features.cobranza.domain.CobranzaFacturaResult
import com.amaxoniaerp.features.cobranza.domain.CobranzaPagoInput
import com.amaxoniaerp.features.cobranza.domain.CobranzaPendingInvoice
import com.amaxoniaerp.features.cobranza.domain.CobranzaRules
import com.amaxoniaerp.features.cobranza.domain.CobranzaStateException
import com.amaxoniaerp.features.cobranza.domain.CobranzaValidationException
import com.amaxoniaerp.features.cobranza.domain.CreateCobranzaRequest
import com.amaxoniaerp.features.cobranza.domain.CreateCobranzaResponse
import com.amaxoniaerp.features.cobranza.domain.PaymentAllocation
import com.amaxoniaerp.features.sales.data.CajaIngresoEgreso
import com.amaxoniaerp.features.sales.data.CajaStatus
import com.amaxoniaerp.features.sales.data.SalesCajaNuevaDetalleFormaPagoTable
import com.amaxoniaerp.features.sales.data.SalesCajaNuevaDetalleTableFactory
import com.amaxoniaerp.features.sales.data.SalesCajaNuevaDetalleTableVE
import com.amaxoniaerp.features.sales.data.SalesCajaNuevaReciboTableFactory
import com.amaxoniaerp.features.sales.data.SalesCajaNuevaReciboTableVE
import com.amaxoniaerp.features.sales.data.SalesCajaNuevaTableFactory
import com.amaxoniaerp.features.sales.data.resolveCajaDetalleFormaPagoTipoMovimiento
import com.amaxoniaerp.features.sales.domain.SalePaymentInput
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.TransactionManager
import java.math.BigDecimal
import java.math.RoundingMode
import java.sql.ResultSet
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

private const val CORRELATIVO_COBRANZA = "cod_caja_ingr_cobranza"
private const val USER_LENGTH = 20
private const val ID_LENGTH = 36
private const val REFERENCIA_LENGTH = 20
private const val SERIE_LENGTH = 10
private val SALDO_TOLERANCE = BigDecimal("0.005")

/** Fila CxC (`caja_nueva` FACT) bloqueada para el cobro. */
private data class CxcRow(
    val cajaId: String,
    val idFactura: String,
    val codFactura: String,
    val idCliente: String,
    val status: String,
    val idCajaSecuencia: String,
    val saldo: BigDecimal,
)

/** Porción de pago aplicada a una factura, con su fila ICC y el vuelto que le corresponde. */
private data class AllocationTarget(
    val row: CxcRow,
    val iccCajaId: String,
    val allocation: PaymentAllocation,
    val cambio: Double,
)

/** Fila `caja_nueva_detalle` a insertar. */
private data class DetalleSpec(
    val cajaId: String,
    val cajaReciboId: String,
    val pago: CobranzaPagoInput,
    val monto: BigDecimal,
    val concepto: String,
)

/** Datos comunes de escritura de un cobro. */
private data class CobroContext(
    val request: CreateCobranzaRequest,
    val countryCode: String,
    val idSecuencia: String,
    val nroRecibo: String,
    val clienteNombre: String,
    val username: String,
    val now: LocalDateTime,
) {
    val today: LocalDate get() = now.toLocalDate()
}

/**
 * Cobranza de facturas pendientes a clientes (equivale a `cobranza_cliente/save` del web,
 * corrigiendo sus fallas conocidas: un único recibo, un detalle por forma de pago y factura,
 * montos por factura y no el total).
 *
 * Por cada factura cobrada:
 * - `caja_nueva_detalle` sobre la fila FACT de la factura (con `caja_recibo_id` del recibo) →
 *   reduce el saldo de la CxC; es lo que anula el web (`cobranza_cliente/anular`).
 * - una fila `caja_nueva` ICC (`status='Pagada'`) en la secuencia de caja actual, con su
 *   espejo de detalle por forma de pago → el ingreso cuadra en el cierre de caja.
 * - si el saldo llega a cero: FACT `status='Pagada'` y `factura.cod_estatus=2`.
 */
class CobranzaRepository {
    suspend fun pendientes(
        database: Database,
        idCliente: String,
    ): List<CobranzaPendingInvoice> =
        dbQuery(database) {
            val tx = TransactionManager.current()
            val fiscalColumn =
                if ("numerodocumentofiscal" in tx.columnsOf("factura")) "f.numeroDocumentoFiscal" else "''"
            tx
                .execQuery(
                    """
                    SELECT c.caja_id, c.id_factura, c.monto, c.fecha, f.cod_factura, c.comprobante_numero,
                           $fiscalColumn AS numero_fiscal, f.fecha_vencimiento,
                           ($SALDO_EXPR) AS saldo
                    FROM caja_nueva c
                    LEFT JOIN factura f ON f.id_factura = c.id_factura
                    WHERE c.status = 'Pendiente' AND c.comprobante = 'FACT'
                      AND COALESCE(f.id_cliente, c.id_cliente) = ?
                    ORDER BY c.fecha, c.fecha_creacion
                    """.trimIndent(),
                    listOf(idCliente),
                ) { rs ->
                    CobranzaPendingInvoice(
                        idFactura = rs.getString("id_factura").orEmpty(),
                        codFactura = rs.getString("cod_factura") ?: rs.getString("comprobante_numero").orEmpty(),
                        numeroFiscal = rs.getString("numero_fiscal").orEmpty(),
                        fecha =
                            rs
                                .getDate("fecha")
                                ?.toLocalDate()
                                ?.toString()
                                .orEmpty(),
                        fechaVencimiento = rs.getDateOrNull("fecha_vencimiento"),
                        montoOriginal = rs.money("monto").toDouble(),
                        saldo = rs.money("saldo").toDouble(),
                    )
                }.filter { it.saldo > 0.0 && it.idFactura.isNotBlank() }
        }

    suspend fun create(
        database: Database,
        countryCode: String,
        request: CreateCobranzaRequest,
        username: String,
    ): CreateCobranzaResponse =
        dbQuery(database) {
            CobranzaRules.validate(request)
            val tx = TransactionManager.current()
            tx.findExistingRecibo(request.idCobranza)?.let { return@dbQuery it }

            val siglasByForma = tx.loadFormasPago(request.pagos)
            val cxcRows = request.facturas.map { tx.lockCxc(it.idFactura, request.idCliente) }
            val shares = request.facturas.map { CobranzaRules.money(it.monto) }
            cxcRows.zip(shares).forEach { (row, share) ->
                if (share > row.saldo + SALDO_TOLERANCE) {
                    throw CobranzaStateException(
                        "El monto a cobrar de la factura ${row.codFactura} supera su saldo pendiente (${row.saldo})",
                    )
                }
            }

            val context =
                CobroContext(
                    request = request,
                    countryCode = countryCode,
                    idSecuencia = resolveOpenSecuencia(request),
                    nroRecibo = tx.nextNroRecibo(),
                    clienteNombre = tx.clienteNombre(request.idCliente),
                    username = username.take(USER_LENGTH),
                    now = BusinessClock.nowForCountry(countryCode),
                )
            val allocations = CobranzaRules.allocate(shares, request.pagos.map { CobranzaRules.money(it.monto) })
            val total = shares.sumOf { it }

            insertRecibo(context, total, cxcRows.singleOrNull()?.idFactura.orEmpty())
            val pagosConCambioAplicado = mutableSetOf<Int>()
            val results =
                cxcRows.mapIndexed { index, row ->
                    val share = shares[index]
                    val iccCajaId = insertIngresoCobranza(context, row, share)
                    allocations.filter { it.facturaIndex == index }.forEach { allocation ->
                        // El vuelto de un pago en efectivo se registra una sola vez (primera porción).
                        val cambio =
                            if (pagosConCambioAplicado.add(allocation.pagoIndex)) {
                                request.pagos[allocation.pagoIndex].efectivoCambio
                            } else {
                                0.0
                            }
                        insertDetalles(context, AllocationTarget(row, iccCajaId, allocation, cambio), siglasByForma)
                    }
                    tx.applyToCxc(row, share)
                }

            CreateCobranzaResponse(
                success = true,
                idCobranza = request.idCobranza,
                nroRecibo = context.nroRecibo,
                montoTotal = total.toDouble(),
                facturas = results,
            )
        }

    private fun Transaction.findExistingRecibo(idCobranza: String): CreateCobranzaResponse? =
        execQuery(
            "SELECT nro_recibo, monto FROM caja_nueva_recibo WHERE caja_recibo_id = ?",
            listOf(idCobranza),
        ) { rs ->
            CreateCobranzaResponse(
                success = true,
                idCobranza = idCobranza,
                nroRecibo = rs.getString("nro_recibo").orEmpty(),
                montoTotal = rs.money("monto").toDouble(),
                duplicado = true,
            )
        }.firstOrNull()

    private fun Transaction.loadFormasPago(pagos: List<CobranzaPagoInput>): Map<Int, String> {
        val ids = pagos.map { it.idFormaPago }.distinct()
        val found =
            execQuery(
                "SELECT id_forma_pago, siglas FROM caja_forma_pago WHERE id_forma_pago IN (${ids.joinToString(",") { "?" }})",
                ids,
            ) { rs ->
                rs.getInt("id_forma_pago") to
                    rs
                        .getString("siglas")
                        .orEmpty()
                        .trim()
                        .uppercase()
            }.toMap()
        ids.firstOrNull { it !in found }?.let { throw CobranzaValidationException("Forma de pago $it no existe") }
        found.values.firstOrNull(CobranzaRules::isCreditSigla)?.let {
            throw CobranzaValidationException("No se puede cobrar una factura con la forma de pago crédito ($it)")
        }
        return found
    }

    private fun Transaction.lockCxc(
        idFactura: String,
        idCliente: String,
    ): CxcRow {
        val row =
            execQuery(
                """
                SELECT c.caja_id, c.id_factura, c.status, c.id_caja_secuencia,
                       COALESCE(f.cod_factura, c.comprobante_numero) AS cod_factura,
                       COALESCE(f.id_cliente, c.id_cliente) AS id_cliente
                FROM caja_nueva c
                LEFT JOIN factura f ON f.id_factura = c.id_factura
                WHERE c.id_factura = ? AND c.comprobante = 'FACT' AND c.ing_eg = 'I'
                ORDER BY c.fecha_creacion
                LIMIT 1
                FOR UPDATE
                """.trimIndent(),
                listOf(idFactura),
            ) { rs ->
                CxcRow(
                    cajaId = rs.getString("caja_id"),
                    idFactura = rs.getString("id_factura"),
                    codFactura = rs.getString("cod_factura").orEmpty(),
                    idCliente = rs.getString("id_cliente").orEmpty(),
                    status = rs.getString("status").orEmpty(),
                    idCajaSecuencia = rs.getString("id_caja_secuencia").orEmpty(),
                    saldo = BigDecimal.ZERO,
                )
            }.firstOrNull() ?: throw CobranzaStateException("La factura seleccionada no tiene cuenta por cobrar")
        val error =
            when {
                row.idCliente != idCliente -> "La factura ${row.codFactura} no pertenece al cliente"
                !row.status.equals(CajaStatus.Pendiente.name, ignoreCase = true) ->
                    "La factura ${row.codFactura} ya no tiene saldo pendiente"
                else -> null
            }
        error?.let { throw CobranzaStateException(it) }
        val saldo =
            execQuery("SELECT ($SALDO_EXPR) AS saldo FROM caja_nueva c WHERE c.caja_id = ?", listOf(row.cajaId)) {
                it.money("saldo")
            }.first()
        return row.copy(saldo = saldo)
    }

    private fun resolveOpenSecuencia(request: CreateCobranzaRequest): String {
        val open =
            findOpenSecuenciaRows(request.idCaja).firstOrNull()?.get(CajaSecuenciaTable.idCajaSecuencia)
                ?: throw CobranzaStateException("La caja no tiene una secuencia abierta. Abre la caja para cobrar")
        return open.take(ID_LENGTH)
    }

    private fun Transaction.nextNroRecibo(): String {
        val (value, formato) =
            reserveCorrelativo(CORRELATIVO_COBRANZA, returnPrevious = false)
                ?: throw CobranzaStateException("No está configurado el correlativo '$CORRELATIVO_COBRANZA' en la empresa")
        return formatCorrelativo(value, formato)
    }

    private fun Transaction.clienteNombre(idCliente: String): String =
        execQuery("SELECT nombre, apellido FROM clientes WHERE id_cliente = ?", listOf(idCliente)) { rs ->
            listOfNotNull(rs.getString("nombre"), rs.getString("apellido")).joinToString(" ").trim()
        }.firstOrNull().orEmpty()

    private fun insertRecibo(
        ctx: CobroContext,
        total: BigDecimal,
        singleFacturaId: String,
    ) {
        val table = SalesCajaNuevaReciboTableFactory.forCountry(ctx.countryCode)
        val request = ctx.request
        table.insert {
            it[table.cajaReciboId] = request.idCobranza
            it[table.tipoRecibo] = "ICC"
            it[table.nroRecibo] = ctx.nroRecibo
            it[table.fecha] = ctx.today
            it[table.monto] = total
            it[table.observacion] = request.observacion.ifBlank { "Cobranza #${ctx.nroRecibo}, Cliente: ${ctx.clienteNombre}" }
            it[table.codVendedor] = request.codVendedor
            it[table.idCliente] = request.idCliente
            it[table.idProveedor] = ""
            it[table.usuarioCreacion] = ctx.username
            it[table.fechaCreacion] = ctx.now
            it[table.status] = "AC"
            it[table.contabilizado] = 0
            it[table.numcomContabilizado] = 0
            it[table.fechaContabilizado] = ctx.today
            it[table.idFactura] = singleFacturaId
            it[table.idPedido] = ""
            it[table.idAbono] = ""
            it[table.idTransaccion] = request.idCobranza
            it[table.nroReferencia] =
                request.pagos
                    .firstOrNull { p -> p.referencia.isNotBlank() }
                    ?.referencia
                    .orEmpty()
                    .take(REFERENCIA_LENGTH)
            it[table.tipoPagoSubtipo] = 0
            if (table is SalesCajaNuevaReciboTableVE) it[table.idConsignacion] = ""
        }
    }

    /** Fila `caja_nueva` ICC en la secuencia actual (ingreso del cobro para el cierre de caja). */
    private fun insertIngresoCobranza(
        ctx: CobroContext,
        row: CxcRow,
        share: BigDecimal,
    ): String {
        val table = SalesCajaNuevaTableFactory.forCountry(ctx.countryCode)
        val cajaId = UUID.randomUUID().toString()
        val request = ctx.request
        table.insert {
            it[table.cajaId] = cajaId
            it[table.idTransaccion] = request.idCobranza
            it[table.fecha] = ctx.today
            it[table.ingEg] = CajaIngresoEgreso.I
            it[table.monto] = share
            it[table.comprobante] = "ICC"
            it[table.comprobanteNumero] = ctx.nroRecibo
            it[table.idFactura] = row.idFactura
            it[table.idCliente] = request.idCliente
            it[table.concepto] =
                "Ingreso por Cobranza #${ctx.nroRecibo}, Factura #${row.codFactura}, Fecha: ${ctx.today}, " +
                "Cliente: ${ctx.clienteNombre}, Monto: ${share.toPlainString()}"
            it[table.status] = CajaStatus.Pagada
            it[table.sucursalId] = request.idSucursal
            it[table.usuarioCreacion] = ctx.username
            it[table.fechaCreacion] = ctx.now
            it[table.idCompra] = ""
            it[table.idProveedor] = ""
            it[table.idOrdenPago] = ""
            it[table.serieSucursal] = request.serieSucursal.take(SERIE_LENGTH)
            it[table.idCajaSecuencia] = ctx.idSecuencia
            it[table.idPedido] = ""
            it[table.idAbono] = ""
            it[table.idNotaCredito] = ""
        }
        return cajaId
    }

    /**
     * Detalle de una porción de pago: sobre la CxC (reduce saldo, ligado al recibo) y, si la
     * factura es de otra secuencia de caja, un espejo sobre la fila ICC para que el cierre de la
     * secuencia actual cuadre por forma de pago. El espejo no lleva `caja_recibo_id` para no
     * duplicar el crédito en el estado de cuenta ni en la anulación del web.
     */
    private fun insertDetalles(
        ctx: CobroContext,
        target: AllocationTarget,
        siglasByForma: Map<Int, String>,
    ) {
        val pago = ctx.request.pagos[target.allocation.pagoIndex]
        val monto = target.allocation.monto
        val concepto = "Cobranza #${ctx.nroRecibo} Factura #${target.row.codFactura}"
        val cxcDetalleId = insertDetalle(ctx, DetalleSpec(target.row.cajaId, ctx.request.idCobranza, pago, monto, concepto))
        val detalleId =
            if (target.row.idCajaSecuencia == ctx.idSecuencia) {
                cxcDetalleId
            } else {
                insertDetalle(ctx, DetalleSpec(target.iccCajaId, "", pago, monto, concepto))
            }
        insertDetalleFormaPago(ctx, target, detalleId, siglasByForma[pago.idFormaPago])
    }

    private fun insertDetalle(
        ctx: CobroContext,
        spec: DetalleSpec,
    ): String {
        val table = SalesCajaNuevaDetalleTableFactory.forCountry(ctx.countryCode)
        val detalleId = UUID.randomUUID().toString()
        table.insert {
            it[table.cajaDetalleId] = detalleId
            it[table.cajaId] = spec.cajaId
            it[table.idFormaPago] = spec.pago.idFormaPago
            it[table.idTransaccion] = ctx.request.idCobranza
            it[table.cajaReciboId] = spec.cajaReciboId
            it[table.monto] = spec.monto
            it[table.montoOriginal] = spec.monto
            it[table.concepto] = spec.concepto
            it[table.usuarioCreacion] = ctx.username
            it[table.fechaCreacion] = ctx.now
            it[table.retencionTipo] = ""
            it[table.retencionPorcentaje] = ""
            it[table.numero] = spec.pago.referencia.take(REFERENCIA_LENGTH)
            it[table.observacion] = ""
            it[table.retencionBaseCalculo] = ""
            it[table.serieSucursal] = ""
            it[table.cajaSecuencia] = ""
            it[table.numeroControl] = ""
            it[table.numeroComprobante] = ""
            it[table.retencionMonto] = ""
            it[table.retencionDetalleJson] = ""
            if (table is SalesCajaNuevaDetalleTableVE) {
                it[table.montoRecibido] = spec.monto
                it[table.montoMonedaPrincipal] = spec.monto
            }
        }
        return detalleId
    }

    /**
     * `caja_nueva_detalle_forma_pago` sobre la fila ICC (secuencia actual): solo efectivo con
     * vuelto, tarjeta, Nequi o anticipo, misma regla que la venta POS.
     */
    private fun insertDetalleFormaPago(
        ctx: CobroContext,
        target: AllocationTarget,
        detalleId: String,
        siglas: String?,
    ) {
        val pago = ctx.request.pagos[target.allocation.pagoIndex]
        val monto = target.allocation.monto
        val asSalePayment =
            SalePaymentInput(
                idFormaPago = pago.idFormaPago,
                tipoMovimiento = siglas.orEmpty(),
                monto = monto.toDouble(),
                montoRecibido = monto.toDouble(),
                efectivoCambio = target.cambio,
                siglas = siglas,
            )
        val tipoMovimiento = resolveCajaDetalleFormaPagoTipoMovimiento(asSalePayment) ?: return
        val t = SalesCajaNuevaDetalleFormaPagoTable
        t.insert {
            it[t.cajaDetalleFormaPagoId] = UUID.randomUUID().toString()
            it[t.cajaId] = target.iccCajaId
            it[t.cajaDetalleId] = detalleId
            it[t.tipoMovimiento] = tipoMovimiento
            it[t.idFormaPago] = pago.idFormaPago
            it[t.comprobante] = "ICC"
            it[t.concepto] = "Ingreso por cobranza"
            it[t.monto] = monto
            it[t.montoOriginal] = monto
            it[t.efectivoCambio] =
                if (tipoMovimiento == "CASH") CobranzaRules.money(target.cambio) else BigDecimal.ZERO.setScale(2)
        }
    }

    /** Actualiza la CxC tras aplicar [share]; si queda en cero, la factura queda pagada. */
    private fun Transaction.applyToCxc(
        row: CxcRow,
        share: BigDecimal,
    ): CobranzaFacturaResult {
        val nuevo = (row.saldo - share).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP)
        val pagada = nuevo <= SALDO_TOLERANCE
        if (pagada) {
            execUpdate("UPDATE caja_nueva SET status = 'Pagada' WHERE caja_id = ?", listOf(row.cajaId))
            execUpdate("UPDATE factura SET cod_estatus = 2 WHERE id_factura = ?", listOf(row.idFactura))
        }
        execUpdate(
            "UPDATE factura_detalle_formapago SET totalizar_saldo_pendiente = ? WHERE id_factura = ?",
            listOf(if (pagada) BigDecimal.ZERO.setScale(2) else nuevo, row.idFactura),
        )
        return CobranzaFacturaResult(
            idFactura = row.idFactura,
            codFactura = row.codFactura,
            montoAplicado = share.toDouble(),
            saldoAnterior = row.saldo.toDouble(),
            saldoNuevo = if (pagada) 0.0 else nuevo.toDouble(),
            pagada = pagada,
        )
    }

    private companion object {
        /** Saldo de una fila CxC `c`: monto menos abonos no crédito (misma regla que el web). */
        const val SALDO_EXPR =
            "c.monto - COALESCE((SELECT SUM(cd.monto) FROM caja_nueva_detalle cd " +
                "LEFT JOIN caja_forma_pago fp ON fp.id_forma_pago = cd.id_forma_pago " +
                "WHERE cd.caja_id = c.caja_id AND (fp.siglas IS NULL OR fp.siglas NOT IN ('CREDITO','CRED','CXC'))), 0)"
    }
}

private fun ResultSet.money(column: String): BigDecimal = (getBigDecimal(column) ?: BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP)

private fun ResultSet.getDateOrNull(column: String): String =
    runCatching { getDate(column)?.toLocalDate()?.toString() }.getOrNull().orEmpty()
