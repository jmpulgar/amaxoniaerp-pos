package com.amaxoniaerp.features.mesas.data

import com.amaxoniaerp.core.database.dbQuery
import com.amaxoniaerp.features.mesas.domain.EstadoMesaOperativo
import com.amaxoniaerp.features.mesas.domain.EstadoSesionMesa
import com.amaxoniaerp.features.mesas.domain.MesaEstadoResponse
import com.amaxoniaerp.features.mesas.domain.SesionMesaResult
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.exceptions.ExposedSQLException
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction

/**
 * Repositorio de sesiones operativas de mesa.
 *
 * Los wrappers suspend abren la transacción y delegan el cuerpo transaccional en
 * SesionMesaMutaciones.kt; los lookups y mappers viven en SesionMesaLookups.kt /
 * SesionMesaMappers.kt.
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
                    .where { (MesasTable.plantaId eq areaId) and (MesasTable.activo eq true) }
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
            newSuspendedTransaction<SesionMesaResult>(Dispatchers.IO, database) {
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
            newSuspendedTransaction<SesionMesaResult>(Dispatchers.IO, database) {
                mutarSesionInterno(sesionId, EstadoSesionMesa.CERRADA, isCancel = false, pedidos::tieneOperaciones)
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
            newSuspendedTransaction<SesionMesaResult>(Dispatchers.IO, database) {
                mutarSesionInterno(sesionId, EstadoSesionMesa.CANCELADA, isCancel = true, pedidos::tieneOperaciones)
            }
        } catch (e: ExposedSQLException) {
            if (esViolacionUniqueSesion(e)) SesionMesaResult.SesionYaAbierta else throw e
        }

    /**
     * Marca la sesión como `CUENTA_SOLICITADA`: el operario pidió la cuenta pero todavía no
     * ha pagado. Sigue admitiendo pedidos (un cliente puede agregar items tras pedir la
     * cuenta antes de pagar). Solo válido desde `ABIERTA`.
     */
    suspend fun solicitarCuenta(
        database: Database,
        sesionId: Int,
    ): SesionMesaResult =
        newSuspendedTransaction<SesionMesaResult>(Dispatchers.IO, database) {
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
        newSuspendedTransaction<SesionMesaResult>(Dispatchers.IO, database) {
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
        newSuspendedTransaction<SesionMesaResult>(Dispatchers.IO, database) {
            cerrarPorPagoInterno(sesionId)
        }
}
