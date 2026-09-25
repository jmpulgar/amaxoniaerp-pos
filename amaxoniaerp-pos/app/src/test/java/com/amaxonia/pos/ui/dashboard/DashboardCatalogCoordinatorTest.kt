package com.amaxonia.pos.ui.dashboard

import com.amaxonia.pos.domain.model.BestSellerProduct
import com.amaxonia.pos.domain.model.PriceLevel
import com.amaxonia.pos.domain.model.Product
import com.amaxonia.pos.domain.model.ProductStock
import com.amaxonia.pos.domain.model.ServerCountry
import com.amaxonia.pos.domain.model.SummaryStats
import com.amaxonia.pos.domain.repository.Department
import com.amaxonia.pos.domain.repository.DashboardSessionReader
import com.amaxonia.pos.domain.repository.ImageUrlResolver
import com.amaxonia.pos.domain.repository.InvoiceHistoryFilter
import com.amaxonia.pos.domain.repository.ProductRepository
import com.amaxonia.pos.domain.repository.ReportRepository
import com.amaxonia.pos.domain.repository.ServerEnvironment
import com.amaxonia.pos.test.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardCatalogCoordinatorTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private class FakeProductRepository : ProductRepository {
        val searchCalls = mutableListOf<String>()
        val allProductsCalls = mutableListOf<Int?>()
        var searchResult: Result<List<Product>> = Result.success(emptyList())
        var departmentsResult: Result<List<Department>> = Result.success(emptyList())

        override suspend fun getAllProducts(): Result<List<Product>> = Result.success(emptyList())

        override suspend fun getAllProducts(departmentId: Int?): Result<List<Product>> = Result.success(emptyList())

        override suspend fun getAllProducts(
            departmentId: Int?,
            page: Int,
            pageSize: Int,
            itemType: String?,
        ): Result<List<Product>> {
            allProductsCalls.add(departmentId)
            return Result.success(emptyList())
        }

        override suspend fun getProductById(id: String): Result<Product> =
            Result.failure(UnsupportedOperationException())

        override suspend fun getDepartments(): Result<List<Department>> = departmentsResult

        override suspend fun getSections(departmentId: Int): Result<List<Department>> = Result.success(emptyList())

        override suspend fun getFamilies(sectionId: Int): Result<List<Department>> = Result.success(emptyList())

        override suspend fun getSubFamilies(familyId: Int): Result<List<Department>> = Result.success(emptyList())

        override suspend fun getBrands(): Result<List<Department>> = Result.success(emptyList())

        override suspend fun getLines(brandId: Int): Result<List<Department>> = Result.success(emptyList())

        override suspend fun getProductStock(id: String): Result<ProductStock> =
            Result.failure(UnsupportedOperationException())

        override suspend fun saveProduct(product: Product): Result<Unit> = Result.success(Unit)

        override suspend fun deleteProduct(id: String): Result<Unit> = Result.success(Unit)

        override suspend fun searchProducts(query: String): Result<List<Product>> = Result.success(emptyList())

        override suspend fun searchProducts(
            query: String,
            departmentId: Int?,
            page: Int,
            pageSize: Int,
            itemType: String?,
        ): Result<List<Product>> {
            searchCalls.add(query)
            return searchResult
        }
    }

    private class FakeReportRepository : ReportRepository {
        override suspend fun getSummaryStats(filter: InvoiceHistoryFilter): Result<SummaryStats> =
            Result.failure(UnsupportedOperationException())

        override suspend fun getBestSellers(): Result<List<BestSellerProduct>> = Result.success(emptyList())
    }

    private class FakeSessionReader : DashboardSessionReader {
        override suspend fun currentAdminDatabase(): String = "admin_db"
        override suspend fun currentCountry(): ServerCountry? = null
    }

    private class FakeServerEnvironment : ServerEnvironment {
        override fun selectCountry(country: ServerCountry) {}
    }

    private object FixedImageResolver : ImageUrlResolver {
        override fun product(companyDatabase: String, photoPath: String): String = ""
        override fun client(companyDatabase: String, clientId: String, filename: String): String = ""
    }

    private fun testProduct(id: String, name: String) =
        Product(
            id = id,
            description = name,
            code = "C-$id",
            department = "Dept",
            prices = listOf(PriceLevel(label = "A", pricePlusTax = 10.0)),
        )

    @Test
    fun `SetSearchQuery debounces and executes search without premature empty products`() =
        runTest {
            val fakeRepo = FakeProductRepository()
            fakeRepo.searchResult = Result.success(listOf(testProduct("1", "Coca Cola")))

            val coordinator =
                DashboardCatalogCoordinator(
                    productRepository = fakeRepo,
                    reportRepository = FakeReportRepository(),
                    sessionReader = FakeSessionReader(),
                    serverEnvironment = FakeServerEnvironment(),
                    productMapper = DashboardProductMapper(FixedImageResolver),
                )

            val initialProduct = DashboardProduct(id = "old", name = "Old Product", price = 5.0)
            val state = MutableStateFlow(DashboardState(products = listOf(initialProduct)))

            // Trigger search query
            coordinator.onAction(DashboardCatalogUiAction.SetSearchQuery("Coca"), this, state)

            // Before debounce expires: previous product stays, no search executed yet
            advanceTimeBy(100)
            assertEquals("Coca", state.value.searchQuery)
            assertEquals(listOf(initialProduct), state.value.products)
            assertEquals(0, fakeRepo.searchCalls.size)

            // After debounce expires: search executes and updates products
            advanceTimeBy(200)
            advanceUntilIdle()

            assertEquals(1, fakeRepo.searchCalls.size)
            assertEquals("Coca", fakeRepo.searchCalls[0])
            assertEquals(1, state.value.products.size)
            assertEquals("Coca Cola", state.value.products[0].name)
            assertFalse(state.value.isLoading)
        }

    @Test
    fun `rapid SetSearchQuery keystrokes cancel previous searches`() =
        runTest {
            val fakeRepo = FakeProductRepository()
            fakeRepo.searchResult = Result.success(listOf(testProduct("2", "Pepsi")))

            val coordinator =
                DashboardCatalogCoordinator(
                    productRepository = fakeRepo,
                    reportRepository = FakeReportRepository(),
                    sessionReader = FakeSessionReader(),
                    serverEnvironment = FakeServerEnvironment(),
                    productMapper = DashboardProductMapper(FixedImageResolver),
                )

            val state = MutableStateFlow(DashboardState())

            coordinator.onAction(DashboardCatalogUiAction.SetSearchQuery("P"), this, state)
            advanceTimeBy(100)
            coordinator.onAction(DashboardCatalogUiAction.SetSearchQuery("Pe"), this, state)
            advanceTimeBy(100)
            coordinator.onAction(DashboardCatalogUiAction.SetSearchQuery("Pep"), this, state)
            advanceTimeBy(100)
            coordinator.onAction(DashboardCatalogUiAction.SetSearchQuery("Pepsi"), this, state)

            // No calls yet because debounce of 250ms was not reached between keystrokes
            assertEquals(0, fakeRepo.searchCalls.size)

            // Allow debounce to complete for the final query
            advanceTimeBy(300)
            advanceUntilIdle()

            assertEquals(1, fakeRepo.searchCalls.size)
            assertEquals("Pepsi", fakeRepo.searchCalls[0])
            assertEquals("Pepsi", state.value.products.single().name)
        }

    @Test
    fun `cancelled in-flight search does not set error state`() =
        runTest {
            val fakeRepo = FakeProductRepository()
            fakeRepo.searchResult = Result.failure(kotlinx.coroutines.CancellationException("StandaloneCoroutine was cancelled"))

            val coordinator =
                DashboardCatalogCoordinator(
                    productRepository = fakeRepo,
                    reportRepository = FakeReportRepository(),
                    sessionReader = FakeSessionReader(),
                    serverEnvironment = FakeServerEnvironment(),
                    productMapper = DashboardProductMapper(FixedImageResolver),
                )

            val state = MutableStateFlow(DashboardState())

            coordinator.onAction(DashboardCatalogUiAction.SetSearchQuery("First"), this, state)
            advanceTimeBy(300)
            advanceUntilIdle()

            assertEquals(null, state.value.error)
        }

    @Test
    fun `offlineScopeFlow emission auto-updates selectedDepartmentId and reloads catalog`() =
        runTest {
            val fakeRepo = FakeProductRepository()
            fakeRepo.departmentsResult = Result.success(
                listOf(
                    Department(id = 10, name = "Bebidas"),
                    Department(id = 20, name = "Comidas"),
                ),
            )
            fakeRepo.searchResult = Result.success(listOf(testProduct("1", "Agua Mineral")))

            val scopeFlow = MutableStateFlow(
                com.amaxonia.pos.domain.model.offline.OfflineScopeSelection(
                    departmentIds = setOf(10),
                ),
            )

            val coordinator =
                DashboardCatalogCoordinator(
                    productRepository = fakeRepo,
                    reportRepository = FakeReportRepository(),
                    sessionReader = FakeSessionReader(),
                    serverEnvironment = FakeServerEnvironment(),
                    productMapper = DashboardProductMapper(FixedImageResolver),
                    visibilityScopeFlow = scopeFlow,
                )

            val state = MutableStateFlow(DashboardState())
            coordinator.start(this, state)
            advanceUntilIdle()

            // Department 10 should be selected automatically as per the scope, and departments filtered to scope
            assertEquals(10, state.value.selectedDepartmentId)
            assertEquals("Bebidas", state.value.selectedCategory)
            assertEquals(1, state.value.departments.size)
            assertEquals(10, state.value.departments.first().id)
            assertEquals(listOf(10), fakeRepo.allProductsCalls)

            // When scope changes to department 20
            scopeFlow.value = com.amaxonia.pos.domain.model.offline.OfflineScopeSelection(
                departmentIds = setOf(20),
            )
            advanceUntilIdle()

            assertEquals(20, state.value.selectedDepartmentId)
            assertEquals("Comidas", state.value.selectedCategory)
            assertEquals(1, state.value.departments.size)
            assertEquals(20, state.value.departments.first().id)
            assertEquals(listOf(10, 20), fakeRepo.allProductsCalls)

            coordinator.stop()
        }

    @Test
    fun `RefreshCatalog action reloads departments and products`() =
        runTest {
            val fakeRepo = FakeProductRepository()
            fakeRepo.departmentsResult = Result.success(listOf(Department(id = 5, name = "Postres")))

            val coordinator =
                DashboardCatalogCoordinator(
                    productRepository = fakeRepo,
                    reportRepository = FakeReportRepository(),
                    sessionReader = FakeSessionReader(),
                    serverEnvironment = FakeServerEnvironment(),
                    productMapper = DashboardProductMapper(FixedImageResolver),
                )

            val state = MutableStateFlow(DashboardState(selectedDepartmentId = 5, selectedCategory = "Postres"))
            coordinator.start(this, state)
            advanceUntilIdle()

            assertEquals(1, fakeRepo.allProductsCalls.size)

            coordinator.onAction(DashboardCatalogUiAction.RefreshCatalog, this, state)
            advanceUntilIdle()

            assertEquals(2, fakeRepo.allProductsCalls.size)
            assertEquals(1, state.value.departments.size)
            assertEquals(5, state.value.selectedDepartmentId)
            assertEquals("Postres", state.value.selectedCategory)
        }
}
