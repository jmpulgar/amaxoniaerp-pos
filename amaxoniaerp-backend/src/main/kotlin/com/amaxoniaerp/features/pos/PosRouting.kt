package com.amaxoniaerp.features.pos

import com.amaxoniaerp.core.database.DatabaseManager
import com.amaxoniaerp.core.tenant.resolveCompanyRequestContext
import com.amaxoniaerp.features.pos.data.FormasPagoRepository
import com.amaxoniaerp.features.pos.domain.FormaPagoResponse
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.authenticate
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route

private const val SECOND_DEFAULT_REGISTRATION_TYPE = 3

fun Route.posRouting(formasPagoRepository: FormasPagoRepository) {
    authenticate {
        route("/api/pos") {
            get("/formas-pago") { listarFormasPago(call, formasPagoRepository) }
        }
    }
}

private suspend fun listarFormasPago(
    call: ApplicationCall,
    formasPagoRepository: FormasPagoRepository,
) = run {
    val ctx = call.resolveCompanyRequestContext() ?: return@run

    val cajaId = call.request.queryParameters["cajaId"]?.takeIf { it.isNotBlank() }
    val tipoRegistro =
        call.request.queryParameters["tipoRegistro"]
            ?.split(',')
            ?.mapNotNull { it.trim().toIntOrNull() }
            ?: listOf(1, SECOND_DEFAULT_REGISTRATION_TYPE)

    val companyDb = DatabaseManager.connectToCompanyDb(ctx.countryCode, ctx.adminDb)
    val formasPago =
        formasPagoRepository.listFormasPago(
            database = companyDb,
            cajaId = cajaId,
            tipoRegistro = tipoRegistro,
        )

    call.respond(
        HttpStatusCode.OK,
        FormaPagoResponse(success = true, data = formasPago),
    )
}
