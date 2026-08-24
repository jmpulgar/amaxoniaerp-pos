package com.amaxoniaerp.features.creditnotes.data

import com.amaxoniaerp.core.time.BusinessClock
import com.amaxoniaerp.features.creditnotes.domain.CreateCreditNoteRequest
import com.amaxoniaerp.features.creditnotes.domain.CreditNoteSettlementType
import com.amaxoniaerp.features.creditnotes.domain.CreditNoteValidationException
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.batchInsert
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.update
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

/** Contexto común resuelto al crear/preparar una nota de crédito. */
internal class CreditNoteCreationContext(
    val invoice: InvoiceHeader,
    val client: ClientContext,
    val invoiceLines: List<SourceInvoiceLine>,
    val baseProcessedLines: List<ProcessedLine>,
    val allReturnedAfterOperation: Boolean,
)

/**
 * Bloquea la factura, carga su contexto y valida la solicitud (líneas,
 * cantidades y regla de anulación). Comparte la resolución entre `create`
 * (flujo directo VE/legacy) y `preparePanama` (flujo staged PA).
 */
internal fun resolveCreationContext(
    countryCode: String,
    request: CreateCreditNoteRequest,
): CreditNoteCreationContext {
    validateCreateRequest(request)

    lockInvoiceOrThrow(request.idFactura)

    val invoice = requireInvoiceHeader(request.idFactura)
    val client = loadClient(invoice.idCliente)
    val invoiceLines = loadInvoiceLines(countryCode, invoice.idFactura)
    if (invoiceLines.isEmpty()) {
        throw CreditNoteValidationException("La factura origen no tiene líneas disponibles")
    }

    val requestedLines = normalizeRequestedLines(request, invoiceLines)
    val baseProcessedLines = buildProcessedLines(invoiceLines, requestedLines)
    val allReturnedAfterOperation = resolvesFullReturn(invoiceLines, baseProcessedLines)

    if (request.anular && !allReturnedAfterOperation) {
        throw CreditNoteValidationException(
            "Para anular la factura debes devolver la totalidad de las líneas restantes",
        )
    }

    return CreditNoteCreationContext(
        invoice = invoice,
        client = client,
        invoiceLines = invoiceLines,
        baseProcessedLines = baseProcessedLines,
        allReturnedAfterOperation = allReturnedAfterOperation,
    )
}

/** Datos necesarios para insertar la cabecera de una NC (flujo directo). */
internal data class InsertCreditNoteHeaderInput(
    val countryCode: String,
    val request: CreateCreditNoteRequest,
    val username: String,
    val ctx: CreditNoteCreationContext,
    val financials: CreditNoteFinancials,
    val creditNoteId: String,
    val creditNoteCode: String,
    val creditNoteDate: LocalDate,
    val now: LocalDateTime,
    val cajaContext: CajaContext,
)

internal fun insertCreditNoteHeader(input: InsertCreditNoteHeaderInput) {
    val totals = input.financials.totals
    val headerTable = CreditNoteHeaderTableFactory.forCountry(input.countryCode)
    headerTable.insert {
        it[idDevolucion] = input.creditNoteId
        it[codDevolucion] = input.creditNoteCode
        it[codFactura] = input.ctx.invoice.idFactura
        it[fechaDevolucion] = input.creditNoteDate
        it[codDevolucionFiscal] = PENDING_FISCAL_CODE
        if (headerTable is CreditNoteHeaderTableVE) {
            it[headerTable.nroz] = ""
            it[headerTable.impresoraSerial] = ""
        }
        it[observacion] = input.request.observacion.take(MAX_OBSERVATION_LENGTH)
        it[idCliente] = input.ctx.invoice.idCliente
        it[codVendedor] = input.ctx.invoice.codVendedor
        it[fechaFactura] = input.ctx.invoice.fechaFactura
        it[subtotal] = totals.subtotal
        it[impuesto] = totals.tax
        it[total] = totals.total
        it[usuarioCreacion] = input.username.take(MAX_USERNAME_LENGTH)
        it[fechaCreacion] = input.now
        it[periodoDevolucion] = input.request.periodo.take(MAX_PERIOD_LENGTH)
        it[contabilizado] = 0
        it[numcomContabilizado] = 0
        it[fechaContabilizado] = input.creditNoteDate
        it[idCajaSecuencia] = input.request.idCajaSecuencia
        it[serieSucursal] = input.cajaContext.serieSucursal
        it[cajaSecuencia] = input.cajaContext.cajaSecuencia
        it[idSucursal] = input.cajaContext.idSucursal
        it[idCaja] = input.cajaContext.idCaja
        it[codigoCaja] = input.cajaContext.codigoCaja
        it[codCliente] = input.ctx.client.codigoCliente
        it[descuentoGlobal] = input.financials.globalDiscount
        it[pdescuentoGlobal] = input.ctx.invoice.totalizarPDescuentoGlobal
        it[numeroDocumentoFiscal] = ""
        it[registroMigrado] = 0
        if (headerTable is CreditNoteHeaderTablePA) {
            it[headerTable.tipoDocumento] = "04"
            it[headerTable.naturalezaOperacion] = "11"
            it[headerTable.tipoOperacion] = 1
            it[headerTable.formatoCAFE] = 1
            it[headerTable.entregaCAFE] = 1
            it[headerTable.envioContenedor] = 1
            it[headerTable.tipoVenta] = 1
            it[headerTable.informacionInteres] = ""
            it[headerTable.cufe] = ""
            it[headerTable.qr] = ""
            it[headerTable.fechaRecepcionDGI] = input.now
            it[headerTable.nroProtocoloAutorizacion] = ""
            it[headerTable.fechaLimite] = input.now
            it[headerTable.descuentoGlobalVenta] = input.financials.globalDiscount
        }
    }
}

internal fun insertCreditNoteDetails(
    creditNoteId: String,
    lines: List<ProcessedLine>,
    annulFullyReturnedLines: Boolean,
) {
    if (lines.isNotEmpty()) {
        CreditNoteDetailTable.batchInsert(lines) { line ->
            this[CreditNoteDetailTable.idDevolucionDetalle] = UUID.randomUUID().toString()
            this[CreditNoteDetailTable.idDevolucion] = creditNoteId
            this[CreditNoteDetailTable.idDetalleFactura] = line.sourceLine.idDetalleFactura
            this[CreditNoteDetailTable.idItem] = line.sourceLine.idItem
            this[CreditNoteDetailTable.itemAlmacen] = line.sourceLine.almacen
            this[CreditNoteDetailTable.itemCantidad] = line.quantity.setScale(QUANTITY_SCALE, RoundingMode.HALF_UP)
            this[CreditNoteDetailTable.itemPrecioSinIva] = line.sourceLine.precioSinIva
            this[CreditNoteDetailTable.itemDescuento] = line.sourceLine.descuentoPorcentaje
            this[CreditNoteDetailTable.itemMontoDescuento] = line.discountAmount
            this[CreditNoteDetailTable.itemPIva] = line.sourceLine.pIva
            this[CreditNoteDetailTable.itemTotalSinIva] = line.totalSinIva
            this[CreditNoteDetailTable.itemTotalConIva] = line.totalConIva
            this[CreditNoteDetailTable.codVendedor] = line.sourceLine.codVendedor
            this[CreditNoteDetailTable.itemCodigo] = line.sourceLine.codigo
            this[CreditNoteDetailTable.itemReferencia] = line.sourceLine.referencia
        }
    }

    if (annulFullyReturnedLines) {
        val fullyCancelledIds =
            lines
                .filter { line ->
                    line.sourceLine.availableQuantity
                        .subtract(line.quantity)
                        .isEffectivelyZero()
                }.map { it.sourceLine.idDetalleFactura }

        if (fullyCancelledIds.isNotEmpty()) {
            CreditNoteFacturaDetalleTable.update(
                { CreditNoteFacturaDetalleTable.idDetalleFactura inList fullyCancelledIds },
            ) {
                it[anulado] = true
            }
        }
    }
}

/** Efectos post-confirmación de la NC (caja original, inventario, liquidación). */
internal data class ApplyCreditNoteEffectsInput(
    val countryCode: String,
    val request: CreateCreditNoteRequest,
    val invoice: InvoiceHeader,
    val client: ClientContext,
    val creditNoteId: String,
    val creditNoteCode: String,
    val total: BigDecimal,
    val lines: List<ProcessedLine>,
    val username: String,
    val now: LocalDateTime,
    val creditNoteDate: LocalDate,
    val cajaContext: CajaContext,
    val allReturnedAfterOperation: Boolean,
    val annulInvoiceOnPartial: Boolean,
    val partialPaymentFormId: Int?,
)

internal fun applyCreditNoteEffects(input: ApplyCreditNoteEffectsInput) {
    if (input.annulInvoiceOnPartial) {
        CreditNoteFacturaTable.update({ CreditNoteFacturaTable.idFactura eq input.invoice.idFactura }) {
            it[codEstatus] = ANNULLED_INVOICE_STATUS
        }
    }

    if (input.allReturnedAfterOperation) {
        cancelInvoiceAndOriginalCash(
            input.countryCode,
            input.invoice.idFactura,
            input.username,
            input.creditNoteDate,
            input.now,
        )
    } else {
        registerPartialCreditNoteOnOriginalCash(
            PartialCashRegistration(
                countryCode = input.countryCode,
                invoice = input.invoice,
                creditNoteId = input.creditNoteId,
                creditNoteCode = input.creditNoteCode,
                total = input.total,
                paymentFormId = checkNotNull(input.partialPaymentFormId),
                username = input.username,
                now = input.now,
            ),
        )
    }

    if (input.request.devolverStock) {
        restoreInventory(
            InventoryRestore(
                countryCode = input.countryCode,
                invoice = input.invoice,
                creditNoteId = input.creditNoteId,
                creditNoteCode = input.creditNoteCode,
                lines = input.lines,
                username = input.username,
                date = input.creditNoteDate,
                now = input.now,
                idSucursal = input.cajaContext.idSucursal,
            ),
        )
    }

    applySettlement(input)
}

private fun applySettlement(input: ApplyCreditNoteEffectsInput) {
    when (input.request.settlementType) {
        CreditNoteSettlementType.NINGUNO -> Unit
        CreditNoteSettlementType.REINTEGRO ->
            registerRefundCashEgress(
                RefundEgress(
                    countryCode = input.countryCode,
                    invoice = input.invoice,
                    creditNoteId = input.creditNoteId,
                    creditNoteCode = input.creditNoteCode,
                    total = input.total,
                    idFormaPago = requireRefundPaymentForm(input.request.idFormaPagoReintegro),
                    username = input.username,
                    now = input.now,
                    date = input.creditNoteDate,
                    cajaContext = input.cajaContext,
                ),
            )
        CreditNoteSettlementType.ABONO ->
            registerAbono(
                AbonoRegistration(
                    creditNoteId = input.creditNoteId,
                    total = input.total,
                    invoice = input.invoice,
                    client = input.client,
                    username = input.username,
                    now = input.now,
                    cajaContext = input.cajaContext,
                ),
            )
        CreditNoteSettlementType.CERTIFICADO_REGALO ->
            registerGiftCertificate(
                GiftCertificateRegistration(
                    creditNoteId = input.creditNoteId,
                    total = input.total,
                    client = input.client,
                    username = input.username,
                    now = input.now,
                    cajaContext = input.cajaContext,
                ),
            )
    }
}

internal fun resolveCreationTiming(
    countryCode: String,
    request: CreateCreditNoteRequest,
): Pair<LocalDate, LocalDateTime> = parseDate(request.fecha) to BusinessClock.nowForCountry(countryCode)

/** Datos para insertar la cabecera PENDIENTE de la NC PA staged. */
internal data class InsertPreparedHeaderPAInput(
    val request: CreateCreditNoteRequest,
    val username: String,
    val ctx: CreditNoteCreationContext,
    val financials: CreditNoteFinancials,
    val creditNoteId: String,
    val creditNoteCode: String,
    val creditNoteDate: LocalDate,
    val now: LocalDateTime,
    val cajaContext: CajaContext,
    val numeroDocumentoFiscal: String,
)

internal fun insertPreparedHeaderPA(input: InsertPreparedHeaderPAInput) {
    val header = CreditNoteHeaderTablePA
    header.insert {
        it[idDevolucion] = input.creditNoteId
        it[codDevolucion] = input.creditNoteCode
        it[codFactura] = input.ctx.invoice.idFactura
        it[fechaDevolucion] = input.creditNoteDate
        it[codDevolucionFiscal] = PENDING_FISCAL_CODE
        it[observacion] = input.request.observacion.take(MAX_OBSERVATION_LENGTH)
        it[idCliente] = input.ctx.invoice.idCliente
        it[codVendedor] = input.ctx.invoice.codVendedor
        it[fechaFactura] = input.ctx.invoice.fechaFactura
        it[subtotal] = input.financials.totals.subtotal
        it[impuesto] = input.financials.totals.tax
        it[total] = input.financials.totals.total
        it[usuarioCreacion] = input.username.take(MAX_USERNAME_LENGTH)
        it[fechaCreacion] = input.now
        it[periodoDevolucion] = input.request.periodo.take(MAX_PERIOD_LENGTH)
        it[contabilizado] = 0
        it[numcomContabilizado] = 0
        it[fechaContabilizado] = input.creditNoteDate
        it[idCajaSecuencia] = input.request.idCajaSecuencia
        it[serieSucursal] = input.cajaContext.serieSucursal
        it[cajaSecuencia] = input.cajaContext.cajaSecuencia
        it[idSucursal] = input.cajaContext.idSucursal
        it[idCaja] = input.cajaContext.idCaja
        it[codigoCaja] = input.cajaContext.codigoCaja
        it[codCliente] = input.ctx.client.codigoCliente
        it[descuentoGlobal] = input.financials.globalDiscount
        it[pdescuentoGlobal] = input.ctx.invoice.totalizarPDescuentoGlobal
        it[header.numeroDocumentoFiscal] = input.numeroDocumentoFiscal
        it[registroMigrado] = 0
        it[tipoDocumento] = "04"
        it[naturalezaOperacion] = "11"
        it[tipoOperacion] = 1
        it[formatoCAFE] = 1
        it[entregaCAFE] = 1
        it[envioContenedor] = 1
        it[tipoVenta] = 1
        it[informacionInteres] = ""
        it[cufe] = ""
        it[qr] = ""
        it[fechaRecepcionDGI] = null
        it[nroProtocoloAutorizacion] = ""
        it[fechaLimite] = null
        it[descuentoGlobalVenta] = input.financials.globalDiscount
    }
}
