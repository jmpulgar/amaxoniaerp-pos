package com.amaxoniaerp.features.electronicinvoice.pac.thefactory

import com.amaxoniaerp.features.electronicinvoice.domain.PacAuthToken
import com.amaxoniaerp.features.electronicinvoice.domain.PacCommunicationException
import com.amaxoniaerp.features.electronicinvoice.domain.PacCredentials
import com.amaxoniaerp.features.electronicinvoice.domain.PacEstadoDocumento
import com.amaxoniaerp.features.electronicinvoice.domain.PacEstadoDocumentoSolicitud
import com.amaxoniaerp.features.electronicinvoice.domain.PacResponse
import com.amaxoniaerp.features.electronicinvoice.pac.PanamaElectronicInvoiceClient
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.slf4j.LoggerFactory
import java.util.Base64

private const val CUFE_LOG_PREFIX_LENGTH = 20
private const val RESPONSE_LOG_PREVIEW_LENGTH = 200
private val PDF_MAGIC_BYTES = byteArrayOf(0x25, 0x50, 0x44, 0x46) // "%PDF"
private val theFactoryRestClientJson =
    Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

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

    override suspend fun authenticate(credentials: PacCredentials): Result<PacAuthToken> =
        runCatching {
            val url = "${credentials.baseUrl.trimEnd('/')}/api/Autenticacion"
            logger.info("Autenticando con The Factory HKA en: {}", url)

            val response: HttpResponse =
                httpClient.post(url) {
                    contentType(ContentType.Application.Json)
                    setBody(
                        TheFactoryAuthRequest(
                            usuario = credentials.usuario,
                            clave = credentials.clave,
                        ),
                    )
                }

            if (!response.status.isSuccess()) {
                val body = runCatching { response.bodyAsText() }.getOrDefault("")
                throw PacCommunicationException(
                    "Autenticación fallida con The Factory HKA: HTTP ${response.status}. Body: $body",
                )
            }

            val body = response.body<TheFactoryAuthResponse>()
            val token =
                body.token
                    ?: throw PacCommunicationException(
                        "Token vacío en respuesta de autenticación: ${body.mensaje ?: "sin mensaje"}",
                    )

            logger.info("Autenticación exitosa con The Factory HKA")
            PacAuthToken(
                token = token,
                expiresAt = System.currentTimeMillis() + 3_600_000, // 1 hora
            )
        }.onFailure { e ->
            logger.error("Error autenticando con The Factory HKA", e)
        }

    override suspend fun sendDocument(
        baseUrl: String,
        token: PacAuthToken,
        payload: TheFactoryHkaDocumentoWrapper,
    ): Result<PacResponse> =
        runCatching {
            val url = "${baseUrl.trimEnd('/')}/api/Enviar"
            val payloadJson =
                runCatching {
                    theFactoryRestClientJson.encodeToString(TheFactoryHkaDocumentoWrapper.serializer(), payload)
                }.getOrDefault("<serialización no disponible>")
            logger.info(
                "[FE-PAC] Enviando documento electrónico a The Factory HKA: {}\nPayload JSON:\n{}",
                url,
                payloadJson,
            )

            val response: HttpResponse =
                httpClient.post(url) {
                    contentType(ContentType.Application.Json)
                    header(HttpHeaders.Authorization, "Bearer ${token.token}")
                    setBody(payload)
                }

            val responseText = response.bodyAsText()
            logger.info("[FE-PAC] Respuesta recibida de The Factory HKA [HTTP {}]:\n{}", response.status, responseText)

            if (!response.status.isSuccess()) {
                throw PacCommunicationException(
                    "Error HTTP ${response.status} al enviar documento a The Factory HKA. Body: $responseText",
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

    override suspend fun consultarEstadoDocumento(
        baseUrl: String,
        token: PacAuthToken,
        solicitud: PacEstadoDocumentoSolicitud,
    ): Result<PacEstadoDocumento> =
        runCatching {
            val url = "${baseUrl.trimEnd('/')}/api/EstadoDocumento"
            logger.info("[FE-PAC] Consultando EstadoDocumento para documento {}", solicitud.numeroDocumentoFiscal)

            val response: HttpResponse =
                httpClient.post(url) {
                    contentType(ContentType.Application.Json)
                    header(HttpHeaders.Authorization, "Bearer ${token.token}")
                    setBody(
                        TheFactoryEstadoDocumentoRequest(
                            codigoSucursalEmisor = solicitud.codigoSucursalEmisor,
                            puntoFacturacionFiscal = solicitud.puntoFacturacionFiscal,
                            numeroDocumentoFiscal = solicitud.numeroDocumentoFiscal,
                            tipoDocumento = solicitud.tipoDocumento,
                        ),
                    )
                }

            val responseText = response.bodyAsText()
            logger.info("[FE-PAC] EstadoDocumento [HTTP {}]: {}", response.status, responseText)

            if (!response.status.isSuccess()) {
                throw PacCommunicationException(
                    "Error HTTP ${response.status} en EstadoDocumento. Body: $responseText",
                )
            }

            val body = response.body<TheFactoryEstadoDocumentoResponse>()
            PacEstadoDocumento(
                codigo = body.codigo ?: response.status.value.toString(),
                mensaje = body.mensaje ?: body.resultado ?: "",
                cufe = body.cufe,
                fechaRecepcionDGI = body.fechaRecepcionDocumento,
            )
        }.onFailure { e ->
            logger.error("Error consultando EstadoDocumento en The Factory HKA", e)
        }

    override suspend fun downloadPdf(
        baseUrl: String,
        token: PacAuthToken,
        cufe: String,
    ): Result<ByteArray> =
        downloadDocument(
            baseUrl = baseUrl,
            token = token,
            cufe = cufe,
            tipoArchivo = "pdf",
            fallbackEndpoint = "DescargaPDF",
            fileDescription = "PDF",
            isRawContent = ::isRawPdf,
        )

    override suspend fun downloadXml(
        baseUrl: String,
        token: PacAuthToken,
        cufe: String,
    ): Result<ByteArray> =
        downloadDocument(
            baseUrl = baseUrl,
            token = token,
            cufe = cufe,
            tipoArchivo = "xml",
            fallbackEndpoint = "DescargaXML",
            fileDescription = "XML",
            isRawContent = ::isRawXml,
        )

    private suspend fun downloadDocument(
        baseUrl: String,
        token: PacAuthToken,
        cufe: String,
        tipoArchivo: String,
        fallbackEndpoint: String,
        fileDescription: String,
        isRawContent: (ContentType?, ByteArray) -> Boolean,
    ): Result<ByteArray> =
        runCatching {
            val url = "${baseUrl.trimEnd('/')}/api/Descarga"
            logger.info("Descargando {} de The Factory HKA para CUFE: {}", fileDescription, cufe)

            var response: HttpResponse =
                httpClient.post(url) {
                    contentType(ContentType.Application.Json)
                    header(HttpHeaders.Authorization, "Bearer ${token.token}")
                    setBody(TheFactoryDescargaArchivoRequest(cufe = cufe, tipoArchivo = tipoArchivo))
                }

            if (response.status == HttpStatusCode.NotFound) {
                val fallbackUrl = "${baseUrl.trimEnd('/')}/api/$fallbackEndpoint"
                logger.info("Endpoint /api/Descarga no encontrado. Intentando con {}", fallbackUrl)
                response =
                    httpClient.post(fallbackUrl) {
                        contentType(ContentType.Application.Json)
                        header(HttpHeaders.Authorization, "Bearer ${token.token}")
                        setBody(TheFactoryDescargaArchivoRequest(cufe = cufe, tipoArchivo = tipoArchivo))
                    }
            }

            if (!response.status.isSuccess()) {
                val errorBody = runCatching { response.bodyAsText() }.getOrDefault("")
                throw PacCommunicationException(
                    "Error HTTP ${response.status} al descargar $fileDescription de The Factory HKA. Body: $errorBody",
                )
            }

            val contentType = response.contentType()
            val rawBytes = response.body<ByteArray>()

            if (isRawContent(contentType, rawBytes)) {
                rawBytes
            } else {
                val responseText = rawBytes.decodeToString()
                logger.info(
                    "Respuesta Descarga The Factory HKA [HTTP {}]: {}",
                    response.status,
                    responseText.take(RESPONSE_LOG_PREVIEW_LENGTH),
                )
                val jsonObject =
                    runCatching {
                        theFactoryRestClientJson.parseToJsonElement(responseText).jsonObject
                    }.getOrNull()

                val base64Content =
                    jsonObject?.get("archivo")?.jsonPrimitive?.contentOrNull
                        ?: jsonObject?.get("Archivo")?.jsonPrimitive?.contentOrNull

                if (!base64Content.isNullOrBlank()) {
                    Base64.getDecoder().decode(base64Content.trim())
                } else {
                    val msg =
                        jsonObject?.get("mensaje")?.jsonPrimitive?.contentOrNull
                            ?: jsonObject?.get("Mensaje")?.jsonPrimitive?.contentOrNull
                            ?: jsonObject?.get("resultado")?.jsonPrimitive?.contentOrNull
                            ?: jsonObject?.get("Resultado")?.jsonPrimitive?.contentOrNull
                            ?: "Respuesta de The Factory HKA sin archivo descargable"
                    throw PacCommunicationException(msg)
                }
            }
        }.onFailure { e ->
            logger.error("Error descargando {} de The Factory HKA para CUFE: {}", fileDescription, cufe, e)
        }

    private fun isRawPdf(
        contentType: ContentType?,
        bytes: ByteArray,
    ): Boolean {
        if (contentType?.match(ContentType.Application.Pdf) == true) return true
        if (bytes.size < PDF_MAGIC_BYTES.size) return false
        return bytes.take(PDF_MAGIC_BYTES.size).toByteArray().contentEquals(PDF_MAGIC_BYTES)
    }

    private fun isRawXml(
        contentType: ContentType?,
        bytes: ByteArray,
    ): Boolean {
        if (contentType?.match(ContentType.Application.Xml) == true) return true
        if (contentType?.match(ContentType.Text.Xml) == true) return true
        val trimmed =
            bytes
                .take(10)
                .toByteArray()
                .decodeToString()
                .trimStart()
        return trimmed.startsWith("<?xml") || trimmed.startsWith("<")
    }

    override suspend fun sendEmail(
        baseUrl: String,
        token: PacAuthToken,
        cufe: String,
        emails: List<String>,
    ): Result<TheFactoryEnviarCorreoResponse> =
        runCatching {
            val url = "${baseUrl.trimEnd('/')}/api/EnvioCorreo"
            logger.info(
                "Enviando factura electrónica por correo desde The Factory HKA. CUFE={}",
                cufe.take(CUFE_LOG_PREFIX_LENGTH),
            )

            val response: HttpResponse =
                httpClient.post(url) {
                    contentType(ContentType.Application.Json)
                    header(HttpHeaders.Authorization, "Bearer ${token.token}")
                    setBody(TheFactoryEnviarCorreoRequest(cufe = cufe, correos = emails))
                }

            val responseText = response.bodyAsText()
            logger.debug("Respuesta EnvioCorreo The Factory HKA [HTTP {}]: {}", response.status, responseText)

            if (!response.status.isSuccess()) {
                throw PacCommunicationException(
                    "Error HTTP ${response.status} al enviar correo desde The Factory HKA. Body: $responseText",
                )
            }

            response.body<TheFactoryEnviarCorreoResponse>()
        }.onFailure { e ->
            logger.error("Error enviando correo The Factory HKA para CUFE: {}", cufe, e)
        }
}
