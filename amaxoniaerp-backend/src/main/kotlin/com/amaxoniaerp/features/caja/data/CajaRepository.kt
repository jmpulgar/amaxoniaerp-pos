package com.amaxoniaerp.features.caja.data

import com.amaxoniaerp.core.database.DatabaseManager
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
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
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
        countryCode: String,
        dbName: String,
        idCaja: String,
    ): CajaSecuencia? {
        val database = DatabaseManager.connectToCompanyDb(countryCode, dbName)
        return dbQuery(database) {
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
    }

    suspend fun openCaja(
        countryCode: String,
        dbName: String,
        request: AperturaRequest,
        username: String,
    ): Result<CajaSecuencia> {
        val database = DatabaseManager.connectToCompanyDb(countryCode, dbName)
        val currentOpen = getCajaStatus(countryCode, dbName, request.idCaja)
        if (currentOpen != null) {
            autoCloseOpenSequence(countryCode, dbName, currentOpen.idCajaSecuencia).fold(
                onSuccess = { },
                onFailure = { error ->
                    return Result.failure(
                        IllegalStateException(
                            "No se pudo cerrar automaticamente la secuencia abierta: ${error.message}",
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
            insertApertura(newId, request, username, now, nextSequence)
            Result.success(Unit)
        }.mapCatching {
            getCajaStatus(countryCode, dbName, request.idCaja)
                ?: error("Failed to retrieve open caja.")
        }
    }

    private fun insertApertura(
        newId: String,
        request: AperturaRequest,
        username: String,
        now: java.time.LocalDateTime,
        nextSequence: String,
    ) {
        CajaSecuenciaTable.insert {
            it[idCajaSecuencia] = newId
            it[idCaja] = request.idCaja
            it[idVendedor] = request.idVendedor
            it[fechaApertura] = now
            it[montoEfectivoApertura] = request.montoApertura.toBigDecimal()
            it[usuario] = username
            it[serieSucursal] = request.serieSucursal
            it[secuencia] = nextSequence
            it[contabilizado] = 0
            // EL CAMPO FALTANTE PARA CUMPLIR CON EL ESQUEMA:
            it[serialFiscal] = ""
            it[observacionApertura] = "Apertura automática desde App POS"
            it[observacionCierre] = ""
            it[usuarioContabilizacion] = ""
            it[fechaContabilizacion] = now
        }

        CajaDetalleAperturaTable.insert {
            it[idDetalleApertura] = UUID.randomUUID().toString()
            it[idCajaSecuencia] = newId
            it[cantidad] = 1
            it[valor] = request.montoApertura.toBigDecimal()
            it[monto] = request.montoApertura.toBigDecimal()
            it[serieSucursal] = request.serieSucursal
        }
    }

    suspend fun getNextSecuenciaCodigo(
        countryCode: String,
        dbName: String,
        idCaja: String,
    ): Result<String> {
        val database = DatabaseManager.connectToCompanyDb(countryCode, dbName)
        return runCatching {
            dbQuery(database) {
                resolveNextSecuenciaCode(idCaja)
            }
        }
    }

    private suspend fun autoCloseOpenSequence(
        countryCode: String,
        dbName: String,
        idSecuencia: String,
    ): Result<Unit> =
        getCajaSecuenciaData(countryCode, dbName, idSecuencia, verifyFacturasTemporales = false).fold(
            onSuccess = { data ->
                val request = buildAutoCloseRequest(data)
                saveCajaCierreInternal(
                    countryCode = countryCode,
                    dbName = dbName,
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
        countryCode: String,
        dbName: String,
        idSecuencia: String,
        verifyFacturasTemporales: Boolean = false,
    ): Result<CajaSecuenciaData> {
        val database = DatabaseManager.connectToCompanyDb(countryCode, dbName)
        return runCatching {
            dbQuery(database) { readCajaSecuenciaData(countryCode, idSecuencia, verifyFacturasTemporales) }
        }
    }

    private fun readCajaSecuenciaData(
        countryCode: String,
        idSecuencia: String,
        verifyFacturasTemporales: Boolean,
    ): CajaSecuenciaData {
        val header = loadCajaSecuenciaHeader(idSecuencia)
        val secuenciaRow = header.row

        val detalleApertura = loadDetalleApertura(idSecuencia)
        val montosPorForma = loadMontosPorForma(countryCode, idSecuencia)
        val formaPagoItems = loadFormaPagoItems(countryCode, header.idCaja, montosPorForma)
        val montoEntrada = loadMovimientoTotal(idSecuencia, "E")
        val montoSalida = loadMovimientoTotal(idSecuencia, "S")
        appendEntradasSalidasItems(formaPagoItems, montoEntrada, montoSalida)

        val formaPagoDevolucion = loadFormaPagoDevolucion(idSecuencia)
        applyDevolucionesToFormaPago(formaPagoItems, formaPagoDevolucion)

        val montoEfectivoVentasCalc =
            formaPagoItems
                .filter { it.id > 0 && isCashSigla(it.siglas) }
                .sumOf { it.monto }
        val montoOtrosTotalCalc =
            formaPagoItems
                .filter { it.id > 0 && !isCashSigla(it.siglas) }
                .sumOf { it.monto }

        val totalAnulado = loadTotalAnulado(countryCode, idSecuencia)
        val (totalVentas, cantidadTransacciones) = loadVentasDeSecuencia(countryCode, idSecuencia)
        val inventario = loadInventarioVentas(countryCode, idSecuencia)
        val verificarTemporales =
            if (verifyFacturasTemporales) countFacturasTemporales(countryCode, idSecuencia) else 0

        val montoEfectivoApertura = secuenciaRow[CajaSecuenciaTable.montoEfectivoApertura].toDouble()
        val montoEfectivoTotalCalc = montoEfectivoApertura + montoEfectivoVentasCalc + montoEntrada - montoSalida
        val montoTotalCalc = montoEfectivoTotalCalc + montoOtrosTotalCalc
        val montoCierreCalc = montoEfectivoApertura + totalVentas + montoEntrada - montoSalida - totalAnulado

        val fechaApertura = secuenciaRow[CajaSecuenciaTable.fechaApertura]
        val fechaCierre = secuenciaRow[CajaSecuenciaTable.fechaCierre]
        val fechaCreacion = secuenciaRow[CajaSecuenciaTable.fechaCreacion]
        val cajaRow = header.cajaRow

        return CajaSecuenciaData(
            id = secuenciaRow[CajaSecuenciaTable.idCajaSecuencia],
            idCaja = header.idCaja,
            idVendedor = secuenciaRow[CajaSecuenciaTable.idVendedor],
            secuencia = secuenciaRow[CajaSecuenciaTable.secuencia],
            fechaApertura = formatCajaDateTime(fechaApertura),
            fechaCierre = formatCajaDateTime(fechaCierre),
            fechaCreacion = formatCajaDateTime(fechaCreacion),
            usuario = secuenciaRow[CajaSecuenciaTable.usuario],
            observacionApertura = secuenciaRow[CajaSecuenciaTable.observacionApertura],
            observacionCierre = secuenciaRow[CajaSecuenciaTable.observacionCierre],
            montoEfectivoApertura = montoEfectivoApertura,
            montoEfectivoVentas = montoEfectivoVentasCalc,
            montoEfectivoEntrada = montoEntrada,
            montoEfectivoSalida = montoSalida,
            montoEfectivoTotal = montoEfectivoTotalCalc,
            montoEfectivoCierre = montoEfectivoTotalCalc,
            montoEfectivoDiferencia = 0.0,
            montoOtrosTotal = montoOtrosTotalCalc,
            montoOtrosCierre = montoOtrosTotalCalc,
            montoOtrosDiferencia = 0.0,
            montoTotal = montoTotalCalc,
            montoCierre = montoCierreCalc,
            montoDiferencia = 0.0,
            totalVentas = totalVentas,
            cantidadTransacciones = cantidadTransacciones,
            numeroCierreFiscal = secuenciaRow[CajaSecuenciaTable.numeroCierreFiscal],
            serieSucursal = secuenciaRow[CajaSecuenciaTable.serieSucursal],
            serialFiscal = secuenciaRow[CajaSecuenciaTable.serialFiscal],
            contabilizado = secuenciaRow[CajaSecuenciaTable.contabilizado],
            ffechaApertura = fechaApertura?.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")) ?: "",
            ffechaCierre = fechaCierre?.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")) ?: "",
            cajaCodigo = cajaRow?.get(CajaTable.codCaja),
            caja = cajaRow?.get(CajaTable.caja) ?: cajaRow?.get(CajaTable.descripcion),
            fondoApertura = cajaRow?.get(CajaTable.fondoApertura)?.toDouble() ?: 0.0,
            nombreModelo = cajaRow?.get(CajaTable.impresoraModelo),
            vendedor = header.vendedorNombre,
            detalleApertura = detalleApertura,
            formaPago = formaPagoItems,
            formaPagoDevolucion = formaPagoDevolucion,
            totalAnulado = totalAnulado,
            verificarFacturasTemporales = verificarTemporales,
            inventario = inventario,
        )
    }

    suspend fun saveCajaCierre(
        countryCode: String,
        dbName: String,
        request: CajaCierreSaveRequest,
    ): Result<CajaCierreSaveResponse> =
        saveCajaCierreInternal(
            countryCode = countryCode,
            dbName = dbName,
            request = request,
            validateFacturasTemporales = true,
        )

    private suspend fun saveCajaCierreInternal(
        countryCode: String,
        dbName: String,
        request: CajaCierreSaveRequest,
        validateFacturasTemporales: Boolean,
    ): Result<CajaCierreSaveResponse> {
        val database = DatabaseManager.connectToCompanyDb(countryCode, dbName)
        return runCatching {
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

                persistCierre(request, now)
                rewriteCierreDetalles(request, serieSucursal)

                CajaCierreSaveResponse(
                    success = true,
                    message = "Cierre de caja guardado correctamente",
                    id = request.id,
                )
            }
        }
    }

    private fun persistCierre(
        request: CajaCierreSaveRequest,
        now: java.time.LocalDateTime,
    ) {
        CajaSecuenciaTable.update({ CajaSecuenciaTable.idCajaSecuencia eq request.id }) {
            it[fechaCierre] = now
            it[montoEfectivoVentas] = request.montoEfectivoVentas.toMoney()
            it[montoEfectivoEntrada] = request.montoEfectivoEntrada.toMoney()
            it[montoEfectivoSalida] = request.montoEfectivoSalida.toMoney()
            it[montoEfectivoTotal] = request.montoEfectivoTotal.toMoney()
            it[montoEfectivoCierre] = request.montoEfectivoCierre.toMoney()
            it[montoEfectivoDiferencia] = request.montoEfectivoDiferencia.toMoney()
            it[montoOtrosTotal] = request.montoOtrosTotal.toMoney()
            it[montoOtrosCierre] = request.montoOtrosCierre.toMoney()
            it[montoOtrosDiferencia] = request.montoOtrosDiferencia.toMoney()
            it[montoTotal] = request.montoTotal.toMoney()
            it[montoCierre] = request.montoCierre.toMoney()
            it[montoDiferencia] = request.montoDiferencia.toMoney()
            it[observacionCierre] = request.observacionCierre.orEmpty()
            it[numeroCierreFiscal] = request.numeroCierreFiscal
        }
    }

    private fun rewriteCierreDetalles(
        request: CajaCierreSaveRequest,
        serieSucursal: String,
    ) {
        CajaDetalleCierreTable.deleteWhere { CajaDetalleCierreTable.idSecuencia eq request.id }
        CajaDetalleCierreFormaPagoTable.deleteWhere {
            CajaDetalleCierreFormaPagoTable.idSecuencia eq request.id
        }

        request.detalle
            .filter { it.cantidad > 0 }
            .forEach { detalle ->
                insertCajaDetalleCierre(request.id, serieSucursal, detalle)
            }

        request.detalleFormaPago.forEach { detalle ->
            insertCajaDetalleCierreFormaPago(request.id, serieSucursal, detalle)
        }
    }

    suspend fun getCajaSequenceSummary(
        countryCode: String,
        dbName: String,
        idCaja: String,
    ): CajaCierreSummary? {
        val secuencia = getCajaStatus(countryCode, dbName, idCaja) ?: return null
        val database = DatabaseManager.connectToCompanyDb(countryCode, dbName)

        return dbQuery(database) {
            val names = loadCajaHeaderNames(idCaja, secuencia.idCajaSecuencia)
            val sales = loadSalesTotals(countryCode, secuencia.idCajaSecuencia)
            val movimientos = loadMovimientoTotals(countryCode, secuencia.idCajaSecuencia)
            val formas = loadFormaPagoBreakdown(countryCode, secuencia.idCajaSecuencia)
            buildCajaCierreSummary(secuencia, names, sales, movimientos, formas)
        }
    }

    suspend fun getCajas(
        countryCode: String,
        dbName: String,
        userId: Int,
    ): List<Caja> {
        val database = DatabaseManager.connectToCompanyDb(countryCode, dbName)
        return dbQuery(database) {
            val params = loadCajaCatalogParams(countryCode)
            val defaultBySucursal = loadDefaultWarehouseBySucursal()
            val activeSellers = loadActiveSellers()
            mapCajaRows(countryCode, userId, params, defaultBySucursal, activeSellers)
        }
    }
}
