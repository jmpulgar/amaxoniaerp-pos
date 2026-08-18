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
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.update
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

private const val SHORT_CODE_LENGTH = 10
private const val DEFAULT_TERM_PAYMENT_ID = 3
private const val INVOICE_USER_LENGTH = 32
private const val QUANTITY_SCALE = 3
private const val PERCENT_BASE = 100.0
private const val PACKAGING_UNIT_LENGTH = 15
private const val PROMOTION_CODE_LENGTH = 15
private const val PROMOTION_IDENTIFIER_LENGTH = 36
private const val PROMOTION_TYPE_LENGTH = 20
private const val PROMOTION_NAME_LENGTH = 200
private const val PAYMENT_USER_LENGTH = 60

/** Contexto compartido por las escrituras de factura dentro de la transacciÃ³n de venta. */
internal data class SaleWriteContext(
    val request: ProcessSaleRequest,
    val invoiceId: String,
    val invoiceCode: String,
    val now: LocalDateTime,
    val today: LocalDate,
    val monetaryContext: MonetaryContext,
    val creditDecision: CreditDecision,
)

internal fun insertFactura(ctx: SaleWriteContext) {
    val f = ctx.request.factura
    val subtotalBase = ctx.monetaryContext.toBase(f.subtotal)
    val descuentosItemsBase =
        ctx.monetaryContext.toBase(
            f.descuentosItemFactura.takeIf { it > 0.0 }
                ?: ctx.request.items.sumOf { it.itemMontoDescuento },
        )
    val montoItemsBase =
        ctx.monetaryContext.toBase(
            f.montoItemsFactura.takeIf { it > 0.0 }
                ?: (f.subtotal - f.descuentosItemFactura).coerceAtLeast(0.0),
        )
    val ivaTotalBase = ctx.monetaryContext.toBase(f.ivaTotalFactura)
    val totalGeneralBase = ctx.monetaryContext.toBase(f.totalTotalFactura)
    val fechaVencimientoFactura =
        ctx.creditDecision.fechaVencimiento
            ?: ctx.today.plusDays(ctx.monetaryContext.diasVencimiento.toLong())
    val totalBultosQty = ctx.request.items.sumOf { it.itemCantidadTotal }
    val serieSucursalValue = f.serieSucursal.take(SHORT_CODE_LENGTH)
    val cajaSecuenciaValue = resolveCajaSecuenciaCodigo(f.idCajaSecuencia)

    val facturaTable = SalesFacturaTableFactory.forCountry(ctx.monetaryContext.countryCode)
    facturaTable.insert {
        it[facturaTable.idFactura] = ctx.invoiceId
        it[facturaTable.codFactura] = ctx.invoiceCode
        it[facturaTable.codFacturaFiscal] = f.codFacturaFiscal
        it[facturaTable.idCliente] = f.idCliente
        it[facturaTable.codVendedor] = f.codVendedor
        it[facturaTable.fechaFactura] = parseDateOrToday(f.fechaFactura, ctx.today)
        it[facturaTable.subtotal] = subtotalBase
        it[facturaTable.descuentosItemFactura] = descuentosItemsBase
        it[facturaTable.montoItemsFactura] = montoItemsBase
        it[facturaTable.ivaTotalFactura] = ivaTotalBase
        it[facturaTable.totalTotalFactura] = totalGeneralBase
        it[facturaTable.cantidadItems] = ctx.request.items.size
        it[facturaTable.totalizarSubTotal] = ctx.monetaryContext.toBase(f.totalizarSubTotal)
        it[facturaTable.totalizarDescuentoParcial] = ctx.monetaryContext.toBase(f.totalizarDescuentoParcial)
        it[facturaTable.totalizarTotalOperacion] = ctx.monetaryContext.toBase(f.totalizarTotalOperacion)
        it[facturaTable.totalizarPDescuentoGlobal] = ctx.monetaryContext.toBase(f.totalizarPDescuentoGlobal)
        it[facturaTable.totalizarDescuentoGlobal] = ctx.monetaryContext.toBase(f.totalizarDescuentoGlobal)
        it[facturaTable.totalizarBaseImponible] = ctx.monetaryContext.toBase(f.totalizarBaseImponible)
        it[facturaTable.totalizarMontoIva] = ctx.monetaryContext.toBase(f.totalizarMontoIva)
        it[facturaTable.totalizarTotalGeneral] = ctx.monetaryContext.toBase(f.totalizarTotalGeneral)
        it[facturaTable.totalizarTotalRetencion] = BigDecimal.ZERO.setScale(2)
        it[facturaTable.formaPago] = ctx.creditDecision.formaPago
        it[facturaTable.codEstatus] = f.codEstatus
        it[facturaTable.totalBultos] = totalBultosQty.toMoney()
        it[facturaTable.fechaCreacion] = ctx.now
        it[facturaTable.usuarioCreacion] = f.usuarioCreacion
        it[facturaTable.tipoFactura] = "factura_pos"
        it[facturaTable.modeloFactura] = "pos"
        it[facturaTable.terminoPagoId] =
            ctx.monetaryContext.defaultFormaPagoId.takeIf { id -> id > 0 } ?: DEFAULT_TERM_PAYMENT_ID
        it[facturaTable.facturarA] = f.facturarA
        it[facturaTable.facturarARuc] = f.facturarARuc
        it[facturaTable.facturarADireccion] = f.facturarADireccion
        it[facturaTable.facturarATelefono] = f.facturarATelefono
        it[facturaTable.validarStock] = ctx.monetaryContext.validarStock
        it[facturaTable.idShop] = f.idShop
        it[facturaTable.servicioPeriodo] = ""
        it[facturaTable.servicioOrden] = ""
        it[facturaTable.observacion] = ""
        it[facturaTable.fechaVencimiento] = fechaVencimientoFactura
        it[facturaTable.servicioAnio] = ctx.today.year
        it[facturaTable.servicioMes] =
            ctx.today.monthValue
                .toString()
                .padStart(2, '0')
        it[facturaTable.idCajaSecuencia] = f.idCajaSecuencia
        it[facturaTable.numcomContabilizado] = 0
        it[facturaTable.fechaContabilizado] = ctx.today
        it[facturaTable.serieSucursal] = serieSucursalValue
        it[facturaTable.cajaSecuencia] = cajaSecuenciaValue
        it[facturaTable.idSucursal] = f.idSucursal
        it[facturaTable.idCaja] = f.idCaja
        it[facturaTable.codigoCaja] = f.codigoCaja
        it[facturaTable.codCliente] = f.codCliente
        insertFacturaCountryFields(it, facturaTable, f, ctx)
    }
}

private fun insertFacturaCountryFields(
    stmt: org.jetbrains.exposed.sql.statements.InsertStatement<Number>,
    facturaTable: BaseSalesFacturaTable,
    f: com.amaxoniaerp.features.sales.domain.SaleInvoiceInput,
    ctx: SaleWriteContext,
) {
    // Campos exclusivos de Venezuela
    if (facturaTable is SalesFacturaTableVE) {
        stmt[facturaTable.nroz] = f.nroz
        stmt[facturaTable.impresoraSerial] = f.impresoraSerial
        stmt[facturaTable.multiMoneda] = ctx.monetaryContext.multiMoneda
        stmt[facturaTable.tasa] = ctx.monetaryContext.tasa.toFloat()
        stmt[facturaTable.idTasa] = ctx.monetaryContext.idTasa
        stmt[facturaTable.monedaBase] = ctx.monetaryContext.monedaBase
        stmt[facturaTable.abrMonedaBase] = ctx.monetaryContext.abrMonedaBase
        stmt[facturaTable.monedaSecundaria] = ctx.monetaryContext.monedaSecundaria
        stmt[facturaTable.abrMonedaSecundaria] = ctx.monetaryContext.abrMonedaSecundaria
        stmt[facturaTable.totalRef] = ctx.monetaryContext.totalRef.toFloat()
    } else if (facturaTable is SalesFacturaTablePA) {
        stmt[facturaTable.nroz] = ""
        stmt[facturaTable.impresoraSerial] = ""
        stmt[facturaTable.multiMoneda] = "0"
        stmt[facturaTable.tasa] = 0f
        stmt[facturaTable.idTasa] = 0
        stmt[facturaTable.monedaBase] = ctx.monetaryContext.monedaBase
        stmt[facturaTable.abrMonedaBase] = ctx.monetaryContext.abrMonedaBase
        stmt[facturaTable.monedaSecundaria] = 0
        stmt[facturaTable.abrMonedaSecundaria] = ""
        stmt[facturaTable.totalRef] = 0f
        stmt[facturaTable.clienteSucursalId] = f.clienteSucursalId
    }
}

/** Retorna lista de (detalleId, itemIndex) para vincular con lotes */
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
    ctx.request.impuestos.forEach { tax ->
        SalesFacturaImpuestosTable.insert {
            it[idFacturaImpuestos] = UUID.randomUUID().toString()
            it[idFactura] = ctx.invoiceId
            it[totalizarBaseRetencion] = ctx.monetaryContext.toBase(tax.totalizarBaseRetencion)
            it[codImpuestoIva] = tax.codImpuestoIva
            it[totalizarMontoIva2] = ctx.monetaryContext.toBase(tax.totalizarMontoIva2)
            it[usuarioCreacion] = ctx.request.factura.usuarioCreacion
            it[fechaCreacion] = ctx.now
        }
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

private fun computePaymentBreakdown(request: ProcessSaleRequest): PaymentBreakdown {
    val montosPorTipo =
        request.pagos
            .groupBy { it.tipoMovimiento.trim().uppercase() }
            .mapValues { (_, list) -> list.sumOf { it.monto } }

    fun amountOf(vararg keys: String): Double = keys.sumOf { key -> montosPorTipo[key] ?: 0.0 }

    val knownCodes =
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

    val montoOtros =
        amountOf("OT", "MB") +
            montosPorTipo
                .filterKeys { it !in knownCodes }
                .values
                .sum()

    return PaymentBreakdown(
        efectivo = amountOf("CASH", "EF", "EFE", "EFECTIVO"),
        cheque = amountOf("CH", "CHEQUE"),
        tarjeta = amountOf("TDC", "TARJETA", "PV", "POS", "NEQ"),
        deposito = amountOf("DE", "DEPOSITO"),
        transferencia = amountOf("TR", "TRANSFERENCIA", "PM"),
        credito = amountOf("CR", "CREDITO"),
        debito = amountOf("DB", "DEBITO"),
        certificado = amountOf("CERT", "CERTIFICADO"),
        otros = montoOtros,
    )
}

internal fun parseDateOrToday(
    value: String?,
    defaultDate: LocalDate,
): LocalDate {
    if (value.isNullOrBlank()) return defaultDate
    return runCatching { LocalDate.parse(value) }.getOrDefault(defaultDate)
}
