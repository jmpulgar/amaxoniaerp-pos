package com.amaxonia.pos.data.repository

import com.amaxonia.pos.data.local.LocalStore
import com.amaxonia.pos.data.remote.api.CreditNoteApi
import com.amaxonia.pos.domain.model.creditnote.ConfirmCreditNoteFiscalRequestDto
import com.amaxonia.pos.domain.model.creditnote.ConfirmCreditNoteFiscalResponseDto
import com.amaxonia.pos.domain.model.creditnote.CreateCreditNoteRequestDto
import com.amaxonia.pos.domain.model.creditnote.CreateCreditNoteResponseDto
import com.amaxonia.pos.domain.model.creditnote.CreditNoteDetailDto
import com.amaxonia.pos.domain.model.creditnote.CreditNoteSourceInvoiceDetailDto
import com.amaxonia.pos.domain.model.creditnote.CreditNoteSourceInvoiceListResponseDto
import com.amaxonia.pos.domain.model.creditnote.CreditNotesListResponseDto
import com.amaxonia.pos.domain.repository.CreditNoteRepository

class CreditNoteRepositoryImpl(
    private val api: CreditNoteApi,
    private val localStore: LocalStore,
) : CreditNoteRepository {

    override suspend fun getCreditNotes(search: String?): Result<CreditNotesListResponseDto> {
        return runRequest { authHeader, companyDb ->
            api.getCreditNotes(authHeader = authHeader, companyDb = companyDb, search = search)
        }
    }

    override suspend fun getCreditNoteDetail(id: String): Result<CreditNoteDetailDto> {
        return runRequest { authHeader, companyDb ->
            api.getCreditNoteDetail(authHeader = authHeader, companyDb = companyDb, id = id)
        }
    }

    override suspend fun getSourceInvoices(search: String?): Result<CreditNoteSourceInvoiceListResponseDto> {
        return runRequest { authHeader, companyDb ->
            api.getSourceInvoices(authHeader = authHeader, companyDb = companyDb, search = search)
        }
    }

    override suspend fun getSourceInvoiceDetail(id: String): Result<CreditNoteSourceInvoiceDetailDto> {
        return runRequest { authHeader, companyDb ->
            api.getSourceInvoiceDetail(authHeader = authHeader, companyDb = companyDb, id = id)
        }
    }

    override suspend fun createCreditNote(payload: CreateCreditNoteRequestDto): Result<CreateCreditNoteResponseDto> {
        return runRequest { authHeader, companyDb ->
            api.createCreditNote(authHeader = authHeader, companyDb = companyDb, payload = payload)
        }
    }

    override suspend fun confirmFiscal(id: String, payload: ConfirmCreditNoteFiscalRequestDto): Result<ConfirmCreditNoteFiscalResponseDto> {
        return runRequest { authHeader, companyDb ->
            api.confirmFiscal(authHeader = authHeader, companyDb = companyDb, id = id, payload = payload)
        }
    }

    private suspend fun <T> runRequest(block: suspend (String, String) -> Result<T>): Result<T> {
        return try {
            val session = localStore.readCompanySession()
                ?: throw IllegalStateException("No autorizado: primero selecciona una empresa")
            val token = session.token
            val companyDb = session.company.adminDb
            block("Bearer $token", companyDb)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
