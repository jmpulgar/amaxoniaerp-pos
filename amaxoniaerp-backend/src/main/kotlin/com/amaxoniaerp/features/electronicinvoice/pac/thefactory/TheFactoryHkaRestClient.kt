package com.amaxoniaerp.features.electronicinvoice.pac.thefactory

import com.amaxoniaerp.features.electronicinvoice.domain.PacAuthToken
import com.amaxoniaerp.features.electronicinvoice.domain.PacCommunicationException
import com.amaxoniaerp.features.electronicinvoice.domain.PacCredentials
import com.amaxoniaerp.features.electronicinvoice.domain.PacResponse
import com.amaxoniaerp.features.electronicinvoice.pac.PanamaElectronicInvoiceClient
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import org.slf4j.LoggerFactory

/**
 * Adapter Pattern: implementación concreta de [PanamaElectronicInvoiceClient]
 * para el PAC "The Factory HKA" usando su API REST.
 *
 * Responsabilidades:
 * - Autenticarse vía `POST /api/Autenticacion` para obtener un JWT.
 * - Enviar documentos electrónicos vía `POST /api/Enviar`.
 * - Normalizar las respuestas específicas de The Factory al [PacResponse] estándar.
 *
 * El [HttpClient] se inyecta externamente (configurado con timeouts, logging, etc.)
 * para facilitar testing y reutilización.
 */
class TheFactoryHkaRestClient(
    private val httpClient: HttpClient,
) : PanamaElectronicInvoiceClient {

    private val logger = LoggerFactory.getLogger(TheFactoryHkaRestClient::class.java)

    override suspend fun authenticate(credentials: PacCredentials): Result<PacAuthToken> {
        return runCatching {
            val url = "${credentials.baseUrl.trimEnd('/')}/api/Autenticacion"
            logger.info("Autenticando con The Factory HKA en: {}", url)

            val response: HttpResponse = httpClient.post(url) {
                contentType(ContentType.Application.Json)
                setBody(TheFactoryAuthRequest(
                    usuario = credentials.usuario,
                    clave = credentials.clave,
                ))
            }

            if (!response.status.isSuccess()) {
                val body = runCatching { response.bodyAsText() }.getOrDefault("")
                throw PacCommunicationException(
                    "Autenticación fallida con The Factory HKA: HTTP ${response.status}. Body: $body"
                )
            }

            val body = response.body<TheFactoryAuthResponse>()
            val token = body.token
                ?: throw PacCommunicationException(
                    "Token vacío en respuesta de autenticación: ${body.mensaje ?: "sin mensaje"}"
                )

            logger.info("Autenticación exitosa con The Factory HKA")
            PacAuthToken(
                token = token,
                expiresAt = System.currentTimeMillis() + 3_600_000, // 1 hora
            )
        }.onFailure { e ->
            logger.error("Error autenticando con The Factory HKA", e)
        }
    }

    override suspend fun sendDocument(
        baseUrl: String,
        token: PacAuthToken,
        payload: TheFactoryHkaDocumentoWrapper,
    ): Result<PacResponse> {
        return runCatching {
            val url = "${baseUrl.trimEnd('/')}/api/Enviar"
            logger.info("Enviando documento electrónico a The Factory HKA: {}", url)

            val response: HttpResponse = httpClient.post(url) {
                contentType(ContentType.Application.Json)
                header(HttpHeaders.Authorization, "Bearer ${token.token}")
                setBody(payload)
            }

            val responseText = response.bodyAsText()
            logger.debug("Respuesta de The Factory HKA [HTTP {}]: {}", response.status, responseText)

            if (!response.status.isSuccess()) {
                throw PacCommunicationException(
                    "Error HTTP ${response.status} al enviar documento a The Factory HKA. Body: $responseText"
                )
            }

            val body = response.body<TheFactoryEnviarResponse>()

            // Normalizar a PacResponse estandarizado
            PacResponse(
                exitoso = (body.codigo == "200" || body.resultado?.contains("exitoso", ignoreCase = true) == true),
                codigo = body.codigo ?: response.status.value.toString(),
                mensaje = body.mensaje ?: body.resultado ?: "",
                cufe = body.cufe,
                qr = body.qr,
                fechaRecepcionDGI = body.fechaRecepcionDGI,
                nroProtocoloAutorizacion = body.nroProtocoloAutorizacion,
                fechaLimite = body.fechaLimite,
            )
        }.onFailure { e ->
            logger.error("Error enviando documento a The Factory HKA", e)
        }
    }

    override suspend fun downloadPdf(
        baseUrl: String,
        token: PacAuthToken,
        cufe: String,
    ): Result<ByteArray> {
        return runCatching {
            val url = "${baseUrl.trimEnd('/')}/api/DescargaPDF"
            logger.info("Descargando PDF de The Factory HKA para CUFE: {}", cufe)

            val response: HttpResponse = httpClient.post(url) {
                contentType(ContentType.Application.Json)
                header(HttpHeaders.Authorization, "Bearer ${token.token}")
                setBody(mapOf("cufe" to cufe))
            }

            if (!response.status.isSuccess()) {
                throw PacCommunicationException(
                    "Error HTTP ${response.status} al descargar PDF de The Factory HKA"
                )
            }

            response.body<ByteArray>()
        }.onFailure { e ->
            logger.error("Error descargando PDF de The Factory HKA para CUFE: {}", cufe, e)
        }
    }

    override suspend fun sendEmail(
        baseUrl: String,
        token: PacAuthToken,
        cufe: String,
        emails: List<String>,
    ): Result<TheFactoryEnviarCorreoResponse> {
        return runCatching {
            val url = "${baseUrl.trimEnd('/')}/api/EnvioCorreo"
            logger.info("Enviando factura electrónica por correo desde The Factory HKA. CUFE={}", cufe.take(20))

            val response: HttpResponse = httpClient.post(url) {
                contentType(ContentType.Application.Json)
                header(HttpHeaders.Authorization, "Bearer ${token.token}")
                setBody(TheFactoryEnviarCorreoRequest(cufe = cufe, correos = emails))
            }

            val responseText = response.bodyAsText()
            logger.debug("Respuesta EnvioCorreo The Factory HKA [HTTP {}]: {}", response.status, responseText)

            if (!response.status.isSuccess()) {
                throw PacCommunicationException(
                    "Error HTTP ${response.status} al enviar correo desde The Factory HKA. Body: $responseText"
                )
            }

            response.body<TheFactoryEnviarCorreoResponse>()
        }.onFailure { e ->
            logger.error("Error enviando correo The Factory HKA para CUFE: {}", cufe, e)
        }
    }
}
