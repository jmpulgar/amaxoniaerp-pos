package com.amaxoniaerp.features.mesas.data

import com.amaxoniaerp.core.database.dbQuery
import com.amaxoniaerp.features.auth.data.UsersTable
import com.amaxoniaerp.features.caja.data.CajaTable
import com.amaxoniaerp.features.mesas.domain.EstadoMesaOperativo
import com.amaxoniaerp.features.mesas.domain.EstadoSesionMesa
import com.amaxoniaerp.features.mesas.domain.MesaEstadoResponse
import com.amaxoniaerp.features.mesas.domain.SesionMesaResponse
import com.amaxoniaerp.features.mesas.domain.SesionMesaResult
import org.jetbrains.exposed.exceptions.ExposedSQLException
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.update
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Operaciones de sesión de mesa para el POS.
 *
 * Reglas contractuales:
 * - Toda operación deriva `sucursal_id` desde la `caja` recibida; nunca se confía en el cliente.
 * - Antes de abrir se valida: caja existente y con sucursal, área perteneciente a esa sucursal
 *   y activa, mesa perteneciente al área y activa, ausencia de otra sesión activa. El índice
 *   único `(mesa_id, activo)` protege contra race conditions entre cajas concurrentes.
 * - Cerrar/Cancelar solo se permiten si la sesión no tiene operaciones asociadas. Una
 *   operación asociada es cualquier línea de `pedido_mesa` con estado distinto de
 *   `ENTREGADA`/`CANCELADA`: pedidos pendientes de enviar, o enviados que todavía no se han
 *   entregado o anulado. La verificación real vive en [PedidoMesaRepository.tieneOperaciones]
 *   y se invoca desde [mutarSesionInterno] dentro de la misma transacción.
 */
class SesionMesaRepository(
    /**
     * Repositorio de pedidos de mesa para que [tieneOperaciones] consulte las operaciones
     * reales asociadas a la sesión. Por defecto se inyecta una implementación que devuelve
     * siempre `false`, lo que preserva el contrato con los tests existentes de la fase
     * anterior a pedidos y permite que el wiring produccionista lo reemplace con la consulta
     * a `pedido_mesa`.
     */
    private val pedidos: PedidoMesaOperacionesLookup = NoOpPedidoMesaOperacionesLookup,
) {
    /**
     * Estados derivados de todas las mesas activas de [areaId].
     *
     * Una mesa sin sesión activa se reporta como `DISPONIBLE`. Una mesa con sesión activa se
     * reporta como `OCUPADA` con la sesión vigente incluida.
     *
     * Devuelve [SesionMesaResult.AreaNoPerteneceSucursal] si el área no existe en la sucursal
     * de la caja o no está activa.
     */
    suspend fun listarEstados(
        database: Database,
        sucursalId: Int,
        areaId: Int,
    ): SesionMesaResult =
        dbQuery(database) {
            if (!areaActivaPerteneceASucursal(areaId, sucursalId)) {
                return@dbQuery SesionMesaResult.AreaNoPerteneceSucursal
            }

            val mesas =
                MesasTable
                    .selectAll()
                    .where { (MesasTable.plantaId eq areaId) and (MesasTable.activo eq ACTIVE) }
                    .orderBy(MesasTable.id)
                    .map { it[MesasTable.id] }
                    .toSet()

            val sessionsActivasByMesa = sesionesActivasByMesa(mesas)

            val estados =
                mesas.sorted().map { mesaId ->
                    val sesion = sessionsActivasByMesa[mesaId]
                    if (sesion != null) {
                        MesaEstadoResponse(
                            mesaId = mesaId,
                            estado = EstadoMesaOperativo.OCUPADA.name,
                            sesion = sesion,
                        )
                    } else {
                        MesaEstadoResponse(
                            mesaId = mesaId,
                            estado = EstadoMesaOperativo.DISPONIBLE.name,
                            sesion = null,
                        )
                    }
                }

            SesionMesaResult.States(estados)
        }

    /**
     * Abre una sesión operativa sobre la mesa indicada.
     *
     * Envuelve todas las validaciones y el `INSERT` en la misma transacción para que el
     * índice único `(mesa_id, activo)` aplique de forma atómica. Si dos cajas intentasen abrir
     * la misma mesa en el mismo instante, el segundo `INSERT` se resolvería con
     * `DuplicateKeyException` se traduce a [SesionMesaResult.SesionYaAbierta].
     */
    suspend fun abrir(
        database: Database,
        scope: AbrirSesionScope,
    ): SesionMesaResult {
        if (scope.cantidadPersonas <= 0) return SesionMesaResult.CantidadPersonasInvalida

        return try {
            newSuspendedTransaction<SesionMesaResult>(kotlin.coroutines.coroutineContext, database) {
                abrirInterno(scope)
            }
        } catch (e: ExposedSQLException) {
            // Race condition contra el índice único: otra caja acabó de ganar la apertura.
            if (esViolacionUniqueSesion(e)) SesionMesaResult.SesionYaAbierta else throw e
        }
    }

    /**
     * Recupera la sesión activa de una mesa, o `null` si no existe.
     *
     * No filtra por `areaId`/`sucursalId` porque ya está implícito en la mesa: si la mesa no
     * existe o está inactiva, simplemente no habrá sesión activa y se devuelve `null`.
     */
    suspend fun sesionActiva(
        database: Database,
        mesaId: Int,
    ): SesionMesaResult =
        dbQuery(database) {
            SesionMesaResult.Found(sesionActivaDeMesa(mesaId))
        }

    /**
     * Cierra la sesión normalmente. Solo permitido si no tiene operaciones asociadas y la
     * sesión sigue abierta.
     */
    suspend fun cerrar(
        database: Database,
        sesionId: Int,
    ): SesionMesaResult =
        try {
            newSuspendedTransaction<SesionMesaResult>(kotlin.coroutines.coroutineContext, database) {
                mutarSesionInterno(sesionId, EstadoSesionMesa.CERRADA, isCancel = false)
            }
        } catch (e: ExposedSQLException) {
            if (esViolacionUniqueSesion(e)) SesionMesaResult.SesionYaAbierta else throw e
        }

    /**
     * Anula la sesión (apertura accidental, mesa equivocada). Solo si no tiene operaciones.
     * Diferencia operacional de [cerrar]: una cancelación no cuenta como consumo real para
     * reportes de ocupación y permite reabrir la mesa sin rezagarla.
     */
    suspend fun cancelar(
        database: Database,
        sesionId: Int,
    ): SesionMesaResult =
        try {
            newSuspendedTransaction<SesionMesaResult>(kotlin.coroutines.coroutineContext, database) {
                mutarSesionInterno(sesionId, EstadoSesionMesa.CANCELADA, isCancel = true)
            }
        } catch (e: ExposedSQLException) {
            if (esViolacionUniqueSesion(e)) SesionMesaResult.SesionYaAbierta else throw e
        }

    /**
     * Marca la sesión como `CUENTA_SOLICITADA`: el operario pidió la cuenta pero todavía no
     * ha pagado. Sigue admitiendo pedidos (un cliente puede agregar items tras pedir la
     * cuenta antes de pagar). Solo v&aacute;lido desde `ABIERTA`.
     */
    suspend fun solicitarCuenta(
        database: Database,
        sesionId: Int,
    ): SesionMesaResult =
        newSuspendedTransaction<SesionMesaResult>(kotlin.coroutines.coroutineContext, database) {
            transicionarSesionCuenta(sesionId, destino = EstadoSesionMesa.CUENTA_SOLICITADA)
        }

    /**
     * Revierte `CUENTA_SOLICITADA` hacia `ABIERTA`: el operario canceló la solicitud de cuenta
     * (decidió agregar más productos o esperar). Solo válida desde `CUENTA_SOLICITADA`.
     */
    suspend fun cancelarSolicitudCuenta(
        database: Database,
        sesionId: Int,
    ): SesionMesaResult =
        newSuspendedTransaction<SesionMesaResult>(kotlin.coroutines.coroutineContext, database) {
            transicionarSesionCuenta(sesionId, destino = EstadoSesionMesa.ABIERTA)
        }

    /**
     * Cierra la sesión por liquidación total de cuenta (`CERRADA_PAGADA`). Solo se invoca
     * cuando el saldo de cuentas activas de la sesión es cero y no quedan pedidos pendientes
     * en cocina (`ENTREGADA`/`CANCELADA` en todas las líneas). El caller es
     * `CuentaMesaRepository.marcarFacturada`, que ya validó el saldo dentro de la misma tx.
     */
    suspend fun cerrarPorPago(
        database: Database,
        sesionId: Int,
    ): SesionMesaResult =
        newSuspendedTransaction<SesionMesaResult>(kotlin.coroutines.coroutineContext, database) {
            cerrarPorPagoInterno(sesionId)
        }

    // ------------------------------------------------------------------
    // Helpers internos (se ejecutan dentro de la tx suspendida)
    // ------------------------------------------------------------------

    /**
     * Cuerpo transaccional de [abrir]. Cada rama devuelve un [SesionMesaResult]; el wrapper
     * `newSuspendedTransaction` propaga ese valor fuera de la transacción.
     */
    private fun abrirInterno(scope: AbrirSesionScope): SesionMesaResult {
        val sucursalId =
            sucursalDeCaja(scope.cajaId)
                ?: return SesionMesaResult.AreaNoPerteneceSucursal

        if (!areaActivaPerteneceASucursal(scope.areaId, sucursalId)) {
            return SesionMesaResult.AreaNoPerteneceSucursal
        }

        val mesaOk = mesaActivaPerteneceAArea(scope.mesaId, scope.areaId)
        when {
            !mesaOk.first -> return SesionMesaResult.MesaNoPerteneceArea
            !mesaOk.second -> return SesionMesaResult.MesaInactiva
        }

        if (existeSesionActiva(scope.mesaId)) {
            return SesionMesaResult.SesionYaAbierta
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
        return SesionMesaResult.Opened(response)
    }

    /**
     * Transición de ciclo de cuenta entre `ABIERTA` y `CUENTA_SOLICITADA` (biye). El
     * [destino] debe ser uno de esos dos estados. Cualquier otro estado de origen se rechaza
     * con `SesionYaFinalizada`.
     */
    private fun transicionarSesionCuenta(
        sesionId: Int,
        destino: EstadoSesionMesa,
    ): SesionMesaResult {
        require(destino == EstadoSesionMesa.ABIERTA || destino == EstadoSesionMesa.CUENTA_SOLICITADA) {
            "transicionarSesionCuenta solo admite ABIERTA<->CUENTA_SOLICITADA, no $destino"
        }
        val sesion =
            SesionMesaTable
                .selectAll()
                .where { SesionMesaTable.id eq sesionId }
                .singleOrNull()
                ?: return SesionMesaResult.SesionNoEncontrada

        val estadoActual =
            EstadoSesionMesa.fromCodigo(sesion[SesionMesaTable.estado])
                ?: return SesionMesaResult.SesionYaFinalizada
        val valido =
            (estadoActual == EstadoSesionMesa.ABIERTA && destino == EstadoSesionMesa.CUENTA_SOLICITADA) ||
                (estadoActual == EstadoSesionMesa.CUENTA_SOLICITADA && destino == EstadoSesionMesa.ABIERTA)
        if (!valido) return SesionMesaResult.SesionYaFinalizada

        SesionMesaTable.update({ SesionMesaTable.id eq sesionId }) {
            it[SesionMesaTable.estado] = destino.codigo
        }
        return SesionMesaResult.Closed(sesion.toSesionMesaResponse().copy(estado = destino.codigo))
    }

    /**
     * Cierra la sesión por pago completo: transiciona `ABIERTA` o `CUENTA_SOLICITADA` hacia
     * `CERRADA_PAGADA` y registra `fechaCierre`. No se exige `tieneOperaciones == false`
     * porque el caller (`CuentaMesaRepository.marcarFacturada`) garantiza que el saldo de
     * cuentas activas es 0 y no queda ninguna cantidad no cancelada sin facturar. Por eso una
     * línea todavía no entregada sí mantiene abierta la mesa.
     */
    private fun cerrarPorPagoInterno(sesionId: Int): SesionMesaResult {
        val sesion =
            SesionMesaTable
                .selectAll()
                .where { SesionMesaTable.id eq sesionId }
                .singleOrNull()
                ?: return SesionMesaResult.SesionNoEncontrada

        val estadoActual =
            EstadoSesionMesa.fromCodigo(sesion[SesionMesaTable.estado])
                ?: return SesionMesaResult.SesionYaFinalizada
        if (estadoActual.esFinal) return SesionMesaResult.SesionYaFinalizada

        val ahora = LocalDateTime.now()
        SesionMesaTable.update({ SesionMesaTable.id eq sesionId }) {
            it[SesionMesaTable.estado] = EstadoSesionMesa.CERRADA_PAGADA.codigo
            it[SesionMesaTable.fechaCierre] = ahora
            it[SesionMesaTable.activo] = INACTIVE
        }
        return SesionMesaResult.Closed(
            sesion.toSesionMesaResponse().copy(
                estado = EstadoSesionMesa.CERRADA_PAGADA.codigo,
                fechaCierre = ahora.formatIso(),
                activo = false,
            ),
        )
    }

    private fun mutarSesionInterno(
        sesionId: Int,
        destino: EstadoSesionMesa,
        isCancel: Boolean,
    ): SesionMesaResult {
        val sesion =
            SesionMesaTable
                .selectAll()
                .where { SesionMesaTable.id eq sesionId }
                .singleOrNull()
                ?: return SesionMesaResult.SesionNoEncontrada

        val estadoCodigo = sesion[SesionMesaTable.estado]
        val estadoActual =
            EstadoSesionMesa.fromCodigo(estadoCodigo)
                ?: return SesionMesaResult.SesionYaFinalizada
        // Cerrar/Cancelar directo: solo se permite si la sesión está ABIERTA o CUENTA_SOLICITADA
        // (noexistentente pagada). El paso por caja real (pago completo) usa cerrarPorPago.
        if (estadoActual != EstadoSesionMesa.ABIERTA && estadoActual != EstadoSesionMesa.CUENTA_SOLICITADA) {
            return SesionMesaResult.SesionYaFinalizada
        }

        val mesaId = sesion[SesionMesaTable.mesaId]
        if (tieneOperaciones(sesionId, mesaId)) {
            return SesionMesaResult.SesionConOperaciones
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
        return if (isCancel) SesionMesaResult.Cancelled(response) else SesionMesaResult.Closed(response)
    }

    /**
     * Hook de fases anteriores que ahora consulta operaciones reales de [PedidoMesaRepository]:
     * si existe al menos una línea de `pedido_mesa` con estado distinto de `ENTREGADA` o
     * `CANCELADA`, la sesión no se puede cerrar ni cancelar.
     *
     * Es **sincrónico** a propósito: el llamador corre dentro de una transacción suspendida y
     * la consulta comparte la conexión para atomicidad.
     */
    private fun tieneOperaciones(
        sesionId: Int,
        mesaId: Int,
    ): Boolean = pedidos.tieneOperaciones(sesionId, mesaId)

    private fun areaActivaPerteneceASucursal(
        areaId: Int,
        sucursalId: Int,
    ): Boolean =
        PlantasTable
            .selectAll()
            .where {
                (PlantasTable.id eq areaId) and
                    (PlantasTable.sucursalId eq sucursalId) and
                    (PlantasTable.activo eq ACTIVE)
            }.limit(1)
            .singleOrNull() != null

    private fun mesaActivaPerteneceAArea(
        mesaId: Int,
        areaId: Int,
    ): Pair<Boolean, Boolean> {
        val row =
            MesasTable
                .selectAll()
                .where { (MesasTable.id eq mesaId) and (MesasTable.plantaId eq areaId) }
                .limit(1)
                .singleOrNull()
                ?: return false to false
        return true to (row[MesasTable.activo] == ACTIVE)
    }

    private fun existeSesionActiva(mesaId: Int): Boolean =
        SesionMesaTable
            .selectAll()
            .where { (SesionMesaTable.mesaId eq mesaId) and (SesionMesaTable.activo eq ACTIVE) }
            .limit(1)
            .singleOrNull() != null

    private fun sesionActivaDeMesa(mesaId: Int): SesionMesaResponse? {
        val row =
            SesionMesaTable
                .selectAll()
                .where { (SesionMesaTable.mesaId eq mesaId) and (SesionMesaTable.activo eq ACTIVE) }
                .orderBy(SesionMesaTable.fechaApertura to org.jetbrains.exposed.sql.SortOrder.DESC)
                .limit(1)
                .singleOrNull()
                ?: return null
        return row.toSesionMesaResponse()
    }

    private fun sesionesActivasByMesa(mesas: Set<Int>): Map<Int, SesionMesaResponse> {
        if (mesas.isEmpty()) return emptyMap()
        val usuarioById = usuariosById()
        return SesionMesaTable
            .selectAll()
            .where {
                (SesionMesaTable.mesaId inList mesas) and (SesionMesaTable.activo eq ACTIVE)
            }.map { it.toSesionMesaResponse(usuarioByCod = usuarioById) }
            .associateBy { it.mesaId }
    }

    private fun usuariosById(): Map<Int, String> =
        UsersTable
            .selectAll()
            .map { it[UsersTable.codUsuario] to it[UsersTable.usuario] }
            .toMap()

    private fun sucursalDeCaja(cajaId: String): Int? =
        CajaTable
            .select(CajaTable.idSucursal, CajaTable.codEstatus)
            .where { CajaTable.idCaja eq cajaId }
            .limit(1)
            .singleOrNull()
            ?.let { row ->
                val activo = row[CajaTable.codEstatus] == ACTIVE
                if (activo) row[CajaTable.idSucursal] else null
            }

    /**
     * Construye una respuesta sintética cuando acabamos de insertar/actualizar y tenemos los
     * datos frescos en memoria. [usuarioNombre] se resuelve fuera cuando interesa (listados
     * por lote) o puntualmente tras abrir/cerrar; nunca se hace N+1.
     */
    @Suppress("LongParameterList")
    private fun sessionRowToResponse(
        id: Int,
        sucursalId: Int,
        cajaId: String,
        areaId: Int,
        mesaId: Int,
        usuarioId: Int,
        usuarioNombre: String?,
        cantidadPersonas: Int,
        estado: String,
        fechaApertura: LocalDateTime,
        fechaCierre: LocalDateTime?,
        activo: Boolean,
    ): SesionMesaResponse =
        SesionMesaResponse(
            id = id,
            sucursalId = sucursalId,
            cajaId = cajaId,
            areaId = areaId,
            mesaId = mesaId,
            usuarioId = usuarioId,
            usuario = usuarioNombre,
            cantidadPersonas = cantidadPersonas,
            estado = estado,
            fechaApertura = fechaApertura.formatIso(),
            fechaCierre = fechaCierre?.formatIso(),
            activo = activo,
        )

    private fun usuarioNombre(usuarioId: Int): String? =
        UsersTable
            .selectAll()
            .where { UsersTable.codUsuario eq usuarioId }
            .singleOrNull()
            ?.get(UsersTable.usuario)

    private fun ResultRow.toSesionMesaResponse(usuarioByCod: Map<Int, String> = emptyMap()): SesionMesaResponse =
        SesionMesaResponse(
            id = this[SesionMesaTable.id],
            sucursalId = this[SesionMesaTable.sucursalId],
            cajaId = this[SesionMesaTable.cajaId],
            areaId = this[SesionMesaTable.areaId],
            mesaId = this[SesionMesaTable.mesaId],
            usuarioId = this[SesionMesaTable.usuarioId],
            // Primero el mapa caché (más eficiente en listados); si no está, no resolvemos
            // en este path para evitar N+1.
            usuario = usuarioByCod[this[SesionMesaTable.usuarioId]],
            cantidadPersonas = this[SesionMesaTable.cantidadPersonas],
            estado = this[SesionMesaTable.estado],
            fechaApertura = this[SesionMesaTable.fechaApertura].formatIso(),
            fechaCierre = this[SesionMesaTable.fechaCierre]?.formatIso(),
            activo = this[SesionMesaTable.activo] == ACTIVE,
        )

    private fun LocalDateTime.formatIso(): String = ISO_FORMATTER.format(this)

    private fun esViolacionUniqueSesion(e: ExposedSQLException): Boolean {
        val msg = e.message?.lowercase().orEmpty()
        return DUPLICATE_KEYS.any { msg.contains(it) }
    }

    private companion object {
        const val ACTIVE = 1
        const val INACTIVE = 0
        val ISO_FORMATTER: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME
        val DUPLICATE_KEYS =
            listOf("uq_sesion_mesa_activa", "duplicate key", "unique constraint", "duplicate entry")
    }
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
 * tests de la fase anterior y es inyectada para los tests de sesión que noSeedean pedidos.
 */
object NoOpPedidoMesaOperacionesLookup : PedidoMesaOperacionesLookup {
    override fun tieneOperaciones(
        sesionId: Int,
        mesaId: Int,
    ): Boolean = false
}
