package com.amaxoniaerp.features.caja.application

import com.amaxoniaerp.core.database.dbQuery
import com.amaxoniaerp.core.time.BusinessClock
import com.amaxoniaerp.features.caja.domain.AperturaRequest
import com.amaxoniaerp.features.caja.domain.CajaCierreSaveRequest
import com.amaxoniaerp.features.caja.domain.CajaCierreSaveResponse
import com.amaxoniaerp.features.caja.domain.CajaSecuencia
import com.amaxoniaerp.features.caja.domain.buildAutoCloseRequest
import org.jetbrains.exposed.sql.Database
import org.slf4j.LoggerFactory
import java.time.ZoneId
import java.util.UUID

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

    /**
     * Abre una nueva secuencia de caja. Si la caja tiene una sesión abierta
     * se auto-cierra antes de abrir la siguiente, y toda la operación —
     * auto-close, inserción y relectura de estado— corre en UNA fase de
     * transacción: si la apertura falla después del auto-close, ambas
     * escrituras revierten y la sesión previa permanece abierta.
     *
     * Nota: esta atomicidad aplica a fallos dentro de la fase; la
     * serialización de aperturas concurrentes requiere un índice único a
     * nivel de esquema (decisión funcional separada).
     */
    suspend fun open(
        database: Database,
        countryCode: String,
        dbName: String,
        request: AperturaRequest,
        username: String,
    ): Result<CajaSecuencia> {
        log.info(
            "openCaja reloj negocio: countryCode={} zone={} fechaAperturaLocal={} jvmDefaultZone={}",
            countryCode,
            BusinessClock.zoneForCountry(countryCode),
            BusinessClock.nowForCountry(countryCode),
            ZoneId.systemDefault(),
        )

        return runCatching {
            dbQuery(database) {
                currentOpenSecuencias(dbName, request.idCaja).firstOrNull()?.let { abierta ->
                    runCatching { autoCloseInPhase(countryCode, abierta.idCajaSecuencia) }
                        .getOrElse { error ->
                            log.warn(
                                "No se pudo cerrar automaticamente la secuencia abierta. idSecuencia={}",
                                abierta.idCajaSecuencia,
                                error,
                            )
                            throw IllegalStateException(
                                "No se pudo cerrar automaticamente la secuencia abierta",
                                error,
                            )
                        }
                }

                val newId = UUID.randomUUID().toString()
                val nextSequence = store.nextSecuenciaCode(request.idCaja)
                store.insertApertura(
                    newId = newId,
                    request = request,
                    username = username,
                    now = BusinessClock.nowForCountry(countryCode),
                    nextSequence = nextSequence,
                )

                currentOpenSecuencias(dbName, request.idCaja).firstOrNull()
                    ?: error("Failed to retrieve open caja.")
            }
        }
    }

    /**
     * Auto-close dentro de la fase de apertura: cierra con los montos
     * calculados y diferencias en cero, sin bloquear por facturas
     * temporales (comportamiento vigente congelado). Relee la guarda de la
     * secuencia para validar estado y tomar la serie sucursal, igual que el
     * cierre vigente.
     */
    private fun autoCloseInPhase(
        countryCode: String,
        idSecuencia: String,
    ) {
        val data =
            store.readSecuenciaData(
                countryCode = countryCode,
                idSecuencia = idSecuencia,
                verifyFacturasTemporales = false,
            )
        val cierreRequest = buildAutoCloseRequest(data)
        val guard =
            store.findSecuenciaGuard(idSecuencia)
                ?: error("Secuencia de caja no encontrada")
        if (guard.cerrada) {
            error("La secuencia de caja ya se encuentra cerrada")
        }
        store.writeCierre(cierreRequest, BusinessClock.nowForCountry(countryCode), guard.serieSucursal)
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
