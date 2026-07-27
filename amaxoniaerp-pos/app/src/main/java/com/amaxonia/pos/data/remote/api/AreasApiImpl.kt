package com.amaxonia.pos.data.remote.api

import com.amaxonia.pos.core.result.catchingResult
import com.amaxonia.pos.data.local.AppJson
import com.amaxonia.pos.data.remote.ApiClient
import com.amaxonia.pos.domain.model.mesas.AreasResponse
import com.amaxonia.pos.domain.model.mesas.MesasResponse
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

class AreasApiImpl(
    private val apiClient: ApiClient,
) : AreasApi {
    override suspend fun getAreas(
        cajaId: String,
        authHeader: String,
    ): Result<AreasResponse> =
        catchingResult {
            val response =
                apiClient.httpClient.get("api/pos/areas") {
                    header("Authorization", authHeader)
                    parameter("cajaId", cajaId)
                }
            Result.success(
                response.parseOrThrow(
                    serializer = AreasResponse.serializer(),
                    fallbackMessage = "No se pudieron consultar las áreas",
                ),
            )
        }

    override suspend fun getMesas(
        cajaId: String,
        areaId: Int,
        authHeader: String,
    ): Result<MesasResponse> =
        catchingResult {
            val response =
                apiClient.httpClient.get("api/pos/areas/$areaId/mesas") {
                    header("Authorization", authHeader)
                    parameter("cajaId", cajaId)
                }
            Result.success(
                response.parseOrThrow(
                    serializer = MesasResponse.serializer(),
                    fallbackMessage = "No se pudieron consultar las mesas",
                ),
            )
        }

    /**
     * El backend devuelve `{"error": "..."}` en los fallos (401/403/404/409/500). Se traduce a una
     * excepción con ese mensaje para que la UI muestre el motivo real y no un texto genérico.
     */
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
