package com.amaxoniaerp.features.promotions.route

import com.amaxoniaerp.core.database.DatabaseManager
import com.amaxoniaerp.core.tenant.resolveCompanyRequestContext
import com.amaxoniaerp.features.promotions.data.PromotionsRepository
import com.amaxoniaerp.features.promotions.domain.PromotionsListResponse
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.authenticate
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route

fun Route.promotionsRoutes(repository: PromotionsRepository) {
    authenticate {
        route("/promociones") {
            get { listarPromociones(call, repository) }
        }
    }
}

private suspend fun listarPromociones(
    call: ApplicationCall,
    repository: PromotionsRepository,
) = run {
    val ctx = call.resolveCompanyRequestContext() ?: return@run
    val companyDb = DatabaseManager.connectToCompanyDb(ctx.countryCode, ctx.adminDb)
    call.respond(PromotionsListResponse(repository.listPromotions(companyDb)))
}
