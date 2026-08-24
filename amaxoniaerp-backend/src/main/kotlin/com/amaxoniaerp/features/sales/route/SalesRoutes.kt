package com.amaxoniaerp.features.sales.route

import com.amaxoniaerp.core.tenant.connectDatabase
import com.amaxoniaerp.core.tenant.resolveCompanyRequestContext
import com.amaxoniaerp.features.sales.application.ProcessSaleUseCase
import com.amaxoniaerp.features.sales.domain.ProcessSaleRequest
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import org.slf4j.LoggerFactory

fun Route.salesRoutes(processSaleUseCase: ProcessSaleUseCase) {
    val handlers = SalesHandlers(processSaleUseCase)

    authenticate {
        route("/api/pos/ventas") {
            post("/procesar") { handlers.procesar(call) }
        }
    }
}

/**
 * Handler del endpoint de procesar venta POS. Resuelve el tenant por el seam
 * canónico, delega en el caso de uso y mapea los errores de dominio a HTTP.
 */
internal class SalesHandlers(
    private val processSaleUseCase: ProcessSaleUseCase,
) {
    private val log = LoggerFactory.getLogger("SalesRoutes")

    suspend fun procesar(call: ApplicationCall) =
        run {
            val ctx = call.resolveCompanyRequestContext() ?: return@run

            val request = call.receive<ProcessSaleRequest>()
            log.info(
                "Processing POS sale. country={} adminDb={} idCaja={} idCliente={} items={} pagos={} total={}",
                ctx.countryCode,
                ctx.adminDb,
                request.factura.idCaja,
                request.factura.idCliente,
                request.items.size,
                request.pagos.size,
                request.factura.totalTotalFactura,
            )
            val companyDb = ctx.connectDatabase()

            val result = processSaleUseCase.execute(companyDb, ctx.countryCode, request)
            log.info(
                "POS sale processed. country={} idFactura={} codFactura={} status={}",
                ctx.countryCode,
                result.idFactura,
                result.codFactura,
                result.codEstatus,
            )
            call.respond(HttpStatusCode.Created, result)
        }
}
