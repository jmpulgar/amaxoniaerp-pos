package com.amaxoniaerp.features.geography.route

import com.amaxoniaerp.core.tenant.connectDatabase
import com.amaxoniaerp.core.tenant.resolveCompanyRequestContext
import com.amaxoniaerp.features.geography.data.GeographyRepository
import com.amaxoniaerp.features.geography.domain.AddressLevelsListResponse
import com.amaxoniaerp.features.geography.domain.CatalogListResponse
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.authenticate
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route

private const val DEFAULT_PAGE_LIMIT = 100
private const val MAX_PAGE_LIMIT = 1_000
private const val ADDRESS_LEVEL_THREE = 3

fun Route.geographyRoutes(geographyRepository: GeographyRepository) {
    val handlers = GeographyHandlers(geographyRepository)

    authenticate {
        get("/countries") { handlers.listarPaises(call) }
        route("/address-levels") {
            get("/{level}") { handlers.listarNivelesDireccion(call) }
        }
    }
}

/**
 * Handlers de los endpoints de geografía (catálogos de solo lectura).
 */
internal class GeographyHandlers(
    private val geographyRepository: GeographyRepository,
) {
    suspend fun listarPaises(call: ApplicationCall) =
        run {
            val ctx = call.resolveCompanyRequestContext() ?: return@run

            val paging = call.resolvePaging() ?: return@run

            val companyDb = ctx.connectDatabase()
            val (countries, total) =
                geographyRepository.listCatalog(
                    database = companyDb,
                    tableName = "paises",
                    limit = paging.limit,
                    offset = paging.offset,
                    includeTotal = paging.includeTotal,
                )
            call.respond(CatalogListResponse(data = countries, total = total))
        }

    suspend fun listarNivelesDireccion(call: ApplicationCall) =
        run {
            val ctx = call.resolveCompanyRequestContext() ?: return@run

            val level = call.parameters["level"]?.toIntOrNull()
            if (level == null) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid address level"))
                return@run
            }

            val tableName =
                when (level) {
                    1 -> "direccion_nivel1"
                    2 -> "direccion_nivel2"
                    ADDRESS_LEVEL_THREE -> "direccion_nivel3"
                    else -> null
                }
            if (tableName == null) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid address level"))
                return@run
            }

            val paging = call.resolvePaging() ?: return@run

            val companyDb = ctx.connectDatabase()
            val (levels, total) =
                geographyRepository.listAddressLevels(
                    database = companyDb,
                    tableName = tableName,
                    limit = paging.limit,
                    offset = paging.offset,
                    includeTotal = paging.includeTotal,
                )
            call.respond(AddressLevelsListResponse(data = levels, total = total))
        }
}

private data class GeographyPaging(
    val limit: Int,
    val offset: Long,
    val includeTotal: Boolean,
)

private suspend fun ApplicationCall.resolvePaging(): GeographyPaging? =
    run {
        val limit = request.queryParameters["limit"]?.toIntOrNull() ?: DEFAULT_PAGE_LIMIT
        val offset = request.queryParameters["offset"]?.toLongOrNull() ?: 0L
        val includeTotalParam = request.queryParameters["includeTotal"]
        val includeTotal = includeTotalParam?.toBooleanStrictOrNull() ?: true

        if (limit <= 0 || limit > MAX_PAGE_LIMIT || offset < 0) {
            respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid pagination parameters"))
            return@run null
        }
        if (includeTotalParam != null && includeTotalParam.toBooleanStrictOrNull() == null) {
            respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid includeTotal parameter"))
            return@run null
        }
        GeographyPaging(limit = limit, offset = offset, includeTotal = includeTotal)
    }
