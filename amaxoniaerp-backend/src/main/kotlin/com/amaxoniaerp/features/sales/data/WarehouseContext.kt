package com.amaxoniaerp.features.sales.data

import com.amaxoniaerp.features.companies.data.ParametrosGeneralesTableFactory
import com.amaxoniaerp.features.sales.domain.InvalidSaleRequestException
import com.amaxoniaerp.features.sales.domain.ProcessSaleRequest
import com.amaxoniaerp.features.sales.domain.SaleItemInput
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.select

internal data class WarehouseContext(
    val defaultWarehouseId: Int,
    val allowedWarehouseIds: Set<Int>,
    val idSucursal: Int?,
    val serieSucursal: String?,
)

internal fun resolveWarehouseContext(
    countryCode: String,
    cajaId: String,
): WarehouseContext {
    val isVE = countryCode.equals("VE", ignoreCase = true)
    val cajaTable = SalesCajaTableFactory.forCountry(countryCode)
    val columns =
        if (isVE && cajaTable is SalesCajaTableVE) {
            listOf(cajaTable.idSucursal, cajaTable.codAlmacen)
        } else {
            listOf(cajaTable.idSucursal)
        }
    val caja =
        cajaTable
            .select(columns)
            .where { cajaTable.id eq cajaId }
            .limit(1)
            .firstOrNull()
            ?: throw InvalidSaleRequestException("No se encontró caja para id_caja=$cajaId")

    val cajaWarehouseId =
        if (isVE && cajaTable is SalesCajaTableVE) {
            caja.getOrNull(cajaTable.codAlmacen)?.takeIf { it > 0 }
        } else {
            null
        }
    val cajaSucursalId = caja[cajaTable.idSucursal]
    val serieSucursal = cajaSucursalId?.let(::serieDeSucursal)
    val globalWarehouseId = globalWarehouse(countryCode)

    val defaultWarehouseId =
        cajaWarehouseId ?: sucursalDefaultWarehouse(cajaSucursalId) ?: globalWarehouseId
            ?: throw InvalidSaleRequestException(
                "No se pudo resolver almacén por defecto para caja=$cajaId (caja/sucursal/parámetros generales)",
            )

    return WarehouseContext(
        defaultWarehouseId = defaultWarehouseId,
        allowedWarehouseIds =
            allowedWarehouses(cajaWarehouseId, globalWarehouseId, cajaSucursalId, defaultWarehouseId),
        idSucursal = cajaSucursalId,
        serieSucursal = serieSucursal,
    )
}

private fun serieDeSucursal(sucursalId: Int): String? =
    SalesSucursalTable
        .select(SalesSucursalTable.serie)
        .where { SalesSucursalTable.id eq sucursalId }
        .limit(1)
        .firstOrNull()
        ?.get(SalesSucursalTable.serie)
        ?.takeIf { it.isNotBlank() }

private fun sucursalDefaultWarehouse(cajaSucursalId: Int?): Int? =
    cajaSucursalId?.let { sucursalId ->
        SalesSucursalAlmacenTable
            .select(SalesSucursalAlmacenTable.idAlmacen)
            .where {
                (SalesSucursalAlmacenTable.idSucursal eq sucursalId) and
                    (SalesSucursalAlmacenTable.defaultVentas eq 1)
            }.limit(1)
            .firstOrNull()
            ?.get(SalesSucursalAlmacenTable.idAlmacen)
            ?.takeIf { it > 0 }
    }

private fun globalWarehouse(countryCode: String): Int? {
    val pgTable = ParametrosGeneralesTableFactory.forCountry(countryCode)
    return pgTable
        .select(pgTable.codAlmacen)
        .orderBy(pgTable.codEmpresa)
        .limit(1)
        .firstOrNull()
        ?.get(pgTable.codAlmacen)
        ?.let { kotlin.math.abs(it) }
        ?.takeIf { it > 0 }
}

private fun allowedWarehouses(
    cajaWarehouseId: Int?,
    globalWarehouseId: Int?,
    cajaSucursalId: Int?,
    defaultWarehouseId: Int,
): Set<Int> {
    val allowed = mutableSetOf<Int>()
    if (cajaWarehouseId != null) {
        allowed += cajaWarehouseId
    }
    if (globalWarehouseId != null) {
        allowed += globalWarehouseId
    }
    if (cajaSucursalId != null) {
        allowed +=
            SalesSucursalAlmacenTable
                .select(SalesSucursalAlmacenTable.idAlmacen)
                .where { SalesSucursalAlmacenTable.idSucursal eq cajaSucursalId }
                .mapNotNull { row -> row[SalesSucursalAlmacenTable.idAlmacen].takeIf { it > 0 } }
    }
    allowed += defaultWarehouseId
    return allowed
}

internal fun validateWarehouseOwnership(
    request: ProcessSaleRequest,
    normalizedItems: List<SaleItemInput>,
    context: WarehouseContext,
) {
    val invalidWarehouses =
        normalizedItems
            .map { it.itemAlmacen }
            .filter { it !in context.allowedWarehouseIds }
            .distinct()

    if (invalidWarehouses.isNotEmpty()) {
        throw InvalidSaleRequestException(
            "Almacen(es) no permitidos para caja=${request.factura.idCaja}: ${invalidWarehouses.joinToString(
                ",",
            )}. Permitidos: ${context.allowedWarehouseIds.sorted().joinToString(",")}",
        )
    }
}
