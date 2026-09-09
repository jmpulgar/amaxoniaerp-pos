package com.amaxoniaerp.features.sync.data

import com.amaxoniaerp.features.clients.data.ClientSucursalTable
import com.amaxoniaerp.features.clients.data.ClientsTable
import com.amaxoniaerp.features.items.data.BaseItemsTable
import com.amaxoniaerp.features.items.data.ItemsTableFactory
import com.amaxoniaerp.features.items.data.mapRowToProductSync
import com.amaxoniaerp.features.pos.data.CajaFormaPagoTable
import com.amaxoniaerp.features.pos.data.CajaFormaTable
import com.amaxoniaerp.features.sync.domain.CajaPaymentMethodSyncDto
import com.amaxoniaerp.features.sync.domain.ClientBranchSyncDto
import com.amaxoniaerp.features.sync.domain.ClientSyncDto
import com.amaxoniaerp.features.sync.domain.ClientTypeSyncDto
import com.amaxoniaerp.features.sync.domain.PaymentMethodSyncDto
import com.amaxoniaerp.features.sync.domain.ProductSyncDto
import com.amaxoniaerp.features.sync.domain.PromotionDetailSyncDto
import com.amaxoniaerp.features.sync.domain.PromotionSyncDto
import com.amaxoniaerp.features.sync.domain.SyncEntityType
import com.amaxoniaerp.features.sync.domain.SyncScope
import com.amaxoniaerp.features.sync.domain.cajaPaymentMethodContentHash
import com.amaxoniaerp.features.sync.domain.clientBranchContentHash
import com.amaxoniaerp.features.sync.domain.clientContentHash
import com.amaxoniaerp.features.sync.domain.clientTypeContentHash
import com.amaxoniaerp.features.sync.domain.paymentMethodContentHash
import com.amaxoniaerp.features.sync.domain.productContentHash
import com.amaxoniaerp.features.sync.domain.promotionContentHash
import com.amaxoniaerp.features.sync.domain.promotionDetailContentHash
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.Query
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.andWhere
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.slf4j.LoggerFactory

/**
 * Lectura del catálogo para el sync: páginas keyset de bootstrap, conteo +
 * hash de contenido bajo alcance, y carga de entidades pequeñas. El alcance
 * (ADR-008) filtra PRODUCT por departamento y CLIENT por sucursal propietaria.
 */
class SyncCatalogReader {
    private val log = LoggerFactory.getLogger(SyncCatalogReader::class.java)
    private val json = Json { encodeDefaults = true }

    /** Serializa cualquier DTO slim a JSON sin casts genéricos. */
    fun encodeDto(dto: Any): String =
        when (dto) {
            is ProductSyncDto -> json.encodeToString(ProductSyncDto.serializer(), dto)
            is ClientSyncDto -> json.encodeToString(ClientSyncDto.serializer(), dto)
            is ClientBranchSyncDto -> json.encodeToString(ClientBranchSyncDto.serializer(), dto)
            is ClientTypeSyncDto -> json.encodeToString(ClientTypeSyncDto.serializer(), dto)
            is PromotionSyncDto -> json.encodeToString(PromotionSyncDto.serializer(), dto)
            is PromotionDetailSyncDto -> json.encodeToString(PromotionDetailSyncDto.serializer(), dto)
            is PaymentMethodSyncDto -> json.encodeToString(PaymentMethodSyncDto.serializer(), dto)
            is CajaPaymentMethodSyncDto -> json.encodeToString(CajaPaymentMethodSyncDto.serializer(), dto)
            else -> throw IllegalArgumentException("DTO de sync no soportado: ${dto::class.simpleName}")
        }

    fun contentHashOf(dto: Any): Long =
        when (dto) {
            is ProductSyncDto -> productContentHash(dto)
            is ClientSyncDto -> clientContentHash(dto)
            is ClientBranchSyncDto -> clientBranchContentHash(dto)
            is ClientTypeSyncDto -> clientTypeContentHash(dto)
            is PromotionSyncDto -> promotionContentHash(dto)
            is PromotionDetailSyncDto -> promotionDetailContentHash(dto)
            is PaymentMethodSyncDto -> paymentMethodContentHash(dto)
            is CajaPaymentMethodSyncDto -> cajaPaymentMethodContentHash(dto)
            else -> 0L
        }

    fun countAndHash(
        type: SyncEntityType,
        countryCode: String,
        scope: SyncScope,
    ): Pair<Long, Long> =
        when (type) {
            SyncEntityType.PRODUCT -> {
                var count = 0L
                var hash = 0L
                productQuery(countryCode, scope).forEach { row ->
                    count++
                    hash += productContentHash(mapRowToProductSync(row, countryCode))
                }
                count to hash
            }
            SyncEntityType.CLIENT -> {
                var count = 0L
                var hash = 0L
                clientQuery(scope).forEach { row ->
                    count++
                    hash += clientContentHash(mapClientSync(row))
                }
                count to hash
            }
            else -> {
                val rows = loadSmallEntity(type)
                rows.size.toLong() to rows.fold(0L) { acc, dto -> acc + contentHashOf(dto) }
            }
        }

    fun countProducts(
        countryCode: String,
        scope: SyncScope,
    ): Long = productQuery(countryCode, scope).count()

    fun countClients(scope: SyncScope): Long = clientQuery(scope).count()

    // ------------------------------------------------------------------
    // Páginas de bootstrap
    // ------------------------------------------------------------------

    fun productPage(
        countryCode: String,
        scope: SyncScope,
        afterId: String?,
        limit: Int,
    ): Page {
        val table = ItemsTableFactory.getTableForCountry(countryCode)
        val query = productQuery(countryCode, scope)
        if (afterId != null) {
            query.andWhere { table.idItem greater (afterId.toIntOrNull() ?: 0) }
        }
        val rows = query.orderBy(table.idItem, SortOrder.ASC).limit(limit + 1).toList()
        val hasMore = rows.size > limit
        val dtos = rows.take(limit).map { mapRowToProductSync(it, countryCode) }
        return Page(dtos, dtos.lastOrNull()?.id.takeIf { hasMore }, hasMore)
    }

    fun clientPage(
        scope: SyncScope,
        afterId: String?,
        limit: Int,
    ): Page {
        val query = clientQuery(scope)
        if (afterId != null) {
            query.andWhere { ClientsTable.idCliente greater afterId }
        }
        val rows = query.orderBy(ClientsTable.idCliente, SortOrder.ASC).limit(limit + 1).toList()
        val hasMore = rows.size > limit
        val dtos = rows.take(limit).map { mapClientSync(it) }
        return Page(dtos, dtos.lastOrNull()?.id.takeIf { hasMore }, hasMore)
    }

    fun clientBranchPage(
        afterId: String?,
        limit: Int,
    ): Page =
        intKeysetPage(ClientSucursalTable.selectAll(), ClientSucursalTable.sucursalId, afterId, limit) {
            mapClientBranchSync(it)
        }

    fun paymentMethodPage(
        afterId: String?,
        limit: Int,
    ): Page =
        intKeysetPage(CajaFormaPagoTable.selectAll(), CajaFormaPagoTable.idFormaPago, afterId, limit) {
            mapPaymentMethodSync(it)
        }

    fun cajaPaymentMethodPage(
        afterId: String?,
        limit: Int,
    ): Page {
        val query = CajaFormaTable.selectAll()
        val after = afterId?.split(':')
        if (after != null && after.size == 2) {
            val caja = after[0]
            val forma = after[1].toIntOrNull() ?: -1
            query.andWhere {
                (CajaFormaTable.idCaja greater caja) or
                    ((CajaFormaTable.idCaja eq caja) and (CajaFormaTable.idFormaPago greater forma))
            }
        }
        val rows =
            query
                .orderBy(CajaFormaTable.idCaja, SortOrder.ASC)
                .orderBy(CajaFormaTable.idFormaPago, SortOrder.ASC)
                .limit(limit + 1)
                .toList()
        val hasMore = rows.size > limit
        val dtos = rows.take(limit).map { mapCajaPaymentMethodSync(it) }
        val lastKey = dtos.lastOrNull()?.let { "${it.idCaja}:${it.idFormaPago}" }
        return Page(dtos, lastKey.takeIf { hasMore }, hasMore)
    }

    fun clientTypePage(): Page = Page(loadClientTypes(), null, hasMore = false)

    fun promotionPage(): Page = Page(loadPromotions(), null, hasMore = false)

    fun promotionDetailPage(): Page = Page(loadPromotionDetails(), null, hasMore = false)

    // ------------------------------------------------------------------
    // Carga completa de entidades pequeñas (sin alcance)
    // ------------------------------------------------------------------

    fun loadSmallEntity(type: SyncEntityType): List<Any> =
        when (type) {
            SyncEntityType.CLIENT_BRANCH -> loadClientBranches()
            SyncEntityType.CLIENT_TYPE -> loadClientTypes()
            SyncEntityType.PROMOTION -> loadPromotions()
            SyncEntityType.PROMOTION_DETAIL -> loadPromotionDetails()
            SyncEntityType.PAYMENT_METHOD -> loadPaymentMethods()
            SyncEntityType.CAJA_PAYMENT_METHOD -> loadCajaPaymentMethods()
            else -> emptyList()
        }

    fun loadClientTypes(): List<ClientTypeSyncDto> =
        runCatching {
            TransactionManager.current().exec("SELECT * FROM tipo_cliente ORDER BY 1") { rs ->
                val meta = rs.metaData
                val cols = (1..meta.columnCount).associateBy { meta.getColumnLabel(it).lowercase() }
                val idIdx = firstColumn(cols, "id", "cod_tipo_cliente", "codigo", "id_tipo_cliente")
                val descIdx = firstColumn(cols, "descripcion", "description", "denominacion", "nombre")
                val feIdx = firstColumn(cols, "tipoclientefe", "codigo_fe", "fe_code", "cod_fe", "fe")
                buildList {
                    while (rs.next()) {
                        add(
                            ClientTypeSyncDto(
                                id = valueAsInt(idIdx?.let { rs.getObject(it) }),
                                descripcion = valueAsString(descIdx?.let { rs.getObject(it) }).orEmpty(),
                                tipoClienteFe = valueAsString(feIdx?.let { rs.getObject(it) }),
                            ),
                        )
                    }
                }
            } ?: emptyList()
        }.getOrElse {
            log.warn("No se pudo leer tipo_cliente: {}", it.message)
            emptyList()
        }

    private fun loadPromotions(): List<PromotionSyncDto> =
        runCatching {
            TransactionManager
                .current()
                .exec(
                    """
                    SELECT id, id_item, codigo, inicio, fin, promocion, imagen, activo, descuento_global
                    FROM promocion ORDER BY id
                    """.trimIndent(),
                ) { rs ->
                    buildList {
                        while (rs.next()) {
                            add(
                                PromotionSyncDto(
                                    id = rs.getString("id"),
                                    idItem = rs.getString("id_item") ?: "",
                                    codigo = rs.getString("codigo") ?: "",
                                    inicio = rs.getTimestamp("inicio")?.toLocalDateTime()?.toString(),
                                    fin = rs.getTimestamp("fin")?.toLocalDateTime()?.toString(),
                                    promocion = rs.getString("promocion") ?: "",
                                    imagen = rs.getString("imagen") ?: "",
                                    activo = rs.getInt("activo"),
                                    descuentoGlobal = rs.getBigDecimal("descuento_global")?.toDouble() ?: 0.0,
                                ),
                            )
                        }
                    }
                } ?: emptyList()
        }.getOrElse {
            log.warn("No se pudo leer promocion: {}", it.message)
            emptyList()
        }

    private fun loadPromotionDetails(): List<PromotionDetailSyncDto> =
        runCatching {
            TransactionManager
                .current()
                .exec(
                    """
                    SELECT id, id_promocion, id_item, cantidad, cantidad_total, unidad_empaque,
                           descuento, descuento_monto, id_tipo_precio, precio, impuesto,
                           impuesto_porcentaje, importe, grupo
                    FROM promocion_detalle ORDER BY id
                    """.trimIndent(),
                ) { rs ->
                    buildList {
                        while (rs.next()) {
                            add(
                                PromotionDetailSyncDto(
                                    id = rs.getString("id"),
                                    idPromocion = rs.getString("id_promocion") ?: "",
                                    idItem = rs.getString("id_item") ?: "",
                                    cantidad = rs.getBigDecimal("cantidad")?.toDouble() ?: 0.0,
                                    cantidadTotal = rs.getBigDecimal("cantidad_total")?.toDouble() ?: 0.0,
                                    unidadEmpaque = rs.getString("unidad_empaque") ?: "",
                                    descuento = rs.getString("descuento")?.toDoubleOrNull() ?: 0.0,
                                    descuentoMonto = rs.getBigDecimal("descuento_monto")?.toDouble() ?: 0.0,
                                    idTipoPrecio = rs.getString("id_tipo_precio"),
                                    precio = rs.getBigDecimal("precio")?.toDouble() ?: 0.0,
                                    impuesto = rs.getBigDecimal("impuesto")?.toDouble() ?: 0.0,
                                    impuestoPorcentaje = rs.getBigDecimal("impuesto_porcentaje")?.toDouble() ?: 0.0,
                                    importe = rs.getBigDecimal("importe")?.toDouble() ?: 0.0,
                                    grupo = rs.getString("grupo") ?: "",
                                ),
                            )
                        }
                    }
                } ?: emptyList()
        }.getOrElse {
            log.warn("No se pudo leer promocion_detalle: {}", it.message)
            emptyList()
        }

    private fun loadClientBranches(): List<ClientBranchSyncDto> =
        ClientSucursalTable
            .selectAll()
            .orderBy(ClientSucursalTable.sucursalId, SortOrder.ASC)
            .toList()
            .map { mapClientBranchSync(it) }

    private fun loadPaymentMethods(): List<PaymentMethodSyncDto> =
        CajaFormaPagoTable
            .selectAll()
            .orderBy(CajaFormaPagoTable.idFormaPago, SortOrder.ASC)
            .toList()
            .map { mapPaymentMethodSync(it) }

    private fun loadCajaPaymentMethods(): List<CajaPaymentMethodSyncDto> =
        CajaFormaTable
            .selectAll()
            .orderBy(CajaFormaTable.idCaja, SortOrder.ASC)
            .orderBy(CajaFormaTable.idFormaPago, SortOrder.ASC)
            .toList()
            .map { mapCajaPaymentMethodSync(it) }

    // ------------------------------------------------------------------
    // Queries base con alcance
    // ------------------------------------------------------------------

    /** Resolved-department ∈ alcance: (dep_id>0 ∧ dep_id∈ids) ∨ (dep_id≤0 ∧ cod_dep∈ids). */
    private fun departmentScopeCondition(
        table: BaseItemsTable,
        scope: SyncScope,
    ): Op<Boolean> =
        with(org.jetbrains.exposed.sql.SqlExpressionBuilder) {
            ((table.departamentoId greater 0) and (table.departamentoId inList scope.departmentIds.toList())) or
                ((table.departamentoId lessEq 0) and (table.codDepartamento inList scope.departmentIds.toList()))
        }

    private fun productQuery(
        countryCode: String,
        scope: SyncScope,
    ): Query {
        val table = ItemsTableFactory.getTableForCountry(countryCode)
        val query = table.selectAll()
        if (!scope.allProducts) {
            query.andWhere { departmentScopeCondition(table, scope) }
        }
        return query
    }

    private fun clientQuery(scope: SyncScope): Query {
        val query = ClientsTable.selectAll()
        if (!scope.allClients) {
            query.andWhere { ClientsTable.idSucursal inList scope.branchIds.toList() }
        }
        return query
    }

    private inline fun intKeysetPage(
        base: Query,
        keyColumn: org.jetbrains.exposed.sql.Column<Int>,
        afterId: String?,
        limit: Int,
        map: (org.jetbrains.exposed.sql.ResultRow) -> Any,
    ): Page {
        val query = base
        if (afterId != null) {
            val after = afterId.toIntOrNull() ?: 0
            query.andWhere { keyColumn greater after }
        }
        val rows = query.orderBy(keyColumn, SortOrder.ASC).limit(limit + 1).toList()
        val hasMore = rows.size > limit
        val dtos = rows.take(limit).map(map)
        val lastKey = rows.getOrNull(limit - 1)?.let { it[keyColumn].toString() }
        return Page(dtos, lastKey.takeIf { hasMore }, hasMore)
    }

    data class Page(
        val items: List<Any>,
        val lastKey: String?,
        val hasMore: Boolean,
    )

    companion object {
        private fun firstColumn(
            cols: Map<String, Int>,
            vararg names: String,
        ): Int? {
            for (name in names) {
                val idx = cols[name]
                if (idx != null) return idx
            }
            return null
        }

        private fun valueAsString(value: Any?): String? =
            when (value) {
                null -> null
                else -> value.toString()
            }

        private fun valueAsInt(value: Any?): Int =
            when (value) {
                is Number -> value.toInt()
                is String -> value.toIntOrNull() ?: 0
                else -> 0
            }
    }
}
