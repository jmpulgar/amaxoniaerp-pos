package com.amaxoniaerp.features.mesas

import com.amaxoniaerp.core.database.DatabaseManager
import com.amaxoniaerp.features.mesas.data.CuentaMesaRepository
import com.amaxoniaerp.features.mesas.data.CuentaScope
import com.amaxoniaerp.features.mesas.data.MarcarFacturadaCommand
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
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import org.slf4j.LoggerFactory

private const val ERR_SESSION_SCOPE = "La sesiÃ³n no pertenece a esa mesa"
private const val ERR_UNEXPECTED = "Respuesta inesperada"
private const val ERR_ACCOUNT_FINAL_STATE = "La sesiÃ³n no admite cuentas (estado final)"
private const val ERR_SELECTED_ORDER_BALANCE =
    "Un pedido seleccionado no existe, no estÃ¡ entregado o ya no tiene saldo"
private const val ERR_CREATE_ACCOUNT = "No se pudo crear la cuenta"
private const val ERR_CANCEL_ACCOUNT = "No se pudo cancelar la cuenta"
private const val ERR_ACCOUNT_NOT_ACTIVE = "La cuenta ya no estÃ¡ activa y no se puede cancelar"

internal data class CuentaRoutingIds(
    val ctx: PosCompanyContext,
    val cajaId: String,
    val areaId: Int,
    val mesaId: Int,
    val sesionId: Int,
)

/**
 * Handlers de los endpoints de cuenta de mesa. Cada endpoint valida contexto y
 * parÃ¡metros, ejecuta la operaciÃ³n del repositorio y mapea el resultado a HTTP.
 */
internal class CuentaMesaHandlers(
    private val cuentaMesaRepository: CuentaMesaRepository,
    private val sesionMesaRepository: SesionMesaRepository,
    private val mesasRepository: MesasRepository,
) {
    private val log = LoggerFactory.getLogger("CuentaMesaRouting")

    /** Solicita o cancela la solicitud de cuenta de la sesiÃ³n. */
    suspend fun mutarSolicitudCuenta(
        call: ApplicationCall,
        solicitar: Boolean,
    ) = run {
        val tri = call.extractRoutingIds() ?: return@run
        val database = DatabaseManager.connectToCompanyDb(tri.ctx.countryCode, tri.ctx.adminDb)
        if (!call.ensureCuentaScope(database, tri)) return@run
        val result =
            if (solicitar) {
                sesionMesaRepository.solicitarCuenta(database, tri.sesionId)
            } else {
                sesionMesaRepository.cancelarSolicitudCuenta(database, tri.sesionId)
            }
        val okMessage = if (solicitar) "Cuenta solicitada" else "Solicitud de cuenta cancelada"
        respondSesionMutacion(call, result, okMessage)
    }

    suspend fun listar(call: ApplicationCall) =
        run {
            val tri = call.extractRoutingIds() ?: return@run
            val database = DatabaseManager.connectToCompanyDb(tri.ctx.countryCode, tri.ctx.adminDb)
            if (!call.ensureCuentaScope(database, tri)) return@run
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
            log.debug("Cuentas listadas. adminDb={} sesionId={}", tri.ctx.adminDb, tri.sesionId)
        }

    suspend fun crear(call: ApplicationCall) =
        run {
            val tri = call.extractRoutingIds() ?: return@run
            val body = call.receive<CrearCuentaRequest>()
            val database = DatabaseManager.connectToCompanyDb(tri.ctx.countryCode, tri.ctx.adminDb)
            if (!call.ensureCuentaScope(database, tri)) return@run
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
            log.debug("Cuenta creada. adminDb={} sesionId={}", tri.ctx.adminDb, tri.sesionId)
        }

    suspend fun detalle(call: ApplicationCall) =
        run {
            val tri = call.extractRoutingIds() ?: return@run
            val cuentaId = call.requireCuentaId() ?: return@run
            val database = DatabaseManager.connectToCompanyDb(tri.ctx.countryCode, tri.ctx.adminDb)
            if (!call.ensureCuentaScope(database, tri)) return@run
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
            log.debug("Cuenta obtenida. adminDb={} cuentaId={}", tri.ctx.adminDb, cuentaId)
        }

    suspend fun cancelar(call: ApplicationCall) =
        run {
            val tri = call.extractRoutingIds() ?: return@run
            val cuentaId = call.requireCuentaId() ?: return@run
            val database = DatabaseManager.connectToCompanyDb(tri.ctx.countryCode, tri.ctx.adminDb)
            if (!call.ensureCuentaScope(database, tri)) return@run
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
                    call.respond(HttpStatusCode.Conflict, mapOf("error" to ERR_ACCOUNT_NOT_ACTIVE))

                else -> call.respond(HttpStatusCode.InternalServerError, mapOf("error" to ERR_CANCEL_ACCOUNT))
            }
            log.debug("Cuenta cancelada. adminDb={} cuentaId={}", tri.ctx.adminDb, cuentaId)
        }

    suspend fun marcarFacturada(call: ApplicationCall) =
        run {
            val tri = call.extractRoutingIds() ?: return@run
            val cuentaId = call.requireCuentaId() ?: return@run
            val body = call.receive<MarcarCuentaFacturadaRequest>()
            val database = DatabaseManager.connectToCompanyDb(tri.ctx.countryCode, tri.ctx.adminDb)
            if (!call.ensureCuentaScope(database, tri)) return@run
            val result =
                cuentaMesaRepository.marcarFacturada(
                    database = database,
                    command =
                        MarcarFacturadaCommand(
                            sesionId = tri.sesionId,
                            mesaId = tri.mesaId,
                            cuentaId = cuentaId,
                            idempotencyKey = body.idempotencyKey,
                            idFactura = body.idFactura,
                            codFactura = body.codFactura,
                        ),
                )
            call.respondMarcarFacturada(result, tri.sesionId, cuentaId)
        }

    private suspend fun respondSesionMutacion(
        call: ApplicationCall,
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
                call.respond(HttpStatusCode.NotFound, mapOf("error" to "SesiÃ³n no encontrada"))

            SesionMesaResult.SesionYaFinalizada ->
                call.respond(
                    HttpStatusCode.Conflict,
                    mapOf("error" to "La sesiÃ³n no admite esta operaciÃ³n (estado final)"),
                )

            else -> {
                log.warn("Respuesta no esperada al solicitar/cancelar cuenta: {}", result)
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "No se pudo modificar la sesiÃ³n"))
            }
        }
    }

    private suspend fun ApplicationCall.extractRoutingIds(): CuentaRoutingIds? =
        run {
            val ctx = resolvePosContext() ?: return@run null
            val cajaId = requireCajaId() ?: return@run null
            val areaId = requireAreaId() ?: return@run null
            val mesaId = requireMesaId() ?: return@run null
            val sesionId = requireSesionId() ?: return@run null
            CuentaRoutingIds(ctx = ctx, cajaId = cajaId, areaId = areaId, mesaId = mesaId, sesionId = sesionId)
        }

    private suspend fun ApplicationCall.ensureCuentaScope(
        database: org.jetbrains.exposed.sql.Database,
        ids: CuentaRoutingIds,
    ): Boolean {
        val cajaScope =
            resolveScopeOrRespond(mesasRepository, database, ids.ctx, ids.cajaId)
                ?: return false
        val valid =
            cuentaMesaRepository.scopeValido(
                database,
                CuentaScope(
                    sesionId = ids.sesionId,
                    cajaId = ids.cajaId,
                    sucursalId = cajaScope.sucursalId,
                    areaId = ids.areaId,
                    mesaId = ids.mesaId,
                ),
            )
        if (!valid) {
            respond(
                HttpStatusCode.NotFound,
                mapOf("error" to "La sesiÃ³n no pertenece a la caja, Ã¡rea o mesa indicadas"),
            )
        }
        return valid
    }

    private suspend fun ApplicationCall.requireCuentaId(): Int? =
        run {
            val v = parameters["cuentaId"]?.toIntOrNull()
            if (v == null || v <= 0) {
                respond(HttpStatusCode.BadRequest, mapOf("error" to "El identificador de cuenta es invÃ¡lido"))
                return@run null
            }
            v
        }
}

private val cuentaHandlersLog = LoggerFactory.getLogger("CuentaMesaRouting")

private suspend fun ApplicationCall.respondMarcarFacturada(
    result: CuentaMesaResult,
    sesionId: Int,
    cuentaId: Int,
) {
    when (result) {
        is CuentaMesaResult.Facturada ->
            respond(
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
            // confirmado y no repetir el `procesar venta`. El campo `error` trae el motivo.
            respond(
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
            respond(HttpStatusCode.NotFound, mapOf("error" to ERR_SESSION_SCOPE))

        CuentaMesaResult.CuentaNoEncontrada ->
            respond(HttpStatusCode.NotFound, mapOf("error" to "Cuenta no encontrada"))

        CuentaMesaResult.CuentaNoActiva ->
            respond(HttpStatusCode.Conflict, mapOf("error" to "La cuenta ya no estÃ¡ activa"))

        CuentaMesaResult.SesionNoActiva ->
            respond(HttpStatusCode.Conflict, mapOf("error" to "La sesiÃ³n no admite esta operaciÃ³n"))

        else -> {
            cuentaHandlersLog.warn("Respuesta no esperada al marcar facturada: {}", result)
            respond(
                HttpStatusCode.InternalServerError,
                mapOf("error" to "No se pudo confirmar la facturaciÃ³n"),
            )
        }
    }
}
