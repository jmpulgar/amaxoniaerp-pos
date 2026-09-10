package com.amaxoniaerp.features.sales.data

import com.amaxoniaerp.features.sales.domain.InsufficientStockException
import com.amaxoniaerp.features.sales.domain.SaleItemInput
import com.amaxoniaerp.features.sales.domain.SalePaymentInput
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greaterEq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.minus
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.batchInsert
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.update
import java.math.BigDecimal
import java.time.Year
import java.time.format.DateTimeFormatter
import java.util.UUID

private const val INVENTORY_QUANTITY_SCALE = 4
private const val TWO_DIGIT_YEAR_MODULUS = 100
private const val STANDARD_USER_LENGTH = 20
private const val SEQUENCE_ID_MAX_LENGTH = 36

internal fun updateInventoryAndKardex(ctx: SaleWriteContext) {
    val physicalItems = ctx.request.items.filter { it.esProductoFisico }
    if (physicalItems.isEmpty()) return

    val kardexId = UUID.randomUUID().toString()
    val documentCode = "FACT-${ctx.invoiceCode}"
    val shouldValidateStock = ctx.monetaryContext.shouldValidateStock()

    physicalItems.forEach { item -> updateStockForItem(item, shouldValidateStock, ctx) }

    insertKardexHeader(ctx, kardexId, documentCode, physicalItems)
    physicalItems.forEach { item -> insertKardexDetalle(ctx, kardexId, item) }
}

private fun updateStockForItem(
    item: SaleItemInput,
    shouldValidateStock: Boolean,
    ctx: SaleWriteContext,
) {
    val requested = item.itemCantidadTotal.toScaledBigDecimal(2)
    val updated =
        if (shouldValidateStock) {
            SalesStockTable.update({
                (SalesStockTable.idItem eq item.idItem) and
                    (SalesStockTable.codAlmacen eq item.itemAlmacen) and
                    (SalesStockTable.cantidad greaterEq requested.toFloat())
            }) {
                it.update(cantidad, cantidad.minus(requested.toFloat()))
            }
        } else {
            SalesStockTable.update({
                (SalesStockTable.idItem eq item.idItem) and
                    (SalesStockTable.codAlmacen eq item.itemAlmacen)
            }) {
                it.update(cantidad, cantidad.minus(requested.toFloat()))
            }
        }

    when {
        updated == 1 -> Unit
        shouldValidateStock -> {
            throw InsufficientStockException(
                "No se pudo descontar stock para item=${item.idItem}, almacen=${item.itemAlmacen}",
            )
        }

        else -> {
            SalesStockTable.insert {
                it[idItem] = item.idItem
                it[codAlmacen] = item.itemAlmacen
                it[cantidad] = requested.negate().toFloat()
                if (ctx.monetaryContext.countryCode.uppercase() == "PA") {
                    it[cantidadMuestra] = BigDecimal.ZERO.setScale(INVENTORY_QUANTITY_SCALE)
                    it[minimo] = 0L
                    it[maximo] = 0L
                }
            }
        }
    }
}

private fun insertKardexHeader(
    ctx: SaleWriteContext,
    kardexId: String,
    documentCode: String,
    physicalItems: List<SaleItemInput>,
) {
    val kardexTable = SalesKardexTableFactory.forCountry(ctx.monetaryContext.countryCode)

    kardexTable.insert {
        it[kardexTable.idTransaccion] = kardexId
        it[kardexTable.tipoMovimientoAlmacen] = 2
        it[kardexTable.autorizadoPor] = ctx.request.factura.usuarioCreacion
        it[kardexTable.observacion] = "Salida por Ventas"
        it[kardexTable.fecha] = ctx.today
        it[kardexTable.usuarioCreacion] = ctx.request.factura.usuarioCreacion
        it[kardexTable.fechaCreacion] = ctx.now
        it[kardexTable.estado] = "Procesado"
        it[kardexTable.idDocumento] = ctx.invoiceId
        it[kardexTable.codProveedor] = 0
        it[kardexTable.comprobante] = "FACT"
        it[kardexTable.anio] = Year.from(ctx.today).value % TWO_DIGIT_YEAR_MODULUS
        it[kardexTable.tipoCosto] = "PROM"
        it[kardexTable.estatus] = 1
        it[kardexTable.entregadoACodigo] = "POS"
        it[kardexTable.entregadoANombre] = "VENTA"
        it[kardexTable.codDocumento] = documentCode
        it[kardexTable.subtipoMovimientoAlmacen] = 0
        it[kardexTable.contabilizado] = 0
        it[kardexTable.fechaContabilizacion] = ctx.today
        it[kardexTable.usuarioContabilizacion] = ""
        it[kardexTable.idAlmacenSalida] = physicalItems.first().itemAlmacen
        it[kardexTable.idSucursal] = ctx.request.factura.idSucursal
        it[kardexTable.validadoFecha] = ctx.today
        it[kardexTable.validadoUsuario] =
            ctx.request.factura.usuarioCreacion
                .take(STANDARD_USER_LENGTH)
        it[kardexTable.validadoObservacion] = "Salida por Ventas"
        if (kardexTable is SalesKardexTablePA) {
            it[kardexTable.controlaStock] = 0
        }
    }
}

private fun insertKardexDetalle(
    ctx: SaleWriteContext,
    kardexId: String,
    item: SaleItemInput,
) {
    val kardexDetalleTable = SalesKardexDetalleTableFactory.forCountry(ctx.monetaryContext.countryCode)
    kardexDetalleTable.insert {
        it[kardexDetalleTable.idTransaccionDetalle] = UUID.randomUUID().toString()
        it[kardexDetalleTable.idTransaccion] = kardexId
        it[kardexDetalleTable.idAlmacenEntrada] = 0
        it[kardexDetalleTable.idAlmacenSalida] = item.itemAlmacen
        it[kardexDetalleTable.idItem] = item.idItem
        it[kardexDetalleTable.cantidad] = item.itemCantidadTotal.toFloat()
        it[kardexDetalleTable.cantidadDistribuida] = 0
        it[kardexDetalleTable.precio] = ctx.monetaryContext.toBase(item.itemPrecioSinIva)
        it[kardexDetalleTable.cantidadMuestra] = 0
        it[kardexDetalleTable.unidadBulto] = "UNIDAD"
        it[kardexDetalleTable.cantidadBulto] = BigDecimal.ONE.setScale(2)
        it[kardexDetalleTable.unidadEmpaque] = "UNIDAD"
        it[kardexDetalleTable.cantidadTotal] = item.itemCantidadTotal.toScaledBigDecimal(2)
        it[kardexDetalleTable.costo] = BigDecimal.ZERO.setScale(2)
        if (kardexDetalleTable is SalesKardexDetalleTablePA) {
            it[kardexDetalleTable.idCentroCosto] = 0
            it[kardexDetalleTable.idLoteItem] = 0
        }
    }
}

/** Identificadores de las filas de caja generadas para una venta. */
private data class CajaEntryIds(
    val cajaId: String,
    val transactionId: String,
    val cajaReciboId: String,
)

internal fun insertCajaEntries(ctx: SaleWriteContext) {
    val ids =
        CajaEntryIds(
            cajaId = UUID.randomUUID().toString(),
            transactionId = UUID.randomUUID().toString(),
            cajaReciboId = UUID.randomUUID().toString(),
        )
    val resumen = ctx.request.pagoResumen
    val totalBase = ctx.monetaryContext.toBase(resumen.totalizarMontoCancelar)
    val fechaTexto = ctx.today.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
    val montoTexto = totalBase.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString()

    insertCajaNueva(ctx, ids, totalBase, fechaTexto, montoTexto)
    insertCajaRecibo(ctx, ids.cajaReciboId, totalBase, fechaTexto)
    insertCajaDetallePagos(ctx, ids)
}

private fun insertCajaNueva(
    ctx: SaleWriteContext,
    ids: CajaEntryIds,
    totalBase: BigDecimal,
    fechaTexto: String,
    montoTexto: String,
) {
    val cajaNuevaTable = SalesCajaNuevaTableFactory.forCountry(ctx.monetaryContext.countryCode)
    val clienteNombre =
        ctx.request.factura.facturarA
            .ifBlank { "CLIENTE MOSTRADOR" }
    val concepto =
        "Ingreso por Factura #${ctx.invoiceCode}, Fecha: $fechaTexto, " +
            "Cliente: $clienteNombre, Monto: $montoTexto."

    cajaNuevaTable.insert {
        it[cajaNuevaTable.cajaId] = ids.cajaId
        it[cajaNuevaTable.idTransaccion] = ids.transactionId
        it[cajaNuevaTable.fecha] = ctx.today
        it[cajaNuevaTable.ingEg] = CajaIngresoEgreso.I
        it[cajaNuevaTable.monto] = totalBase
        it[cajaNuevaTable.comprobante] = "FACT"
        it[cajaNuevaTable.comprobanteNumero] = ctx.invoiceCode
        it[cajaNuevaTable.idFactura] = ctx.invoiceId
        it[cajaNuevaTable.idCliente] = ctx.request.factura.idCliente
        it[cajaNuevaTable.concepto] = concepto
        it[cajaNuevaTable.status] =
            if (ctx.creditDecision.saldoEsperado > BigDecimal.ZERO) {
                CajaStatus.Pendiente
            } else {
                CajaStatus.Pagada
            }
        it[cajaNuevaTable.sucursalId] = ctx.request.factura.idSucursal
        it[cajaNuevaTable.usuarioCreacion] =
            ctx.request.factura.usuarioCreacion
                .take(STANDARD_USER_LENGTH)
        it[cajaNuevaTable.fechaCreacion] = ctx.now
        it[cajaNuevaTable.idCompra] = ""
        it[cajaNuevaTable.idProveedor] = ""
        it[cajaNuevaTable.idOrdenPago] = ""
        it[cajaNuevaTable.serieSucursal] = ctx.request.factura.serieSucursal
        it[cajaNuevaTable.idCajaSecuencia] =
            ctx.request.factura.idCajaSecuencia
                .take(SEQUENCE_ID_MAX_LENGTH)
        it[cajaNuevaTable.idPedido] = ""
        it[cajaNuevaTable.idAbono] = ""
        it[cajaNuevaTable.idNotaCredito] = ""
    }
}

private fun insertCajaRecibo(
    ctx: SaleWriteContext,
    cajaReciboId: String,
    totalBase: BigDecimal,
    fechaTexto: String,
) {
    val clienteNombre =
        ctx.request.factura.facturarA
            .ifBlank { "CLIENTE MOSTRADOR" }
    val cajaReciboTable = SalesCajaNuevaReciboTableFactory.forCountry(ctx.monetaryContext.countryCode)
    cajaReciboTable.insert {
        it[cajaReciboTable.cajaReciboId] = cajaReciboId
        it[cajaReciboTable.tipoRecibo] = "ICC"
        it[cajaReciboTable.nroRecibo] = "FACT/${ctx.invoiceCode}"
        it[cajaReciboTable.fecha] = ctx.today
        it[cajaReciboTable.monto] = totalBase
        it[cajaReciboTable.observacion] =
            "Ingreso por Factura #${ctx.invoiceCode}, Fecha: $fechaTexto, " +
            "Cliente: $clienteNombre"
        it[cajaReciboTable.codVendedor] = ctx.request.factura.codVendedor
        it[cajaReciboTable.idCliente] = ctx.request.factura.idCliente
        it[cajaReciboTable.idProveedor] = ""
        it[cajaReciboTable.usuarioCreacion] =
            ctx.request.factura.usuarioCreacion
                .take(STANDARD_USER_LENGTH)
        it[cajaReciboTable.fechaCreacion] = ctx.now
        it[cajaReciboTable.status] = "AC"
        it[cajaReciboTable.contabilizado] = 0
        it[cajaReciboTable.numcomContabilizado] = 0
        it[cajaReciboTable.fechaContabilizado] = ctx.today
        it[cajaReciboTable.idFactura] = ctx.invoiceId
        it[cajaReciboTable.idPedido] = ""
        it[cajaReciboTable.idAbono] = ""
        it[cajaReciboTable.idTransaccion] = ""
        it[cajaReciboTable.nroReferencia] = ""
        it[cajaReciboTable.tipoPagoSubtipo] = 0
        if (cajaReciboTable is SalesCajaNuevaReciboTableVE) {
            it[cajaReciboTable.idConsignacion] = ""
        }
    }
}

private data class PagoDetallePair(
    val pago: SalePaymentInput,
    val detalleId: String,
    val montoPagoBase: BigDecimal,
    val montoRecibidoBase: BigDecimal,
)

private fun insertCajaDetallePagos(
    ctx: SaleWriteContext,
    ids: CajaEntryIds,
) {
    if (ctx.request.pagos.isEmpty()) return
    val items =
        ctx.request.pagos.map { pago ->
            PagoDetallePair(
                pago = pago,
                detalleId = UUID.randomUUID().toString(),
                montoPagoBase = ctx.monetaryContext.toBase(pago.monto),
                montoRecibidoBase = ctx.monetaryContext.toBase(pago.montoRecibido),
            )
        }

    insertCajaNuevaDetalleBatch(ctx, ids, items)
    insertCajaNuevaDetalleFormaPagoBatch(ctx, ids, items)
}

private fun isCreditPayment(pago: SalePaymentInput): Boolean {
    val tipo =
        pago.tipoMovimiento
            ?.trim()
            ?.uppercase()
            .orEmpty()
    val siglas =
        pago.siglas
            ?.trim()
            ?.uppercase()
            .orEmpty()
    return tipo in setOf("CXC", "CR", "CRED", "CREDITO") || siglas in setOf("CXC", "CR", "CRED", "CREDITO")
}

private fun insertCajaNuevaDetalleBatch(
    ctx: SaleWriteContext,
    ids: CajaEntryIds,
    items: List<PagoDetallePair>,
) {
    val cajaNuevaDetalleTable = SalesCajaNuevaDetalleTableFactory.forCountry(ctx.monetaryContext.countryCode)
    cajaNuevaDetalleTable.batchInsert(items) { item ->
        val pago = item.pago
        val isCredit = isCreditPayment(pago)
        this[cajaNuevaDetalleTable.cajaDetalleId] = item.detalleId
        this[cajaNuevaDetalleTable.cajaId] = ids.cajaId
        this[cajaNuevaDetalleTable.idFormaPago] = pago.idFormaPago
        this[cajaNuevaDetalleTable.idTransaccion] = ids.transactionId
        this[cajaNuevaDetalleTable.cajaReciboId] = ids.cajaReciboId
        this[cajaNuevaDetalleTable.monto] = if (isCredit) BigDecimal.ZERO.setScale(2) else item.montoPagoBase
        this[cajaNuevaDetalleTable.montoOriginal] = item.montoPagoBase
        this[cajaNuevaDetalleTable.concepto] = null
        this[cajaNuevaDetalleTable.usuarioCreacion] =
            ctx.request.factura.usuarioCreacion
                .take(STANDARD_USER_LENGTH)
        this[cajaNuevaDetalleTable.fechaCreacion] = ctx.now
        this[cajaNuevaDetalleTable.retencionTipo] = ""
        this[cajaNuevaDetalleTable.retencionPorcentaje] = ""
        this[cajaNuevaDetalleTable.numero] = ""
        this[cajaNuevaDetalleTable.observacion] = ""
        this[cajaNuevaDetalleTable.retencionBaseCalculo] = ""
        this[cajaNuevaDetalleTable.serieSucursal] = ""
        this[cajaNuevaDetalleTable.cajaSecuencia] = ""
        this[cajaNuevaDetalleTable.numeroControl] = ""
        this[cajaNuevaDetalleTable.numeroComprobante] = ""
        this[cajaNuevaDetalleTable.retencionMonto] = ""
        this[cajaNuevaDetalleTable.retencionDetalleJson] = ""
        if (cajaNuevaDetalleTable is SalesCajaNuevaDetalleTableVE) {
            this[cajaNuevaDetalleTable.montoRecibido] = item.montoRecibidoBase
            this[cajaNuevaDetalleTable.montoMonedaPrincipal] = item.montoPagoBase
        }
    }
}

private fun insertCajaNuevaDetalleFormaPagoBatch(
    ctx: SaleWriteContext,
    ids: CajaEntryIds,
    items: List<PagoDetallePair>,
) {
    val qualifyingItems =
        items.mapNotNull { item ->
            val tipo = resolveCajaDetalleFormaPagoTipoMovimiento(item.pago)
            if (tipo != null) item to tipo else null
        }
    if (qualifyingItems.isEmpty()) return

    SalesCajaNuevaDetalleFormaPagoTable.batchInsert(qualifyingItems) { (item, tipoMovimiento) ->
        val pago = item.pago
        this[SalesCajaNuevaDetalleFormaPagoTable.cajaDetalleFormaPagoId] = UUID.randomUUID().toString()
        this[SalesCajaNuevaDetalleFormaPagoTable.cajaId] = ids.cajaId
        this[SalesCajaNuevaDetalleFormaPagoTable.cajaDetalleId] = item.detalleId
        this[SalesCajaNuevaDetalleFormaPagoTable.tipoMovimiento] = tipoMovimiento
        this[SalesCajaNuevaDetalleFormaPagoTable.idFormaPago] = pago.idFormaPago
        this[SalesCajaNuevaDetalleFormaPagoTable.comprobante] = "FACT"
        this[SalesCajaNuevaDetalleFormaPagoTable.concepto] = "Ingreso por venta"
        this[SalesCajaNuevaDetalleFormaPagoTable.monto] = item.montoPagoBase
        this[SalesCajaNuevaDetalleFormaPagoTable.montoOriginal] = item.montoPagoBase
        this[SalesCajaNuevaDetalleFormaPagoTable.tdcProveedor] = pago.tdcProveedor.orEmpty()
        this[SalesCajaNuevaDetalleFormaPagoTable.tdcNumero] = pago.tdcNumero.orEmpty()
        this[SalesCajaNuevaDetalleFormaPagoTable.tdcTitular] = pago.tdcTitular.orEmpty()
        this[SalesCajaNuevaDetalleFormaPagoTable.tdcVencimiento] = pago.tdcVencimiento.orEmpty()
        this[SalesCajaNuevaDetalleFormaPagoTable.tdcCvv] = pago.tdcCvv.orEmpty()
        this[SalesCajaNuevaDetalleFormaPagoTable.codigoVerificacion] = pago.codigoVerificacion.orEmpty()
        this[SalesCajaNuevaDetalleFormaPagoTable.idAbonoDetalle] = pago.idAbonoDetalle.orEmpty()
        this[SalesCajaNuevaDetalleFormaPagoTable.efectivoCambio] =
            if (tipoMovimiento ==
                "CASH"
            ) {
                ctx.monetaryContext.toBase(pago.efectivoCambio)
            } else {
                BigDecimal.ZERO.setScale(2)
            }
    }
}

/**
 * Determina si un pago califica para inserción en `caja_nueva_detalle_forma_pago`.
 * Solo se insertan filas en estos 4 casos específicos:
 * 1. Efectivo con vuelto -> 'CASH'
 * 2. Tarjeta de crédito -> 'TDC'
 * 3. Nequi -> 'NEQ'
 * 4. Uso de anticipos -> 'ANTICIPO'
 *
 * En crédito puro (CXC) o formas bancarias estándar NO se inserta ninguna fila.
 */
internal fun resolveCajaDetalleFormaPagoTipoMovimiento(pago: SalePaymentInput): String? {
    val tipo =
        pago.tipoMovimiento
            ?.trim()
            ?.uppercase()
            .orEmpty()
    val siglas =
        pago.siglas
            ?.trim()
            ?.uppercase()
            .orEmpty()

    return when {
        tipo in setOf("CASH", "EF", "EFE", "EFECTIVO") ||
            siglas in setOf("EF", "EFE", "EFECTIVO", "CASH") ||
            pago.efectivoCambio > 0.0 -> "CASH"
        tipo in
            setOf(
                "TDC",
                "TC",
            ) ||
            siglas in setOf("TDC", "TC", "TARJETA") ||
            !pago.tdcNumero.isNullOrBlank() -> "TDC"
        tipo in
            setOf(
                "NEQ",
                "NEQUI",
            ) ||
            siglas in setOf("NEQ", "NEQUI") ||
            !pago.codigoVerificacion.isNullOrBlank() -> "NEQ"
        tipo in setOf("ANTICIPO", "ABONO") ||
            siglas in setOf("ANTICIPO", "ABONO") ||
            !pago.idAbonoDetalle.isNullOrBlank() -> "ANTICIPO"
        else -> null
    }
}
