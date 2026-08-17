package com.amaxoniaerp.features.mesas.data

import com.amaxoniaerp.core.database.dbQuery
import com.amaxoniaerp.features.mesas.domain.CrearCuentaRequest
import com.amaxoniaerp.features.mesas.domain.CuentaMesaResponse
import com.amaxoniaerp.features.mesas.domain.CuentaMesaResult
import com.amaxoniaerp.features.mesas.domain.EstadoCuentaIdempotencia
import com.amaxoniaerp.features.mesas.domain.EstadoCuentaMesa
import com.amaxoniaerp.features.mesas.domain.EstadoSesionMesa
import com.amaxoniaerp.features.sales.domain.InvalidSaleRequestException
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.update
import java.math.BigDecimal
import java.time.LocalDateTime

/** Identificación de sesión+caja+sucursal+área+mesa para validación de scope. */
data class CuentaScope(
    val sesionId: Int,
    val cajaId: String,
    val sucursalId: Int,
    val areaId: Int,
    val mesaId: Int,
)

/** Comando de marcado de cuenta facturada (idempotente por key). */
data class MarcarFacturadaCommand(
    val sesionId: Int,
    val mesaId: Int,
    val cuentaId: Int,
    val idempotencyKey: String,
    val idFactura: String,
    val codFactura: String?,
)

/**
 * Operaciones de cuenta/división de mesa.
 *
 * Modelado:
 * - Una sesión puede tener N cuentas activas en paralelo (divisiones por producto/cantidad).
 *   El saldo disponible de cada [PedidoMesaTable] se reparte: `Σ(cuenta_detalle.cantidad)` para
 *   un pedido nunca puede superar `item_cantidad - cantidad_facturada`.
 * - Una cuenta con `estado = ACTIVA` puede evolucionar a:
 *   - [EstadoCuentaMesa.PAGADA]: vía [marcarFacturada] con un idFactura confirmado.
 *   - [EstadoCuentaMesa.CANCELADA]: vía [cancelarCuenta] cuando el operario descarta la
 *     división sin pagar; sus líneas se eliminan y el saldo vuelve a estar disponible.
 *
 * Transaccionalidad:
 * - Toda mutación de cuenta se hace dentro de una transacción Exposed (`newSuspendedTransaction`).
 *   La función [marcarFacturada] aplica en bloque: marca `cuenta_detalle.facturado = 1`,
 *   incrementa `pedido_mesa.cantidad_facturada`, fija `cuenta_mesa.id_factura`,
 *   registra `cuenta_mesa_idempotencia.estado = CONFIRMED` y, si la sesión quedó totalmente
 *   liquidada (sin cuentas activas y sin pedidos pendientes en cocina), transiciona la sesión
 *   a `CERRADA_PAGADA`.
 *
 * Idempotencia:
 * - La tabla `cuenta_mesa_idempotencia` y el `idFactura` determinista garantizan que un reintento
 *   del POS (timeout/doble tap) no duplique efectos. Su confirmación ocurre con la factura.
 *
 * Las consultas viven en CuentaMesaQueries.kt, la facturación atómica en
 * CuentaMesaFacturacion.kt, la validación de venta en CuentaMesaVentaValidation.kt
 * y los mappers en CuentaMesaMappers.kt.
 */
class CuentaMesaRepository {
    suspend fun scopeValido(
        database: Database,
        scope: CuentaScope,
    ): Boolean =
        dbQuery(database) {
            SesionMesaTable
                .selectAll()
                .where {
                    (SesionMesaTable.id eq scope.sesionId) and
                        (SesionMesaTable.cajaId eq scope.cajaId) and
                        (SesionMesaTable.sucursalId eq scope.sucursalId) and
                        (SesionMesaTable.areaId eq scope.areaId) and
                        (SesionMesaTable.mesaId eq scope.mesaId) and
                        (SesionMesaTable.activo eq ACTIVE)
                }.limit(1)
                .any()
        }

    /** Lista todas las cuentas de la sesión (incluye PAGADA/CANCELADA para auditoría). */
    suspend fun listarCuentas(
        database: Database,
        sesionId: Int,
        mesaId: Int,
    ): CuentaMesaResult =
        dbQuery(database) {
            val sesion = sesionActiva(sesionId, mesaId) ?: return@dbQuery CuentaMesaResult.SesionNoPerteneceMesa
            val cuentas = cargarCuentas(listOf(sesion.id))
            CuentaMesaResult.Listada(cuentas)
        }

    /** Devuelve la cuenta indicada por id, validando que pertenezca a la sesión. */
    suspend fun obtenerCuenta(
        database: Database,
        sesionId: Int,
        mesaId: Int,
        cuentaId: Int,
    ): CuentaMesaResult =
        dbQuery(database) {
            val sesion = sesionActiva(sesionId, mesaId) ?: return@dbQuery CuentaMesaResult.SesionNoPerteneceMesa
            val cuenta = cargarCuenta(sesion.id, cuentaId) ?: return@dbQuery CuentaMesaResult.CuentaNoEncontrada
            CuentaMesaResult.Creada(cuenta)
        }

    /**
     * Crea una cuenta nueva sobre la sesión. Si [CrearCuentaRequest.incluirTodoPendiente] es
     * `true`, se ignora [CrearCuentaRequest.items] y se crea con todos los pedidos
     * ENTREGADOS/no CANCELADOS que tienen saldo pendiente. Si `false`, se requiere `items` con
     * `cantidad <= saldo_pendiente` por pedido.
     */
    suspend fun crear(
        database: Database,
        sesionId: Int,
        mesaId: Int,
        request: CrearCuentaRequest,
    ): CuentaMesaResult =
        newSuspendedTransaction<CuentaMesaResult>(kotlin.coroutines.coroutineContext, database) {
            // Una sesión es el agregado de reserva: el lock serializa divisiones concurrentes
            // para que no compartan cantidades ni numero_cuenta.
            val sesion =
                sesionActiva(sesionId, mesaId, forUpdate = true)
                    ?: return@newSuspendedTransaction CuentaMesaResult.SesionNoPerteneceMesa
            val estadoSesion = EstadoSesionMesa.fromCodigo(sesion.estado)
            if (estadoSesion == null || !estadoSesion.admitePedidos) {
                return@newSuspendedTransaction CuentaMesaResult.SesionNoActiva
            }

            val pedidosFacturables = pedidosFacturablesDeSesion(sesion.id)
            if (pedidosFacturables.isEmpty()) {
                return@newSuspendedTransaction CuentaMesaResult.SinItemsParaCrear
            }

            val propuesta = proponerDetalles(request, pedidosFacturables)
            if (propuesta.error != null) {
                return@newSuspendedTransaction propuesta.error
            }

            val cuentaId = insertarCuentaConDetalles(sesion.id, propuesta.detalles)

            val cuenta = cargarCuenta(sesion.id, cuentaId)!!
            CuentaMesaResult.Creada(cuenta)
        }

    /**
     * Compatibilidad con el flujo legado de dos pasos. El POS actual registra SENDING desde la
     * transacción estándar de venta. Devuelve [CuentaMesaResult.IdempotenciaDuplicada] si ya hay
     * un intento CONFIRMED/SENDING previo para el mismo key.
     */
    suspend fun iniciarIdempotencia(
        database: Database,
        sesionId: Int,
        mesaId: Int,
        cuentaId: Int,
        idempotencyKey: String,
    ): CuentaMesaResult =
        newSuspendedTransaction<CuentaMesaResult>(kotlin.coroutines.coroutineContext, database) {
            val sesion =
                sesionActiva(sesionId, mesaId)
                    ?: return@newSuspendedTransaction CuentaMesaResult.SesionNoPerteneceMesa
            val cuenta =
                cargarCuenta(sesion.id, cuentaId, forUpdate = true)
                    ?: return@newSuspendedTransaction CuentaMesaResult.CuentaNoEncontrada
            if (cuenta.estado != EstadoCuentaMesa.ACTIVA.codigo) {
                return@newSuspendedTransaction CuentaMesaResult.CuentaNoActiva
            }
            val existente =
                CuentaMesaIdempotenciaTable
                    .selectAll()
                    .where { CuentaMesaIdempotenciaTable.idempotencyKey eq idempotencyKey }
                    .singleOrNull()
            if (existente != null) {
                val estadoExistente =
                    EstadoCuentaIdempotencia.fromCodigo(existente[CuentaMesaIdempotenciaTable.estado])
                when (estadoExistente) {
                    EstadoCuentaIdempotencia.CONFIRMED, EstadoCuentaIdempotencia.SENDING ->
                        return@newSuspendedTransaction CuentaMesaResult.IdempotenciaDuplicada
                    EstadoCuentaIdempotencia.FAILED -> {
                        // Reintento tras fallo: reabre el intento.
                        CuentaMesaIdempotenciaTable.update({
                            CuentaMesaIdempotenciaTable.idempotencyKey eq idempotencyKey
                        }) {
                            it[CuentaMesaIdempotenciaTable.estado] = EstadoCuentaIdempotencia.SENDING.codigo
                            it[CuentaMesaIdempotenciaTable.intentos] =
                                existente[CuentaMesaIdempotenciaTable.intentos] + 1
                            it[CuentaMesaIdempotenciaTable.fechaUltimoIntento] = LocalDateTime.now()
                            it[CuentaMesaIdempotenciaTable.errorMensaje] = null
                        }
                        CuentaMesaResult.Creada(cuenta)
                    }
                    null -> CuentaMesaResult.IdempotenciaDuplicada
                }
            } else {
                CuentaMesaIdempotenciaTable.insert {
                    it[CuentaMesaIdempotenciaTable.idempotencyKey] = idempotencyKey
                    it[CuentaMesaIdempotenciaTable.cuentaMesaId] = cuentaId
                    it[CuentaMesaIdempotenciaTable.sesionMesaId] = sesion.id
                    it[CuentaMesaIdempotenciaTable.estado] = EstadoCuentaIdempotencia.SENDING.codigo
                    it[CuentaMesaIdempotenciaTable.fechaPrimerIntento] = LocalDateTime.now()
                    it[CuentaMesaIdempotenciaTable.fechaUltimoIntento] = LocalDateTime.now()
                }
                CuentaMesaResult.Creada(cuenta)
            }
        }

    /**
     * Marca una cuenta ACTIVA como [EstadoCuentaMesa.PAGADA], asociando la factura y
     * decretando el `cantidad_facturada` de cada `pedido_mesa` afectado. Idempotente en
     * `idempotencyKey`.
     *
     * Si la sesión queda totalmente liquidada (sin cuentas activas y sin pedidos por entregar),
     * transiciona la sesión a `CERRADA_PAGADA` y devuelve `sesionCerrada = true`.
     */
    suspend fun marcarFacturada(
        database: Database,
        command: MarcarFacturadaCommand,
    ): CuentaMesaResult =
        newSuspendedTransaction<CuentaMesaResult>(kotlin.coroutines.coroutineContext, database) {
            // 1. Idempotencia: si ya está CONFIRMED para este key, devolver cuenta actual sin mutar.
            val previa =
                verificarIdempotenciaPrevia(command.idempotencyKey, command.sesionId, command.cuentaId)
            if (previa.error != null) {
                return@newSuspendedTransaction previa.error
            }

            // 2. Validaciones
            val sesion =
                sesionActiva(command.sesionId, command.mesaId)
                    ?: return@newSuspendedTransaction CuentaMesaResult.SesionNoPerteneceMesa
            val cuenta =
                cargarCuenta(sesion.id, command.cuentaId, forUpdate = true)
                    ?: return@newSuspendedTransaction CuentaMesaResult.CuentaNoEncontrada
            if (cuenta.estado != EstadoCuentaMesa.ACTIVA.codigo) {
                return@newSuspendedTransaction CuentaMesaResult.CuentaNoActiva
            }

            // 3. Aplicar atómicamente la facturación
            val ahora = LocalDateTime.now()
            val errorFacturacion = aplicarFacturacionDetalles(cuenta, command.cuentaId)
            if (errorFacturacion != null) {
                return@newSuspendedTransaction errorFacturacion
            }
            marcarCuentaPagada(command.cuentaId, command.idFactura, command.codFactura, ahora)

            // 4. Idempotencia: queda CONFIRMED
            confirmarIdempotencia(
                ConfirmarIdempotenciaInput(
                    command = command,
                    sesionId = sesion.id,
                    existente = previa.existente,
                    ahora = ahora,
                ),
            )

            // 5. Cerrar la sesión si está totalmente liquidada
            val sesionCerrada =
                !existeCuentaActivaEnSesion(sesion.id) && !existeSaldoPendienteEnSesion(sesion.id)
            if (sesionCerrada) {
                runCerradoPorPago(sesion.id)
            }

            val cuentaFinal = cargarCuenta(sesion.id, command.cuentaId)!!
            CuentaMesaResult.Facturada(cuenta = cuentaFinal, sesionCerrada = sesionCerrada)
        }

    /**
     * Registra que un intento de facturación falló (el POS ya sabe que `procesar venta` dio 4xx
     * o 5xx). Marca el intento como FAILED para que un reintento con la misma key se permita.
     */
    suspend fun registrarIdempotenciaFallida(
        database: Database,
        idempotencyKey: String,
        errorMensaje: String,
    ): CuentaMesaResult =
        newSuspendedTransaction<CuentaMesaResult>(kotlin.coroutines.coroutineContext, database) {
            val existente =
                CuentaMesaIdempotenciaTable
                    .selectAll()
                    .where { CuentaMesaIdempotenciaTable.idempotencyKey eq idempotencyKey }
                    .singleOrNull()
            if (existente == null) {
                return@newSuspendedTransaction CuentaMesaResult.IdempotenciaFallidaPrevia
            }
            val estadoActual =
                EstadoCuentaIdempotencia.fromCodigo(existente[CuentaMesaIdempotenciaTable.estado])
            if (estadoActual == EstadoCuentaIdempotencia.CONFIRMED) {
                return@newSuspendedTransaction CuentaMesaResult.IdempotenciaDuplicada
            }
            CuentaMesaIdempotenciaTable.update({ CuentaMesaIdempotenciaTable.idempotencyKey eq idempotencyKey }) {
                it[CuentaMesaIdempotenciaTable.estado] = EstadoCuentaIdempotencia.FAILED.codigo
                it[CuentaMesaIdempotenciaTable.errorMensaje] = errorMensaje.take(CUENTA_MAX_ERROR_LEN)
                it[CuentaMesaIdempotenciaTable.fechaUltimoIntento] = LocalDateTime.now()
            }
            CuentaMesaResult.Creada(CuentaMesaResponse())
        }

    /**
     * Cancela una cuenta ACTIVA sin facturar (operario descartó la división). Elimina sus
     * detalles y libera el saldo de los pedidos afectados. No afecta a idempotencia.
     */
    suspend fun cancelarCuenta(
        database: Database,
        sesionId: Int,
        mesaId: Int,
        cuentaId: Int,
    ): CuentaMesaResult =
        newSuspendedTransaction<CuentaMesaResult>(kotlin.coroutines.coroutineContext, database) {
            val sesion =
                sesionActiva(sesionId, mesaId, forUpdate = true)
                    ?: return@newSuspendedTransaction CuentaMesaResult.SesionNoPerteneceMesa
            val cuenta =
                cargarCuenta(sesion.id, cuentaId, forUpdate = true)
                    ?: return@newSuspendedTransaction CuentaMesaResult.CuentaNoEncontrada
            if (cuenta.estado != EstadoCuentaMesa.ACTIVA.codigo) {
                return@newSuspendedTransaction CuentaMesaResult.CuentaNoActiva
            }
            CuentaMesaDetalleTable.deleteWhere { CuentaMesaDetalleTable.cuentaMesaId eq cuentaId }
            CuentaMesaTable.update({ CuentaMesaTable.id eq cuentaId }) {
                it[CuentaMesaTable.estado] = EstadoCuentaMesa.CANCELADA.codigo
                it[CuentaMesaTable.fechaCierre] = LocalDateTime.now()
                it[CuentaMesaTable.activo] = INACTIVE
                it[CuentaMesaTable.saldoRestante] = BigDecimal.ZERO
            }
            val cuentaFinal = cargarCuenta(sesion.id, cuentaId)!!
            CuentaMesaResult.Creada(cuentaFinal)
        }

    /** Aplica la factura a la cuenta dentro de la transacción de venta. */
    fun confirmarVentaEnTransaccion(
        validada: CuentaMesaVentaValidada,
        idFactura: String,
        codFactura: String,
    ): Boolean {
        val ahora = LocalDateTime.now()
        validada.cuenta.detalle.forEach { detalle ->
            val pedido =
                PedidoMesaTable
                    .selectAll()
                    .where { PedidoMesaTable.id eq detalle.pedidoMesaId }
                    .single()
            val actual = pedido[PedidoMesaTable.cantidadFacturada]
            val nueva = actual + detalle.cantidad.toBigDecimal()
            if (nueva > pedido[PedidoMesaTable.itemCantidad]) {
                throw InvalidSaleRequestException("La cantidad de la cuenta ya fue facturada por otro cobro")
            }
            val updated =
                PedidoMesaTable.update({
                    (PedidoMesaTable.id eq detalle.pedidoMesaId) and
                        (PedidoMesaTable.cantidadFacturada eq actual)
                }) {
                    it[cantidadFacturada] = nueva
                }
            if (updated != 1) {
                throw InvalidSaleRequestException(
                    "La cuenta cambió durante el cobro; reintenta con el saldo actualizado",
                )
            }
        }
        CuentaMesaDetalleTable.update({
            (CuentaMesaDetalleTable.cuentaMesaId eq validada.context.cuentaMesaId) and
                (CuentaMesaDetalleTable.facturado eq NOT_FACTURADO)
        }) {
            it[facturado] = FACTURADO
        }
        marcarCuentaPagada(validada.context.cuentaMesaId, idFactura, codFactura, ahora)
        CuentaMesaIdempotenciaTable.update({ CuentaMesaIdempotenciaTable.idempotencyKey eq idFactura }) {
            it[estado] = EstadoCuentaIdempotencia.CONFIRMED.codigo
            it[idFacturaResultado] = idFactura
            it[codFacturaResultado] = codFactura
            it[fechaUltimoIntento] = ahora
        }

        val sinSaldo = !existeSaldoPendienteEnSesion(validada.context.sesionMesaId)
        val sinCuentasActivas = !existeCuentaActivaEnSesion(validada.context.sesionMesaId)
        return sinSaldo && sinCuentasActivas && runCerradoPorPago(validada.context.sesionMesaId)
    }
}

data class CuentaMesaVentaValidada(
    val context: com.amaxoniaerp.features.sales.domain.CuentaMesaVentaInput,
    val cuenta: CuentaMesaResponse,
)
