package com.amaxoniaerp.features.caja.data

import com.amaxoniaerp.core.database.dbQuery
import com.amaxoniaerp.features.caja.domain.Caja
import com.amaxoniaerp.features.caja.domain.CajaCierreSummary
import com.amaxoniaerp.features.caja.domain.CajaSecuencia
import com.amaxoniaerp.features.caja.domain.CajaSecuenciaData
import org.jetbrains.exposed.sql.Database
import org.slf4j.LoggerFactory

/**
 * Repositorio de caja: queries tipo Archetype A (catálogo, resumen de
 * cierre, datos y código de secuencia). La sesión —apertura atómica,
 * cierre y estado— vive en CajaSessionWorkflow (application) sobre el
 * puerto CajaSessionStore; las primitivas con alcance de transacción están
 * en CajaSessionPrimitives.kt, la lectura de la secuencia en
 * CajaSecuenciaDataReader.kt, el resumen de cierre en
 * CajaCierreSummaryReader.kt, el catálogo de cajas en CajaCatalogReader.kt
 * y los helpers de cierre en CajaCierreSupport.kt.
 */
class CajaRepository {
    private val log = LoggerFactory.getLogger(CajaRepository::class.java)

    suspend fun getCajaStatus(
        database: Database,
        dbName: String,
        idCaja: String,
    ): CajaSecuencia? =
        dbQuery(database) {
            val openCajas = findOpenSecuenciaRows(idCaja)

            if (openCajas.size > 1) {
                log.warn(
                    "Detected multiple open caja_secuencia records. companyDb={} idCaja={} records={}",
                    dbName,
                    idCaja,
                    openCajas.map { it[CajaSecuenciaTable.idCajaSecuencia] },
                )
            }

            openCajas.firstOrNull()?.let(::mapOpenSecuenciaRow)
        }

    suspend fun getNextSecuenciaCodigo(
        database: Database,
        idCaja: String,
    ): Result<String> =
        runCatching {
            dbQuery(database) {
                resolveNextSecuenciaCode(idCaja)
            }
        }

    suspend fun getCajaSecuenciaData(
        database: Database,
        countryCode: String,
        idSecuencia: String,
        verifyFacturasTemporales: Boolean = false,
    ): Result<CajaSecuenciaData> =
        runCatching {
            dbQuery(database) { readCajaSecuenciaData(countryCode, idSecuencia, verifyFacturasTemporales) }
        }

    suspend fun getCajaSequenceSummary(
        database: Database,
        countryCode: String,
        dbName: String,
        idCaja: String,
    ): CajaCierreSummary? {
        val secuencia = getCajaStatus(database, dbName, idCaja) ?: return null

        return dbQuery(database) {
            val names = loadCajaHeaderNames(idCaja, secuencia.idCajaSecuencia)
            val sales = loadSalesTotals(countryCode, secuencia.idCajaSecuencia)
            val movimientos = loadMovimientoTotals(countryCode, secuencia.idCajaSecuencia)
            val formas = loadFormaPagoBreakdown(countryCode, secuencia.idCajaSecuencia)
            buildCajaCierreSummary(secuencia, names, sales, movimientos, formas)
        }
    }

    suspend fun getCajas(
        database: Database,
        countryCode: String,
        userId: Int,
    ): List<Caja> =
        dbQuery(database) {
            val params = loadCajaCatalogParams(countryCode)
            val defaultBySucursal = loadDefaultWarehouseBySucursal()
            val activeSellers = loadActiveSellers()
            val warehouseNames = loadWarehouseNames()
            mapCajaRows(countryCode, userId, params, defaultBySucursal, activeSellers, warehouseNames)
        }
}
