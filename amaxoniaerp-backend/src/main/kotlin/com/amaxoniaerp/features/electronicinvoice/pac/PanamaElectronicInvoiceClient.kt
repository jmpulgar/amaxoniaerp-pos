package com.amaxoniaerp.features.electronicinvoice.pac

import com.amaxoniaerp.features.electronicinvoice.domain.PacAuthToken
import com.amaxoniaerp.features.electronicinvoice.domain.PacCredentials
import com.amaxoniaerp.features.electronicinvoice.domain.PacResponse
import com.amaxoniaerp.features.electronicinvoice.pac.thefactory.TheFactoryEnviarCorreoResponse
import com.amaxoniaerp.features.electronicinvoice.pac.thefactory.TheFactoryHkaDocumentoWrapper

/**
 * Port Pattern: contrato que debe cumplir cualquier PAC de Panamá.
 *
 * Hoy la única implementación es [TheFactoryHkaRestClient].
 * Si mañana se integra otro PAC, se crea una nueva clase que implemente
 * esta interfaz y se inyecta vía el constructor de [PanamaInvoiceProcessor].
 *
 * El sistema principal nunca depende de respuestas específicas de un PAC concreto;
 * todas las interacciones se normalizan a [PacResponse].
 */
interface PanamaElectronicInvoiceClient {

    /**
     * Obtiene un token JWT del PAC usando las credenciales del contribuyente.
     */
    suspend fun authenticate(credentials: PacCredentials): Result<PacAuthToken>

    /**
     * Envía un documento electrónico al PAC para su validación y autorización por la DGI.
     *
     * @param baseUrl URL base del endpoint REST del PAC.
     * @param token Token de autenticación obtenido de [authenticate].
     * @param payload Documento electrónico estructurado según el formato del PAC.
     * @return [PacResponse] estandarizado con CUFE, QR y fechas si es exitoso.
     */
    suspend fun sendDocument(
        baseUrl: String,
        token: PacAuthToken,
        payload: TheFactoryHkaDocumentoWrapper,
    ): Result<PacResponse>

    /**
     * Envía el CAFE/PDF del documento autorizado por correo usando el CUFE.
     */
    suspend fun sendEmail(
        baseUrl: String,
        token: PacAuthToken,
        cufe: String,
        emails: List<String>,
    ): Result<TheFactoryEnviarCorreoResponse>

    /**
     * Descarga el PDF/CAFE del documento autorizado usando el CUFE.
     */
    suspend fun downloadPdf(
        baseUrl: String,
        token: PacAuthToken,
        cufe: String,
    ): Result<ByteArray>
}
