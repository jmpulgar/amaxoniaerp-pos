package com.amaxoniaerp.features.items.data

import com.amaxoniaerp.features.items.domain.ItemStockByWarehouse
import com.amaxoniaerp.features.items.domain.PriceLevel
import com.amaxoniaerp.features.items.domain.Product
import com.amaxoniaerp.features.sync.domain.PriceLevelSyncDto
import com.amaxoniaerp.features.sync.domain.ProductSyncDto
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.transactions.TransactionManager
import java.math.BigDecimal

private const val PERCENT_BASE = 100.0

/**
 * DTO slim de sincronización (mismo resultado que mapRowToProduct + estatus).
 * Reutiliza la derivación exacta de precios/impuestos para que el POS y el
 * ERP calculen idéntico el hash canónico de contenido.
 */
internal fun mapRowToProductSync(
    row: ResultRow,
    countryCode: String,
): ProductSyncDto {
    val product = mapRowToProduct(row, countryCode)
    val table = ItemsTableFactory.getTableForCountry(countryCode)
    return ProductSyncDto(
        id = product.id,
        code = product.code,
        description = product.description,
        reference = product.reference,
        barcode1 = product.barcode1,
        barcode2 = product.barcode2,
        barcode3 = product.barcode3,
        department = product.department.toIntOrNull() ?: 0,
        isExempt = product.isExempt,
        taxRate = product.taxRate,
        costActual = product.costActual,
        unitPackage = product.unitPackage,
        bulkQuantity = product.bulkQuantity,
        portionUnit = product.portionUnit,
        unitOrPackage = product.unitOrPackage,
        estatus = row.getOrNull(table.estatus),
        prices =
            product.prices.map { level ->
                PriceLevelSyncDto(
                    label = level.label,
                    price = level.price,
                    utilityPercent = level.utilityPercent,
                    pricePlusUtility = level.pricePlusUtility,
                    pricePlusTax = level.pricePlusTax,
                    unitPrice = level.unitPrice,
                    unitPricePlusTax = level.unitPricePlusTax,
                    discountPercent = level.discountPercent,
                )
            },
    )
}

internal fun mapRowToProduct(
    row: ResultRow,
    countryCode: String,
): Product {
    val table = ItemsTableFactory.getTableForCountry(countryCode)
    val storedTaxRate = row[table.iva].toDouble()
    val isExempt = isExemptRow(row, table, storedTaxRate)
    val resolvedLineId = row[table.lineaId].takeIf { it > 0 } ?: row[table.codLinea]
    val resolvedBrandId = row[table.marcaId].takeIf { it > 0 } ?: resolveBrandIdByLineId(resolvedLineId)

    return Product(
        id = row[table.idItem].toString(),
        code = row[table.codItem],
        reference = row[table.referencia] ?: "",
        description = row[table.descripcion1],
        barcode1 = row[table.codigoBarras],
        barcode2 = row[table.codigoBarras2],
        barcode3 = row[table.codigoBarras3],
        photoUrl = row[table.foto] ?: "",
        department = (row[table.departamentoId].takeIf { it > 0 } ?: row[table.codDepartamento]).toString(),
        section = row[table.seccionId].toString(),
        family = row[table.familiaId].toString(),
        subFamily = row[table.subfamiliaId].toString(),
        brand = resolvedBrandId.toString(),
        line = resolvedLineId.toString(),
        isExempt = isExempt,
        taxRate = storedTaxRate,
        costActual = row[table.costoActual].toDouble(),
        costAverage = row[table.costoPromedio].toDouble(),
        costPrevious = row[table.costoAnterior].toDouble(),
        unitPackage = row.getOrNull(table.unidadEmpaque).orEmpty(),
        bulkQuantity = row.getOrNull(table.cantidadBulto)?.toDouble()?.takeIf { it > 0.0 } ?: 1.0,
        portionUnit = row.getOrNull(table.unidadPorcion),
        unitOrPackage = row.getOrNull(table.unidadOEmpaque).orEmpty().ifBlank { "UNIDAD" },
        isService = (row.getOrNull(table.tipoProd) == 1) || (row.getOrNull(table.codItemForma) == 2),
        prices = createPriceLevels(row, table),
        gobSegment = "",
        gobFamily = "",
    ).let { product ->
        when (table) {
            is ItemsTableVE -> product
            is ItemsTablePA ->
                product.copy(
                    gobSegment = row.getOrNull(table.idSegmentoGob)?.toString() ?: "",
                    gobFamily = row.getOrNull(table.idFamiliaGob)?.toString() ?: "",
                )
            else -> product
        }
    }
}

private fun createPriceLevels(
    row: ResultRow,
    table: BaseItemsTable,
): List<PriceLevel> {
    val storedTaxRate = row[table.iva].toDouble()
    val isExempt = isExemptRow(row, table, storedTaxRate)

    fun level(
        label: String,
        price: Double,
        utilityPercent: Double,
        priceWithTax: Double,
        unitPrice: Double,
        discountPercent: Double,
    ) = PriceLevel(
        label = label,
        price = price,
        utilityPercent = utilityPercent,
        pricePlusUtility = price,
        pricePlusTax = pricePlusTaxOrExempt(isExempt, price, priceWithTax),
        unitPrice = unitPrice,
        unitPricePlusTax = unitPriceWithTax(unitPrice, storedTaxRate, isExempt),
        discountPercent = discountPercent,
    )

    val unitPrice1 = row.getOrNull(table.precio1Extra)?.toDouble() ?: 0.0
    val unitPrice2 = row.getOrNull(table.precio2Extra)?.toDouble() ?: 0.0
    val unitPrice3 = row.getOrNull(table.precio3Extra)?.toDouble() ?: 0.0
    val unitPrice4 = row.getOrNull(table.precio4Extra)?.toDouble() ?: 0.0
    val unitPrice5 = row.getOrNull(table.precio5Extra)?.toDouble() ?: 0.0
    return listOf(
        level(
            "A",
            row[table.precio1].toDouble(),
            row[table.utilidad1].toDouble(),
            row[table.coniva1].toDouble(),
            unitPrice1,
            row[table.descuento1].toDouble(),
        ),
        level(
            "B",
            row[table.precio2].toDouble(),
            row[table.utilidad2].toDouble(),
            row[table.coniva2].toDouble(),
            unitPrice2,
            row[table.descuento2].toDouble(),
        ),
        level(
            "C",
            row[table.precio3].toDouble(),
            row[table.utilidad3].toDouble(),
            row[table.coniva3].toDouble(),
            unitPrice3,
            row[table.descuento3].toDouble(),
        ),
        level(
            "D",
            row[table.precio4].toDouble(),
            row[table.utilidad4].toDouble(),
            row[table.coniva4].toDouble(),
            unitPrice4,
            row[table.descuento4].toDouble(),
        ),
        level(
            "E",
            row[table.precio5].toDouble(),
            row[table.utilidad5].toDouble(),
            row[table.coniva5].toDouble(),
            unitPrice5,
            row[table.descuento5].toDouble(),
        ),
    )
}

private fun isExemptRow(
    row: ResultRow,
    table: BaseItemsTable,
    storedTaxRate: Double,
): Boolean {
    val hasTaxInPrices =
        listOf(
            row[table.coniva1].toDouble() > row[table.precio1].toDouble(),
            row[table.coniva2].toDouble() > row[table.precio2].toDouble(),
            row[table.coniva3].toDouble() > row[table.precio3].toDouble(),
            row[table.coniva4].toDouble() > row[table.precio4].toDouble(),
            row[table.coniva5].toDouble() > row[table.precio5].toDouble(),
        ).any { it }
    return storedTaxRate <= 0.0 && !hasTaxInPrices
}

private fun pricePlusTaxOrExempt(
    isExempt: Boolean,
    price: Double,
    priceWithTax: Double,
): Double = if (isExempt) price else priceWithTax

private fun unitPriceWithTax(
    unitPrice: Double,
    storedTaxRate: Double,
    isExempt: Boolean,
): Double =
    if (unitPrice <= 0.0 || isExempt) {
        unitPrice
    } else {
        unitPrice * (1.0 + (storedTaxRate / PERCENT_BASE))
    }

private fun resolveBrandIdByLineId(lineId: Int): Int {
    if (lineId <= 0) return 0
    val sql = "SELECT marca FROM linea WHERE cod_linea = $lineId LIMIT 1"
    return TransactionManager.current().exec(sql) { rs ->
        if (rs.next()) rs.getInt("marca") else 0
    } ?: 0
}

internal fun stockByWarehouseSql(itemId: Int): String =
    """
    SELECT
        A.cod_almacen AS almacenId,
        COALESCE(A.descripcion, '') AS almacenNombre,
        COALESCE(A.tipo, '') AS almacenTipo,
        COALESCE(E.cantidad, 0) AS cantidad,
        COALESCE(E.cantidad_muestra, 0) AS cantidadMuestra,
        COALESCE(SUM(P.cantidad), 0) AS cantidadPrecomprometida,
        (COALESCE(E.cantidad, 0) - COALESCE(SUM(P.cantidad), 0)) AS cantidadDisponible,
        COALESCE(E.minimo, 0) AS stockMinimo,
        COALESCE(E.maximo, 0) AS stockMaximo,
        COALESCE(A.orden, 999999) AS ordenAlmacen
    FROM almacen A
    LEFT JOIN item_existencia_almacen E
        ON E.cod_almacen = A.cod_almacen
        AND E.id_item = $itemId
    LEFT JOIN item_precompromiso P
        ON P.id_almacen = A.cod_almacen
        AND P.id_item = $itemId
    GROUP BY
        A.cod_almacen,
        A.descripcion,
        A.tipo,
        A.orden,
        E.cantidad,
        E.cantidad_muestra,
        E.minimo,
        E.maximo
    ORDER BY ordenAlmacen ASC, A.cod_almacen ASC
    """.trimIndent()

internal fun mapStockRow(result: java.sql.ResultSet): ItemStockByWarehouse =
    ItemStockByWarehouse(
        almacenId = result.getInt("almacenId"),
        almacenNombre = result.getString("almacenNombre"),
        almacenTipo = result.getString("almacenTipo"),
        cantidad = result.getBigDecimal("cantidad").toSafeDouble(),
        cantidadMuestra = result.getBigDecimal("cantidadMuestra").toSafeDouble(),
        cantidadPrecomprometida = result.getBigDecimal("cantidadPrecomprometida").toSafeDouble(),
        cantidadDisponible = result.getBigDecimal("cantidadDisponible").toSafeDouble(),
        stockMinimo = result.getBigDecimal("stockMinimo").toSafeDouble(),
        stockMaximo = result.getBigDecimal("stockMaximo").toSafeDouble(),
    )

internal fun isSaleWarehouse(tipo: String?): Boolean =
    run {
        val normalized = tipo?.trim()?.uppercase().orEmpty()
        if (normalized.isBlank()) return true
        if (normalized.contains("MERMA")) return false
        if (normalized.contains("DESPERDICIO")) return false
        if (normalized.contains("NO_VENTA")) return false
        return normalized !in setOf("M", "MERMA", "WASTE")
    }

internal fun BigDecimal?.toSafeDouble(): Double = this?.toDouble() ?: 0.0
