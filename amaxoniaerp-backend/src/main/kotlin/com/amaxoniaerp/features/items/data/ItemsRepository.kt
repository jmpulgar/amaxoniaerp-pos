package com.amaxoniaerp.features.items.data

import com.amaxoniaerp.core.database.dbQuery
import com.amaxoniaerp.features.items.domain.CreateProductRequest
import com.amaxoniaerp.features.items.domain.ItemLotInfo
import com.amaxoniaerp.features.items.domain.ItemLotsResponse
import com.amaxoniaerp.features.items.domain.ItemStockByWarehouse
import com.amaxoniaerp.features.items.domain.ItemStockResponse
import com.amaxoniaerp.features.items.domain.Product
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.SqlExpressionBuilder.like
import org.jetbrains.exposed.sql.andWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.update
import java.math.BigDecimal

/**
 * Repositorio de items Multi-Tenant con Safe Parsing.
 */
class ItemsRepository {
    suspend fun getItemStockByWarehouse(
        database: Database,
        itemId: Int,
    ): ItemStockResponse =
        dbQuery(database) {
            val sql =
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

            val warehouses =
                TransactionManager.current().exec(sql) { result ->
                    val list = mutableListOf<ItemStockByWarehouse>()
                    while (result.next()) {
                        list.add(
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
                            ),
                        )
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
        limit: Int,
        offset: Long,
        search: String?,
        includeTotal: Boolean,
        departmentId: Int? = null,
    ): Pair<List<Product>, Long> =
        dbQuery(database) {
            val table = ItemsTableFactory.getTableForCountry(countryCode)

            val query = table.selectAll()

            if (!search.isNullOrBlank()) {
                query.andWhere {
                    (table.codItem like "%$search%") or
                        (table.descripcion1 like "%$search%") or
                        (table.codigoBarras like "%$search%") or
                        (table.referencia like "%$search%")
                }
            }
            if (departmentId != null && departmentId > 0) {
                query.andWhere { (table.departamentoId eq departmentId) or (table.codDepartamento eq departmentId) }
            }

            val total = if (includeTotal) query.count() else -1L
            val data =
                query
                    .orderBy(table.descripcion1)
                    .limit(limit)
                    .offset(offset)
                    .map { row -> mapRowToProduct(row, countryCode) }

            data to total
        }

    suspend fun listDepartments(database: Database): List<Pair<Int, String>> =
        dbQuery(database) {
            DepartamentoTable
                .selectAll()
                .orderBy(DepartamentoTable.descripcion)
                .map { row ->
                    val id = row[DepartamentoTable.id]
                    val code = row[DepartamentoTable.codigo]?.trim().orEmpty()
                    val desc = row[DepartamentoTable.descripcion]?.trim().orEmpty()
                    val name =
                        when {
                            code.isNotBlank() && desc.isNotBlank() -> "$code - $desc"
                            desc.isNotBlank() -> desc
                            code.isNotBlank() -> code
                            else -> "Departamento $id"
                        }
                    id to name
                }
        }

    suspend fun listSections(
        database: Database,
        departmentId: Int,
    ): List<Pair<Int, String>> =
        dbQuery(database) {
            listCatalogBySql(
                """
                SELECT
                    S.id AS id,
                    CASE
                        WHEN COALESCE(NULLIF(TRIM(S.codigo), ''), '') <> ''
                             AND COALESCE(NULLIF(TRIM(S.descripcion), ''), '') <> ''
                            THEN CONCAT(S.codigo, ' - ', S.descripcion)
                        WHEN COALESCE(NULLIF(TRIM(S.descripcion), ''), '') <> ''
                            THEN S.descripcion
                        WHEN COALESCE(NULLIF(TRIM(S.codigo), ''), '') <> ''
                            THEN S.codigo
                        ELSE CONCAT('Seccion ', S.id)
                    END AS name
                FROM seccion S
                INNER JOIN seccion_departamento SD ON SD.seccion_id = S.id
                WHERE SD.departamento_id = $departmentId
                ORDER BY name ASC
                """.trimIndent(),
            )
        }

    suspend fun listFamilies(
        database: Database,
        sectionId: Int,
    ): List<Pair<Int, String>> =
        dbQuery(database) {
            listCatalogBySql(
                """
                SELECT DISTINCT
                    F.id AS id,
                    CASE
                        WHEN COALESCE(NULLIF(TRIM(F.codigo), ''), '') <> ''
                             AND COALESCE(NULLIF(TRIM(F.descripcion), ''), '') <> ''
                            THEN CONCAT(F.codigo, ' - ', F.descripcion)
                        WHEN COALESCE(NULLIF(TRIM(F.descripcion), ''), '') <> ''
                            THEN F.descripcion
                        WHEN COALESCE(NULLIF(TRIM(F.codigo), ''), '') <> ''
                            THEN F.codigo
                        ELSE CONCAT('Familia ', F.id)
                    END AS name
                FROM familia F
                INNER JOIN item I ON I.familia_id = F.id
                WHERE I.seccion_id = $sectionId
                ORDER BY name ASC
                """.trimIndent(),
            )
        }

    suspend fun listSubFamilies(
        database: Database,
        familyId: Int,
    ): List<Pair<Int, String>> =
        dbQuery(database) {
            listCatalogBySql(
                """
                SELECT
                    SF.id AS id,
                    CASE
                        WHEN COALESCE(NULLIF(TRIM(SF.codigo), ''), '') <> ''
                             AND COALESCE(NULLIF(TRIM(SF.descripcion), ''), '') <> ''
                            THEN CONCAT(SF.codigo, ' - ', SF.descripcion)
                        WHEN COALESCE(NULLIF(TRIM(SF.descripcion), ''), '') <> ''
                            THEN SF.descripcion
                        WHEN COALESCE(NULLIF(TRIM(SF.codigo), ''), '') <> ''
                            THEN SF.codigo
                        ELSE CONCAT('Subfamilia ', SF.id)
                    END AS name
                FROM subfamilia SF
                INNER JOIN subfamilia_familia SFF ON SFF.subfamilia_id = SF.id
                WHERE SFF.familia_id = $familyId
                ORDER BY name ASC
                """.trimIndent(),
            )
        }

    suspend fun listBrands(database: Database): List<Pair<Int, String>> =
        dbQuery(database) {
            listCatalogBySql(
                """
                SELECT
                    M.id AS id,
                    CASE
                        WHEN COALESCE(NULLIF(TRIM(M.codigo), ''), '') <> ''
                             AND COALESCE(NULLIF(TRIM(M.descripcion), ''), '') <> ''
                            THEN CONCAT(M.codigo, ' - ', M.descripcion)
                        WHEN COALESCE(NULLIF(TRIM(M.descripcion), ''), '') <> ''
                            THEN M.descripcion
                        WHEN COALESCE(NULLIF(TRIM(M.codigo), ''), '') <> ''
                            THEN M.codigo
                        ELSE CONCAT('Marca ', M.id)
                    END AS name
                FROM marca M
                ORDER BY name ASC
                """.trimIndent(),
            )
        }

    suspend fun listLines(
        database: Database,
        brandId: Int,
    ): List<Pair<Int, String>> =
        dbQuery(database) {
            listCatalogBySql(
                """
                SELECT
                    L.cod_linea AS id,
                    CASE
                        WHEN COALESCE(NULLIF(TRIM(L.descripcion), ''), '') <> ''
                            THEN CONCAT(LPAD(L.cod_linea, 5, '0'), ' - ', L.descripcion)
                        ELSE CONCAT('Linea ', L.cod_linea)
                    END AS name
                FROM linea L
                WHERE L.marca = $brandId
                ORDER BY name ASC
                """.trimIndent(),
            )
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
                    is ItemsTableVE -> {
                        table.insert {
                            // Campos base
                            it[codItem] = request.code
                            it[descripcion1] = request.name
                            it[descripcion2] = request.description
                            it[referencia] = request.reference
                            it[codigoBarras] = request.barcode
                            it[codigoBarras2] = request.barcode2 ?: ""
                            it[codigoBarras3] = request.barcode3 ?: ""
                            it[codDepartamento] = request.departmentId
                            it[departamentoId] = request.departmentId
                            it[seccionId] = request.sectionId
                            it[familiaId] = request.familyId
                            it[subfamiliaId] = request.subfamilyId
                            it[marcaId] = request.brandId
                            it[codLinea] = request.lineId
                            it[lineaId] = request.lineId
                            it[precio1] = request.price1.toBigDecimal()
                            it[utilidad1] = request.utility1.toBigDecimal()
                            it[coniva1] = request.priceWithTax1.toBigDecimal()
                            it[precio2] = request.price2.toBigDecimal()
                            it[utilidad2] = request.utility2.toBigDecimal()
                            it[coniva2] = request.priceWithTax2.toBigDecimal()
                            it[precio3] = request.price3.toBigDecimal()
                            it[utilidad3] = request.utility3.toBigDecimal()
                            it[coniva3] = request.priceWithTax3.toBigDecimal()
                            it[precio4] = request.price4.toBigDecimal()
                            it[utilidad4] = request.utility4.toBigDecimal()
                            it[coniva4] = request.priceWithTax4.toBigDecimal()
                            it[precio5] = request.price5.toBigDecimal()
                            it[utilidad5] = request.utility5.toBigDecimal()
                            it[coniva5] = request.priceWithTax5.toBigDecimal()
                            it[costoActual] = request.currentCost.toBigDecimal()
                            it[montoExento] = !request.isTaxExempt
                            it[iva] = if (request.isTaxExempt) BigDecimal.ZERO else request.taxRate.toBigDecimal()
                            it[existenciaTotal] = request.totalStock
                            it[estatus] = "A"
                            it[codItemForma] = 1
                            it[tipoProd] = 2
                            it[usuarioCreacion] = "API"
                            // Campos específicos VE (solo los que existen)
                            it[balanza] = request.isScale ?: false
                            it[idMonedaBase] = request.baseCurrencyId
                        } get table.idItem
                    }
                    is ItemsTablePA -> {
                        table.insert {
                            // Campos base
                            it[codItem] = request.code
                            it[descripcion1] = request.name
                            it[descripcion2] = request.description
                            it[referencia] = request.reference
                            it[codigoBarras] = request.barcode
                            it[codigoBarras2] = request.barcode2 ?: ""
                            it[codigoBarras3] = request.barcode3 ?: ""
                            it[codDepartamento] = request.departmentId
                            it[departamentoId] = request.departmentId
                            it[seccionId] = request.sectionId
                            it[familiaId] = request.familyId
                            it[subfamiliaId] = request.subfamilyId
                            it[marcaId] = request.brandId
                            it[codLinea] = request.lineId
                            it[lineaId] = request.lineId
                            it[precio1] = request.price1.toBigDecimal()
                            it[utilidad1] = request.utility1.toBigDecimal()
                            it[coniva1] = request.priceWithTax1.toBigDecimal()
                            it[precio2] = request.price2.toBigDecimal()
                            it[utilidad2] = request.utility2.toBigDecimal()
                            it[coniva2] = request.priceWithTax2.toBigDecimal()
                            it[precio3] = request.price3.toBigDecimal()
                            it[utilidad3] = request.utility3.toBigDecimal()
                            it[coniva3] = request.priceWithTax3.toBigDecimal()
                            it[precio4] = request.price4.toBigDecimal()
                            it[utilidad4] = request.utility4.toBigDecimal()
                            it[coniva4] = request.priceWithTax4.toBigDecimal()
                            it[precio5] = request.price5.toBigDecimal()
                            it[utilidad5] = request.utility5.toBigDecimal()
                            it[coniva5] = request.priceWithTax5.toBigDecimal()
                            it[costoActual] = request.currentCost.toBigDecimal()
                            it[montoExento] = !request.isTaxExempt
                            it[iva] = if (request.isTaxExempt) BigDecimal.ZERO else request.taxRate.toBigDecimal()
                            it[existenciaTotal] = request.totalStock
                            it[estatus] = "A"
                            it[codItemForma] = 1
                            it[tipoProd] = 2
                            it[usuarioCreacion] = "API"
                            // Campos específicos PA (solo los que existen)
                            it[detallesKit] = request.kitDetails ?: "F"
                            it[idSegmentoGob] = request.governmentSegmentId
                            it[idFamiliaGob] = request.governmentFamilyId
                        } get table.idItem
                    }
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
                    is ItemsTableVE -> {
                        table.update({ table.idItem eq id }) {
                            it[codItem] = request.code
                            it[descripcion1] = request.name
                            it[referencia] = request.reference
                            it[codigoBarras] = request.barcode
                            it[codigoBarras2] = request.barcode2 ?: ""
                            it[codigoBarras3] = request.barcode3 ?: ""
                            it[codDepartamento] = request.departmentId
                            it[departamentoId] = request.departmentId
                            it[seccionId] = request.sectionId
                            it[familiaId] = request.familyId
                            it[subfamiliaId] = request.subfamilyId
                            it[marcaId] = request.brandId
                            it[codLinea] = request.lineId
                            it[lineaId] = request.lineId
                            it[precio1] = request.price1.toBigDecimal()
                            it[utilidad1] = request.utility1.toBigDecimal()
                            it[coniva1] = request.priceWithTax1.toBigDecimal()
                            it[precio2] = request.price2.toBigDecimal()
                            it[utilidad2] = request.utility2.toBigDecimal()
                            it[coniva2] = request.priceWithTax2.toBigDecimal()
                            it[precio3] = request.price3.toBigDecimal()
                            it[utilidad3] = request.utility3.toBigDecimal()
                            it[coniva3] = request.priceWithTax3.toBigDecimal()
                            it[precio4] = request.price4.toBigDecimal()
                            it[utilidad4] = request.utility4.toBigDecimal()
                            it[coniva4] = request.priceWithTax4.toBigDecimal()
                            it[precio5] = request.price5.toBigDecimal()
                            it[utilidad5] = request.utility5.toBigDecimal()
                            it[coniva5] = request.priceWithTax5.toBigDecimal()
                            it[costoActual] = request.currentCost.toBigDecimal()
                            it[montoExento] = !request.isTaxExempt
                            it[iva] = if (request.isTaxExempt) BigDecimal.ZERO else request.taxRate.toBigDecimal()
                            // Solo VE
                            it[balanza] = request.isScale ?: false
                            it[idMonedaBase] = request.baseCurrencyId
                        }
                    }
                    is ItemsTablePA -> {
                        table.update({ table.idItem eq id }) {
                            it[codItem] = request.code
                            it[descripcion1] = request.name
                            it[referencia] = request.reference
                            it[codigoBarras] = request.barcode
                            it[codigoBarras2] = request.barcode2 ?: ""
                            it[codigoBarras3] = request.barcode3 ?: ""
                            it[codDepartamento] = request.departmentId
                            it[departamentoId] = request.departmentId
                            it[seccionId] = request.sectionId
                            it[familiaId] = request.familyId
                            it[subfamiliaId] = request.subfamilyId
                            it[marcaId] = request.brandId
                            it[codLinea] = request.lineId
                            it[lineaId] = request.lineId
                            it[precio1] = request.price1.toBigDecimal()
                            it[utilidad1] = request.utility1.toBigDecimal()
                            it[coniva1] = request.priceWithTax1.toBigDecimal()
                            it[precio2] = request.price2.toBigDecimal()
                            it[utilidad2] = request.utility2.toBigDecimal()
                            it[coniva2] = request.priceWithTax2.toBigDecimal()
                            it[precio3] = request.price3.toBigDecimal()
                            it[utilidad3] = request.utility3.toBigDecimal()
                            it[coniva3] = request.priceWithTax3.toBigDecimal()
                            it[precio4] = request.price4.toBigDecimal()
                            it[utilidad4] = request.utility4.toBigDecimal()
                            it[coniva4] = request.priceWithTax4.toBigDecimal()
                            it[precio5] = request.price5.toBigDecimal()
                            it[utilidad5] = request.utility5.toBigDecimal()
                            it[coniva5] = request.priceWithTax5.toBigDecimal()
                            it[costoActual] = request.currentCost.toBigDecimal()
                            it[montoExento] = !request.isTaxExempt
                            it[iva] = if (request.isTaxExempt) BigDecimal.ZERO else request.taxRate.toBigDecimal()
                            // Solo PA
                            it[detallesKit] = request.kitDetails ?: "F"
                            it[idSegmentoGob] = request.governmentSegmentId
                            it[idFamiliaGob] = request.governmentFamilyId
                        }
                    }
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

    private fun mapRowToProduct(
        row: ResultRow,
        countryCode: String,
    ): Product {
        val table = ItemsTableFactory.getTableForCountry(countryCode)
        val storedTaxRate = row[table.iva].toDouble()
        val hasTaxInPrices =
            listOf(
                row[table.coniva1].toDouble() > row[table.precio1].toDouble(),
                row[table.coniva2].toDouble() > row[table.precio2].toDouble(),
                row[table.coniva3].toDouble() > row[table.precio3].toDouble(),
                row[table.coniva4].toDouble() > row[table.precio4].toDouble(),
                row[table.coniva5].toDouble() > row[table.precio5].toDouble(),
            ).any { it }
        val isExempt = storedTaxRate <= 0.0 && !hasTaxInPrices
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
    ): List<com.amaxoniaerp.features.items.domain.PriceLevel> {
        val storedTaxRate = row[table.iva].toDouble()
        val hasTaxInPrices =
            listOf(
                row[table.coniva1].toDouble() > row[table.precio1].toDouble(),
                row[table.coniva2].toDouble() > row[table.precio2].toDouble(),
                row[table.coniva3].toDouble() > row[table.precio3].toDouble(),
                row[table.coniva4].toDouble() > row[table.precio4].toDouble(),
                row[table.coniva5].toDouble() > row[table.precio5].toDouble(),
            ).any { it }
        val isExempt = storedTaxRate <= 0.0 && !hasTaxInPrices

        fun unitPriceWithTax(unitPrice: Double): Double =
            if (unitPrice <= 0.0 || isExempt) unitPrice else unitPrice * (1.0 + (storedTaxRate / 100.0))

        val unitPrice1 = row.getOrNull(table.precio1Extra)?.toDouble() ?: 0.0
        val unitPrice2 = row.getOrNull(table.precio2Extra)?.toDouble() ?: 0.0
        val unitPrice3 = row.getOrNull(table.precio3Extra)?.toDouble() ?: 0.0
        val unitPrice4 = row.getOrNull(table.precio4Extra)?.toDouble() ?: 0.0
        val unitPrice5 = row.getOrNull(table.precio5Extra)?.toDouble() ?: 0.0
        return listOf(
            com.amaxoniaerp.features.items.domain.PriceLevel(
                label = "A",
                price = row[table.precio1].toDouble(),
                utilityPercent = row[table.utilidad1].toDouble(),
                pricePlusUtility = row[table.precio1].toDouble(),
                pricePlusTax = if (isExempt) row[table.precio1].toDouble() else row[table.coniva1].toDouble(),
                unitPrice = unitPrice1,
                unitPricePlusTax = unitPriceWithTax(unitPrice1),
                discountPercent = row[table.descuento1].toDouble(),
            ),
            com.amaxoniaerp.features.items.domain.PriceLevel(
                label = "B",
                price = row[table.precio2].toDouble(),
                utilityPercent = row[table.utilidad2].toDouble(),
                pricePlusUtility = row[table.precio2].toDouble(),
                pricePlusTax = if (isExempt) row[table.precio2].toDouble() else row[table.coniva2].toDouble(),
                unitPrice = unitPrice2,
                unitPricePlusTax = unitPriceWithTax(unitPrice2),
                discountPercent = row[table.descuento2].toDouble(),
            ),
            com.amaxoniaerp.features.items.domain.PriceLevel(
                label = "C",
                price = row[table.precio3].toDouble(),
                utilityPercent = row[table.utilidad3].toDouble(),
                pricePlusUtility = row[table.precio3].toDouble(),
                pricePlusTax = if (isExempt) row[table.precio3].toDouble() else row[table.coniva3].toDouble(),
                unitPrice = unitPrice3,
                unitPricePlusTax = unitPriceWithTax(unitPrice3),
                discountPercent = row[table.descuento3].toDouble(),
            ),
            com.amaxoniaerp.features.items.domain.PriceLevel(
                label = "D",
                price = row[table.precio4].toDouble(),
                utilityPercent = row[table.utilidad4].toDouble(),
                pricePlusUtility = row[table.precio4].toDouble(),
                pricePlusTax = if (isExempt) row[table.precio4].toDouble() else row[table.coniva4].toDouble(),
                unitPrice = unitPrice4,
                unitPricePlusTax = unitPriceWithTax(unitPrice4),
                discountPercent = row[table.descuento4].toDouble(),
            ),
            com.amaxoniaerp.features.items.domain.PriceLevel(
                label = "E",
                price = row[table.precio5].toDouble(),
                utilityPercent = row[table.utilidad5].toDouble(),
                pricePlusUtility = row[table.precio5].toDouble(),
                pricePlusTax = if (isExempt) row[table.precio5].toDouble() else row[table.coniva5].toDouble(),
                unitPrice = unitPrice5,
                unitPricePlusTax = unitPriceWithTax(unitPrice5),
                discountPercent = row[table.descuento5].toDouble(),
            ),
        )
    }
}

private fun listCatalogBySql(sql: String): List<Pair<Int, String>> =
    TransactionManager.current().exec(sql) { result ->
        val list = mutableListOf<Pair<Int, String>>()
        while (result.next()) {
            list.add(result.getInt("id") to result.getString("name"))
        }
        list
    } ?: emptyList()

private fun resolveBrandIdByLineId(lineId: Int): Int {
    if (lineId <= 0) return 0
    val sql = "SELECT marca FROM linea WHERE cod_linea = $lineId LIMIT 1"
    return TransactionManager.current().exec(sql) { rs ->
        if (rs.next()) rs.getInt("marca") else 0
    } ?: 0
}

private fun isSaleWarehouse(tipo: String?): Boolean {
    val normalized = tipo?.trim()?.uppercase().orEmpty()
    if (normalized.isBlank()) return true
    if (normalized.contains("MERMA")) return false
    if (normalized.contains("DESPERDICIO")) return false
    if (normalized.contains("NO_VENTA")) return false
    return normalized !in setOf("M", "MERMA", "WASTE")
}

private fun BigDecimal?.toSafeDouble(): Double = this?.toDouble() ?: 0.0
