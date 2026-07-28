package com.amaxonia.pos.data.remote.api

import com.amaxonia.pos.core.result.catchingResult
import com.amaxonia.pos.data.local.AppJson
import com.amaxonia.pos.data.remote.ApiClient
import com.amaxonia.pos.domain.model.mesas.AbrirSesionRequest
import com.amaxonia.pos.domain.model.mesas.AbrirSesionResponse
import com.amaxonia.pos.domain.model.mesas.EstadosMesasResponse
import com.amaxonia.pos.domain.model.mesas.SesionActivaResponse
import com.amaxonia.pos.domain.model.mesas.SesionMutacionResponse
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
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
 * Implementación Ktor de [SesionMesaApi]. Sigue el mismo patrón que [AreasApiImpl]: parsea el
 * body, traduce un `{"error": "..."}` del backend a excepción con ese mensaje y solo devuelve
 * `Result.success` cuando el HTTP fue exitoso.
 */
class SesionMesaApiImpl(
    private val apiClient: ApiClient,
) : SesionMesaApi {
    override suspend fun getEstados(
        cajaId: String,
        areaId: Int,
        authHeader: String,
    ): Result<EstadosMesasResponse> =
        catchingResult {
            val response =
                apiClient.httpClient.get("api/pos/areas/$areaId/mesas/estados") {
                    header("Authorization", authHeader)
                    parameter("cajaId", cajaId)
                }
            Result.success(
                response.parseOrThrow(
                    EstadosMesasResponse.serializer(),
                    "No se pudieron consultar los estados de las mesas",
                ),
            )
        }

    override suspend fun abrir(
        cajaId: String,
        areaId: Int,
        mesaId: Int,
        request: AbrirSesionRequest,
        authHeader: String,
    ): Result<AbrirSesionResponse> =
        catchingResult {
            val response =
                apiClient.httpClient.post("api/pos/areas/$areaId/mesas/$mesaId/sesiones") {
                    header("Authorization", authHeader)
                    parameter("cajaId", cajaId)
                    contentType(ContentType.Application.Json)
                    setBody(request)
                }
            Result.success(
                response.parseOrThrow(
                    AbrirSesionResponse.serializer(),
                    "No se pudo abrir la sesión de la mesa",
                ),
            )
        }

    override suspend fun getSesionActiva(
        cajaId: String,
        areaId: Int,
        mesaId: Int,
        authHeader: String,
    ): Result<SesionActivaResponse> =
        catchingResult {
            val response =
                apiClient.httpClient.get("api/pos/areas/$areaId/mesas/$mesaId/sesiones/activa") {
                    header("Authorization", authHeader)
                    parameter("cajaId", cajaId)
                }
            Result.success(
                response.parseOrThrow(
                    SesionActivaResponse.serializer(),
                    "No se pudo recuperar la sesión activa",
                ),
            )
        }

    override suspend fun cerrar(
        cajaId: String,
        areaId: Int,
        mesaId: Int,
        sesionId: Int,
        authHeader: String,
    ): Result<SesionMutacionResponse> =
        catchingResult {
            val response =
                apiClient.httpClient.post(
                    "api/pos/areas/$areaId/mesas/$mesaId/sesiones/$sesionId/cerrar",
                ) {
                    header("Authorization", authHeader)
                    parameter("cajaId", cajaId)
                    contentType(ContentType.Application.Json)
                }
            Result.success(
                response.parseOrThrow(
                    SesionMutacionResponse.serializer(),
                    "No se pudo cerrar la sesión de la mesa",
                ),
            )
        }

    override suspend fun cancelar(
        cajaId: String,
        areaId: Int,
        mesaId: Int,
        sesionId: Int,
        authHeader: String,
    ): Result<SesionMutacionResponse> =
        catchingResult {
            val response =
                apiClient.httpClient.post(
                    "api/pos/areas/$areaId/mesas/$mesaId/sesiones/$sesionId/cancelar",
                ) {
                    header("Authorization", authHeader)
                    parameter("cajaId", cajaId)
                    contentType(ContentType.Application.Json)
                }
            Result.success(
                response.parseOrThrow(
                    SesionMutacionResponse.serializer(),
                    "No se pudo cancelar la sesión de la mesa",
                ),
            )
        }

    private suspend fun <T> HttpResponse.parseOrThrow(
        serializer: DeserializationStrategy<T>,
        fallbackMessage: String,
    ): T {
        val body = bodyAsText()
        val backendError = body.extractErrorMessage()

        if (!status.isSuccess()) {
            error(backendError ?: fallbackMessage)
        }

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
