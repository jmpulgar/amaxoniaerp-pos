package com.amaxoniaerp.features.creditnotes.application

import com.amaxoniaerp.core.database.dbQuery
import com.amaxoniaerp.features.creditnotes.data.CreditNoteListQuery
import com.amaxoniaerp.features.creditnotes.data.CreditNoteRepository
import com.amaxoniaerp.features.creditnotes.data.finalizePanamaAccepted
import com.amaxoniaerp.features.creditnotes.data.getCreditNoteDetail
import com.amaxoniaerp.features.creditnotes.data.getSourceInvoiceDetail
import com.amaxoniaerp.features.creditnotes.data.listCreditNotes
import com.amaxoniaerp.features.creditnotes.data.listEligibleInvoices
import com.amaxoniaerp.features.creditnotes.data.markPanamaFiscalStatus
import com.amaxoniaerp.features.creditnotes.data.preparePanama
import com.amaxoniaerp.features.creditnotes.data.recordPanamaDiagnostic
import com.amaxoniaerp.features.creditnotes.domain.ConfirmCreditNoteFiscalRequest
import com.amaxoniaerp.features.creditnotes.domain.ConfirmCreditNoteFiscalResponse
import com.amaxoniaerp.features.creditnotes.domain.CreateCreditNoteRequest
import com.amaxoniaerp.features.creditnotes.domain.CreateCreditNoteResponse
import com.amaxoniaerp.features.creditnotes.domain.CreditNoteDetailResponse
import com.amaxoniaerp.features.creditnotes.domain.CreditNoteFiscalStatus
import com.amaxoniaerp.features.creditnotes.domain.CreditNoteSourceInvoiceDetailResponse
import com.amaxoniaerp.features.creditnotes.domain.CreditNoteSourceInvoiceListResponse
import com.amaxoniaerp.features.creditnotes.domain.CreditNoteValidationException
import com.amaxoniaerp.features.creditnotes.domain.CreditNotesListResponse
import com.amaxoniaerp.features.creditnotes.domain.PreparedCreditNote
import org.jetbrains.exposed.sql.Database
import org.slf4j.LoggerFactory
import java.time.LocalDate

class CreditNoteService(
    private val repository: CreditNoteRepository,
    private val panamaCreditNoteProcessor: PanamaCreditNoteProcessor? = null,
) {
    private val logger = LoggerFactory.getLogger(CreditNoteService::class.java)

    suspend fun list(
        database: Database,
        countryCode: String,
        query: CreditNoteListQuery,
    ): CreditNotesListResponse =
        dbQuery(database) {
            val (data, total) = repository.listCreditNotes(countryCode, query)
            CreditNotesListResponse(data = data, total = total)
        }

    suspend fun listEligibleInvoices(
        database: Database,
        countryCode: String,
        limit: Int,
        offset: Long,
        search: String?,
        fechaInicio: LocalDate? = null,
        fechaFin: LocalDate? = null,
    ): CreditNoteSourceInvoiceListResponse =
        dbQuery(database) {
            val (data, total) =
                repository.listEligibleInvoices(
                    countryCode,
                    limit,
                    offset,
                    search,
                    fechaInicio,
                    fechaFin,
                )
            CreditNoteSourceInvoiceListResponse(data = data, total = total)
        }

    suspend fun getDetail(
        database: Database,
        id: String,
        countryCode: String,
    ): CreditNoteDetailResponse? =
        dbQuery(database) {
            repository.getCreditNoteDetail(id, countryCode)
        }

    suspend fun getInvoiceDetail(
        database: Database,
        id: String,
        countryCode: String,
    ): CreditNoteSourceInvoiceDetailResponse? =
        dbQuery(database) {
            repository.getSourceInvoiceDetail(id, countryCode)
        }

    suspend fun create(
        database: Database,
        countryCode: String,
        request: CreateCreditNoteRequest,
        username: String,
        companyDb: String? = null,
    ): CreateCreditNoteResponse {
        if (!countryCode.equals("PA", ignoreCase = true)) {
            return dbQuery(database) {
                repository.create(countryCode, request, username)
            }
        }

        val processor =
            panamaCreditNoteProcessor
                ?: throw CreditNoteValidationException("No existe procesador PAC para notas de crédito PA")
        val prepared =
            dbQuery(database) {
                repository.preparePanama(request, username)
            }

        return when (val result = processor.process(database, prepared, companyDb)) {
            is PanamaCreditNotePacResult.Accepted -> finalizeAccepted(database, prepared, request, result)
            is PanamaCreditNotePacResult.Rejected ->
                dbQuery(database) {
                    repository.markPanamaFiscalStatus(
                        id = prepared.id,
                        status = CreditNoteFiscalStatus.RECHAZADA,
                        message = "${result.codigo}: ${result.mensaje}",
                    )
                }

            is PanamaCreditNotePacResult.Uncertain ->
                dbQuery(database) {
                    repository.markPanamaFiscalStatus(
                        id = prepared.id,
                        status = CreditNoteFiscalStatus.INCIERTA,
                        message = "${result.codigo}: ${result.mensaje}",
                    )
                }
        }
    }

    private suspend fun finalizeAccepted(
        database: Database,
        prepared: PreparedCreditNote,
        request: CreateCreditNoteRequest,
        result: PanamaCreditNotePacResult.Accepted,
    ): CreateCreditNoteResponse =
        runCatching {
            val response =
                dbQuery(database) {
                    repository.finalizePanamaAccepted(
                        id = prepared.id,
                        request = request,
                        pacResponse = result.response,
                        numeroDocumentoFiscal = prepared.numeroDocumentoFiscal,
                    )
                }
            appendPdfDiagnostic(database, prepared.id, response, result.pdfDiagnostic)
        }.getOrElse { e ->
            if (e is Error) throw e
            logger.error("PAC aceptó NC PA {}, pero falló la persistencia local", prepared.id, e)
            val diagnostic =
                buildString {
                    append("PAC aceptó la NC, pero falló la persistencia local: ")
                    append(e.message)
                    result.pdfDiagnostic?.let {
                        append(". Diagnóstico PDF: ")
                        append(it)
                    }
                }
            dbQuery(database) {
                repository.markPanamaFiscalStatus(
                    id = prepared.id,
                    status = CreditNoteFiscalStatus.INCIERTA,
                    message = diagnostic,
                )
            }
        }

    private suspend fun appendPdfDiagnostic(
        database: Database,
        creditNoteId: String,
        response: CreateCreditNoteResponse,
        diagnostic: String?,
    ): CreateCreditNoteResponse {
        if (diagnostic == null) {
            return response
        }
        runCatching {
            dbQuery(database) {
                repository.recordPanamaDiagnostic(creditNoteId, diagnostic)
            }
        }.onFailure { error ->
            logger.error(
                "NC PA {} confirmada, pero no se pudo guardar el diagnóstico del PDF",
                creditNoteId,
                error,
            )
        }
        return response.copy(fiscalMessage = diagnostic)
    }

    suspend fun confirmFiscal(
        database: Database,
        countryCode: String,
        id: String,
        request: ConfirmCreditNoteFiscalRequest,
    ): ConfirmCreditNoteFiscalResponse =
        dbQuery(database) {
            repository.confirmFiscal(countryCode, id, request)
        }
}
