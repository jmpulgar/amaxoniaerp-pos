package com.amaxonia.pos.data.remote.api

import com.amaxonia.pos.core.result.catchingResult
import com.amaxonia.pos.data.local.AppJson
import com.amaxonia.pos.data.remote.ApiClient
import com.amaxonia.pos.domain.model.payment.FormaPago
import com.amaxonia.pos.domain.model.payment.FormasPagoResponse
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonPrimitive

class FormaPagoApiImpl(
    private val apiClient: ApiClient,
) : FormaPagoApi {
    override suspend fun getFormasPago(
        cajaId: String?,
        authHeader: String,
    ): Result<FormasPagoResponse> =
        catchingResult {
            val response =
                apiClient.httpClient.get("api/pos/formas-pago") {
                    header("Authorization", authHeader)
                    if (!cajaId.isNullOrBlank()) {
                        parameter("cajaId", cajaId)
                    }
                }

            val responseText = response.bodyAsText()
            val jsonElement = AppJson.decodeFromString(JsonElement.serializer(), responseText)

            val parsed =
                when (jsonElement) {
                    is JsonArray -> {
                        val forms = AppJson.decodeFromJsonElement<List<FormaPago>>(jsonElement)
                        FormasPagoResponse(success = true, data = forms)
                    }

                    is JsonObject -> {
                        val error = jsonElement["error"]?.jsonPrimitive?.contentOrNull
                        if (!error.isNullOrBlank()) {
                            error(error)
                        }

                        val dataElement = jsonElement["data"]
                        if (dataElement != null) {
                            val forms = AppJson.decodeFromJsonElement<List<FormaPago>>(dataElement)
                            val success =
                                jsonElement["success"]
                                    ?.jsonPrimitive
                                    ?.booleanOrNull
                                    ?: true
                            FormasPagoResponse(success = success, data = forms)
                        } else {
                            AppJson.decodeFromJsonElement(FormasPagoResponse.serializer(), jsonElement)
                        }
                    }

                    else -> error("Respuesta inválida del endpoint de formas de pago")
                }

            Result.success(parsed)
        }
}
