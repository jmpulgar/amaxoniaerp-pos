package com.amaxoniaerp.features.creditnotes.application

import com.amaxoniaerp.core.database.dbQuery
import com.amaxoniaerp.features.creditnotes.data.CreditNoteRepository
import com.amaxoniaerp.features.creditnotes.domain.ConfirmCreditNoteFiscalRequest
import com.amaxoniaerp.features.creditnotes.domain.ConfirmCreditNoteFiscalResponse
import com.amaxoniaerp.features.creditnotes.domain.CreateCreditNoteRequest
import com.amaxoniaerp.features.creditnotes.domain.CreateCreditNoteResponse
import com.amaxoniaerp.features.creditnotes.domain.CreditNoteDetailResponse
import com.amaxoniaerp.features.creditnotes.domain.CreditNoteSourceInvoiceDetailResponse
import com.amaxoniaerp.features.creditnotes.domain.CreditNoteSourceInvoiceListResponse
import com.amaxoniaerp.features.creditnotes.domain.CreditNotesListResponse
import org.jetbrains.exposed.sql.Database
import java.time.LocalDate

class CreditNoteService(
    private val repository: CreditNoteRepository,
) {
    suspend fun list(
        database: Database,
        limit: Int,
        offset: Long,
        search: String?,
        fechaInicio: LocalDate?,
        fechaFin: LocalDate?,
    ): CreditNotesListResponse = dbQuery(database) {
        val (data, total) = repository.listCreditNotes(limit, offset, search, fechaInicio, fechaFin)
        CreditNotesListResponse(data = data, total = total)
    }

    suspend fun listEligibleInvoices(
        database: Database,
        limit: Int,
        offset: Long,
        search: String?,
    ): CreditNoteSourceInvoiceListResponse = dbQuery(database) {
        val (data, total) = repository.listEligibleInvoices(limit, offset, search)
        CreditNoteSourceInvoiceListResponse(data = data, total = total)
    }

    suspend fun getDetail(database: Database, id: String, countryCode: String): CreditNoteDetailResponse? = dbQuery(database) {
        repository.getCreditNoteDetail(id, countryCode)
    }

    suspend fun getInvoiceDetail(database: Database, id: String): CreditNoteSourceInvoiceDetailResponse? = dbQuery(database) {
        repository.getSourceInvoiceDetail(id)
    }

    suspend fun create(
        database: Database,
        countryCode: String,
        request: CreateCreditNoteRequest,
        username: String,
    ): CreateCreditNoteResponse = dbQuery(database) {
        repository.create(countryCode, request, username)
    }

    suspend fun confirmFiscal(
        database: Database,
        id: String,
        request: ConfirmCreditNoteFiscalRequest,
    ): ConfirmCreditNoteFiscalResponse = dbQuery(database) {
        repository.confirmFiscal(id, request)
    }
}
