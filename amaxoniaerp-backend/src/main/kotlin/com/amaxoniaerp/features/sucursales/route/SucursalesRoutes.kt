package com.amaxoniaerp.features.sucursales.route

import com.amaxoniaerp.core.tenant.connectDatabase
import com.amaxoniaerp.core.tenant.resolveCompanyRequestContext
import com.amaxoniaerp.features.sucursales.data.SucursalRepository
import com.amaxoniaerp.features.sucursales.domain.SaveSucursalRequest
import com.amaxoniaerp.features.sucursales.domain.SucursalDetailResponse
import com.amaxoniaerp.features.sucursales.domain.SucursalesListResponse
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import org.jetbrains.exposed.sql.Database
import org.slf4j.LoggerFactory

fun Route.sucursalesRoutes(sucursalRepository: SucursalRepository) {
    val handlers = SucursalesHandlers(sucursalRepository)

    authenticate {
        route("/api/sucursales") {
            get { handlers.listar(call) }
            get("/{id}") { handlers.detalle(call) }
            post { handlers.crear(call) }
            put("/{id}") { handlers.actualizar(call) }
        }
    }
}

internal class SucursalesHandlers(
    private val sucursalRepository: SucursalRepository,
) {
    private val log = LoggerFactory.getLogger("SucursalesRoutes")

    suspend fun listar(call: ApplicationCall) =
        run {
            val database = call.resolveDatabase() ?: return@run
            val list = sucursalRepository.listSucursales(database)
            call.respond(HttpStatusCode.OK, SucursalesListResponse(data = list))
        }

    suspend fun detalle(call: ApplicationCall) =
        run {
            val database = call.resolveDatabase() ?: return@run
            val id = call.requireIntParam("id", "ID de sucursal inválido") ?: return@run
            val sucursal = sucursalRepository.getSucursalById(database, id)
            if (sucursal == null) {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to "Sucursal no encontrada"))
                return@run
            }
            call.respond(HttpStatusCode.OK, SucursalDetailResponse(data = sucursal))
        }

    suspend fun crear(call: ApplicationCall) =
        run {
            val database = call.resolveDatabase() ?: return@run
            val request =
                runCatching { call.receive<SaveSucursalRequest>() }.getOrElse {
                    log.warn("Payload de sucursal inválido", it)
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Payload inválido"))
                    return@run
                }

            if (request.sucursal.isBlank()) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "El nombre de la sucursal es requerido"))
                return@run
            }

            val created = sucursalRepository.createSucursal(database, request)
            call.respond(HttpStatusCode.Created, SucursalDetailResponse(data = created))
        }

    suspend fun actualizar(call: ApplicationCall) =
        run {
            val database = call.resolveDatabase() ?: return@run
            val id = call.requireIntParam("id", "ID de sucursal inválido") ?: return@run
            val request =
                runCatching { call.receive<SaveSucursalRequest>() }.getOrElse {
                    log.warn("Payload de sucursal inválido", it)
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Payload inválido"))
                    return@run
                }

            if (request.sucursal.isBlank()) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "El nombre de la sucursal es requerido"))
                return@run
            }

            val updated = sucursalRepository.updateSucursal(database, id, request)
            if (updated == null) {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to "Sucursal no encontrada"))
                return@run
            }
            call.respond(HttpStatusCode.OK, SucursalDetailResponse(data = updated))
        }
}

private suspend fun ApplicationCall.resolveDatabase(): Database? {
    val ctx = resolveCompanyRequestContext() ?: return null
    return ctx.connectDatabase()
}

private suspend fun ApplicationCall.requireIntParam(
    name: String,
    errorMessage: String,
): Int? =
    run {
        val value = parameters[name]?.toIntOrNull()
        if (value == null) {
            respond(HttpStatusCode.BadRequest, mapOf("error" to errorMessage))
            return@run null
        }
        value
    }
