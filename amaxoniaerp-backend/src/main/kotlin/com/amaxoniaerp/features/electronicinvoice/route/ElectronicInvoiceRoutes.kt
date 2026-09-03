package com.amaxoniaerp.features.electronicinvoice.route

import com.amaxoniaerp.core.tenant.connectDatabase
import com.amaxoniaerp.core.tenant.resolveCompanyRequestContext
import com.amaxoniaerp.features.electronicinvoice.application.ElectronicInvoiceProcessorFactory
import com.amaxoniaerp.features.electronicinvoice.domain.ElectronicInvoiceResult
import com.amaxoniaerp.features.electronicinvoice.domain.IncidenciaFiscal
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.authenticate
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlinx.serialization.Serializable

/**
 * Endpoints REST para facturación electrónica.
 *
 * Permite enviar manualmente una factura existente al PAC. Este endpoint es
 * complementario a la integración automática en [ProcessSaleUseCase] y sirve
 * para reenvíos, reintentos o envíos diferidos.
 */
fun Route.electronicInvoiceRoutes(factory: ElectronicInvoiceProcessorFactory) {
    val handlers = ElectronicInvoiceHandlers(factory)

    authenticate {
        route("/api/facturacion-electronica") {
            /**
             * POST /api/facturacion-electronica/{invoiceId}/enviar
             *
             * Envía una factura existente al PAC para obtener CUFE/QR.
             * Si el país es VE, retorna 200 con "no aplicable".
             */
            post("/{invoiceId}/enviar") { handlers.enviar(call) }
        }
    }
}

/**
 * Respuesta tipada del envío FE (éxito, ya emitida, no aplicable o tipo no
 * soportado). Las claves son el contrato del cliente POS
 * ([com.amaxoniaerp.features.electronicinvoice] -> POS ElectronicInvoiceResultDto):
 * `success`, `cufe`, `qr`, `message`, `alreadyIssued`, `numeroDocumentoFiscal`.
 */
@Serializable
data class ElectronicInvoiceResponse(
    val success: Boolean,
    val message: String? = null,
    val cufe: String? = null,
    val qr: String? = null,
    val fechaRecepcionDGI: String? = null,
    val nroProtocoloAutorizacion: String? = null,
    val alreadyIssued: Boolean = false,
    val numeroDocumentoFiscal: String? = null,
    val numeroControl: String? = null,
)

/**
 * Respuesta de rechazo del PAC/DGI (HTTP 502). `incidenciasFiscales` trae los
 * códigos DGI de 4 dígitos parseados y `reintentable` clasifica si un
 * reintento directo tiene sentido (Q4). Los campos del contrato van SIN
 * default para que se emitan siempre, independiente de la configuración
 * `encodeDefaults` del runtime.
 */
@Serializable
data class ElectronicInvoiceFailureResponse(
    val success: Boolean,
    val codigo: String,
    val mensaje: String,
    val reintentable: Boolean,
    val incidenciasFiscales: List<IncidenciaFiscal>,
)

/**
 * Resultado incierto (HTTP 409): el PAC pudo haber creado el documento;
 * no se puede afirmar fallo ni éxito y requiere conciliación.
 */
@Serializable
data class ElectronicInvoiceUncertainResponse(
    val success: Boolean,
    val codigo: String,
    val mensaje: String,
    val incierta: Boolean,
    val transaccionId: String?,
    val action: String,
)

/** Body inválido en la petición. */
@Serializable
data class ElectronicInvoiceBadRequestResponse(
    val error: String,
)

/**
 * Handler del envío manual de facturas al PAC.
 */
internal class ElectronicInvoiceHandlers(
    private val factory: ElectronicInvoiceProcessorFactory,
) {
    suspend fun enviar(call: ApplicationCall) =
        run {
            val ctx = call.resolveCompanyRequestContext() ?: return@run

            val invoiceId = call.parameters["invoiceId"]
            if (invoiceId == null) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ElectronicInvoiceBadRequestResponse(error = "Falta invoiceId en la URL"),
                )
                return@run
            }

            val database = ctx.connectDatabase()
            val processor = factory.forCountry(ctx.countryCode)

            when (val result = processor.processElectronicInvoice(database, invoiceId)) {
                is ElectronicInvoiceResult.Success -> call.respondSuccess(result)
                is ElectronicInvoiceResult.Failure -> call.respondFailure(result)
                is ElectronicInvoiceResult.NotApplicable -> call.respondNotApplicable(result)
                is ElectronicInvoiceResult.UnsupportedDocumentType -> call.respondUnsupported(result)
                is ElectronicInvoiceResult.AlreadyIssued -> call.respondAlreadyIssued(result)
                is ElectronicInvoiceResult.Uncertain -> call.respondUncertain(result)
            }
        }

    private suspend fun ApplicationCall.respondSuccess(result: ElectronicInvoiceResult.Success) =
        respond(
            HttpStatusCode.OK,
            ElectronicInvoiceResponse(
                success = true,
                cufe = result.cufe,
                qr = result.qr.orEmpty(),
                fechaRecepcionDGI = result.fechaRecepcionDGI.orEmpty(),
                nroProtocoloAutorizacion = result.nroProtocoloAutorizacion.orEmpty(),
            ),
        )

    private suspend fun ApplicationCall.respondFailure(result: ElectronicInvoiceResult.Failure) =
        respond(
            HttpStatusCode.BadGateway,
            ElectronicInvoiceFailureResponse(
                success = false,
                codigo = result.codigo,
                mensaje = result.mensaje,
                reintentable = result.reintentable ?: false,
                incidenciasFiscales = result.incidenciasFiscales,
            ),
        )

    private suspend fun ApplicationCall.respondNotApplicable(result: ElectronicInvoiceResult.NotApplicable) =
        respond(
            HttpStatusCode.OK,
            ElectronicInvoiceResponse(
                success = true,
                message = "Facturación electrónica no aplica para ${result.country}",
            ),
        )

    private suspend fun ApplicationCall.respondUnsupported(result: ElectronicInvoiceResult.UnsupportedDocumentType) =
        respond(
            HttpStatusCode.OK,
            ElectronicInvoiceResponse(
                success = true,
                message =
                    "Tipo de documento '${result.tipoDocumento}' no implementado " +
                        "en ${result.country} (FASE 1)",
            ),
        )

    private suspend fun ApplicationCall.respondAlreadyIssued(result: ElectronicInvoiceResult.AlreadyIssued) =
        respond(
            HttpStatusCode.OK,
            ElectronicInvoiceResponse(
                success = true,
                message =
                    "La factura ya posee numeración fiscal " +
                        "(${result.numeroDocumentoFiscal})",
                alreadyIssued = true,
                numeroDocumentoFiscal = result.numeroDocumentoFiscal,
                numeroControl = result.numeroControl,
                // Compat POS: en alreadyIssued el CUFE viaja por numeroControl.
                cufe = result.numeroControl,
            ),
        )

    // Resultado incierto: el PAC pudo haber creado el documento.
    // No se puede afirmar fallo ni éxito.
    private suspend fun ApplicationCall.respondUncertain(result: ElectronicInvoiceResult.Uncertain) =
        respond(
            HttpStatusCode.Conflict,
            ElectronicInvoiceUncertainResponse(
                success = false,
                codigo = result.codigo,
                mensaje = result.mensaje,
                incierta = true,
                transaccionId = result.transaccionId,
                action = "Requiere conciliación manual",
            ),
        )
}
