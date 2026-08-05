package com.amaxoniaerp.features.electronicinvoice.pac.thefactory.venezuela

import com.amaxoniaerp.features.electronicinvoice.domain.PacAuthToken
import com.amaxoniaerp.features.electronicinvoice.domain.PacCredentials

// ─── Port: contrato del cliente HTTP de The Factory HKA Venezuela ────────────
// Independiente del cliente Panamá: las rutas, DTOs y semántica del JSON son
// distintas y NO se comparten con `PanamaElectronicInvoiceClient`.

/**
 * Resultado estructurado de una llamada al PAC Venezuela.
 *
 * El brief exige separar SIEMPRE estos seis niveles y nunca mezclarlos:
 *
 *  1. [httpStatus]: estado HTTP de la respuesta (401, 400, 500, 200...).
 *  2. [rawBody]: cuerpo textual decodificado (vacío si no llegó).
 *  3. [codigo]: valor textual del campo `codigo` del JSON business.
 *  4. [mensaje]: valor textual del campo `mensaje`.
 *  5. [validaciones]: arreglo `validaciones` devuelto por el PAC.
 *  6. [resultado]: objeto `resultado` (específico de cada operación).
 *
 * En_failure HTTP el [codigo] se sintetiza del status (ej. "HTTP_401") para
 * tener un canal de logs limpio. En timeout se emite excepción (ver abajo).
 */
data class VenezuelaHkaResponse<T>(
    val httpStatus: Int,
    val rawBody: String,
    val codigo: String,
    val mensaje: String,
    val validaciones: List<String>,
    val resultado: T?,
) {
    /** HTTP en rango 2xx. El éxito de negocio se evalúa aparte. */
    val httpOk: Boolean get() = httpStatus in 200..299

    /** codigo == "200" (codigo de negocio exitoso del PAC VE). */
    val businessOk: Boolean get() = codigo == "200"

    /** Atajo frecuente: la llamada fue exitosa en transporte Y negocio. */
    val fullyOk: Boolean get() = httpOk && businessOk
}

/** Errores lanzados por el cliente HTTP Venezuela. */
sealed class VenezuelaHkaClientException(message: String, cause: Throwable? = null) :
    RuntimeException(message, cause) {
    /** Timeout de red (no necesariamente timeout fiscal). */
    class Timeout(message: String, cause: Throwable? = null) : VenezuelaHkaClientException(message, cause)
    /** Falla de red antes de recibir respuesta (DNS, conexión rechazada, TLS). */
    class Network(message: String, cause: Throwable? = null) : VenezuelaHkaClientException(message, cause)
}

/** Port del PAC Venezuela. Una sola implementación: [VenezuelaHkaRestClient]. */
interface VenezuelaHkaClient {
    /**
     * POST /api/Autenticacion → JWT.
     * Lanza [VenezuelaHkaClientException.Timeout] / [VenezuelaHkaClientException.Network].
     */
    suspend fun authenticate(credentials: PacCredentials): VenezuelaHkaResponse<VenezuelaHkaAuthResponse>

    /**
     * POST /api/Consultar_Ultimo_Documento → último número fiscal emitido.
     */
    suspend fun fetchLastDocument(
        baseUrl: String,
        token: PacAuthToken,
        request: VenezuelaHkaUltimoDocumentoRequest,
    ): VenezuelaHkaResponse<VenezuelaHkaUltimoDocumentoResponse>

    /**
     * POST /api/Emision_Procesar → resultado de emisión final.
     */
    suspend fun emitDocument(
        baseUrl: String,
        token: PacAuthToken,
        payload: VenezuelaHkaDocumentoWrapper,
    ): VenezuelaHkaResponse<VenezuelaHkaEmisionResponse>
}
