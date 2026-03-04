package com.amaxoniaerp.features.caja.data

import com.amaxoniaerp.core.database.DatabaseManager
import com.amaxoniaerp.core.database.dbQuery
import com.amaxoniaerp.features.caja.domain.AperturaRequest
import com.amaxoniaerp.features.caja.domain.Caja
import com.amaxoniaerp.features.caja.domain.CajaSecuencia
import com.amaxoniaerp.features.caja.domain.CurrencyConfig
import com.amaxoniaerp.features.caja.domain.SellerSummary
import com.amaxoniaerp.features.companies.data.ParametrosGeneralesTable
import com.amaxoniaerp.features.companies.data.TasasCambioTable
import org.jetbrains.exposed.sql.*
import org.slf4j.LoggerFactory
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
            return Result.failure(Exception("Caja is already open."))
        }

        val now = LocalDateTime.now()
        val newId = UUID.randomUUID().toString()

        return dbQuery(database) {
            CajaSecuenciaTable.insert {
                it[idCajaSecuencia] = newId
                it[idCaja] = request.idCaja
                it[fechaApertura] = now
                it[montoEfectivoApertura] = request.montoApertura.toBigDecimal()
                it[usuario] = username
                it[serieSucursal] = request.serieSucursal
                it[secuencia] = "000001"
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

    private data class SellerRecord(
        val id: Int,
        val nombre: String,
        val codUsuarios: String,
        val idCajas: String?,
        val idTiendas: String,
    )
}
