package com.amaxonia.pos.data.repository

import com.amaxonia.pos.core.result.catchingResult
import com.amaxonia.pos.data.local.AppJson
import com.amaxonia.pos.data.local.LocalStore
import com.amaxonia.pos.data.local.readCompanySession
import com.amaxonia.pos.data.remote.ApiClient
import com.amaxonia.pos.domain.model.cobranza.CobranzaPendientesResponseDto
import com.amaxonia.pos.domain.model.cobranza.CreateCobranzaRequestDto
import com.amaxonia.pos.domain.model.cobranza.CreateCobranzaResponseDto
import com.amaxonia.pos.domain.model.pedidos.CreatePedidoRequestDto
import com.amaxonia.pos.domain.model.pedidos.CreatePedidoResponseDto
import com.amaxonia.pos.domain.model.pedidos.PedidoDetailDto
import com.amaxonia.pos.domain.model.pedidos.PedidoDetailResponseDto
import com.amaxonia.pos.domain.model.pedidos.PedidoListResponseDto
import com.amaxonia.pos.domain.model.pedidos.PedidoSummaryDto
import com.amaxonia.pos.domain.repository.CobranzaRepository
import com.amaxonia.pos.domain.repository.PedidoRepository
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.encodeURLPathPart
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Cliente HTTP de endpoints POS con cabeceras de tenant (`Authorization` + `Company-DB`) y
 * mapeo uniforme de errores `{"error": "..."}` del backend.
 */
class TenantJsonClient(
    private val apiClient: ApiClient,
    private val localStore: LocalStore,
) {
    suspend fun <T> get(
        path: String,
        serializer: KSerializer<T>,
        fallbackMessage: String,
        query: Map<String, String?> = emptyMap(),
    ): Result<T> =
        execute(serializer, fallbackMessage) { session ->
            apiClient.httpClient.get(path) {
                tenantHeaders(session)
                query.forEach { (key, value) -> if (!value.isNullOrBlank()) parameter(key, value) }
            }
        }

    suspend fun <T> post(
        path: String,
        payload: Any,
        serializer: KSerializer<T>,
        fallbackMessage: String,
    ): Result<T> =
        execute(serializer, fallbackMessage) { session ->
            apiClient.httpClient.post(path) {
                tenantHeaders(session)
                contentType(ContentType.Application.Json)
                setBody(payload)
            }
        }

    private suspend fun <T> execute(
        serializer: KSerializer<T>,
        fallbackMessage: String,
        call: suspend (TenantSession) -> HttpResponse,
    ): Result<T> =
        catchingResult {
            val session =
                localStore.readCompanySession()
                    ?: error("No autorizado: primero selecciona una empresa")
            val response = call(TenantSession("Bearer ${session.token}", session.company.adminDb))
            Result.success(parse(response.bodyAsText(), serializer, fallbackMessage))
        }

    private fun HttpRequestBuilder.tenantHeaders(session: TenantSession) {
        header("Authorization", session.authHeader)
        header("Company-DB", session.companyDb)
    }

    private fun <T> parse(
        text: String,
        serializer: KSerializer<T>,
        fallbackMessage: String,
    ): T {
        val json = runCatching { AppJson.decodeFromString(JsonElement.serializer(), text) }.getOrNull()
        val error = (json as? JsonObject)?.get("error")?.jsonPrimitive?.contentOrNull
        if (!error.isNullOrBlank()) error(error)
        return runCatching { AppJson.decodeFromString(serializer, text) }.getOrElse { error(fallbackMessage) }
    }

    data class TenantSession(
        val authHeader: String,
        val companyDb: String,
    )
}

class PedidoRepositoryImpl(
    private val client: TenantJsonClient,
) : PedidoRepository {
    override suspend fun create(request: CreatePedidoRequestDto): Result<CreatePedidoResponseDto> =
        client.post(PATH, request, CreatePedidoResponseDto.serializer(), "No se pudo guardar el pedido")

    override suspend fun pendientes(search: String?): Result<List<PedidoSummaryDto>> =
        client
            .get(
                PATH,
                PedidoListResponseDto.serializer(),
                "No se pudieron cargar los pedidos",
                mapOf("estatus" to "1", "search" to search),
            ).map { it.data }

    override suspend fun detail(idPedido: String): Result<PedidoDetailDto> =
        client
            .get(
                "$PATH/${idPedido.encodeURLPathPart()}",
                PedidoDetailResponseDto.serializer(),
                "No se pudo cargar el pedido",
            ).map { it.data }

    private companion object {
        const val PATH = "api/pos/pedidos"
    }
}

class CobranzaRepositoryImpl(
    private val client: TenantJsonClient,
) : CobranzaRepository {
    override suspend fun pendientes(idCliente: String): Result<CobranzaPendientesResponseDto> =
        client.get(
            "$PATH/clientes/${idCliente.encodeURLPathPart()}/pendientes",
            CobranzaPendientesResponseDto.serializer(),
            "No se pudieron cargar las facturas pendientes",
        )

    override suspend fun cobrar(request: CreateCobranzaRequestDto): Result<CreateCobranzaResponseDto> =
        client.post(PATH, request, CreateCobranzaResponseDto.serializer(), "No se pudo registrar el cobro")

    private companion object {
        const val PATH = "api/pos/cobranzas"
    }
}
