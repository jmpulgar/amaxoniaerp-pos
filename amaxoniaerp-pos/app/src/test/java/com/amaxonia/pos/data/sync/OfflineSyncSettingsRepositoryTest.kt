package com.amaxonia.pos.data.sync

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.amaxonia.pos.data.local.LocalStore
import com.amaxonia.pos.data.local.db.AppDatabase
import com.amaxonia.pos.data.remote.ApiClient
import com.amaxonia.pos.data.remote.ApiConfigManager
import com.amaxonia.pos.data.remote.ApiService
import com.amaxonia.pos.data.remote.SyncApi
import com.amaxonia.pos.data.repository.FakeSecureKeyValueStore
import com.amaxonia.pos.domain.repository.Department
import com.amaxonia.pos.domain.repository.ProductRepository
import com.amaxonia.pos.domain.model.Product
import com.amaxonia.pos.domain.model.ProductStock
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OfflineSyncSettingsRepositoryTest {
    private lateinit var db: AppDatabase
    private lateinit var store: LocalStore
    private lateinit var scopeStore: OfflineSyncSettingsStore
    private lateinit var repository: OfflineSyncSettingsRepositoryImpl
    private var bootstrapEnqueued = false

    private val fakeProductRepository = object : ProductRepository {
        override suspend fun getDepartments(): Result<List<Department>> =
            Result.success(listOf(Department(1, "Bebidas"), Department(2, "Snacks")))
        override suspend fun getSections(departmentId: Int): Result<List<Department>> = Result.success(emptyList())
        override suspend fun getFamilies(sectionId: Int): Result<List<Department>> = Result.success(emptyList())
        override suspend fun getSubFamilies(familyId: Int): Result<List<Department>> = Result.success(emptyList())
        override suspend fun getBrands(): Result<List<Department>> = Result.success(emptyList())
        override suspend fun getLines(brandId: Int): Result<List<Department>> = Result.success(emptyList())
        override suspend fun getAllProducts(page: Int, pageSize: Int): Result<List<Product>> = Result.success(emptyList())
        override suspend fun getAllProducts(): Result<List<Product>> = Result.success(emptyList())
        override suspend fun getAllProducts(departmentId: Int?): Result<List<Product>> = Result.success(emptyList())
        override suspend fun getAllProducts(departmentId: Int?, page: Int, pageSize: Int): Result<List<Product>> = Result.success(emptyList())
        override suspend fun getProductById(id: String): Result<Product> = Result.failure(NotImplementedError())
        override suspend fun getProductStock(id: String): Result<ProductStock> = Result.failure(NotImplementedError())
        override suspend fun searchProducts(query: String): Result<List<Product>> = Result.success(emptyList())
        override suspend fun saveProduct(product: Product): Result<Unit> = Result.success(Unit)
        override suspend fun deleteProduct(id: String): Result<Unit> = Result.success(Unit)
    }

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        store = LocalStore(context, FakeSecureKeyValueStore())
        scopeStore = OfflineSyncSettingsStore(context)
        val apiConfig = ApiConfigManager()
        val syncApi = SyncApi(ApiService(ApiClient(apiConfig)))
        bootstrapEnqueued = false

        repository = OfflineSyncSettingsRepositoryImpl(
            database = db,
            syncApi = syncApi,
            localStore = store,
            productRepository = fakeProductRepository,
            scopeStore = scopeStore,
            bootstrapEnqueuer = { bootstrapEnqueued = true },
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `toggleDepartment desactiva productModeAll y agrega el departamento seleccionado`() {
        repository.setProductModeAll(true)
        assertTrue(repository.uiState.value.productModeAll)

        repository.toggleDepartment(1)

        assertFalse(repository.uiState.value.productModeAll)
        assertEquals(setOf(1), repository.uiState.value.selectedDepartmentIds)
    }

    @Test
    fun `setProductModeAll a true limpia los departamentos seleccionados`() {
        repository.toggleDepartment(1)
        repository.toggleDepartment(2)
        assertEquals(setOf(1, 2), repository.uiState.value.selectedDepartmentIds)
        assertFalse(repository.uiState.value.productModeAll)

        repository.setProductModeAll(true)

        assertTrue(repository.uiState.value.productModeAll)
        assertTrue(repository.uiState.value.selectedDepartmentIds.isEmpty())
    }

    @Test
    fun `toggleSucursal desactiva clientModeAll y agrega la sucursal seleccionada`() {
        repository.setClientModeAll(true)
        assertTrue(repository.uiState.value.clientModeAll)

        repository.toggleSucursal("10")

        assertFalse(repository.uiState.value.clientModeAll)
        assertEquals(setOf("10"), repository.uiState.value.selectedSucursalIds)
    }

    @Test
    fun `setClientModeAll a true limpia las sucursales seleccionadas`() {
        repository.toggleSucursal("10")
        assertFalse(repository.uiState.value.clientModeAll)

        repository.setClientModeAll(true)

        assertTrue(repository.uiState.value.clientModeAll)
        assertTrue(repository.uiState.value.selectedSucursalIds.isEmpty())
    }

    @Test
    fun `selectAllDepartments asigna conjunto y desactiva productModeAll`() {
        repository.setProductModeAll(true)
        repository.selectAllDepartments(setOf(1, 2, 3))

        assertFalse(repository.uiState.value.productModeAll)
        assertEquals(setOf(1, 2, 3), repository.uiState.value.selectedDepartmentIds)

        repository.clearDepartments()
        assertFalse(repository.uiState.value.productModeAll)
        assertTrue(repository.uiState.value.selectedDepartmentIds.isEmpty())
    }

    @Test
    fun `selectAllSucursales asigna conjunto y desactiva clientModeAll`() {
        repository.setClientModeAll(true)
        repository.selectAllSucursales(setOf("1", "2"))

        assertFalse(repository.uiState.value.clientModeAll)
        assertEquals(setOf("1", "2"), repository.uiState.value.selectedSucursalIds)

        repository.clearSucursales()
        assertFalse(repository.uiState.value.clientModeAll)
        assertTrue(repository.uiState.value.selectedSucursalIds.isEmpty())
    }
}
