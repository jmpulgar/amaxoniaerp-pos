package com.amaxoniaerp.features.caja.data

import com.amaxoniaerp.core.database.DatabaseManager
import com.amaxoniaerp.core.database.dbQuery
import com.amaxoniaerp.core.time.BusinessClock
import com.amaxoniaerp.features.caja.domain.AperturaRequest
import com.amaxoniaerp.features.caja.domain.Caja
import com.amaxoniaerp.features.caja.domain.CajaCierreDetalleRequest
import com.amaxoniaerp.features.caja.domain.CajaCierreFormaPagoRequest
import com.amaxoniaerp.features.caja.domain.CajaCierreSaveRequest
import com.amaxoniaerp.features.caja.domain.CajaCierreSaveResponse
import com.amaxoniaerp.features.caja.domain.CajaCierreSummary
import com.amaxoniaerp.features.caja.domain.CajaDetalleAperturaItem
import com.amaxoniaerp.features.caja.domain.CajaFormaPagoDevolucionItem
import com.amaxoniaerp.features.caja.domain.CajaFormaPagoItem
import com.amaxoniaerp.features.caja.domain.CajaFormaPagoTotal
import com.amaxoniaerp.features.caja.domain.CajaInventarioItem
import com.amaxoniaerp.features.caja.domain.CajaSecuencia
import com.amaxoniaerp.features.caja.domain.CajaSecuenciaData
import com.amaxoniaerp.features.caja.domain.CurrencyConfig
import com.amaxoniaerp.features.caja.domain.SellerSummary
import com.amaxoniaerp.features.companies.data.ParametrosGeneralesTableFactory
import com.amaxoniaerp.features.companies.data.ParametrosGeneralesTableVE
import com.amaxoniaerp.features.companies.data.TasasCambioTableFactory
import com.amaxoniaerp.features.companies.data.TasasCambioTableVE
import com.amaxoniaerp.features.facturas.data.EstatusTable
import com.amaxoniaerp.features.pos.data.CajaFormaPagoTable
import com.amaxoniaerp.features.pos.data.CajaFormaTable
import com.amaxoniaerp.features.sales.data.CajaIngresoEgreso
import com.amaxoniaerp.features.sales.data.CajaStatus
import com.amaxoniaerp.features.sales.data.SalesCajaNuevaDetalleFormaPagoTable
import com.amaxoniaerp.features.sales.data.SalesCajaNuevaDetalleTableFactory
import com.amaxoniaerp.features.sales.data.SalesCajaNuevaTableFactory
import com.amaxoniaerp.features.sales.data.SalesFacturaDetalleTable
import com.amaxoniaerp.features.sales.data.SalesFacturaTableFactory
import com.amaxoniaerp.features.sales.data.SalesStockTable
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.count
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.get
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.leftJoin
import org.jetbrains.exposed.sql.max
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.trim
import org.jetbrains.exposed.sql.update
import org.slf4j.LoggerFactory
import java.math.BigDecimal
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

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
                            row[CajaSecuenciaTable.fechaApertura]?.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) ?: "",
                        montoApertura = row[CajaSecuenciaTable.montoEfectivoApertura].toDouble(),
                        fechaCierre = row[CajaSecuenciaTable.fechaCierre]?.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
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
            Result.success(Unit)
        }.mapCatching {
            getCajaStatus(countryCode, dbName, request.idCaja)
                ?: throw Exception("Failed to retrieve open caja.")
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

                val request =
                    CajaCierreSaveRequest(
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

    suspend fun getCajaSecuenciaData(
        countryCode: String,
        dbName: String,
        idSecuencia: String,
        verifyFacturasTemporales: Boolean = false,
    ): Result<CajaSecuenciaData> {
        val database = DatabaseManager.connectToCompanyDb(countryCode, dbName)
        return runCatching {
            dbQuery(database) {
                val secuenciaRow =
                    CajaSecuenciaTable
                        .selectAll()
                        .where { CajaSecuenciaTable.idCajaSecuencia eq idSecuencia }
                        .limit(1)
                        .firstOrNull()
                        ?: throw IllegalStateException("Secuencia de caja no encontrada")

                val idCaja = secuenciaRow[CajaSecuenciaTable.idCaja]
                val idVendedor = secuenciaRow[CajaSecuenciaTable.idVendedor]

                val cajaRow =
                    CajaTable
                        .select(
                            CajaTable.idCaja,
                            CajaTable.codCaja,
                            CajaTable.descripcion,
                            CajaTable.caja,
                            CajaTable.fondoApertura,
                            CajaTable.impresoraModelo,
                        ).where { CajaTable.idCaja eq idCaja }
                        .limit(1)
                        .firstOrNull()

                val vendedorNombre =
                    idVendedor?.let { vendedorId ->
                        VendedorTable
                            .select(VendedorTable.nombre)
                            .where { VendedorTable.idVendedor eq vendedorId }
                            .limit(1)
                            .firstOrNull()
                            ?.get(VendedorTable.nombre)
                    }

                val detalleApertura =
                    CajaDetalleAperturaTable
                        .leftJoin(MonedaDenominacionTable, { idMonedaDenominacion }, { MonedaDenominacionTable.id })
                        .selectAll()
                        .where { CajaDetalleAperturaTable.idCajaSecuencia eq idSecuencia }
                        .map { row ->
                            CajaDetalleAperturaItem(
                                id = row[CajaDetalleAperturaTable.idDetalleApertura],
                                idSecuencia = row[CajaDetalleAperturaTable.idCajaSecuencia],
                                idMonedaDenominacion = row[CajaDetalleAperturaTable.idMonedaDenominacion],
                                cantidad = row[CajaDetalleAperturaTable.cantidad],
                                valor = row[CajaDetalleAperturaTable.valor].toDouble(),
                                monto = row[CajaDetalleAperturaTable.monto].toDouble(),
                                denominacion = row[MonedaDenominacionTable.denominacion],
                            )
                        }

                val cajaNuevaDetalleTable = SalesCajaNuevaDetalleTableFactory.forCountry(countryCode)
                val cajaNuevaTable = SalesCajaNuevaTableFactory.forCountry(countryCode)
                val montosPorForma =
                    cajaNuevaDetalleTable
                        .join(cajaNuevaTable, JoinType.INNER, cajaNuevaDetalleTable.cajaId, cajaNuevaTable.cajaId)
                        .select(cajaNuevaDetalleTable.idFormaPago, cajaNuevaDetalleTable.monto)
                        .where {
                            (cajaNuevaTable.idCajaSecuencia eq idSecuencia) and
                                (cajaNuevaTable.status neq CajaStatus.Anulada)
                        }.groupBy { it[cajaNuevaDetalleTable.idFormaPago] }
                        .mapValues { (_, rows) -> rows.sumOf { it[cajaNuevaDetalleTable.monto]?.toDouble() ?: 0.0 } }

                val formasActivas =
                    CajaFormaTable
                        .leftJoin(CajaFormaPagoTable, { idFormaPago }, { CajaFormaPagoTable.idFormaPago })
                        .leftJoin(CajaFormaPagoGrupoTable, { CajaFormaPagoTable.grupo }, { CajaFormaPagoGrupoTable.id })
                        .selectAll()
                        .where { (CajaFormaTable.idCaja eq idCaja) and (CajaFormaTable.activo eq 1) }
                        .associateBy { it[CajaFormaTable.idFormaPago] }

                val formaPagoIds = (montosPorForma.keys.filterNotNull() + formasActivas.keys).distinct()

                val catalogoFormas =
                    if (formaPagoIds.isEmpty()) {
                        emptyMap()
                    } else {
                        CajaFormaPagoTable
                            .leftJoin(CajaFormaPagoGrupoTable, { grupo }, { CajaFormaPagoGrupoTable.id })
                            .selectAll()
                            .where { CajaFormaPagoTable.idFormaPago inList formaPagoIds }
                            .associateBy { it.getOrNull(CajaFormaPagoTable.idFormaPago) }
                            .filterKeys { it != null }
                            .mapKeys { it.key!! }
                    }

                val formaPagoItems =
                    formaPagoIds
                        .mapNotNull { idForma ->
                            val row = formasActivas[idForma] ?: catalogoFormas[idForma] ?: return@mapNotNull null
                            CajaFormaPagoItem(
                                id = idForma,
                                formaPago = row.getOrNull(CajaFormaPagoTable.descripcion),
                                siglas = row.getOrNull(CajaFormaPagoTable.siglas),
                                grupo = row.getOrNull(CajaFormaPagoTable.grupo),
                                imagen = row.getOrNull(CajaFormaPagoTable.imagen)?.takeIf { it.isNotBlank() },
                                idCajaTpConcepto = row.getOrNull(CajaFormaPagoTable.idCajaTpConcepto),
                                tipoMoneda = row.getOrNull(CajaFormaPagoTable.tipoMoneda),
                                estatus = row.getOrNull(CajaFormaPagoTable.activo) ?: 0,
                                grupoNombre = row.getOrNull(CajaFormaPagoGrupoTable.grupo),
                                grupoImagen = row.getOrNull(CajaFormaPagoGrupoTable.imagen),
                                grupoOrden = row.getOrNull(CajaFormaPagoGrupoTable.orden),
                                grupoActivo = row.getOrNull(CajaFormaPagoGrupoTable.activo),
                                monto = montosPorForma[idForma] ?: 0.0,
                            )
                        }.toMutableList()

                val montoEntrada =
                    runCatching {
                        CajaMovimientoTable
                            .select(CajaMovimientoTable.total)
                            .where {
                                (CajaMovimientoTable.idSecuencia eq idSecuencia) and
                                    (CajaMovimientoTable.tipo eq "E")
                            }.sumOf { it[CajaMovimientoTable.total].toDouble() }
                    }.getOrDefault(0.0)

                val montoSalida =
                    runCatching {
                        CajaMovimientoTable
                            .select(CajaMovimientoTable.total)
                            .where {
                                (CajaMovimientoTable.idSecuencia eq idSecuencia) and
                                    (CajaMovimientoTable.tipo eq "S")
                            }.sumOf { it[CajaMovimientoTable.total].toDouble() }
                    }.getOrDefault(0.0)

                if (montoEntrada != 0.0) {
                    formaPagoItems +=
                        CajaFormaPagoItem(
                            id = -100,
                            formaPago = "ENTRADAS",
                            siglas = "E",
                            monto = montoEntrada,
                            estatus = 1,
                        )
                }
                if (montoSalida != 0.0) {
                    formaPagoItems +=
                        CajaFormaPagoItem(
                            id = -101,
                            formaPago = "SALIDAS",
                            siglas = "S",
                            monto = montoSalida,
                            estatus = 1,
                        )
                }

                val formaPagoDevolucion =
                    runCatching {
                        val devolucionRows =
                            FacturaDevolucionTable
                                .selectAll()
                                .where { FacturaDevolucionTable.idCajaSecuencia eq idSecuencia }
                                .toList()

                        val devolucionesPorForma =
                            devolucionRows
                                .groupBy { it[FacturaDevolucionTable.idFormaPago] ?: 30 }
                                .mapValues { (_, rows) ->
                                    rows.sumOf { row -> row[FacturaDevolucionTable.totalTotalFactura]?.toDouble() ?: 0.0 }
                                }

                        if (devolucionesPorForma.isEmpty()) {
                            emptyList()
                        } else {
                            val ids = devolucionesPorForma.keys.toList()
                            val meta =
                                CajaFormaPagoTable
                                    .select(CajaFormaPagoTable.idFormaPago, CajaFormaPagoTable.siglas, CajaFormaPagoTable.descripcion)
                                    .where { CajaFormaPagoTable.idFormaPago inList ids }
                                    .associateBy { it[CajaFormaPagoTable.idFormaPago] }

                            devolucionesPorForma.map { (idForma, monto) ->
                                val row = meta[idForma]
                                CajaFormaPagoDevolucionItem(
                                    idFormaPago = idForma,
                                    siglas = row?.get(CajaFormaPagoTable.siglas) ?: if (idForma == 30) "NC" else null,
                                    descripcion =
                                        row?.get(CajaFormaPagoTable.descripcion) ?: if (idForma ==
                                            30
                                        ) {
                                            "NOTA DE CREDITO"
                                        } else {
                                            null
                                        },
                                    monto = monto,
                                )
                            }
                        }
                    }.getOrDefault(emptyList())

                formaPagoDevolucion.forEach { devolucion ->
                    val index = formaPagoItems.indexOfFirst { it.id == devolucion.idFormaPago }
                    if (index >= 0) {
                        val current = formaPagoItems[index]
                        formaPagoItems[index] = current.copy(monto = current.monto + devolucion.monto)
                    } else {
                        formaPagoItems +=
                            CajaFormaPagoItem(
                                id = devolucion.idFormaPago,
                                formaPago = devolucion.descripcion ?: "NOTA DE CREDITO",
                                siglas = devolucion.siglas ?: "NC",
                                estatus = 1,
                                monto = devolucion.monto,
                            )
                    }
                }

                val montoEfectivoVentasCalc =
                    formaPagoItems
                        .filter { it.id > 0 && isCashSigla(it.siglas) }
                        .sumOf { it.monto }

                val montoOtrosTotalCalc =
                    formaPagoItems
                        .filter { it.id > 0 && !isCashSigla(it.siglas) }
                        .sumOf { it.monto }

                val facturaTable = SalesFacturaTableFactory.forCountry(countryCode)
                val totalAnulado =
                    facturaTable
                        .select(facturaTable.totalTotalFactura)
                        .where {
                            (facturaTable.idCajaSecuencia eq idSecuencia) and
                                (facturaTable.codEstatus eq 3)
                        }.sumOf { it[facturaTable.totalTotalFactura].toDouble() }

                val facturasValidas =
                    facturaTable
                        .select(facturaTable.idFactura, facturaTable.totalTotalFactura, facturaTable.codEstatus)
                        .where { facturaTable.idCajaSecuencia eq idSecuencia }
                        .filter { row -> (row[facturaTable.codEstatus] ?: 0) != 3 }
                val totalVentas = facturasValidas.sumOf { it[facturaTable.totalTotalFactura].toDouble() }
                val cantidadTransacciones = facturasValidas.size
                val facturaIds = facturasValidas.map { it[facturaTable.idFactura] }
                val detalleVentas =
                    if (facturaIds.isEmpty()) {
                        emptyList()
                    } else {
                        SalesFacturaDetalleTable
                            .select(
                                SalesFacturaDetalleTable.idItem,
                                SalesFacturaDetalleTable.itemCodigo,
                                SalesFacturaDetalleTable.itemDescripcion,
                                SalesFacturaDetalleTable.itemCantidadTotal,
                            ).where { SalesFacturaDetalleTable.idFactura inList facturaIds }
                            .toList()
                    }
                val itemIds = detalleVentas.map { it[SalesFacturaDetalleTable.idItem] }.distinct()
                val stockDisponible =
                    if (itemIds.isEmpty()) {
                        emptyMap()
                    } else {
                        SalesStockTable
                            .select(SalesStockTable.idItem, SalesStockTable.cantidad)
                            .where { SalesStockTable.idItem inList itemIds }
                            .groupBy { it[SalesStockTable.idItem] }
                            .mapValues { (_, rows) -> rows.sumOf { it[SalesStockTable.cantidad].toDouble() } }
                    }
                val inventario =
                    detalleVentas
                        .groupBy { it[SalesFacturaDetalleTable.idItem] }
                        .map { (itemId, rows) ->
                            val first = rows.first()
                            val sold = rows.sumOf { it[SalesFacturaDetalleTable.itemCantidadTotal].toDouble() }
                            val available = stockDisponible[itemId] ?: 0.0
                            CajaInventarioItem(
                                codigo = first[SalesFacturaDetalleTable.itemCodigo].ifBlank { itemId.toString() },
                                descripcion = first[SalesFacturaDetalleTable.itemDescripcion].ifBlank { "Producto $itemId" },
                                existenciaInicial = available + sold,
                                cantidadVendida = sold,
                                existenciaDisponible = available,
                            )
                        }.sortedBy { it.descripcion }

                val montoEfectivoTotalCalc =
                    secuenciaRow[CajaSecuenciaTable.montoEfectivoApertura].toDouble() +
                        montoEfectivoVentasCalc +
                        montoEntrada -
                        montoSalida
                val montoTotalCalc = montoEfectivoTotalCalc + montoOtrosTotalCalc
                val montoCierreCalc =
                    secuenciaRow[CajaSecuenciaTable.montoEfectivoApertura].toDouble() +
                        totalVentas +
                        montoEntrada -
                        montoSalida -
                        totalAnulado

                val verificarTemporales =
                    if (verifyFacturasTemporales) {
                        facturaTable
                            .select(facturaTable.formaPago)
                            .where {
                                (facturaTable.idCajaSecuencia eq idSecuencia) and
                                    (facturaTable.codEstatus eq 1)
                            }.count { row -> !row[facturaTable.formaPago].equals("credito", ignoreCase = true) }
                    } else {
                        0
                    }

                val fechaApertura = secuenciaRow[CajaSecuenciaTable.fechaApertura]
                val fechaCierre = secuenciaRow[CajaSecuenciaTable.fechaCierre]
                val fechaCreacion = secuenciaRow[CajaSecuenciaTable.fechaCreacion]

                CajaSecuenciaData(
                    id = secuenciaRow[CajaSecuenciaTable.idCajaSecuencia],
                    idCaja = idCaja,
                    idVendedor = secuenciaRow[CajaSecuenciaTable.idVendedor],
                    secuencia = secuenciaRow[CajaSecuenciaTable.secuencia],
                    fechaApertura = fechaApertura?.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
                    fechaCierre = fechaCierre?.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
                    fechaCreacion = fechaCreacion?.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
                    usuario = secuenciaRow[CajaSecuenciaTable.usuario],
                    observacionApertura = secuenciaRow[CajaSecuenciaTable.observacionApertura],
                    observacionCierre = secuenciaRow[CajaSecuenciaTable.observacionCierre],
                    montoEfectivoApertura = secuenciaRow[CajaSecuenciaTable.montoEfectivoApertura].toDouble(),
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
                    vendedor = vendedorNombre,
                    detalleApertura = detalleApertura,
                    formaPago = formaPagoItems,
                    formaPagoDevolucion = formaPagoDevolucion,
                    totalAnulado = totalAnulado,
                    verificarFacturasTemporales = verificarTemporales,
                    inventario = inventario,
                )
            }
        }
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
                        ?: throw IllegalStateException("Secuencia de caja no encontrada")

                if (secuenciaRow[CajaSecuenciaTable.fechaCierre] != null) {
                    throw IllegalStateException("La secuencia de caja ya se encuentra cerrada")
                }

                if (validateFacturasTemporales) {
                    val facturaTable = SalesFacturaTableFactory.forCountry(countryCode)
                    val temporales =
                        facturaTable
                            .select(facturaTable.formaPago)
                            .where {
                                (facturaTable.idCajaSecuencia eq request.id) and
                                    (facturaTable.codEstatus eq 1)
                            }.count { row -> !row[facturaTable.formaPago].equals("credito", ignoreCase = true) }

                    if (temporales > 0) {
                        throw IllegalStateException("Existen facturas temporales pendientes por procesar")
                    }
                }

                val now = BusinessClock.nowForCountry(countryCode)
                val serieSucursal = secuenciaRow[CajaSecuenciaTable.serieSucursal]

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

                CajaDetalleCierreTable.deleteWhere { CajaDetalleCierreTable.idSecuencia eq request.id }
                CajaDetalleCierreFormaPagoTable.deleteWhere { CajaDetalleCierreFormaPagoTable.idSecuencia eq request.id }

                request.detalle
                    .filter { it.cantidad > 0 }
                    .forEach { detalle ->
                        insertCajaDetalleCierre(request.id, serieSucursal, detalle)
                    }

                request.detalleFormaPago.forEach { detalle ->
                    insertCajaDetalleCierreFormaPago(request.id, serieSucursal, detalle)
                }

                CajaCierreSaveResponse(
                    success = true,
                    message = "Cierre de caja guardado correctamente",
                    id = request.id,
                )
            }
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
            val cajaName =
                CajaTable
                    .select(CajaTable.descripcion)
                    .where { CajaTable.idCaja eq idCaja }
                    .limit(1)
                    .firstOrNull()
                    ?.get(CajaTable.descripcion)
                    ?.takeIf { it.isNotBlank() }
                    ?: "Caja"

            val vendedorName =
                CajaSecuenciaTable
                    .leftJoin(VendedorTable, { idVendedor }, { VendedorTable.idVendedor })
                    .select(VendedorTable.nombre)
                    .where { CajaSecuenciaTable.idCajaSecuencia eq secuencia.idCajaSecuencia }
                    .limit(1)
                    .firstOrNull()
                    ?.get(VendedorTable.nombre)
                    ?.takeIf { it.isNotBlank() }

            val facturaTable = SalesFacturaTableFactory.forCountry(countryCode)
            val facturaRows =
                facturaTable
                    .leftJoin(EstatusTable, { codEstatus }, { EstatusTable.codEstatus })
                    .select(
                        facturaTable.idFactura,
                        facturaTable.totalTotalFactura,
                        EstatusTable.descripcion,
                    ).where { facturaTable.idCajaSecuencia eq secuencia.idCajaSecuencia }
                    .toList()

            var totalSales = 0.0
            var transactionCount = 0
            facturaRows.forEach { row ->
                val statusDesc = row[EstatusTable.descripcion]
                if (!isCancelledStatus(statusDesc)) {
                    totalSales += row[facturaTable.totalTotalFactura].toDouble()
                    transactionCount += 1
                }
            }

            val cajaNuevaTable = SalesCajaNuevaTableFactory.forCountry(countryCode)
            val movimientos =
                cajaNuevaTable
                    .select(
                        cajaNuevaTable.ingEg,
                        cajaNuevaTable.monto,
                        cajaNuevaTable.status,
                    ).where { cajaNuevaTable.idCajaSecuencia eq secuencia.idCajaSecuencia }
                    .toList()

            var totalIncome = 0.0
            var totalExpense = 0.0
            var totalCancelled = 0.0
            movimientos.forEach { row ->
                val amount = row[cajaNuevaTable.monto]?.toDouble() ?: 0.0
                when (row[cajaNuevaTable.status]) {
                    CajaStatus.Anulada -> totalCancelled += amount
                    else ->
                        when (row[cajaNuevaTable.ingEg]) {
                            CajaIngresoEgreso.I -> totalIncome += amount
                            CajaIngresoEgreso.E -> totalExpense += amount
                            null -> Unit
                        }
                }
            }

            val formasCatalogo =
                CajaFormaPagoTable
                    .select(
                        CajaFormaPagoTable.idFormaPago,
                        CajaFormaPagoTable.siglas,
                        CajaFormaPagoTable.descripcion,
                    ).toList()
                    .associate { row ->
                        row[CajaFormaPagoTable.idFormaPago] to (row[CajaFormaPagoTable.siglas] to row[CajaFormaPagoTable.descripcion])
                    }

            val formaRows =
                SalesCajaNuevaDetalleFormaPagoTable
                    .join(
                        cajaNuevaTable,
                        JoinType.INNER,
                        onColumn = SalesCajaNuevaDetalleFormaPagoTable.cajaId,
                        otherColumn = cajaNuevaTable.cajaId,
                    ).select(
                        SalesCajaNuevaDetalleFormaPagoTable.idFormaPago,
                        SalesCajaNuevaDetalleFormaPagoTable.tipoMovimiento,
                        SalesCajaNuevaDetalleFormaPagoTable.monto,
                    ).where {
                        (cajaNuevaTable.idCajaSecuencia eq secuencia.idCajaSecuencia) and
                            (cajaNuevaTable.status neq CajaStatus.Anulada)
                    }.toList()

            data class FormaAccum(
                val idFormaPago: Int?,
                val siglas: String?,
                val descripcion: String?,
                var total: Double,
            )

            val formasMap = linkedMapOf<String, FormaAccum>()
            var totalCash = 0.0
            var totalCard = 0.0
            var totalOther = 0.0

            formaRows.forEach { row ->
                val amount = row[SalesCajaNuevaDetalleFormaPagoTable.monto]?.toDouble() ?: 0.0
                if (amount == 0.0) return@forEach

                val tipoMovimiento = row[SalesCajaNuevaDetalleFormaPagoTable.tipoMovimiento]
                val idFormaPago = row[SalesCajaNuevaDetalleFormaPagoTable.idFormaPago]
                val metadata = idFormaPago?.let { formasCatalogo[it] }
                val siglas = metadata?.first
                val descripcion = metadata?.second

                when (classifyPaymentCategory(tipoMovimiento, siglas, descripcion)) {
                    PaymentCategory.CASH -> totalCash += amount
                    PaymentCategory.CARD -> totalCard += amount
                    PaymentCategory.OTHER -> totalOther += amount
                }

                val formKey =
                    idFormaPago?.toString()
                        ?: listOf(siglas.orEmpty(), descripcion.orEmpty(), tipoMovimiento.orEmpty())
                            .joinToString("|")

                val current = formasMap[formKey]
                if (current == null) {
                    formasMap[formKey] =
                        FormaAccum(
                            idFormaPago = idFormaPago,
                            siglas = siglas ?: tipoMovimiento,
                            descripcion = descripcion,
                            total = amount,
                        )
                } else {
                    current.total += amount
                }
            }

            val expectedClose = secuencia.montoApertura + totalIncome - totalExpense - totalCancelled

            CajaCierreSummary(
                idCajaSecuencia = secuencia.idCajaSecuencia,
                idCaja = secuencia.idCaja,
                cajaName = cajaName,
                vendedorName = vendedorName,
                openedAt = secuencia.fechaApertura,
                openAmount = secuencia.montoApertura,
                totalSales = totalSales,
                transactionCount = transactionCount,
                totalCash = totalCash,
                totalCard = totalCard,
                totalOther = totalOther,
                totalIncome = totalIncome,
                totalExpense = totalExpense,
                totalCancelled = totalCancelled,
                expectedClose = expectedClose,
                formasPago =
                    formasMap.values.map {
                        CajaFormaPagoTotal(
                            idFormaPago = it.idFormaPago,
                            siglas = it.siglas,
                            descripcion = it.descripcion,
                            total = it.total,
                        )
                    },
            )
        }
    }

    suspend fun getCajas(
        countryCode: String,
        dbName: String,
        userId: Int,
    ): List<Caja> {
        val database = DatabaseManager.connectToCompanyDb(countryCode, dbName)
        return dbQuery(database) {
            val parametrosTable = ParametrosGeneralesTableFactory.forCountry(countryCode)
            val parametrosRow =
                if (parametrosTable is ParametrosGeneralesTableVE) {
                    parametrosTable.select(
                        parametrosTable.codAlmacen,
                        parametrosTable.monedaBase,
                        parametrosTable.abrMonedaBase,
                        parametrosTable.porcentajeImpuestoPrincipal,
                        parametrosTable.defaultIdFormaPagoFactura,
                        parametrosTable.multiMoneda,
                        parametrosTable.monedaSecundaria,
                        parametrosTable.abrMonedaSecundaria,
                    )
                } else {
                    parametrosTable.select(
                        parametrosTable.codAlmacen,
                        parametrosTable.monedaBase,
                        parametrosTable.abrMonedaBase,
                        parametrosTable.porcentajeImpuestoPrincipal,
                        parametrosTable.defaultIdFormaPagoFactura,
                    )
                }.orderBy(parametrosTable.codEmpresa)
                    .limit(1)
                    .firstOrNull()

            val globalDefaultWarehouse =
                parametrosRow
                    ?.get(parametrosTable.codAlmacen)
                    ?.let { kotlin.math.abs(it) }
                    ?.takeIf { it > 0 }

            val defaultTaxRate =
                parametrosRow
                    ?.get(parametrosTable.porcentajeImpuestoPrincipal)
                    ?.toDouble()
                    ?: 0.0

            val defaultFormaPagoId =
                parametrosRow
                    ?.get(parametrosTable.defaultIdFormaPagoFactura)

            val multiMonedaFromParams =
                (parametrosTable is ParametrosGeneralesTableVE) &&
                    (
                        parametrosRow
                            ?.get((parametrosTable as ParametrosGeneralesTableVE).multiMoneda)
                            ?.equals("Si", ignoreCase = true)
                            ?: false
                    )

            val monedaBase =
                parametrosRow
                    ?.get(parametrosTable.monedaBase)
                    ?: 1

            val abrMonedaBase =
                parametrosRow
                    ?.get(parametrosTable.abrMonedaBase)
                    ?.takeIf { it.isNotBlank() }
                    ?: "USD"

            val monedaSecundaria =
                if (parametrosTable is ParametrosGeneralesTableVE) {
                    parametrosRow?.get(parametrosTable.monedaSecundaria) ?: monedaBase
                } else {
                    monedaBase
                }

            val abrMonedaSecundaria =
                if (parametrosTable is ParametrosGeneralesTableVE) {
                    parametrosRow
                        ?.get(parametrosTable.abrMonedaSecundaria)
                        ?.takeIf { it.isNotBlank() }
                        ?: abrMonedaBase
                } else {
                    abrMonedaBase
                }

            val tasaActual =
                if (multiMonedaFromParams) {
                    val tasasTable = TasasCambioTableFactory.forCountry(countryCode)
                    if (tasasTable is TasasCambioTableVE) {
                        tasasTable
                            .select(tasasTable.id, tasasTable.tasaInversa)
                            .where {
                                (tasasTable.divisa eq monedaSecundaria) and
                                    (tasasTable.monedabase eq monedaBase)
                            }.orderBy(tasasTable.id to SortOrder.DESC)
                            .limit(1)
                            .firstOrNull()
                    } else {
                        null
                    }
                } else {
                    null
                }

            val currencyConfig =
                CurrencyConfig(
                    multiMoneda = if (multiMonedaFromParams) "SI" else "NO",
                    tasa =
                        tasaActual?.let {
                            val tasasTable = TasasCambioTableFactory.forCountry(countryCode) as TasasCambioTableVE
                            it[tasasTable.tasaInversa]?.toDouble() ?: 1.0
                        } ?: 1.0,
                    idTasa =
                        tasaActual?.let {
                            val tasasTable = TasasCambioTableFactory.forCountry(countryCode) as TasasCambioTableVE
                            it[tasasTable.id]?.toInt() ?: 0
                        } ?: 0,
                    monedaBase = monedaBase,
                    abrMonedaBase = abrMonedaBase,
                    monedaSecundaria = monedaSecundaria,
                    abrMonedaSecundaria = abrMonedaSecundaria,
                )

            val defaultBySucursal =
                SucursalAlmacenTable
                    .select(SucursalAlmacenTable.idSucursal, SucursalAlmacenTable.idAlmacen)
                    .where { SucursalAlmacenTable.defaultVentas eq 1 }
                    .orderBy(SucursalAlmacenTable.idSucursal)
                    .groupBy { it[SucursalAlmacenTable.idSucursal] }
                    .mapValues { (_, rows) ->
                        rows.firstNotNullOfOrNull { row ->
                            row[SucursalAlmacenTable.idAlmacen].takeIf { it > 0 }
                        }
                    }

            val activeSellers =
                VendedorTable
                    .select(
                        VendedorTable.idVendedor,
                        VendedorTable.nombre,
                        VendedorTable.codUsuarios,
                        VendedorTable.idCajas,
                        VendedorTable.idTiendas,
                    ).where { VendedorTable.activo eq 1 }
                    .orderBy(VendedorTable.idVendedor)
                    .map { row ->
                        SellerRecord(
                            id = row[VendedorTable.idVendedor],
                            nombre = row[VendedorTable.nombre],
                            codUsuarios = row[VendedorTable.codUsuarios],
                            idCajas = row[VendedorTable.idCajas],
                            idTiendas = row[VendedorTable.idTiendas],
                        )
                    }

            val availableSellers = activeSellers.map { SellerSummary(id = it.id, nombre = it.nombre) }
            val userIdToken = userId.toString()
            val assignedCajaIds =
                activeSellers
                    .filter { csvContains(it.codUsuarios, userIdToken) }
                    .flatMap { csvTokens(it.idCajas) }
                    .toSet()

            val isVE = countryCode.equals("VE", ignoreCase = true)

            val cajaColumns =
                CajaTable.columns
                    .filter { isVE || it != CajaTable.codAlmacen }

            CajaTable
                .leftJoin(
                    otherTable = SucursalTable,
                    onColumn = { CajaTable.idSucursal },
                    otherColumn = { SucursalTable.idSucursal },
                ).select(cajaColumns + SucursalTable.columns)
                .map { row ->
                    val nombreSucursal =
                        row[SucursalTable.sucursal]?.takeIf { it.isNotBlank() }
                            ?: row[SucursalTable.descripcion]?.takeIf { it.isNotBlank() }

                    val idCaja = row[CajaTable.idCaja]
                    val idSucursal = row[CajaTable.idSucursal]
                    val cajaWarehouse = row.getOrNull(CajaTable.codAlmacen)?.takeIf { it > 0 }
                    val resolvedDefaultWarehouse =
                        cajaWarehouse
                            ?: idSucursal?.let { defaultBySucursal[it] }
                            ?: globalDefaultWarehouse

                    val sucursalToken = idSucursal?.toString()
                    val defaultSeller =
                        activeSellers.firstOrNull { csvContains(it.codUsuarios, userIdToken) }
                            ?: activeSellers.firstOrNull { csvContains(it.idCajas, idCaja) }
                            ?: sucursalToken?.let { token ->
                                activeSellers.firstOrNull { csvContains(it.idTiendas, token) }
                            }

                    Caja(
                        idCaja = idCaja,
                        codCaja = row[CajaTable.codCaja],
                        caja = row[CajaTable.caja],
                        descripcion = row[CajaTable.descripcion],
                        estatus = row[CajaTable.codEstatus],
                        idSucursal = idSucursal,
                        codAlmacen = row.getOrNull(CajaTable.codAlmacen),
                        defaultWarehouseId = resolvedDefaultWarehouse,
                        defaultSellerId = defaultSeller?.id,
                        defaultSellerName = defaultSeller?.nombre,
                        availableSellers = availableSellers,
                        serieSucursal = row[SucursalTable.serie],
                        defaultTaxRate = defaultTaxRate,
                        defaultFormaPagoId = defaultFormaPagoId,
                        currency = currencyConfig,
                        serieCaja = row[CajaTable.serieCaja],
                        sucursalNombre = nombreSucursal,
                        sucursalCodigo = row[SucursalTable.codigo],
                        codigoSucursalEmisor = row[SucursalTable.codigoSucursalEmisor],
                    )
                }.filter { caja -> assignedCajaIds.isEmpty() || caja.idCaja in assignedCajaIds }
        }
    }

    private fun csvContains(
        csv: String?,
        token: String,
    ): Boolean {
        if (csv.isNullOrBlank()) return false
        return csv.split(',').any { it.trim() == token }
    }

    private fun csvTokens(csv: String?): List<String> {
        if (csv.isNullOrBlank()) return emptyList()
        return csv.split(',').mapNotNull { it.trim().takeIf(String::isNotBlank) }
    }

    private fun resolveNextSecuenciaCode(idCaja: String): String {
        val max =
            CajaSecuenciaTable
                .select(CajaSecuenciaTable.secuencia)
                .where { CajaSecuenciaTable.idCaja eq idCaja }
                .mapNotNull { row -> row[CajaSecuenciaTable.secuencia]?.trim()?.toIntOrNull() }
                .maxOrNull()
                ?: 0
        val next = max + 1
        return next.toString().padStart(6, '0')
    }

    private fun insertCajaDetalleCierre(
        idSecuencia: String,
        serieSucursal: String,
        detalle: CajaCierreDetalleRequest,
    ) {
        CajaDetalleCierreTable.insert {
            it[id] = UUID.randomUUID().toString()
            it[CajaDetalleCierreTable.idSecuencia] = idSecuencia
            it[idMonedaDenominacion] = detalle.idMonedaDenominacion
            it[cantidad] = detalle.cantidad
            it[valor] = detalle.valor.toMoney()
            it[monto] = detalle.monto.toMoney()
            it[CajaDetalleCierreTable.serieSucursal] = serieSucursal
        }
    }

    private fun insertCajaDetalleCierreFormaPago(
        idSecuencia: String,
        serieSucursal: String,
        detalle: CajaCierreFormaPagoRequest,
    ) {
        CajaDetalleCierreFormaPagoTable.insert {
            it[id] = UUID.randomUUID().toString()
            it[CajaDetalleCierreFormaPagoTable.idSecuencia] = idSecuencia
            it[idFormaPago] = detalle.idFormaPago
            it[montoVentas] = detalle.monto.toMoney()
            it[montoCierre] = detalle.montoCierre.toMoney()
            it[montoDiferencia] = detalle.montoDiferencia.toMoney()
            it[CajaDetalleCierreFormaPagoTable.serieSucursal] = serieSucursal
        }
    }

    private fun Double.toMoney(): BigDecimal = BigDecimal.valueOf(this).setScale(2, java.math.RoundingMode.HALF_UP)

    private fun buildAutoCloseFormaPagoTotals(data: CajaSecuenciaData): Map<Int, FormaPagoCloseTotal> {
        val totals = linkedMapOf<Int, FormaPagoCloseTotal>()

        data.formaPago
            .asSequence()
            .filter { it.id > 0 && it.monto != 0.0 }
            .forEach { line ->
                totals.merge(
                    line.id,
                    FormaPagoCloseTotal(sigla = line.siglas, monto = line.monto),
                ) { current, incoming ->
                    current.copy(monto = current.monto + incoming.monto)
                }
            }

        data.formaPagoDevolucion
            .asSequence()
            .filter { it.idFormaPago > 0 && it.monto != 0.0 }
            .forEach { line ->
                totals.merge(
                    line.idFormaPago,
                    FormaPagoCloseTotal(sigla = line.siglas, monto = line.monto),
                ) { current, incoming ->
                    current.copy(monto = current.monto + incoming.monto)
                }
            }

        return totals
            .filterValues { it.monto > 0.0 }
            .toMap()
    }

    private fun isCancelledStatus(description: String?): Boolean {
        if (description.isNullOrBlank()) return false
        return description.equals("Anulada", ignoreCase = true) ||
            description.equals("Anulado", ignoreCase = true)
    }

    private fun isCashSigla(siglas: String?): Boolean {
        val value = siglas.orEmpty().trim().uppercase()
        return value == "CASH" || value == "EF" || value == "EFE" || value == "EFECTIVO"
    }

    private fun classifyPaymentCategory(
        tipoMovimiento: String?,
        siglas: String?,
        descripcion: String?,
    ): PaymentCategory {
        val normalizedSigla = siglas.orEmpty().trim().uppercase()
        val normalizedTipo = tipoMovimiento.orEmpty().trim().uppercase()
        val normalizedDescripcion = descripcion.orEmpty().trim().uppercase()

        if (normalizedSigla in CASH_CODES || normalizedTipo in CASH_CODES || normalizedDescripcion.contains("EFECTIVO")) {
            return PaymentCategory.CASH
        }
        if (
            normalizedSigla in CARD_CODES ||
            normalizedTipo in CARD_CODES ||
            normalizedDescripcion.contains("TARJETA") ||
            normalizedDescripcion.contains("DEBITO") ||
            normalizedDescripcion.contains("CREDITO")
        ) {
            return PaymentCategory.CARD
        }
        return PaymentCategory.OTHER
    }

    private enum class PaymentCategory {
        CASH,
        CARD,
        OTHER,
    }

    private companion object {
        val CASH_CODES = setOf("CASH", "EF", "EFE", "EFECTIVO")
        val CARD_CODES = setOf("TDC", "TARJETA", "PV", "POS", "NEQ", "DB", "DEBITO", "CR", "CREDITO")
    }

    private data class FormaPagoCloseTotal(
        val sigla: String?,
        val monto: Double,
    )

    private data class SellerRecord(
        val id: Int,
        val nombre: String,
        val codUsuarios: String,
        val idCajas: String?,
        val idTiendas: String,
    )
}
