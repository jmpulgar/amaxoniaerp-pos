package com.amaxoniaerp.features.clients.route

import com.amaxoniaerp.core.tenant.connectDatabase
import com.amaxoniaerp.core.tenant.resolveCompanyRequestContext
import com.amaxoniaerp.features.clients.data.ClientsRepository
import com.amaxoniaerp.features.clients.domain.ClientsListResponse
import com.amaxoniaerp.features.clients.domain.CreateClientRequest
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

private const val DEFAULT_PAGE_LIMIT = 100
private const val MAX_PAGE_LIMIT = 1_000

fun Route.clientsRoutes(clientsRepository: ClientsRepository) {
    val handlers = ClientsHandlers(clientsRepository)

    authenticate {
        route("/clients") {
            get { handlers.listar(call) }
            get("/default") { handlers.clientePorDefecto(call) }
            get("/{id}") { handlers.detalle(call) }
            get("/{id}/sucursales") { handlers.sucursales(call) }
            post { handlers.crear(call) }
            put("/{id}") { handlers.actualizar(call) }
        }
    }
}

/**
 * Handlers de los endpoints de clientes. CRUD/query simple: `route -> repository`.
 */
internal class ClientsHandlers(
    private val clientsRepository: ClientsRepository,
) {
    suspend fun listar(call: ApplicationCall) =
        run {
            val ctx = call.resolveCompanyRequestContext() ?: return@run

            val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: DEFAULT_PAGE_LIMIT
            val offset = call.request.queryParameters["offset"]?.toLongOrNull() ?: 0L
            val search = call.request.queryParameters["search"]
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

            val companyDb = ctx.connectDatabase()
            val (clients, total) = clientsRepository.listClients(companyDb, limit, offset, search, includeTotal)
            call.respond(ClientsListResponse(data = clients, total = total))
        }

    suspend fun clientePorDefecto(call: ApplicationCall) =
        run {
            val ctx = call.resolveCompanyRequestContext() ?: return@run

            val companyDb = ctx.connectDatabase()
            val defaultClient = clientsRepository.getDefaultClient(companyDb, ctx.countryCode)
            if (defaultClient == null) {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to "Default client not configured"))
                return@run
            }

            call.respond(defaultClient)
        }

    suspend fun detalle(call: ApplicationCall) =
        run {
            val database = resolveDatabase(call) ?: return@run
            val id = call.requireClientId() ?: return@run

            val client = clientsRepository.getClientById(database, id)
            if (client == null) {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to "Client not found"))
                return@run
            }

            call.respond(client)
        }

    suspend fun sucursales(call: ApplicationCall) =
        run {
            val ctx = call.resolveCompanyRequestContext() ?: return@run
            val id = call.requireClientId() ?: return@run

            val companyDb = ctx.connectDatabase()
            val sucursales = clientsRepository.listClientSucursales(companyDb, ctx.countryCode, id)
            call.respond(sucursales)
        }

    suspend fun crear(call: ApplicationCall) =
        run {
            val ctx = call.resolveCompanyRequestContext() ?: return@run

            val request = call.receive<CreateClientRequest>()
            if (request.identification.isBlank() || request.name.isBlank()) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "RUC and Name are required"))
                return@run
            }

            val companyDb = ctx.connectDatabase()
            val client = clientsRepository.createClient(companyDb, ctx.countryCode, request)
            call.respond(HttpStatusCode.Created, client)
        }

    suspend fun actualizar(call: ApplicationCall) =
        run {
            val database = resolveDatabase(call) ?: return@run
            val id = call.requireClientId() ?: return@run

            val request = call.receive<CreateClientRequest>()
            if (request.identification.isBlank() || request.name.isBlank()) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "RUC and Name are required"))
                return@run
            }

            val client = clientsRepository.updateClient(database, id, request)
            if (client == null) {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to "Client not found"))
                return@run
            }

            call.respond(client)
        }

    private suspend fun resolveDatabase(call: ApplicationCall): Database? =
        run {
            val ctx = call.resolveCompanyRequestContext() ?: return@run null
            ctx.connectDatabase()
        }

    private suspend fun ApplicationCall.requireClientId(): String? =
        run {
            val id = parameters["id"]
            if (id == null) {
                respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid client id"))
                return@run null
            }
            id
        }
}
