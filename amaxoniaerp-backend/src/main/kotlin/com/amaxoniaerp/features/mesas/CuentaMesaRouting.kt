package com.amaxoniaerp.features.mesas

import com.amaxoniaerp.features.mesas.data.CuentaMesaRepository
import com.amaxoniaerp.features.mesas.data.MesasRepository
import com.amaxoniaerp.features.mesas.data.SesionMesaRepository
import io.ktor.server.application.call
import io.ktor.server.auth.authenticate
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route

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
    val handlers =
        CuentaMesaHandlers(
            cuentaMesaRepository = cuentaMesaRepository,
            sesionMesaRepository = sesionMesaRepository,
            mesasRepository = mesasRepository,
        )

    authenticate {
        route("/api/pos/areas/{areaId}/mesas/{mesaId}/sesiones/{sesionId}") {
            /**
             * Solicita la cuenta: transiciona la sesión a `CUENTA_SOLICITADA`. Reversible.
             */
            post("solicitar-cuenta") { handlers.mutarSolicitudCuenta(call, solicitar = true) }

            /**
             * Cancela la solicitud de cuenta: revierte `CUENTA_SOLICITADA` -> `ABIERTA`.
             */
            post("cancelar-solicitud-cuenta") { handlers.mutarSolicitudCuenta(call, solicitar = false) }

            route("cuenta") {
                /**
                 * Lista todas las cuentas de la sesión (incluye PAGADAS/CANCELADAS para auditoría).
                 */
                get { handlers.listar(call) }

                /**
                 * Crea una cuenta completa (`{incluir_todo_pendiente:true}`) o una división
                 * (`{items:[{pedido_mesa_id, cantidad}], incluir_todo_pendiente:false}`).
                 */
                post { handlers.crear(call) }

                route("{cuentaId}") {
                    /**
                     * Detalle de una cuenta.
                     */
                    get { handlers.detalle(call) }

                    /**
                     * Cancela una cuenta ACTIVA sin facturar. Libera los saldos asociados.
                     */
                    post("cancelar") { handlers.cancelar(call) }

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
                    post("marcar-facturada") { handlers.marcarFacturada(call) }
                }
            }
        }
    }
}
