package com.amaxoniaerp.features.electronicinvoice.pac.thefactory.venezuela

import com.amaxoniaerp.features.electronicinvoice.domain.PacAuthToken
import com.amaxoniaerp.features.electronicinvoice.domain.PacCredentials
import io.ktor.client.HttpClient
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders

import io.ktor.http.contentType
import io.ktor.serialization.JsonConvertException
import kotlinx.serialization.json.Json
import java.net.UnknownHostException

/**
 * Implementación REST concreta de [VenezuelaHkaClient] para The Factory HKA.
 *
 * Rutas usadas (mantienen nombres del Swagger VE; no se reutilizan endpoints PA):
 *  - POST {baseUrl}/api/Autenticacion
 *  - POST {baseUrl}/api/Consultar_Ultimo_Documento
 *  - POST {baseUrl}/api/Emision_Procesar
 *
 * Política de seguridad:
 *  - El [HttpClient] lo inyecta Routing; ahí se asegura HTTPS con verificación
 *    TLS/hostname habilitada (sin trust-all) y timeouts request/connect/socket
 *    explícitos. Acá no se desactiva nada.
 *  - Nunca se loguean `token`, `usuario`, `clave` ni el JWT completo: solo una
 *    huella de 8 caracteres para trazabilidad.
 *  - `VenezuelaHkaResponse.rawBody` conserva el cuerpo textual; pero por logs
 *    únicamente se emite httpStatus + codigo + mensaje. El body crudo del JSON
 *    de respuesta NO se escribe en logs INFO/WARN.
 */
class VenezuelaHkaRestClient(
    private val httpClient: HttpClient,
    private val json: Json = feJsonVE,
) : VenezuelaHkaClient {

    private val log = org.slf4j.LoggerFactory.getLogger(VenezuelaHkaRestClient::class.java)

    override suspend fun authenticate(
        credentials: PacCredentials,
    ): VenezuelaHkaResponse<VenezuelaHkaAuthResponse> {
        val url = "${credentials.baseUrl.trimEnd('/')}/api/Autenticacion"
        log.info("[VE-HKA] Autenticacion usuario={} host={}", credentials.usuario.take(8), hostOf(url))
        val raw = postRaw(url, token = null) {
            setBody(VenezuelaHkaAuthRequest(
                usuario = credentials.usuario,
                clave = credentials.clave,
            ))
        }
        val parsed: VenezuelaHkaAuthResponse? = decodeBody(raw.bodyIfOk)
        // La autenticación no trae codigo de negocio; éxito = HTTP 200 + token.
        val codigo = parsed?.let { "200" } ?: raw.codigo
        val mensaje = parsed?.mensaje.orEmpty()
        return VenezuelaHkaResponse(
            httpStatus = raw.status,
            rawBody = raw.text,
            codigo = codigo,
            mensaje = mensaje,
            validaciones = emptyList(),
            resultado = parsed,
        )
    }

    override suspend fun fetchLastDocument(
        baseUrl: String,
        token: PacAuthToken,
        request: VenezuelaHkaUltimoDocumentoRequest,
    ): VenezuelaHkaResponse<VenezuelaHkaUltimoDocumentoResponse> {
        val url = "${baseUrl.trimEnd('/')}/api/Consultar_Ultimo_Documento"
        log.info("[VE-HKA] UltimoDocumento serie={} tipo={}", request.serie, request.tipoDocumento)
        val raw = postRaw(url, token) { setBody(request) }
        val parsed: VenezuelaHkaUltimoDocumentoResponse? = decodeBody(raw.bodyIfOk)
        return raw.build(parsed)
    }

    override suspend fun emitDocument(
        baseUrl: String,
        token: PacAuthToken,
        payload: VenezuelaHkaDocumentoWrapper,
    ): VenezuelaHkaResponse<VenezuelaHkaEmisionResponse> {
        val url = "${baseUrl.trimEnd('/')}/api/Emision_Procesar"
        log.info(
            "[VE-HKA] Emision tipo={} numDoc={} items={} formasPago={}",
            payload.documento.datosTransaccion.tipoDocumento,
            payload.documento.datosTransaccion.numeroDocumentoFiscal,
            payload.documento.listaItems.size,
            payload.documento.totalesSubTotales.listaFormaPago.size,
        )
        val raw = postRaw(url, token) { setBody(payload) }
        val parsed: VenezuelaHkaEmisionResponse? = decodeBody(raw.bodyIfOk)
        return raw.build(parsed)
    }

    // ─── Interno ─────────────────────────────────────────────────────────────

    private class RawCall(val status: Int, val text: String) {
        val httpOk: Boolean get() = status in 200..299
        val codigo: String get() = if (httpOk) "200" else "HTTP_$status"
        val bodyIfOk: String get() = if (httpOk) text else ""
    }

    private suspend inline fun postRaw(
        url: String,
        token: PacAuthToken?,
        crossinline configure: io.ktor.client.request.HttpRequestBuilder.() -> Unit,
    ): RawCall {
        return try {
            val response: HttpResponse = httpClient.post(url) {
                contentType(ContentType.Application.Json)
                if (token != null) header(HttpHeaders.Authorization, "Bearer ${token.token}")
                configure()
            }
            val status = response.status.value
            val text = response.bodyAsText()
            RawCall(status, text)
        } catch (e: HttpRequestTimeoutException) {
            throw VenezuelaHkaClientException.Timeout(
                "Timeout llamando a The Factory HKA VE. La respuesta NO se recibió.",
                e,
            )
        } catch (e: SocketTimeoutException) {
            throw VenezuelaHkaClientException.Timeout(
                "Timeout de socket con The Factory HKA VE. La respuesta NO se recibió.",
                e,
            )
        } catch (e: UnknownHostException) {
            throw VenezuelaHkaClientException.Network(
                "Host inaccesible: ${e.message}. Verifique baseUrl HKA VE.",
                e,
            )
        } catch (e: java.net.ConnectException) {
            throw VenezuelaHkaClientException.Network(
                "Conexión rechazada/no establecida con The Factory HKA VE.",
                e,
            )
        }
    }

    private inline fun <reified T> decodeBody(body: String): T? {
        if (body.isBlank()) return null
        return try { json.decodeFromString<T>(body) }
        catch (_: JsonConvertException) { null }
        catch (_: Exception) { null }
    }

    /**
     * Construye una respuesta genérica extrayendo codigo/mensaje/validaciones
     * del body parseado; en falla HTTP usa el sintético de HTTP_xxx.
     *
     * Importante: si la respuesta HTTP es 2xx pero el cuerpo NO se pudo
     * decodificar (JSON inválido/vacío) NO se sintetiza codigo "200" porque
     * eso implicaría éxito de negocio. En su lugar se emite `INVALID_JSON`
     * para que `businessOk` sea false y la estrategia trate la respuesta como
     * no exitosa (lo que según el contexto derivará en Failure o Uncertain).
     */
    private fun <T : Any> RawCall.build(parsed: T?): VenezuelaHkaResponse<T> {
        val codigo: String = when (parsed) {
            is VenezuelaHkaUltimoDocumentoResponse -> parsed.codigo ?: this.codigo
            is VenezuelaHkaEmisionResponse -> parsed.codigo ?: this.codigo
            else -> if (httpOk) "INVALID_JSON" else this.codigo
        }
        val mensaje: String = when (parsed) {
            is VenezuelaHkaUltimoDocumentoResponse -> parsed.mensaje.orEmpty()
            is VenezuelaHkaEmisionResponse -> parsed.mensaje.orEmpty()
            else -> if (!httpOk) "HTTP $status desde PAC VE" else "Respuesta no decodificable del PAC VE"
        }
        val validaciones: List<String> = when (parsed) {
            is VenezuelaHkaUltimoDocumentoResponse -> parsed.validaciones
            is VenezuelaHkaEmisionResponse -> parsed.validaciones
            else -> emptyList()
        }
        return VenezuelaHkaResponse(
            httpStatus = status,
            rawBody = text,
            codigo = codigo,
            mensaje = mensaje,
            validaciones = validaciones,
            resultado = parsed,
        )
    }

    private fun hostOf(url: String): String =
        runCatching { java.net.URI(url).host }.getOrNull().orEmpty().ifBlank { "?" }
}
