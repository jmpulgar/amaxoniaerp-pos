package com.amaxonia.pos.data.sync

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.amaxonia.pos.data.local.db.AppDatabase
import com.amaxonia.pos.data.remote.SyncApiClient
import com.amaxonia.pos.data.remote.SyncCursorExpiredApiException
import com.amaxonia.pos.data.sync.SyncEngine
import com.amaxonia.pos.data.sync.SyncBootstrapResponseDto
import com.amaxonia.pos.data.sync.SyncDeltaChangeDto
import com.amaxonia.pos.data.sync.SyncDeltaResponseDto
import com.amaxonia.pos.data.sync.SyncManifestDto
import com.amaxonia.pos.data.sync.SyncScopePreviewDto
import com.amaxonia.pos.data.remote.SyncScopeQuery
import com.amaxonia.pos.data.sync.SyncCatalogItemDto
import com.amaxonia.pos.data.sync.OfflineSyncScope
import com.amaxonia.pos.data.sync.ProductSyncDto
import com.amaxonia.pos.data.sync.PriceLevelSyncDto
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private fun productDto(
    id: String,
    taxRate: Double = 16.0,
): ProductSyncDto =
    ProductSyncDto(
        id = id,
        code = "C-$id",
        description = "Producto $id",
        barcode1 = "750$id",
        department = 1,
        taxRate = taxRate,
        estatus = "A",
        prices = listOf(
            com.amaxonia.pos.data.sync.PriceLevelSyncDto(label = "A", price = 10.0, pricePlusTax = 11.6),
        ),
    )

private fun jsonOf(dto: ProductSyncDto): JsonElement = Json.encodeToJsonElement(ProductSyncDto.serializer(), dto)

private fun bootstrapPage(
    items: List<ProductSyncDto>,
    nextAfterId: String?,
): SyncBootstrapResponseDto =
    SyncBootstrapResponseDto(
        entityType = "PRODUCT",
        items = items.map { jsonOf(it) },
        nextAfterId = nextAfterId,
        hasMore = nextAfterId != null,
    )

private fun emptyPage(entity: String) = SyncBootstrapResponseDto(entityType = entity, items = emptyList(), hasMore = false)

/** Cliente de sync en memoria: páginas encoladas por endpoint. */
private class FakeSyncApi : SyncApiClient {
    var manifest = SyncManifestDto(latestChangeId = 100)
    val bootstrapPages = mutableMapOf<String, MutableList<SyncBootstrapResponseDto>>()
    val deltaPages = mutableListOf<SyncDeltaResponseDto>()
    var changesError: SyncCursorExpiredApiException? = null
    var lastBootstrapAfterId: String? = null

    override suspend fun manifest(
        token: String,
        scope: SyncScopeQuery,
    ): SyncManifestDto = manifest

    override suspend fun bootstrap(
        token: String,
        entityType: String,
        afterId: String?,
        limit: Int,
        scope: SyncScopeQuery,
    ): SyncBootstrapResponseDto {
        lastBootstrapAfterId = afterId
        val queue = bootstrapPages[entityType]
        if (queue.isNullOrEmpty()) return emptyPage(entityType)
        return if (afterId == null) queue.first() else queue.last()
    }

    override suspend fun changes(
        token: String,
        cursor: Long,
        limit: Int,
        scope: SyncScopeQuery,
    ): SyncDeltaResponseDto {
        changesError?.let { throw it }
        return if (cursor == 0L && deltaPages.isEmpty()) {
            SyncDeltaResponseDto()
        } else {
            val page = deltaPages.removeFirstOrNull() ?: SyncDeltaResponseDto(nextCursor = cursor)
            page
        }
    }

    override suspend fun scopePreview(
        token: String,
        entity: String,
        scope: SyncScopeQuery,
    ): SyncScopePreviewDto = SyncScopePreviewDto(entityType = entity)

    override suspend fun sucursales(token: String): List<SyncCatalogItemDto> = emptyList()
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SyncEngineTest {

    private lateinit var database: AppDatabase
    private val fakeApi = FakeSyncApi()
    private val scope = OfflineSyncScope.ALL

    private fun engine(): SyncEngine =
        SyncEngine(
            database = database,
            api = fakeApi,
            scopeProvider = { scope },
            tenantProvider = { "T1" },
            tokenProvider = { "token" },
        )

    @Before
    fun setUp() {
        database =
            Room
                .inMemoryDatabaseBuilder(
                    ApplicationProvider.getApplicationContext(),
                    AppDatabase::class.java,
                ).allowMainThreadQueries()
                .build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun seedProductBootstrap() {
        fakeApi.manifest = SyncManifestDto(latestChangeId = 100)
        fakeApi.bootstrapPages["PRODUCT"] =
            mutableListOf(
                bootstrapPage(listOf(productDto("P1"), productDto("P2")), nextAfterId = "P2"),
                bootstrapPage(listOf(productDto("P3")), nextAfterId = null),
            )
    }

    @Test
    fun `bootstrap carga el catálogo y siembra el cursor con el snapshot`() = runBlocking {
        seedProductBootstrap()

        val outcome = engine().runBootstrap()

        val success = outcome as SyncEngine.Outcome.Success
        assertTrue(success.bootstrapped)
        val products = database.productDao().getPaged(limit = 100, offset = 0)
        assertEquals(3, products.size)
        val global = database.syncStateDao().get("T1", "GLOBAL")!!
        assertEquals(100L, global.cursor)
        assertEquals("UP_TO_DATE", global.status)
    }

    @Test
    fun `delta aplica upserts y deletes y avanza el cursor`() = runBlocking {
        seedProductBootstrap()
        engine().runBootstrap()

        val updated = productDto("P1", taxRate = 21.0)
        val deleted = productDto("P2")
        val upsertJson = jsonOf(updated)
        val deleteJson = jsonOf(deleted)
        fakeApi.deltaPages +=
            SyncDeltaResponseDto(
                changes =
                    listOf(
                        com.amaxonia.pos.data.sync.SyncDeltaChangeDto(
                            changeId = 101,
                            entityType = "PRODUCT",
                            entityId = "P1",
                            op = "UPSERT",
                            payload = upsertJson,
                        ),
                        com.amaxonia.pos.data.sync.SyncDeltaChangeDto(
                            changeId = 102,
                            entityType = "PRODUCT",
                            entityId = "P2",
                            op = "DELETE",
                            payload = deleteJson,
                        ),
                    ),
                nextCursor = 102,
                hasMore = false,
            )

        val outcome = engine().runIncremental()

        val success = outcome as SyncEngine.Outcome.Success
        assertEquals(2, success.changesApplied)
        val p1 = database.productDao().getById("P1")
        assertEquals(21.0, p1?.taxRate)
        assertNull(database.productDao().getById("P2"))
        assertEquals(102L, database.syncStateDao().get("T1", "GLOBAL")?.cursor)
    }

    @Test
    fun `cursor expirado dispara resync completo automatico`() = runBlocking {
        seedProductBootstrap()
        engine().runBootstrap()

        seedProductBootstrap() // repone páginas de bootstrap para el resync
        fakeApi.changesError = SyncCursorExpiredApiException(oldestRetainedChangeId = 90)

        val outcome = engine().runIncremental()

        val success = outcome as SyncEngine.Outcome.Success
        assertTrue(success.bootstrapped)
        assertEquals(3, database.productDao().getPaged(limit = 100, offset = 0).size)
    }
}
