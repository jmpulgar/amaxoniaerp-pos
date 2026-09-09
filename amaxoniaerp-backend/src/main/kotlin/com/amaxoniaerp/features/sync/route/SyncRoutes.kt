package com.amaxoniaerp.features.sync.route

import com.amaxoniaerp.core.tenant.connectDatabase
import com.amaxoniaerp.core.tenant.resolveCompanyRequestContext
import com.amaxoniaerp.features.items.data.ItemsRepository
import com.amaxoniaerp.features.items.data.listDepartments
import com.amaxoniaerp.features.sync.data.SyncCursorExpiredException
import com.amaxoniaerp.features.sync.data.SyncRepository
import com.amaxoniaerp.features.sync.domain.SimpleCatalogItemDto
import com.amaxoniaerp.features.sync.domain.SyncEntityType
import com.amaxoniaerp.features.sync.domain.SyncScope
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.authenticate
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route

private const val DEFAULT_PAGE_LIMIT = 500
private const val MAX_PAGE_LIMIT = 1_000
private const val ERR_INVALID_ENTITY = "Entidad de sync inválida"
private const val CODE_CURSOR_EXPIRED = "CURSOR_EXPIRED"

/**
 * Endpoints del sync incremental (/api/sync/v1, PLAN §3). Read-only e
 * idempotentes por construcción; el alcance viaja en los parámetros
 * `deptIds`/`branchIds` (ADR-008). Endpoints legacy intactos.
 */
fun Route.syncRoutes(
    syncRepository: SyncRepository,
    itemsRepository: ItemsRepository,
) {
    authenticate {
        route("/api/sync/v1") {
            get("/manifest") {
                run {
                    val ctx = call.resolveCompanyRequestContext() ?: return@run
                    val companyDb = ctx.connectDatabase()
                    call.respond(
                        syncRepository.manifest(
                            database = companyDb,
                            countryCode = ctx.countryCode,
                            scope = call.syncScope(),
                        ),
                    )
                }
            }

            /** Alias semántico del manifest para el barrido de reconciliación. */
            get("/reconcile") {
                run {
                    val ctx = call.resolveCompanyRequestContext() ?: return@run
                    val companyDb = ctx.connectDatabase()
                    call.respond(
                        syncRepository.manifest(
                            database = companyDb,
                            countryCode = ctx.countryCode,
                            scope = call.syncScope(),
                        ),
                    )
                }
            }

            get("/bootstrap/{entityType}") {
                run {
                    val ctx = call.resolveCompanyRequestContext() ?: return@run
                    val typeParam = call.parameters["entityType"]?.uppercase()
                    val type = SyncEntityType.entries.firstOrNull { it.wire == typeParam }
                    if (type == null) {
                        call.respond(HttpStatusCode.BadRequest, mapOf("error" to ERR_INVALID_ENTITY))
                        return@run
                    }
                    val limit = call.pageLimit()
                    val afterId = call.request.queryParameters["afterId"]
                    val companyDb = ctx.connectDatabase()
                    call.respond(
                        syncRepository.bootstrapPage(
                            database = companyDb,
                            countryCode = ctx.countryCode,
                            entityType = type,
                            scope = call.syncScope(),
                            afterId = afterId,
                            limit = limit,
                        ),
                    )
                }
            }

            get("/changes") {
                run {
                    val ctx = call.resolveCompanyRequestContext() ?: return@run
                    val cursor = call.request.queryParameters["cursor"]?.toLongOrNull() ?: 0L
                    val limit = call.pageLimit()
                    val companyDb = ctx.connectDatabase()
                    try {
                        call.respond(
                            syncRepository.changesPage(
                                database = companyDb,
                                countryCode = ctx.countryCode,
                                scope = call.syncScope(),
                                cursor = cursor,
                                limit = limit,
                            ),
                        )
                    } catch (e: SyncCursorExpiredException) {
                        call.respond(
                            HttpStatusCode.Gone,
                            mapOf(
                                "error" to
                                    mapOf(
                                        "code" to CODE_CURSOR_EXPIRED,
                                        "oldestRetainedChangeId" to e.oldestRetainedChangeId,
                                    ),
                            ),
                        )
                    }
                }
            }

            get("/scope-preview") {
                run {
                    val ctx = call.resolveCompanyRequestContext() ?: return@run
                    val entityParam = call.request.queryParameters["entity"]?.uppercase()
                    val type = SyncEntityType.entries.firstOrNull { it.wire == entityParam }
                    if (type == null) {
                        call.respond(HttpStatusCode.BadRequest, mapOf("error" to ERR_INVALID_ENTITY))
                        return@run
                    }
                    val companyDb = ctx.connectDatabase()
                    call.respond(
                        syncRepository.scopePreview(
                            database = companyDb,
                            countryCode = ctx.countryCode,
                            entityType = type,
                            scope = call.syncScope(),
                        ),
                    )
                }
            }

            get("/sucursales") {
                run {
                    val ctx = call.resolveCompanyRequestContext() ?: return@run
                    val companyDb = ctx.connectDatabase()
                    call.respond(syncRepository.sucursales(companyDb))
                }
            }

            get("/departamentos") {
                run {
                    val ctx = call.resolveCompanyRequestContext() ?: return@run
                    val companyDb = ctx.connectDatabase()
                    val items =
                        itemsRepository.listDepartments(companyDb).map { (id, nombre) ->
                            SimpleCatalogItemDto(id = id.toString(), nombre = nombre)
                        }
                    call.respond(items)
                }
            }
        }
    }
}

private fun ApplicationCall.syncScope(): SyncScope =
    SyncScope.fromParams(
        deptIds = request.queryParameters["deptIds"],
        branchIds = request.queryParameters["branchIds"],
    )

private fun ApplicationCall.pageLimit(): Int =
    request.queryParameters["limit"]?.toIntOrNull()?.coerceIn(1, MAX_PAGE_LIMIT) ?: DEFAULT_PAGE_LIMIT
