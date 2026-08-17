package com.amaxoniaerp.features.clients.route

import com.amaxoniaerp.core.database.DatabaseManager
import com.amaxoniaerp.core.tenant.resolveCompanyRequestContext
import com.amaxoniaerp.features.clients.data.ClientTypesRepository
import com.amaxoniaerp.features.clients.domain.ClientTypesListResponse
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.authenticate
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

private const val DEFAULT_PAGE_LIMIT = 100
private const val MAX_PAGE_LIMIT = 1_000

fun Route.clientTypesRoutes(clientTypesRepository: ClientTypesRepository) {
    authenticate {
        get("/client-types") { listarClientTypes(call, clientTypesRepository) }
    }
}

private suspend fun listarClientTypes(
    call: ApplicationCall,
    clientTypesRepository: ClientTypesRepository,
) = run {
    val ctx = call.resolveCompanyRequestContext() ?: return@run

    val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: DEFAULT_PAGE_LIMIT
    val offset = call.request.queryParameters["offset"]?.toLongOrNull() ?: 0L
    val includeTotalParam = call.request.queryParameters["includeTotal"]
    val includeTotal = includeTotalParam?.toBooleanStrictOrNull() ?: true

    if (limit <= 0 || limit > MAX_PAGE_LIMIT || offset < 0) {
        call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid pagination parameters"))
        return@run
    }
    if (includeTotalParam != null && includeTotalParam.toBooleanStrictOrNull() == null) {
        call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid includeTotal parameter"))
        return@run
    }

    val companyDb = DatabaseManager.connectToCompanyDb(ctx.countryCode, ctx.adminDb)
    val (types, total) =
        clientTypesRepository.listClientTypes(
            database = companyDb,
            limit = limit,
            offset = offset,
            includeTotal = includeTotal,
        )
    call.respond(ClientTypesListResponse(data = types, total = total))
}
