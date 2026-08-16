package com.amaxoniaerp.features.mesas.data

import com.amaxoniaerp.core.database.dbQuery
import com.amaxoniaerp.features.mesas.domain.CrearCuentaRequest
import com.amaxoniaerp.features.mesas.domain.CuentaDetalleResponse
import com.amaxoniaerp.features.mesas.domain.CuentaMesaResponse
import com.amaxoniaerp.features.mesas.domain.CuentaMesaResult
import com.amaxoniaerp.features.mesas.domain.EstadoCuentaIdempotencia
import com.amaxoniaerp.features.mesas.domain.EstadoCuentaMesa
import com.amaxoniaerp.features.mesas.domain.EstadoPedidoMesa
import com.amaxoniaerp.features.mesas.domain.EstadoSesionMesa
import com.amaxoniaerp.features.sales.domain.CuentaMesaVentaInput
import com.amaxoniaerp.features.sales.domain.InvalidSaleRequestException
import com.amaxoniaerp.features.sales.domain.ProcessSaleRequest
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.SqlExpressionBuilder.minus
import org.jetbrains.exposed.sql.SqlExpressionBuilder.neq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.plus
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.update
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Operaciones de cuenta/división de mesa.
 *
 * Modelado:
 * - Una sesión puede tener N cuentas activas en paralelo (divisiones por producto/cantidad).
 *   El saldo disponible de cada [PedidoMesaTable] se reparte: `Σ(cuenta_detalle.cantidad)` para
 *   un pedido never puede superar `item_cantidad - cantidad_facturada`.
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
 */
class CuentaMesaRepository {
    suspend fun scopeValido(
        database: Database,
        sesionId: Int,
        cajaId: String,
        sucursalId: Int,
        areaId: Int,
        mesaId: Int,
    ): Boolean =
        dbQuery(database) {
            SesionMesaTable
                .selectAll()
                .where {
                    (SesionMesaTable.id eq sesionId) and
                        (SesionMesaTable.cajaId eq cajaId) and
                        (SesionMesaTable.sucursalId eq sucursalId) and
                        (SesionMesaTable.areaId eq areaId) and
                        (SesionMesaTable.mesaId eq mesaId) and
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

            val detallesABackend: List<DetallePropuesta> =
                if (request.incluirTodoPendiente) {
                    pedidosFacturables.map { p ->
                        DetallePropuesta(
                            pedido = p.row,
                            cantidad = p.saldoPendiente,
                        )
                    }
                } else {
                    if (request.items.isEmpty()) {
                        return@newSuspendedTransaction CuentaMesaResult.SinItemsParaCrear
                    }
                    val porPedido = pedidosFacturables.associateBy { it.row[PedidoMesaTable.id] }
                    val lista = mutableListOf<DetallePropuesta>()
                    request.items.groupBy { it.pedidoMesaId }.forEach { (pedidoId, solicitudes) ->
                        val pedido =
                            porPedido[pedidoId]
                                ?: return@newSuspendedTransaction CuentaMesaResult.PedidoNoEncontrado
                        val cantidadSolicitada =
                            solicitudes.fold(BigDecimal.ZERO) { total, solicitud ->
                                total +
                                    (
                                        solicitud.cantidad?.toBigDecimal()?.stripTrailingZeros()
                                            ?: pedido.saldoPendiente
                                    )
                            }
                        if (cantidadSolicitada <= BigDecimal.ZERO || cantidadSolicitada > pedido.saldoPendiente) {
                            return@newSuspendedTransaction CuentaMesaResult.CantidadSuperaSaldo
                        }
                        lista += DetallePropuesta(pedido = pedido.row, cantidad = cantidadSolicitada)
                    }
                    lista
                }

            val numeroCuenta = siguienteNumeroCuenta(sesion.id)
            val ahora = LocalDateTime.now()
            val cuentaId =
                CuentaMesaTable.insert {
                    it[CuentaMesaTable.sesionMesaId] = sesion.id
                    it[CuentaMesaTable.numeroCuenta] = numeroCuenta
                    it[CuentaMesaTable.estado] = EstadoCuentaMesa.ACTIVA.codigo
                    it[CuentaMesaTable.fechaCreacion] = ahora
                    it[CuentaMesaTable.activo] = ACTIVE
                }[CuentaMesaTable.id]

            // Inserta detalles y acumula totales
            var subtotal = BigDecimal.ZERO
            var descuento = BigDecimal.ZERO
            var impuesto = BigDecimal.ZERO
            var total = BigDecimal.ZERO
            detallesABackend.forEach { detalle ->
                val cantidad = detalle.cantidad
                val row = detalle.pedido
                val factorCantidad =
                    cantidad.divide(row[PedidoMesaTable.itemCantidad], 6, RoundingMode.HALF_EVEN)
                val detalleSub = row[PedidoMesaTable.itemTotalSinIva].multiply(factorCantidad)
                val detalleDesc = row[PedidoMesaTable.itemMontoDescuento].multiply(factorCantidad)
                val detalleIva =
                    row[PedidoMesaTable.itemTotalConIva]
                        .subtract(
                            row[PedidoMesaTable.itemTotalSinIva],
                        ).multiply(factorCantidad)
                val detalleTotal = row[PedidoMesaTable.itemTotalConIva].multiply(factorCantidad)

                CuentaMesaDetalleTable.insert {
                    it[CuentaMesaDetalleTable.cuentaMesaId] = cuentaId
                    it[CuentaMesaDetalleTable.pedidoMesaId] = row[PedidoMesaTable.id]
                    it[CuentaMesaDetalleTable.productoId] = row[PedidoMesaTable.productoId]
                    it[CuentaMesaDetalleTable.itemAlmacen] = row[PedidoMesaTable.itemAlmacen]
                    it[CuentaMesaDetalleTable.itemCodigo] = row[PedidoMesaTable.itemCodigo]
                    it[CuentaMesaDetalleTable.itemDescripcion] = row[PedidoMesaTable.itemDescripcion]
                    it[CuentaMesaDetalleTable.cantidad] = cantidad
                    it[CuentaMesaDetalleTable.itemPrecioSinIva] = row[PedidoMesaTable.itemPrecioSinIva]
                    it[CuentaMesaDetalleTable.itemDescuento] = row[PedidoMesaTable.itemDescuento]
                    it[CuentaMesaDetalleTable.itemMontoDescuento] = detalleDesc.setScale(SCALE, RoundingMode.HALF_EVEN)
                    it[CuentaMesaDetalleTable.itemPIva] = row[PedidoMesaTable.itemPIva]
                    it[CuentaMesaDetalleTable.itemTotalSinIva] = detalleSub.setScale(SCALE, RoundingMode.HALF_EVEN)
                    it[CuentaMesaDetalleTable.itemTotalConIva] = detalleTotal.setScale(SCALE, RoundingMode.HALF_EVEN)
                    it[CuentaMesaDetalleTable.facturado] = NOT_FACTURADO
                    it[CuentaMesaDetalleTable.fechaCreacion] = ahora
                }
                subtotal = subtotal.add(detalleSub.setScale(SCALE, RoundingMode.HALF_EVEN))
                descuento = descuento.add(detalleDesc.setScale(SCALE, RoundingMode.HALF_EVEN))
                impuesto = impuesto.add(detalleIva.setScale(SCALE, RoundingMode.HALF_EVEN))
                total = total.add(detalleTotal.setScale(SCALE, RoundingMode.HALF_EVEN))
            }

            CuentaMesaTable.update({ CuentaMesaTable.id eq cuentaId }) {
                it[CuentaMesaTable.subtotal] = subtotal
                it[CuentaMesaTable.descuento] = descuento
                it[CuentaMesaTable.impuesto] = impuesto
                it[CuentaMesaTable.total] = total
                it[CuentaMesaTable.saldoRestante] = total
            }

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
            val sesion = sesionActiva(sesionId, mesaId) ?: return@newSuspendedTransaction CuentaMesaResult.SesionNoPerteneceMesa
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
                            it[CuentaMesaIdempotenciaTable.intentos] = existente[CuentaMesaIdempotenciaTable.intentos] + 1
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
        sesionId: Int,
        mesaId: Int,
        cuentaId: Int,
        idempotencyKey: String,
        idFactura: String,
        codFactura: String?,
    ): CuentaMesaResult =
        newSuspendedTransaction<CuentaMesaResult>(kotlin.coroutines.coroutineContext, database) {
            // 1. Idempotencia: si ya está CONFIRMED para este key, devolver cuenta actual sin mutar.
            val existente =
                CuentaMesaIdempotenciaTable
                    .selectAll()
                    .where { CuentaMesaIdempotenciaTable.idempotencyKey eq idempotencyKey }
                    .singleOrNull()
            if (existente != null) {
                val estadoExistente =
                    EstadoCuentaIdempotencia.fromCodigo(existente[CuentaMesaIdempotenciaTable.estado])
                if (estadoExistente == EstadoCuentaIdempotencia.CONFIRMED) {
                    val cuentaFinal =
                        cargarCuenta(sesionId, cuentaId)
                            ?: return@newSuspendedTransaction CuentaMesaResult.CuentaNoEncontrada
                    return@newSuspendedTransaction CuentaMesaResult.IdempotenciaDuplicada
                }
                if (existente[CuentaMesaIdempotenciaTable.cuentaMesaId] != cuentaId ||
                    existente[CuentaMesaIdempotenciaTable.sesionMesaId] != sesionId
                ) {
                    return@newSuspendedTransaction CuentaMesaResult.IdempotenciaDuplicada
                }
            }

            // 2. Validaciones
            val sesion = sesionActiva(sesionId, mesaId) ?: return@newSuspendedTransaction CuentaMesaResult.SesionNoPerteneceMesa
            val cuenta =
                cargarCuenta(sesion.id, cuentaId, forUpdate = true)
                    ?: return@newSuspendedTransaction CuentaMesaResult.CuentaNoEncontrada
            if (cuenta.estado != EstadoCuentaMesa.ACTIVA.codigo) {
                return@newSuspendedTransaction CuentaMesaResult.CuentaNoActiva
            }

            // 3. Aplicar atómicamente la facturación
            val ahora = LocalDateTime.now()
            cuenta.detalle.forEach { d ->
                val pedido = PedidoMesaTable.selectAll().where { PedidoMesaTable.id eq d.pedidoMesaId }.single()
                val actual = pedido[PedidoMesaTable.cantidadFacturada]
                val nueva = actual + d.cantidad.toBigDecimal()
                if (nueva > pedido[PedidoMesaTable.itemCantidad]) {
                    return@newSuspendedTransaction CuentaMesaResult.CantidadSuperaSaldo
                }
                val updated =
                    PedidoMesaTable.update({
                        (PedidoMesaTable.id eq d.pedidoMesaId) and
                            (PedidoMesaTable.cantidadFacturada eq actual)
                    }) {
                        it[cantidadFacturada] = nueva
                    }
                if (updated != 1) {
                    return@newSuspendedTransaction CuentaMesaResult.CantidadSuperaSaldo
                }
            }
            CuentaMesaDetalleTable.update(
                {
                    (CuentaMesaDetalleTable.cuentaMesaId eq cuentaId) and
                        (CuentaMesaDetalleTable.facturado eq NOT_FACTURADO)
                },
            ) {
                it[CuentaMesaDetalleTable.facturado] = FACTURADO
            }

            CuentaMesaTable.update({ CuentaMesaTable.id eq cuentaId }) {
                it[CuentaMesaTable.estado] = EstadoCuentaMesa.PAGADA.codigo
                it[CuentaMesaTable.idFactura] = idFactura
                it[CuentaMesaTable.codFactura] = codFactura
                it[CuentaMesaTable.fechaFactura] = ahora
                it[CuentaMesaTable.fechaCierre] = ahora
                it[CuentaMesaTable.activo] = INACTIVE
                it[CuentaMesaTable.saldoRestante] = BigDecimal.ZERO
            }

            // 4. Idempotencia: queda CONFIRMED
            if (existente != null) {
                CuentaMesaIdempotenciaTable.update({ CuentaMesaIdempotenciaTable.idempotencyKey eq idempotencyKey }) {
                    it[CuentaMesaIdempotenciaTable.estado] = EstadoCuentaIdempotencia.CONFIRMED.codigo
                    it[CuentaMesaIdempotenciaTable.idFacturaResultado] = idFactura
                    it[CuentaMesaIdempotenciaTable.codFacturaResultado] = codFactura
                    it[CuentaMesaIdempotenciaTable.fechaUltimoIntento] = ahora
                    it[CuentaMesaIdempotenciaTable.errorMensaje] = null
                }
            } else {
                CuentaMesaIdempotenciaTable.insert {
                    it[CuentaMesaIdempotenciaTable.idempotencyKey] = idempotencyKey
                    it[CuentaMesaIdempotenciaTable.cuentaMesaId] = cuentaId
                    it[CuentaMesaIdempotenciaTable.sesionMesaId] = sesion.id
                    it[CuentaMesaIdempotenciaTable.estado] = EstadoCuentaIdempotencia.CONFIRMED.codigo
                    it[CuentaMesaIdempotenciaTable.idFacturaResultado] = idFactura
                    it[CuentaMesaIdempotenciaTable.codFacturaResultado] = codFactura
                    it[CuentaMesaIdempotenciaTable.fechaPrimerIntento] = ahora
                    it[CuentaMesaIdempotenciaTable.fechaUltimoIntento] = ahora
                }
            }

            // 5. Cerrar la sesión si está totalmente liquidada
            val restantesActivas = existeCuentaActivaEnSesion(sesion.id)
            val saldoPendiente = existeSaldoPendienteEnSesion(sesion.id)
            val sesionCerrada = !restantesActivas && !saldoPendiente
            if (sesionCerrada) {
                runCerradoPorPago(sesion.id)
            }

            val cuentaFinal = cargarCuenta(sesion.id, cuentaId)!!
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
                it[CuentaMesaIdempotenciaTable.errorMensaje] = errorMensaje.take(MAX_ERROR_LEN)
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

    /**
     * Valida una cuenta antes de crear la factura. Debe invocarse desde una transacción Exposed
     * ya abierta y seguida por [confirmarVentaEnTransaccion] en esa misma transacción.
     */
    fun validarVentaEnTransaccion(
        context: CuentaMesaVentaInput,
        request: ProcessSaleRequest,
        idFactura: String,
    ): CuentaMesaVentaValidada {
        val sesion =
            SesionMesaTable
                .selectAll()
                .where {
                    (SesionMesaTable.id eq context.sesionMesaId) and
                        (SesionMesaTable.areaId eq context.areaId) and
                        (SesionMesaTable.mesaId eq context.mesaId) and
                        (SesionMesaTable.cajaId eq request.factura.idCaja) and
                        (SesionMesaTable.activo eq ACTIVE)
                }.singleOrNull()
                ?: throw InvalidSaleRequestException("La sesión de mesa no pertenece a la caja, área o mesa indicadas")
        val estadoSesion = EstadoSesionMesa.fromCodigo(sesion[SesionMesaTable.estado])
        if (estadoSesion == null || estadoSesion.esFinal) {
            throw InvalidSaleRequestException("La sesión de mesa ya no está activa")
        }

        val cuenta =
            cargarCuenta(context.sesionMesaId, context.cuentaMesaId, forUpdate = true)
                ?: throw InvalidSaleRequestException("Cuenta de mesa no encontrada")
        if (cuenta.estado != EstadoCuentaMesa.ACTIVA.codigo || cuenta.detalle.isEmpty()) {
            throw InvalidSaleRequestException("La cuenta de mesa ya no está disponible para cobro")
        }
        if (dinero(cuenta.total) != dinero(request.factura.totalTotalFactura)) {
            throw InvalidSaleRequestException("El total de la venta no coincide con la cuenta de mesa")
        }

        val esperado =
            cuenta.detalle
                .groupBy { Triple(it.productoId, it.itemAlmacen, dinero(it.itemPrecioSinIva)) }
                .mapValues { (_, lineas) -> cantidad(lineas.sumOf { it.cantidad }) }
        val recibido =
            request.items
                .groupBy { Triple(it.idItem, it.itemAlmacen, dinero(it.itemPrecioSinIva)) }
                .mapValues { (_, lineas) -> cantidad(lineas.sumOf { it.itemCantidadTotal }) }
        if (esperado != recibido) {
            throw InvalidSaleRequestException("Los productos o cantidades de la venta no coinciden con la cuenta de mesa")
        }

        cuenta.detalle.forEach { detalle ->
            val pedido =
                PedidoMesaTable
                    .selectAll()
                    .where {
                        (PedidoMesaTable.id eq detalle.pedidoMesaId) and
                            (PedidoMesaTable.sesionMesaId eq context.sesionMesaId) and
                            (PedidoMesaTable.activo eq ACTIVE)
                    }.singleOrNull()
                    ?: throw InvalidSaleRequestException("Una línea de la cuenta ya no existe")
            if (pedido[PedidoMesaTable.estado] == EstadoPedidoMesa.CANCELADA.codigo ||
                pedido[PedidoMesaTable.estado] != EstadoPedidoMesa.ENTREGADA.codigo
            ) {
                throw InvalidSaleRequestException("Sólo se pueden facturar pedidos entregados y no cancelados")
            }
            val nuevoFacturado = pedido[PedidoMesaTable.cantidadFacturada] + detalle.cantidad.toBigDecimal()
            if (nuevoFacturado > pedido[PedidoMesaTable.itemCantidad]) {
                throw InvalidSaleRequestException("La cantidad de la cuenta supera el saldo pendiente")
            }
        }

        val idem =
            CuentaMesaIdempotenciaTable
                .selectAll()
                .where { CuentaMesaIdempotenciaTable.idempotencyKey eq idFactura }
                .singleOrNull()
        if (idem != null &&
            (
                idem[CuentaMesaIdempotenciaTable.cuentaMesaId] != context.cuentaMesaId ||
                    idem[CuentaMesaIdempotenciaTable.sesionMesaId] != context.sesionMesaId
            )
        ) {
            throw InvalidSaleRequestException("La clave idempotente pertenece a otra cuenta de mesa")
        }
        val ahora = LocalDateTime.now()
        if (idem == null) {
            CuentaMesaIdempotenciaTable.insert {
                it[idempotencyKey] = idFactura
                it[cuentaMesaId] = context.cuentaMesaId
                it[sesionMesaId] = context.sesionMesaId
                it[estado] = EstadoCuentaIdempotencia.SENDING.codigo
                it[intentos] = 1
                it[fechaPrimerIntento] = ahora
                it[fechaUltimoIntento] = ahora
            }
        } else {
            CuentaMesaIdempotenciaTable.update({ CuentaMesaIdempotenciaTable.idempotencyKey eq idFactura }) {
                it[estado] = EstadoCuentaIdempotencia.SENDING.codigo
                it[intentos] = idem[CuentaMesaIdempotenciaTable.intentos] + 1
                it[fechaUltimoIntento] = ahora
                it[errorMensaje] = null
            }
        }
        return CuentaMesaVentaValidada(context, cuenta)
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
                throw InvalidSaleRequestException("La cuenta cambió durante el cobro; reintenta con el saldo actualizado")
            }
        }
        CuentaMesaDetalleTable.update({
            (CuentaMesaDetalleTable.cuentaMesaId eq validada.context.cuentaMesaId) and
                (CuentaMesaDetalleTable.facturado eq NOT_FACTURADO)
        }) {
            it[facturado] = FACTURADO
        }
        CuentaMesaTable.update({ CuentaMesaTable.id eq validada.context.cuentaMesaId }) {
            it[estado] = EstadoCuentaMesa.PAGADA.codigo
            it[CuentaMesaTable.idFactura] = idFactura
            it[CuentaMesaTable.codFactura] = codFactura
            it[fechaFactura] = ahora
            it[fechaCierre] = ahora
            it[activo] = INACTIVE
            it[saldoRestante] = BigDecimal.ZERO
        }
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

    // ------------------------------------------------------------------
    // Helpers internos
    // ------------------------------------------------------------------

    private data class PedidoFacturable(
        val row: ResultRow,
        val saldoPendiente: BigDecimal,
    )

    private data class DetallePropuesta(
        val pedido: ResultRow,
        val cantidad: BigDecimal,
    )

    private fun sesionActiva(
        sesionId: Int,
        mesaId: Int,
        forUpdate: Boolean = false,
    ): SesionSumario? {
        val query =
            SesionMesaTable
                .selectAll()
                .where {
                    (SesionMesaTable.id eq sesionId) and
                        (SesionMesaTable.mesaId eq mesaId) and
                        (SesionMesaTable.activo eq ACTIVE)
                }
        if (forUpdate) query.forUpdate()
        val row =
            query.singleOrNull()
                ?: return null
        return SesionSumario(id = row[SesionMesaTable.id], estado = row[SesionMesaTable.estado])
    }

    private data class SesionSumario(
        val id: Int,
        val estado: String,
    )

    private fun siguienteNumeroCuenta(sesionId: Int): Int {
        val maximo =
            CuentaMesaTable
                .selectAll()
                .where { CuentaMesaTable.sesionMesaId eq sesionId }
                .mapNotNull { it[CuentaMesaTable.numeroCuenta] }
                .maxOrNull()
        return (maximo ?: 0) + 1
    }

    private fun pedidosFacturablesDeSesion(sesionId: Int): List<PedidoFacturable> {
        val cuentasActivas =
            CuentaMesaTable
                .selectAll()
                .where {
                    (CuentaMesaTable.sesionMesaId eq sesionId) and
                        (CuentaMesaTable.estado eq EstadoCuentaMesa.ACTIVA.codigo) and
                        (CuentaMesaTable.activo eq ACTIVE)
                }.map { it[CuentaMesaTable.id] }
        val reservado =
            if (cuentasActivas.isEmpty()) {
                emptyMap()
            } else {
                CuentaMesaDetalleTable
                    .selectAll()
                    .where { CuentaMesaDetalleTable.cuentaMesaId inList cuentasActivas }
                    .groupBy { it[CuentaMesaDetalleTable.pedidoMesaId] }
                    .mapValues { (_, rows) -> rows.fold(BigDecimal.ZERO) { acc, row -> acc + row[CuentaMesaDetalleTable.cantidad] } }
            }
        return PedidoMesaTable
            .selectAll()
            .where {
                (PedidoMesaTable.sesionMesaId eq sesionId) and
                    (PedidoMesaTable.activo eq ACTIVE) and
                    (PedidoMesaTable.estado eq EstadoPedidoMesa.ENTREGADA.codigo)
            }.orderBy(PedidoMesaTable.id)
            .map { row ->
                val cantidad = row[PedidoMesaTable.itemCantidad]
                val facturada = row[PedidoMesaTable.cantidadFacturada]
                val saldo = (cantidad - facturada - (reservado[row[PedidoMesaTable.id]] ?: BigDecimal.ZERO)).stripTrailingZeros()
                PedidoFacturable(row = row, saldoPendiente = saldo)
            }.filter { it.saldoPendiente.compareTo(BigDecimal.ZERO) > 0 }
    }

    private fun cargarCuentas(sesionIds: List<Int>): List<CuentaMesaResponse> =
        if (sesionIds.isEmpty()) {
            emptyList()
        } else {
            CuentaMesaTable
                .selectAll()
                .where { CuentaMesaTable.sesionMesaId inList sesionIds }
                .orderBy(CuentaMesaTable.id)
                .map { it.toCuentaMesaResponse() }
        }

    private fun cargarCuenta(
        sesionId: Int,
        cuentaId: Int,
        forUpdate: Boolean = false,
    ): CuentaMesaResponse? {
        val query =
            CuentaMesaTable
                .selectAll()
                .where {
                    (CuentaMesaTable.id eq cuentaId) and (CuentaMesaTable.sesionMesaId eq sesionId)
                }
        if (forUpdate) query.forUpdate()
        val row =
            query.singleOrNull()
                ?: return null
        val detalles =
            CuentaMesaDetalleTable
                .selectAll()
                .where { CuentaMesaDetalleTable.cuentaMesaId eq cuentaId }
                .orderBy(CuentaMesaDetalleTable.id)
                .map { it.toCuentaDetalleResponse() }
        return row.toCuentaMesaResponse(detalles = detalles)
    }

    private fun existeCuentaActivaEnSesion(sesionId: Int): Boolean =
        CuentaMesaTable
            .selectAll()
            .where {
                (CuentaMesaTable.sesionMesaId eq sesionId) and
                    (CuentaMesaTable.activo eq ACTIVE) and
                    (CuentaMesaTable.estado eq EstadoCuentaMesa.ACTIVA.codigo)
            }.limit(1)
            .singleOrNull() != null

    private fun existeSaldoPendienteEnSesion(sesionId: Int): Boolean =
        PedidoMesaTable
            .selectAll()
            .where {
                (PedidoMesaTable.sesionMesaId eq sesionId) and
                    (PedidoMesaTable.activo eq ACTIVE) and
                    (PedidoMesaTable.estado neq EstadoPedidoMesa.CANCELADA.codigo)
            }.any { row -> row[PedidoMesaTable.cantidadFacturada] < row[PedidoMesaTable.itemCantidad] }

    /**
     * Cierra la sesión por pago completo, dentro de la transacción actual. Idempotente: si la
     * sesión ya no es `ABIERTA`/`CUENTA_SOLICITADA` (race con otra caja), lo ignora.
     *
     * Devuelve la respuesta sintética de la sesión cerrada para auditoría; el caller
     * (`marcarFacturada`) lo usa solo para señalizar `sesionCerrada = true` al POS.
     */
    private fun runCerradoPorPago(sesionId: Int): Boolean {
        val row =
            SesionMesaTable
                .selectAll()
                .where {
                    (SesionMesaTable.id eq sesionId) and
                        (SesionMesaTable.activo eq ACTIVE)
                }.singleOrNull()
                ?: return false
        val estadoActual =
            EstadoSesionMesa.fromCodigo(row[SesionMesaTable.estado])
                ?: return false
        if (estadoActual.esFinal) return false
        val ahora = LocalDateTime.now()
        SesionMesaTable.update({ SesionMesaTable.id eq sesionId }) {
            it[SesionMesaTable.estado] = EstadoSesionMesa.CERRADA_PAGADA.codigo
            it[SesionMesaTable.fechaCierre] = ahora
            it[SesionMesaTable.activo] = INACTIVE
        }
        return true
    }

    private fun ResultRow.toCuentaMesaResponse(detalles: List<CuentaDetalleResponse> = emptyList()): CuentaMesaResponse =
        CuentaMesaResponse(
            id = this[CuentaMesaTable.id],
            sesionMesaId = this[CuentaMesaTable.sesionMesaId],
            numeroCuenta = this[CuentaMesaTable.numeroCuenta],
            estado = this[CuentaMesaTable.estado],
            subtotal = this[CuentaMesaTable.subtotal].toDouble(),
            descuento = this[CuentaMesaTable.descuento].toDouble(),
            impuesto = this[CuentaMesaTable.impuesto].toDouble(),
            total = this[CuentaMesaTable.total].toDouble(),
            saldoRestante = this[CuentaMesaTable.saldoRestante].toDouble(),
            idFactura = this[CuentaMesaTable.idFactura],
            codFactura = this[CuentaMesaTable.codFactura],
            fechaFactura = this[CuentaMesaTable.fechaFactura]?.formatIso(),
            fechaCreacion = this[CuentaMesaTable.fechaCreacion].formatIso(),
            detalle = detalles,
        )

    private fun ResultRow.toCuentaDetalleResponse(): CuentaDetalleResponse =
        CuentaDetalleResponse(
            id = this[CuentaMesaDetalleTable.id],
            cuentaMesaId = this[CuentaMesaDetalleTable.cuentaMesaId],
            pedidoMesaId = this[CuentaMesaDetalleTable.pedidoMesaId],
            productoId = this[CuentaMesaDetalleTable.productoId],
            itemAlmacen = this[CuentaMesaDetalleTable.itemAlmacen],
            itemCodigo = this[CuentaMesaDetalleTable.itemCodigo],
            itemDescripcion = this[CuentaMesaDetalleTable.itemDescripcion],
            cantidad = this[CuentaMesaDetalleTable.cantidad].toDouble(),
            itemPrecioSinIva = this[CuentaMesaDetalleTable.itemPrecioSinIva].toDouble(),
            itemDescuento = this[CuentaMesaDetalleTable.itemDescuento].toDouble(),
            itemMontoDescuento = this[CuentaMesaDetalleTable.itemMontoDescuento].toDouble(),
            itemPIva = this[CuentaMesaDetalleTable.itemPIva].toDouble(),
            itemTotalSinIva = this[CuentaMesaDetalleTable.itemTotalSinIva].toDouble(),
            itemTotalConIva = this[CuentaMesaDetalleTable.itemTotalConIva].toDouble(),
            facturado = this[CuentaMesaDetalleTable.facturado] == FACTURADO,
            fechaCreacion = this[CuentaMesaDetalleTable.fechaCreacion].formatIso(),
        )

    private fun LocalDateTime.formatIso(): String = ISO_FORMATTER.format(this)

    private fun dinero(value: Double): BigDecimal = BigDecimal.valueOf(value).setScale(SCALE, RoundingMode.HALF_EVEN)

    private fun cantidad(value: Double): BigDecimal = BigDecimal.valueOf(value).setScale(3, RoundingMode.HALF_EVEN)

    private companion object {
        const val ACTIVE = 1
        const val INACTIVE = 0
        const val NOT_FACTURADO = 0
        const val FACTURADO = 1
        const val SCALE = 2
        const val MAX_ERROR_LEN = 500
        val ISO_FORMATTER: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME

        /** Estados que NO impiden el cierre por pago: solo ENTREGADA/CANCELADA. */
        val ESTADOS_NO_IMPiden_CIERRE =
            listOf(EstadoPedidoMesa.ENTREGADA.codigo, EstadoPedidoMesa.CANCELADA.codigo)
    }
}

data class CuentaMesaVentaValidada(
    val context: CuentaMesaVentaInput,
    val cuenta: CuentaMesaResponse,
)
