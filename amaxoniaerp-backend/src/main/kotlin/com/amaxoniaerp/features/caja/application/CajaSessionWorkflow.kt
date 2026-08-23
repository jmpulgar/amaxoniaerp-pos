package com.amaxoniaerp.features.caja.application

import com.amaxoniaerp.core.database.dbQuery
import com.amaxoniaerp.core.time.BusinessClock
import com.amaxoniaerp.features.caja.domain.CajaCierreSaveRequest
import com.amaxoniaerp.features.caja.domain.CajaCierreSaveResponse
import com.amaxoniaerp.features.caja.domain.CajaSecuencia
import org.jetbrains.exposed.sql.Database
import org.slf4j.LoggerFactory

/**
 * Workflow de sesión de caja (apertura → cierre → estado): módulo profundo
 * que concentra las guardas de negocio de la sesión —existencia, "ya
 * cerrada", facturas temporales pendientes—, el auto-close al reabrir y el
 * pliegue monetario del cierre, componiendo las primitivas de
 * [CajaSessionStore] bajo sus propias fases de transacción cortas.
 *
 * Los mensajes de error y los contratos HTTP que consumen estos resultados
 * están congelados: cambiarlos es decisión funcional separada.
 */
class CajaSessionWorkflow(
    private val store: CajaSessionStore,
) {
    private val log = LoggerFactory.getLogger(CajaSessionWorkflow::class.java)

    /**
     * Cierra la secuencia indicada validando estado y facturas temporales
     * dentro de una única fase de transacción.
     */
    suspend fun close(
        database: Database,
        countryCode: String,
        request: CajaCierreSaveRequest,
    ): Result<CajaCierreSaveResponse> =
        runCatching {
            dbQuery(database) {
                val guard =
                    store.findSecuenciaGuard(request.id)
                        ?: error("Secuencia de caja no encontrada")

                if (guard.cerrada) {
                    error("La secuencia de caja ya se encuentra cerrada")
                }

                if (store.countFacturasTemporalesPendientes(countryCode, request.id) > 0) {
                    error("Existen facturas temporales pendientes por procesar")
                }

                val now = BusinessClock.nowForCountry(countryCode)
                store.writeCierre(request, now, guard.serieSucursal)

                CajaCierreSaveResponse(
                    success = true,
                    message = "Cierre de caja guardado correctamente",
                    id = request.id,
                )
            }
        }

    /** Secuencia abierta más reciente de la caja; null si no hay ninguna. */
    suspend fun status(
        database: Database,
        dbName: String,
        idCaja: String,
    ): CajaSecuencia? =
        dbQuery(database) {
            currentOpenSecuencias(dbName, idCaja).firstOrNull()
        }

    private fun currentOpenSecuencias(
        dbName: String,
        idCaja: String,
    ): List<CajaSecuencia> {
        val abiertas = store.findOpenSecuencias(idCaja)
        if (abiertas.size > 1) {
            log.warn(
                "Detected multiple open caja_secuencia records. companyDb={} idCaja={} records={}",
                dbName,
                idCaja,
                abiertas.map { it.idCajaSecuencia },
            )
        }
        return abiertas
    }
}
