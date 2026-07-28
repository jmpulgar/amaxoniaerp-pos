package com.amaxonia.pos.data.remote.api

import com.amaxonia.pos.core.result.catchingResult
import com.amaxonia.pos.data.local.AppJson
import com.amaxonia.pos.data.remote.ApiClient
import com.amaxonia.pos.domain.model.mesas.CambiarEstadoPedidoRequest
import com.amaxonia.pos.domain.model.mesas.CrearPedidoMesaRequest
import com.amaxonia.pos.domain.model.mesas.EnviarComandaRequest
import com.amaxonia.pos.domain.model.mesas.EnviarComandaResponse
import com.amaxonia.pos.domain.model.mesas.PedidoMesaActualizadoResponse
import com.amaxonia.pos.domain.model.mesas.PedidoMesaCreadoResponse
import com.amaxonia.pos.domain.model.mesas.PedidosMesaListResponse
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Implementación Ktor de [PedidosMesaApi]. Replica el patrón de [SesionMesaApiImpl]: parsea el
 * body, traduce `{"error": "..."}` del backend a excepción con ese mensaje y solo devuelve
 * `Result.success` cuando el HTTP fue exitoso.
 */
class PedidosMesaApiImpl(
    private val apiClient: ApiClient,
) : PedidosMesaApi {
    private fun base(
        areaId: Int,
        mesaId: Int,
        sesionId: Int,
    ) = "api/pos/areas/$areaId/mesas/$mesaId/sesiones/$sesionId/pedidos"

    override suspend fun listar(
        cajaId: String,
        areaId: Int,
        mesaId: Int,
        sesionId: Int,
        estado: String?,
        authHeader: String,
    ): Result<PedidosMesaListResponse> =
        catchingResult {
            val response =
                apiClient.httpClient.get(base(areaId, mesaId, sesionId)) {
                    header("Authorization", authHeader)
                    parameter("cajaId", cajaId)
                    if (!estado.isNullOrBlank()) parameter("estado", estado)
                }
            Result.success(
                response.parseOrThrow(
                    PedidosMesaListResponse.serializer(),
                    "No se pudieron consultar los pedidos",
                ),
            )
        }

    override suspend fun crear(
        cajaId: String,
        areaId: Int,
        mesaId: Int,
        sesionId: Int,
        request: CrearPedidoMesaRequest,
        authHeader: String,
    ): Result<PedidoMesaCreadoResponse> =
        catchingResult {
            val response =
                apiClient.httpClient.post(base(areaId, mesaId, sesionId)) {
                    header("Authorization", authHeader)
                    parameter("cajaId", cajaId)
                    contentType(ContentType.Application.Json)
                    setBody(request)
                }
            Result.success(
                response.parseOrThrow(
                    PedidoMesaCreadoResponse.serializer(),
                    "No se pudieron crear los pedidos",
                ),
            )
        }

    override suspend fun enviarComanda(
        cajaId: String,
        areaId: Int,
        mesaId: Int,
        sesionId: Int,
        request: EnviarComandaRequest,
        authHeader: String,
    ): Result<EnviarComandaResponse> =
        catchingResult {
            val response =
                apiClient.httpClient.post("${base(areaId, mesaId, sesionId)}/enviar") {
                    header("Authorization", authHeader)
                    parameter("cajaId", cajaId)
                    contentType(ContentType.Application.Json)
                    setBody(request)
                }
            Result.success(
                response.parseOrThrow(
                    EnviarComandaResponse.serializer(),
                    "No se pudo enviar la comanda",
                ),
            )
        }

    override suspend fun cambiarEstado(
        cajaId: String,
        areaId: Int,
        mesaId: Int,
        sesionId: Int,
        pedidoId: Int,
        request: CambiarEstadoPedidoRequest,
        authHeader: String,
    ): Result<PedidoMesaActualizadoResponse> =
        catchingResult {
            val response =
                apiClient.httpClient.patch("${base(areaId, mesaId, sesionId)}/$pedidoId") {
                    header("Authorization", authHeader)
                    parameter("cajaId", cajaId)
                    contentType(ContentType.Application.Json)
                    setBody(request)
                }
            Result.success(
                response.parseOrThrow(
                    PedidoMesaActualizadoResponse.serializer(),
                    "No se pudo cambiar el estado del pedido",
                ),
            )
        }

    private suspend fun <T> HttpResponse.parseOrThrow(
        serializer: DeserializationStrategy<T>,
        fallbackMessage: String,
    ): T {
        val body = bodyAsText()
        val backendError = body.extractErrorMessage()
        if (!status.isSuccess()) error(backendError ?: fallbackMessage)
        return runCatching { AppJson.decodeFromString(serializer, body) }
            .getOrElse { error(backendError ?: fallbackMessage) }
    }

    private fun String.extractErrorMessage(): String? {
        val json = runCatching { AppJson.decodeFromString(JsonElement.serializer(), this) }.getOrNull()
        return (json as? JsonObject)
            ?.get("error")
            ?.jsonPrimitive
            ?.contentOrNull
            ?.takeIf { it.isNotBlank() }
    }
}
