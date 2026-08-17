package com.amaxoniaerp.features.mesas

import com.amaxoniaerp.core.database.DatabaseManager
import com.amaxoniaerp.features.mesas.data.PedidoMesaRepository
import com.amaxoniaerp.features.mesas.domain.CambiarEstadoPedidoRequest
import com.amaxoniaerp.features.mesas.domain.CrearPedidoMesaRequest
import com.amaxoniaerp.features.mesas.domain.EnviarComandaRequest
import com.amaxoniaerp.features.mesas.domain.EnviarComandaResponse
import com.amaxoniaerp.features.mesas.domain.EstadoPedidoMesa
import com.amaxoniaerp.features.mesas.domain.PedidoMesaActualizadoResponse
import com.amaxoniaerp.features.mesas.domain.PedidoMesaCreadoResponse
import com.amaxoniaerp.features.mesas.domain.PedidoMesaResult
import com.amaxoniaerp.features.mesas.domain.PedidosMesaListResponse
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import org.slf4j.LoggerFactory

private const val ERR_LIST_ORDERS = "No se pudieron listar los pedidos"
private const val ERR_EMPTY_ITEMS = "La petición no trae items para agregar"
private const val ERR_CREATE_ORDERS = "No se pudieron crear los pedidos"
private const val ERR_SEND_ORDER = "No se pudo enviar la comanda"
private const val ERR_INVALID_ORDER_ID = "El identificador de pedido es inválido"
private const val ERR_ORDER_SCOPE = "El pedido no existe o no pertenece a la sesión"
private const val ERR_UPDATE_ORDER_STATUS = "No se pudo cambiar el estado del pedido"
private const val ERR_SESSION_NOT_IN_TABLE = "La sesión no pertenece a esa mesa"
private const val ERR_SESSION_NOT_OPEN = "La sesión ya no está abierta"
private const val ERR_INVALID_BODY = "Cuerpo de la petición inválido"
private const val ERR_INVALID_ORDER_STATUS = "Estado de pedido inválido"

/**
 * Pedidos y comandas asociados a la sesión operativa de mesa para el POS.
 *
 * Endpoints (colgados del path de sesión ya existente):
 *
 * - `GET    /api/pos/areas/{areaId}/mesas/{mesaId}/sesiones/{sesionId}/pedidos?cajaId=&estado=`
 * - `POST   /api/pos/areas/{areaId}/mesas/{mesaId}/sesiones/{sesionId}/pedidos?cajaId=` (crear)
 * - `POST   /api/pos/areas/{areaId}/mesas/{mesaId}/sesiones/{sesionId}/pedidos/enviar?cajaId=`
 * - `PATCH  /api/pos/areas/{areaId}/mesas/{mesaId}/sesiones/{sesionId}/pedidos/{pedidoId}?cajaId=`
 *
 * Todas las operaciones reutilizan `resolvePosContext`, `requireCajaId`, `requireAreaId`,
 * `requireMesaId`, `requireSesionId` (estos últimos dos compartidos desde SesionMesaRouting).
 *
 * El `cajaId` solo sirve para validar el acceso del usuario a la caja y derivar sucursal
 * (igual que en sesiones): los pedidos viven exclusivamente ligados a la sesión, no a la caja,
 * porque varias cajas pueden operar contra la misma mesa en turnos distintos.
 */
fun Route.pedidoMesaRouting(pedidoMesaRepository: PedidoMesaRepository) {
    val handlers = PedidoMesaHandlers(pedidoMesaRepository)

    authenticate {
        route("/api/pos/areas/{areaId}/mesas/{mesaId}/sesiones/{sesionId}/pedidos") {
            /**
             * Lista líneas de la sesión. `estado` opcional permite traer solo pendientes,
             * solo enviadas, etc.
             */
            get { handlers.listar(call) }

            /**
             * Crea líneas de pedido sobre la sesión. Permite `enviar_inmediato` para crear y
             * mandar a cocina en un solo paso.
             */
            post { handlers.crear(call) }

            /**
             * Envía comanda: pasa todas las líneas PENDIENTE (o las indicadas) a ENVIADA con
             * el siguiente `comanda_secuencia`.
             */
            post("enviar") { handlers.enviar(call) }

            /**
             * Cambia el estado de una línea (avance hacia EN_PREPARACION/LISTA/ENTREGADA o
             * anulación a CANCELADA). El cuerpo trae el nuevo estado en `estado`.
             */
            patch("{pedidoId}") { handlers.cambiarEstado(call) }
        }
    }
}

/**
 * Handlers de los endpoints de pedidos de mesa. Validan contexto/parámetros, ejecutan
 * la operación del repositorio y mapean el resultado a HTTP.
 */
internal class PedidoMesaHandlers(
    private val pedidoMesaRepository: PedidoMesaRepository,
) {
    private val log = LoggerFactory.getLogger("PedidoMesaRouting")

    suspend fun listar(call: ApplicationCall) = run {
        val ctx = call.resolvePosContext() ?: return@run
        call.requireCajaId() ?: return@run
        call.requireAreaId() ?: return@run
        val mesaId = call.requireMesaId() ?: return@run
        val sesionId = call.requireSesionId() ?: return@run

        val estadoCodigo = call.request.queryParameters["estado"]
        val estado = estadoCodigo?.let { EstadoPedidoMesa.fromCodigo(it.uppercase()) }
        if (estadoCodigo != null && estado == null) {
            call.respond(HttpStatusCode.BadRequest, mapOf("error" to ERR_INVALID_ORDER_STATUS))
            return@run
        }

        val database = DatabaseManager.connectToCompanyDb(ctx.countryCode, ctx.adminDb)
        val result = pedidoMesaRepository.listar(database, sesionId, mesaId, estado)
        when (result) {
            is PedidoMesaResult.Listado ->
                call.respond(
                    HttpStatusCode.OK,
                    PedidosMesaListResponse(
                        success = true,
                        sesionMesaId = sesionId,
                        mesaId = mesaId,
                        data = result.pedidos,
                    ),
                )

            PedidoMesaResult.SesionNoPerteneceMesa ->
                call.respond(HttpStatusCode.NotFound, mapOf("error" to ERR_SESSION_NOT_IN_TABLE))

            else -> call.respond(HttpStatusCode.InternalServerError, mapOf("error" to ERR_LIST_ORDERS))
        }
    }

    suspend fun crear(call: ApplicationCall) = run {
        val ctx = call.resolvePosContext() ?: return@run
        call.requireCajaId() ?: return@run
        call.requireAreaId() ?: return@run
        val mesaId = call.requireMesaId() ?: return@run
        val sesionId = call.requireSesionId() ?: return@run

        val body =
            runCatching { call.receive<CrearPedidoMesaRequest>() }.getOrElse { e ->
                if (e is Error) throw e
                log.warn("Body inválido al crear pedido: {}", e.message)
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to ERR_INVALID_BODY))
                return@run
            }

        val database = DatabaseManager.connectToCompanyDb(ctx.countryCode, ctx.adminDb)
        val result = pedidoMesaRepository.crear(database, sesionId, mesaId, body)
        when (result) {
            is PedidoMesaResult.Creado ->
                call.respond(
                    HttpStatusCode.Created,
                    PedidoMesaCreadoResponse(
                        success = true,
                        sesionMesaId = result.sesionMesaId,
                        comandaSecuencia = result.comandaSecuencia,
                        data = result.pedidos,
                    ),
                )

            PedidoMesaResult.SesionNoPerteneceMesa ->
                call.respond(HttpStatusCode.NotFound, mapOf("error" to ERR_SESSION_NOT_IN_TABLE))

            PedidoMesaResult.SesionNoActiva ->
                call.respond(HttpStatusCode.Conflict, mapOf("error" to ERR_SESSION_NOT_OPEN))

            PedidoMesaResult.SinItemsParaCrear ->
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to ERR_EMPTY_ITEMS))

            else -> call.respond(HttpStatusCode.InternalServerError, mapOf("error" to ERR_CREATE_ORDERS))
        }
    }

    suspend fun enviar(call: ApplicationCall) = run {
        val ctx = call.resolvePosContext() ?: return@run
        call.requireCajaId() ?: return@run
        val mesaId = call.requireMesaId() ?: return@run
        val sesionId = call.requireSesionId() ?: return@run

        val body =
            runCatching { call.receive<EnviarComandaRequest>() }.getOrElse { e ->
                if (e is Error) throw e
                // Cuerpo vacío es válido: enviar TODOS los pendientes.
                EnviarComandaRequest()
            }

        val database = DatabaseManager.connectToCompanyDb(ctx.countryCode, ctx.adminDb)
        val result = pedidoMesaRepository.enviarComanda(database, sesionId, mesaId, body.pedidoIds)
        when (result) {
            is PedidoMesaResult.Enviada ->
                call.respond(
                    HttpStatusCode.OK,
                    EnviarComandaResponse(
                        success = true,
                        comandaSecuencia = result.comandaSecuencia,
                        cantidadLineas = result.pedidos.size,
                        data = result.pedidos,
                    ),
                )

            PedidoMesaResult.SesionNoPerteneceMesa ->
                call.respond(HttpStatusCode.NotFound, mapOf("error" to ERR_SESSION_NOT_IN_TABLE))

            PedidoMesaResult.SesionNoActiva ->
                call.respond(HttpStatusCode.Conflict, mapOf("error" to ERR_SESSION_NOT_OPEN))

            PedidoMesaResult.SinPedidosPendientes ->
                call.respond(
                    HttpStatusCode.Conflict,
                    mapOf("error" to "No hay pedidos pendientes para enviar"),
                )

            else -> call.respond(HttpStatusCode.InternalServerError, mapOf("error" to ERR_SEND_ORDER))
        }
    }

    suspend fun cambiarEstado(call: ApplicationCall) = run {
        val ctx = call.resolvePosContext() ?: return@run
        call.requireCajaId() ?: return@run
        val mesaId = call.requireMesaId() ?: return@run
        val sesionId = call.requireSesionId() ?: return@run
        val pedidoId = call.parameters["pedidoId"]?.toIntOrNull()?.takeIf { it > 0 }
        if (pedidoId == null) {
            call.respond(HttpStatusCode.BadRequest, mapOf("error" to ERR_INVALID_ORDER_ID))
            return@run
        }

        val body =
            runCatching { call.receive<CambiarEstadoPedidoRequest>() }.getOrElse { e ->
                if (e is Error) throw e
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to ERR_INVALID_BODY))
                return@run
            }
        val destino = EstadoPedidoMesa.fromCodigo(body.estado.uppercase())
        if (destino == null) {
            call.respond(HttpStatusCode.BadRequest, mapOf("error" to ERR_INVALID_ORDER_STATUS))
            return@run
        }

        val database = DatabaseManager.connectToCompanyDb(ctx.countryCode, ctx.adminDb)
        val result = pedidoMesaRepository.cambiarEstado(database, sesionId, mesaId, pedidoId, destino)
        when (result) {
            is PedidoMesaResult.EstadoActualizado ->
                call.respond(
                    HttpStatusCode.OK,
                    PedidoMesaActualizadoResponse(success = true, data = result.pedido),
                )

            PedidoMesaResult.PedidoNoEncontrado ->
                call.respond(HttpStatusCode.NotFound, mapOf("error" to ERR_ORDER_SCOPE))

            PedidoMesaResult.EstadoInvalido ->
                call.respond(
                    HttpStatusCode.Conflict,
                    mapOf("error" to "El cambio de estado no es válido para la línea"),
                )

            PedidoMesaResult.SesionNoPerteneceMesa ->
                call.respond(HttpStatusCode.NotFound, mapOf("error" to ERR_SESSION_NOT_IN_TABLE))

            else ->
                call.respond(
                    HttpStatusCode.InternalServerError,
                    mapOf("error" to ERR_UPDATE_ORDER_STATUS),
                )
        }
    }
}
