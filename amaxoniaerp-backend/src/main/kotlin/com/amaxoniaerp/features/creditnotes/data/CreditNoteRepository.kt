package com.amaxoniaerp.features.creditnotes.data

import com.amaxoniaerp.core.time.BusinessClock
import com.amaxoniaerp.features.clients.data.ClientsTable
import com.amaxoniaerp.features.creditnotes.domain.ConfirmCreditNoteFiscalRequest
import com.amaxoniaerp.features.creditnotes.domain.ConfirmCreditNoteFiscalResponse
import com.amaxoniaerp.features.creditnotes.domain.CreateCreditNoteRequest
import com.amaxoniaerp.features.creditnotes.domain.CreateCreditNoteResponse
import com.amaxoniaerp.features.creditnotes.domain.CreditNoteDetailLine
import com.amaxoniaerp.features.creditnotes.domain.CreditNoteDetailResponse
import com.amaxoniaerp.features.creditnotes.domain.CreditNoteFiscalDocument
import com.amaxoniaerp.features.creditnotes.domain.CreditNoteFiscalLine
import com.amaxoniaerp.features.creditnotes.domain.CreditNoteFiscalStatus
import com.amaxoniaerp.features.creditnotes.domain.CreditNoteNotFoundException
import com.amaxoniaerp.features.creditnotes.domain.CreditNoteSettlementType
import com.amaxoniaerp.features.creditnotes.domain.CreditNoteSourceInvoiceDetailResponse
import com.amaxoniaerp.features.creditnotes.domain.CreditNoteSourceInvoiceLine
import com.amaxoniaerp.features.creditnotes.domain.CreditNoteSourceInvoiceSummary
import com.amaxoniaerp.features.creditnotes.domain.CreditNoteSummary
import com.amaxoniaerp.features.creditnotes.domain.CreditNoteValidationException
import com.amaxoniaerp.features.creditnotes.domain.PreparedCreditNote
import com.amaxoniaerp.features.electronicinvoice.data.FECorrelativosTable
import com.amaxoniaerp.features.electronicinvoice.domain.PacResponse
import com.amaxoniaerp.features.items.data.FacturaDetalleProductoLoteTable
import com.amaxoniaerp.features.items.data.ItemExistenciaAlmacenTable
import com.amaxoniaerp.features.items.data.ItemLoteTable
import com.amaxoniaerp.features.pos.data.CajaFormaPagoTable
import com.amaxoniaerp.features.sales.data.CajaIngresoEgreso
import com.amaxoniaerp.features.sales.data.CajaStatus
import com.amaxoniaerp.features.sales.data.SalesCajaNuevaDetalleFormaPagoTable
import com.amaxoniaerp.features.sales.data.SalesCajaNuevaDetalleTableFactory
import com.amaxoniaerp.features.sales.data.SalesCajaNuevaDetalleTableVE
import com.amaxoniaerp.features.sales.data.SalesCajaNuevaReciboTableFactory
import com.amaxoniaerp.features.sales.data.SalesCajaNuevaTableFactory
import com.amaxoniaerp.features.sales.data.SalesKardexDetalleTableFactory
import com.amaxoniaerp.features.sales.data.SalesKardexDetalleTablePA
import com.amaxoniaerp.features.sales.data.SalesKardexTableFactory
import com.amaxoniaerp.features.sales.data.SalesKardexTablePA
import org.jetbrains.exposed.exceptions.ExposedSQLException
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greater
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.SqlExpressionBuilder.like
import org.jetbrains.exposed.sql.SqlExpressionBuilder.neq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.andWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.insertIgnore
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

class CreditNoteRepository {
    fun listCreditNotes(
        countryCode: String,
        limit: Int,
        offset: Long,
        search: String?,
        fechaInicio: LocalDate?,
        fechaFin: LocalDate?,
    ): Pair<List<CreditNoteSummary>, Long> {
        val headerTable = CreditNoteHeaderTableFactory.forCountry(countryCode)
        val query =
            headerTable
                .join(ClientsTable, JoinType.LEFT, headerTable.idCliente, ClientsTable.idCliente)
                .join(CreditNoteFacturaTable, JoinType.LEFT, headerTable.codFactura, CreditNoteFacturaTable.idFactura)
                .selectAll()

        if (fechaInicio != null && fechaFin != null) {
            query.andWhere { headerTable.fechaDevolucion.between(fechaInicio, fechaFin) }
        }

        if (!search.isNullOrBlank()) {
            val term = "%$search%"
            query.andWhere {
                (headerTable.codDevolucion like term) or
                    (CreditNoteFacturaTable.codFactura like term) or
                    (ClientsTable.nombre like term) or
                    (ClientsTable.apellido like term) or
                    (ClientsTable.rif like term)
            }
        }

        val total = query.count()
        val data =
            query
                .orderBy(headerTable.fechaCreacion to SortOrder.DESC)
                .limit(limit)
                .offset(offset)
                .map { mapSummaryRow(it, countryCode) }

        return data to total
    }

    fun getCreditNoteDetail(
        id: String,
        countryCode: String,
    ): CreditNoteDetailResponse? {
        val headerTable = CreditNoteHeaderTableFactory.forCountry(countryCode)
        val headerRow =
            headerTable
                .join(ClientsTable, JoinType.LEFT, headerTable.idCliente, ClientsTable.idCliente)
                .join(CreditNoteFacturaTable, JoinType.LEFT, headerTable.codFactura, CreditNoteFacturaTable.idFactura)
                .selectAll()
                .where { headerTable.idDevolucion eq id }
                .limit(1)
                .firstOrNull()
                ?: return null

        val detailRows =
            CreditNoteDetailTable
                .join(
                    CreditNoteFacturaDetalleTable,
                    JoinType.LEFT,
                    CreditNoteDetailTable.idDetalleFactura,
                    CreditNoteFacturaDetalleTable.idDetalleFactura,
                ).selectAll()
                .where { CreditNoteDetailTable.idDevolucion eq id }
                .toList()

        val lines = detailRows.map(::mapDetailLine)
        val header = mapHeaderContext(headerRow, countryCode)
        return buildDetailResponse(header, lines)
    }

    fun listEligibleInvoices(
        countryCode: String,
        limit: Int,
        offset: Long,
        search: String?,
    ): Pair<List<CreditNoteSourceInvoiceSummary>, Long> {
        val query =
            CreditNoteFacturaTable
                .join(ClientsTable, JoinType.LEFT, CreditNoteFacturaTable.idCliente, ClientsTable.idCliente)
                .selectAll()

        if (!countryCode.equals("PA", ignoreCase = true)) {
            query.andWhere { CreditNoteFacturaTable.codEstatus neq 3 }
        }
        query.andWhere { CreditNoteFacturaTable.totalTotalFactura greater BigDecimal.ZERO }

        if (!search.isNullOrBlank()) {
            val term = "%$search%"
            query.andWhere {
                (CreditNoteFacturaTable.codFactura like term) or
                    (CreditNoteFacturaTable.numeroDocumentoFiscal like term) or
                    (ClientsTable.nombre like term) or
                    (ClientsTable.apellido like term) or
                    (ClientsTable.rif like term)
            }
        }

        val invoiceRows =
            query
                .orderBy(CreditNoteFacturaTable.fechaCreacion to SortOrder.DESC)
                .limit(limit)
                .offset(offset)
                .toList()

        val summaries =
            invoiceRows.mapNotNull { row ->
                val invoiceId = row[CreditNoteFacturaTable.idFactura]
                val source = buildSourceInvoiceDetail(invoiceId, countryCode) ?: return@mapNotNull null
                if (source.remainingAmount <= 0.0) {
                    null
                } else {
                    CreditNoteSourceInvoiceSummary(
                        id = source.id,
                        codigo = source.codigo,
                        codigoFiscal = source.codigoFiscal,
                        numeroDocumentoFiscal = source.numeroDocumentoFiscal,
                        fecha = source.fecha,
                        clienteNombre = source.clienteNombre,
                        clienteIdentificacion = source.clienteIdentificacion,
                        total = source.totalOriginal,
                        remainingAmount = source.remainingAmount,
                        items = source.lines.count { it.cantidadDisponible > 0.0 },
                        moneda = source.moneda,
                    )
                }
            }

        return summaries to summaries.size.toLong()
    }

    fun getSourceInvoiceDetail(
        invoiceId: String,
        countryCode: String = "VE",
    ): CreditNoteSourceInvoiceDetailResponse? = buildSourceInvoiceDetail(invoiceId, countryCode)

    /**
     * Reserva e inserta una NC PA sin aplicar todavía efectos comerciales.
     * La fila PENDIENTE y sus líneas son la reserva de cantidades frente a otra
     * preparación concurrente.
     */
    fun preparePanama(
        request: CreateCreditNoteRequest,
        username: String,
    ): PreparedCreditNote {
        validateCreateRequest(request)

        lockInvoiceForCreditNote(request.idFactura)
            ?: throw CreditNoteNotFoundException("Factura origen no encontrada")

        val invoice =
            loadInvoiceHeader(request.idFactura)
                ?: throw CreditNoteNotFoundException("Factura origen no encontrada")
        val client = loadClient(invoice.idCliente)
        val invoiceLines = loadInvoiceLines("PA", invoice.idFactura)
        if (invoiceLines.isEmpty()) {
            throw CreditNoteValidationException("La factura origen no tiene líneas disponibles")
        }

        val requestedLines = normalizeRequestedLines(request, invoiceLines)
        val baseProcessedLines = buildProcessedLines(invoiceLines, requestedLines)
        val allReturnedAfterOperation =
            invoiceLines.all { sourceLine ->
                val returnedInThisRequest =
                    baseProcessedLines
                        .firstOrNull { it.sourceLine.idDetalleFactura == sourceLine.idDetalleFactura }
                        ?.quantity
                        ?: BigDecimal.ZERO
                sourceLine.availableQuantity.subtract(returnedInThisRequest).isEffectivelyZero()
            }

        if (request.anular && !allReturnedAfterOperation) {
            throw CreditNoteValidationException("Para anular la factura debes devolver la totalidad de las líneas restantes")
        }

        // Valida la configuración necesaria antes de reservar la NC.
        if (!allReturnedAfterOperation) {
            resolveCreditNotePaymentFormId()
        }

        val financials =
            calculateFinancials(
                invoice = invoice,
                invoiceLines = invoiceLines,
                processedLines = baseProcessedLines,
                previousTotals = loadPreviousCreditNoteTotals("PA", invoice.idFactura),
                allReturnedAfterOperation = allReturnedAfterOperation,
            )
        val creditNoteDate = parseDate(request.fecha)
        val now = BusinessClock.nowForCountry("PA")
        val cajaContext = resolveCajaContext(request.idCajaSecuencia)
        val nextCorrelative = advanceCreditNoteCorrelative(cajaContext.idCaja)
        val numeroDocumentoFiscal = reserveFiscalDocumentNumber()
        val creditNoteId = UUID.randomUUID().toString()
        val creditNoteCode = buildCreditNoteCode(cajaContext.codigoCaja, nextCorrelative)
        val header = CreditNoteHeaderTablePA

        header.insert {
            it[idDevolucion] = creditNoteId
            it[codDevolucion] = creditNoteCode
            it[codFactura] = invoice.idFactura
            it[fechaDevolucion] = creditNoteDate
            it[codDevolucionFiscal] = PENDING_FISCAL_CODE
            it[observacion] = request.observacion.take(MAX_OBSERVATION_LENGTH)
            it[idCliente] = invoice.idCliente
            it[codVendedor] = invoice.codVendedor
            it[fechaFactura] = invoice.fechaFactura
            it[subtotal] = financials.totals.subtotal
            it[impuesto] = financials.totals.tax
            it[total] = financials.totals.total
            it[usuarioCreacion] = username.take(MAX_USERNAME_LENGTH)
            it[fechaCreacion] = now
            it[periodoDevolucion] = request.periodo.take(MAX_PERIOD_LENGTH)
            it[contabilizado] = 0
            it[numcomContabilizado] = 0
            it[fechaContabilizado] = creditNoteDate
            it[idCajaSecuencia] = request.idCajaSecuencia
            it[serieSucursal] = cajaContext.serieSucursal
            it[cajaSecuencia] = cajaContext.cajaSecuencia
            it[idSucursal] = cajaContext.idSucursal
            it[idCaja] = cajaContext.idCaja
            it[codigoCaja] = cajaContext.codigoCaja
            it[codCliente] = client.codigoCliente
            it[descuentoGlobal] = financials.globalDiscount
            it[pdescuentoGlobal] = invoice.totalizarPDescuentoGlobal
            it[header.numeroDocumentoFiscal] = numeroDocumentoFiscal
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
            it[descuentoGlobalVenta] = financials.globalDiscount
        }

        insertCreditNoteDetails(creditNoteId, financials.lines)

        return PreparedCreditNote(
            id = creditNoteId,
            codigo = creditNoteCode,
            numeroDocumentoFiscal = numeroDocumentoFiscal,
        )
    }

    /**
     * Finaliza una NC PA aceptada por el PAC. La cabecera se bloquea y el estado
     * se comprueba antes de ejecutar cualquier efecto para que la repetición sea
     * idempotente.
     */
    fun finalizePanamaAccepted(
        id: String,
        request: CreateCreditNoteRequest,
        pacResponse: PacResponse,
        numeroDocumentoFiscal: String,
    ): CreateCreditNoteResponse {
        val header = CreditNoteHeaderTablePA
        val headerRow =
            header
                .selectAll()
                .where { header.idDevolucion eq id }
                .forUpdate()
                .singleOrNull()
                ?: throw CreditNoteNotFoundException("Nota de crédito no encontrada")

        val currentStatus =
            resolveFiscalStatus(
                headerRow[header.codDevolucionFiscal].orEmpty(),
                headerRow[header.numeroDocumentoFiscal].orEmpty(),
            )
        if (currentStatus == CreditNoteFiscalStatus.CONFIRMADA) {
            return buildCreationResponse(id, true)
        }
        if (currentStatus == CreditNoteFiscalStatus.RECHAZADA) {
            return buildCreationResponse(id, false, "La nota de crédito ya fue rechazada")
        }

        val invoiceId = headerRow[header.codFactura]
        lockInvoiceForCreditNote(invoiceId)
            ?: throw CreditNoteNotFoundException("Factura origen no encontrada")
        val invoice =
            loadInvoiceHeader(invoiceId)
                ?: throw CreditNoteNotFoundException("Factura origen no encontrada")
        val client = loadClient(invoice.idCliente)
        val invoiceLines = loadInvoiceLines("PA", invoice.idFactura)
        val processedLines = loadPreparedLines(id, invoiceLines)
        if (processedLines.isEmpty()) {
            throw CreditNoteValidationException("La nota de crédito no tiene líneas preparadas")
        }
        val allReturnedAfterOperation = invoiceLines.all { it.availableQuantity.isEffectivelyZero() }
        val now = BusinessClock.nowForCountry("PA")
        val creditNoteDate = headerRow[header.fechaDevolucion]
        val cajaContext = resolveCajaContext(headerRow[header.idCajaSecuencia] ?: request.idCajaSecuencia)
        val username = headerRow[header.usuarioCreacion]
        val reservedDocumentNumber =
            normalizeFiscalDocumentNumber(
                headerRow[header.numeroDocumentoFiscal].orEmpty(),
            )
        val normalizedDocumentNumber = normalizeFiscalDocumentNumber(numeroDocumentoFiscal)
        if (normalizedDocumentNumber != reservedDocumentNumber) {
            throw CreditNoteValidationException("El número fiscal no coincide con la reserva de la nota de crédito")
        }
        val cufe = pacResponse.cufe?.trim().orEmpty()
        if (cufe.isBlank()) {
            throw CreditNoteValidationException("El PAC aceptó la NC sin CUFE")
        }

        header.update({ header.idDevolucion eq id }) {
            it[codDevolucionFiscal] = CONFIRMED_FISCAL_CODE
            it[header.numeroDocumentoFiscal] = normalizedDocumentNumber
            it[header.cufe] = cufe
            pacResponse.qr?.let { value -> it[header.qr] = value }
            pacResponse.nroProtocoloAutorizacion?.let { value -> it[header.nroProtocoloAutorizacion] = value }
            parsePacDate(pacResponse.fechaRecepcionDGI)?.let { value -> it[header.fechaRecepcionDGI] = value }
            parsePacDate(pacResponse.fechaLimite)?.let { value -> it[header.fechaLimite] = value }
            it[header.informacionInteres] = ""
        }

        processedLines.forEach { line ->
            if (line.sourceLine.availableQuantity.isEffectivelyZero()) {
                CreditNoteFacturaDetalleTable.update(
                    { CreditNoteFacturaDetalleTable.idDetalleFactura eq line.sourceLine.idDetalleFactura },
                ) {
                    it[anulado] = true
                }
            }
        }

        CreditNoteFacturaTable.update({ CreditNoteFacturaTable.idFactura eq invoice.idFactura }) {
            it[codEstatus] = 3
        }

        if (allReturnedAfterOperation) {
            cancelInvoiceAndOriginalCash("PA", invoice.idFactura, username, creditNoteDate, now)
        } else {
            registerPartialCreditNoteOnOriginalCash(
                countryCode = "PA",
                invoice = invoice,
                creditNoteId = id,
                creditNoteCode = headerRow[header.codDevolucion],
                total = headerRow[header.total],
                paymentFormId = resolveCreditNotePaymentFormId(),
                username = username,
                now = now,
            )
        }

        if (request.devolverStock) {
            restoreInventory(
                countryCode = "PA",
                invoice = invoice,
                creditNoteId = id,
                creditNoteCode = headerRow[header.codDevolucion],
                lines = processedLines,
                username = username,
                date = creditNoteDate,
                now = now,
                idSucursal = cajaContext.idSucursal,
            )
        }

        when (request.settlementType) {
            CreditNoteSettlementType.NINGUNO -> Unit
            CreditNoteSettlementType.REINTEGRO ->
                registerRefundCashEgress(
                    countryCode = "PA",
                    invoice = invoice,
                    creditNoteId = id,
                    creditNoteCode = headerRow[header.codDevolucion],
                    total = headerRow[header.total],
                    idFormaPago =
                        request.idFormaPagoReintegro
                            ?: throw CreditNoteValidationException("Forma de pago de reintegro requerida"),
                    username = username,
                    now = now,
                    date = creditNoteDate,
                    cajaContext = cajaContext,
                )
            CreditNoteSettlementType.ABONO ->
                registerAbono(
                    creditNoteId = id,
                    total = headerRow[header.total],
                    invoice = invoice,
                    client = client,
                    username = username,
                    now = now,
                    cajaContext = cajaContext,
                )
            CreditNoteSettlementType.CERTIFICADO_REGALO ->
                registerGiftCertificate(
                    creditNoteId = id,
                    total = headerRow[header.total],
                    client = client,
                    username = username,
                    now = now,
                    cajaContext = cajaContext,
                )
        }

        return buildCreationResponse(id, true)
    }

    fun markPanamaFiscalStatus(
        id: String,
        status: CreditNoteFiscalStatus,
        message: String,
    ): CreateCreditNoteResponse {
        require(status == CreditNoteFiscalStatus.RECHAZADA || status == CreditNoteFiscalStatus.INCIERTA) {
            "Sólo se pueden marcar estados PAC no confirmados"
        }
        val header = CreditNoteHeaderTablePA
        val row =
            header
                .selectAll()
                .where { header.idDevolucion eq id }
                .forUpdate()
                .singleOrNull()
                ?: throw CreditNoteNotFoundException("Nota de crédito no encontrada")
        val currentStatus =
            resolveFiscalStatus(
                row[header.codDevolucionFiscal].orEmpty(),
                row[header.numeroDocumentoFiscal].orEmpty(),
            )
        if (currentStatus != CreditNoteFiscalStatus.CONFIRMADA) {
            header.update({ header.idDevolucion eq id }) {
                it[codDevolucionFiscal] = fiscalStatusCode(status)
                it[informacionInteres] = message.take(MAX_PAC_DIAGNOSTIC_LENGTH)
            }
        }
        return buildCreationResponse(
            id = id,
            success = currentStatus == CreditNoteFiscalStatus.CONFIRMADA,
            message = message,
        )
    }

    fun recordPanamaDiagnostic(
        id: String,
        message: String,
    ): CreateCreditNoteResponse {
        val header = CreditNoteHeaderTablePA
        val row =
            header
                .selectAll()
                .where { header.idDevolucion eq id }
                .forUpdate()
                .singleOrNull()
                ?: throw CreditNoteNotFoundException("Nota de crédito no encontrada")
        val currentStatus =
            resolveFiscalStatus(
                row[header.codDevolucionFiscal].orEmpty(),
                row[header.numeroDocumentoFiscal].orEmpty(),
            )
        if (currentStatus == CreditNoteFiscalStatus.CONFIRMADA) {
            header.update({ header.idDevolucion eq id }) {
                it[header.informacionInteres] = message.take(MAX_PAC_DIAGNOSTIC_LENGTH)
            }
        }
        return buildCreationResponse(
            id = id,
            success = currentStatus == CreditNoteFiscalStatus.CONFIRMADA,
            message = message,
        )
    }

    fun create(
        countryCode: String,
        request: CreateCreditNoteRequest,
        username: String,
    ): CreateCreditNoteResponse {
        validateCreateRequest(request)

        lockInvoiceForCreditNote(request.idFactura)
            ?: throw CreditNoteNotFoundException("Factura origen no encontrada")

        val invoice =
            loadInvoiceHeader(request.idFactura)
                ?: throw CreditNoteNotFoundException("Factura origen no encontrada")
        val client = loadClient(invoice.idCliente)
        val invoiceLines = loadInvoiceLines(countryCode, invoice.idFactura)
        if (invoiceLines.isEmpty()) {
            throw CreditNoteValidationException("La factura origen no tiene líneas disponibles")
        }

        val requestedLines = normalizeRequestedLines(request, invoiceLines)
        val baseProcessedLines = buildProcessedLines(invoiceLines, requestedLines)
        val allReturnedAfterOperation =
            invoiceLines.all { sourceLine ->
                val returnedInThisRequest =
                    baseProcessedLines
                        .firstOrNull { it.sourceLine.idDetalleFactura == sourceLine.idDetalleFactura }
                        ?.quantity
                        ?: BigDecimal.ZERO
                sourceLine.availableQuantity.subtract(returnedInThisRequest).isEffectivelyZero()
            }

        if (request.anular && !allReturnedAfterOperation) {
            throw CreditNoteValidationException("Para anular la factura debes devolver la totalidad de las líneas restantes")
        }

        val previousTotals = loadPreviousCreditNoteTotals(countryCode, invoice.idFactura)
        val financials =
            calculateFinancials(
                invoice = invoice,
                invoiceLines = invoiceLines,
                processedLines = baseProcessedLines,
                previousTotals = previousTotals,
                allReturnedAfterOperation = allReturnedAfterOperation,
            )
        val processedLines = financials.lines
        val partialPaymentFormId =
            if (allReturnedAfterOperation) {
                null
            } else {
                resolveCreditNotePaymentFormId()
            }
        val creditNoteDate = parseDate(request.fecha)
        val now = BusinessClock.nowForCountry(countryCode)
        val cajaContext = resolveCajaContext(request.idCajaSecuencia)
        val nextCorrelative = advanceCreditNoteCorrelative(cajaContext.idCaja)
        val creditNoteId = UUID.randomUUID().toString()
        val creditNoteCode = buildCreditNoteCode(cajaContext.codigoCaja, nextCorrelative)
        val totals = financials.totals

        val headerTable = CreditNoteHeaderTableFactory.forCountry(countryCode)
        headerTable.insert {
            it[idDevolucion] = creditNoteId
            it[codDevolucion] = creditNoteCode
            it[codFactura] = invoice.idFactura
            it[fechaDevolucion] = creditNoteDate
            it[codDevolucionFiscal] = PENDING_FISCAL_CODE
            if (headerTable is CreditNoteHeaderTableVE) {
                it[headerTable.nroz] = ""
                it[headerTable.impresoraSerial] = ""
            }
            it[observacion] = request.observacion.take(MAX_OBSERVATION_LENGTH)
            it[idCliente] = invoice.idCliente
            it[codVendedor] = invoice.codVendedor
            it[fechaFactura] = invoice.fechaFactura
            it[subtotal] = totals.subtotal
            it[impuesto] = totals.tax
            it[total] = totals.total
            it[usuarioCreacion] = username.take(MAX_USERNAME_LENGTH)
            it[fechaCreacion] = now
            it[periodoDevolucion] = request.periodo.take(MAX_PERIOD_LENGTH)
            it[contabilizado] = 0
            it[numcomContabilizado] = 0
            it[fechaContabilizado] = creditNoteDate
            it[idCajaSecuencia] = request.idCajaSecuencia
            it[serieSucursal] = cajaContext.serieSucursal
            it[cajaSecuencia] = cajaContext.cajaSecuencia
            it[idSucursal] = cajaContext.idSucursal
            it[idCaja] = cajaContext.idCaja
            it[codigoCaja] = cajaContext.codigoCaja
            it[codCliente] = client.codigoCliente
            it[descuentoGlobal] = financials.globalDiscount
            it[pdescuentoGlobal] = invoice.totalizarPDescuentoGlobal
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
                it[headerTable.fechaRecepcionDGI] = now
                it[headerTable.nroProtocoloAutorizacion] = ""
                it[headerTable.fechaLimite] = now
                it[headerTable.descuentoGlobalVenta] = financials.globalDiscount
            }
        }

        processedLines.forEach { line ->
            CreditNoteDetailTable.insert {
                it[idDevolucionDetalle] = UUID.randomUUID().toString()
                it[idDevolucion] = creditNoteId
                it[idDetalleFactura] = line.sourceLine.idDetalleFactura
                it[idItem] = line.sourceLine.idItem
                it[itemAlmacen] = line.sourceLine.almacen
                it[itemCantidad] = line.quantity.setScale(3, RoundingMode.HALF_UP)
                it[itemPrecioSinIva] = line.sourceLine.precioSinIva
                it[itemDescuento] = line.sourceLine.descuentoPorcentaje
                it[itemMontoDescuento] = line.discountAmount
                it[itemPIva] = line.sourceLine.pIva
                it[itemTotalSinIva] = line.totalSinIva
                it[itemTotalConIva] = line.totalConIva
                it[codVendedor] = line.sourceLine.codVendedor
                it[itemCodigo] = line.sourceLine.codigo
                it[itemReferencia] = line.sourceLine.referencia
            }

            val lineFullyCancelled =
                line.sourceLine.availableQuantity
                    .subtract(line.quantity)
                    .isEffectivelyZero()
            if (lineFullyCancelled) {
                CreditNoteFacturaDetalleTable.update(
                    { CreditNoteFacturaDetalleTable.idDetalleFactura eq line.sourceLine.idDetalleFactura },
                ) {
                    it[anulado] = true
                }
            }
        }

        if (countryCode.equals("PA", ignoreCase = true)) {
            CreditNoteFacturaTable.update({ CreditNoteFacturaTable.idFactura eq invoice.idFactura }) {
                it[codEstatus] = 3
            }
        }

        if (allReturnedAfterOperation) {
            cancelInvoiceAndOriginalCash(countryCode, invoice.idFactura, username, creditNoteDate, now)
        } else {
            registerPartialCreditNoteOnOriginalCash(
                countryCode = countryCode,
                invoice = invoice,
                creditNoteId = creditNoteId,
                creditNoteCode = creditNoteCode,
                total = totals.total,
                paymentFormId = checkNotNull(partialPaymentFormId),
                username = username,
                now = now,
            )
        }

        if (request.devolverStock) {
            restoreInventory(
                countryCode = countryCode,
                invoice = invoice,
                creditNoteId = creditNoteId,
                creditNoteCode = creditNoteCode,
                lines = processedLines,
                username = username,
                date = creditNoteDate,
                now = now,
                idSucursal = cajaContext.idSucursal,
            )
        }

        when (request.settlementType) {
            CreditNoteSettlementType.NINGUNO -> Unit
            CreditNoteSettlementType.REINTEGRO ->
                registerRefundCashEgress(
                    countryCode = countryCode,
                    invoice = invoice,
                    creditNoteId = creditNoteId,
                    creditNoteCode = creditNoteCode,
                    total = totals.total,
                    idFormaPago =
                        request.idFormaPagoReintegro ?: throw CreditNoteValidationException("Forma de pago de reintegro requerida"),
                    username = username,
                    now = now,
                    date = creditNoteDate,
                    cajaContext = cajaContext,
                )
            CreditNoteSettlementType.ABONO ->
                registerAbono(
                    creditNoteId = creditNoteId,
                    total = totals.total,
                    invoice = invoice,
                    client = client,
                    username = username,
                    now = now,
                    cajaContext = cajaContext,
                )
            CreditNoteSettlementType.CERTIFICADO_REGALO ->
                registerGiftCertificate(
                    creditNoteId = creditNoteId,
                    total = totals.total,
                    client = client,
                    username = username,
                    now = now,
                    cajaContext = cajaContext,
                )
        }

        val responseHeader =
            CreditNoteHeaderContext(
                id = creditNoteId,
                codigo = creditNoteCode,
                facturaId = invoice.idFactura,
                facturaCodigo = invoice.codFactura,
                fecha = creditNoteDate,
                fechaCreacion = now,
                periodo = request.periodo,
                observacion = request.observacion,
                clienteNombre = client.nombreCompleto,
                clienteIdentificacion = client.identificacion,
                clienteDireccion = client.direccion,
                clienteTelefono = client.telefono,
                subtotal = totals.subtotal,
                impuesto = totals.tax,
                total = totals.total,
                fiscalStatus = CreditNoteFiscalStatus.PENDIENTE,
                fiscalNumber = "",
                printerSerial = "",
                originalFiscalNumber =
                    request.numeroFiscalElectronico.ifBlank {
                        invoice.numeroDocumentoFiscal.ifBlank { invoice.codFacturaFiscal }
                    },
                originalInvoiceDate = invoice.fechaFactura,
                anulaFacturaCompleta = allReturnedAfterOperation,
            )
        val responseLines = processedLines.map { it.toDetailLine() }
        val detail = buildDetailResponse(responseHeader, responseLines)

        return CreateCreditNoteResponse(
            success = true,
            id = creditNoteId,
            codigo = creditNoteCode,
            subtotal = totals.subtotal.toDouble(),
            impuesto = totals.tax.toDouble(),
            total = totals.total.toDouble(),
            fiscalStatus = CreditNoteFiscalStatus.PENDIENTE,
            detail = detail,
        )
    }

    fun confirmFiscal(
        countryCode: String,
        id: String,
        request: ConfirmCreditNoteFiscalRequest,
    ): ConfirmCreditNoteFiscalResponse {
        val headerTable = CreditNoteHeaderTableFactory.forCountry(countryCode)
        val header =
            headerTable
                .selectAll()
                .where { headerTable.idDevolucion eq id }
                .limit(1)
                .firstOrNull()
                ?: throw CreditNoteNotFoundException("Nota de crédito no encontrada")

        val requestedFiscalCode = request.codDevolucionFiscal.trim()
        val requestedDocumentNumber = request.numeroDocumentoFiscal.trim()
        val normalizedFiscalCode = requestedFiscalCode.takeIf(::isValidFiscalValue) ?: PENDING_FISCAL_CODE
        val normalizedDocumentNumber = requestedDocumentNumber.takeIf(::isValidFiscalValue).orEmpty()
        val fiscalStatus = resolveFiscalStatus(normalizedFiscalCode, normalizedDocumentNumber)

        headerTable.update({ headerTable.idDevolucion eq id }) {
            it[codDevolucionFiscal] = normalizedFiscalCode
            it[numeroDocumentoFiscal] = normalizedDocumentNumber
            if (headerTable is CreditNoteHeaderTableVE) {
                it[headerTable.impresoraSerial] = request.printerSerial.trim()
                it[headerTable.nroz] = request.nroz.trim()
            }
        }

        return ConfirmCreditNoteFiscalResponse(
            success = true,
            id = id,
            codigo = header[headerTable.codDevolucion],
            fiscalStatus = fiscalStatus,
            codDevolucionFiscal = normalizedFiscalCode,
            numeroDocumentoFiscal = normalizedDocumentNumber,
            printerSerial = request.printerSerial.trim(),
        )
    }

    private fun buildSourceInvoiceDetail(
        invoiceId: String,
        countryCode: String,
    ): CreditNoteSourceInvoiceDetailResponse? {
        val invoice = loadInvoiceHeader(invoiceId) ?: return null
        val client = loadClient(invoice.idCliente)
        val lines = loadInvoiceLines(countryCode, invoiceId)
        if (lines.isEmpty()) return null

        val remainingAmount = lines.fold(BigDecimal.ZERO) { acc, line -> acc + line.availableTotalConIva }

        val totalOriginal = invoice.totalTotalFactura.toDouble()
        val totalRef = invoice.totalRef?.toDouble() ?: 0.0
        val isBs = invoice.moneda.equals("BS", ignoreCase = true) || invoice.moneda.equals("Bs.", ignoreCase = true)

        val totalBs = if (isBs) totalOriginal else totalRef
        val totalUsd = if (!isBs) totalOriginal else totalRef

        return CreditNoteSourceInvoiceDetailResponse(
            id = invoice.idFactura,
            codigo = invoice.codFactura,
            codigoFiscal = invoice.codFacturaFiscal,
            numeroDocumentoFiscal = invoice.numeroDocumentoFiscal,
            fecha = formatDate(invoice.fechaFactura),
            clienteId = invoice.idCliente,
            clienteNombre = client.nombreCompleto,
            clienteIdentificacion = client.identificacion,
            clienteDireccion = client.direccion,
            clienteTelefono = client.telefono,
            codVendedor = invoice.codVendedor,
            totalOriginal = totalOriginal,
            subtotalOriginal = invoice.totalizarSubTotal.toDouble(),
            impuestoOriginal = invoice.totalizarMontoIva.toDouble(),
            remainingAmount = remainingAmount.toDouble(),
            moneda = invoice.moneda,
            tasa = invoice.tasa?.toDouble(),
            totalBs = totalBs,
            totalUsd = totalUsd,
            lines = lines.map { it.toResponseLine() },
        )
    }

    private fun loadInvoiceHeader(invoiceId: String): InvoiceHeader? {
        val row =
            CreditNoteFacturaTable
                .selectAll()
                .where { CreditNoteFacturaTable.idFactura eq invoiceId }
                .limit(1)
                .firstOrNull()
                ?: return null

        return InvoiceHeader(
            idFactura = row[CreditNoteFacturaTable.idFactura],
            codFactura = row[CreditNoteFacturaTable.codFactura],
            codFacturaFiscal = row[CreditNoteFacturaTable.codFacturaFiscal].orEmpty(),
            numeroDocumentoFiscal = row[CreditNoteFacturaTable.numeroDocumentoFiscal].orEmpty(),
            idCliente = row[CreditNoteFacturaTable.idCliente],
            codVendedor = row[CreditNoteFacturaTable.codVendedor],
            codEstatus = row[CreditNoteFacturaTable.codEstatus] ?: 0,
            fechaFactura = row[CreditNoteFacturaTable.fechaFactura],
            subtotal = row[CreditNoteFacturaTable.subtotal],
            totalizarSubTotal = row[CreditNoteFacturaTable.totalizarSubTotal],
            totalizarTotalOperacion = row[CreditNoteFacturaTable.totalizarTotalOperacion],
            totalizarPDescuentoGlobal = row[CreditNoteFacturaTable.totalizarPDescuentoGlobal],
            totalizarDescuentoGlobal = row[CreditNoteFacturaTable.totalizarDescuentoGlobal],
            totalizarBaseImponible = row[CreditNoteFacturaTable.totalizarBaseImponible],
            totalizarMontoIva = row[CreditNoteFacturaTable.totalizarMontoIva],
            totalizarTotalGeneral = row[CreditNoteFacturaTable.totalizarTotalGeneral],
            totalTotalFactura = row[CreditNoteFacturaTable.totalTotalFactura],
            formaPago = row[CreditNoteFacturaTable.formaPago],
            idCajaSecuencia = row[CreditNoteFacturaTable.idCajaSecuencia],
            idCaja = row[CreditNoteFacturaTable.idCaja],
            idSucursal = row[CreditNoteFacturaTable.idSucursal],
            serieSucursal = row[CreditNoteFacturaTable.serieSucursal],
            codigoCaja = row[CreditNoteFacturaTable.codigoCaja],
            facturarA = row[CreditNoteFacturaTable.facturarA],
            facturarARuc = row[CreditNoteFacturaTable.facturarARuc],
            facturarADireccion = row[CreditNoteFacturaTable.facturarADireccion],
            facturarATelefono = row[CreditNoteFacturaTable.facturarATelefono],
            moneda = row[CreditNoteFacturaTable.abrMonedaBase].orEmpty().ifBlank { "USD" },
            tasa = row[CreditNoteFacturaTable.tasa],
            totalRef = row[CreditNoteFacturaTable.totalRef],
        )
    }

    /**
     * Serializes credit-note creation for one source invoice. The lock must be
     * acquired before loading returned quantities so concurrent transactions
     * cannot both validate against the same available balance.
     */
    private fun lockInvoiceForCreditNote(invoiceId: String): ResultRow? =
        CreditNoteFacturaTable
            .select(CreditNoteFacturaTable.idFactura)
            .where { CreditNoteFacturaTable.idFactura eq invoiceId }
            .forUpdate()
            .singleOrNull()

    private fun loadClient(idCliente: String): ClientContext {
        val row =
            ClientsTable
                .selectAll()
                .where { ClientsTable.idCliente eq idCliente }
                .limit(1)
                .firstOrNull()

        val nombre = row?.get(ClientsTable.nombre).orEmpty()
        val apellido = row?.get(ClientsTable.apellido).orEmpty()
        return ClientContext(
            idCliente = idCliente,
            codigoCliente = row?.get(ClientsTable.codCliente).orEmpty().ifBlank { idCliente },
            nombreCompleto = "$nombre $apellido".trim().ifBlank { "CONSUMIDOR FINAL" },
            identificacion = row?.get(ClientsTable.rif).orEmpty().ifBlank { "CF" },
            direccion = row?.get(ClientsTable.direccion).orEmpty(),
            telefono = row?.get(ClientsTable.telefonos).orEmpty(),
        )
    }

    private fun loadInvoiceLines(invoiceId: String): List<SourceInvoiceLine> = loadInvoiceLines(countryCode = null, invoiceId = invoiceId)

    private fun loadInvoiceLines(
        countryCode: String?,
        invoiceId: String,
    ): List<SourceInvoiceLine> {
        val detailRows =
            CreditNoteFacturaDetalleTable
                .selectAll()
                .where { CreditNoteFacturaDetalleTable.idFactura eq invoiceId }
                .orderBy(CreditNoteFacturaDetalleTable.idDetalleFactura)
                .toList()
        if (detailRows.isEmpty()) return emptyList()

        val detailIds = detailRows.map { it[CreditNoteFacturaDetalleTable.idDetalleFactura] }
        val allReturnedRows =
            CreditNoteDetailTable
                .selectAll()
                .where { CreditNoteDetailTable.idDetalleFactura inList detailIds }
                .toList()
        val returnedRows =
            if (countryCode.equals("PA", ignoreCase = true)) {
                val rejectedCreditNoteIds =
                    try {
                        CreditNoteHeaderTablePA
                            .selectAll()
                            .mapNotNull { row ->
                                row[CreditNoteHeaderTablePA.codDevolucionFiscal]
                                    .orEmpty()
                                    .trim()
                                    .takeIf { it.equals(REJECTED_FISCAL_CODE, ignoreCase = true) }
                                    ?.let { row[CreditNoteHeaderTablePA.idDevolucion] }
                            }.toSet()
                    } catch (_: ExposedSQLException) {
                        // Algunos esquemas de pruebas sólo tienen las tablas comunes.
                        emptySet()
                    }
                allReturnedRows.filterNot { it[CreditNoteDetailTable.idDevolucion] in rejectedCreditNoteIds }
            } else {
                allReturnedRows
            }
        val returnedByDetail =
            returnedRows
                .groupBy { it[CreditNoteDetailTable.idDetalleFactura] }
                .mapValues { (_, rows) ->
                    rows.fold(
                        BigDecimal.ZERO,
                    ) { acc, row -> acc + row[CreditNoteDetailTable.itemCantidad].setScale(3, RoundingMode.HALF_UP) }
                }

        return detailRows.map { row ->
            val quantityOriginal = row[CreditNoteFacturaDetalleTable.itemCantidadTotal].setScale(3, RoundingMode.HALF_UP)
            val returned =
                returnedByDetail[row[CreditNoteFacturaDetalleTable.idDetalleFactura]] ?: BigDecimal.ZERO.setScale(3, RoundingMode.HALF_UP)
            val available = quantityOriginal.subtract(returned).coerceAtLeastZero(3)
            val unitTotalSinIva = divideSafe(row[CreditNoteFacturaDetalleTable.itemTotalSinIva], quantityOriginal, 6)
            val unitTotalConIva = divideSafe(row[CreditNoteFacturaDetalleTable.itemTotalConIva], quantityOriginal, 6)
            val unitDiscount = divideSafe(row[CreditNoteFacturaDetalleTable.itemMontoDescuento], quantityOriginal, 6)

            SourceInvoiceLine(
                idDetalleFactura = row[CreditNoteFacturaDetalleTable.idDetalleFactura],
                idItem = row[CreditNoteFacturaDetalleTable.idItem],
                descripcion = row[CreditNoteFacturaDetalleTable.itemDescripcion],
                codigo = row[CreditNoteFacturaDetalleTable.itemCodigo].orEmpty(),
                referencia = row[CreditNoteFacturaDetalleTable.itemReferencia].orEmpty(),
                quantityOriginal = quantityOriginal,
                returnedQuantity = returned,
                availableQuantity = available,
                precioSinIva = row[CreditNoteFacturaDetalleTable.itemPrecioSinIva],
                descuentoPorcentaje = row[CreditNoteFacturaDetalleTable.itemDescuento],
                descuentoMontoTotal = row[CreditNoteFacturaDetalleTable.itemMontoDescuento],
                pIva = row[CreditNoteFacturaDetalleTable.itemPIva],
                totalSinIvaOriginal = row[CreditNoteFacturaDetalleTable.itemTotalSinIva],
                totalConIvaOriginal = row[CreditNoteFacturaDetalleTable.itemTotalConIva],
                availableTotalSinIva = unitTotalSinIva.multiply(available).setScale(2, RoundingMode.HALF_UP),
                availableTotalConIva = unitTotalConIva.multiply(available).setScale(2, RoundingMode.HALF_UP),
                unitDiscountAmount = unitDiscount,
                almacen = row[CreditNoteFacturaDetalleTable.itemAlmacen],
                codVendedor = row[CreditNoteFacturaDetalleTable.codVendedor],
            )
        }
    }

    private fun normalizeRequestedLines(
        request: CreateCreditNoteRequest,
        invoiceLines: List<SourceInvoiceLine>,
    ): Map<String, BigDecimal> {
        val requestLines =
            if (request.detalle.isEmpty() && request.anular) {
                invoiceLines
                    .filter { it.availableQuantity > BigDecimal.ZERO }
                    .map { source -> RequestedLine(source.idDetalleFactura, source.availableQuantity.toDouble()) }
            } else {
                request.detalle.map { RequestedLine(it.idDetalleFactura, it.cantidad) }
            }

        if (requestLines.isEmpty()) {
            throw CreditNoteValidationException("Debes indicar al menos una línea a devolver")
        }

        return requestLines
            .groupBy { it.idDetalleFactura }
            .mapValues { (_, rows) ->
                rows.fold(BigDecimal.ZERO) { acc, line ->
                    val quantity = BigDecimal.valueOf(line.cantidad).setScale(3, RoundingMode.HALF_UP)
                    if (quantity <= BigDecimal.ZERO) {
                        throw CreditNoteValidationException("Las cantidades a devolver deben ser mayores a cero")
                    }
                    acc + quantity
                }
            }
    }

    private fun buildProcessedLines(
        invoiceLines: List<SourceInvoiceLine>,
        requestedLines: Map<String, BigDecimal>,
    ): List<ProcessedLine> {
        val linesById = invoiceLines.associateBy { it.idDetalleFactura }
        return requestedLines
            .map { (idDetalleFactura, quantity) ->
                val sourceLine =
                    linesById[idDetalleFactura]
                        ?: throw CreditNoteValidationException("La línea $idDetalleFactura no pertenece a la factura origen")
                if (quantity > sourceLine.availableQuantity) {
                    throw CreditNoteValidationException(
                        "La cantidad a devolver para ${sourceLine.descripcion} excede lo disponible (${sourceLine.availableQuantity.toDouble()})",
                    )
                }

                val unitTotalSinIva = divideSafe(sourceLine.totalSinIvaOriginal, sourceLine.quantityOriginal, 6)
                val unitTotalConIva = divideSafe(sourceLine.totalConIvaOriginal, sourceLine.quantityOriginal, 6)

                ProcessedLine(
                    sourceLine = sourceLine,
                    quantity = quantity,
                    discountAmount = sourceLine.unitDiscountAmount.multiply(quantity).setScale(2, RoundingMode.HALF_UP),
                    totalSinIva = unitTotalSinIva.multiply(quantity).setScale(2, RoundingMode.HALF_UP),
                    totalConIva = unitTotalConIva.multiply(quantity).setScale(2, RoundingMode.HALF_UP),
                    globalDiscountAmount = BigDecimal.ZERO.setScale(2),
                )
            }.sortedBy { it.sourceLine.idDetalleFactura }
    }

    private fun loadPreviousCreditNoteTotals(
        countryCode: String,
        invoiceId: String,
    ): PreviousCreditNoteTotals {
        val headerTable = CreditNoteHeaderTableFactory.forCountry(countryCode)
        return headerTable
            .selectAll()
            .where { headerTable.codFactura eq invoiceId }
            .toList()
            .filter { row ->
                !countryCode.equals("PA", ignoreCase = true) ||
                    !row[headerTable.codDevolucionFiscal]
                        .orEmpty()
                        .trim()
                        .equals(REJECTED_FISCAL_CODE, ignoreCase = true)
            }.fold(PreviousCreditNoteTotals()) { totals, row ->
                totals.copy(
                    subtotal = totals.subtotal + row[headerTable.subtotal],
                    tax = totals.tax + row[headerTable.impuesto],
                    total = totals.total + row[headerTable.total],
                    globalDiscount = totals.globalDiscount + (row[headerTable.descuentoGlobal] ?: BigDecimal.ZERO),
                )
            }
    }

    private fun calculateFinancials(
        invoice: InvoiceHeader,
        invoiceLines: List<SourceInvoiceLine>,
        processedLines: List<ProcessedLine>,
        previousTotals: PreviousCreditNoteTotals,
        allReturnedAfterOperation: Boolean,
    ): CreditNoteFinancials {
        val originalGlobalDiscount = invoice.totalizarDescuentoGlobal.coerceAtLeastZero(2)
        val baseBeforeGlobal =
            invoice.totalizarTotalOperacion.takeIf { it > BigDecimal.ZERO }
                ?: invoiceLines.fold(BigDecimal.ZERO) { acc, line -> acc + line.totalSinIvaOriginal }
        val remainingGlobalDiscount = (originalGlobalDiscount - previousTotals.globalDiscount).coerceAtLeastZero(2)
        val processedBase = processedLines.fold(BigDecimal.ZERO) { acc, line -> acc + line.totalSinIva }
        val proportionalCurrentDiscount =
            proportionalAmount(
                amount = originalGlobalDiscount,
                numerator = processedBase,
                denominator = baseBeforeGlobal,
            )
        val currentGlobalDiscount =
            if (allReturnedAfterOperation) {
                remainingGlobalDiscount
            } else {
                minBigDecimal(proportionalCurrentDiscount, remainingGlobalDiscount)
            }
        var allocatedGlobalDiscount = BigDecimal.ZERO.setScale(2)
        var adjustedLines =
            processedLines.mapIndexed { index, line ->
                val allocation =
                    when {
                        currentGlobalDiscount.compareTo(BigDecimal.ZERO) == 0 -> BigDecimal.ZERO.setScale(2)
                        index == processedLines.lastIndex -> currentGlobalDiscount - allocatedGlobalDiscount
                        processedBase.compareTo(BigDecimal.ZERO) == 0 -> BigDecimal.ZERO.setScale(2)
                        else ->
                            minBigDecimal(
                                currentGlobalDiscount
                                    .multiply(line.totalSinIva)
                                    .divide(processedBase, 12, RoundingMode.HALF_UP)
                                    .setScale(2, RoundingMode.HALF_UP),
                                currentGlobalDiscount - allocatedGlobalDiscount,
                            )
                    }
                allocatedGlobalDiscount += allocation
                val subtotal = (line.totalSinIva - allocation).setScale(2, RoundingMode.HALF_UP)
                val tax = calculateLineTax(subtotal, line.sourceLine.pIva)
                line.copy(
                    totalSinIva = subtotal,
                    totalConIva = subtotal + tax,
                    globalDiscountAmount = allocation,
                )
            }

        if (allReturnedAfterOperation && adjustedLines.isNotEmpty()) {
            val originalTotal =
                invoice.totalTotalFactura.takeIf { it.compareTo(BigDecimal.ZERO) != 0 }
                    ?: invoice.totalizarTotalGeneral
            val targetTotal = (originalTotal - previousTotals.total).setScale(2, RoundingMode.HALF_UP)
            val targetTax = (invoice.totalizarMontoIva - previousTotals.tax).setScale(2, RoundingMode.HALF_UP)
            val targetSubtotal = targetTotal - targetTax
            val currentSubtotal = adjustedLines.fold(BigDecimal.ZERO) { acc, line -> acc + line.totalSinIva }
            val currentTotal = adjustedLines.fold(BigDecimal.ZERO) { acc, line -> acc + line.totalConIva }
            val subtotalResidual = targetSubtotal - currentSubtotal
            val totalResidual = targetTotal - currentTotal
            val lastIndex = adjustedLines.lastIndex
            adjustedLines =
                adjustedLines.mapIndexed { index, line ->
                    if (index != lastIndex) {
                        line
                    } else {
                        line.copy(
                            totalSinIva = (line.totalSinIva + subtotalResidual).setScale(2, RoundingMode.HALF_UP),
                            totalConIva = (line.totalConIva + totalResidual).setScale(2, RoundingMode.HALF_UP),
                        )
                    }
                }
        }

        return CreditNoteFinancials(
            lines = adjustedLines,
            totals = calculateTotals(adjustedLines),
            globalDiscount = currentGlobalDiscount,
        )
    }

    private fun proportionalAmount(
        amount: BigDecimal,
        numerator: BigDecimal,
        denominator: BigDecimal,
    ): BigDecimal {
        if (amount.compareTo(BigDecimal.ZERO) == 0 ||
            numerator.compareTo(BigDecimal.ZERO) <= 0 ||
            denominator.compareTo(BigDecimal.ZERO) <= 0
        ) {
            return BigDecimal.ZERO.setScale(2)
        }
        val boundedNumerator = minBigDecimal(numerator, denominator)
        return amount
            .multiply(boundedNumerator)
            .divide(denominator, 12, RoundingMode.HALF_UP)
            .setScale(2, RoundingMode.HALF_UP)
    }

    private fun calculateLineTax(
        subtotal: BigDecimal,
        taxRate: BigDecimal,
    ): BigDecimal {
        if (subtotal.compareTo(BigDecimal.ZERO) == 0 || taxRate.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO.setScale(2)
        }
        return subtotal
            .multiply(taxRate)
            .divide(BigDecimal("100"), 12, RoundingMode.HALF_UP)
            .setScale(2, RoundingMode.HALF_UP)
    }

    private fun minBigDecimal(
        first: BigDecimal,
        second: BigDecimal,
    ): BigDecimal = if (first <= second) first else second

    private fun calculateTotals(lines: List<ProcessedLine>): CreditNoteTotals {
        val subtotal = lines.fold(BigDecimal.ZERO.setScale(2)) { acc, line -> acc + line.totalSinIva }
        val total = lines.fold(BigDecimal.ZERO.setScale(2)) { acc, line -> acc + line.totalConIva }
        val tax = total.subtract(subtotal).setScale(2, RoundingMode.HALF_UP)
        return CreditNoteTotals(subtotal = subtotal, tax = tax, total = total)
    }

    private fun resolveCajaContext(idCajaSecuencia: String): CajaContext {
        val row =
            CreditNoteCajaSecuenciaTable
                .join(CreditNoteCajaTable, JoinType.INNER, CreditNoteCajaSecuenciaTable.idCaja, CreditNoteCajaTable.idCaja)
                .selectAll()
                .where { CreditNoteCajaSecuenciaTable.idCajaSecuencia eq idCajaSecuencia }
                .limit(1)
                .firstOrNull()
                ?: throw CreditNoteValidationException("No se encontró la caja secuencia indicada")

        return CajaContext(
            idCaja = row[CreditNoteCajaTable.idCaja],
            idSucursal = row[CreditNoteCajaTable.idSucursal] ?: 1,
            codigoCaja = row[CreditNoteCajaTable.codigo].orEmpty().ifBlank { "NC" },
            serieSucursal =
                row[CreditNoteCajaSecuenciaTable.serieSucursal]
                    ?: row[CreditNoteCajaTable.serieCaja]
                    ?: "00001",
            cajaSecuencia = row[CreditNoteCajaSecuenciaTable.secuencia].orEmpty().ifBlank { "000001" },
            impresoraModelo = row[CreditNoteCajaTable.impresoraModelo].orEmpty(),
        )
    }

    private fun advanceCreditNoteCorrelative(idCaja: String): Int {
        val current =
            CreditNoteCajaTable
                .select(CreditNoteCajaTable.notacreditoCorrelativo)
                .where { CreditNoteCajaTable.idCaja eq idCaja }
                .forUpdate()
                .limit(1)
                .firstOrNull()
                ?.get(CreditNoteCajaTable.notacreditoCorrelativo)
                ?: 0

        val next = current + 1
        val updated =
            CreditNoteCajaTable.update({ CreditNoteCajaTable.idCaja eq idCaja }) {
                it[notacreditoCorrelativo] = next
            }
        if (updated != 1) {
            throw CreditNoteValidationException("No se pudo avanzar el correlativo de nota de crédito")
        }
        return next
    }

    private fun buildCreditNoteCode(
        codigoCaja: String,
        nextCorrelative: Int,
    ): String = "${codigoCaja.takeIf { it.isNotBlank() } ?: "NC"}-${nextCorrelative.toString().padStart(5, '0')}"

    private fun validateCreateRequest(request: CreateCreditNoteRequest) {
        if (request.idCajaSecuencia.isBlank()) {
            throw CreditNoteValidationException("La nota de crédito requiere una caja secuencia activa")
        }
        if (request.settlementType == CreditNoteSettlementType.REINTEGRO && request.idFormaPagoReintegro == null) {
            throw CreditNoteValidationException("Debes indicar la forma de pago de reintegro")
        }
    }

    private fun reserveFiscalDocumentNumber(): String {
        val row =
            FECorrelativosTable
                .selectAll()
                .where { FECorrelativosTable.campo eq "numeroDocumentoFiscal" }
                .forUpdate()
                .singleOrNull()
                ?: throw CreditNoteValidationException("No existe correlativo fiscal para numeroDocumentoFiscal")

        val next = row[FECorrelativosTable.contador].toLong() + 1L
        if (next <= 0L || next > 9_999_999_999L) {
            throw CreditNoteValidationException("El correlativo fiscal excede el rango permitido")
        }

        val updated =
            FECorrelativosTable.update({ FECorrelativosTable.id eq row[FECorrelativosTable.id] }) {
                it[contador] = next.toInt()
            }
        if (updated != 1) {
            throw CreditNoteValidationException("No se pudo reservar el correlativo fiscal")
        }
        return next.toString().padStart(10, '0')
    }

    private fun normalizeFiscalDocumentNumber(value: String): String {
        val normalized = value.trim()
        if (normalized.isBlank() || !normalized.all(Char::isDigit) || normalized.length > 10) {
            throw CreditNoteValidationException("El número fiscal de la nota de crédito debe ser numérico de hasta 10 dígitos")
        }
        if (normalized.all { it == '0' }) {
            throw CreditNoteValidationException("El número fiscal de la nota de crédito no puede ser cero")
        }
        return normalized.padStart(10, '0')
    }

    private fun parsePacDate(value: String?): LocalDateTime? {
        val normalized = value?.trim().orEmpty()
        if (normalized.isBlank()) return null
        return runCatching { OffsetDateTime.parse(normalized).toLocalDateTime() }
            .recoverCatching { LocalDateTime.parse(normalized) }
            .recoverCatching { LocalDate.parse(normalized).atStartOfDay() }
            .getOrNull()
    }

    private fun insertCreditNoteDetails(
        creditNoteId: String,
        lines: List<ProcessedLine>,
    ) {
        lines.forEach { line ->
            CreditNoteDetailTable.insert {
                it[idDevolucionDetalle] = UUID.randomUUID().toString()
                it[idDevolucion] = creditNoteId
                it[idDetalleFactura] = line.sourceLine.idDetalleFactura
                it[idItem] = line.sourceLine.idItem
                it[itemAlmacen] = line.sourceLine.almacen
                it[itemCantidad] = line.quantity.setScale(3, RoundingMode.HALF_UP)
                it[itemPrecioSinIva] = line.sourceLine.precioSinIva
                it[itemDescuento] = line.sourceLine.descuentoPorcentaje
                it[itemMontoDescuento] = line.discountAmount
                it[itemPIva] = line.sourceLine.pIva
                it[itemTotalSinIva] = line.totalSinIva
                it[itemTotalConIva] = line.totalConIva
                it[codVendedor] = line.sourceLine.codVendedor
                it[itemCodigo] = line.sourceLine.codigo
                it[itemReferencia] = line.sourceLine.referencia
            }
        }
    }

    private fun loadPreparedLines(
        creditNoteId: String,
        invoiceLines: List<SourceInvoiceLine>,
    ): List<ProcessedLine> {
        val sourceLinesById = invoiceLines.associateBy { it.idDetalleFactura }
        return CreditNoteDetailTable
            .selectAll()
            .where { CreditNoteDetailTable.idDevolucion eq creditNoteId }
            .orderBy(CreditNoteDetailTable.idDevolucionDetalle)
            .map { row ->
                val sourceLine =
                    sourceLinesById[row[CreditNoteDetailTable.idDetalleFactura]]
                        ?: throw CreditNoteValidationException("La línea preparada ya no pertenece a la factura origen")
                ProcessedLine(
                    sourceLine = sourceLine,
                    quantity = row[CreditNoteDetailTable.itemCantidad],
                    discountAmount = row[CreditNoteDetailTable.itemMontoDescuento],
                    totalSinIva = row[CreditNoteDetailTable.itemTotalSinIva],
                    totalConIva = row[CreditNoteDetailTable.itemTotalConIva],
                    globalDiscountAmount = BigDecimal.ZERO.setScale(2),
                )
            }
    }

    private fun buildCreationResponse(
        id: String,
        success: Boolean,
        message: String? = null,
    ): CreateCreditNoteResponse {
        val detail =
            getCreditNoteDetail(id, "PA")
                ?: throw CreditNoteNotFoundException("Nota de crédito no encontrada")
        return CreateCreditNoteResponse(
            success = success,
            id = detail.id,
            codigo = detail.codigo,
            subtotal = detail.subtotal,
            impuesto = detail.impuesto,
            total = detail.total,
            fiscalStatus = detail.fiscalStatus,
            detail = detail,
            fiscalMessage = message?.take(MAX_PAC_DIAGNOSTIC_LENGTH),
        )
    }

    private fun resolveCreditNotePaymentFormId(): Int =
        CajaFormaPagoTable
            .selectAll()
            .where { CajaFormaPagoTable.activo eq 1 }
            .mapNotNull { row ->
                row[CajaFormaPagoTable.siglas]
                    ?.trim()
                    ?.takeIf { it.equals("NC", ignoreCase = true) }
                    ?.let { row[CajaFormaPagoTable.idFormaPago] }
            }.firstOrNull()
            ?: throw CreditNoteValidationException("No existe forma de pago activa para Nota de CrÃ©dito (NC)")

    private fun cancelInvoiceAndOriginalCash(
        countryCode: String,
        invoiceId: String,
        username: String,
        date: LocalDate,
        now: LocalDateTime,
    ) {
        CreditNoteFacturaTable.update({ CreditNoteFacturaTable.idFactura eq invoiceId }) {
            it[codEstatus] = 3
        }

        val cajaNuevaTable = SalesCajaNuevaTableFactory.forCountry(countryCode)
        val cajaNuevaDetalleTable = SalesCajaNuevaDetalleTableFactory.forCountry(countryCode)
        val originalCajas =
            cajaNuevaTable
                .selectAll()
                .where { cajaNuevaTable.idFactura eq invoiceId }
                .toList()

        originalCajas.forEach { cajaRow ->
            val cajaId = cajaRow[cajaNuevaTable.cajaId]
            cajaNuevaTable.update({ cajaNuevaTable.cajaId eq cajaId }) {
                it[status] = CajaStatus.Anulada
            }

            val reciboIds =
                cajaNuevaDetalleTable
                    .select(cajaNuevaDetalleTable.cajaReciboId)
                    .where { cajaNuevaDetalleTable.cajaId eq cajaId }
                    .map { it[cajaNuevaDetalleTable.cajaReciboId] }
                    .filter { it.isNotBlank() }

            if (reciboIds.isNotEmpty()) {
                val reciboTable = SalesCajaNuevaReciboTableFactory.forCountry(countryCode)
                reciboTable.update({ reciboTable.cajaReciboId inList reciboIds }) {
                    it[reciboTable.status] = "AN"
                    it[reciboTable.usuarioCreacion] = username.take(MAX_USERNAME_LENGTH)
                    it[reciboTable.fechaCreacion] = LocalDateTime.of(date, now.toLocalTime())
                }
            }
        }
    }

    private fun registerPartialCreditNoteOnOriginalCash(
        countryCode: String,
        invoice: InvoiceHeader,
        creditNoteId: String,
        creditNoteCode: String,
        total: BigDecimal,
        paymentFormId: Int,
        username: String,
        now: LocalDateTime,
    ) {
        val cajaNuevaTable = SalesCajaNuevaTableFactory.forCountry(countryCode)
        val cajaNuevaDetalleTable = SalesCajaNuevaDetalleTableFactory.forCountry(countryCode)
        val originalCaja =
            cajaNuevaTable
                .selectAll()
                .where { cajaNuevaTable.idFactura eq invoice.idFactura }
                .orderBy(cajaNuevaTable.fechaCreacion to SortOrder.DESC)
                .limit(1)
                .firstOrNull()
                ?: return

        val cajaId = originalCaja[cajaNuevaTable.cajaId]
        val cajaReciboId =
            cajaNuevaDetalleTable
                .select(cajaNuevaDetalleTable.cajaReciboId)
                .where { cajaNuevaDetalleTable.cajaId eq cajaId }
                .limit(1)
                .firstOrNull()
                ?.get(cajaNuevaDetalleTable.cajaReciboId)
                .orEmpty()

        cajaNuevaDetalleTable.insert {
            it[cajaDetalleId] = UUID.randomUUID().toString()
            it[this.cajaId] = cajaId
            it[idFormaPago] = paymentFormId
            it[idTransaccion] = originalCaja[cajaNuevaTable.idTransaccion]
            it[this.cajaReciboId] = cajaReciboId
            val reversalAmount = if (countryCode.equals("PA", ignoreCase = true)) -total else total
            it[monto] = reversalAmount
            it[montoOriginal] = reversalAmount
            it[concepto] = "Nota de credito $creditNoteCode"
            it[usuarioCreacion] = username.take(MAX_USERNAME_LENGTH)
            it[fechaCreacion] = now
            it[retencionTipo] = ""
            it[retencionPorcentaje] = ""
            it[numero] = ""
            it[observacion] = "Nota de credito aplicada sobre factura ${invoice.codFactura}"
            it[retencionBaseCalculo] = ""
            it[serieSucursal] = invoice.serieSucursal
            it[cajaSecuencia] = invoice.idCajaSecuencia
            it[numeroControl] = ""
            it[numeroComprobante] = creditNoteCode
            it[retencionMonto] = ""
            it[retencionDetalleJson] = ""
            if (cajaNuevaDetalleTable is SalesCajaNuevaDetalleTableVE) {
                it[cajaNuevaDetalleTable.montoRecibido] = total
                it[cajaNuevaDetalleTable.montoMonedaPrincipal] = total
            }
        }

        cajaNuevaTable.update({ cajaNuevaTable.cajaId eq cajaId }) {
            val current = originalCaja[cajaNuevaTable.monto] ?: BigDecimal.ZERO.setScale(2)
            it[monto] = current.subtract(total).coerceAtLeast(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP)
            it[idNotaCredito] = creditNoteId
        }
    }

    private fun restoreInventory(
        countryCode: String,
        invoice: InvoiceHeader,
        creditNoteId: String,
        creditNoteCode: String,
        lines: List<ProcessedLine>,
        username: String,
        date: LocalDate,
        now: LocalDateTime,
        idSucursal: Int,
    ) {
        if (lines.isEmpty()) return

        val kardexId = UUID.randomUUID().toString()
        val kardexTable = SalesKardexTableFactory.forCountry(countryCode)
        kardexTable.insert {
            it[kardexTable.idTransaccion] = kardexId
            it[kardexTable.tipoMovimientoAlmacen] = 14
            it[kardexTable.autorizadoPor] = username.take(MAX_USERNAME_LENGTH)
            it[kardexTable.observacion] = "Entrada por nota de crédito $creditNoteCode"
            it[kardexTable.fecha] = date
            it[kardexTable.usuarioCreacion] = username.take(MAX_USERNAME_LENGTH)
            it[kardexTable.fechaCreacion] = now
            it[kardexTable.estado] = "Procesado"
            it[kardexTable.idDocumento] = creditNoteId
            it[kardexTable.codProveedor] = 0
            it[kardexTable.comprobante] = creditNoteCode
            it[kardexTable.anio] = date.year
            it[kardexTable.tipoCosto] = "PEPS"
            it[kardexTable.estatus] = 1
            it[kardexTable.entregadoACodigo] = invoice.facturarARuc.take(10)
            it[kardexTable.entregadoANombre] = invoice.facturarA.take(30)
            it[kardexTable.codDocumento] = creditNoteCode
            it[kardexTable.subtipoMovimientoAlmacen] = 0
            it[kardexTable.contabilizado] = 0
            it[kardexTable.fechaContabilizacion] = date
            it[kardexTable.usuarioContabilizacion] = username.take(MAX_USERNAME_LENGTH)
            it[kardexTable.idAlmacenSalida] = lines.first().sourceLine.almacen
            it[kardexTable.idSucursal] = idSucursal
            it[kardexTable.validadoFecha] = date
            it[kardexTable.validadoUsuario] = username.take(MAX_USERNAME_LENGTH)
            it[kardexTable.validadoObservacion] = "Entrada por devolucion"
            if (kardexTable is SalesKardexTablePA) {
                it[kardexTable.controlaStock] = 0
            }
        }

        val kardexDetalleTable = SalesKardexDetalleTableFactory.forCountry(countryCode)
        lines.forEach { line ->
            val quantity = line.quantity.setScale(2, RoundingMode.HALF_UP)
            kardexDetalleTable.insert {
                it[kardexDetalleTable.idTransaccionDetalle] = UUID.randomUUID().toString()
                it[kardexDetalleTable.idTransaccion] = kardexId
                it[kardexDetalleTable.idAlmacenEntrada] = line.sourceLine.almacen
                it[kardexDetalleTable.idAlmacenSalida] = 0
                it[kardexDetalleTable.idItem] = line.sourceLine.idItem
                it[kardexDetalleTable.cantidad] = quantity.toFloat()
                it[kardexDetalleTable.cantidadDistribuida] = 0
                it[kardexDetalleTable.precio] = line.sourceLine.precioSinIva
                it[kardexDetalleTable.cantidadMuestra] = 0
                it[kardexDetalleTable.unidadBulto] = "UNIDAD"
                it[kardexDetalleTable.cantidadBulto] = BigDecimal.ONE.setScale(2)
                it[kardexDetalleTable.unidadEmpaque] = "UNIDAD"
                it[kardexDetalleTable.cantidadTotal] = quantity
                it[kardexDetalleTable.costo] = BigDecimal.ZERO.setScale(2)
                if (kardexDetalleTable is SalesKardexDetalleTablePA) {
                    it[kardexDetalleTable.idCentroCosto] = 0
                    it[kardexDetalleTable.idLoteItem] = 0
                }
            }

            val stockRow =
                ItemExistenciaAlmacenTable
                    .selectAll()
                    .where {
                        (ItemExistenciaAlmacenTable.idItem eq line.sourceLine.idItem) and
                            (ItemExistenciaAlmacenTable.codAlmacen eq line.sourceLine.almacen)
                    }.limit(1)
                    .firstOrNull()
            if (stockRow == null) {
                ItemExistenciaAlmacenTable.insertIgnore {
                    it[idItem] = line.sourceLine.idItem
                    it[codAlmacen] = line.sourceLine.almacen
                    it[cantidad] = quantity
                    it[cantidadMuestra] = BigDecimal.ZERO.setScale(4)
                    it[minimo] = BigDecimal.ZERO.setScale(4)
                    it[maximo] = BigDecimal.ZERO.setScale(4)
                }
            } else {
                val currentQuantity = stockRow[ItemExistenciaAlmacenTable.cantidad] ?: BigDecimal.ZERO.setScale(4)
                ItemExistenciaAlmacenTable.update({
                    (ItemExistenciaAlmacenTable.idItem eq line.sourceLine.idItem) and
                        (ItemExistenciaAlmacenTable.codAlmacen eq line.sourceLine.almacen)
                }) {
                    it[cantidad] = currentQuantity.add(quantity).setScale(4, RoundingMode.HALF_UP)
                }
            }

            restoreLotAvailability(line)
        }
    }

    private fun restoreLotAvailability(line: ProcessedLine) {
        var remaining = line.quantity.setScale(0, RoundingMode.DOWN).toInt()
        if (remaining <= 0) return

        val lotRows =
            FacturaDetalleProductoLoteTable
                .selectAll()
                .where { FacturaDetalleProductoLoteTable.idDetalleFactura eq line.sourceLine.idDetalleFactura }
                .orderBy(FacturaDetalleProductoLoteTable.id)
                .toList()

        lotRows.forEach { row ->
            if (remaining <= 0) return@forEach
            val restoreQty = minOf(remaining, row[FacturaDetalleProductoLoteTable.cantidad])
            if (restoreQty <= 0) return@forEach

            val lotId = row[FacturaDetalleProductoLoteTable.idLoteItem]
            val lotRow =
                ItemLoteTable
                    .selectAll()
                    .where { ItemLoteTable.idLoteItem eq lotId }
                    .limit(1)
                    .firstOrNull()
            if (lotRow != null) {
                val currentAvailability = lotRow[ItemLoteTable.disponibilidad]
                ItemLoteTable.update({ ItemLoteTable.idLoteItem eq lotId }) {
                    it[disponibilidad] = currentAvailability.add(BigDecimal.valueOf(restoreQty.toLong()).setScale(2))
                }
            }
            remaining -= restoreQty
        }
    }

    private fun registerRefundCashEgress(
        countryCode: String,
        invoice: InvoiceHeader,
        creditNoteId: String,
        creditNoteCode: String,
        total: BigDecimal,
        idFormaPago: Int,
        username: String,
        now: LocalDateTime,
        date: LocalDate,
        cajaContext: CajaContext,
    ) {
        val cajaId = UUID.randomUUID().toString()
        val transactionId = UUID.randomUUID().toString()
        val detalleId = UUID.randomUUID().toString()
        val concepto = "Reintegro por nota de crédito $creditNoteCode / factura ${invoice.codFactura}"

        val cajaNuevaTable = SalesCajaNuevaTableFactory.forCountry(countryCode)
        cajaNuevaTable.insert {
            it[this.cajaId] = cajaId
            it[idTransaccion] = transactionId
            it[fecha] = date
            it[ingEg] = CajaIngresoEgreso.E
            it[monto] = total
            it[comprobante] = "NC"
            it[comprobanteNumero] = creditNoteCode
            it[idFactura] = invoice.idFactura
            it[idCliente] = invoice.idCliente
            it[status] = CajaStatus.Pagada
            it[sucursalId] = cajaContext.idSucursal
            it[usuarioCreacion] = username.take(MAX_USERNAME_LENGTH)
            it[fechaCreacion] = now
            it[idCompra] = ""
            it[idProveedor] = ""
            it[cajaNuevaTable.concepto] = concepto
            it[idOrdenPago] = ""
            it[serieSucursal] = cajaContext.serieSucursal
            it[idCajaSecuencia] = invoice.idCajaSecuencia
            it[idPedido] = ""
            it[idAbono] = ""
            it[idNotaCredito] = creditNoteId
        }

        val cajaNuevaDetalleTable = SalesCajaNuevaDetalleTableFactory.forCountry(countryCode)
        cajaNuevaDetalleTable.insert {
            it[cajaDetalleId] = detalleId
            it[this.cajaId] = cajaId
            it[this.idFormaPago] = idFormaPago
            it[idTransaccion] = transactionId
            it[cajaReciboId] = ""
            it[monto] = total
            it[montoOriginal] = total
            it[cajaNuevaDetalleTable.concepto] = concepto
            it[usuarioCreacion] = username.take(MAX_USERNAME_LENGTH)
            it[fechaCreacion] = now
            it[retencionTipo] = ""
            it[retencionPorcentaje] = ""
            it[numero] = ""
            it[observacion] = ""
            it[retencionBaseCalculo] = ""
            it[serieSucursal] = cajaContext.serieSucursal
            it[cajaSecuencia] = cajaContext.cajaSecuencia
            it[numeroControl] = ""
            it[numeroComprobante] = creditNoteCode
            it[retencionMonto] = ""
            it[retencionDetalleJson] = ""
            if (cajaNuevaDetalleTable is SalesCajaNuevaDetalleTableVE) {
                it[cajaNuevaDetalleTable.montoRecibido] = total
                it[cajaNuevaDetalleTable.montoMonedaPrincipal] = total
            }
        }

        SalesCajaNuevaDetalleFormaPagoTable.insert {
            it[cajaDetalleFormaPagoId] = UUID.randomUUID().toString()
            it[this.cajaId] = cajaId
            it[this.cajaDetalleId] = detalleId
            it[tipoMovimiento] = "OT"
            it[this.idFormaPago] = idFormaPago
            it[comprobante] = "NC"
            it[SalesCajaNuevaDetalleFormaPagoTable.concepto] = concepto
            it[monto] = total
            it[montoOriginal] = total
            it[tdcProveedor] = ""
            it[tdcNumero] = ""
            it[tdcTitular] = ""
            it[tdcVencimiento] = ""
            it[tdcCvv] = ""
            it[codigoVerificacion] = ""
            it[idAbonoDetalle] = ""
            it[efectivoCambio] = BigDecimal.ZERO.setScale(2)
        }
    }

    private fun registerAbono(
        creditNoteId: String,
        total: BigDecimal,
        invoice: InvoiceHeader,
        client: ClientContext,
        username: String,
        now: LocalDateTime,
        cajaContext: CajaContext,
    ) {
        val current =
            CreditNoteCajaTable
                .select(CreditNoteCajaTable.abonoCorrelativo)
                .where { CreditNoteCajaTable.idCaja eq cajaContext.idCaja }
                .limit(1)
                .firstOrNull()
                ?.get(CreditNoteCajaTable.abonoCorrelativo)
                ?: 0
        val next = current + 1
        CreditNoteCajaTable.update({ CreditNoteCajaTable.idCaja eq cajaContext.idCaja }) {
            it[abonoCorrelativo] = next
        }

        CreditNoteAbonoTable.insert {
            it[idAbono] = UUID.randomUUID().toString()
            it[codAbono] = "AB-${cajaContext.codigoCaja}-${next.toString().padStart(5, '0')}"
            it[fecha] = now
            it[vencimiento] = 0
            it[fechaVencimiento] = now
            it[idVendedor] = invoice.codVendedor
            it[idCajero] = invoice.codVendedor
            it[idCliente] = client.idCliente
            it[idCajaSecuencia] = invoice.idCajaSecuencia
            it[monto] = total
            it[saldo] = total
            it[estatus] = 1
            it[descripcion] = "Abono generado por nota de crédito $creditNoteId"
            it[observacion] = ""
            it[tipo] = "nota_credito"
            it[idOperacion] = creditNoteId
            it[codigoReparacion] = ""
            it[fechaCreacion] = now
            it[usuarioCreacion] = username.take(MAX_USERNAME_LENGTH)
            it[fechaModificacion] = now
            it[usuarioModificacion] = username.take(MAX_USERNAME_LENGTH)
            it[fechaAnulacion] = now
            it[usuarioAnulacion] = username.take(MAX_USERNAME_LENGTH)
            it[idTransaccion] = creditNoteId
            it[serieSucursal] = cajaContext.serieSucursal
            it[idSucursal] = cajaContext.idSucursal
            it[idCaja] = cajaContext.idCaja
        }
    }

    private fun registerGiftCertificate(
        creditNoteId: String,
        total: BigDecimal,
        client: ClientContext,
        username: String,
        now: LocalDateTime,
        cajaContext: CajaContext,
    ) {
        val current =
            CreditNoteCajaTable
                .select(CreditNoteCajaTable.certificadoCorrelativo)
                .where { CreditNoteCajaTable.idCaja eq cajaContext.idCaja }
                .limit(1)
                .firstOrNull()
                ?.get(CreditNoteCajaTable.certificadoCorrelativo)
                ?: 0
        val next = current + 1
        CreditNoteCajaTable.update({ CreditNoteCajaTable.idCaja eq cajaContext.idCaja }) {
            it[certificadoCorrelativo] = next
        }

        CreditNoteGiftCertificateTable.insert {
            it[idCertificado] = UUID.randomUUID().toString()
            it[codigo] = "CG-${cajaContext.codigoCaja}-${next.toString().padStart(5, '0')}"
            it[monto] = total
            it[saldo] = total
            it[idCliente] = client.idCliente
            it[idCaja] = cajaContext.idCaja
            it[estatus] = 1
            it[usuarioCreacion] = username.take(MAX_USERNAME_LENGTH)
            it[fechaCreacion] = now
            it[idTransaccion] = creditNoteId
        }
    }

    private fun buildDetailResponse(
        header: CreditNoteHeaderContext,
        lines: List<CreditNoteDetailLine>,
    ): CreditNoteDetailResponse =
        CreditNoteDetailResponse(
            id = header.id,
            codigo = header.codigo,
            facturaId = header.facturaId,
            facturaCodigo = header.facturaCodigo,
            fecha = formatDate(header.fecha),
            periodo = header.periodo,
            observacion = header.observacion,
            clienteNombre = header.clienteNombre,
            clienteIdentificacion = header.clienteIdentificacion,
            subtotal = header.subtotal.toDouble(),
            impuesto = header.impuesto.toDouble(),
            total = header.total.toDouble(),
            fiscalStatus = header.fiscalStatus,
            fiscalNumber = header.fiscalNumber,
            printerSerial = header.printerSerial,
            anulaFacturaCompleta = header.anulaFacturaCompleta,
            lines = lines,
            fiscalDocument =
                CreditNoteFiscalDocument(
                    creditNoteId = header.id,
                    creditNoteCode = header.codigo,
                    date = formatDate(header.fecha),
                    customerName = header.clienteNombre,
                    customerIdentifier = header.clienteIdentificacion,
                    customerAddress = header.clienteDireccion,
                    customerPhone = header.clienteTelefono,
                    originalInvoiceCode = header.facturaCodigo,
                    originalFiscalNumber = header.originalFiscalNumber,
                    originalInvoiceDate = formatDate(header.originalInvoiceDate),
                    printerSerial = header.printerSerial,
                    comment = header.observacion,
                    lines =
                        lines.map { line ->
                            CreditNoteFiscalLine(
                                description = line.descripcion,
                                quantity = line.cantidad,
                                unitPriceWithoutTax = line.precioSinIva,
                                totalWithTax = line.totalConIva,
                                taxRate = line.pIva,
                            )
                        },
                ),
        )

    private fun mapSummaryRow(
        row: ResultRow,
        countryCode: String,
    ): CreditNoteSummary {
        val headerTable = CreditNoteHeaderTableFactory.forCountry(countryCode)
        val clienteNombre =
            listOf(row[ClientsTable.nombre], row[ClientsTable.apellido].orEmpty())
                .filter { it.isNotBlank() }
                .joinToString(" ")
                .ifBlank { "CONSUMIDOR FINAL" }
        val fiscalNumber =
            resolveDisplayFiscalNumber(
                codDevolucionFiscal = row[headerTable.codDevolucionFiscal].orEmpty(),
                numeroDocumentoFiscal = row[headerTable.numeroDocumentoFiscal].orEmpty(),
            )

        return CreditNoteSummary(
            id = row[headerTable.idDevolucion],
            codigo = row[headerTable.codDevolucion],
            facturaId = row[headerTable.codFactura],
            facturaCodigo = row[CreditNoteFacturaTable.codFactura],
            fecha = formatDate(row[headerTable.fechaDevolucion]),
            fechaCreacion = formatDateTime(row[headerTable.fechaCreacion]),
            clienteNombre = clienteNombre,
            clienteIdentificacion = row[ClientsTable.rif],
            total = row[headerTable.total].toDouble(),
            subtotal = row[headerTable.subtotal].toDouble(),
            impuesto = row[headerTable.impuesto].toDouble(),
            fiscalStatus =
                resolveFiscalStatus(
                    codDevolucionFiscal = row[headerTable.codDevolucionFiscal].orEmpty(),
                    numeroDocumentoFiscal = row[headerTable.numeroDocumentoFiscal].orEmpty(),
                ),
            fiscalNumber = fiscalNumber,
            printerSerial = if (headerTable is CreditNoteHeaderTableVE) row[headerTable.impresoraSerial].orEmpty() else "",
            observacion = row[headerTable.observacion].orEmpty(),
        )
    }

    private fun mapHeaderContext(
        row: ResultRow,
        countryCode: String,
    ): CreditNoteHeaderContext {
        val headerTable = CreditNoteHeaderTableFactory.forCountry(countryCode)
        val clienteNombre =
            listOf(row[ClientsTable.nombre], row[ClientsTable.apellido].orEmpty())
                .filter { it.isNotBlank() }
                .joinToString(" ")
                .ifBlank { "CONSUMIDOR FINAL" }

        val codDevolucionFiscal = row[headerTable.codDevolucionFiscal].orEmpty()
        val numeroDocumentoFiscal = row[headerTable.numeroDocumentoFiscal].orEmpty()

        return CreditNoteHeaderContext(
            id = row[headerTable.idDevolucion],
            codigo = row[headerTable.codDevolucion],
            facturaId = row[headerTable.codFactura],
            facturaCodigo = row[CreditNoteFacturaTable.codFactura],
            fecha = row[headerTable.fechaDevolucion],
            fechaCreacion = row[headerTable.fechaCreacion] ?: BusinessClock.nowForCountry(countryCode),
            periodo = row[headerTable.periodoDevolucion].orEmpty(),
            observacion = row[headerTable.observacion].orEmpty(),
            clienteNombre = clienteNombre,
            clienteIdentificacion = row[ClientsTable.rif],
            clienteDireccion = row[CreditNoteFacturaTable.facturarADireccion],
            clienteTelefono = row[CreditNoteFacturaTable.facturarATelefono],
            subtotal = row[headerTable.subtotal],
            impuesto = row[headerTable.impuesto],
            total = row[headerTable.total],
            fiscalStatus = resolveFiscalStatus(codDevolucionFiscal, numeroDocumentoFiscal),
            fiscalNumber = resolveDisplayFiscalNumber(codDevolucionFiscal, numeroDocumentoFiscal),
            printerSerial = if (headerTable is CreditNoteHeaderTableVE) row[headerTable.impresoraSerial].orEmpty() else "",
            originalFiscalNumber =
                row[CreditNoteFacturaTable.numeroDocumentoFiscal].orEmpty().ifBlank {
                    row[CreditNoteFacturaTable.codFacturaFiscal].orEmpty()
                },
            originalInvoiceDate = row[CreditNoteFacturaTable.fechaFactura],
            anulaFacturaCompleta = row[headerTable.total].compareTo(row[CreditNoteFacturaTable.totalTotalFactura]) == 0,
        )
    }

    private fun mapDetailLine(row: ResultRow): CreditNoteDetailLine =
        CreditNoteDetailLine(
            id = row[CreditNoteDetailTable.idDevolucionDetalle],
            idDetalleFactura = row[CreditNoteDetailTable.idDetalleFactura],
            idItem = row[CreditNoteDetailTable.idItem],
            descripcion = row[CreditNoteFacturaDetalleTable.itemDescripcion],
            codigo = row[CreditNoteDetailTable.itemCodigo].orEmpty(),
            referencia = row[CreditNoteDetailTable.itemReferencia].orEmpty(),
            cantidad = row[CreditNoteDetailTable.itemCantidad].toDouble(),
            precioSinIva = row[CreditNoteDetailTable.itemPrecioSinIva].toDouble(),
            descuentoPorcentaje = row[CreditNoteDetailTable.itemDescuento].toDouble(),
            descuentoMonto = row[CreditNoteDetailTable.itemMontoDescuento].toDouble(),
            pIva = row[CreditNoteDetailTable.itemPIva].toDouble(),
            totalSinIva = row[CreditNoteDetailTable.itemTotalSinIva].toDouble(),
            totalConIva = row[CreditNoteDetailTable.itemTotalConIva].toDouble(),
        )

    private fun resolveFiscalStatus(
        codDevolucionFiscal: String,
        numeroDocumentoFiscal: String,
    ): CreditNoteFiscalStatus =
        when (codDevolucionFiscal.trim().uppercase()) {
            PENDING_FISCAL_CODE -> CreditNoteFiscalStatus.PENDIENTE
            UNCERTAIN_FISCAL_CODE -> CreditNoteFiscalStatus.INCIERTA
            REJECTED_FISCAL_CODE -> CreditNoteFiscalStatus.RECHAZADA
            CONFIRMED_FISCAL_CODE -> CreditNoteFiscalStatus.CONFIRMADA
            else -> {
                val hasFiscalCode = isValidFiscalValue(codDevolucionFiscal)
                val hasDocumentNumber = isValidFiscalValue(numeroDocumentoFiscal)
                if (hasFiscalCode || hasDocumentNumber) CreditNoteFiscalStatus.CONFIRMADA else CreditNoteFiscalStatus.PENDIENTE
            }
        }

    private fun fiscalStatusCode(status: CreditNoteFiscalStatus): String =
        when (status) {
            CreditNoteFiscalStatus.PENDIENTE -> PENDING_FISCAL_CODE
            CreditNoteFiscalStatus.INCIERTA -> UNCERTAIN_FISCAL_CODE
            CreditNoteFiscalStatus.RECHAZADA -> REJECTED_FISCAL_CODE
            CreditNoteFiscalStatus.CONFIRMADA -> CONFIRMED_FISCAL_CODE
        }

    private fun resolveDisplayFiscalNumber(
        codDevolucionFiscal: String,
        numeroDocumentoFiscal: String,
    ): String =
        listOf(codDevolucionFiscal, numeroDocumentoFiscal)
            .map(String::trim)
            .firstOrNull(::isValidFiscalValue)
            .orEmpty()

    private fun isValidFiscalValue(value: String): Boolean {
        val normalized = value.trim()
        if (normalized.isBlank()) return false
        if (normalized == PENDING_FISCAL_CODE) return false
        val digits = normalized.filter(Char::isDigit)
        return digits.isNotEmpty() && digits.any { it != '0' }
    }

    private fun parseDate(value: String): LocalDate =
        runCatching { LocalDate.parse(value) }
            .getOrElse { throw CreditNoteValidationException("Fecha inválida, usa formato yyyy-MM-dd") }

    private fun formatDate(value: LocalDate?): String {
        if (value == null) return ""
        return value.format(DATE_FORMATTER)
    }

    private fun formatDateTime(value: LocalDateTime?): String {
        if (value == null) return ""
        return value.format(DATE_TIME_FORMATTER)
    }

    private fun divideSafe(
        value: BigDecimal,
        divisor: BigDecimal,
        scale: Int,
    ): BigDecimal {
        if (divisor.compareTo(BigDecimal.ZERO) == 0) return BigDecimal.ZERO.setScale(scale, RoundingMode.HALF_UP)
        return value.divide(divisor, scale, RoundingMode.HALF_UP)
    }

    private fun ProcessedLine.toDetailLine(): CreditNoteDetailLine =
        CreditNoteDetailLine(
            id = "",
            idDetalleFactura = sourceLine.idDetalleFactura,
            idItem = sourceLine.idItem,
            descripcion = sourceLine.descripcion,
            codigo = sourceLine.codigo,
            referencia = sourceLine.referencia,
            cantidad = quantity.toDouble(),
            precioSinIva = sourceLine.precioSinIva.toDouble(),
            descuentoPorcentaje = sourceLine.descuentoPorcentaje.toDouble(),
            descuentoMonto = discountAmount.toDouble(),
            pIva = sourceLine.pIva.toDouble(),
            totalSinIva = totalSinIva.toDouble(),
            totalConIva = totalConIva.toDouble(),
        )

    private fun SourceInvoiceLine.toResponseLine(): CreditNoteSourceInvoiceLine =
        CreditNoteSourceInvoiceLine(
            idDetalleFactura = idDetalleFactura,
            idItem = idItem,
            descripcion = descripcion,
            codigo = codigo,
            referencia = referencia,
            cantidadOriginal = quantityOriginal.toDouble(),
            cantidadDevuelta = returnedQuantity.toDouble(),
            cantidadDisponible = availableQuantity.toDouble(),
            precioSinIva = precioSinIva.toDouble(),
            descuentoPorcentaje = descuentoPorcentaje.toDouble(),
            descuentoMontoTotal = descuentoMontoTotal.toDouble(),
            pIva = pIva.toDouble(),
            totalSinIvaOriginal = totalSinIvaOriginal.toDouble(),
            totalConIvaOriginal = totalConIvaOriginal.toDouble(),
            totalSinIvaDisponible = availableTotalSinIva.toDouble(),
            totalConIvaDisponible = availableTotalConIva.toDouble(),
            almacen = almacen,
        )

    private data class RequestedLine(
        val idDetalleFactura: String,
        val cantidad: Double,
    )

    private data class CreditNoteTotals(
        val subtotal: BigDecimal,
        val tax: BigDecimal,
        val total: BigDecimal,
    )

    private data class ClientContext(
        val idCliente: String,
        val codigoCliente: String,
        val nombreCompleto: String,
        val identificacion: String,
        val direccion: String,
        val telefono: String,
    )

    private data class InvoiceHeader(
        val idFactura: String,
        val codFactura: String,
        val codFacturaFiscal: String,
        val numeroDocumentoFiscal: String,
        val idCliente: String,
        val codVendedor: Int,
        val codEstatus: Int,
        val fechaFactura: LocalDate?,
        val subtotal: BigDecimal,
        val totalizarSubTotal: BigDecimal,
        val totalizarTotalOperacion: BigDecimal,
        val totalizarPDescuentoGlobal: BigDecimal,
        val totalizarDescuentoGlobal: BigDecimal,
        val totalizarBaseImponible: BigDecimal,
        val totalizarMontoIva: BigDecimal,
        val totalizarTotalGeneral: BigDecimal,
        val totalTotalFactura: BigDecimal,
        val formaPago: String,
        val idCajaSecuencia: String,
        val idCaja: String,
        val idSucursal: Int,
        val serieSucursal: String,
        val codigoCaja: String,
        val facturarA: String,
        val facturarARuc: String,
        val facturarADireccion: String,
        val facturarATelefono: String,
        val moneda: String,
        val tasa: BigDecimal?,
        val totalRef: BigDecimal?,
    )

    private data class SourceInvoiceLine(
        val idDetalleFactura: String,
        val idItem: Int,
        val descripcion: String,
        val codigo: String,
        val referencia: String,
        val quantityOriginal: BigDecimal,
        val returnedQuantity: BigDecimal,
        val availableQuantity: BigDecimal,
        val precioSinIva: BigDecimal,
        val descuentoPorcentaje: BigDecimal,
        val descuentoMontoTotal: BigDecimal,
        val pIva: BigDecimal,
        val totalSinIvaOriginal: BigDecimal,
        val totalConIvaOriginal: BigDecimal,
        val availableTotalSinIva: BigDecimal,
        val availableTotalConIva: BigDecimal,
        val unitDiscountAmount: BigDecimal,
        val almacen: Int,
        val codVendedor: Int,
    )

    private data class ProcessedLine(
        val sourceLine: SourceInvoiceLine,
        val quantity: BigDecimal,
        val discountAmount: BigDecimal,
        val totalSinIva: BigDecimal,
        val totalConIva: BigDecimal,
        val globalDiscountAmount: BigDecimal,
    )

    private data class PreviousCreditNoteTotals(
        val subtotal: BigDecimal = BigDecimal.ZERO.setScale(2),
        val tax: BigDecimal = BigDecimal.ZERO.setScale(2),
        val total: BigDecimal = BigDecimal.ZERO.setScale(2),
        val globalDiscount: BigDecimal = BigDecimal.ZERO.setScale(2),
    )

    private data class CreditNoteFinancials(
        val lines: List<ProcessedLine>,
        val totals: CreditNoteTotals,
        val globalDiscount: BigDecimal,
    )

    private data class CajaContext(
        val idCaja: String,
        val idSucursal: Int,
        val codigoCaja: String,
        val serieSucursal: String,
        val cajaSecuencia: String,
        val impresoraModelo: String,
    )

    private data class CreditNoteHeaderContext(
        val id: String,
        val codigo: String,
        val facturaId: String,
        val facturaCodigo: String,
        val fecha: LocalDate?,
        val fechaCreacion: LocalDateTime,
        val periodo: String,
        val observacion: String,
        val clienteNombre: String,
        val clienteIdentificacion: String,
        val clienteDireccion: String,
        val clienteTelefono: String,
        val subtotal: BigDecimal,
        val impuesto: BigDecimal,
        val total: BigDecimal,
        val fiscalStatus: CreditNoteFiscalStatus,
        val fiscalNumber: String,
        val printerSerial: String,
        val originalFiscalNumber: String,
        val originalInvoiceDate: LocalDate?,
        val anulaFacturaCompleta: Boolean,
    )

    private companion object {
        const val MAX_OBSERVATION_LENGTH = 300
        const val MAX_PERIOD_LENGTH = 20
        const val MAX_USERNAME_LENGTH = 50
        const val PENDING_FISCAL_CODE = "00000000"
        const val UNCERTAIN_FISCAL_CODE = "INCIERTA"
        const val REJECTED_FISCAL_CODE = "RECHAZADA"
        const val CONFIRMED_FISCAL_CODE = "CONFIRMADA"
        const val MAX_PAC_DIAGNOSTIC_LENGTH = 5000
        val DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ISO_DATE
        val DATE_TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    }
}

private fun BigDecimal.coerceAtLeastZero(scale: Int): BigDecimal =
    if (this < BigDecimal.ZERO) BigDecimal.ZERO.setScale(scale, RoundingMode.HALF_UP) else setScale(scale, RoundingMode.HALF_UP)

private fun BigDecimal.isEffectivelyZero(): Boolean =
    setScale(3, RoundingMode.HALF_UP).compareTo(BigDecimal.ZERO.setScale(3, RoundingMode.HALF_UP)) == 0
