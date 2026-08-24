package com.amaxoniaerp.features.sales.data

import com.amaxoniaerp.features.items.data.FacturaDetalleProductoLoteTable
import com.amaxoniaerp.features.items.data.ItemLoteTable
import com.amaxoniaerp.features.sales.domain.InsufficientStockException
import com.amaxoniaerp.features.sales.domain.ProcessSaleRequest
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greaterEq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.minus
import org.jetbrains.exposed.sql.SqlExpressionBuilder.plus
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.batchInsert
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.update
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.util.UUID

private const val INVOICE_USER_LENGTH = 32
private const val QUANTITY_SCALE = 3
private const val PERCENT_BASE = 100.0
private const val PACKAGING_UNIT_LENGTH = 15
private const val PROMOTION_CODE_LENGTH = 15
private const val PROMOTION_IDENTIFIER_LENGTH = 36
private const val PROMOTION_TYPE_LENGTH = 20
private const val PROMOTION_NAME_LENGTH = 200
private const val PAYMENT_USER_LENGTH = 60

internal fun insertFacturaDetalle(ctx: SaleWriteContext): List<String> {
    val vendedorPorDefecto = ctx.request.factura.codVendedor
    val usuario =
        ctx.request.factura.usuarioCreacion
            .take(INVOICE_USER_LENGTH)
    val detalleIds = mutableListOf<String>()

    ctx.request.items.forEach { item ->
        val vendedorLinea = item.codVendedor?.takeIf { it > 0 } ?: vendedorPorDefecto
        val itemTaxRate = item.itemPIva
        val itemTotalSinIvaBase = ctx.monetaryContext.toBase(item.itemTotalSinIva)
        val itemTotalConIvaBase = ctx.monetaryContext.toBase(item.itemTotalConIva)
        val itemPriceSinIvaBase = ctx.monetaryContext.toBase(item.itemPrecioSinIva)

        val detalleId = UUID.randomUUID().toString()
        detalleIds.add(detalleId)

        SalesFacturaDetalleTable.insert {
            it[idDetalleFactura] = detalleId
            it[idFactura] = ctx.invoiceId
            it[idItem] = item.idItem
            it[itemAlmacen] = item.itemAlmacen
            it[itemDescripcion] = item.itemDescripcion
            it[itemCantidad] = item.itemCantidad.toScaledBigDecimal(QUANTITY_SCALE)
            it[itemPrecioSinIva] = itemPriceSinIvaBase
            it[itemDescuento] = item.itemDescuento.toMoney()
            it[itemMontoDescuento] = ctx.monetaryContext.toBase(item.itemMontoDescuento)
            it[itemPiva] = itemTaxRate.toMoney()
            it[itemTotalSinIva] = itemTotalSinIvaBase
            it[itemTotalConIva] = itemTotalConIvaBase
            it[cantidadBulto] = item.cantidadBulto.coerceAtLeast(1)
            it[gananciaItemIndividual] = itemTotalSinIvaBase
            it[porcentajeGanancia] = BigDecimal.valueOf(PERCENT_BASE).setScale(2)
            it[poseeSerial] = "NO"
            it[serialesSeleccionados] = ""
            it[usuarioCreacion] = usuario
            it[fechaCreacion] = ctx.now
            it[itemListaPrecio] = "BASE"
            it[itemUnidadEmpaque] = item.itemUnidadEmpaque.take(PACKAGING_UNIT_LENGTH).ifBlank { "UNIDAD" }
            it[itemCantidadTotal] = item.itemCantidadTotal.toScaledBigDecimal(0)
            it[promocionId] = item.promocionId.take(PROMOTION_IDENTIFIER_LENGTH)
            it[promocionTipo] = item.promocionTipo.take(PROMOTION_TYPE_LENGTH)
            it[promocionCodigo] = item.promocionCodigo.take(PROMOTION_CODE_LENGTH)
            it[promocionNombre] = item.promocionNombre.take(PROMOTION_NAME_LENGTH)
            it[promocionGrupo] = item.promocionGrupo.take(PROMOTION_IDENTIFIER_LENGTH)
            it[promocionDetalleId] = item.promocionDetalleId.take(PROMOTION_IDENTIFIER_LENGTH)
            it[promocionCantidad] = item.promocionCantidad.toScaledBigDecimal(QUANTITY_SCALE)
            it[grupo] = 1
            it[descuentoAutorizacion] = ""
            it[codVendedor] = vendedorLinea
            it[itemCodigo] = item.itemCodigo
            it[itemReferencia] = item.itemReferencia
            it[idSegmento] = item.idSegmento
            it[idFamilia] = item.idFamilia
        }
    }
    return detalleIds
}

/**
 * Inserta trazabilidad por lote y descuenta disponibilidad en item_lote.
 * Solo aplica a items con poseeConfiguracionLote == "si" y codigosLote no vacio.
 */
internal fun processLotTracking(
    ctx: SaleWriteContext,
    detalleIds: List<String>,
) {
    ctx.request.items.forEachIndexed { index, item ->
        if (item.poseeConfiguracionLote.equals("si", ignoreCase = true) && item.codigosLote.isNotEmpty()) {
            val detalleId = detalleIds.getOrNull(index) ?: return@forEachIndexed

            item.codigosLote.forEach { lote ->
                // Insertar registro de trazabilidad
                FacturaDetalleProductoLoteTable.insert {
                    it[id] = UUID.randomUUID().toString()
                    it[idDetalleFactura] = detalleId
                    it[idItem] = item.idItem
                    it[idLoteItem] = lote.idLoteItem
                    it[cantidad] = lote.cantidad
                }

                // Descontar disponibilidad y registrar venta en item_lote de forma condicional.
                val loteCantidad = BigDecimal.valueOf(lote.cantidad.toLong())
                val updated =
                    ItemLoteTable.update({
                        (ItemLoteTable.idLoteItem eq lote.idLoteItem) and
                            (ItemLoteTable.disponibilidad greaterEq loteCantidad)
                    }) {
                        it.update(disponibilidad, disponibilidad.minus(loteCantidad))
                        it.update(procesamiento, procesamiento.plus(loteCantidad))
                        it.update(venta, venta.plus(loteCantidad))
                    }
                if (updated != 1) {
                    throw InsufficientStockException(
                        "Lote insuficiente: idLoteItem=${lote.idLoteItem}, solicitado=$loteCantidad",
                    )
                }
            }
        }
    }
}

internal fun insertFacturaImpuestos(ctx: SaleWriteContext) {
    if (ctx.request.impuestos.isEmpty()) return
    SalesFacturaImpuestosTable.batchInsert(ctx.request.impuestos) { tax ->
        this[SalesFacturaImpuestosTable.idFacturaImpuestos] = UUID.randomUUID().toString()
        this[SalesFacturaImpuestosTable.idFactura] = ctx.invoiceId
        this[SalesFacturaImpuestosTable.totalizarBaseRetencion] = ctx.monetaryContext.toBase(tax.totalizarBaseRetencion)
        this[SalesFacturaImpuestosTable.codImpuestoIva] = tax.codImpuestoIva
        this[SalesFacturaImpuestosTable.totalizarMontoIva2] = ctx.monetaryContext.toBase(tax.totalizarMontoIva2)
        this[SalesFacturaImpuestosTable.usuarioCreacion] = ctx.request.factura.usuarioCreacion
        this[SalesFacturaImpuestosTable.fechaCreacion] = ctx.now
    }
}

internal fun insertFacturaDetalleFormaPago(ctx: SaleWriteContext) {
    val resumen = ctx.request.pagoResumen
    val montos = computePaymentBreakdown(ctx.request)

    val fpgTable = SalesFacturaDetalleFormaPagoTableFactory.forCountry(ctx.monetaryContext.countryCode)
    fpgTable.insert {
        it[fpgTable.codFacturaDetalleFormaPago] = UUID.randomUUID().toString()
        it[fpgTable.idFactura] = ctx.invoiceId
        it[fpgTable.totalizarMontoCancelar] = ctx.monetaryContext.toBase(resumen.totalizarMontoCancelar)
        it[fpgTable.totalizarSaldoPendiente] = ctx.monetaryContext.toBase(ctx.creditDecision.saldoEsperado)
        it[fpgTable.totalizarCambio] = ctx.monetaryContext.toBase(resumen.totalizarCambio)
        it[fpgTable.totalizarMontoEfectivo] = ctx.monetaryContext.toBase(montos.efectivo)
        it[fpgTable.optCheque] = if (montos.cheque > 0.0) 1 else 0
        it[fpgTable.totalizarMontoCheque] = ctx.monetaryContext.toBase(montos.cheque)
        it[fpgTable.totalizarNroCheque] = BigDecimal.ZERO.setScale(2)
        it[fpgTable.totalizarNombreBanco] = 0
        it[fpgTable.optTarjeta] = if (montos.tarjeta > 0.0) 1 else 0
        it[fpgTable.totalizarMontoTarjeta] = ctx.monetaryContext.toBase(montos.tarjeta)
        it[fpgTable.totalizarNroTarjeta] = BigDecimal.ZERO.setScale(2)
        it[fpgTable.totalizarTipoTarjeta] = 0
        it[fpgTable.optDeposito] = if (montos.deposito > 0.0) 1 else 0
        it[fpgTable.totalizarMontoDeposito] = ctx.monetaryContext.toBase(montos.deposito)
        it[fpgTable.totalizarNroDeposito] = BigDecimal.ZERO.setScale(2)
        it[fpgTable.totalizarBancoDeposito] = 0
        it[fpgTable.fechaVencimiento] = ctx.creditDecision.fechaVencimiento
        it[fpgTable.observacion] = ""
        it[fpgTable.personaContacto] = ""
        it[fpgTable.telefono] = ""
        it[fpgTable.optOtroDocumento] = if (montos.otros > 0.0) 1 else 0
        it[fpgTable.totalizarTipoOtroDocumento] = 0
        it[fpgTable.totalizarMontoOtroDocumento] = ctx.monetaryContext.toBase(montos.otros)
        it[fpgTable.totalizarNroOtroDocumento] = 0
        it[fpgTable.totalizarBancoOtroDocumento] = 0
        it[fpgTable.fechaCreacion] = ctx.now
        it[fpgTable.usuarioCreacion] =
            ctx.request.factura.usuarioCreacion
                .take(PAYMENT_USER_LENGTH)
        it[fpgTable.totalizarMontoCredito] = ctx.monetaryContext.toBase(montos.credito)
        it[fpgTable.totalizarMontoDebito] = ctx.monetaryContext.toBase(montos.debito)
        it[fpgTable.totalizarMontoTransferencia] = ctx.monetaryContext.toBase(montos.transferencia)
        it[fpgTable.totalizarMontoCertificado] = ctx.monetaryContext.toBase(montos.certificado)
        it[fpgTable.totalizarMontoCxc] = ctx.monetaryContext.toBase(ctx.creditDecision.totalCxc)
        it[fpgTable.totalizarMontoOtros] = ctx.monetaryContext.toBase(montos.otros)
        insertFormaPagoCountryFields(it, fpgTable)
    }
}

private fun insertFormaPagoCountryFields(
    stmt: org.jetbrains.exposed.sql.statements.InsertStatement<Number>,
    fpgTable: BaseSalesFacturaDetalleFormaPagoTable,
) {
    if (fpgTable is SalesFacturaDetalleFormaPagoTableVE) {
        stmt[fpgTable.totalizarMontoDivisa] = BigDecimal.ZERO.setScale(2)
    } else if (fpgTable is SalesFacturaDetalleFormaPagoTablePA) {
        stmt[fpgTable.codigoRetencion] = ""
        stmt[fpgTable.totalizarMontoRetencion] = BigDecimal.ZERO.setScale(2)
    }
}

/** Montos agregados por tipo de pago (normalizado a los cÃ³digos del ERP). */
private data class PaymentBreakdown(
    val efectivo: Double,
    val cheque: Double,
    val tarjeta: Double,
    val deposito: Double,
    val transferencia: Double,
    val credito: Double,
    val debito: Double,
    val certificado: Double,
    val otros: Double,
)

private val ZERO_MONEY = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)

private val KNOWN_PAYMENT_CODES =
    setOf(
        "CASH",
        "EF",
        "EFE",
        "EFECTIVO",
        "CH",
        "CHEQUE",
        "TDC",
        "TARJETA",
        "PV",
        "POS",
        "NEQ",
        "DE",
        "DEPOSITO",
        "TR",
        "TRANSFERENCIA",
        "PM",
        "CR",
        "CREDITO",
        "DB",
        "DEBITO",
        "CERT",
        "CERTIFICADO",
        "CXC",
        "OT",
        "MB",
    )

private fun computePaymentBreakdown(request: ProcessSaleRequest): PaymentBreakdown {
    val montosPorTipo =
        request.pagos
            .groupBy { it.tipoMovimiento.trim().uppercase() }
            .mapValues { (_, list) ->
                list
                    .map { it.monto.toMoney() }
                    .fold(BigDecimal.ZERO, BigDecimal::add)
                    .setScale(2, RoundingMode.HALF_UP)
            }

    fun amountOf(vararg keys: String): BigDecimal =
        keys
            .map { key -> montosPorTipo[key] ?: ZERO_MONEY }
            .fold(BigDecimal.ZERO, BigDecimal::add)
            .setScale(2, RoundingMode.HALF_UP)

    val otrosNoReconocidos =
        montosPorTipo
            .filterKeys { it !in KNOWN_PAYMENT_CODES }
            .values
            .fold(BigDecimal.ZERO, BigDecimal::add)

    val montoOtros = (amountOf("OT", "MB") + otrosNoReconocidos).setScale(2, RoundingMode.HALF_UP)

    return PaymentBreakdown(
        efectivo = amountOf("CASH", "EF", "EFE", "EFECTIVO").toDouble(),
        cheque = amountOf("CH", "CHEQUE").toDouble(),
        tarjeta = amountOf("TDC", "TARJETA", "PV", "POS", "NEQ").toDouble(),
        deposito = amountOf("DE", "DEPOSITO").toDouble(),
        transferencia = amountOf("TR", "TRANSFERENCIA", "PM").toDouble(),
        credito = amountOf("CR", "CREDITO").toDouble(),
        debito = amountOf("DB", "DEBITO").toDouble(),
        certificado = amountOf("CERT", "CERTIFICADO").toDouble(),
        otros = montoOtros.toDouble(),
    )
}

internal fun parseDateOrToday(
    value: String?,
    defaultDate: LocalDate,
): LocalDate {
    if (value.isNullOrBlank()) return defaultDate
    return runCatching { LocalDate.parse(value) }.getOrDefault(defaultDate)
}
