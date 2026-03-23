package com.amaxoniaerp.features.caja.data

import com.amaxoniaerp.core.database.DatabaseManager
import com.amaxoniaerp.core.database.dbQuery
import com.amaxoniaerp.features.caja.domain.AperturaRequest
import com.amaxoniaerp.features.caja.domain.Caja
import com.amaxoniaerp.features.caja.domain.CajaCierreDetalleRequest
import com.amaxoniaerp.features.caja.domain.CajaCierreFormaPagoRequest
import com.amaxoniaerp.features.caja.domain.CajaCierreSaveRequest
import com.amaxoniaerp.features.caja.domain.CajaCierreSaveResponse
import com.amaxoniaerp.features.caja.domain.CajaCierreSummary
import com.amaxoniaerp.features.caja.domain.CajaDetalleAperturaItem
import com.amaxoniaerp.features.caja.domain.CajaFormaPagoTotal
import com.amaxoniaerp.features.caja.domain.CajaFormaPagoDevolucionItem
import com.amaxoniaerp.features.caja.domain.CajaFormaPagoItem
import com.amaxoniaerp.features.caja.domain.CajaSecuenciaData
import com.amaxoniaerp.features.caja.domain.CajaSecuencia
import com.amaxoniaerp.features.caja.domain.CurrencyConfig
import com.amaxoniaerp.features.caja.domain.SellerSummary
import com.amaxoniaerp.features.companies.data.ParametrosGeneralesTable
import com.amaxoniaerp.features.companies.data.TasasCambioTable
import com.amaxoniaerp.features.facturas.data.EstatusTable
import com.amaxoniaerp.features.pos.data.CajaFormaTable
import com.amaxoniaerp.features.pos.data.CajaFormaPagoTable
import com.amaxoniaerp.features.sales.data.CajaIngresoEgreso
import com.amaxoniaerp.features.sales.data.CajaStatus
import com.amaxoniaerp.features.sales.data.SalesCajaNuevaDetalleTable
import com.amaxoniaerp.features.sales.data.SalesCajaNuevaDetalleFormaPagoTable
import com.amaxoniaerp.features.sales.data.SalesCajaNuevaTable
import com.amaxoniaerp.features.sales.data.SalesFacturaTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.slf4j.LoggerFactory
import java.math.BigDecimal
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

class CajaRepository {
    private val log = LoggerFactory.getLogger(CajaRepository::class.java)

    suspend fun getCajaStatus(dbName: String, idCaja: String): CajaSecuencia? {
        val database = DatabaseManager.connectToCompanyDb(dbName)
        return dbQuery(database) {
            val openCajas = CajaSecuenciaTable
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
                    openCajas.map { it[CajaSecuenciaTable.idCajaSecuencia] }
                )
            }

            openCajas
                .firstOrNull()
                ?.let { row ->
                    CajaSecuencia(
                        idCajaSecuencia = row[CajaSecuenciaTable.idCajaSecuencia],
                        idCaja = row[CajaSecuenciaTable.idCaja],
                        fechaApertura = row[CajaSecuenciaTable.fechaApertura]?.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) ?: "",
                        montoApertura = row[CajaSecuenciaTable.montoEfectivoApertura].toDouble(),
                        fechaCierre = row[CajaSecuenciaTable.fechaCierre]?.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
                        montoCierre = row[CajaSecuenciaTable.montoEfectivoCierre]?.toDouble(),
                        estatus = if (row[CajaSecuenciaTable.fechaCierre] == null) 1 else 0,
                        usuarioApertura = row[CajaSecuenciaTable.usuario] ?: "",
                        usuarioCierre = null,
                        serieSucursal = row[CajaSecuenciaTable.serieSucursal],
                        idSucursal = 1 // default since it was removed
                    )
                }
        }
    }

    suspend fun openCaja(dbName: String, request: AperturaRequest, username: String): Result<CajaSecuencia> {
        val database = DatabaseManager.connectToCompanyDb(dbName)
        val currentOpen = getCajaStatus(dbName, request.idCaja)
        if (currentOpen != null) {
            autoCloseOpenSequence(dbName, currentOpen.idCajaSecuencia).fold(
                onSuccess = { },
                onFailure = { error ->
                    return Result.failure(
                        IllegalStateException(
                            "No se pudo cerrar automaticamente la secuencia abierta: ${error.message}",
                            error,
                        )
                    )
                }
            )
        }

        val now = LocalDateTime.now()
        val newId = UUID.randomUUID().toString()
        val nextSequence = dbQuery(database) {
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
            getCajaStatus(dbName, request.idCaja)
                ?: throw Exception("Failed to retrieve open caja.")
        }
    }

    suspend fun getNextSecuenciaCodigo(dbName: String, idCaja: String): Result<String> {
        val database = DatabaseManager.connectToCompanyDb(dbName)
        return runCatching {
            dbQuery(database) {
                resolveNextSecuenciaCode(idCaja)
            }
        }
    }

    private suspend fun autoCloseOpenSequence(dbName: String, idSecuencia: String): Result<Unit> {
        return getCajaSecuenciaData(dbName, idSecuencia, verifyFacturasTemporales = false).fold(
            onSuccess = { data ->
                val formaPagoTotals = buildAutoCloseFormaPagoTotals(data)
                val montoEfectivoVentas = formaPagoTotals
                    .filter { (_, item) -> isCashSigla(item.sigla) }
                    .values
                    .sumOf { it.monto }
                val montoOtrosTotal = formaPagoTotals
                    .filterNot { (_, item) -> isCashSigla(item.sigla) }
                    .values
                    .sumOf { it.monto }
                val montoEfectivoTotal =
                    data.monto_efectivo_apertura +
                        montoEfectivoVentas +
                        data.monto_efectivo_entrada -
                        data.monto_efectivo_salida
                val montoTotal = montoEfectivoTotal + montoOtrosTotal

                val request = CajaCierreSaveRequest(
                    id = data.id,
                    monto_efectivo_ventas = montoEfectivoVentas,
                    monto_efectivo_entrada = data.monto_efectivo_entrada,
                    monto_efectivo_salida = data.monto_efectivo_salida,
                    monto_efectivo_total = montoEfectivoTotal,
                    monto_efectivo_cierre = montoEfectivoTotal,
                    monto_efectivo_diferencia = 0.0,
                    monto_otros_total = montoOtrosTotal,
                    monto_otros_cierre = montoOtrosTotal,
                    monto_otros_diferencia = 0.0,
                    monto_total = montoTotal,
                    monto_cierre = montoTotal,
                    monto_diferencia = 0.0,
                    detalle = emptyList(),
                    detalle_formapago = formaPagoTotals
                        .map { (idFormaPago, item) ->
                            CajaCierreFormaPagoRequest(
                                id_forma_pago = idFormaPago,
                                monto = item.monto,
                                monto_cierre = item.monto,
                                monto_diferencia = 0.0,
                            )
                        },
                    observacion_cierre = "Cierre automático por nueva apertura",
                    numero_cierre_fiscal = "",
                )

                saveCajaCierreInternal(
                    dbName = dbName,
                    request = request,
                    validateFacturasTemporales = false,
                ).map { Unit }
            },
            onFailure = { error ->
                Result.failure(error)
            }
        )
    }

    suspend fun getCajaSecuenciaData(
        dbName: String,
        idSecuencia: String,
        verifyFacturasTemporales: Boolean = false,
    ): Result<CajaSecuenciaData> {
        val database = DatabaseManager.connectToCompanyDb(dbName)
        return runCatching {
            dbQuery(database) {
                val secuenciaRow = CajaSecuenciaTable
                    .selectAll()
                    .where { CajaSecuenciaTable.idCajaSecuencia eq idSecuencia }
                    .limit(1)
                    .firstOrNull()
                    ?: throw IllegalStateException("Secuencia de caja no encontrada")

                val idCaja = secuenciaRow[CajaSecuenciaTable.idCaja]
                val idVendedor = secuenciaRow[CajaSecuenciaTable.idVendedor]

                val cajaRow = CajaTable
                    .selectAll()
                    .where { CajaTable.idCaja eq idCaja }
                    .limit(1)
                    .firstOrNull()

                val vendedorNombre = idVendedor?.let { vendedorId ->
                    VendedorTable
                        .select(VendedorTable.nombre)
                        .where { VendedorTable.idVendedor eq vendedorId }
                        .limit(1)
                        .firstOrNull()
                        ?.get(VendedorTable.nombre)
                }

                val detalleApertura = CajaDetalleAperturaTable
                    .leftJoin(MonedaDenominacionTable, { idMonedaDenominacion }, { MonedaDenominacionTable.id })
                    .selectAll()
                    .where { CajaDetalleAperturaTable.idCajaSecuencia eq idSecuencia }
                    .map { row ->
                        CajaDetalleAperturaItem(
                            id = row[CajaDetalleAperturaTable.idDetalleApertura],
                            id_secuencia = row[CajaDetalleAperturaTable.idCajaSecuencia],
                            id_moneda_denominacion = row[CajaDetalleAperturaTable.idMonedaDenominacion],
                            cantidad = row[CajaDetalleAperturaTable.cantidad],
                            valor = row[CajaDetalleAperturaTable.valor].toDouble(),
                            monto = row[CajaDetalleAperturaTable.monto].toDouble(),
                            denominacion = row[MonedaDenominacionTable.denominacion],
                        )
                    }

                val montosPorForma = SalesCajaNuevaDetalleTable
                    .join(SalesCajaNuevaTable, JoinType.INNER, SalesCajaNuevaDetalleTable.cajaId, SalesCajaNuevaTable.cajaId)
                    .select(SalesCajaNuevaDetalleTable.idFormaPago, SalesCajaNuevaDetalleTable.monto)
                    .where {
                        (SalesCajaNuevaTable.idCajaSecuencia eq idSecuencia) and
                            (SalesCajaNuevaTable.status neq CajaStatus.Anulada)
                    }
                    .groupBy { it[SalesCajaNuevaDetalleTable.idFormaPago] }
                    .mapValues { (_, rows) -> rows.sumOf { it[SalesCajaNuevaDetalleTable.monto]?.toDouble() ?: 0.0 } }

                val formasActivas = CajaFormaTable
                    .leftJoin(CajaFormaPagoTable, { idFormaPago }, { CajaFormaPagoTable.idFormaPago })
                    .leftJoin(CajaFormaPagoGrupoTable, { CajaFormaPagoTable.grupo }, { CajaFormaPagoGrupoTable.id })
                    .selectAll()
                    .where { (CajaFormaTable.idCaja eq idCaja) and (CajaFormaTable.activo eq 1) }
                    .associateBy { it[CajaFormaPagoTable.idFormaPago] }

                val formaPagoIds = (montosPorForma.keys.filterNotNull() + formasActivas.keys).distinct()

                val catalogoFormas = if (formaPagoIds.isEmpty()) {
                    emptyMap()
                } else {
                    CajaFormaPagoTable
                        .leftJoin(CajaFormaPagoGrupoTable, { grupo }, { CajaFormaPagoGrupoTable.id })
                        .selectAll()
                        .where { CajaFormaPagoTable.idFormaPago inList formaPagoIds }
                        .associateBy { it[CajaFormaPagoTable.idFormaPago] }
                }

                val formaPagoItems = formaPagoIds.mapNotNull { idForma ->
                    val row = formasActivas[idForma] ?: catalogoFormas[idForma] ?: return@mapNotNull null
                    CajaFormaPagoItem(
                        id = idForma,
                        forma_pago = row[CajaFormaPagoTable.descripcion],
                        siglas = row[CajaFormaPagoTable.siglas],
                        grupo = row[CajaFormaPagoTable.grupo],
                        imagen = row[CajaFormaPagoTable.imagen].takeIf { it.isNotBlank() },
                        id_caja_tp_concepto = row[CajaFormaPagoTable.idCajaTpConcepto],
                        tipo_moneda = row[CajaFormaPagoTable.tipoMoneda],
                        estatus = row[CajaFormaPagoTable.activo],
                        grupo_nombre = row[CajaFormaPagoGrupoTable.grupo],
                        grupo_imagen = row[CajaFormaPagoGrupoTable.imagen],
                        grupo_orden = row[CajaFormaPagoGrupoTable.orden],
                        grupo_activo = row[CajaFormaPagoGrupoTable.activo],
                        monto = montosPorForma[idForma] ?: 0.0,
                    )
                }.toMutableList()

                val montoEntrada = runCatching {
                    CajaMovimientoTable
                        .select(CajaMovimientoTable.total)
                        .where {
                            (CajaMovimientoTable.idSecuencia eq idSecuencia) and
                                (CajaMovimientoTable.tipo eq "E")
                        }
                        .sumOf { it[CajaMovimientoTable.total].toDouble() }
                }.getOrDefault(0.0)

                val montoSalida = runCatching {
                    CajaMovimientoTable
                        .select(CajaMovimientoTable.total)
                        .where {
                            (CajaMovimientoTable.idSecuencia eq idSecuencia) and
                                (CajaMovimientoTable.tipo eq "S")
                        }
                        .sumOf { it[CajaMovimientoTable.total].toDouble() }
                }.getOrDefault(0.0)

                if (montoEntrada != 0.0) {
                    formaPagoItems += CajaFormaPagoItem(
                        id = -100,
                        forma_pago = "ENTRADAS",
                        siglas = "E",
                        monto = montoEntrada,
                        estatus = 1,
                    )
                }
                if (montoSalida != 0.0) {
                    formaPagoItems += CajaFormaPagoItem(
                        id = -101,
                        forma_pago = "SALIDAS",
                        siglas = "S",
                        monto = montoSalida,
                        estatus = 1,
                    )
                }

                val formaPagoDevolucion = runCatching {
                    val devolucionRows = FacturaDevolucionTable
                        .selectAll()
                        .where { FacturaDevolucionTable.idCajaSecuencia eq idSecuencia }
                        .toList()

                    val devolucionesPorForma = devolucionRows
                        .groupBy { it[FacturaDevolucionTable.idFormaPago] ?: 30 }
                        .mapValues { (_, rows) ->
                            rows.sumOf { row -> row[FacturaDevolucionTable.totalTotalFactura]?.toDouble() ?: 0.0 }
                        }

                    if (devolucionesPorForma.isEmpty()) {
                        emptyList()
                    } else {
                        val ids = devolucionesPorForma.keys.toList()
                        val meta = CajaFormaPagoTable
                            .select(CajaFormaPagoTable.idFormaPago, CajaFormaPagoTable.siglas, CajaFormaPagoTable.descripcion)
                            .where { CajaFormaPagoTable.idFormaPago inList ids }
                            .associateBy { it[CajaFormaPagoTable.idFormaPago] }

                        devolucionesPorForma.map { (idForma, monto) ->
                            val row = meta[idForma]
                            CajaFormaPagoDevolucionItem(
                                id_forma_pago = idForma,
                                siglas = row?.get(CajaFormaPagoTable.siglas) ?: if (idForma == 30) "NC" else null,
                                descripcion = row?.get(CajaFormaPagoTable.descripcion) ?: if (idForma == 30) "NOTA DE CREDITO" else null,
                                monto = monto,
                            )
                        }
                    }
                }.getOrDefault(emptyList())

                formaPagoDevolucion.forEach { devolucion ->
                    val index = formaPagoItems.indexOfFirst { it.id == devolucion.id_forma_pago }
                    if (index >= 0) {
                        val current = formaPagoItems[index]
                        formaPagoItems[index] = current.copy(monto = current.monto + devolucion.monto)
                    } else {
                        formaPagoItems += CajaFormaPagoItem(
                            id = devolucion.id_forma_pago,
                            forma_pago = devolucion.descripcion ?: "NOTA DE CREDITO",
                            siglas = devolucion.siglas ?: "NC",
                            estatus = 1,
                            monto = devolucion.monto,
                        )
                    }
                }

                val montoEfectivoVentasCalc = formaPagoItems
                    .filter { it.id > 0 && isCashSigla(it.siglas) }
                    .sumOf { it.monto }

                val montoOtrosTotalCalc = formaPagoItems
                    .filter { it.id > 0 && !isCashSigla(it.siglas) }
                    .sumOf { it.monto }

                val totalAnulado = SalesFacturaTable
                    .select(SalesFacturaTable.totalTotalFactura)
                    .where {
                        (SalesFacturaTable.idCajaSecuencia eq idSecuencia) and
                            (SalesFacturaTable.codEstatus eq 3)
                    }
                    .sumOf { it[SalesFacturaTable.totalTotalFactura].toDouble() }

                val facturasValidas = SalesFacturaTable
                    .select(SalesFacturaTable.totalTotalFactura, SalesFacturaTable.codEstatus)
                    .where { SalesFacturaTable.idCajaSecuencia eq idSecuencia }
                    .filter { row -> (row[SalesFacturaTable.codEstatus] ?: 0) != 3 }
                val totalVentas = facturasValidas.sumOf { it[SalesFacturaTable.totalTotalFactura].toDouble() }
                val cantidadTransacciones = facturasValidas.size

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

                val verificarTemporales = if (verifyFacturasTemporales) {
                    SalesFacturaTable
                        .select(SalesFacturaTable.formaPago)
                        .where {
                            (SalesFacturaTable.idCajaSecuencia eq idSecuencia) and
                                (SalesFacturaTable.codEstatus eq 1)
                        }
                        .count { row -> !row[SalesFacturaTable.formaPago].equals("credito", ignoreCase = true) }
                } else {
                    0
                }

                val fechaApertura = secuenciaRow[CajaSecuenciaTable.fechaApertura]
                val fechaCierre = secuenciaRow[CajaSecuenciaTable.fechaCierre]
                val fechaCreacion = secuenciaRow[CajaSecuenciaTable.fechaCreacion]

                CajaSecuenciaData(
                    id = secuenciaRow[CajaSecuenciaTable.idCajaSecuencia],
                    id_caja = idCaja,
                    id_vendedor = secuenciaRow[CajaSecuenciaTable.idVendedor],
                    secuencia = secuenciaRow[CajaSecuenciaTable.secuencia],
                    fecha_apertura = fechaApertura?.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
                    fecha_cierre = fechaCierre?.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
                    fecha_creacion = fechaCreacion?.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
                    usuario = secuenciaRow[CajaSecuenciaTable.usuario],
                    observacion_apertura = secuenciaRow[CajaSecuenciaTable.observacionApertura],
                    observacion_cierre = secuenciaRow[CajaSecuenciaTable.observacionCierre],
                    monto_efectivo_apertura = secuenciaRow[CajaSecuenciaTable.montoEfectivoApertura].toDouble(),
                    monto_efectivo_ventas = montoEfectivoVentasCalc,
                    monto_efectivo_entrada = montoEntrada,
                    monto_efectivo_salida = montoSalida,
                    monto_efectivo_total = montoEfectivoTotalCalc,
                    monto_efectivo_cierre = montoEfectivoTotalCalc,
                    monto_efectivo_diferencia = 0.0,
                    monto_otros_total = montoOtrosTotalCalc,
                    monto_otros_cierre = montoOtrosTotalCalc,
                    monto_otros_diferencia = 0.0,
                    monto_total = montoTotalCalc,
                    monto_cierre = montoCierreCalc,
                    monto_diferencia = 0.0,
                    total_ventas = totalVentas,
                    cantidad_transacciones = cantidadTransacciones,
                    numero_cierre_fiscal = secuenciaRow[CajaSecuenciaTable.numeroCierreFiscal],
                    serie_sucursal = secuenciaRow[CajaSecuenciaTable.serieSucursal],
                    serial_fiscal = secuenciaRow[CajaSecuenciaTable.serialFiscal],
                    contabilizado = secuenciaRow[CajaSecuenciaTable.contabilizado],
                    ffecha_apertura = fechaApertura?.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")) ?: "",
                    ffecha_cierre = fechaCierre?.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")) ?: "",
                    caja_codigo = cajaRow?.get(CajaTable.codCaja),
                    caja = cajaRow?.get(CajaTable.caja) ?: cajaRow?.get(CajaTable.descripcion),
                    fondo_apertura = cajaRow?.get(CajaTable.fondoApertura)?.toDouble() ?: 0.0,
                    nombre_modelo = cajaRow?.get(CajaTable.impresoraModelo),
                    vendedor = vendedorNombre,
                    detalle_apertura = detalleApertura,
                    forma_pago = formaPagoItems,
                    forma_pago_devolucion = formaPagoDevolucion,
                    total_anulado = totalAnulado,
                    verificar_facturas_temporales = verificarTemporales,
                )
            }
        }
    }

    suspend fun saveCajaCierre(dbName: String, request: CajaCierreSaveRequest): Result<CajaCierreSaveResponse> {
        return saveCajaCierreInternal(
            dbName = dbName,
            request = request,
            validateFacturasTemporales = true,
        )
    }

    private suspend fun saveCajaCierreInternal(
        dbName: String,
        request: CajaCierreSaveRequest,
        validateFacturasTemporales: Boolean,
    ): Result<CajaCierreSaveResponse> {
        val database = DatabaseManager.connectToCompanyDb(dbName)
        return runCatching {
            dbQuery(database) {
                val secuenciaRow = CajaSecuenciaTable
                    .selectAll()
                    .where { CajaSecuenciaTable.idCajaSecuencia eq request.id }
                    .limit(1)
                    .firstOrNull()
                    ?: throw IllegalStateException("Secuencia de caja no encontrada")

                if (secuenciaRow[CajaSecuenciaTable.fechaCierre] != null) {
                    throw IllegalStateException("La secuencia de caja ya se encuentra cerrada")
                }

                if (validateFacturasTemporales) {
                    val temporales = SalesFacturaTable
                        .select(SalesFacturaTable.formaPago)
                        .where {
                            (SalesFacturaTable.idCajaSecuencia eq request.id) and
                                (SalesFacturaTable.codEstatus eq 1)
                        }
                        .count { row -> !row[SalesFacturaTable.formaPago].equals("credito", ignoreCase = true) }

                    if (temporales > 0) {
                        throw IllegalStateException("Existen facturas temporales pendientes por procesar")
                    }
                }

                val now = LocalDateTime.now()
                val serieSucursal = secuenciaRow[CajaSecuenciaTable.serieSucursal]

                CajaSecuenciaTable.update({ CajaSecuenciaTable.idCajaSecuencia eq request.id }) {
                    it[fechaCierre] = now
                    it[montoEfectivoVentas] = request.monto_efectivo_ventas.toMoney()
                    it[montoEfectivoEntrada] = request.monto_efectivo_entrada.toMoney()
                    it[montoEfectivoSalida] = request.monto_efectivo_salida.toMoney()
                    it[montoEfectivoTotal] = request.monto_efectivo_total.toMoney()
                    it[montoEfectivoCierre] = request.monto_efectivo_cierre.toMoney()
                    it[montoEfectivoDiferencia] = request.monto_efectivo_diferencia.toMoney()
                    it[montoOtrosTotal] = request.monto_otros_total.toMoney()
                    it[montoOtrosCierre] = request.monto_otros_cierre.toMoney()
                    it[montoOtrosDiferencia] = request.monto_otros_diferencia.toMoney()
                    it[montoTotal] = request.monto_total.toMoney()
                    it[montoCierre] = request.monto_cierre.toMoney()
                    it[montoDiferencia] = request.monto_diferencia.toMoney()
                    it[observacionCierre] = request.observacion_cierre.orEmpty()
                    it[numeroCierreFiscal] = request.numero_cierre_fiscal
                }

                CajaDetalleCierreTable.deleteWhere { CajaDetalleCierreTable.idSecuencia eq request.id }
                CajaDetalleCierreFormaPagoTable.deleteWhere { CajaDetalleCierreFormaPagoTable.idSecuencia eq request.id }

                request.detalle
                    .filter { it.cantidad > 0 }
                    .forEach { detalle ->
                        insertCajaDetalleCierre(request.id, serieSucursal, detalle)
                    }

                request.detalle_formapago.forEach { detalle ->
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

    suspend fun getCajaSequenceSummary(dbName: String, idCaja: String): CajaCierreSummary? {
        val secuencia = getCajaStatus(dbName, idCaja) ?: return null
        val database = DatabaseManager.connectToCompanyDb(dbName)

        return dbQuery(database) {
            val cajaName = CajaTable
                .select(CajaTable.descripcion)
                .where { CajaTable.idCaja eq idCaja }
                .limit(1)
                .firstOrNull()
                ?.get(CajaTable.descripcion)
                ?.takeIf { it.isNotBlank() }
                ?: "Caja"

            val vendedorName = CajaSecuenciaTable
                .leftJoin(VendedorTable, { idVendedor }, { VendedorTable.idVendedor })
                .select(VendedorTable.nombre)
                .where { CajaSecuenciaTable.idCajaSecuencia eq secuencia.idCajaSecuencia }
                .limit(1)
                .firstOrNull()
                ?.get(VendedorTable.nombre)
                ?.takeIf { it.isNotBlank() }

            val facturaRows = SalesFacturaTable
                .leftJoin(EstatusTable, { codEstatus }, { EstatusTable.codEstatus })
                .select(
                    SalesFacturaTable.idFactura,
                    SalesFacturaTable.totalTotalFactura,
                    EstatusTable.descripcion,
                )
                .where { SalesFacturaTable.idCajaSecuencia eq secuencia.idCajaSecuencia }
                .toList()

            var totalSales = 0.0
            var transactionCount = 0
            facturaRows.forEach { row ->
                val statusDesc = row[EstatusTable.descripcion]
                if (!isCancelledStatus(statusDesc)) {
                    totalSales += row[SalesFacturaTable.totalTotalFactura].toDouble()
                    transactionCount += 1
                }
            }

            val movimientos = SalesCajaNuevaTable
                .select(
                    SalesCajaNuevaTable.ingEg,
                    SalesCajaNuevaTable.monto,
                    SalesCajaNuevaTable.status,
                )
                .where { SalesCajaNuevaTable.idCajaSecuencia eq secuencia.idCajaSecuencia }
                .toList()

            var totalIncome = 0.0
            var totalExpense = 0.0
            var totalCancelled = 0.0
            movimientos.forEach { row ->
                val amount = row[SalesCajaNuevaTable.monto]?.toDouble() ?: 0.0
                when (row[SalesCajaNuevaTable.status]) {
                    CajaStatus.Anulada -> totalCancelled += amount
                    else -> when (row[SalesCajaNuevaTable.ingEg]) {
                        CajaIngresoEgreso.I -> totalIncome += amount
                        CajaIngresoEgreso.E -> totalExpense += amount
                        null -> Unit
                    }
                }
            }

            val formasCatalogo = CajaFormaPagoTable
                .select(
                    CajaFormaPagoTable.idFormaPago,
                    CajaFormaPagoTable.siglas,
                    CajaFormaPagoTable.descripcion,
                )
                .toList()
                .associate { row ->
                    row[CajaFormaPagoTable.idFormaPago] to (row[CajaFormaPagoTable.siglas] to row[CajaFormaPagoTable.descripcion])
                }

            val formaRows = SalesCajaNuevaDetalleFormaPagoTable
                .join(
                    SalesCajaNuevaTable,
                    JoinType.INNER,
                    onColumn = SalesCajaNuevaDetalleFormaPagoTable.cajaId,
                    otherColumn = SalesCajaNuevaTable.cajaId,
                )
                .select(
                    SalesCajaNuevaDetalleFormaPagoTable.idFormaPago,
                    SalesCajaNuevaDetalleFormaPagoTable.tipoMovimiento,
                    SalesCajaNuevaDetalleFormaPagoTable.monto,
                )
                .where {
                    (SalesCajaNuevaTable.idCajaSecuencia eq secuencia.idCajaSecuencia) and
                        (SalesCajaNuevaTable.status neq CajaStatus.Anulada)
                }
                .toList()

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

                val formKey = idFormaPago?.toString()
                    ?: listOf(siglas.orEmpty(), descripcion.orEmpty(), tipoMovimiento.orEmpty())
                        .joinToString("|")

                val current = formasMap[formKey]
                if (current == null) {
                    formasMap[formKey] = FormaAccum(
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
                formasPago = formasMap.values.map {
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

    suspend fun getCajas(dbName: String, userId: Int): List<Caja> {
        val database = DatabaseManager.connectToCompanyDb(dbName)
        return dbQuery(database) {
            val parametrosRow = ParametrosGeneralesTable
                .select(
                    ParametrosGeneralesTable.codAlmacen,
                    ParametrosGeneralesTable.multiMoneda,
                    ParametrosGeneralesTable.monedaBase,
                    ParametrosGeneralesTable.abrMonedaBase,
                    ParametrosGeneralesTable.monedaSecundaria,
                    ParametrosGeneralesTable.abrMonedaSecundaria,
                    ParametrosGeneralesTable.porcentajeImpuestoPrincipal,
                    ParametrosGeneralesTable.defaultIdFormaPagoFactura,
                )
                .orderBy(ParametrosGeneralesTable.codEmpresa)
                .limit(1)
                .firstOrNull()

            val globalDefaultWarehouse = parametrosRow
                ?.get(ParametrosGeneralesTable.codAlmacen)
                ?.let { kotlin.math.abs(it) }
                ?.takeIf { it > 0 }

            val defaultTaxRate = parametrosRow
                ?.get(ParametrosGeneralesTable.porcentajeImpuestoPrincipal)
                ?.toDouble()
                ?: 0.0

            val defaultFormaPagoId = parametrosRow
                ?.get(ParametrosGeneralesTable.defaultIdFormaPagoFactura)

            val multiMonedaFromParams = parametrosRow
                ?.get(ParametrosGeneralesTable.multiMoneda)
                ?.equals("Si", ignoreCase = true)
                ?: false

            val monedaBase = parametrosRow
                ?.get(ParametrosGeneralesTable.monedaBase)
                ?: 1

            val abrMonedaBase = parametrosRow
                ?.get(ParametrosGeneralesTable.abrMonedaBase)
                ?.takeIf { it.isNotBlank() }
                ?: "USD"

            val monedaSecundaria = parametrosRow
                ?.get(ParametrosGeneralesTable.monedaSecundaria)
                ?: monedaBase

            val abrMonedaSecundaria = parametrosRow
                ?.get(ParametrosGeneralesTable.abrMonedaSecundaria)
                ?.takeIf { it.isNotBlank() }
                ?: abrMonedaBase

            val tasaActual = if (multiMonedaFromParams) {
                TasasCambioTable
                    .select(TasasCambioTable.id, TasasCambioTable.tasaInversa)
                    .where {
                        (TasasCambioTable.divisa eq monedaSecundaria) and
                            (TasasCambioTable.monedabase eq monedaBase)
                    }
                    .orderBy(TasasCambioTable.id to SortOrder.DESC)
                    .limit(1)
                    .firstOrNull()
            } else {
                null
            }

            val currencyConfig = CurrencyConfig(
                multiMoneda = if (multiMonedaFromParams) "SI" else "NO",
                tasa = tasaActual?.get(TasasCambioTable.tasaInversa)?.toDouble() ?: 1.0,
                idTasa = tasaActual?.get(TasasCambioTable.id)?.toInt() ?: 0,
                monedaBase = monedaBase,
                abrMonedaBase = abrMonedaBase,
                monedaSecundaria = monedaSecundaria,
                abrMonedaSecundaria = abrMonedaSecundaria,
            )

            val defaultBySucursal = SucursalAlmacenTable
                .select(SucursalAlmacenTable.idSucursal, SucursalAlmacenTable.idAlmacen)
                .where { SucursalAlmacenTable.defaultVentas eq 1 }
                .orderBy(SucursalAlmacenTable.idSucursal)
                .groupBy { it[SucursalAlmacenTable.idSucursal] }
                .mapValues { (_, rows) ->
                    rows.firstNotNullOfOrNull { row ->
                        row[SucursalAlmacenTable.idAlmacen].takeIf { it > 0 }
                    }
                }

            val activeSellers = VendedorTable
                .select(
                    VendedorTable.idVendedor,
                    VendedorTable.nombre,
                    VendedorTable.codUsuarios,
                    VendedorTable.idCajas,
                    VendedorTable.idTiendas,
                )
                .where { VendedorTable.activo eq 1 }
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

            CajaTable
                .leftJoin(
                    otherTable = SucursalTable,
                    onColumn = { CajaTable.idSucursal },
                    otherColumn = { SucursalTable.idSucursal }
                )
                .selectAll()
                .map { row ->
                    val nombreSucursal = row[SucursalTable.sucursal]?.takeIf { it.isNotBlank() }
                        ?: row[SucursalTable.descripcion]?.takeIf { it.isNotBlank() }

                    val idCaja = row[CajaTable.idCaja]
                    val idSucursal = row[CajaTable.idSucursal]
                    val cajaWarehouse = row[CajaTable.codAlmacen]?.takeIf { it > 0 }
                    val resolvedDefaultWarehouse = cajaWarehouse
                        ?: idSucursal?.let { defaultBySucursal[it] }
                        ?: globalDefaultWarehouse

                    val userIdToken = userId.toString()
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
                        codAlmacen = row[CajaTable.codAlmacen],
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
                }
        }
    }

    private fun csvContains(csv: String?, token: String): Boolean {
        if (csv.isNullOrBlank()) return false
        return csv.split(',').any { it.trim() == token }
    }

    private fun resolveNextSecuenciaCode(idCaja: String): String {
        val max = CajaSecuenciaTable
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
            it[idMonedaDenominacion] = detalle.id_moneda_denominacion
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
            it[idFormaPago] = detalle.id_forma_pago
            it[montoVentas] = detalle.monto.toMoney()
            it[montoCierre] = detalle.monto_cierre.toMoney()
            it[montoDiferencia] = detalle.monto_diferencia.toMoney()
            it[CajaDetalleCierreFormaPagoTable.serieSucursal] = serieSucursal
        }
    }

    private fun Double.toMoney(): BigDecimal =
        BigDecimal.valueOf(this).setScale(2, java.math.RoundingMode.HALF_UP)

    private fun buildAutoCloseFormaPagoTotals(
        data: CajaSecuenciaData,
    ): Map<Int, FormaPagoCloseTotal> {
        val totals = linkedMapOf<Int, FormaPagoCloseTotal>()

        data.forma_pago
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

        data.forma_pago_devolucion
            .asSequence()
            .filter { it.id_forma_pago > 0 && it.monto != 0.0 }
            .forEach { line ->
                totals.merge(
                    line.id_forma_pago,
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
