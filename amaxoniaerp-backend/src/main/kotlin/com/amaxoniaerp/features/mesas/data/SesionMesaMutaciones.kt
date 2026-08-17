package com.amaxoniaerp.features.mesas.data

import com.amaxoniaerp.features.mesas.domain.EstadoSesionMesa
import com.amaxoniaerp.features.mesas.domain.SesionMesaResult
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import java.time.LocalDateTime

/**
 * Cuerpo transaccional de abrir. Cada rama devuelve un [SesionMesaResult]; el wrapper
 * `newSuspendedTransaction` propaga ese valor fuera de la transacción.
 */
internal fun abrirInterno(scope: AbrirSesionScope): SesionMesaResult =
    run {
        val sucursalId =
            sucursalDeCaja(scope.cajaId)
                ?: return@run SesionMesaResult.AreaNoPerteneceSucursal

        if (!areaActivaPerteneceASucursal(scope.areaId, sucursalId)) {
            return@run SesionMesaResult.AreaNoPerteneceSucursal
        }

        val mesaOk = mesaActivaPerteneceAArea(scope.mesaId, scope.areaId)
        when {
            !mesaOk.first -> return@run SesionMesaResult.MesaNoPerteneceArea
            !mesaOk.second -> return@run SesionMesaResult.MesaInactiva
        }

        if (existeSesionActiva(scope.mesaId)) {
            return@run SesionMesaResult.SesionYaAbierta
        }

        val ahora = LocalDateTime.now()
        val id =
            SesionMesaTable.insert {
                it[SesionMesaTable.sucursalId] = sucursalId
                it[SesionMesaTable.cajaId] = scope.cajaId
                it[SesionMesaTable.areaId] = scope.areaId
                it[SesionMesaTable.mesaId] = scope.mesaId
                it[SesionMesaTable.usuarioId] = scope.usuarioId
                it[SesionMesaTable.cantidadPersonas] = scope.cantidadPersonas
                it[SesionMesaTable.estado] = EstadoSesionMesa.ABIERTA.codigo
                it[SesionMesaTable.fechaApertura] = ahora
                it[SesionMesaTable.activo] = ACTIVE
            }[SesionMesaTable.id]

        val usuariosById = usuariosById()
        val response =
            sessionRowToResponse(
                id = id,
                sucursalId = sucursalId,
                cajaId = scope.cajaId,
                areaId = scope.areaId,
                mesaId = scope.mesaId,
                usuarioId = scope.usuarioId,
                usuarioNombre = usuariosById[scope.usuarioId],
                cantidadPersonas = scope.cantidadPersonas,
                estado = EstadoSesionMesa.ABIERTA.codigo,
                fechaApertura = ahora,
                fechaCierre = null,
                activo = true,
            )
        SesionMesaResult.Opened(response)
    }

/**
 * Transición de ciclo de cuenta entre `ABIERTA` y `CUENTA_SOLICITADA` (biye). El
 * [destino] debe ser uno de esos dos estados. Cualquier otro estado de origen se rechaza
 * con `SesionYaFinalizada`.
 */
internal fun transicionarSesionCuenta(
    sesionId: Int,
    destino: EstadoSesionMesa,
): SesionMesaResult =
    run {
        require(destino == EstadoSesionMesa.ABIERTA || destino == EstadoSesionMesa.CUENTA_SOLICITADA) {
            "transicionarSesionCuenta solo admite ABIERTA<->CUENTA_SOLICITADA, no $destino"
        }
        val sesion =
            SesionMesaTable
                .selectAll()
                .where { SesionMesaTable.id eq sesionId }
                .singleOrNull()
                ?: return@run SesionMesaResult.SesionNoEncontrada

        val estadoActual =
            EstadoSesionMesa.fromCodigo(sesion[SesionMesaTable.estado])
                ?: return@run SesionMesaResult.SesionYaFinalizada
        val valido =
            (estadoActual == EstadoSesionMesa.ABIERTA && destino == EstadoSesionMesa.CUENTA_SOLICITADA) ||
                (estadoActual == EstadoSesionMesa.CUENTA_SOLICITADA && destino == EstadoSesionMesa.ABIERTA)
        if (!valido) return@run SesionMesaResult.SesionYaFinalizada

        SesionMesaTable.update({ SesionMesaTable.id eq sesionId }) {
            it[SesionMesaTable.estado] = destino.codigo
        }
        SesionMesaResult.Closed(sesion.toSesionMesaResponse().copy(estado = destino.codigo))
    }

/**
 * Cierra la sesión por pago completo: transiciona `ABIERTA` o `CUENTA_SOLICITADA` hacia
 * `CERRADA_PAGADA` y registra `fechaCierre`. No se exige `tieneOperaciones == false`
 * porque el caller (`CuentaMesaRepository.marcarFacturada`) garantiza que el saldo de
 * cuentas activas es 0 y no queda ninguna cantidad no cancelada sin facturar. Por eso una
 * línea todavía no entregada sí mantiene abierta la mesa.
 */
internal fun cerrarPorPagoInterno(sesionId: Int): SesionMesaResult =
    run {
        val sesion =
            SesionMesaTable
                .selectAll()
                .where { SesionMesaTable.id eq sesionId }
                .singleOrNull()
                ?: return@run SesionMesaResult.SesionNoEncontrada

        val estadoActual =
            EstadoSesionMesa.fromCodigo(sesion[SesionMesaTable.estado])
                ?: return@run SesionMesaResult.SesionYaFinalizada
        if (estadoActual.esFinal) return@run SesionMesaResult.SesionYaFinalizada

        val ahora = LocalDateTime.now()
        SesionMesaTable.update({ SesionMesaTable.id eq sesionId }) {
            it[SesionMesaTable.estado] = EstadoSesionMesa.CERRADA_PAGADA.codigo
            it[SesionMesaTable.fechaCierre] = ahora
            it[SesionMesaTable.activo] = INACTIVE
        }
        SesionMesaResult.Closed(
            sesion.toSesionMesaResponse().copy(
                estado = EstadoSesionMesa.CERRADA_PAGADA.codigo,
                fechaCierre = ahora.formatSesionIso(),
                activo = false,
            ),
        )
    }

internal fun mutarSesionInterno(
    sesionId: Int,
    destino: EstadoSesionMesa,
    isCancel: Boolean,
    tieneOperaciones: (sesionId: Int, mesaId: Int) -> Boolean,
): SesionMesaResult =
    run {
        val sesion =
            SesionMesaTable
                .selectAll()
                .where { SesionMesaTable.id eq sesionId }
                .singleOrNull()
                ?: return@run SesionMesaResult.SesionNoEncontrada

        val estadoCodigo = sesion[SesionMesaTable.estado]
        val estadoActual =
            EstadoSesionMesa.fromCodigo(estadoCodigo)
                ?: return@run SesionMesaResult.SesionYaFinalizada
        // Cerrar/Cancelar directo: solo se permite si la sesión está ABIERTA o CUENTA_SOLICITADA
        // (no pagada). El paso por caja real (pago completo) usa cerrarPorPago.
        if (estadoActual != EstadoSesionMesa.ABIERTA && estadoActual != EstadoSesionMesa.CUENTA_SOLICITADA) {
            return@run SesionMesaResult.SesionYaFinalizada
        }

        val mesaId = sesion[SesionMesaTable.mesaId]
        if (tieneOperaciones(sesionId, mesaId)) {
            return@run SesionMesaResult.SesionConOperaciones
        }

        val ahora = LocalDateTime.now()
        if (isCancel) {
            // Cancelación: se elimina físicamente la sesión para que la mesa pueda
            // reabrirse sin dejar histórico de "apertura accidental". El índice único
            // (mesa_id, activo) sigue siendo respetado por el flujo normal.
            SesionMesaTable.deleteWhere { SesionMesaTable.id eq sesionId }
        } else {
            SesionMesaTable.update({ SesionMesaTable.id eq sesionId }) {
                it[SesionMesaTable.estado] = destino.codigo
                it[SesionMesaTable.fechaCierre] = ahora
                it[SesionMesaTable.activo] = INACTIVE
            }
        }

        val usuarioNombre = usuarioNombre(sesion[SesionMesaTable.usuarioId])
        val response =
            sessionRowToResponse(
                id = sesionId,
                sucursalId = sesion[SesionMesaTable.sucursalId],
                cajaId = sesion[SesionMesaTable.cajaId],
                areaId = sesion[SesionMesaTable.areaId],
                mesaId = mesaId,
                usuarioId = sesion[SesionMesaTable.usuarioId],
                usuarioNombre = usuarioNombre,
                cantidadPersonas = sesion[SesionMesaTable.cantidadPersonas],
                estado = destino.codigo,
                fechaApertura = sesion[SesionMesaTable.fechaApertura],
                fechaCierre = ahora,
                activo = false,
            )
        if (isCancel) SesionMesaResult.Cancelled(response) else SesionMesaResult.Closed(response)
    }

/**
 * Datos que el routing ya validó/derivó para pasar al repositorio al abrir una sesión.
 */
data class AbrirSesionScope(
    val cajaId: String,
    val areaId: Int,
    val mesaId: Int,
    val usuarioId: Int,
    val cantidadPersonas: Int,
)

/**
 * Consulta de operaciones asociadas a una sesión: la usa [SesionMesaRepository] para decidir
 * si una sesión se puede cerrar o cancelar. La implementación real vive en
 * [PedidoMesaRepository.tieneOperaciones].
 *
 * La separación evita dependencia circular: `SesionMesaRepository` solo necesita una consulta
 * de lectura, no todo el repositorio de pedidos.
 */
fun interface PedidoMesaOperacionesLookup {
    /**
     * Devuelve `true` si existe al menos una operación activa (línea de pedido con estado
     * distinto de `ENTREGADA`/`CANCELADA`) asociada a la sesión.
     *
     * Debe ejecutarse dentro de la transacción del llamador para compartir la conexión.
     */
    fun tieneOperaciones(
        sesionId: Int,
        mesaId: Int,
    ): Boolean
}

/**
 * Implementación por defecto que dice "no hay operaciones": preserva el contrato con los
 * tests de la fase anterior y es inyectada para los tests de sesión que no seedean pedidos.
 */
object NoOpPedidoMesaOperacionesLookup : PedidoMesaOperacionesLookup {
    override fun tieneOperaciones(
        sesionId: Int,
        mesaId: Int,
    ): Boolean = false
}
