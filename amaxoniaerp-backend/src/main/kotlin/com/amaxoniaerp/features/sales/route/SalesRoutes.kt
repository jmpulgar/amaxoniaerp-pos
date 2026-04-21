package com.amaxoniaerp.features.sales.route

import com.amaxoniaerp.core.database.DatabaseManager
import com.amaxoniaerp.features.auth.route.getAdminDb
import com.amaxoniaerp.features.auth.route.getCountryCode
import com.amaxoniaerp.features.sales.application.ProcessSaleUseCase
import com.amaxoniaerp.features.sales.domain.DuplicateInvoiceException
import com.amaxoniaerp.features.sales.domain.InsufficientStockException
import com.amaxoniaerp.features.sales.domain.InvalidSaleRequestException
import com.amaxoniaerp.features.sales.domain.ProcessSaleRequest
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import org.slf4j.LoggerFactory

fun Route.salesRoutes(processSaleUseCase: ProcessSaleUseCase) {
    val log = LoggerFactory.getLogger("SalesRoutes")

    authenticate {
        route("/api/pos/ventas") {
            post("/procesar") {
                val principal = call.principal<JWTPrincipal>()
                    ?: return@post call.respond(
                        HttpStatusCode.Unauthorized,
                        mapOf("error" to "Token inválido")
                    )

                val tokenType = principal.payload.getClaim("token_type").asString()
                if (tokenType != "company") {
                    return@post call.respond(
                        HttpStatusCode.Forbidden,
                        mapOf("error" to "Se requiere token de empresa")
                    )
                }

                val countryCode = principal.getCountryCode()
                    ?: return@post call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf("error" to "Falta country_code en token")
                    )

                val adminDb = principal.getAdminDb()
                    ?: return@post call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf("error" to "Falta admin_db en token")
                    )

                val request = call.receive<ProcessSaleRequest>()
                val companyDb = DatabaseManager.connectToCompanyDb(countryCode, adminDb)

                try {
                    val result = processSaleUseCase.execute(companyDb, countryCode, request)
                    call.respond(HttpStatusCode.Created, result)
                } catch (e: DuplicateInvoiceException) {
                    call.respond(HttpStatusCode.Conflict, mapOf("error" to (e.message ?: "Factura duplicada")))
                } catch (e: InsufficientStockException) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to (e.message ?: "Stock insuficiente")))
                } catch (e: InvalidSaleRequestException) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to (e.message ?: "Solicitud inválida")))
                } catch (e: Exception) {
                    log.error(
                        "Error processing POS sale. idCaja={} idCliente={}",
                        request.factura.idCaja,
                        request.factura.idCliente,
                        e
                    )
                    call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Error interno al procesar venta"))
                }
            }
        }
    }
}
