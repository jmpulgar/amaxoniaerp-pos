package com.amaxoniaerp.features.caja.data

import com.amaxoniaerp.core.database.dbQuery
import com.amaxoniaerp.core.time.BusinessClock
import com.amaxoniaerp.features.caja.domain.AperturaRequest
import com.amaxoniaerp.features.caja.domain.Caja
import com.amaxoniaerp.features.caja.domain.CajaCierreFormaPagoRequest
import com.amaxoniaerp.features.caja.domain.CajaCierreSaveRequest
import com.amaxoniaerp.features.caja.domain.CajaCierreSaveResponse
import com.amaxoniaerp.features.caja.domain.CajaCierreSummary
import com.amaxoniaerp.features.caja.domain.CajaSecuencia
import com.amaxoniaerp.features.caja.domain.CajaSecuenciaData
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.selectAll
import org.slf4j.LoggerFactory
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

/**
 * Repositorio de caja. La lectura de la secuencia vive en
 * CajaSecuenciaDataReader.kt, el resumen de cierre en
 * CajaCierreSummaryReader.kt, el catálogo de cajas en CajaCatalogReader.kt y
 * los helpers de cierre en CajaCierreSupport.kt.
 */
class CajaRepository {
    private val log = LoggerFactory.getLogger(CajaRepository::class.java)

    suspend fun getCajaStatus(
        database: Database,
        dbName: String,
        idCaja: String,
    ): CajaSecuencia? =
        dbQuery(database) {
            val openCajas =
                CajaSecuenciaTable
                    .selectAll()
                    .where { (CajaSecuenciaTable.idCaja eq idCaja) and (CajaSecuenciaTable.fechaCierre.isNull()) }
                    .orderBy(CajaSecuenciaTable.fechaApertura to SortOrder.DESC)
                    .limit(2)
                    .toList()

            if (openCajas.size > 1) {
                log.warn(
                    "Detected multiple open caja_secuencia records. companyDb={} idCaja={} records={}",
                    dbName,
                    idCaja,
                    openCajas.map { it[CajaSecuenciaTable.idCajaSecuencia] },
                )
            }

            openCajas
                .firstOrNull()
                ?.let { row ->
                    CajaSecuencia(
                        idCajaSecuencia = row[CajaSecuenciaTable.idCajaSecuencia],
                        idCaja = row[CajaSecuenciaTable.idCaja],
                        fechaApertura =
                            row[CajaSecuenciaTable.fechaApertura]?.format(
                                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
                            ) ?: "",
                        montoApertura = row[CajaSecuenciaTable.montoEfectivoApertura].toDouble(),
                        fechaCierre =
                            row[CajaSecuenciaTable.fechaCierre]?.format(
                                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
                            ),
                        montoCierre = row[CajaSecuenciaTable.montoEfectivoCierre]?.toDouble(),
                        estatus = if (row[CajaSecuenciaTable.fechaCierre] == null) 1 else 0,
                        usuarioApertura = row[CajaSecuenciaTable.usuario] ?: "",
                        usuarioCierre = null,
                        serieSucursal = row[CajaSecuenciaTable.serieSucursal],
                        idSucursal = 1, // default since it was removed
                    )
                }
        }

    suspend fun openCaja(
        database: Database,
        countryCode: String,
        dbName: String,
        request: AperturaRequest,
        username: String,
    ): Result<CajaSecuencia> {
        val currentOpen = getCajaStatus(database, dbName, request.idCaja)
        if (currentOpen != null) {
            autoCloseOpenSequence(database, countryCode, currentOpen.idCajaSecuencia).fold(
                onSuccess = { },
                onFailure = { error ->
                    log.warn(
                        "No se pudo cerrar automaticamente la secuencia abierta. idSecuencia={}",
                        currentOpen.idCajaSecuencia,
                        error,
                    )
                    return Result.failure(
                        IllegalStateException(
                            "No se pudo cerrar automaticamente la secuencia abierta",
                            error,
                        ),
                    )
                },
            )
        }

        val now = BusinessClock.nowForCountry(countryCode)
        log.info(
            "openCaja reloj negocio: countryCode={} zone={} fechaAperturaLocal={} jvmDefaultZone={}",
            countryCode,
            BusinessClock.zoneForCountry(countryCode),
            now,
            ZoneId.systemDefault(),
        )
        val newId = UUID.randomUUID().toString()
        val nextSequence =
            dbQuery(database) {
                resolveNextSecuenciaCode(request.idCaja)
            }

        return dbQuery(database) {
            insertAperturaRecord(newId, request, username, now, nextSequence)
            Result.success(Unit)
        }.mapCatching {
            getCajaStatus(database, dbName, request.idCaja)
                ?: error("Failed to retrieve open caja.")
        }
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

    private suspend fun autoCloseOpenSequence(
        database: Database,
        countryCode: String,
        idSecuencia: String,
    ): Result<Unit> =
        getCajaSecuenciaData(database, countryCode, idSecuencia, verifyFacturasTemporales = false).fold(
            onSuccess = { data ->
                val request = buildAutoCloseRequest(data)
                saveCajaCierreInternal(
                    database = database,
                    countryCode = countryCode,
                    request = request,
                    validateFacturasTemporales = false,
                ).map { Unit }
            },
            onFailure = { error ->
                Result.failure(error)
            },
        )

    private fun buildAutoCloseRequest(data: CajaSecuenciaData): CajaCierreSaveRequest {
        val formaPagoTotals = buildAutoCloseFormaPagoTotals(data)
        val montoEfectivoVentas =
            formaPagoTotals
                .filter { (_, item) -> isCashSigla(item.sigla) }
                .values
                .sumOf { it.monto }
        val montoOtrosTotal =
            formaPagoTotals
                .filterNot { (_, item) -> isCashSigla(item.sigla) }
                .values
                .sumOf { it.monto }
        val montoEfectivoTotal =
            data.montoEfectivoApertura +
                montoEfectivoVentas +
                data.montoEfectivoEntrada -
                data.montoEfectivoSalida
        val montoTotal = montoEfectivoTotal + montoOtrosTotal

        return CajaCierreSaveRequest(
            id = data.id,
            montoEfectivoVentas = montoEfectivoVentas,
            montoEfectivoEntrada = data.montoEfectivoEntrada,
            montoEfectivoSalida = data.montoEfectivoSalida,
            montoEfectivoTotal = montoEfectivoTotal,
            montoEfectivoCierre = montoEfectivoTotal,
            montoEfectivoDiferencia = 0.0,
            montoOtrosTotal = montoOtrosTotal,
            montoOtrosCierre = montoOtrosTotal,
            montoOtrosDiferencia = 0.0,
            montoTotal = montoTotal,
            montoCierre = montoTotal,
            montoDiferencia = 0.0,
            detalle = emptyList(),
            detalleFormaPago =
                formaPagoTotals
                    .map { (idFormaPago, item) ->
                        CajaCierreFormaPagoRequest(
                            idFormaPago = idFormaPago,
                            monto = item.monto,
                            montoCierre = item.monto,
                            montoDiferencia = 0.0,
                        )
                    },
            observacionCierre = "Cierre automático por nueva apertura",
            numeroCierreFiscal = "",
        )
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

    suspend fun saveCajaCierre(
        database: Database,
        countryCode: String,
        request: CajaCierreSaveRequest,
    ): Result<CajaCierreSaveResponse> =
        saveCajaCierreInternal(
            database = database,
            countryCode = countryCode,
            request = request,
            validateFacturasTemporales = true,
        )

    private suspend fun saveCajaCierreInternal(
        database: Database,
        countryCode: String,
        request: CajaCierreSaveRequest,
        validateFacturasTemporales: Boolean,
    ): Result<CajaCierreSaveResponse> =
        runCatching {
            dbQuery(database) {
                val secuenciaRow =
                    CajaSecuenciaTable
                        .selectAll()
                        .where { CajaSecuenciaTable.idCajaSecuencia eq request.id }
                        .limit(1)
                        .firstOrNull()
                        ?: error("Secuencia de caja no encontrada")

                if (secuenciaRow[CajaSecuenciaTable.fechaCierre] != null) {
                    error("La secuencia de caja ya se encuentra cerrada")
                }

                if (validateFacturasTemporales &&
                    countFacturasTemporales(countryCode, request.id) > 0
                ) {
                    error("Existen facturas temporales pendientes por procesar")
                }

                val now = BusinessClock.nowForCountry(countryCode)
                val serieSucursal = secuenciaRow[CajaSecuenciaTable.serieSucursal]

                persistCierreRecord(request, now)
                rewriteCierreDetallesRecord(request, serieSucursal)

                CajaCierreSaveResponse(
                    success = true,
                    message = "Cierre de caja guardado correctamente",
                    id = request.id,
                )
            }
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
            mapCajaRows(countryCode, userId, params, defaultBySucursal, activeSellers)
        }
}
