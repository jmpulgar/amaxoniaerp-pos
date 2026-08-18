package com.amaxoniaerp.features.creditnotes.data

import com.amaxoniaerp.core.time.BusinessClock
import com.amaxoniaerp.features.clients.data.ClientsTable
import com.amaxoniaerp.features.creditnotes.domain.ConfirmCreditNoteFiscalRequest
import com.amaxoniaerp.features.creditnotes.domain.ConfirmCreditNoteFiscalResponse
import com.amaxoniaerp.features.creditnotes.domain.CreateCreditNoteRequest
import com.amaxoniaerp.features.creditnotes.domain.CreateCreditNoteResponse
import com.amaxoniaerp.features.creditnotes.domain.CreditNoteDetailResponse
import com.amaxoniaerp.features.creditnotes.domain.CreditNoteFiscalStatus
import com.amaxoniaerp.features.creditnotes.domain.CreditNoteNotFoundException
import com.amaxoniaerp.features.creditnotes.domain.CreditNoteSourceInvoiceSummary
import com.amaxoniaerp.features.creditnotes.domain.CreditNoteSummary
import com.amaxoniaerp.features.creditnotes.domain.CreditNoteValidationException
import com.amaxoniaerp.features.creditnotes.domain.PreparedCreditNote
import com.amaxoniaerp.features.electronicinvoice.domain.PacResponse
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greater
import org.jetbrains.exposed.sql.SqlExpressionBuilder.like
import org.jetbrains.exposed.sql.SqlExpressionBuilder.neq
import org.jetbrains.exposed.sql.andWhere
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.UUID

/**
 * Repositorio de notas de crédito. La resolución de contexto vive en
 * CreditNoteInvoiceLoader.kt, los cálculos financieros en
 * CreditNoteFinancialCalc.kt, los efectos de caja/inventario en
 * CreditNoteCajaEffects.kt, el workflow de creación en
 * CreditNoteCreationWorkflow.kt y los mappers en CreditNoteMappers.kt.
 */
class CreditNoteRepository {
    fun listCreditNotes(
        countryCode: String,
        query: CreditNoteListQuery,
    ): Pair<List<CreditNoteSummary>, Long> {
        val headerTable = CreditNoteHeaderTableFactory.forCountry(countryCode)
        val select =
            headerTable
                .join(ClientsTable, JoinType.LEFT, headerTable.idCliente, ClientsTable.idCliente)
                .join(CreditNoteFacturaTable, JoinType.LEFT, headerTable.codFactura, CreditNoteFacturaTable.idFactura)
                .selectAll()

        if (query.fechaInicio != null && query.fechaFin != null) {
            select.andWhere { headerTable.fechaDevolucion.between(query.fechaInicio, query.fechaFin) }
        }

        if (!query.search.isNullOrBlank()) {
            val term = "%${query.search}%"
            select.andWhere {
                (headerTable.codDevolucion like term) or
                    (CreditNoteFacturaTable.codFactura like term) or
                    (ClientsTable.nombre like term) or
                    (ClientsTable.apellido like term) or
                    (ClientsTable.rif like term)
            }
        }

        val total = select.count()
        val data =
            select
                .orderBy(headerTable.fechaCreacion to SortOrder.DESC)
                .limit(query.limit)
                .offset(query.offset)
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
            query.andWhere { CreditNoteFacturaTable.codEstatus neq ANNULLED_INVOICE_STATUS }
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
    ): com.amaxoniaerp.features.creditnotes.domain.CreditNoteSourceInvoiceDetailResponse? =
        buildSourceInvoiceDetail(invoiceId, countryCode)

    /**
     * Reserva e inserta una NC PA sin aplicar todavía efectos comerciales.
     * La fila PENDIENTE y sus líneas son la reserva de cantidades frente a otra
     * preparación concurrente.
     */
    fun preparePanama(
        request: CreateCreditNoteRequest,
        username: String,
    ): PreparedCreditNote {
        val ctx = resolveCreationContext("PA", request)

        // Valida la configuración necesaria antes de reservar la NC.
        val partialPaymentFormId = if (!ctx.allReturnedAfterOperation) resolveCreditNotePaymentFormId() else null

        val financials =
            calculateFinancials(
                invoice = ctx.invoice,
                invoiceLines = ctx.invoiceLines,
                processedLines = ctx.baseProcessedLines,
                previousTotals = loadPreviousCreditNoteTotals("PA", ctx.invoice.idFactura),
                allReturnedAfterOperation = ctx.allReturnedAfterOperation,
            )
        val (creditNoteDate, now) = resolveCreationTiming("PA", request)
        val cajaContext = resolveCajaContext(request.idCajaSecuencia)
        val nextCorrelative = advanceCreditNoteCorrelative(cajaContext.idCaja)
        val numeroDocumentoFiscal = reserveFiscalDocumentNumber()
        val creditNoteId = UUID.randomUUID().toString()
        val creditNoteCode = buildCreditNoteCode(cajaContext.codigoCaja, nextCorrelative)

        insertPreparedHeaderPA(
            InsertPreparedHeaderPAInput(
                request = request,
                username = username,
                ctx = ctx,
                financials = financials,
                creditNoteId = creditNoteId,
                creditNoteCode = creditNoteCode,
                creditNoteDate = creditNoteDate,
                now = now,
                cajaContext = cajaContext,
                numeroDocumentoFiscal = numeroDocumentoFiscal,
            ),
        )

        insertCreditNoteDetails(creditNoteId, financials.lines, annulFullyReturnedLines = false)

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
        val headerRow = lockHeaderOrThrow(id)
        val currentStatus =
            resolveFiscalStatus(
                headerRow[CreditNoteHeaderTablePA.codDevolucionFiscal].orEmpty(),
                headerRow[CreditNoteHeaderTablePA.numeroDocumentoFiscal].orEmpty(),
            )
        if (currentStatus == CreditNoteFiscalStatus.CONFIRMADA) {
            return buildCreationResponse(id, true)
        }
        if (currentStatus == CreditNoteFiscalStatus.RECHAZADA) {
            return buildCreationResponse(id, false, "La nota de crédito ya fue rechazada")
        }

        val finalizeCtx =
            resolveFinalizeContext(
                id = id,
                request = request,
                headerRow = headerRow,
                pacResponse = pacResponse,
                numeroDocumentoFiscal = numeroDocumentoFiscal,
            )

        persistAcceptedHeader(id, pacResponse, finalizeCtx.normalizedDocumentNumber, finalizeCtx.cufe)
        annulFullyReturnedPreparedLines(finalizeCtx.processedLines)
        applyCreditNoteEffects(finalizeCtx.effectsInput)

        return buildCreationResponse(id, true)
    }

    private fun lockHeaderOrThrow(id: String): ResultRow {
        val header = CreditNoteHeaderTablePA
        return header
            .selectAll()
            .where { header.idDevolucion eq id }
            .forUpdate()
            .singleOrNull()
            ?: throw CreditNoteNotFoundException("Nota de crédito no encontrada")
    }

    private class FinalizeContext(
        val normalizedDocumentNumber: String,
        val cufe: String,
        val processedLines: List<ProcessedLine>,
        val effectsInput: ApplyCreditNoteEffectsInput,
    )

    private fun resolveFinalizeContext(
        id: String,
        request: CreateCreditNoteRequest,
        headerRow: ResultRow,
        pacResponse: PacResponse,
        numeroDocumentoFiscal: String,
    ): FinalizeContext {
        val header = CreditNoteHeaderTablePA
        val invoiceId = headerRow[header.codFactura]
        lockInvoiceOrThrow(invoiceId)
        val invoice = requireInvoiceHeader(invoiceId)
        val client = loadClient(invoice.idCliente)
        val invoiceLines = loadInvoiceLines("PA", invoice.idFactura)
        val processedLines = loadPreparedLines(id, invoiceLines)
        if (processedLines.isEmpty()) {
            throw CreditNoteValidationException("La nota de crédito no tiene líneas preparadas")
        }
        val allReturnedAfterOperation = invoiceLines.all { it.availableQuantity.isEffectivelyZero() }
        val now = BusinessClock.nowForCountry("PA")
        val creditNoteDate = headerRow[header.fechaDevolucion]
        val cajaContext =
            resolveCajaContext(headerRow[header.idCajaSecuencia] ?: request.idCajaSecuencia)
        val username = headerRow[header.usuarioCreacion]

        val reservedDocumentNumber =
            normalizeFiscalDocumentNumber(headerRow[header.numeroDocumentoFiscal].orEmpty())
        val normalizedDocumentNumber = normalizeFiscalDocumentNumber(numeroDocumentoFiscal)
        if (normalizedDocumentNumber != reservedDocumentNumber) {
            throw CreditNoteValidationException("El número fiscal no coincide con la reserva de la nota de crédito")
        }
        val cufe = pacResponse.cufe?.trim().orEmpty()
        if (cufe.isBlank()) {
            throw CreditNoteValidationException("El PAC aceptó la NC sin CUFE")
        }

        return FinalizeContext(
            normalizedDocumentNumber = normalizedDocumentNumber,
            cufe = cufe,
            processedLines = processedLines,
            effectsInput =
                ApplyCreditNoteEffectsInput(
                    countryCode = "PA",
                    request = request,
                    invoice = invoice,
                    client = client,
                    creditNoteId = id,
                    creditNoteCode = headerRow[header.codDevolucion],
                    total = headerRow[header.total],
                    lines = processedLines,
                    username = username,
                    now = now,
                    creditNoteDate = creditNoteDate,
                    cajaContext = cajaContext,
                    allReturnedAfterOperation = allReturnedAfterOperation,
                    annulInvoiceOnPartial = true,
                    partialPaymentFormId = null,
                ),
        )
    }

    private fun persistAcceptedHeader(
        id: String,
        pacResponse: PacResponse,
        normalizedDocumentNumber: String,
        cufe: String,
    ) {
        val header = CreditNoteHeaderTablePA
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
    }

    private fun annulFullyReturnedPreparedLines(processedLines: List<ProcessedLine>) {
        processedLines.forEach { line ->
            if (line.sourceLine.availableQuantity.isEffectivelyZero()) {
                CreditNoteFacturaDetalleTable.update(
                    { CreditNoteFacturaDetalleTable.idDetalleFactura eq line.sourceLine.idDetalleFactura },
                ) {
                    it[anulado] = true
                }
            }
        }
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
        val row = lockHeaderOrThrow(id)
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
        val row = lockHeaderOrThrow(id)
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
        val ctx = resolveCreationContext(countryCode, request)
        val isPA = countryCode.equals("PA", ignoreCase = true)

        val financials =
            calculateFinancials(
                invoice = ctx.invoice,
                invoiceLines = ctx.invoiceLines,
                processedLines = ctx.baseProcessedLines,
                previousTotals = loadPreviousCreditNoteTotals(countryCode, ctx.invoice.idFactura),
                allReturnedAfterOperation = ctx.allReturnedAfterOperation,
            )
        val processedLines = financials.lines
        val partialPaymentFormId =
            if (ctx.allReturnedAfterOperation) {
                null
            } else {
                resolveCreditNotePaymentFormId()
            }
        val (creditNoteDate, now) = resolveCreationTiming(countryCode, request)
        val cajaContext = resolveCajaContext(request.idCajaSecuencia)
        val nextCorrelative = advanceCreditNoteCorrelative(cajaContext.idCaja)
        val creditNoteId = UUID.randomUUID().toString()
        val creditNoteCode = buildCreditNoteCode(cajaContext.codigoCaja, nextCorrelative)
        val totals = financials.totals

        insertCreditNoteHeader(
            InsertCreditNoteHeaderInput(
                countryCode = countryCode,
                request = request,
                username = username,
                ctx = ctx,
                financials = financials,
                creditNoteId = creditNoteId,
                creditNoteCode = creditNoteCode,
                creditNoteDate = creditNoteDate,
                now = now,
                cajaContext = cajaContext,
            ),
        )

        insertCreditNoteDetails(creditNoteId, processedLines, annulFullyReturnedLines = true)

        applyCreditNoteEffects(
            ApplyCreditNoteEffectsInput(
                countryCode = countryCode,
                request = request,
                invoice = ctx.invoice,
                client = ctx.client,
                creditNoteId = creditNoteId,
                creditNoteCode = creditNoteCode,
                total = totals.total,
                lines = processedLines,
                username = username,
                now = now,
                creditNoteDate = creditNoteDate,
                cajaContext = cajaContext,
                allReturnedAfterOperation = ctx.allReturnedAfterOperation,
                annulInvoiceOnPartial = isPA,
                partialPaymentFormId = partialPaymentFormId,
            ),
        )

        return buildPendingCreationResponse(
            PendingResponseInput(
                request = request,
                ctx = ctx,
                financials = financials,
                creditNoteId = creditNoteId,
                creditNoteCode = creditNoteCode,
                now = now,
                creditNoteDate = creditNoteDate,
            ),
        )
    }

    private data class PendingResponseInput(
        val request: CreateCreditNoteRequest,
        val ctx: CreditNoteCreationContext,
        val financials: CreditNoteFinancials,
        val creditNoteId: String,
        val creditNoteCode: String,
        val now: LocalDateTime,
        val creditNoteDate: java.time.LocalDate,
    )

    private fun buildPendingCreationResponse(input: PendingResponseInput): CreateCreditNoteResponse {
        val request = input.request
        val ctx = input.ctx
        val totals = input.financials.totals
        val processedLines = input.financials.lines
        val responseHeader =
            CreditNoteHeaderContext(
                id = input.creditNoteId,
                codigo = input.creditNoteCode,
                facturaId = ctx.invoice.idFactura,
                facturaCodigo = ctx.invoice.codFactura,
                fecha = input.creditNoteDate,
                fechaCreacion = input.now,
                periodo = request.periodo,
                observacion = request.observacion,
                clienteNombre = ctx.client.nombreCompleto,
                clienteIdentificacion = ctx.client.identificacion,
                clienteDireccion = ctx.client.direccion,
                clienteTelefono = ctx.client.telefono,
                subtotal = totals.subtotal,
                impuesto = totals.tax,
                total = totals.total,
                fiscalStatus = CreditNoteFiscalStatus.PENDIENTE,
                fiscalNumber = "",
                printerSerial = "",
                originalFiscalNumber =
                    request.numeroFiscalElectronico.ifBlank {
                        ctx.invoice.numeroDocumentoFiscal.ifBlank { ctx.invoice.codFacturaFiscal }
                    },
                originalInvoiceDate = ctx.invoice.fechaFactura,
                anulaFacturaCompleta = ctx.allReturnedAfterOperation,
            )
        val responseLines = processedLines.map { it.toDetailLine() }
        val detail = buildDetailResponse(responseHeader, responseLines)

        return CreateCreditNoteResponse(
            success = true,
            id = input.creditNoteId,
            codigo = input.creditNoteCode,
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
}
