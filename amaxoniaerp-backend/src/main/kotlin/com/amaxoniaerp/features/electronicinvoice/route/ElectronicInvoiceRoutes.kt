package com.amaxoniaerp.features.electronicinvoice.route

import com.amaxoniaerp.core.database.DatabaseManager
import com.amaxoniaerp.features.auth.route.getAdminDb
import com.amaxoniaerp.features.auth.route.getCountryCode
import com.amaxoniaerp.features.electronicinvoice.application.ElectronicInvoiceProcessorFactory
import com.amaxoniaerp.features.electronicinvoice.domain.ElectronicInvoiceResult
import io.ktor.http.HttpStatusCode
import io.ktor.http.parameters
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import org.slf4j.LoggerFactory

/**
 * Endpoints REST para facturación electrónica.
 *
 * Permite enviar manualmente una factura existente al PAC. Este endpoint es
 * complementario a la integración automática en [ProcessSaleUseCase] y sirve
 * para reenvíos, reintentos o envíos diferidos.
 */
fun Route.electronicInvoiceRoutes(factory: ElectronicInvoiceProcessorFactory) {
    val log = LoggerFactory.getLogger("ElectronicInvoiceRoutes")

    authenticate {
        route("/api/facturacion-electronica") {
            /**
             * POST /api/facturacion-electronica/{invoiceId}/enviar
             *
             * Envía una factura existente al PAC para obtener CUFE/QR.
             * Si el país es VE, retorna 200 con "no aplicable".
             */
            post("/{invoiceId}/enviar") {
                val principal =
                    call.principal<JWTPrincipal>()
                        ?: return@post call.respond(
                            HttpStatusCode.Unauthorized,
                            mapOf("error" to "Token inválido"),
                        )

                val tokenType = principal.payload.getClaim("token_type").asString()
                if (tokenType != "company") {
                    return@post call.respond(
                        HttpStatusCode.Forbidden,
                        mapOf("error" to "Se requiere token de empresa"),
                    )
                }

                val countryCode =
                    principal.getCountryCode()
                        ?: return@post call.respond(
                            HttpStatusCode.BadRequest,
                            mapOf("error" to "Falta country_code en token"),
                        )

                val adminDb =
                    principal.getAdminDb()
                        ?: return@post call.respond(
                            HttpStatusCode.BadRequest,
                            mapOf("error" to "Falta admin_db en token"),
                        )

                val invoiceId =
                    call.parameters["invoiceId"]
                        ?: return@post call.respond(
                            HttpStatusCode.BadRequest,
                            mapOf("error" to "Falta invoiceId en la URL"),
                        )

                val database = DatabaseManager.connectToCompanyDb(countryCode, adminDb)
                val processor = factory.forCountry(countryCode)

                try {
                    when (val result = processor.processElectronicInvoice(database, invoiceId)) {
                        is ElectronicInvoiceResult.Success -> {
                            call.respond(
                                HttpStatusCode.OK,
                                mapOf(
                                    "success" to true,
                                    "cufe" to result.cufe,
                                    "qr" to (result.qr ?: ""),
                                    "fechaRecepcionDGI" to (result.fechaRecepcionDGI ?: ""),
                                    "nroProtocoloAutorizacion" to (result.nroProtocoloAutorizacion ?: ""),
                                ),
                            )
                        }

                        is ElectronicInvoiceResult.Failure -> {
                            call.respond(
                                HttpStatusCode.BadGateway,
                                mapOf(
                                    "success" to false,
                                    "codigo" to result.codigo,
                                    "mensaje" to result.mensaje,
                                ),
                            )
                        }

                        is ElectronicInvoiceResult.NotApplicable -> {
                            call.respond(
                                HttpStatusCode.OK,
                                mapOf(
                                    "success" to true,
                                    "message" to "Facturación electrónica no aplica para ${result.country}",
                                ),
                            )
                        }

                        is ElectronicInvoiceResult.UnsupportedDocumentType -> {
                            call.respond(
                                HttpStatusCode.OK,
                                mapOf(
                                    "success" to true,
                                    "message" to "Tipo de documento '${result.tipoDocumento}' no implementado en ${result.country} (FASE 1)",
                                ),
                            )
                        }

                        is ElectronicInvoiceResult.AlreadyIssued -> {
                            call.respond(
                                HttpStatusCode.OK,
                                mapOf(
                                    "success" to true,
                                    "message" to "La factura ya posee numeración fiscal (${result.numeroDocumentoFiscal})",
                                    "numeroDocumentoFiscal" to result.numeroDocumentoFiscal,
                                    "numeroControl" to (result.numeroControl ?: ""),
                                ),
                            )
                        }

                        is ElectronicInvoiceResult.Uncertain -> {
                            // Resultado incierto: el PAC pudo haber creado el
                            // documento. No se puede afirmar fallo ni éxito.
                            call.respond(
                                HttpStatusCode.Conflict,
                                mapOf(
                                    "success" to false,
                                    "codigo" to result.codigo,
                                    "mensaje" to result.mensaje,
                                    "incierta" to true,
                                    "transaccionId" to (result.transaccionId ?: ""),
                                    "action" to "Requiere conciliación manual",
                                ),
                            )
                        }
                    }
                } catch (e: Exception) {
                    log.error("Error procesando FE para factura {}", invoiceId, e)
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        mapOf("error" to "Error interno al procesar facturación electrónica"),
                    )
                }
            }
        }
    }
}
