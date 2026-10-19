package com.amaxoniaerp.features.items.data

import com.amaxoniaerp.core.database.dbQuery
import com.amaxoniaerp.features.items.domain.CreateProductRequest
import com.amaxoniaerp.features.items.domain.ItemLotInfo
import com.amaxoniaerp.features.items.domain.ItemLotsResponse
import com.amaxoniaerp.features.items.domain.ItemStockByWarehouse
import com.amaxoniaerp.features.items.domain.ItemStockResponse
import com.amaxoniaerp.features.items.domain.Product
import com.amaxoniaerp.features.items.domain.ProductOptionGroupResponse
import com.amaxoniaerp.features.items.domain.ProductOptionItemResponse
import com.amaxoniaerp.features.items.domain.ProductOptionsResponse
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greater
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inSubQuery
import org.jetbrains.exposed.sql.SqlExpressionBuilder.like
import org.jetbrains.exposed.sql.SqlExpressionBuilder.neq
import org.jetbrains.exposed.sql.and
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
                        (table.codigoBarras2 like "%${query.search}%") or
                        (table.codigoBarras3 like "%${query.search}%") or
                        (table.referencia like "%${query.search}%")
                }
            }
            if (!query.departmentIds.isNullOrEmpty()) {
                select.andWhere {
                    (table.departamentoId inList query.departmentIds) or (table.codDepartamento inList query.departmentIds)
                }
            }
            if (query.departmentId != null && query.departmentId > 0) {
                select.andWhere {
                    (table.departamentoId eq query.departmentId) or (table.codDepartamento eq query.departmentId)
                }
            }
            when (query.itemType?.uppercase()) {
                "SERVICE", "SERVICIO" -> {
                    select.andWhere {
                        (table.tipoProd eq 1) or (table.codItemForma eq 2)
                    }
                }
                "PRODUCT", "PRODUCTO" -> {
                    select.andWhere {
                        (table.tipoProd.isNull() or (table.tipoProd neq 1)) and
                            (table.codItemForma.isNull() or (table.codItemForma neq 2))
                    }
                }
            }

            if (!query.warehouseIds.isNullOrEmpty()) {
                val stockSubQuery =
                    ItemExistenciaAlmacenTable
                        .select(ItemExistenciaAlmacenTable.idItem)
                        .where {
                            (ItemExistenciaAlmacenTable.codAlmacen inList query.warehouseIds) and
                                (ItemExistenciaAlmacenTable.cantidad.isNotNull()) and
                                (ItemExistenciaAlmacenTable.cantidad greater java.math.BigDecimal.ZERO)
                        }
                select.andWhere {
                    (table.tipoProd eq 1) or
                        (table.codItemForma eq 2) or
                        (table.idItem inSubQuery stockSubQuery)
                }
            }

            val total = if (query.includeTotal) select.count() else -1L
            val data =
                select
                    .orderBy(table.descripcion1)
                    .limit(query.limit)
                    .offset(query.offset)
                    .map { row -> mapRowToProduct(row, countryCode) }

            enrichWithCombos(data) to total
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
                val items =
                    table
                        .selectAll()
                        .andWhere { table.idItem inList ids }
                        .map { row -> mapRowToProduct(row, countryCode) }
                enrichWithCombos(items)
            }
        }

    suspend fun getItemById(
        database: Database,
        countryCode: String,
        id: Int,
    ): Product? =
        dbQuery(database) {
            val table = ItemsTableFactory.getTableForCountry(countryCode)

            val items =
                table
                    .selectAll()
                    .andWhere { table.idItem eq id }
                    .map { row -> mapRowToProduct(row, countryCode) }
            enrichWithCombos(items).singleOrNull()
        }

    private fun enrichWithCombos(products: List<Product>): List<Product> {
        if (products.isEmpty()) return products
        val itemIds = products.mapNotNull { it.id.toIntOrNull() }
        val combosByItemId =
            if (itemIds.isNotEmpty()) {
                runCatching {
                    val idsClause = itemIds.joinToString(",")
                    TransactionManager.current().exec(
                        "SELECT id_item, id_combo FROM item_combos WHERE id_item IN ($idsClause)",
                    ) { rs ->
                        val map = mutableMapOf<Int, Int>()
                        while (rs.next()) {
                            map[rs.getInt("id_item")] = rs.getInt("id_combo")
                        }
                        map
                    } ?: emptyMap()
                }.getOrElse { emptyMap() }
            } else {
                emptyMap()
            }

        return products.map { prod ->
            val intId = prod.id.toIntOrNull()
            val comboId = intId?.let { combosByItemId[it] }
            if (comboId != null) {
                prod.copy(
                    poseeProductoCombo = "si",
                    idCombo = comboId.toString(),
                )
            } else if (prod.tipoArticulo.equals("C", ignoreCase = true)) {
                prod.copy(
                    poseeProductoCombo = "si",
                )
            } else {
                prod
            }
        }
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

    suspend fun resolveItemIdByCode(
        database: Database,
        code: String,
    ): Int? =
        dbQuery(database) {
            runCatching {
                val escaped = code.replace("'", "''")
                TransactionManager.current().exec(
                    "SELECT id_item FROM item WHERE cod_item = '$escaped' LIMIT 1",
                ) { rs ->
                    if (rs.next()) rs.getInt("id_item") else null
                }
            }.getOrNull()
        }

    suspend fun getItemOptions(
        database: Database,
        itemId: Int,
        almacenId: Int? = null,
    ): ProductOptionsResponse =
        dbQuery(database) {
            val idCombo = resolveComboId(itemId)
            if (idCombo == null) {
                return@dbQuery ProductOptionsResponse(
                    success = true,
                    idCombo = null,
                    grupos = emptyList(),
                )
            }

            val groupCols = getTableColumns("grupos")
            val itemCols = getTableColumns("grupo_items")
            if (groupCols.isEmpty() || itemCols.isEmpty()) {
                return@dbQuery ProductOptionsResponse(
                    success = true,
                    idCombo = idCombo.toString(),
                    grupos = emptyList(),
                )
            }

            val grupos = queryComboGroups(idCombo, groupCols)
            val adicionaByGroup = grupos.associate { (it["id_grupo"] as String) to (it["_adiciona"] as? Int ?: 0) }
            val stockByItem = queryStockByItem(almacenId)
            val itemsByGrupo = queryGroupOptions(idCombo, itemCols, adicionaByGroup, stockByItem, almacenId)

            val gruposConOpciones =
                grupos
                    .map { g ->
                        val idGrp = g["id_grupo"] as String
                        val optionsList = itemsByGrupo[idGrp] ?: emptyList()
                        val nombreVal = g["nombre"] as? String ?: ""
                        ProductOptionGroupResponse(
                            idGrupo = idGrp,
                            tipo = g["tipo"] as? String ?: "COMBO",
                            nombre = nombreVal,
                            nombreGrupo = nombreVal,
                            minimo = g["minimo"] as? Int ?: 0,
                            maximo = g["maximo"] as? Int ?: 1,
                            maximoVeces = g["maximo"] as? Int ?: 1,
                            adiciona = g["_adiciona"] as? Int ?: 0,
                            opciones = optionsList,
                            items = optionsList,
                        )
                    }.filter { it.opciones.isNotEmpty() }

            ProductOptionsResponse(
                success = true,
                idCombo = idCombo.toString(),
                grupos = gruposConOpciones,
            )
        }

    private fun resolveComboId(itemId: Int): Int? =
        runCatching {
            TransactionManager.current().exec(
                "SELECT id_combo FROM item_combos WHERE id_item = $itemId ORDER BY id_item_combo LIMIT 1",
            ) { rs ->
                if (rs.next()) rs.getInt("id_combo") else null
            }
        }.getOrNull() ?: runCatching {
            val idPadre =
                TransactionManager.current().exec(
                    "SELECT id_item_padre FROM item WHERE id_item = $itemId",
                ) { rs ->
                    if (rs.next()) rs.getInt("id_item_padre").takeIf { !rs.wasNull() && it > 0 } else null
                }
            if (idPadre != null) {
                TransactionManager.current().exec(
                    "SELECT id_combo FROM item_combos WHERE id_item = $idPadre ORDER BY id_item_combo LIMIT 1",
                ) { rs ->
                    if (rs.next()) rs.getInt("id_combo") else null
                }
            } else {
                null
            }
        }.getOrNull()

    private fun getTableColumns(tableName: String): Set<String> =
        runCatching {
            TransactionManager.current().exec("SELECT * FROM $tableName WHERE 1 = 0") { rs ->
                val md = rs.metaData
                (1..md.columnCount).map { md.getColumnLabel(it).lowercase() }.toSet()
            }
        }.getOrNull() ?: emptySet()

    private fun queryComboGroups(
        idCombo: Int,
        groupCols: Set<String>,
    ): List<MutableMap<String, Any?>> {
        val hasTipo = "tipo" in groupCols
        val hasMinimo = "minimo" in groupCols
        val hasOrden = "orden" in groupCols
        val hasAdiciona = "adiciona" in groupCols
        val hasMaximo = "maximo_veces" in groupCols

        val grupoFields =
            buildList {
                add("id_grupo")
                add("nombre_grupo")
                if (hasMaximo) add("maximo_veces")
                if (hasAdiciona) add("adiciona")
                if (hasTipo) add("tipo")
                if (hasMinimo) add("minimo")
                if (hasOrden) add("orden")
            }
        val orderBy = if (hasOrden) "ORDER BY orden, id_grupo" else "ORDER BY id_grupo"
        val sqlGrupos = "SELECT ${grupoFields.joinToString(", ")} FROM grupos WHERE id_combo = $idCombo $orderBy"

        return runCatching {
            TransactionManager.current().exec(sqlGrupos) { rs ->
                val list = mutableListOf<MutableMap<String, Any?>>()
                while (rs.next()) {
                    val tipoVal =
                        if (hasTipo) {
                            rs
                                .getString("tipo")
                                ?.trim()
                                ?.uppercase()
                                ?.takeIf { it.isNotEmpty() } ?: "COMBO"
                        } else {
                            "COMBO"
                        }
                    val maxVal = if (hasMaximo) rs.getInt("maximo_veces").coerceAtLeast(1) else 1
                    val minVal = if (hasMinimo) rs.getInt("minimo") else (if (tipoVal == "COMBO") 1 else 0)
                    val adicionaVal = if (hasAdiciona) rs.getInt("adiciona") else 0

                    list.add(
                        mutableMapOf(
                            "id_grupo" to rs.getInt("id_grupo").toString(),
                            "tipo" to tipoVal,
                            "nombre" to (rs.getString("nombre_grupo") ?: "").trim(),
                            "minimo" to minVal,
                            "maximo" to maxVal,
                            "_adiciona" to adicionaVal,
                        ),
                    )
                }
                list
            }
        }.getOrNull() ?: emptyList()
    }

    private fun queryStockByItem(almacenId: Int?): Map<Int, Double> {
        if (almacenId == null || almacenId <= 0) return emptyMap()
        return runCatching {
            TransactionManager.current().exec(
                "SELECT id_item, SUM(cantidad) AS stock FROM item_existencia_almacen " +
                    "WHERE cod_almacen = $almacenId GROUP BY id_item",
            ) { rs ->
                val map = mutableMapOf<Int, Double>()
                while (rs.next()) {
                    map[rs.getInt("id_item")] = rs.getDouble("stock")
                }
                map
            } ?: emptyMap()
        }.getOrElse { emptyMap() }
    }

    private fun queryGroupOptions(
        idCombo: Int,
        itemCols: Set<String>,
        adicionaByGroup: Map<String, Int>,
        stockByItem: Map<Int, Double>,
        almacenId: Int?,
    ): Map<String, List<ProductOptionItemResponse>> {
        val hasNombre = "nombre" in itemCols
        val hasPrecio = "precio" in itemCols
        val hasCantidad = "cantidad" in itemCols
        val hasPorDefecto = "por_defecto" in itemCols
        val hasItemOrden = "orden" in itemCols
        val hasIdItem = "id_item" in itemCols

        val itemFields =
            buildList {
                add("gi.id_grupo_item")
                add("gi.id_grupo")
                if (hasIdItem) add("gi.id_item")
                if (hasNombre) add("gi.nombre")
                if (hasPrecio) add("gi.precio")
                if (hasCantidad) add("gi.cantidad")
                if (hasPorDefecto) add("gi.por_defecto")
                if (hasItemOrden) add("gi.orden")
                add("i.descripcion1")
                add("i.cod_item")
                add("i.coniva1")
                add("i.precio1")
                add("i.estatus")
                add("i.foto")
                add("i.foto1")
            }
        val itemOrderBy = if (hasItemOrden) "ORDER BY gi.orden, gi.id_grupo_item" else "ORDER BY gi.id_grupo_item"
        val sqlItems =
            """
            SELECT ${itemFields.joinToString(", ")}
            FROM grupo_items gi
            JOIN grupos g ON g.id_grupo = gi.id_grupo
            LEFT JOIN item i ON i.id_item = gi.id_item
            WHERE g.id_combo = $idCombo $itemOrderBy
            """.trimIndent()

        return runCatching {
            TransactionManager.current().exec(sqlItems) { rs ->
                val map = mutableMapOf<String, MutableList<ProductOptionItemResponse>>()
                while (rs.next()) {
                    val entry = mapOptionRow(rs, itemCols, adicionaByGroup, stockByItem, almacenId)
                    if (entry != null) {
                        map.getOrPut(entry.first) { mutableListOf() }.add(entry.second)
                    }
                }
                map
            }
        }.getOrNull() ?: emptyMap()
    }

    private fun mapOptionRow(
        rs: java.sql.ResultSet,
        itemCols: Set<String>,
        adicionaByGroup: Map<String, Int>,
        stockByItem: Map<Int, Double>,
        almacenId: Int?,
    ): Pair<String, ProductOptionItemResponse>? {
        val idGrp = rs.getInt("id_grupo").toString()
        val optIdItem = if ("id_item" in itemCols) rs.getInt("id_item").takeIf { !rs.wasNull() && it > 0 } else null

        val name = if ("nombre" in itemCols) rs.getString("nombre")?.trim()?.takeIf { it.isNotEmpty() } else null
        val resolvedName = name ?: rs.getString("descripcion1").orEmpty().trim()
        if (resolvedName.isEmpty() && optIdItem == null) return null

        val rawPrice = if ("precio" in itemCols) rs.getObject("precio") else null
        val optAdiciona = adicionaByGroup[idGrp] ?: 0
        val resolvedPrice = resolveOptionPrice(rawPrice, optAdiciona, rs)

        val qty =
            if ("cantidad" in itemCols) {
                val q = rs.getObject("cantidad")
                if (q != null) (q as? Number)?.toDouble() ?: 1.0 else 1.0
            } else {
                1.0
            }

        val defaultVal = if ("por_defecto" in itemCols) rs.getInt("por_defecto") else 0
        val agotado =
            if (almacenId != null && almacenId > 0 && optIdItem != null) {
                val stock = stockByItem[optIdItem] ?: 0.0
                if (stock <= 0.0) 1 else 0
            } else {
                0
            }

        val fotoPath =
            rs.getString("foto")?.takeIf { it.isNotBlank() }
                ?: rs.getString("foto1")?.takeIf { it.isNotBlank() }

        val opt =
            ProductOptionItemResponse(
                idGrupoItem = rs.getInt("id_grupo_item").toString(),
                idItem = optIdItem?.toString(),
                codItem = rs.getString("cod_item").orEmpty(),
                nombre = resolvedName,
                nombreOpcion = resolvedName,
                descripcion1 = rs.getString("descripcion1"),
                precio = resolvedPrice,
                precioExtra = resolvedPrice,
                coniva1 = rs.getDouble("coniva1"),
                cantidad = qty,
                porDefecto = defaultVal,
                agotado = agotado,
                foto = fotoPath,
                foto1 = rs.getString("foto1"),
            )
        return idGrp to opt
    }

    private fun resolveOptionPrice(
        rawPrice: Any?,
        optAdiciona: Int,
        rs: java.sql.ResultSet,
    ): Double =
        if (rawPrice != null) {
            (rawPrice as? Number)?.toDouble() ?: rs.getDouble("precio")
        } else if (optAdiciona == 1) {
            val coniva = rs.getDouble("coniva1")
            if (coniva > 0.0) coniva else rs.getDouble("precio1")
        } else {
            0.0
        }
}

/** Filtros de listado de productos (paginacion + busqueda + departamento + almacenes). */
data class ItemsListQuery(
    val limit: Int,
    val offset: Long,
    val search: String?,
    val includeTotal: Boolean,
    val departmentId: Int? = null,
    val departmentIds: List<Int>? = null,
    val itemType: String? = null,
    val warehouseIds: List<Int>? = null,
)
