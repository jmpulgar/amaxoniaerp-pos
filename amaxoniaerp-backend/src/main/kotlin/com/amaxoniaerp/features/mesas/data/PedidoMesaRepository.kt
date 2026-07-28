package com.amaxoniaerp.features.mesas.data

import com.amaxoniaerp.core.database.dbQuery
import com.amaxoniaerp.features.mesas.domain.CrearPedidoMesaItemRequest
import com.amaxoniaerp.features.mesas.domain.CrearPedidoMesaRequest
import com.amaxoniaerp.features.mesas.domain.EstadoPedidoMesa
import com.amaxoniaerp.features.mesas.domain.EstadoSesionMesa
import com.amaxoniaerp.features.mesas.domain.PedidoMesaResponse
import com.amaxoniaerp.features.mesas.domain.PedidoMesaResult
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.SqlExpressionBuilder.notInList
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.update
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Operaciones de pedidos y comandas ligadas a una sesión de mesa.
 *
 * Reglas contractuales:
 * - Todas las operaciones validan en la misma transacción que la sesión existe, sigue activa
 *   (`estado = ABIERTA, activo = 1`) y pertenece a la mesa indicada en la URL.
 * - `crear` con `enviarInmediato = false` deja las nuevas líneas en estado `PENDIENTE`)
 *   y `comandaSecuencia = NULL` (buffer del POS).
 * - `crear` con `enviarInmediato = true` asigna automáticamente `comandaSecuencia` = siguiente
 *   secuencia de la sesión y marca las líneas como `ENVIADA`.
 * - `enviarComanda` recoge todos los `PENDIENTE` (o los indicados vía `pedidoIds`), les asigna
 *   el siguiente `comandaSecuencia` y los pasa a `ENVIADA`. Si no hay pedidos pendientes
 *   devuelve [PedidoMesaResult.SinPedidosPendientes].
 * - `cambiarEstado` actualiza la línea entre los estados no finales hacia adelante, o a
 *   `CANCELADA` desde cualquier estado no final. Las líneas `ENTREGADA` o `CANCELADA` no se
 *   pueden seguir moviendo.
 */
class PedidoMesaRepository {
    /**
     * Lista las líneas de la sesión, opcionalmente filtradas por estado.
     */
    suspend fun listar(
        database: Database,
        sesionId: Int,
        mesaId: Int,
        estado: EstadoPedidoMesa? = null,
    ): PedidoMesaResult =
        dbQuery(database) {
            val sesion = sesionActivaPorMesa(sesionId, mesaId) ?: return@dbQuery PedidoMesaResult.SesionNoPerteneceMesa
            val query =
                if (estado != null) {
                    PedidoMesaTable
                        .selectAll()
                        .where {
                            (PedidoMesaTable.sesionMesaId eq sesion.id) and
                                (PedidoMesaTable.activo eq ACTIVE) and
                                (PedidoMesaTable.estado eq estado.codigo)
                        }
                } else {
                    PedidoMesaTable
                        .selectAll()
                        .where {
                            (PedidoMesaTable.sesionMesaId eq sesion.id) and
                                (PedidoMesaTable.activo eq ACTIVE)
                        }
                }
            val pedidos = query.orderBy(PedidoMesaTable.id).map { it.toPedidoMesaResponse(mesaId) }
            PedidoMesaResult.Listado(pedidos)
        }

    /**
     * Crea líneas de pedido sobre la sesión.
     *
     * Si `enviarInmediato = true`, las líneas se marcan `ENVIADA` y se les asigna el siguiente
     * `comandaSecuencia` de la sesión. Si `false`, quedan `PENDIENTE` para enviar más tarde.
     */
    suspend fun crear(
        database: Database,
        sesionId: Int,
        mesaId: Int,
        request: CrearPedidoMesaRequest,
    ): PedidoMesaResult {
        if (request.items.isEmpty()) return PedidoMesaResult.SinItemsParaCrear

        return newSuspendedTransaction<PedidoMesaResult>(kotlin.coroutines.coroutineContext, database) {
            val sesion = sesionActivaPorMesa(sesionId, mesaId) ?: return@newSuspendedTransaction PedidoMesaResult.SesionNoPerteneceMesa
            if (EstadoSesionMesa.fromCodigo(sesion.estado)?.admitePedidos != true) {
                return@newSuspendedTransaction PedidoMesaResult.SesionNoActiva
            }

            val ahora = LocalDateTime.now()
            val secuencia: Int?
            val estadoCodigo: String
            val fechaEnvio: LocalDateTime?
            if (request.enviarInmediato) {
                secuencia = siguienteSecuenciaComanda(sesion.id)
                estadoCodigo = EstadoPedidoMesa.ENVIADA.codigo
                fechaEnvio = ahora
            } else {
                secuencia = null
                estadoCodigo = EstadoPedidoMesa.PENDIENTE.codigo
                fechaEnvio = null
            }

            val nuevos =
                request.items.map { item ->
                    val id =
                        insertarLinea(
                            sesionId = sesion.id,
                            comandaSecuencia = secuencia,
                            estado = estadoCodigo,
                            fechaCreacion = ahora,
                            fechaEnvio = fechaEnvio,
                            item = item,
                        )
                    PedidoMesaTable
                        .selectAll()
                        .where { PedidoMesaTable.id eq id }
                        .single()
                        .toPedidoMesaResponse(mesaId)
                }

            PedidoMesaResult.Creado(
                sesionMesaId = sesion.id,
                comandaSecuencia = secuencia,
                pedidos = nuevos,
            )
        }
    }

    /**
     * Envía los pedidos PENDIENTE (todos o los indicados por `pedidoIds`) a cocina/bar: les
     * asigna el siguiente `comandaSecuencia` y los pasa a `ENVIADA`.
     *
     * Devuelve [PedidoMesaResult.SinPedidosPendientes] si no había pedidos por enviar.
     */
    suspend fun enviarComanda(
        database: Database,
        sesionId: Int,
        mesaId: Int,
        pedidoIds: List<Int>,
    ): PedidoMesaResult =
        newSuspendedTransaction<PedidoMesaResult>(kotlin.coroutines.coroutineContext, database) {
            val sesion = sesionActivaPorMesa(sesionId, mesaId) ?: return@newSuspendedTransaction PedidoMesaResult.SesionNoPerteneceMesa
            if (EstadoSesionMesa.fromCodigo(sesion.estado)?.admitePedidos != true) {
                return@newSuspendedTransaction PedidoMesaResult.SesionNoActiva
            }

            val idsPendientes =
                if (pedidoIds.isEmpty()) {
                    PedidoMesaTable
                        .selectAll()
                        .where {
                            (PedidoMesaTable.sesionMesaId eq sesion.id) and
                                (PedidoMesaTable.estado eq EstadoPedidoMesa.PENDIENTE.codigo) and
                                (PedidoMesaTable.activo eq ACTIVE)
                        }
                        .orderBy(PedidoMesaTable.id)
                        .map { it[PedidoMesaTable.id] }
                } else {
                    PedidoMesaTable
                        .selectAll()
                        .where {
                            (PedidoMesaTable.sesionMesaId eq sesion.id) and
                                (PedidoMesaTable.estado eq EstadoPedidoMesa.PENDIENTE.codigo) and
                                (PedidoMesaTable.activo eq ACTIVE) and
                                (PedidoMesaTable.id inList pedidoIds)
                        }
                        .orderBy(PedidoMesaTable.id)
                        .map { it[PedidoMesaTable.id] }
                }

            if (idsPendientes.isEmpty()) return@newSuspendedTransaction PedidoMesaResult.SinPedidosPendientes

            val ahora = LocalDateTime.now()
            val secuencia = siguienteSecuenciaComanda(sesion.id)

            PedidoMesaTable.update({ PedidoMesaTable.id inList idsPendientes }) {
                it[PedidoMesaTable.comandaSecuencia] = secuencia
                it[PedidoMesaTable.estado] = EstadoPedidoMesa.ENVIADA.codigo
                it[PedidoMesaTable.fechaEnvio] = ahora
            }

            val actualizados =
                PedidoMesaTable
                    .selectAll()
                    .where { PedidoMesaTable.id inList idsPendientes }
                    .orderBy(PedidoMesaTable.id)
                    .map { it.toPedidoMesaResponse(mesaId) }

            PedidoMesaResult.Enviada(
                comandaSecuencia = secuencia,
                pedidos = actualizados,
            )
        }

    /**
     * Cambia el estado de una línea. Solo avanza entre estados no finales o anula; una línea
     * `ENTREGADA` o `CANCELADA` no se mueve nunca.
     */
    suspend fun cambiarEstado(
        database: Database,
        sesionId: Int,
        mesaId: Int,
        pedidoId: Int,
        destino: EstadoPedidoMesa,
    ): PedidoMesaResult =
        newSuspendedTransaction<PedidoMesaResult>(kotlin.coroutines.coroutineContext, database) {
            val sesion = sesionActivaPorMesa(sesionId, mesaId) ?: return@newSuspendedTransaction PedidoMesaResult.SesionNoPerteneceMesa
            val linea =
                PedidoMesaTable
                    .selectAll()
                    .where {
                        (PedidoMesaTable.id eq pedidoId) and
                            (PedidoMesaTable.sesionMesaId eq sesion.id) and
                            (PedidoMesaTable.activo eq ACTIVE)
                    }
                    .singleOrNull()
                    ?: return@newSuspendedTransaction PedidoMesaResult.PedidoNoEncontrado

            val actual = EstadoPedidoMesa.fromCodigo(linea[PedidoMesaTable.estado]) ?: return@newSuspendedTransaction PedidoMesaResult.EstadoInvalido
            if (!transicionValida(actual, destino)) return@newSuspendedTransaction PedidoMesaResult.EstadoInvalido

            val ahora = LocalDateTime.now()
            PedidoMesaTable.update({ PedidoMesaTable.id eq pedidoId }) {
                it[PedidoMesaTable.estado] = destino.codigo
                if (destino == EstadoPedidoMesa.ENTREGADA) it[PedidoMesaTable.fechaEntrega] = ahora
            }

            val actualizado =
                PedidoMesaTable
                    .selectAll()
                    .where { PedidoMesaTable.id eq pedidoId }
                    .single()
                    .toPedidoMesaResponse(mesaId)

            PedidoMesaResult.EstadoActualizado(actualizado)
        }

    /**
     * ¿Tiene la sesión operaciones que impidan cerrarla o cancelarla?
     *
     * Cualquier línea activa con estado distinto de `ENTREGADA` o `CANCELADA` cuenta como
     * operación pendiente: la sesión NO se puede cerrar ni cancelar mientras exista alguna.
     *
     * Es **sincrónica** a propósito: el llamador ([SesionMesaRepository.mutarSesionInterno])
     * corre dentro de una transacción suspendida que comparte la conexión, así ejecutamos la
     * consulta contra esa misma conexión para atomicidad. Si se invoca desde afuera debe ser
     * envuelto en una transacción por el llamador.
     */
    fun tieneOperaciones(
        sesionId: Int,
        @Suppress("UNUSED_PARAMETER") mesaId: Int,
    ): Boolean =
        PedidoMesaTable
            .selectAll()
            .where {
                (PedidoMesaTable.sesionMesaId eq sesionId) and
                    (PedidoMesaTable.activo eq ACTIVE) and
                    (PedidoMesaTable.estado notInList ESTADOS_FINALES_CODIGO)
            }
            .limit(1)
            .singleOrNull() != null

    // ------------------------------------------------------------------
    // Helpers internos
    // ------------------------------------------------------------------

    private data class SesionSumario(val id: Int, val estado: String)

    private fun sesionActivaPorMesa(
        sesionId: Int,
        mesaId: Int,
    ): SesionSumario? {
        val row =
            SesionMesaTable
                .selectAll()
                .where {
                    (SesionMesaTable.id eq sesionId) and
                        (SesionMesaTable.mesaId eq mesaId)
                }
                .singleOrNull()
                ?: return null
        return SesionSumario(id = row[SesionMesaTable.id], estado = row[SesionMesaTable.estado])
    }

    /** Siguiente número de comanda dentro de la sesión (1, 2, 3, ...). */
    private fun siguienteSecuenciaComanda(sesionId: Int): Int {
        val maximo =
            PedidoMesaTable
                .selectAll()
                .where { PedidoMesaTable.sesionMesaId eq sesionId }
                .mapNotNull { it[PedidoMesaTable.comandaSecuencia] }
                .maxOrNull()
        return (maximo ?: 0) + 1
    }

    private fun ResultRow.toPedidoMesaResponse(mesaId: Int): PedidoMesaResponse =
        PedidoMesaResponse(
            id = this[PedidoMesaTable.id],
            sesionMesaId = this[PedidoMesaTable.sesionMesaId],
            mesaId = mesaId,
            comandaSecuencia = this[PedidoMesaTable.comandaSecuencia],
            productoId = this[PedidoMesaTable.productoId],
            itemAlmacen = this[PedidoMesaTable.itemAlmacen],
            itemCodigo = this[PedidoMesaTable.itemCodigo],
            itemDescripcion = this[PedidoMesaTable.itemDescripcion],
            itemCantidad = this[PedidoMesaTable.itemCantidad].toDouble(),
            itemPrecioSinIva = this[PedidoMesaTable.itemPrecioSinIva].toDouble(),
            itemDescuento = this[PedidoMesaTable.itemDescuento].toDouble(),
            itemMontoDescuento = this[PedidoMesaTable.itemMontoDescuento].toDouble(),
            itemPIva = this[PedidoMesaTable.itemPIva].toDouble(),
            itemTotalSinIva = this[PedidoMesaTable.itemTotalSinIva].toDouble(),
            itemTotalConIva = this[PedidoMesaTable.itemTotalConIva].toDouble(),
            cantidadBulto = this[PedidoMesaTable.cantidadBulto],
            unidadEmpaque = this[PedidoMesaTable.unidadEmpaque],
            notas = this[PedidoMesaTable.notas],
            promocionId = this[PedidoMesaTable.promocionId],
            promocionTipo = this[PedidoMesaTable.promocionTipo],
            promocionDetalleId = this[PedidoMesaTable.promocionDetalleId],
            estado = this[PedidoMesaTable.estado],
            cantidadFacturada = this[PedidoMesaTable.cantidadFacturada].toDouble(),
            fechaCreacion = this[PedidoMesaTable.fechaCreacion].formatIso(),
            fechaEnvio = this[PedidoMesaTable.fechaEnvio]?.formatIso(),
            fechaEntrega = this[PedidoMesaTable.fechaEntrega]?.formatIso(),
        )

    private fun insertarLinea(
        sesionId: Int,
        comandaSecuencia: Int?,
        estado: String,
        fechaCreacion: LocalDateTime,
        fechaEnvio: LocalDateTime?,
        item: CrearPedidoMesaItemRequest,
    ): Int =
        PedidoMesaTable.insert {
            it[PedidoMesaTable.sesionMesaId] = sesionId
            it[PedidoMesaTable.comandaSecuencia] = comandaSecuencia
            it[PedidoMesaTable.productoId] = item.productoId
            it[PedidoMesaTable.itemAlmacen] = item.itemAlmacen
            it[PedidoMesaTable.itemCodigo] = item.itemCodigo
            it[PedidoMesaTable.itemDescripcion] = item.itemDescripcion
            it[PedidoMesaTable.itemCantidad] = item.itemCantidad.toBigDecimal()
            it[PedidoMesaTable.itemPrecioSinIva] = item.itemPrecioSinIva.toBigDecimal()
            it[PedidoMesaTable.itemDescuento] = item.itemDescuento.toBigDecimal()
            it[PedidoMesaTable.itemMontoDescuento] = item.itemMontoDescuento.toBigDecimal()
            it[PedidoMesaTable.itemPIva] = item.itemPIva.toBigDecimal()
            it[PedidoMesaTable.itemTotalSinIva] = item.itemTotalSinIva.toBigDecimal()
            it[PedidoMesaTable.itemTotalConIva] = item.itemTotalConIva.toBigDecimal()
            it[PedidoMesaTable.cantidadBulto] = item.cantidadBulto
            it[PedidoMesaTable.unidadEmpaque] = item.unidadEmpaque
            it[PedidoMesaTable.notas] = item.notas
            it[PedidoMesaTable.promocionId] = item.promocionId
            it[PedidoMesaTable.promocionTipo] = item.promocionTipo
            it[PedidoMesaTable.promocionDetalleId] = item.promocionDetalleId
            it[PedidoMesaTable.estado] = estado
            it[PedidoMesaTable.fechaCreacion] = fechaCreacion
            it[PedidoMesaTable.fechaEnvio] = fechaEnvio
        }[PedidoMesaTable.id]

    private fun transicionValida(
        actual: EstadoPedidoMesa,
        destino: EstadoPedidoMesa,
    ): Boolean {
        if (actual.esFinal) return false
        if (destino == EstadoPedidoMesa.CANCELADA) return true
        // Avance hacia adelante; nunca retroceder a PENDIENTE una vez enviado.
        val ordenActual = ORDEN_ESTADOS.getValue(actual)
        val ordenDestino = ORDEN_ESTADOS.getValue(destino)
        return ordenDestino > ordenActual && destino != EstadoPedidoMesa.PENDIENTE
    }

    private fun LocalDateTime.formatIso(): String = ISO_FORMATTER.format(this)

    private companion object {
        const val ACTIVE = 1
        val ISO_FORMATTER: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME

        val ESTADOS_FINALES_CODIGO = listOf(EstadoPedidoMesa.ENTREGADA.codigo, EstadoPedidoMesa.CANCELADA.codigo)

        /** Orden lógico para validar que las transiciones solo avancen. */
        val ORDEN_ESTADOS: Map<EstadoPedidoMesa, Int> =
            mapOf(
                EstadoPedidoMesa.PENDIENTE to 1,
                EstadoPedidoMesa.ENVIADA to 2,
                EstadoPedidoMesa.EN_PREPARACION to 3,
                EstadoPedidoMesa.LISTA to 4,
                EstadoPedidoMesa.ENTREGADA to 5,
                EstadoPedidoMesa.CANCELADA to 6,
            )
    }
}
