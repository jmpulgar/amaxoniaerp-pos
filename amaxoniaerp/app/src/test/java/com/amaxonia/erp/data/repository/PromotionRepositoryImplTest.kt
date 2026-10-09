package com.amaxonia.erp.data.repository

import android.content.ContextWrapper
import com.amaxonia.erp.data.local.LocalStore
import com.amaxonia.erp.data.local.db.ProductDao
import com.amaxonia.erp.data.local.db.ProductEntity
import com.amaxonia.erp.data.local.db.PromocionDao
import com.amaxonia.erp.data.local.db.PromocionDetalleEntity
import com.amaxonia.erp.data.local.db.PromocionEntity
import com.amaxonia.erp.data.remote.ApiClient
import com.amaxonia.erp.data.remote.ApiService
import com.amaxonia.erp.data.remote.NetworkMonitor
import com.amaxonia.erp.data.remote.dto.DepartmentDto
import com.amaxonia.erp.domain.model.AuthUser
import com.amaxonia.erp.domain.model.CompanySession
import com.amaxonia.erp.domain.model.Product
import com.amaxonia.erp.domain.model.SelectedCompany
import com.amaxonia.erp.domain.repository.ProductRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal

class PromotionRepositoryImplTest {

    private class TestLocalStore : LocalStore(ContextWrapper(null)) {
        var companySession: CompanySession? = CompanySession(
            token = "test-token",
            company = SelectedCompany(id = 10, name = "Test Company", adminDb = "test_db"),
            user = AuthUser(id = 1, username = "cashier", role = "admin"),
        )
        override suspend fun readCompanySession(): CompanySession? = companySession
    }

    private class FakeProductRepository : ProductRepository {
        var products: List<Product> = emptyList()

        override suspend fun getAllProducts(page: Int, pageSize: Int, departmentId: Int?): Result<List<Product>> =
            Result.success(products)

        override suspend fun searchProducts(query: String, page: Int, pageSize: Int): Result<List<Product>> =
            Result.success(emptyList())

        override suspend fun createProduct(product: Product, departmentId: Int): Result<Product> =
            Result.success(product)

        override suspend fun updateProduct(id: String, product: Product, departmentId: Int): Result<Product> =
            Result.success(product)

        override suspend fun getDepartments(): Result<List<DepartmentDto>> =
            Result.success(emptyList())
    }

    private class FakePromocionDao : PromocionDao {
        val promociones = mutableListOf<PromocionEntity>()
        val detalles = mutableListOf<PromocionDetalleEntity>()

        override suspend fun upsertPromociones(items: List<PromocionEntity>) {
            promociones.addAll(items)
        }

        override suspend fun upsertDetalles(items: List<PromocionDetalleEntity>) {
            detalles.addAll(items)
        }

        override suspend fun deletePromocionById(id: String) {
            promociones.removeAll { it.id == id }
        }

        override suspend fun deleteDetalleById(id: String) {
            detalles.removeAll { it.id == id }
        }

        override suspend fun clearPromociones() {
            promociones.clear()
        }

        override suspend fun clearDetalles() {
            detalles.clear()
        }

        override suspend fun getAllActive(): List<PromocionEntity> =
            promociones.filter { it.activo }

        override suspend fun getDetallesForPromocion(promocionId: String): List<PromocionDetalleEntity> =
            detalles.filter { it.promocionId == promocionId }
    }

    private class FakeProductDao : ProductDao {
        val products = mutableMapOf<String, ProductEntity>()

        override suspend fun insertAll(items: List<ProductEntity>) {
            items.forEach { products[it.id] = it }
        }
        override suspend fun upsertAll(items: List<ProductEntity>) {
            items.forEach { products[it.id] = it }
        }
        override suspend fun deleteById(id: String) {
            products.remove(id)
        }
        override suspend fun deleteByDepartmentsNotIn(departmentIds: List<Int>) {}
        override suspend fun clearAll() {
            products.clear()
        }
        override suspend fun getById(id: String): ProductEntity? = products[id]
        override suspend fun getByBarcode(code: String): ProductEntity? = null
        override suspend fun getPaged(limit: Int, offset: Int, isService: Int?): List<ProductEntity> = emptyList()
        override suspend fun getPagedByDepartment(departmentId: Int, limit: Int, offset: Int, isService: Int?): List<ProductEntity> = emptyList()
        override suspend fun getPagedByDepartments(departmentIds: List<Int>, limit: Int, offset: Int, isService: Int?): List<ProductEntity> = emptyList()
        override suspend fun searchPaged(query: String, limit: Int, offset: Int, isService: Int?): List<ProductEntity> = emptyList()
        override suspend fun searchPagedByDepartment(query: String, departmentId: Int, limit: Int, offset: Int, isService: Int?): List<ProductEntity> = emptyList()
        override suspend fun searchPagedByDepartments(query: String, departmentIds: List<Int>, limit: Int, offset: Int, isService: Int?): List<ProductEntity> = emptyList()
        override suspend fun count(): Int = products.size
        override suspend fun countByDepartment(departmentId: Int): Int = 0
        override suspend fun countSearch(query: String): Int = 0
        override suspend fun countSearchByDepartment(query: String, departmentId: Int): Int = 0
        override suspend fun getDistinctDepartmentIds(): List<Int> = emptyList()
    }

    private class FakeNetworkMonitor(var online: Boolean) : NetworkMonitor(ContextWrapper(null)) {
        override fun isOnline(): Boolean = online
    }

    private lateinit var localStore: TestLocalStore
    private lateinit var productRepository: FakeProductRepository
    private lateinit var promocionDao: FakePromocionDao
    private lateinit var productDao: FakeProductDao
    private lateinit var networkMonitor: FakeNetworkMonitor
    private lateinit var apiService: ApiService
    private lateinit var repository: PromotionRepositoryImpl

    @Before
    fun setUp() {
        localStore = TestLocalStore()
        productRepository = FakeProductRepository()
        promocionDao = FakePromocionDao()
        productDao = FakeProductDao()
        networkMonitor = FakeNetworkMonitor(online = false)
        apiService = ApiService(ApiClient("http://localhost:59999"))
        repository = PromotionRepositoryImpl(
            apiService = apiService,
            localStore = localStore,
            productRepository = productRepository,
            promocionDao = promocionDao,
            productDao = productDao,
            networkMonitor = networkMonitor,
        )
    }

    @Test
    fun getPromotions_whenOffline_loadsActivePromotionsFromRoom() = runTest {
        val promo = PromocionEntity(
            id = "PROMO-1",
            codigo = "P001",
            nombre = "Combo Navideño",
            imagen = "",
            descuentoGlobal = 15.0,
            idItem = "ITEM-10",
            activo = true,
        )
        val detalle = PromocionDetalleEntity(
            id = "DET-1",
            promocionId = "PROMO-1",
            idItem = "ITEM-10",
            idTipoPrecio = "1",
            cantidad = 2.0,
            cantidadTotal = 2.0,
            unidadEmpaque = "UND",
            descuento = 15.0,
            descuentoMonto = 3.0,
            precio = 10.0,
            impuesto = 1.6,
            impuestoPorcentaje = 16.0,
            importe = 20.0,
            grupo = "G1",
        )
        promocionDao.upsertPromociones(listOf(promo))
        promocionDao.upsertDetalles(listOf(detalle))

        productRepository.products = listOf(
            Product(id = "ITEM-10", description = "Panettone", code = "PAN01")
        )

        val result = repository.getPromotions(forceRefresh = true)

        assertTrue(result.isSuccess)
        val list = result.getOrNull().orEmpty()
        assertEquals(1, list.size)
        assertEquals("PROMO-1", list[0].id)
        assertEquals("Combo Navideño", list[0].nombre)
        assertEquals(BigDecimal.valueOf(15.0), list[0].descuentoGlobal)
        assertEquals(1, list[0].detalles.size)
        assertEquals("Panettone", list[0].detalles[0].productName)
        assertEquals("PAN01", list[0].detalles[0].productCode)
    }

    @Test
    fun getActivePromotionsForProduct_whenOffline_filtersCorrectly() = runTest {
        val promo = PromocionEntity(
            id = "PROMO-2",
            codigo = "P002",
            nombre = "Promo 2x1",
            imagen = "",
            descuentoGlobal = 50.0,
            idItem = "ITEM-99",
            activo = true,
        )
        promocionDao.upsertPromociones(listOf(promo))

        val result = repository.getActivePromotionsForProduct("ITEM-99")

        assertTrue(result.isSuccess)
        val list = result.getOrNull().orEmpty()
        assertEquals(1, list.size)
        assertEquals("PROMO-2", list[0].id)
    }
}
