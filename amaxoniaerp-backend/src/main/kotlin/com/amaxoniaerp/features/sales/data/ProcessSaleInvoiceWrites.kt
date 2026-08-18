package com.amaxoniaerp.features.sales.data

import com.amaxoniaerp.features.sales.domain.ProcessSaleRequest
import org.jetbrains.exposed.sql.SqlExpressionBuilder.minus
import org.jetbrains.exposed.sql.SqlExpressionBuilder.plus
import org.jetbrains.exposed.sql.insert
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

private const val SHORT_CODE_LENGTH = 10
private const val DEFAULT_TERM_PAYMENT_ID = 3

/** Contexto compartido por las escrituras de factura dentro de la transacción de venta. */
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
    val v = resolveFacturaInsertValues(ctx)

    val facturaTable = SalesFacturaTableFactory.forCountry(ctx.monetaryContext.countryCode)
    facturaTable.insert {
        it[facturaTable.idFactura] = ctx.invoiceId
        it[facturaTable.codFactura] = ctx.invoiceCode
        it[facturaTable.codFacturaFiscal] = f.codFacturaFiscal
        it[facturaTable.idCliente] = f.idCliente
        it[facturaTable.codVendedor] = f.codVendedor
        it[facturaTable.fechaFactura] = parseDateOrToday(f.fechaFactura, ctx.today)
        it[facturaTable.subtotal] = v.subtotalBase
        it[facturaTable.descuentosItemFactura] = v.descuentosItemsBase
        it[facturaTable.montoItemsFactura] = v.montoItemsBase
        it[facturaTable.ivaTotalFactura] = v.ivaTotalBase
        it[facturaTable.totalTotalFactura] = v.totalGeneralBase
        it[facturaTable.cantidadItems] = ctx.request.items.size
        insertFacturaTotalizarFields(it, facturaTable, ctx)
        it[facturaTable.formaPago] = ctx.creditDecision.formaPago
        it[facturaTable.codEstatus] = f.codEstatus
        it[facturaTable.totalBultos] =
            ctx.request.items
                .sumOf { it.itemCantidadTotal }
                .toMoney()
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
        it[facturaTable.fechaVencimiento] = v.fechaVencimientoFactura
        it[facturaTable.servicioAnio] = ctx.today.year
        it[facturaTable.servicioMes] =
            ctx.today.monthValue
                .toString()
                .padStart(2, '0')
        it[facturaTable.idCajaSecuencia] = f.idCajaSecuencia
        it[facturaTable.numcomContabilizado] = 0
        it[facturaTable.fechaContabilizado] = ctx.today
        it[facturaTable.serieSucursal] = f.serieSucursal.take(SHORT_CODE_LENGTH)
        it[facturaTable.cajaSecuencia] = resolveCajaSecuenciaCodigo(f.idCajaSecuencia)
        it[facturaTable.idSucursal] = f.idSucursal
        it[facturaTable.idCaja] = f.idCaja
        it[facturaTable.codigoCaja] = f.codigoCaja
        it[facturaTable.codCliente] = f.codCliente
        insertFacturaCountryFields(it, facturaTable, f, ctx)
    }
}

private fun insertFacturaTotalizarFields(
    stmt: org.jetbrains.exposed.sql.statements.InsertStatement<Number>,
    facturaTable: BaseSalesFacturaTable,
    ctx: SaleWriteContext,
) {
    val f = ctx.request.factura
    stmt[facturaTable.totalizarSubTotal] = ctx.monetaryContext.toBase(f.totalizarSubTotal)
    stmt[facturaTable.totalizarDescuentoParcial] = ctx.monetaryContext.toBase(f.totalizarDescuentoParcial)
    stmt[facturaTable.totalizarTotalOperacion] = ctx.monetaryContext.toBase(f.totalizarTotalOperacion)
    stmt[facturaTable.totalizarPDescuentoGlobal] = ctx.monetaryContext.toBase(f.totalizarPDescuentoGlobal)
    stmt[facturaTable.totalizarDescuentoGlobal] = ctx.monetaryContext.toBase(f.totalizarDescuentoGlobal)
    stmt[facturaTable.totalizarBaseImponible] = ctx.monetaryContext.toBase(f.totalizarBaseImponible)
    stmt[facturaTable.totalizarMontoIva] = ctx.monetaryContext.toBase(f.totalizarMontoIva)
    stmt[facturaTable.totalizarTotalGeneral] = ctx.monetaryContext.toBase(f.totalizarTotalGeneral)
    stmt[facturaTable.totalizarTotalRetencion] = BigDecimal.ZERO.setScale(2)
}

private class FacturaInsertValues(
    val subtotalBase: BigDecimal,
    val descuentosItemsBase: BigDecimal,
    val montoItemsBase: BigDecimal,
    val ivaTotalBase: BigDecimal,
    val totalGeneralBase: BigDecimal,
    val fechaVencimientoFactura: LocalDate,
)

private fun resolveFacturaInsertValues(ctx: SaleWriteContext): FacturaInsertValues {
    val f = ctx.request.factura
    return FacturaInsertValues(
        subtotalBase = ctx.monetaryContext.toBase(f.subtotal),
        descuentosItemsBase =
            ctx.monetaryContext.toBase(
                f.descuentosItemFactura.takeIf { it > 0.0 }
                    ?: ctx.request.items.sumOf { it.itemMontoDescuento },
            ),
        montoItemsBase =
            ctx.monetaryContext.toBase(
                f.montoItemsFactura.takeIf { it > 0.0 }
                    ?: (f.subtotal - f.descuentosItemFactura).coerceAtLeast(0.0),
            ),
        ivaTotalBase = ctx.monetaryContext.toBase(f.ivaTotalFactura),
        totalGeneralBase = ctx.monetaryContext.toBase(f.totalTotalFactura),
        fechaVencimientoFactura =
            ctx.creditDecision.fechaVencimiento
                ?: ctx.today.plusDays(ctx.monetaryContext.diasVencimiento.toLong()),
    )
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
