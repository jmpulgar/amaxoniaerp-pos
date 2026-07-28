package com.amaxonia.pos.data.remote.api

import com.amaxonia.pos.core.result.catchingResult
import com.amaxonia.pos.data.local.AppJson
import com.amaxonia.pos.data.remote.ApiClient
import com.amaxonia.pos.domain.model.mesas.CrearCuentaRequest
import com.amaxonia.pos.domain.model.mesas.CrearCuentaResponse
import com.amaxonia.pos.domain.model.mesas.CuentasMesaListResponse
import com.amaxonia.pos.domain.model.mesas.MarcarCuentaFacturadaRequest
import com.amaxonia.pos.domain.model.mesas.MarcarCuentaFacturadaResponse
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
 * Implementación Ktor de [CuentaMesaApi]. Reproduce el patrón de [PedidosMesaApiImpl]: parsea
 * el body, traduce `{"error": "..."}` del backend a excepción con ese mensaje y solo devuelve
 * `Result.success` cuando el HTTP fue exitoso.
 */
class CuentaMesaApiImpl(
    private val apiClient: ApiClient,
) : CuentaMesaApi {
    private fun base(
        areaId: Int,
        mesaId: Int,
        sesionId: Int,
    ) = "api/pos/areas/$areaId/mesas/$mesaId/sesiones/$sesionId"

    override suspend fun listar(
        cajaId: String,
        areaId: Int,
        mesaId: Int,
        sesionId: Int,
        authHeader: String,
    ): Result<CuentasMesaListResponse> =
        catchingResult {
            val response =
                apiClient.httpClient.get("${base(areaId, mesaId, sesionId)}/cuenta") {
                    header("Authorization", authHeader)
                    parameter("cajaId", cajaId)
                }
            Result.success(
                response.parseOrThrow(
                    CuentasMesaListResponse.serializer(),
                    "No se pudieron consultar las cuentas",
                ),
            )
        }

    override suspend fun obtener(
        cajaId: String,
        areaId: Int,
        mesaId: Int,
        sesionId: Int,
        cuentaId: Int,
        authHeader: String,
    ): Result<CrearCuentaResponse> =
        catchingResult {
            val response =
                apiClient.httpClient.get("${base(areaId, mesaId, sesionId)}/cuenta/$cuentaId") {
                    header("Authorization", authHeader)
                    parameter("cajaId", cajaId)
                }
            Result.success(
                response.parseOrThrow(
                    CrearCuentaResponse.serializer(),
                    "No se pudo consultar la cuenta",
                ),
            )
        }

    override suspend fun crear(
        cajaId: String,
        areaId: Int,
        mesaId: Int,
        sesionId: Int,
        request: CrearCuentaRequest,
        authHeader: String,
    ): Result<CrearCuentaResponse> =
        catchingResult {
            val response =
                apiClient.httpClient.post("${base(areaId, mesaId, sesionId)}/cuenta") {
                    header("Authorization", authHeader)
                    parameter("cajaId", cajaId)
                    contentType(ContentType.Application.Json)
                    setBody(request)
                }
            Result.success(
                response.parseOrThrow(
                    CrearCuentaResponse.serializer(),
                    "No se pudo crear la cuenta",
                ),
            )
        }

    override suspend fun cancelar(
        cajaId: String,
        areaId: Int,
        mesaId: Int,
        sesionId: Int,
        cuentaId: Int,
        authHeader: String,
    ): Result<CrearCuentaResponse> =
        catchingResult {
            val response =
                apiClient.httpClient.post("${base(areaId, mesaId, sesionId)}/cuenta/$cuentaId/cancelar") {
                    header("Authorization", authHeader)
                    parameter("cajaId", cajaId)
                }
            Result.success(
                response.parseOrThrow(
                    CrearCuentaResponse.serializer(),
                    "No se pudo cancelar la cuenta",
                ),
            )
        }

    override suspend fun marcarFacturada(
        cajaId: String,
        areaId: Int,
        mesaId: Int,
        sesionId: Int,
        cuentaId: Int,
        request: MarcarCuentaFacturadaRequest,
        authHeader: String,
    ): Result<MarcarCuentaFacturadaResponse> =
        catchingResult {
            val response =
                apiClient.httpClient.post("${base(areaId, mesaId, sesionId)}/cuenta/$cuentaId/marcar-facturada") {
                    header("Authorization", authHeader)
                    parameter("cajaId", cajaId)
                    contentType(ContentType.Application.Json)
                    setBody(request)
                }
            Result.success(
                response.parseOrThrow(
                    MarcarCuentaFacturadaResponse.serializer(),
                    "No se pudo confirmar la facturación",
                ),
            )
        }

    override suspend fun solicitarCuenta(
        cajaId: String,
        areaId: Int,
        mesaId: Int,
        sesionId: Int,
        authHeader: String,
    ): Result<SolicitudCuentaResponse> =
        catchingResult {
            val response =
                apiClient.httpClient.post("${base(areaId, mesaId, sesionId)}/solicitar-cuenta") {
                    header("Authorization", authHeader)
                    parameter("cajaId", cajaId)
                }
            Result.success(
                response.parseOrThrow(
                    SolicitudCuentaResponse.serializer(),
                    "No se pudo solicitar la cuenta",
                ),
            )
        }

    override suspend fun cancelarSolicitudCuenta(
        cajaId: String,
        areaId: Int,
        mesaId: Int,
        sesionId: Int,
        authHeader: String,
    ): Result<SolicitudCuentaResponse> =
        catchingResult {
            val response =
                apiClient.httpClient.post("${base(areaId, mesaId, sesionId)}/cancelar-solicitud-cuenta") {
                    header("Authorization", authHeader)
                    parameter("cajaId", cajaId)
                }
            Result.success(
                response.parseOrThrow(
                    SolicitudCuentaResponse.serializer(),
                    "No se pudo cancelar la solicitud de cuenta",
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
