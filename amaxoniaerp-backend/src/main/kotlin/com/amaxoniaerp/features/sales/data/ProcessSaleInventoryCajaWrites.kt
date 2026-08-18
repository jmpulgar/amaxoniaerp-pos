package com.amaxoniaerp.features.sales.data

import com.amaxoniaerp.features.sales.domain.InsufficientStockException
import com.amaxoniaerp.features.sales.domain.SaleItemInput
import com.amaxoniaerp.features.sales.domain.SalePaymentInput
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greaterEq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.minus
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.update
import java.math.BigDecimal
import java.time.Year
import java.time.format.DateTimeFormatter
import java.util.UUID

private const val INVENTORY_QUANTITY_SCALE = 4
private const val TWO_DIGIT_YEAR_MODULUS = 100
private const val STANDARD_USER_LENGTH = 20

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
    ctx.request.pagos.forEach { pago ->
        insertCajaDetallePago(ctx, ids, pago)
    }
}

private fun cajaConcepto(
    ctx: SaleWriteContext,
    fechaTexto: String,
    montoTexto: String,
): String {
    val clienteNombre =
        ctx.request.factura.facturarA
            .ifBlank { "CLIENTE MOSTRADOR" }
    return "Ingreso por Factura #${ctx.invoiceCode}, Fecha: $fechaTexto, " +
        "Cliente: $clienteNombre, Monto: $montoTexto."
}

private fun insertCajaNueva(
    ctx: SaleWriteContext,
    ids: CajaEntryIds,
    totalBase: BigDecimal,
    fechaTexto: String,
    montoTexto: String,
) {
    val cajaNuevaTable = SalesCajaNuevaTableFactory.forCountry(ctx.monetaryContext.countryCode)

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
        it[cajaNuevaTable.concepto] = cajaConcepto(ctx, fechaTexto, montoTexto)
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
        it[cajaNuevaTable.idCajaSecuencia] = ctx.request.factura.idCajaSecuencia
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

private fun insertCajaDetallePago(
    ctx: SaleWriteContext,
    ids: CajaEntryIds,
    pago: SalePaymentInput,
) {
    val cajaNuevaDetalleTable = SalesCajaNuevaDetalleTableFactory.forCountry(ctx.monetaryContext.countryCode)
    val detalleId = UUID.randomUUID().toString()
    val montoPagoBase = ctx.monetaryContext.toBase(pago.monto)
    val montoRecibidoBase = ctx.monetaryContext.toBase(pago.montoRecibido)

    cajaNuevaDetalleTable.insert {
        it[cajaNuevaDetalleTable.cajaDetalleId] = detalleId
        it[cajaNuevaDetalleTable.cajaId] = ids.cajaId
        it[cajaNuevaDetalleTable.idFormaPago] = pago.idFormaPago
        it[cajaNuevaDetalleTable.idTransaccion] = ids.transactionId
        it[cajaNuevaDetalleTable.cajaReciboId] = ids.cajaReciboId
        it[cajaNuevaDetalleTable.monto] = montoPagoBase
        it[cajaNuevaDetalleTable.montoOriginal] = BigDecimal.ZERO.setScale(2)
        it[cajaNuevaDetalleTable.concepto] = null
        it[cajaNuevaDetalleTable.usuarioCreacion] =
            ctx.request.factura.usuarioCreacion
                .take(STANDARD_USER_LENGTH)
        it[cajaNuevaDetalleTable.fechaCreacion] = ctx.now
        it[cajaNuevaDetalleTable.retencionTipo] = ""
        it[cajaNuevaDetalleTable.retencionPorcentaje] = ""
        it[cajaNuevaDetalleTable.numero] = ""
        it[cajaNuevaDetalleTable.observacion] = ""
        it[cajaNuevaDetalleTable.retencionBaseCalculo] = ""
        it[cajaNuevaDetalleTable.serieSucursal] = ""
        it[cajaNuevaDetalleTable.cajaSecuencia] = ""
        it[cajaNuevaDetalleTable.numeroControl] = ""
        it[cajaNuevaDetalleTable.numeroComprobante] = ""
        it[cajaNuevaDetalleTable.retencionMonto] = ""
        it[cajaNuevaDetalleTable.retencionDetalleJson] = ""
        // Campos exclusivos de Venezuela
        if (cajaNuevaDetalleTable is SalesCajaNuevaDetalleTableVE) {
            it[cajaNuevaDetalleTable.montoRecibido] = montoRecibidoBase
            it[cajaNuevaDetalleTable.montoMonedaPrincipal] = montoPagoBase
        }
    }

    SalesCajaNuevaDetalleFormaPagoTable.insert {
        it[cajaDetalleFormaPagoId] = UUID.randomUUID().toString()
        it[this.cajaId] = ids.cajaId
        it[cajaDetalleId] = detalleId
        it[tipoMovimiento] = pago.tipoMovimiento
        it[idFormaPago] = pago.idFormaPago
        it[comprobante] = "FACT"
        it[concepto] = "Ingreso por venta"
        it[monto] = montoPagoBase
        it[montoOriginal] = montoPagoBase
        it[tdcProveedor] = ""
        it[tdcNumero] = ""
        it[tdcTitular] = ""
        it[tdcVencimiento] = ""
        it[tdcCvv] = ""
        it[codigoVerificacion] = ""
        it[idAbonoDetalle] = ""
        it[efectivoCambio] = ctx.monetaryContext.toBase(pago.efectivoCambio)
    }
}
