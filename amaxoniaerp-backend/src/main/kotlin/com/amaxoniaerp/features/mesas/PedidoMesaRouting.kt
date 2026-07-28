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
import com.amaxoniaerp.features.mesas.domain.PedidoMesaResponse
import com.amaxoniaerp.features.mesas.domain.PedidoMesaResult
import com.amaxoniaerp.features.mesas.domain.PedidosMesaListResponse
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import org.slf4j.LoggerFactory

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
fun Route.pedidoMesaRouting(
    pedidoMesaRepository: PedidoMesaRepository,
) {
    val log = LoggerFactory.getLogger("PedidoMesaRouting")

    authenticate {
        route("/api/pos/areas/{areaId}/mesas/{mesaId}/sesiones/{sesionId}/pedidos") {
            /**
             * Lista líneas de la sesión. `estado` opcional permite traer solo pendientes,
             * solo enviadas, etc.
             */
            get {
                val ctx = call.resolvePosContext() ?: return@get
                val cajaId = call.requireCajaId() ?: return@get
                val areaId = call.requireAreaId() ?: return@get
                val mesaId = call.requireMesaId() ?: return@get
                val sesionId = call.requireSesionId() ?: return@get

                val estado = call.request.queryParameters["estado"]?.let { codigo ->
                    EstadoPedidoMesa.fromCodigo(codigo.uppercase()) ?: run {
                        call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Estado de pedido inválido"))
                        return@get
                    }
                }

                try {
                    val database = DatabaseManager.connectToCompanyDb(ctx.countryCode, ctx.adminDb)

                    @Suppress("UNUSED_VARIABLE")
                    val cajaIdChecked = cajaId // caja derivará sucursal en otros flujos; aquí no la usamos
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
                            call.respond(
                                HttpStatusCode.NotFound,
                                mapOf("error" to "La sesión no pertenece a esa mesa"),
                            )

                        else ->
                            call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "No se pudieron listar los pedidos"))
                    }
                } catch (e: Exception) {
                    log.error("Error listando pedidos. adminDb={} sesionId={}", ctx.adminDb, sesionId, e)
                    call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "No se pudieron listar los pedidos"))
                }
            }

            /**
             * Crea líneas de pedido sobre la sesión. Permite `enviar_inmediato` para crear y
             * mandar a cocina en un solo paso.
             */
            post {
                val ctx = call.resolvePosContext() ?: return@post
                call.requireCajaId() ?: return@post
                val areaId = call.requireAreaId() ?: return@post
                val mesaId = call.requireMesaId() ?: return@post
                val sesionId = call.requireSesionId() ?: return@post

                val body =
                    try {
                        call.receive<CrearPedidoMesaRequest>()
                    } catch (e: Exception) {
                        log.warn("Body inválido al crear pedido: {}", e.message)
                        call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Cuerpo de la petición inválido"))
                        return@post
                    }

                try {
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
                            call.respond(HttpStatusCode.NotFound, mapOf("error" to "La sesión no pertenece a esa mesa"))

                        PedidoMesaResult.SesionNoActiva ->
                            call.respond(HttpStatusCode.Conflict, mapOf("error" to "La sesión ya no está abierta"))

                        PedidoMesaResult.SinItemsParaCrear ->
                            call.respond(HttpStatusCode.BadRequest, mapOf("error" to "La petición no trae items para agregar"))

                        else -> call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "No se pudieron crear los pedidos"))
                    }
                } catch (e: Exception) {
                    log.error("Error creando pedido. adminDb={} sesionId={}", ctx.adminDb, sesionId, e)
                    call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "No se pudieron crear los pedidos"))
                }
            }

            /**
             * Envía comanda: pasa todas las líneas PENDIENTE (o las indicadas) a ENVIADA con
             * el siguiente `comanda_secuencia`.
             */
            post("enviar") {
                val ctx = call.resolvePosContext() ?: return@post
                call.requireCajaId() ?: return@post
                val mesaId = call.requireMesaId() ?: return@post
                val sesionId = call.requireSesionId() ?: return@post

                val body =
                    try {
                        call.receive<EnviarComandaRequest>()
                    } catch (e: Exception) {
                        // Cuerpo vacío es válido: enviar TODOS los pendientes.
                        EnviarComandaRequest()
                    }

                try {
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
                            call.respond(HttpStatusCode.NotFound, mapOf("error" to "La sesión no pertenece a esa mesa"))

                        PedidoMesaResult.SesionNoActiva ->
                            call.respond(HttpStatusCode.Conflict, mapOf("error" to "La sesión ya no está abierta"))

                        PedidoMesaResult.SinPedidosPendientes ->
                            call.respond(
                                HttpStatusCode.Conflict,
                                mapOf("error" to "No hay pedidos pendientes para enviar"),
                            )

                        else -> call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "No se pudo enviar la comanda"))
                    }
                } catch (e: Exception) {
                    log.error("Error enviando comanda. adminDb={} sesionId={}", ctx.adminDb, sesionId, e)
                    call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "No se pudo enviar la comanda"))
                }
            }

            /**
             * Cambia el estado de una línea (avance hacia EN_PREPARACION/LISTA/ENTREGADA o
             * anulación a CANCELADA). El cuerpo trae el nuevo estado en `estado`.
             */
            patch("{pedidoId}") {
                val ctx = call.resolvePosContext() ?: return@patch
                call.requireCajaId() ?: return@patch
                val mesaId = call.requireMesaId() ?: return@patch
                val sesionId = call.requireSesionId() ?: return@patch
                val pedidoId =
                    call.parameters["pedidoId"]?.toIntOrNull()?.takeIf { it > 0 } ?: run {
                        call.respond(HttpStatusCode.BadRequest, mapOf("error" to "El identificador de pedido es inválido"))
                        return@patch
                    }

                val body =
                    try {
                        call.receive<CambiarEstadoPedidoRequest>()
                    } catch (e: Exception) {
                        call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Cuerpo de la petición inválido"))
                        return@patch
                    }
                val destino =
                    EstadoPedidoMesa.fromCodigo(body.estado.uppercase()) ?: run {
                        call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Estado de pedido inválido"))
                        return@patch
                    }

                try {
                    val database = DatabaseManager.connectToCompanyDb(ctx.countryCode, ctx.adminDb)
                    val result = pedidoMesaRepository.cambiarEstado(database, sesionId, mesaId, pedidoId, destino)
                    when (result) {
                        is PedidoMesaResult.EstadoActualizado ->
                            call.respond(
                                HttpStatusCode.OK,
                                PedidoMesaActualizadoResponse(success = true, data = result.pedido),
                            )

                        PedidoMesaResult.PedidoNoEncontrado ->
                            call.respond(HttpStatusCode.NotFound, mapOf("error" to "El pedido no existe o no pertenece a la sesión"))

                        PedidoMesaResult.EstadoInvalido ->
                            call.respond(
                                HttpStatusCode.Conflict,
                                mapOf("error" to "El cambio de estado no es válido para la línea"),
                            )

                        PedidoMesaResult.SesionNoPerteneceMesa ->
                            call.respond(HttpStatusCode.NotFound, mapOf("error" to "La sesión no pertenece a esa mesa"))

                        else -> call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "No se pudo cambiar el estado del pedido"))
                    }
                } catch (e: Exception) {
                    log.error("Error cambiando estado. adminDb={} pedidoId={}", ctx.adminDb, pedidoId, e)
                    call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "No se pudo cambiar el estado del pedido"))
                }
            }
        }
    }
}
