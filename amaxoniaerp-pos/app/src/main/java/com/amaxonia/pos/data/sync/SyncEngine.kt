package com.amaxonia.pos.data.sync

import androidx.room.withTransaction
import com.amaxonia.pos.data.local.db.AppDatabase
import com.amaxonia.pos.data.local.db.CajaPaymentMethodEntity
import com.amaxonia.pos.data.local.db.ClientSucursalEntity
import com.amaxonia.pos.data.local.db.ClientTypeEntity
import com.amaxonia.pos.data.local.db.PaymentMethodEntity
import com.amaxonia.pos.data.local.db.PromocionDetalleEntity
import com.amaxonia.pos.data.local.db.PromocionEntity
import com.amaxonia.pos.data.local.db.ProductEntity
import com.amaxonia.pos.data.local.db.SyncStateEntity
import com.amaxonia.pos.data.local.AppJson
import com.amaxonia.pos.data.remote.SyncApiClient
import com.amaxonia.pos.data.sync.SyncBootstrapResponseDto
import com.amaxonia.pos.data.remote.SyncScopeQuery
import com.amaxonia.pos.data.remote.SyncCursorExpiredApiException
import kotlinx.serialization.json.JsonElement

private const val SCOPE_GLOBAL = "GLOBAL"

/** Empresa + sesión bajo las cuales corre el sync. */
private data class SyncContext(
    val tenant: String,
    val token: String,
)
private const val STATE_BOOTSTRAPPING = "BOOTSTRAPPING"
private const val STATE_UP_TO_DATE = "UP_TO_DATE"
private const val PAGE_LIMIT = 1_000
private const val CHUNK_SIZE = 500
private const val OP_DELETE = "DELETE"

/** Entidades del catálogo en orden de bootstrap (dependencias primero). */
val SYNC_ENTITY_ORDER: List<String> =
    listOf(
        "CLIENT_TYPE",
        "CLIENT",
        "CLIENT_BRANCH",
        "PRODUCT",
        "PROMOTION",
        "PROMOTION_DETAIL",
        "PAYMENT_METHOD",
        "CAJA_PAYMENT_METHOD",
    )

/**
 * Motor de sincronización offline (ADR-007/008, PLAN §7):
 *  - Bootstrap: keyset por entidad, lotes de 500 con el checkpoint
 *    (`afterId`) en la MISMA transacción Room — kill-safe y reanudable.
 *  - Incremental: delta paginado con hidratación del servidor (UPSERT con
 *    payload / DELETE por tombstone o scope-exit); el cursor avanza en la
 *    misma transacción que los datos aplicados.
 *  - 410 CURSOR_EXPIRED → resync completo automático.
 */
class SyncEngine(
    private val database: AppDatabase,
    private val api: SyncApiClient,
    private val scopeProvider: suspend () -> OfflineSyncScope,
    private val tenantProvider: suspend () -> String?,
    private val tokenProvider: suspend () -> String?,
    private val logFn: (String) -> Unit = {},
) {
    sealed interface Outcome {
        data class Success(
            val bootstrapped: Boolean,
            val changesApplied: Int,
        ) : Outcome

        data class Error(val message: String) : Outcome
    }

    /** Sync incremental explícito (usado por el worker y reconciliación). */
    suspend fun runIncremental(): Outcome {
        val context = resolveContext()
        val outcome =
            if (context == null) {
                Outcome.Error(CONTEXT_MISSING)
            } else {
                runIncrementalFor(context, scopeProvider())
            }
        return outcome
    }

    suspend fun runBootstrap(tenantId: String? = null): Outcome {
        val context =
            if (tenantId != null) {
                val token = tokenProvider()
                if (token == null) null else SyncContext(tenantId, token)
            } else {
                resolveContext()
            }
        val outcome =
            if (context == null) {
                Outcome.Error(CONTEXT_MISSING)
            } else {
                runBootstrapFor(context, scopeProvider())
            }
        return outcome
    }

    /** Purga local por cambio de alcance (§18.4); las entidades se re-descargan al re-conectar. */
    suspend fun purgeForScope(scope: OfflineSyncScope): Outcome {
        val tenant = tenantProvider()
        val outcome =
            if (tenant == null) {
                Outcome.Error(CONTEXT_MISSING)
            } else {
                purgeAndMark(tenant, scope)
                Outcome.Success(bootstrapped = false, changesApplied = 0)
            }
        return outcome
    }

    private suspend fun resolveContext(): SyncContext? {
        val tenant = tenantProvider()
        val token = tokenProvider()
        return if (tenant == null || token == null) null else SyncContext(tenant, token)
    }

    private suspend fun runIncrementalFor(
        context: SyncContext,
        scope: OfflineSyncScope,
    ): Outcome {
        val global = database.syncStateDao().get(context.tenant, SCOPE_GLOBAL)
        val outcome =
            when {
                global == null || global.cursor <= 0L -> runBootstrapFor(context, scope)
                else -> runDeltaFor(context, scope, global)
            }
        return outcome
    }

    /**
     * Reconciliación (PLAN §7.3): aplica el delta pendiente y compara conteos
     * locales vs servidor. Divergencia ⇒ bootstrap automático.
     */
    suspend fun reconcile(): Outcome {
        runIncremental()
        val context = resolveContext()
        val scope = scopeProvider()
        val manifest =
            if (context == null) {
                null
            } else {
                runCatching {
                    api.manifest(token = context.token, scope = SyncScopeQuery(scope.deptParams(), scope.branchParams()))
                }.getOrNull()
            }
        val outcome =
            when {
                context == null || manifest == null -> Outcome.Error("Error de reconciliación")
                else -> {
                    val localProducts = database.productDao().count()
                    val localClients = database.clientDao().count()
                    val serverProducts = manifest.entities.firstOrNull { it.type == "PRODUCT" }?.count
                    val serverClients = manifest.entities.firstOrNull { it.type == "CLIENT" }?.count
                    val divergent =
                        (serverProducts != null && serverProducts != localProducts.toLong()) ||
                            (serverClients != null && serverClients != localClients.toLong())
                    if (divergent) {
                        logFn("Reconciliación: divergencia detectada. Bootstrap automático.")
                        runBootstrapFor(context, scope)
                    } else {
                        Outcome.Success(bootstrapped = false, changesApplied = 0)
                    }
                }
            }
        return outcome
    }

    private suspend fun runDeltaFor(
        context: SyncContext,
        scope: OfflineSyncScope,
        global: SyncStateEntity,
    ): Outcome {
        var applied = 0
        var cursor = global.cursor
        var hasMore = true
        var outcome: Outcome = Outcome.Success(bootstrapped = false, changesApplied = 0)
        while (hasMore) {
            val pageResult =
                runCatching {
                    api.changes(
                        token = context.token,
                        cursor = cursor,
                        limit = PAGE_LIMIT,
                        scope = SyncScopeQuery(scope.deptParams(), scope.branchParams()),
                    )
                }
            val page = pageResult.getOrNull()
            if (page == null) {
                outcome = onDeltaFailure(pageResult.exceptionOrNull() ?: RuntimeException("Error de delta"), context, scope)
                hasMore = false
            } else {
                database.withTransaction {
                    page.changes.forEach { change ->
                        applyChange(change.entityType, change.entityId, change.op, change.payload)
                    }
                    database.syncStateDao().upsert(
                        SyncStateEntity(
                            tenantId = context.tenant,
                            scope = SCOPE_GLOBAL,
                            cursor = page.nextCursor,
                            snapshotId = global.snapshotId,
                            status = STATE_UP_TO_DATE,
                            updatedAt = System.currentTimeMillis(),
                        ),
                    )
                }
                applied += page.changes.size
                cursor = page.nextCursor
                hasMore = page.hasMore
                outcome = Outcome.Success(bootstrapped = false, changesApplied = applied)
            }
        }
        return outcome
    }

    /** Cursor expirado → resync completo; cualquier otro error → reintento con backoff. */
    private suspend fun onDeltaFailure(
        cause: Throwable,
        context: SyncContext,
        scope: OfflineSyncScope,
    ): Outcome =
        when (cause) {
            is SyncCursorExpiredApiException -> {
                logFn("Cursor expirado (oldest=${cause.oldestRetainedChangeId}); resync completo")
                runBootstrapFor(context, scope)
            }
            else -> {
                logFn("Error de delta: ${cause.message}")
                Outcome.Error(cause.message ?: "Error de delta")
            }
        }

    private suspend fun runBootstrapFor(
        context: SyncContext,
        scope: OfflineSyncScope,
    ): Outcome {
        val manifest =
            runCatching {
                api.manifest(token = context.token, scope = SyncScopeQuery(scope.deptParams(), scope.branchParams()))
            }
        val snapshotId = manifest.getOrNull()?.latestChangeId
        val bootstrap =
            if (manifest.isFailure || snapshotId == null) {
                Outcome.Error(manifest.exceptionOrNull()?.message ?: "Error de manifest")
            } else {
                val (entitiesLoaded, error) = loadAllEntities(context, scope, snapshotId)
                if (error != null) {
                    Outcome.Error(error)
                } else {
                    markUpToDate(context.tenant, snapshotId)
                    Outcome.Success(bootstrapped = true, changesApplied = entitiesLoaded)
                }
            }
        return bootstrap
    }

    /** Carga las 8 entidades en orden; devuelve ítems cargados o el error del primer fallo. */
    private suspend fun loadAllEntities(
        context: SyncContext,
        scope: OfflineSyncScope,
        snapshotId: Long,
    ): Pair<Int, String?> {
        var applied = 0
        var error: String? = null
        SYNC_ENTITY_ORDER.forEach { entity ->
            var afterId: String? = null
            var hasMore = true
            while (hasMore && error == null) {
                val page =
                    runCatching {
                        api.bootstrap(
                            token = context.token,
                            entityType = entity,
                            afterId = afterId,
                            limit = PAGE_LIMIT,
                            scope = SyncScopeQuery(scope.deptParams(), scope.branchParams()),
                        )
                    }.getOrNull()
                if (page == null) {
                    error = "Error de bootstrap de $entity"
                } else {
                    val entities = page.items.map { decodeEntity(entity, it) }
                    persistBootstrapChunk(context.tenant, entity, snapshotId, entities, page)
                    applied += entities.size
                    afterId = page.nextAfterId
                    hasMore = page.hasMore
                }
            }
        }
        return applied to error
    }

    private suspend fun persistBootstrapChunk(
        tenant: String,
        entity: String,
        snapshotId: Long,
        entities: List<Any>,
        page: SyncBootstrapResponseDto,
    ) {
        database.withTransaction {
            entities.chunked(CHUNK_SIZE).forEach { chunk -> upsertChunk(chunk) }
            database.syncStateDao().upsert(
                SyncStateEntity(
                    tenantId = tenant,
                    scope = entity,
                    cursor = 0,
                    snapshotId = snapshotId,
                    afterId = page.nextAfterId,
                    status = if (page.hasMore) STATE_BOOTSTRAPPING else STATE_UP_TO_DATE,
                    updatedAt = System.currentTimeMillis(),
                ),
            )
        }
    }

    private suspend fun markUpToDate(
        tenant: String,
        snapshotId: Long,
    ) {
        database.withTransaction {
            database.syncStateDao().upsert(
                SyncStateEntity(
                    tenantId = tenant,
                    scope = SCOPE_GLOBAL,
                    cursor = snapshotId,
                    snapshotId = snapshotId,
                    status = STATE_UP_TO_DATE,
                    updatedAt = System.currentTimeMillis(),
                ),
            )
        }
    }

    private suspend fun purgeAndMark(
        tenant: String,
        scope: OfflineSyncScope,
    ) {
        database.withTransaction {
            if (scope.allProducts) {
                database.productDao().clearAll()
            } else {
                database.productDao().deleteByDepartmentsNotIn(scope.departmentIds.toList())
            }
            if (scope.allClients) {
                database.clientDao().deleteBySucursalesNotIn(emptyList())
            } else {
                database.clientDao().deleteBySucursalesNotIn(scope.branchIds.toList())
            }
            SYNC_ENTITY_ORDER.forEach { entity ->
                val state = database.syncStateDao().get(tenant, entity)
                database.syncStateDao().upsert(
                    SyncStateEntity(
                        tenantId = tenant,
                        scope = entity,
                        cursor = state?.cursor ?: 0,
                        snapshotId = state?.snapshotId ?: 0,
                        afterId = null,
                        status = STATE_BOOTSTRAPPING,
                        updatedAt = System.currentTimeMillis(),
                    ),
                )
            }
        }
    }

    private suspend fun upsertChunk(chunk: List<Any>) {
        chunk.forEach { dto ->
            when (dto) {
                is ProductEntity -> database.productDao().upsertAll(listOf(dto))
                is ClientTypeEntity -> database.clientTypeDao().upsertAll(listOf(dto))
                is com.amaxonia.pos.data.local.db.ClientEntity -> database.clientDao().upsertAll(listOf(dto))
                is ClientSucursalEntity -> database.clientSucursalDao().upsertAll(listOf(dto))
                is PromocionEntity -> database.promocionDao().upsertPromociones(listOf(dto))
                is PromocionDetalleEntity -> database.promocionDao().upsertDetalles(listOf(dto))
                is PaymentMethodEntity -> database.paymentMethodDao().upsertAll(listOf(dto))
                is CajaPaymentMethodEntity -> database.paymentMethodDao().upsertCajaMappings(listOf(dto))
            }
        }
    }

    private suspend fun applyChange(
        entityType: String,
        entityId: String,
        op: String,
        payload: JsonElement?,
    ) {
        val isDelete = op == OP_DELETE || payload == null
        when {
            isDelete -> applyDelete(entityType, entityId)
            else -> upsertChunk(listOf(decodeEntity(entityType, payload)))
        }
    }

    private suspend fun applyDelete(
        entityType: String,
        entityId: String,
    ) {
        when (entityType) {
            "PRODUCT" -> database.productDao().deleteById(entityId)
            "CLIENT" -> database.clientDao().deleteById(entityId)
            "CLIENT_BRANCH" -> database.clientSucursalDao().deleteById(entityId.toIntOrNull() ?: -1)
            "CLIENT_TYPE" -> database.clientTypeDao().deleteById(entityId.toIntOrNull() ?: -1)
            "PROMOTION" -> database.promocionDao().deletePromocionById(entityId)
            "PROMOTION_DETAIL" -> database.promocionDao().deleteDetalleById(entityId)
            // Formas de pago: sin delete puntual; convergen por resync/purga.
            else -> Unit
        }
    }

    private fun decodeEntity(
        entityType: String,
        element: JsonElement,
    ): Any =
        when (entityType) {
            "PRODUCT" -> AppJson.decodeFromJsonElement(ProductSyncDto.serializer(), element).toEntity()
            "CLIENT" -> AppJson.decodeFromJsonElement(ClientSyncDto.serializer(), element).toEntity()
            "CLIENT_BRANCH" -> AppJson.decodeFromJsonElement(ClientBranchSyncDto.serializer(), element).toEntity()
            "CLIENT_TYPE" -> AppJson.decodeFromJsonElement(ClientTypeSyncDto.serializer(), element).toEntity()
            "PROMOTION" -> AppJson.decodeFromJsonElement(PromotionSyncDto.serializer(), element).toEntity()
            "PROMOTION_DETAIL" -> AppJson.decodeFromJsonElement(PromotionDetailSyncDto.serializer(), element).toEntity()
            "PAYMENT_METHOD" -> AppJson.decodeFromJsonElement(PaymentMethodSyncDto.serializer(), element).toEntity()
            "CAJA_PAYMENT_METHOD" -> AppJson.decodeFromJsonElement(CajaPaymentMethodSyncDto.serializer(), element).toEntity()
            else -> throw IllegalArgumentException("Entidad de sync desconocida: $entityType")
        }

    private fun OfflineSyncScope.deptParams(): List<Int>? = if (allProducts) null else departmentIds.toList()

    private fun OfflineSyncScope.branchParams(): List<Int>? = if (allClients) null else branchIds.toList()

    companion object {
        private const val CONTEXT_MISSING = "Sin empresa o sesión activa"
    }
}
