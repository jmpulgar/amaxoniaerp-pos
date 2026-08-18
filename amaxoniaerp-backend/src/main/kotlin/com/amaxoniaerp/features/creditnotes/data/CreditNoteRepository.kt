package com.amaxoniaerp.features.creditnotes.data

import com.amaxoniaerp.features.creditnotes.domain.ConfirmCreditNoteFiscalRequest
import com.amaxoniaerp.features.creditnotes.domain.ConfirmCreditNoteFiscalResponse
import com.amaxoniaerp.features.creditnotes.domain.CreateCreditNoteRequest
import com.amaxoniaerp.features.creditnotes.domain.CreateCreditNoteResponse
import com.amaxoniaerp.features.creditnotes.domain.CreditNoteFiscalStatus
import com.amaxoniaerp.features.creditnotes.domain.CreditNoteNotFoundException
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import java.time.LocalDateTime
import java.util.UUID

/**
 * Repositorio de notas de crédito. La resolución de contexto vive en
 * CreditNoteInvoiceLoader.kt, los cálculos financieros en
 * CreditNoteFinancialCalc.kt, los efectos de caja/inventario en
 * CreditNoteCajaEffects.kt, el workflow de creación en
 * CreditNoteCreationWorkflow.kt, las consultas en CreditNoteQueries.kt, el
 * workflow PA en CreditNotePanamaWorkflow.kt y los mappers en
 * CreditNoteMappers.kt.
 */
class CreditNoteRepository {
    fun create(
        countryCode: String,
        request: CreateCreditNoteRequest,
        username: String,
    ): CreateCreditNoteResponse {
        val ctx = resolveCreationContext(countryCode, request)
        val plan = resolveCreationPlan(countryCode, request, ctx)
        val processedLines = plan.financials.lines

        insertCreditNoteHeader(
            InsertCreditNoteHeaderInput(
                countryCode = countryCode,
                request = request,
                username = username,
                ctx = ctx,
                financials = plan.financials,
                creditNoteId = plan.creditNoteId,
                creditNoteCode = plan.creditNoteCode,
                creditNoteDate = plan.timing.creditNoteDate,
                now = plan.timing.now,
                cajaContext = plan.cajaContext,
            ),
        )

        insertCreditNoteDetails(plan.creditNoteId, processedLines, annulFullyReturnedLines = true)

        applyCreditNoteEffects(
            ApplyCreditNoteEffectsInput(
                countryCode = countryCode,
                request = request,
                invoice = ctx.invoice,
                client = ctx.client,
                creditNoteId = plan.creditNoteId,
                creditNoteCode = plan.creditNoteCode,
                total = plan.totals.total,
                lines = processedLines,
                username = username,
                now = plan.timing.now,
                creditNoteDate = plan.timing.creditNoteDate,
                cajaContext = plan.cajaContext,
                allReturnedAfterOperation = ctx.allReturnedAfterOperation,
                annulInvoiceOnPartial = countryCode.equals("PA", ignoreCase = true),
                partialPaymentFormId = plan.partialPaymentFormId,
            ),
        )

        return buildPendingCreationResponse(
            PendingResponseInput(
                request = request,
                ctx = ctx,
                financials = plan.financials,
                creditNoteId = plan.creditNoteId,
                creditNoteCode = plan.creditNoteCode,
                now = plan.timing.now,
                creditNoteDate = plan.timing.creditNoteDate,
            ),
        )
    }

    private class CreationTiming(
        val creditNoteDate: java.time.LocalDate,
        val now: LocalDateTime,
    )

    private class CreationPlan(
        val financials: CreditNoteFinancials,
        val timing: CreationTiming,
        val cajaContext: CajaContext,
        val partialPaymentFormId: Int?,
        val creditNoteId: String,
        nextCorrelative: Int,
    ) {
        val totals = financials.totals

        val creditNoteCode = buildCreditNoteCode(cajaContext.codigoCaja, nextCorrelative)
    }

    private fun resolveCreationPlan(
        countryCode: String,
        request: CreateCreditNoteRequest,
        ctx: CreditNoteCreationContext,
    ): CreationPlan {
        val financials =
            calculateFinancials(
                invoice = ctx.invoice,
                invoiceLines = ctx.invoiceLines,
                processedLines = ctx.baseProcessedLines,
                previousTotals = loadPreviousCreditNoteTotals(countryCode, ctx.invoice.idFactura),
                allReturnedAfterOperation = ctx.allReturnedAfterOperation,
            )
        val partialPaymentFormId =
            if (ctx.allReturnedAfterOperation) {
                null
            } else {
                resolveCreditNotePaymentFormId()
            }
        val (creditNoteDate, now) = resolveCreationTiming(countryCode, request)
        val timing = CreationTiming(creditNoteDate, now)
        val cajaContext = resolveCajaContext(request.idCajaSecuencia)
        val nextCorrelative = advanceCreditNoteCorrelative(cajaContext.idCaja)

        return CreationPlan(
            financials = financials,
            timing = timing,
            cajaContext = cajaContext,
            partialPaymentFormId = partialPaymentFormId,
            creditNoteId = UUID.randomUUID().toString(),
            nextCorrelative = nextCorrelative,
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
}
