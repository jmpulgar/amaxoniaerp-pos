package com.amaxoniaerp.features.mesas

import com.amaxoniaerp.core.database.DatabaseManager
import com.amaxoniaerp.features.mesas.data.CuentaMesaRepository
import com.amaxoniaerp.features.mesas.data.MesasRepository
import com.amaxoniaerp.features.mesas.data.SesionMesaRepository
import com.amaxoniaerp.features.mesas.domain.CrearCuentaRequest
import com.amaxoniaerp.features.mesas.domain.CuentaCreadaResponse
import com.amaxoniaerp.features.mesas.domain.CuentaMesaResponse
import com.amaxoniaerp.features.mesas.domain.CuentaMesaResult
import com.amaxoniaerp.features.mesas.domain.CuentasMesaListResponse
import com.amaxoniaerp.features.mesas.domain.MarcarCuentaFacturadaRequest
import com.amaxoniaerp.features.mesas.domain.MarcarCuentaFacturadaResponse
import com.amaxoniaerp.features.mesas.domain.SesionMesaResult
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import org.slf4j.LoggerFactory

private const val ERR_SESSION_SCOPE = "La sesión no pertenece a esa mesa"
private const val ERR_UNEXPECTED = "Respuesta inesperada"
private const val ERR_LIST_ACCOUNTS = "No se pudieron listar las cuentas"
private const val ERR_ACCOUNT_FINAL_STATE = "La sesión no admite cuentas (estado final)"
private const val ERR_SELECTED_ORDER_BALANCE = "Un pedido seleccionado no existe, no está entregado o ya no tiene saldo"
private const val ERR_CREATE_ACCOUNT = "No se pudo crear la cuenta"
private const val ERR_GET_ACCOUNT = "No se pudo obtener la cuenta"
private const val ERR_CANCEL_ACCOUNT = "No se pudo cancelar la cuenta"

/**
 * Cuenta de mesa y división para el POS.
 *
 * Endpoints (colgados del path de sesión):
 *
 * - `GET    .../sesiones/{sesionId}/cuenta?cajaId=`                                     lista todas las cuentas.
 * - `GET    .../sesiones/{sesionId}/cuenta/{cuentaId}?cajaId=`                          detalle de una cuenta.
 * - `POST   .../sesiones/{sesionId}/cuenta?cajaId=` crear cuenta
 *   (completa o división).
 * - `POST   .../sesiones/{sesionId}/cuenta/{cuentaId}/cancelar?cajaId=`                 cancelar cuenta sin pagar.
 * - `POST   .../sesiones/{sesionId}/cuenta/{cuentaId}/marcar-facturada?cajaId=`         confirmar facturación.
 *
 * Endpoints de solicitud de cuenta (mutan el estado de la sesión):
 *
 * - `POST   .../sesiones/{sesionId}/solicitar-cuenta?cajaId=`                           sesión -> CUENTA_SOLICITADA.
 * - `POST   .../sesiones/{sesionId}/cancelar-solicitud-cuenta?cajaId=`                 revierte a ABIERTA.
 *
 * El POS actual envía el contexto `cuenta_mesa` al endpoint estándar de procesar venta, que
 * confirma factura, cantidades y cierre en una transacción. `marcar-facturada` se conserva
 * para clientes anteriores y continúa siendo idempotente.
 */
fun Route.cuentaMesaRouting(
    cuentaMesaRepository: CuentaMesaRepository,
    sesionMesaRepository: SesionMesaRepository,
    mesasRepository: MesasRepository,
) {
    val log = LoggerFactory.getLogger("CuentaMesaRouting")

    authenticate {
        route("/api/pos/areas/{areaId}/mesas/{mesaId}/sesiones/{sesionId}") {
            /**
             * Solicita la cuenta: transiciona la sesión a `CUENTA_SOLICITADA`. Reversible.
             */
            post("solicitar-cuenta") {
                val tri = call.extractRoutingIds() ?: return@post
                try {
                    val database = DatabaseManager.connectToCompanyDb(tri.ctx.countryCode, tri.ctx.adminDb)
                    if (!call.ensureCuentaScope(cuentaMesaRepository, mesasRepository, database, tri)) return@post
                    val result = sesionMesaRepository.solicitarCuenta(database, tri.sesionId)
                    respondSesionMutacion(call, log, result, okMessage = "Cuenta solicitada")
                } catch (e: Exception) {
                    log.error("Error solicitando cuenta. adminDb={} sesionId={}", tri.ctx.adminDb, tri.sesionId, e)
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        mapOf("error" to "No se pudo solicitar la cuenta"),
                    )
                }
            }

            /**
             * Cancela la solicitud de cuenta: revierte `CUENTA_SOLICITADA` -> `ABIERTA`.
             */
            post("cancelar-solicitud-cuenta") {
                val tri = call.extractRoutingIds() ?: return@post
                try {
                    val database = DatabaseManager.connectToCompanyDb(tri.ctx.countryCode, tri.ctx.adminDb)
                    if (!call.ensureCuentaScope(cuentaMesaRepository, mesasRepository, database, tri)) return@post
                    val result = sesionMesaRepository.cancelarSolicitudCuenta(database, tri.sesionId)
                    respondSesionMutacion(call, log, result, okMessage = "Solicitud de cuenta cancelada")
                } catch (e: Exception) {
                    log.error("Error cancelando solicitud. adminDb={} sesionId={}", tri.ctx.adminDb, tri.sesionId, e)
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        mapOf("error" to "No se pudo cancelar la solicitud de cuenta"),
                    )
                }
            }

            route("cuenta") {
                /**
                 * Lista todas las cuentas de la sesión (incluye PAGADAS/CANCELADAS para auditoría).
                 */
                get {
                    val tri = call.extractRoutingIds() ?: return@get
                    try {
                        val database = DatabaseManager.connectToCompanyDb(tri.ctx.countryCode, tri.ctx.adminDb)
                        if (!call.ensureCuentaScope(cuentaMesaRepository, mesasRepository, database, tri)) return@get
                        val result = cuentaMesaRepository.listarCuentas(database, tri.sesionId, tri.mesaId)
                        when (result) {
                            is CuentaMesaResult.Listada ->
                                call.respond(
                                    HttpStatusCode.OK,
                                    CuentasMesaListResponse(success = true, sesionMesaId = tri.sesionId, data = result.cuentas),
                                )

                            CuentaMesaResult.SesionNoPerteneceMesa ->
                                call.respond(HttpStatusCode.NotFound, mapOf("error" to ERR_SESSION_SCOPE))

                            else -> call.respond(HttpStatusCode.InternalServerError, mapOf("error" to ERR_UNEXPECTED))
                        }
                    } catch (e: Exception) {
                        log.error("Error listando cuentas. adminDb={} sesionId={}", tri.ctx.adminDb, tri.sesionId, e)
                        call.respond(HttpStatusCode.InternalServerError, mapOf("error" to ERR_LIST_ACCOUNTS))
                    }
                }

                /**
                 * Crea una cuenta completa (`{incluir_todo_pendiente:true}`) o una división
                 * (`{items:[{pedido_mesa_id, cantidad}], incluir_todo_pendiente:false}`).
                 */
                post {
                    val tri = call.extractRoutingIds() ?: return@post
                    val body = call.receive<CrearCuentaRequest>()
                    try {
                        val database = DatabaseManager.connectToCompanyDb(tri.ctx.countryCode, tri.ctx.adminDb)
                        if (!call.ensureCuentaScope(cuentaMesaRepository, mesasRepository, database, tri)) return@post
                        val result = cuentaMesaRepository.crear(database, tri.sesionId, tri.mesaId, body)
                        when (result) {
                            is CuentaMesaResult.Creada ->
                                call.respond(
                                    HttpStatusCode.Created,
                                    CuentaCreadaResponse(success = true, sesionMesaId = tri.sesionId, data = result.cuenta),
                                )

                            CuentaMesaResult.SesionNoPerteneceMesa ->
                                call.respond(HttpStatusCode.NotFound, mapOf("error" to ERR_SESSION_SCOPE))

                            CuentaMesaResult.SesionNoActiva ->
                                call.respond(HttpStatusCode.Conflict, mapOf("error" to ERR_ACCOUNT_FINAL_STATE))

                            CuentaMesaResult.CantidadSuperaSaldo ->
                                call.respond(
                                    HttpStatusCode.BadRequest,
                                    mapOf("error" to "La cantidad solicitada supera el saldo pendiente del pedido"),
                                )

                            CuentaMesaResult.PedidoNoEncontrado ->
                                call.respond(
                                    HttpStatusCode.BadRequest,
                                    mapOf("error" to ERR_SELECTED_ORDER_BALANCE),
                                )

                            CuentaMesaResult.SinItemsParaCrear ->
                                call.respond(
                                    HttpStatusCode.BadRequest,
                                    mapOf("error" to "No hay pedidos entregados pendientes de facturar"),
                                )

                            CuentaMesaResult.PedidosPendientesImpidenPago ->
                                call.respond(
                                    HttpStatusCode.Conflict,
                                    mapOf("error" to "Hay pedidos pendientes en cocina que impiden facturar"),
                                )

                            else -> call.respond(HttpStatusCode.InternalServerError, mapOf("error" to ERR_CREATE_ACCOUNT))
                        }
                    } catch (e: Exception) {
                        log.error("Error creando cuenta. adminDb={} sesionId={}", tri.ctx.adminDb, tri.sesionId, e)
                        call.respond(HttpStatusCode.InternalServerError, mapOf("error" to ERR_CREATE_ACCOUNT))
                    }
                }

                route("{cuentaId}") {
                    /**
                     * Detalle de una cuenta.
                     */
                    get {
                        val tri = call.extractRoutingIds() ?: return@get
                        val cuentaId = call.requireCuentaId() ?: return@get
                        try {
                            val database = DatabaseManager.connectToCompanyDb(tri.ctx.countryCode, tri.ctx.adminDb)
                            if (!call.ensureCuentaScope(cuentaMesaRepository, mesasRepository, database, tri)) return@get
                            val result = cuentaMesaRepository.obtenerCuenta(database, tri.sesionId, tri.mesaId, cuentaId)
                            when (result) {
                                is CuentaMesaResult.Creada ->
                                    call.respond(
                                        HttpStatusCode.OK,
                                        CuentaCreadaResponse(success = true, sesionMesaId = tri.sesionId, data = result.cuenta),
                                    )

                                CuentaMesaResult.SesionNoPerteneceMesa ->
                                    call.respond(HttpStatusCode.NotFound, mapOf("error" to ERR_SESSION_SCOPE))

                                CuentaMesaResult.CuentaNoEncontrada ->
                                    call.respond(HttpStatusCode.NotFound, mapOf("error" to "Cuenta no encontrada"))

                                else -> call.respond(HttpStatusCode.InternalServerError, mapOf("error" to ERR_UNEXPECTED))
                            }
                        } catch (e: Exception) {
                            log.error("Error obteniendo cuenta. adminDb={} cuentaId={}", tri.ctx.adminDb, cuentaId, e)
                            call.respond(HttpStatusCode.InternalServerError, mapOf("error" to ERR_GET_ACCOUNT))
                        }
                    }

                    /**
                     * Cancela una cuenta ACTIVA sin facturar. Libera los saldos asociados.
                     */
                    post("cancelar") {
                        val tri = call.extractRoutingIds() ?: return@post
                        val cuentaId = call.requireCuentaId() ?: return@post
                        try {
                            val database = DatabaseManager.connectToCompanyDb(tri.ctx.countryCode, tri.ctx.adminDb)
                            if (!call.ensureCuentaScope(cuentaMesaRepository, mesasRepository, database, tri)) return@post
                            val result = cuentaMesaRepository.cancelarCuenta(database, tri.sesionId, tri.mesaId, cuentaId)
                            when (result) {
                                is CuentaMesaResult.Creada ->
                                    call.respond(
                                        HttpStatusCode.OK,
                                        CuentaCreadaResponse(success = true, sesionMesaId = tri.sesionId, data = result.cuenta),
                                    )

                                CuentaMesaResult.SesionNoPerteneceMesa ->
                                    call.respond(HttpStatusCode.NotFound, mapOf("error" to ERR_SESSION_SCOPE))

                                CuentaMesaResult.CuentaNoEncontrada ->
                                    call.respond(HttpStatusCode.NotFound, mapOf("error" to "Cuenta no encontrada"))

                                CuentaMesaResult.CuentaNoActiva ->
                                    call.respond(
                                        HttpStatusCode.Conflict,
                                        mapOf("error" to "La cuenta ya no está activa y no se puede cancelar"),
                                    )

                                else -> call.respond(HttpStatusCode.InternalServerError, mapOf("error" to ERR_CANCEL_ACCOUNT))
                            }
                        } catch (e: Exception) {
                            log.error("Error cancelando cuenta. adminDb={} cuentaId={}", tri.ctx.adminDb, cuentaId, e)
                            call.respond(HttpStatusCode.InternalServerError, mapOf("error" to ERR_CANCEL_ACCOUNT))
                        }
                    }

                    /**
                     * Marca una cuenta como facturada con éxito. Idempotente en `idempotencyKey`:
                     * - Si el intento ya está CONFIRMED → 200 OK con la cuenta y `sesion_cerrada`
                     *   reflejada.
                     * - Si está SENDING (mismo-intento) → 409 Conflict.
                     * - Si no existe o está FAILED → aplica cambios atómicos y responde 200.
                     *
                     * Si tras marcar NO quedan cuentas activas ni pedidos pendientes de entrega,
                     * la sesión se transiciona a `CERRADA_PAGADA` y se libera la mesa.
                     */
                    post("marcar-facturada") {
                        val tri = call.extractRoutingIds() ?: return@post
                        val cuentaId = call.requireCuentaId() ?: return@post
                        val body = call.receive<MarcarCuentaFacturadaRequest>()
                        try {
                            val database = DatabaseManager.connectToCompanyDb(tri.ctx.countryCode, tri.ctx.adminDb)
                            if (!call.ensureCuentaScope(cuentaMesaRepository, mesasRepository, database, tri)) return@post
                            val result =
                                cuentaMesaRepository.marcarFacturada(
                                    database = database,
                                    sesionId = tri.sesionId,
                                    mesaId = tri.mesaId,
                                    cuentaId = cuentaId,
                                    idempotencyKey = body.idempotencyKey,
                                    idFactura = body.idFactura,
                                    codFactura = body.codFactura,
                                )
                            respondMarcarFacturada(call, log, result, tri.sesionId, cuentaId)
                        } catch (e: Exception) {
                            log.error(
                                "Error marcando cuenta facturada. adminDb={} cuentaId={} key={}",
                                tri.ctx.adminDb,
                                cuentaId,
                                body.idempotencyKey,
                                e,
                            )
                            call.respond(
                                HttpStatusCode.InternalServerError,
                                mapOf("error" to "No se pudo confirmar la facturación de la cuenta"),
                            )
                        }
                    }
                }
            }
        }
    }
}

// ============================================================
// Helpers
// ============================================================

private data class CuentaRoutingIds(
    val ctx: PosCompanyContext,
    val cajaId: String,
    val areaId: Int,
    val mesaId: Int,
    val sesionId: Int,
)

private suspend fun ApplicationCall.extractRoutingIds(): CuentaRoutingIds? {
    val ctx = resolvePosContext() ?: return null
    val cajaId = requireCajaId() ?: return null
    val areaId = requireAreaId() ?: return null
    val mesaId = requireMesaId() ?: return null
    val sesionId = requireSesionId() ?: return null
    return CuentaRoutingIds(ctx = ctx, cajaId = cajaId, areaId = areaId, mesaId = mesaId, sesionId = sesionId)
}

private suspend fun ApplicationCall.ensureCuentaScope(
    repository: CuentaMesaRepository,
    mesasRepository: MesasRepository,
    database: org.jetbrains.exposed.sql.Database,
    ids: CuentaRoutingIds,
): Boolean {
    val cajaScope =
        resolveScopeOrRespond(mesasRepository, database, ids.ctx, ids.cajaId)
            ?: return false
    val valid =
        repository.scopeValido(
            database,
            ids.sesionId,
            ids.cajaId,
            cajaScope.sucursalId,
            ids.areaId,
            ids.mesaId,
        )
    if (!valid) {
        respond(HttpStatusCode.NotFound, mapOf("error" to "La sesión no pertenece a la caja, área o mesa indicadas"))
    }
    return valid
}

private suspend fun ApplicationCall.requireCuentaId(): Int? {
    val v = parameters["cuentaId"]?.toIntOrNull()
    if (v == null || v <= 0) {
        respond(HttpStatusCode.BadRequest, mapOf("error" to "El identificador de cuenta es inválido"))
        return null
    }
    return v
}

private suspend fun respondSesionMutacion(
    call: ApplicationCall,
    log: org.slf4j.Logger,
    result: SesionMesaResult,
    okMessage: String,
) {
    when (result) {
        is SesionMesaResult.Closed ->
            call.respond(
                HttpStatusCode.OK,
                mapOf("success" to true, "mensaje" to okMessage, "sesion" to result.sesion),
            )

        SesionMesaResult.SesionNoEncontrada ->
            call.respond(HttpStatusCode.NotFound, mapOf("error" to "Sesión no encontrada"))

        SesionMesaResult.SesionYaFinalizada ->
            call.respond(
                HttpStatusCode.Conflict,
                mapOf("error" to "La sesión no admite esta operación (estado final)"),
            )

        else -> {
            log.warn("Respuesta no esperada al solicitar/cancelar cuenta: {}", result)
            call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "No se pudo modificar la sesión"))
        }
    }
}

private suspend fun respondMarcarFacturada(
    call: ApplicationCall,
    log: org.slf4j.Logger,
    result: CuentaMesaResult,
    sesionId: Int,
    cuentaId: Int,
) {
    when (result) {
        is CuentaMesaResult.Facturada ->
            call.respond(
                HttpStatusCode.OK,
                MarcarCuentaFacturadaResponse(
                    success = true,
                    sesionMesaId = sesionId,
                    cuentaMesaId = cuentaId,
                    data = result.cuenta,
                    sesionCerrada = result.sesionCerrada,
                ),
            )

        CuentaMesaResult.IdempotenciaDuplicada ->
            // 200 OK + flag `success=true` pero con detalle: el POS debe leer que ya estaba
            // confirmado y no repetir el `procesar venta`. El campo `error` contiene el mensaje.
            call.respond(
                HttpStatusCode.OK,
                MarcarCuentaFacturadaResponse(
                    success = true,
                    sesionMesaId = sesionId,
                    cuentaMesaId = cuentaId,
                    data = CuentaMesaResponse(),
                    sesionCerrada = false,
                    error = "Intento idempotente ya confirmado",
                ),
            )

        CuentaMesaResult.SesionNoPerteneceMesa ->
            call.respond(HttpStatusCode.NotFound, mapOf("error" to ERR_SESSION_SCOPE))

        CuentaMesaResult.CuentaNoEncontrada ->
            call.respond(HttpStatusCode.NotFound, mapOf("error" to "Cuenta no encontrada"))

        CuentaMesaResult.CuentaNoActiva ->
            call.respond(
                HttpStatusCode.Conflict,
                mapOf("error" to "La cuenta ya no está activa"),
            )

        CuentaMesaResult.SesionNoActiva ->
            call.respond(
                HttpStatusCode.Conflict,
                mapOf("error" to "La sesión no admite esta operación"),
            )

        else -> {
            log.warn("Respuesta no esperada al marcar facturada: {}", result)
            call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "No se pudo confirmar la facturación"))
        }
    }
}
