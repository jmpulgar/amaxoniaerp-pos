package com.amaxonia.pos.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.amaxonia.pos.data.local.LocalStore
import com.amaxonia.pos.data.local.db.AppDatabase
import com.amaxonia.pos.data.local.db.toEntity
import com.amaxonia.pos.data.local.saveCompanySession
import com.amaxonia.pos.data.remote.ApiClient
import com.amaxonia.pos.data.remote.ApiConfigManager
import com.amaxonia.pos.data.remote.ApiService
import com.amaxonia.pos.data.remote.dto.ProductDto
import com.amaxonia.pos.data.sync.OfflineSyncScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Politica offline-first del catálogo (TASK-082): la caida de red degrada al
 * catálogo cacheado en Room y el stock por almacén exige conexión explícita.
 * El fallo de red se fuerza con un puerto local cerrado.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OfflineFirstProductRepositoryTest {
    private lateinit var db: AppDatabase
    private lateinit var store: LocalStore
    private lateinit var secure: FakeSecureKeyValueStore

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db =
            Room
                .inMemoryDatabaseBuilder(context, AppDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        secure = FakeSecureKeyValueStore()
        store = LocalStore(context, secure)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `getAllProducts con fallo de red sirve el catalogo cacheado`() =
        runTest {
            store.saveCompanySession(testCompanySession())
            seed(product("1", "a", "Cafetera"), product("2", "b", "Tostadora"))
            val repository = repository(online = true)

            val result = repository.getAllProducts()

            assertEquals(setOf("1", "2"), result.getOrThrow().map { it.id }.toSet())
        }

    @Test
    fun `getProductById con fallo de red cae al producto cacheado`() =
        runTest {
            store.saveCompanySession(testCompanySession())
            seed(product("1", "a", "Cafetera"))
            val repository = repository(online = true)

            val cached = repository.getProductById("1")
            val missing = repository.getProductById("missing")

            assertEquals("Cafetera", cached.getOrThrow().description)
            assertTrue(missing.isFailure)
        }

    @Test
    fun `getProductStock offline falla con mensaje explicito de conexion`() =
        runTest {
            store.saveCompanySession(testCompanySession())
            val repository = repository(online = false)

            val result = repository.getProductStock("1")

            assertEquals("Sin conexión para consultar stock por almacén", result.exceptionOrNull()?.message)
        }

    @Test
    fun `getProductStock sin sesion falla con error de negocio`() =
        runTest {
            val repository = repository(online = true)

            val result = repository.getProductStock("1")

            assertEquals("No hay empresa seleccionada", result.exceptionOrNull()?.message)
        }

    @Test
    fun `getDepartments offline falla en vez de inventar catalogo`() =
        runTest {
            store.saveCompanySession(testCompanySession())
            val repository = repository(online = false)

            val result = repository.getDepartments()

            assertEquals("Sin conexión", result.exceptionOrNull()?.message)
        }

    @Test
    fun `getAllProducts offline devuelve todos los productos descargados sin filtrar por departamento`() =
        runTest {
            store.saveCompanySession(testCompanySession())
            seed(
                product("1", "a", "Cafetera", department = "1"),
                product("2", "b", "Tostadora", department = "2"),
            )
            val repository = repository(online = false, scope = OfflineSyncScope(enabled = true, departmentIds = setOf(1)))

            val result = repository.getAllProducts(departmentId = null)

            assertEquals(setOf("1", "2"), result.getOrThrow().map { it.id }.toSet())
        }

    private fun repository(
        online: Boolean,
        scope: com.amaxonia.pos.data.sync.OfflineSyncScope = com.amaxonia.pos.data.sync.OfflineSyncScope.ALL,
    ): OfflineFirstProductRepository {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val configManager =
            ApiConfigManager().apply {
                updateBaseUrl(closedPortBaseUrl())
            }
        val apiService = ApiService(ApiClient(configManager))
        return OfflineFirstProductRepository(
            apiService = apiService,
            localStore = store,
            productDao = db.productDao(),
            networkMonitor = SettableNetworkMonitor(context, online),
            offlineScopeProvider = { scope },
        )
    }

    private suspend fun seed(vararg products: ProductDto) {
        db.productDao().insertAll(products.map { it.toEntity() })
    }

    private fun product(
        id: String,
        code: String,
        description: String,
        department: String = "0",
    ): ProductDto = ProductDto(id = id, code = code, description = description, department = department)
}
