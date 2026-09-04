package com.amaxoniaerp.features.creditnotes.data

import com.amaxoniaerp.core.time.BusinessClock
import com.amaxoniaerp.features.creditnotes.domain.CreateCreditNoteRequest
import com.amaxoniaerp.features.creditnotes.domain.CreateCreditNoteResponse
import com.amaxoniaerp.features.creditnotes.domain.CreditNoteFiscalStatus
import com.amaxoniaerp.features.creditnotes.domain.CreditNoteNotFoundException
import com.amaxoniaerp.features.creditnotes.domain.CreditNoteValidationException
import com.amaxoniaerp.features.creditnotes.domain.PreparedCreditNote
import com.amaxoniaerp.features.electronicinvoice.domain.PacResponse
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import java.util.UUID

/**
 * Reserva e inserta una NC PA sin aplicar todavía efectos comerciales.
 * La fila PENDIENTE y sus líneas son la reserva de cantidades frente a otra
 * preparación concurrente.
 */
fun CreditNoteRepository.preparePanama(
    request: CreateCreditNoteRequest,
    username: String,
): PreparedCreditNote {
    val ctx = resolveCreationContext("PA", request)

    // Valida la configuración necesaria antes de reservar la NC.
    if (!ctx.allReturnedAfterOperation) resolveCreditNotePaymentFormId()

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
fun CreditNoteRepository.finalizePanamaAccepted(
    id: String,
    request: CreateCreditNoteRequest,
    pacResponse: PacResponse,
    numeroDocumentoFiscal: String,
): CreateCreditNoteResponse {
    val headerRow = lockHeaderOrThrow(id)
    val currentStatus =
        resolvePanamaFiscalStatus(
            cufe = headerRow[CreditNoteHeaderTablePA.cufe].orEmpty(),
            estadoDevolucion = headerRow[CreditNoteHeaderTablePA.estadoDevolucion],
        )
    val earlyResponse =
        when (currentStatus) {
            CreditNoteFiscalStatus.CONFIRMADA -> buildCreationResponse(id, true)
            CreditNoteFiscalStatus.RECHAZADA ->
                buildCreationResponse(id, false, "La nota de crédito ya fue rechazada")
            else -> null
        }
    if (earlyResponse != null) return earlyResponse

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

private fun CreditNoteRepository.resolveFinalizeContext(
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
    val cufe = pacResponse.cufe?.trim().orEmpty()

    val error =
        when {
            normalizedDocumentNumber != reservedDocumentNumber ->
                "El número fiscal no coincide con la reserva de la nota de crédito"
            cufe.isBlank() -> "El PAC aceptó la NC sin CUFE"
            else -> null
        }
    if (error != null) throw CreditNoteValidationException(error)

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
                partialPaymentFormId = if (allReturnedAfterOperation) null else resolveCreditNotePaymentFormId(),
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
    val dgiDate = parsePacDate(pacResponse.fechaRecepcionDGI) ?: BusinessClock.nowForCountry("PA")
    val limitDate = parsePacDate(pacResponse.fechaLimite) ?: BusinessClock.nowForCountry("PA")

    header.update({ header.idDevolucion eq id }) {
        it[header.numeroDocumentoFiscal] = normalizedDocumentNumber
        it[header.cufe] = cufe
        it[header.qr] = pacResponse.qr.orEmpty()
        it[header.nroProtocoloAutorizacion] = pacResponse.nroProtocoloAutorizacion.orEmpty()
        it[header.fechaRecepcionDGI] = dgiDate
        it[header.fechaLimite] = limitDate
        it[header.estadoDevolucion] = "PROCESADA"
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

fun CreditNoteRepository.markPanamaFiscalStatus(
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
        resolvePanamaFiscalStatus(
            cufe = row[header.cufe].orEmpty(),
            estadoDevolucion = row[header.estadoDevolucion],
        )
    if (currentStatus != CreditNoteFiscalStatus.CONFIRMADA) {
        header.update({ header.idDevolucion eq id }) {
            it[estadoDevolucion] = status.name
            it[informacionInteres] = message.take(MAX_PAC_DIAGNOSTIC_LENGTH)
        }
    }
    return buildCreationResponse(
        id = id,
        success = currentStatus == CreditNoteFiscalStatus.CONFIRMADA,
        message = message,
    )
}

fun CreditNoteRepository.recordPanamaDiagnostic(
    id: String,
    message: String,
): CreateCreditNoteResponse {
    val header = CreditNoteHeaderTablePA
    val row = lockHeaderOrThrow(id)
    val currentStatus =
        resolvePanamaFiscalStatus(
            cufe = row[header.cufe].orEmpty(),
            estadoDevolucion = row[header.estadoDevolucion],
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

private fun CreditNoteRepository.buildCreationResponse(
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
