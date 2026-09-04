package com.amaxoniaerp.features.caja.data

import com.amaxoniaerp.features.caja.domain.Caja
import com.amaxoniaerp.features.caja.domain.CurrencyConfig
import com.amaxoniaerp.features.caja.domain.SellerSummary
import com.amaxoniaerp.features.companies.data.ParametrosGeneralesTableFactory
import com.amaxoniaerp.features.companies.data.ParametrosGeneralesTableVE
import com.amaxoniaerp.features.companies.data.TasasCambioTableFactory
import com.amaxoniaerp.features.companies.data.TasasCambioTableVE
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.select

internal data class SellerRecord(
    val id: Int,
    val nombre: String,
    val codUsuarios: String,
    val idCajas: String?,
    val idTiendas: String,
)

internal class CajaCatalogParams(
    val globalDefaultWarehouse: Int?,
    val defaultTaxRate: Double,
    val defaultFormaPagoId: Int?,
    val currency: CurrencyConfig,
)

internal fun loadCajaCatalogParams(countryCode: String): CajaCatalogParams {
    val parametrosTable = ParametrosGeneralesTableFactory.forCountry(countryCode)
    val parametrosRow = selectParametrosRow(parametrosTable)

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

    val currency = loadCurrencyConfig(countryCode, parametrosTable, parametrosRow)

    return CajaCatalogParams(globalDefaultWarehouse, defaultTaxRate, defaultFormaPagoId, currency)
}

private fun selectParametrosRow(parametrosTable: com.amaxoniaerp.features.companies.data.BaseParametrosGeneralesTable): ResultRow? {
    val parametrosTableVE = parametrosTable as? ParametrosGeneralesTableVE
    val slice =
        if (parametrosTableVE != null) {
            parametrosTableVE.select(
                parametrosTable.codAlmacen,
                parametrosTable.monedaBase,
                parametrosTable.abrMonedaBase,
                parametrosTable.porcentajeImpuestoPrincipal,
                parametrosTable.defaultIdFormaPagoFactura,
                parametrosTableVE.multiMoneda,
                parametrosTableVE.monedaSecundaria,
                parametrosTableVE.abrMonedaSecundaria,
            )
        } else {
            parametrosTable.select(
                parametrosTable.codAlmacen,
                parametrosTable.monedaBase,
                parametrosTable.abrMonedaBase,
                parametrosTable.porcentajeImpuestoPrincipal,
                parametrosTable.defaultIdFormaPagoFactura,
            )
        }
    return slice
        .orderBy(parametrosTable.codEmpresa)
        .limit(1)
        .firstOrNull()
}

private fun loadCurrencyConfig(
    countryCode: String,
    parametrosTable: com.amaxoniaerp.features.companies.data.BaseParametrosGeneralesTable,
    parametrosRow: ResultRow?,
): CurrencyConfig {
    val parametrosTableVE = parametrosTable as? ParametrosGeneralesTableVE
    val multiMonedaFromParams =
        parametrosTableVE != null &&
            (
                parametrosRow
                    ?.get(parametrosTableVE.multiMoneda)
                    ?.equals("Si", ignoreCase = true)
                    ?: false
            )

    val monedaBase = parametrosRow?.get(parametrosTable.monedaBase) ?: 1
    val abrMonedaBase =
        parametrosRow
            ?.get(parametrosTable.abrMonedaBase)
            ?.takeIf { it.isNotBlank() }
            ?: "USD"

    val monedaSecundaria =
        if (parametrosTableVE != null) {
            parametrosRow?.get(parametrosTableVE.monedaSecundaria) ?: monedaBase
        } else {
            monedaBase
        }

    val abrMonedaSecundaria = resolveAbrMonedaSecundaria(parametrosTableVE, parametrosRow, abrMonedaBase)

    val tasasTableVE = TasasCambioTableFactory.forCountry(countryCode) as? TasasCambioTableVE
    val tasaActual =
        if (multiMonedaFromParams && tasasTableVE != null) {
            tasasTableVE
                .select(tasasTableVE.id, tasasTableVE.tasaInversa)
                .where {
                    (tasasTableVE.divisa eq monedaSecundaria) and
                        (tasasTableVE.monedabase eq monedaBase)
                }.orderBy(tasasTableVE.id to SortOrder.DESC)
                .limit(1)
                .firstOrNull()
        } else {
            null
        }

    val resolvedTasa = tasaResolvedFromRow(tasaActual, tasasTableVE)
    val resolvedIdTasa = idTasaResolvedFromRow(tasaActual, tasasTableVE)

    return CurrencyConfig(
        multiMoneda = if (multiMonedaFromParams) "SI" else "NO",
        tasa = resolvedTasa,
        idTasa = resolvedIdTasa,
        monedaBase = monedaBase,
        abrMonedaBase = abrMonedaBase,
        monedaSecundaria = monedaSecundaria,
        abrMonedaSecundaria = abrMonedaSecundaria,
    )
}

private fun resolveAbrMonedaSecundaria(
    parametrosTableVE: ParametrosGeneralesTableVE?,
    parametrosRow: ResultRow?,
    abrMonedaBase: String,
): String =
    if (parametrosTableVE != null) {
        parametrosRow
            ?.get(parametrosTableVE.abrMonedaSecundaria)
            ?.takeIf { it.isNotBlank() }
            ?: abrMonedaBase
    } else {
        abrMonedaBase
    }

private fun tasaResolvedFromRow(
    tasaActual: ResultRow?,
    tasasTableVE: TasasCambioTableVE?,
): Double =
    if (tasaActual != null && tasasTableVE != null) {
        tasaActual[tasasTableVE.tasaInversa]?.toDouble() ?: 1.0
    } else {
        1.0
    }

private fun idTasaResolvedFromRow(
    tasaActual: ResultRow?,
    tasasTableVE: TasasCambioTableVE?,
): Int =
    if (tasaActual != null && tasasTableVE != null) {
        tasaActual[tasasTableVE.id]?.toInt() ?: 0
    } else {
        0
    }

internal fun loadDefaultWarehouseBySucursal(): Map<Int, Int?> =
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

internal fun loadActiveSellers(): List<SellerRecord> =
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

internal fun mapCajaRows(
    countryCode: String,
    userId: Int,
    params: CajaCatalogParams,
    defaultBySucursal: Map<Int, Int?>,
    activeSellers: List<SellerRecord>,
): List<Caja> {
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

    return CajaTable
        .join(SucursalTable, org.jetbrains.exposed.sql.JoinType.LEFT, CajaTable.idSucursal, SucursalTable.idSucursal)
        .select(cajaColumns + SucursalTable.columns)
        .map { row ->
            val nombreSucursal =
                row[SucursalTable.sucursal]?.takeIf { it.isNotBlank() }
                    ?: row[SucursalTable.descripcion]?.takeIf { it.isNotBlank() }

            val idCaja = row[CajaTable.idCaja]
            val idSucursal = row[CajaTable.idSucursal]
            val cajaWarehouse =
                if (isVE) {
                    row.getOrNull(CajaTable.codAlmacen)?.takeIf { it > 0 }
                } else {
                    null
                }
            val resolvedDefaultWarehouse =
                cajaWarehouse
                    ?: idSucursal?.let { defaultBySucursal[it] }
                    ?: params.globalDefaultWarehouse

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
                codAlmacen = if (isVE) row.getOrNull(CajaTable.codAlmacen) else null,
                defaultWarehouseId = resolvedDefaultWarehouse,
                defaultSellerId = defaultSeller?.id,
                defaultSellerName = defaultSeller?.nombre,
                availableSellers = availableSellers,
                serieSucursal = row[SucursalTable.serie],
                defaultTaxRate = params.defaultTaxRate,
                defaultFormaPagoId = params.defaultFormaPagoId,
                currency = params.currency,
                serieCaja = row[CajaTable.serieCaja],
                sucursalNombre = nombreSucursal,
                sucursalCodigo = row[SucursalTable.codigo],
                codigoSucursalEmisor = row[SucursalTable.codigoSucursalEmisor],
            )
        }.filter { caja -> assignedCajaIds.isEmpty() || caja.idCaja in assignedCajaIds }
}
