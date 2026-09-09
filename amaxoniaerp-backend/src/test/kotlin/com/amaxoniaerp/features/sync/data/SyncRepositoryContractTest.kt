package com.amaxoniaerp.features.sync.data

import com.amaxoniaerp.features.clients.data.ClientSucursalTable
import com.amaxoniaerp.features.clients.data.ClientsTable
import com.amaxoniaerp.features.items.data.ItemsTableFactory
import com.amaxoniaerp.features.items.data.mapRowToProductSync
import com.amaxoniaerp.features.pos.data.CajaFormaPagoTable
import com.amaxoniaerp.features.pos.data.CajaFormaTable
import com.amaxoniaerp.features.sync.data.CatalogChangesTable
import com.amaxoniaerp.features.sync.domain.SyncEntityType
import com.amaxoniaerp.features.sync.domain.SyncScope
import com.amaxoniaerp.features.sync.domain.productContentHash
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.SqlExpressionBuilder.less
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SyncRepositoryContractTest {
    private lateinit var database: Database
    private val repository = SyncRepository()
    private val country = "VE"

    @BeforeTest
    fun setUp() {
        database =
            Database.connect(
                "jdbc:h2:mem:sync_contract_${System.nanoTime()};MODE=MySQL;DB_CLOSE_DELAY=-1",
                "org.h2.Driver",
            )
        val items = ItemsTableFactory.getTableForCountry(country)
        val deptPan = 1
        val deptLimpieza = 2
        val sucursalA = 10
        val sucursalB = 20
        transaction(database) {
            SchemaUtils.create(items, ClientsTable, ClientSucursalTable, CajaFormaPagoTable, CajaFormaTable, CatalogChangesTable)
            items.insert {
                it[idItem] = 1
                it[codItem] = "P-1"
                it[descripcion1] = "PAN"
                it[marcaId] = 7
                it[departamentoId] = deptPan
                it[estatus] = "A"
            }
            items.insert {
                it[idItem] = 2
                it[codItem] = "P-2"
                it[descripcion1] = "CAFE"
                it[marcaId] = 7
                it[codDepartamento] = deptPan
                it[estatus] = "A"
            }
            items.insert {
                it[idItem] = 3
                it[codItem] = "P-3"
                it[descripcion1] = "JABON"
                it[marcaId] = 7
                it[departamentoId] = deptLimpieza
                it[estatus] = "A"
            }
            ClientsTable.insert {
                it[idCliente] = "CLI-A"
                it[codCliente] = "CLI-0000A"
                it[rif] = "J-1"
                it[nombre] = "CLIENTE A"
                it[estado] = "1"
                it[idSucursal] = sucursalA
            }
            ClientsTable.insert {
                it[idCliente] = "CLI-B"
                it[codCliente] = "CLI-0000B"
                it[rif] = "J-2"
                it[nombre] = "CLIENTE B"
                it[estado] = "1"
                it[idSucursal] = sucursalB
            }
            CatalogChangesTable.insert {
                it[entityType] = "PRODUCT"
                it[entityId] = "1"
                it[op] = "UPSERT"
            }
            CatalogChangesTable.insert {
                it[entityType] = "PRODUCT"
                it[entityId] = "3"
                it[op] = "UPSERT"
            }
            CatalogChangesTable.insert {
                it[entityType] = "PRODUCT"
                it[entityId] = "99"
                it[op] = "UPSERT"
            }
            CatalogChangesTable.insert {
                it[entityType] = "CLIENT"
                it[entityId] = "CLI-A"
                it[op] = "UPSERT"
            }
        }
    }

    @AfterTest
    fun tearDown() {
        val items = ItemsTableFactory.getTableForCountry(country)
        transaction(database) { SchemaUtils.drop(items, ClientsTable, CatalogChangesTable) }
    }

    private fun scopeDepartamento1() = SyncScope.fromParams(deptIds = "1", branchIds = "10")

    private fun jsonId(element: kotlinx.serialization.json.JsonElement): String =
        ((element as kotlinx.serialization.json.JsonObject)["id"] as kotlinx.serialization.json.JsonPrimitive).content

    @Test
    fun `manifest calcula conteo y hash bajo alcance`() =
        runBlocking {
            val scoped = repository.manifest(database, country, scopeDepartamento1())
            val products = scoped.entities.first { it.type == "PRODUCT" }
            val clients = scoped.entities.first { it.type == "CLIENT" }

            assertEquals(2L, products.count)
            assertEquals(1L, clients.count)
            assertTrue(products.hash != 0L)

            val all = repository.manifest(database, country, SyncScope.ALL)
            val allProducts = all.entities.first { it.type == "PRODUCT" }
            assertEquals(3L, allProducts.count)
            assertTrue(allProducts.hash != products.hash)
        }

    @Test
    fun `bootstrap pagina por keyset con alcance`() =
        runBlocking {
            val page1 = repository.bootstrapPage(database, country, SyncEntityType.PRODUCT, scopeDepartamento1(), afterId = null, limit = 1)
            assertEquals(listOf("1"), page1.items.map { jsonId(it) })
            assertTrue(page1.hasMore)
            assertEquals("1", page1.nextAfterId)

            val page2 =
                repository.bootstrapPage(
                    database = database,
                    countryCode = country,
                    entityType = SyncEntityType.PRODUCT,
                    scope = scopeDepartamento1(),
                    afterId = page1.nextAfterId,
                    limit = 1,
                )
            assertEquals(listOf("2"), page2.items.map { jsonId(it) })
            assertTrue(!page2.hasMore)
        }

    @Test
    fun `delta hidrata upsert delete y scope-exit`() =
        runBlocking {
            val delta = repository.changesPage(database, country, scopeDepartamento1(), cursor = 0, limit = 10)

            val expectedChangeCount = 4
            assertEquals(expectedChangeCount, delta.changes.size)
            val byEntity = delta.changes.associateBy { "${it.entityType}:${it.entityId}" }

            val p1 = byEntity.getValue("PRODUCT:1")
            assertEquals("UPSERT", p1.op)
            assertTrue(p1.payload != null)

            val p3 = byEntity.getValue("PRODUCT:3")
            assertEquals("DELETE", p3.op)
            assertTrue(p3.payload == null)

            val p99 = byEntity.getValue("PRODUCT:99")
            assertEquals("DELETE", p99.op)

            val cliA = byEntity.getValue("CLIENT:CLI-A")
            assertEquals("UPSERT", cliA.op)

            assertEquals(4L, delta.nextCursor)
            assertTrue(!delta.hasMore)
        }

    @Test
    fun `cliente fuera de alcance del delta llega como delete`() =
        runBlocking {
            val scopeOtraSucursal = SyncScope.fromParams(deptIds = null, branchIds = "20")
            val delta = repository.changesPage(database, country, scopeOtraSucursal, cursor = 3, limit = 10)
            val cliA = delta.changes.single { it.entityId == "CLI-A" }
            assertEquals("DELETE", cliA.op)
        }

    @Test
    fun `cursor vencido lanza cursor expired con el change id mas viejo`() =
        runBlocking {
            // Podan los cambios 1 y 2: queda oldest=3. Un cursor=1 necesita el
            // cambio 2 (podado) → 410. Un cursor=0 (fresco) nunca expira.
            val cutoffChangeId = 3L
            transaction(database) {
                CatalogChangesTable.deleteWhere { CatalogChangesTable.changeId less cutoffChangeId }
            }
            val staleCursor = 1L
            val exception =
                assertFailsWith<SyncCursorExpiredException> {
                    repository.changesPage(database, country, SyncScope.ALL, cursor = staleCursor, limit = 10)
                }
            assertEquals(cutoffChangeId, exception.oldestRetainedChangeId)

            val fresh = repository.changesPage(database, country, SyncScope.ALL, cursor = 0, limit = 10)
            assertTrue(fresh.changes.isNotEmpty())
        }

    @Test
    fun `scope preview refleja el alcance`() =
        runBlocking {
            val scoped = repository.scopePreview(database, country, SyncEntityType.PRODUCT, scopeDepartamento1())
            assertEquals(2L, scoped.count)

            val all = repository.scopePreview(database, country, SyncEntityType.PRODUCT, SyncScope.ALL)
            assertEquals(3L, all.count)
        }

    @Test
    fun `poda elimina cambios viejos y el feed queda consistente`() =
        runBlocking {
            val oldDays = 45
            val secondsPerDay = 86_400L
            transaction(database) {
                CatalogChangesTable.insert {
                    it[entityType] = "PRODUCT"
                    it[entityId] = "1"
                    it[op] = "UPSERT"
                    it[createdAt] = Instant.now().minusSeconds(oldDays * secondsPerDay)
                }
            }

            val retentionDays = 30
            val deleted = repository.pruneOldChanges(database, retentionDays = retentionDays)
            assertTrue(deleted >= 1)

            val expectedRemaining = 4L
            val remaining = transaction(database) { CatalogChangesTable.selectAll().count() }
            assertEquals(expectedRemaining, remaining)
        }

    @Test
    fun `hash de manifest coincide con el calculo por fila`() =
        runBlocking {
            val manifest = repository.manifest(database, country, SyncScope.ALL)
            val products = manifest.entities.first { it.type == "PRODUCT" }

            var expectedHash = 0L
            transaction(database) {
                val table = ItemsTableFactory.getTableForCountry(country)
                table.selectAll().forEach { row ->
                    val dto = mapRowToProductSync(row, country)
                    expectedHash += productContentHash(dto)
                }
            }
            assertEquals(expectedHash, products.hash)
        }
}
