package com.amaxoniaerp.features.items.data

import com.amaxoniaerp.core.database.dbQuery
import com.amaxoniaerp.features.items.domain.CreateProductRequest
import com.amaxoniaerp.features.items.domain.ItemLotInfo
import com.amaxoniaerp.features.items.domain.ItemLotsResponse
import com.amaxoniaerp.features.items.domain.ItemStockByWarehouse
import com.amaxoniaerp.features.items.domain.ItemStockResponse
import com.amaxoniaerp.features.items.domain.Product
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.SqlExpressionBuilder.like
import org.jetbrains.exposed.sql.andWhere
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.TransactionManager

/**
 * Repositorio de items Multi-Tenant con Safe Parsing.
 *
 * Las consultas de taxonomia viven como extensiones en ItemsTaxonomyQueries.kt,
 * el mapeo de filas en ItemsRowMappers.kt y las escrituras por pais en
 * ItemsWriteQueries.kt.
 */
class ItemsRepository {
    suspend fun getItemStockByWarehouse(
        database: Database,
        itemId: Int,
    ): ItemStockResponse =
        dbQuery(database) {
            val warehouses =
                TransactionManager.current().exec(stockByWarehouseSql(itemId)) { result ->
                    val list = mutableListOf<ItemStockByWarehouse>()
                    while (result.next()) {
                        list.add(mapStockRow(result))
                    }
                    list
                } ?: emptyList()

            val stockTotalDisponible =
                warehouses
                    .filter { isSaleWarehouse(it.almacenTipo) }
                    .sumOf { it.cantidadDisponible }

            ItemStockResponse(
                itemId = itemId,
                stockTotalDisponible = stockTotalDisponible,
                almacenes = warehouses,
            )
        }

    suspend fun listItems(
        database: Database,
        countryCode: String,
        query: ItemsListQuery,
    ): Pair<List<Product>, Long> =
        dbQuery(database) {
            val table = ItemsTableFactory.getTableForCountry(countryCode)

            val select = table.selectAll()

            if (!query.search.isNullOrBlank()) {
                select.andWhere {
                    (table.codItem like "%${query.search}%") or
                        (table.descripcion1 like "%${query.search}%") or
                        (table.codigoBarras like "%${query.search}%") or
                        (table.referencia like "%${query.search}%")
                }
            }
            if (query.departmentId != null && query.departmentId > 0) {
                select.andWhere {
                    (table.departamentoId eq query.departmentId) or (table.codDepartamento eq query.departmentId)
                }
            }

            val total = if (query.includeTotal) select.count() else -1L
            val data =
                select
                    .orderBy(table.descripcion1)
                    .limit(query.limit)
                    .offset(query.offset)
                    .map { row -> mapRowToProduct(row, countryCode) }

            data to total
        }

    suspend fun getItemsByIds(
        database: Database,
        countryCode: String,
        ids: List<Int>,
    ): List<Product> =
        if (ids.isEmpty()) {
            emptyList()
        } else {
            dbQuery(database) {
                val table = ItemsTableFactory.getTableForCountry(countryCode)
                table
                    .selectAll()
                    .andWhere { table.idItem inList ids }
                    .map { row -> mapRowToProduct(row, countryCode) }
            }
        }

    suspend fun getItemById(
        database: Database,
        countryCode: String,
        id: Int,
    ): Product? =
        dbQuery(database) {
            val table = ItemsTableFactory.getTableForCountry(countryCode)

            table
                .selectAll()
                .andWhere { table.idItem eq id }
                .map { row -> mapRowToProduct(row, countryCode) }
                .singleOrNull()
        }

    suspend fun createItem(
        database: Database,
        countryCode: String,
        request: CreateProductRequest,
    ): Product =
        dbQuery(database) {
            val table = ItemsTableFactory.getTableForCountry(countryCode)

            val id =
                when (table) {
                    is ItemsTableVE -> insertItemVE(table, request)
                    is ItemsTablePA -> insertItemPA(table, request)
                    else -> throw IllegalArgumentException("Tabla no soportada")
                }

            table
                .selectAll()
                .andWhere { table.idItem eq id }
                .map { row -> mapRowToProduct(row, countryCode) }
                .single()
        }

    suspend fun updateItem(
        database: Database,
        countryCode: String,
        id: Int,
        request: CreateProductRequest,
    ): Product? =
        dbQuery(database) {
            val table = ItemsTableFactory.getTableForCountry(countryCode)

            val updated =
                when (table) {
                    is ItemsTableVE -> updateItemVE(table, id, request)
                    is ItemsTablePA -> updateItemPA(table, id, request)
                    else -> throw IllegalArgumentException("Tabla no soportada")
                }

            if (updated == 0) {
                null
            } else {
                table
                    .selectAll()
                    .andWhere { table.idItem eq id }
                    .map { row -> mapRowToProduct(row, countryCode) }
                    .singleOrNull()
            }
        }

    /**
     * Verifica si un item tiene configuracion de lote habilitada
     * y retorna los lotes disponibles ordenados por vencimiento ASC (FEFO).
     */
    suspend fun getItemLots(
        database: Database,
        itemId: Int,
    ): ItemLotsResponse =
        dbQuery(database) {
            // Verificar si el item tiene configuracion de lote habilitada
            val hasLotConfig =
                TransactionManager.current().exec(
                    "SELECT CASE WHEN COUNT(*) > 0 THEN 'si' ELSE 'no' END AS posee " +
                        "FROM configuracion_lote WHERE id_item = $itemId AND habilitado = 1",
                ) { rs ->
                    if (rs.next()) rs.getString("posee") == "si" else false
                } ?: false

            if (!hasLotConfig) {
                return@dbQuery ItemLotsResponse(
                    itemId = itemId,
                    poseeConfiguracionLote = false,
                    lotes = emptyList(),
                )
            }

            // Obtener lotes con disponibilidad > 0 ordenados por vencimiento ASC (FEFO)
            val lots =
                TransactionManager.current().exec(
                    "SELECT id_lote_item, codigo_lote_item, vencimiento, disponibilidad, cod_almacen AS id_almacen " +
                        "FROM item_lote WHERE id_item = $itemId AND disponibilidad > 0 " +
                        "ORDER BY vencimiento ASC",
                ) { rs ->
                    val list = mutableListOf<ItemLotInfo>()
                    while (rs.next()) {
                        list.add(
                            ItemLotInfo(
                                idLoteItem = rs.getInt("id_lote_item"),
                                codigoLoteItem = rs.getString("codigo_lote_item"),
                                vencimiento = rs.getString("vencimiento"),
                                disponibilidad = rs.getInt("disponibilidad"),
                                idAlmacen = rs.getInt("id_almacen"),
                            ),
                        )
                    }
                    list
                } ?: emptyList()

            ItemLotsResponse(
                itemId = itemId,
                poseeConfiguracionLote = true,
                lotes = lots,
            )
        }
}

/** Filtros de listado de productos (paginacion + busqueda + departamento). */
data class ItemsListQuery(
    val limit: Int,
    val offset: Long,
    val search: String?,
    val includeTotal: Boolean,
    val departmentId: Int? = null,
)
