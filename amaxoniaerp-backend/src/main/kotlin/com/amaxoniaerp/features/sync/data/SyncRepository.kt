package com.amaxoniaerp.features.sync.data

import com.amaxoniaerp.core.database.dbQuery
import com.amaxoniaerp.features.sync.domain.CatalogContentHash
import com.amaxoniaerp.features.sync.domain.DeltaChangeDto
import com.amaxoniaerp.features.sync.domain.EntityStatDto
import com.amaxoniaerp.features.sync.domain.ScopePreviewResponse
import com.amaxoniaerp.features.sync.domain.SimpleCatalogItemDto
import com.amaxoniaerp.features.sync.domain.SyncBootstrapResponse
import com.amaxoniaerp.features.sync.domain.SyncDeltaResponse
import com.amaxoniaerp.features.sync.domain.SyncEntityType
import com.amaxoniaerp.features.sync.domain.SyncManifestResponse
import com.amaxoniaerp.features.sync.domain.SyncScope
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.less
import org.jetbrains.exposed.sql.andWhere
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.slf4j.LoggerFactory
import java.time.Instant

/** El cursor del dispositivo es anterior al change_id más viejo retenido. */
class SyncCursorExpiredException(
    val oldestRetainedChangeId: Long,
) : RuntimeException("Cursor fuera de la retención del feed")

/**
 * API del sync incremental (/api/sync/v1): manifest/reconcile (conteo + hash
 * de contenido bajo alcance), bootstrap keyset, delta hidratado
 * (scope-exit → DELETE), scope-preview, catálogos del picker y poda.
 * Sin estado: el alcance viaja en cada request (ADR-008).
 */
class SyncRepository(
    private val reader: SyncCatalogReader = SyncCatalogReader(),
    private val hydrator: DeltaHydrator = DeltaHydrator(reader),
) {
    private val log = LoggerFactory.getLogger(SyncRepository::class.java)

    private fun toJsonElement(dto: Any): JsonElement = Json.parseToJsonElement(reader.encodeDto(dto))

    suspend fun manifest(
        database: Database,
        countryCode: String,
        scope: SyncScope,
        retentionDays: Int = DEFAULT_RETENTION_DAYS,
    ): SyncManifestResponse =
        dbQuery(database) {
            SyncManifestResponse(
                hashScheme = CatalogContentHash.SCHEME,
                schemaVersion = SCHEMA_VERSION,
                retentionDays = retentionDays,
                latestChangeId = latestChangeId(),
                entities =
                    SyncEntityType.entries.map { type ->
                        val (count, hash) = reader.countAndHash(type, countryCode, scope)
                        EntityStatDto(type = type.wire, count = count, hash = hash)
                    },
            )
        }

    suspend fun bootstrapPage(
        database: Database,
        countryCode: String,
        entityType: SyncEntityType,
        scope: SyncScope,
        afterId: String?,
        limit: Int,
    ): SyncBootstrapResponse =
        dbQuery(database) {
            val page = readerPage(entityType, countryCode, scope, afterId, limit)
            SyncBootstrapResponse(
                entityType = entityType.wire,
                items = page.items.map { toJsonElement(it) },
                nextAfterId = page.lastKey,
                hasMore = page.hasMore,
            )
        }

    suspend fun changesPage(
        database: Database,
        countryCode: String,
        scope: SyncScope,
        cursor: Long,
        limit: Int,
    ): SyncDeltaResponse =
        dbQuery(database) {
            val oldest = oldestRetainedChangeId()
            // cursor=0 significa "desde el principio": nunca expira. Un cursor
            // C>0 expira cuando el siguiente cambio que necesita (C+1) ya fue
            // podado (C+1 < oldest).
            if (oldest != null && cursor > 0 && cursor + 1 < oldest) {
                throw SyncCursorExpiredException(oldest)
            }

            val rows =
                CatalogChangesTable
                    .selectAll()
                    .andWhere { CatalogChangesTable.changeId greater cursor }
                    .orderBy(CatalogChangesTable.changeId, SortOrder.ASC)
                    .limit(limit + 1)
                    .toList()

            val hasMore = rows.size > limit
            val changes =
                rows.take(limit).map { row ->
                    val type = SyncEntityType.entries.first { it.wire == row[CatalogChangesTable.entityType] }
                    val entityId = row[CatalogChangesTable.entityId]
                    val hydrated = hydrator.hydrate(type, entityId, countryCode, scope)
                    DeltaChangeDto(
                        changeId = row[CatalogChangesTable.changeId],
                        entityType = type.wire,
                        entityId = entityId,
                        op = if (hydrated is DeltaHydrator.Hydrated.Upsert) OP_UPSERT else OP_DELETE,
                        payload =
                            (hydrated as? DeltaHydrator.Hydrated.Upsert)?.let {
                                toJsonElement(it.payload)
                            },
                    )
                }

            SyncDeltaResponse(
                changes = changes,
                nextCursor = changes.lastOrNull()?.changeId ?: cursor,
                hasMore = hasMore,
            )
        }

    suspend fun scopePreview(
        database: Database,
        countryCode: String,
        entityType: SyncEntityType,
        scope: SyncScope,
    ): ScopePreviewResponse =
        dbQuery(database) {
            val count =
                when (entityType) {
                    SyncEntityType.PRODUCT -> reader.countProducts(countryCode, scope)
                    SyncEntityType.CLIENT -> reader.countClients(scope)
                    else -> reader.loadSmallEntity(entityType).size.toLong()
                }
            ScopePreviewResponse(entityType = entityType.wire, count = count)
        }

    suspend fun sucursales(database: Database): List<SimpleCatalogItemDto> =
        dbQuery(database) {
            runCatching {
                TransactionManager.current().exec("SELECT * FROM sucursal ORDER BY 1") { rs ->
                    val meta = rs.metaData
                    val cols = (1..meta.columnCount).associateBy { meta.getColumnLabel(it).lowercase() }
                    val idIdx = firstColumn(cols, "id", "cod_sucursal", "codigo")
                    val nameIdx = firstColumn(cols, "nombre", "sucursal", "descripcion", "denominacion")
                    buildList {
                        while (rs.next()) {
                            add(
                                SimpleCatalogItemDto(
                                    id = valueAsString(idIdx?.let { rs.getObject(it) }).orEmpty(),
                                    nombre = valueAsString(nameIdx?.let { rs.getObject(it) }),
                                ),
                            )
                        }
                    }
                } ?: emptyList()
            }.getOrElse {
                log.warn("No se pudo leer el catálogo de sucursales: {}", it.message)
                emptyList()
            }
        }

    /** Elimina del feed los cambios más viejos que `retentionDays`. */
    suspend fun pruneOldChanges(
        database: Database,
        retentionDays: Int = DEFAULT_RETENTION_DAYS,
    ): Int =
        dbQuery(database) {
            val cutoff = Instant.now().minusSeconds(retentionDays * SECONDS_PER_DAY)
            CatalogChangesTable.deleteWhere { createdAt less cutoff }
        }

    private fun readerPage(
        entityType: SyncEntityType,
        countryCode: String,
        scope: SyncScope,
        afterId: String?,
        limit: Int,
    ): SyncCatalogReader.Page =
        when (entityType) {
            SyncEntityType.PRODUCT -> reader.productPage(countryCode, scope, afterId, limit)
            SyncEntityType.CLIENT -> reader.clientPage(scope, afterId, limit)
            SyncEntityType.CLIENT_BRANCH -> reader.clientBranchPage(afterId, limit)
            SyncEntityType.PAYMENT_METHOD -> reader.paymentMethodPage(afterId, limit)
            SyncEntityType.CAJA_PAYMENT_METHOD -> reader.cajaPaymentMethodPage(afterId, limit)
            SyncEntityType.CLIENT_TYPE -> reader.clientTypePage()
            SyncEntityType.PROMOTION -> reader.promotionPage()
            SyncEntityType.PROMOTION_DETAIL -> reader.promotionDetailPage()
        }

    private fun latestChangeId(): Long =
        TransactionManager.current().exec("SELECT MAX(change_id) AS m FROM catalog_changes") { rs ->
            if (rs.next()) (rs.getObject(1) as? Number)?.toLong() ?: 0L else 0L
        } ?: 0L

    private fun oldestRetainedChangeId(): Long? =
        TransactionManager.current().exec("SELECT MIN(change_id) AS m FROM catalog_changes") { rs ->
            if (rs.next()) (rs.getObject(1) as? Number)?.toLong() else null
        }

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

    companion object {
        const val SCHEMA_VERSION = 1
        const val DEFAULT_RETENTION_DAYS = 30
        const val OP_UPSERT = "UPSERT"
        const val OP_DELETE = "DELETE"
        private const val SECONDS_PER_DAY = 86_400L
    }
}
