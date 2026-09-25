package com.amaxonia.pos.ui.products

import com.amaxonia.pos.domain.model.Product
import com.amaxonia.pos.domain.model.ProductStock
import com.amaxonia.pos.domain.repository.ImageUrlResolver
import com.amaxonia.pos.domain.repository.ProductCatalogReader
import com.amaxonia.pos.domain.repository.ProductSessionReader
import com.amaxonia.pos.test.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProductListViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val sampleProduct = Product(
        id = "prod-1",
        description = "Café Molido",
        reference = "REF-001",
        code = "123456",
        photoUrl = "photo.jpg",
    )

    private class FakeProductCatalogReader(
        private val searchResults: List<Product> = emptyList(),
    ) : ProductCatalogReader {
        var lastSearchQuery: String? = null

        override suspend fun getAllProducts(): Result<List<Product>> = Result.success(emptyList())

        override suspend fun getAllProducts(departmentId: Int?): Result<List<Product>> = Result.success(emptyList())

        override suspend fun getAllProducts(page: Int, pageSize: Int): Result<List<Product>> =
            Result.success(emptyList())

        override suspend fun getProductById(id: String): Result<Product> =
            Result.failure(NoSuchElementException())

        override suspend fun getProductStock(id: String): Result<ProductStock> =
            Result.success(ProductStock(itemId = id, stockTotalDisponible = 10.0, almacenes = emptyList()))

        override suspend fun searchProducts(query: String): Result<List<Product>> =
            Result.success(emptyList())

        override suspend fun searchProducts(query: String, page: Int, pageSize: Int): Result<List<Product>> {
            lastSearchQuery = query
            return Result.success(searchResults)
        }
    }

    private class FakeProductSessionReader : ProductSessionReader {
        override suspend fun currentAdminDatabase(): String = "test_admin_db"
    }

    private class FakeImageUrlResolver : ImageUrlResolver {
        override fun product(companyDatabase: String, photoPath: String): String =
            "http://img/$companyDatabase/$photoPath"

        override fun client(companyDatabase: String, clientId: String, filename: String): String = ""
    }

    @Test
    fun `onSearchQueryChange updates state and executes search query`() = runTest {
        val catalogReader = FakeProductCatalogReader(searchResults = listOf(sampleProduct))
        val viewModel = ProductListViewModel(
            productRepository = catalogReader,
            sessionReader = FakeProductSessionReader(),
            imageUrlResolver = FakeImageUrlResolver(),
        )
        advanceUntilIdle()

        viewModel.onSearchQueryChange("Café")
        assertEquals("Café", viewModel.state.value.searchQuery)

        advanceUntilIdle()

        assertEquals("Café", catalogReader.lastSearchQuery)
        assertEquals(listOf(sampleProduct), viewModel.state.value.products)
        assertFalse(viewModel.state.value.isLoading)
    }

    @Test
    fun `rapid onSearchQueryChange cancels previous search and finishes with latest query results`() = runTest {
        val catalogReader = FakeProductCatalogReader(searchResults = listOf(sampleProduct))
        val viewModel = ProductListViewModel(
            productRepository = catalogReader,
            sessionReader = FakeProductSessionReader(),
            imageUrlResolver = FakeImageUrlResolver(),
        )
        advanceUntilIdle()

        viewModel.onSearchQueryChange("C")
        viewModel.onSearchQueryChange("Ca")
        viewModel.onSearchQueryChange("Café")

        advanceUntilIdle()

        assertEquals("Café", viewModel.state.value.searchQuery)
        assertEquals("Café", catalogReader.lastSearchQuery)
        assertFalse(viewModel.state.value.isLoading)
    }
}
