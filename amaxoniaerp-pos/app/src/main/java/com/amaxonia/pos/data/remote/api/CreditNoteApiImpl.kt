package com.amaxonia.pos.data.remote.api

import com.amaxonia.pos.data.local.AppJson
import com.amaxonia.pos.data.remote.ApiClient
import com.amaxonia.pos.domain.model.creditnote.ConfirmCreditNoteFiscalRequestDto
import com.amaxonia.pos.domain.model.creditnote.ConfirmCreditNoteFiscalResponseDto
import com.amaxonia.pos.domain.model.creditnote.CreateCreditNoteRequestDto
import com.amaxonia.pos.domain.model.creditnote.CreateCreditNoteResponseDto
import com.amaxonia.pos.domain.model.creditnote.CreditNoteDetailDto
import com.amaxonia.pos.domain.model.creditnote.CreditNoteSourceInvoiceDetailDto
import com.amaxonia.pos.domain.model.creditnote.CreditNoteSourceInvoiceListResponseDto
import com.amaxonia.pos.domain.model.creditnote.CreditNotesListResponseDto
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

class CreditNoteApiImpl(
    private val apiClient: ApiClient,
) : CreditNoteApi {

    override suspend fun getCreditNotes(authHeader: String, companyDb: String, search: String?): Result<CreditNotesListResponseDto> {
        return getRequest(
            path = "api/pos/notas-credito",
            authHeader = authHeader,
            companyDb = companyDb,
            serializer = CreditNotesListResponseDto.serializer(),
            search = search,
            fallbackMessage = "No se pudieron cargar las notas de crédito",
        )
    }

    override suspend fun getCreditNoteDetail(authHeader: String, companyDb: String, id: String): Result<CreditNoteDetailDto> {
        return getRequest(
            path = "api/pos/notas-credito/$id",
            authHeader = authHeader,
            companyDb = companyDb,
            serializer = CreditNoteDetailDto.serializer(),
            fallbackMessage = "No se pudo cargar la nota de crédito",
        )
    }

    override suspend fun getSourceInvoices(authHeader: String, companyDb: String, search: String?): Result<CreditNoteSourceInvoiceListResponseDto> {
        return getRequest(
            path = "api/pos/notas-credito/facturas",
            authHeader = authHeader,
            companyDb = companyDb,
            serializer = CreditNoteSourceInvoiceListResponseDto.serializer(),
            search = search,
            fallbackMessage = "No se pudieron cargar las facturas elegibles",
        )
    }

    override suspend fun getSourceInvoiceDetail(authHeader: String, companyDb: String, id: String): Result<CreditNoteSourceInvoiceDetailDto> {
        return getRequest(
            path = "api/pos/notas-credito/facturas/$id",
            authHeader = authHeader,
            companyDb = companyDb,
            serializer = CreditNoteSourceInvoiceDetailDto.serializer(),
            fallbackMessage = "No se pudo cargar la factura seleccionada",
        )
    }

    override suspend fun createCreditNote(
        authHeader: String,
        companyDb: String,
        payload: CreateCreditNoteRequestDto,
    ): Result<CreateCreditNoteResponseDto> {
        return postRequest(
            path = "api/pos/notas-credito",
            authHeader = authHeader,
            companyDb = companyDb,
            payload = payload,
            serializer = CreateCreditNoteResponseDto.serializer(),
            fallbackMessage = "No se pudo crear la nota de crédito",
        )
    }

    override suspend fun confirmFiscal(
        authHeader: String,
        companyDb: String,
        id: String,
        payload: ConfirmCreditNoteFiscalRequestDto,
    ): Result<ConfirmCreditNoteFiscalResponseDto> {
        return postRequest(
            path = "api/pos/notas-credito/$id/confirmacion-fiscal",
            authHeader = authHeader,
            companyDb = companyDb,
            payload = payload,
            serializer = ConfirmCreditNoteFiscalResponseDto.serializer(),
            fallbackMessage = "No se pudo confirmar la nota de crédito fiscal",
        )
    }

    private suspend fun <T> getRequest(
        path: String,
        authHeader: String,
        companyDb: String,
        serializer: KSerializer<T>,
        search: String? = null,
        fallbackMessage: String,
    ): Result<T> {
        return try {
            val response = apiClient.httpClient.get(path) {
                header("Authorization", authHeader)
                header("Company-DB", companyDb)
                if (!search.isNullOrBlank()) {
                    parameter("search", search)
                }
            }
            Result.success(parseResponse(response.bodyAsText(), serializer, fallbackMessage))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun <T> postRequest(
        path: String,
        authHeader: String,
        companyDb: String,
        payload: Any,
        serializer: KSerializer<T>,
        fallbackMessage: String,
    ): Result<T> {
        return try {
            val response = apiClient.httpClient.post(path) {
                header("Authorization", authHeader)
                header("Company-DB", companyDb)
                contentType(ContentType.Application.Json)
                setBody(payload)
            }
            Result.success(parseResponse(response.bodyAsText(), serializer, fallbackMessage))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun <T> parseResponse(responseText: String, serializer: KSerializer<T>, fallbackMessage: String): T {
        return runCatching {
            AppJson.decodeFromString(serializer, responseText)
        }.getOrElse {
            val json = runCatching { AppJson.decodeFromString(JsonElement.serializer(), responseText) }.getOrNull()
            if (json is JsonObject) {
                val error = json["error"]?.jsonPrimitive?.contentOrNull
                if (!error.isNullOrBlank()) {
                    throw IllegalStateException(error)
                }
            }
            throw IllegalStateException(fallbackMessage)
        }
    }
}
