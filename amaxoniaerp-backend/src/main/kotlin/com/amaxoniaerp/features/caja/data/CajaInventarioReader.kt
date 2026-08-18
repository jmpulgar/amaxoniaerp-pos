package com.amaxoniaerp.features.caja.data

import com.amaxoniaerp.features.caja.domain.CajaInventarioItem
import com.amaxoniaerp.features.sales.data.SalesFacturaDetalleTable
import com.amaxoniaerp.features.sales.data.SalesFacturaTableFactory
import com.amaxoniaerp.features.sales.data.SalesStockTable
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.select

internal fun loadVentasDeSecuencia(
    countryCode: String,
    idSecuencia: String,
): Pair<Double, Int> {
    val facturaTable = SalesFacturaTableFactory.forCountry(countryCode)
    val facturasValidas =
        facturaTable
            .select(facturaTable.idFactura, facturaTable.totalTotalFactura, facturaTable.codEstatus)
            .where { facturaTable.idCajaSecuencia eq idSecuencia }
            .filter { row -> (row[facturaTable.codEstatus] ?: 0) != ANNULLED_INVOICE_STATUS }
    return facturasValidas.sumOf { it[facturaTable.totalTotalFactura].toDouble() } to facturasValidas.size
}

internal fun loadTotalAnulado(
    countryCode: String,
    idSecuencia: String,
): Double {
    val facturaTable = SalesFacturaTableFactory.forCountry(countryCode)
    return facturaTable
        .select(facturaTable.totalTotalFactura)
        .where {
            (facturaTable.idCajaSecuencia eq idSecuencia) and
                (facturaTable.codEstatus eq ANNULLED_INVOICE_STATUS)
        }.sumOf { it[facturaTable.totalTotalFactura].toDouble() }
}

internal fun countFacturasTemporales(
    countryCode: String,
    idSecuencia: String,
): Int {
    val facturaTable = SalesFacturaTableFactory.forCountry(countryCode)
    return facturaTable
        .select(facturaTable.formaPago)
        .where {
            (facturaTable.idCajaSecuencia eq idSecuencia) and
                (facturaTable.codEstatus eq 1)
        }.count { row -> !row[facturaTable.formaPago].equals("credito", ignoreCase = true) }
}

internal fun loadInventarioVentas(
    countryCode: String,
    idSecuencia: String,
): List<CajaInventarioItem> {
    val facturaTable = SalesFacturaTableFactory.forCountry(countryCode)
    val facturasValidas =
        facturaTable
            .select(facturaTable.idFactura)
            .where { facturaTable.idCajaSecuencia eq idSecuencia }
            .filter { row -> (row[facturaTable.codEstatus] ?: 0) != ANNULLED_INVOICE_STATUS }
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
    return detalleVentas
        .groupBy { it[SalesFacturaDetalleTable.idItem] }
        .map { (itemId, rows) ->
            val first = rows.first()
            val sold = rows.sumOf { it[SalesFacturaDetalleTable.itemCantidadTotal].toDouble() }
            val available = stockDisponible[itemId] ?: 0.0
            CajaInventarioItem(
                codigo = first[SalesFacturaDetalleTable.itemCodigo].ifBlank { itemId.toString() },
                descripcion =
                    first[SalesFacturaDetalleTable.itemDescripcion].ifBlank { "Producto $itemId" },
                existenciaInicial = available + sold,
                cantidadVendida = sold,
                existenciaDisponible = available,
            )
        }.sortedBy { it.descripcion }
}
