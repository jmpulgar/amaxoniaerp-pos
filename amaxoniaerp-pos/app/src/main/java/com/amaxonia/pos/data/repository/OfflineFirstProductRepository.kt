package com.amaxonia.pos.data.repository

import com.amaxonia.pos.data.local.AppJson
import com.amaxonia.pos.data.local.LocalStore
import com.amaxonia.pos.data.local.db.ProductDao
import com.amaxonia.pos.data.local.db.ProductOptionsDao
import com.amaxonia.pos.data.local.db.ProductOptionsEntity
import com.amaxonia.pos.data.local.db.toDomain
import com.amaxonia.pos.data.local.db.toEntity
import com.amaxonia.pos.data.local.readCompanySession
import com.amaxonia.pos.data.remote.ApiService
import com.amaxonia.pos.data.remote.NetworkMonitor
import com.amaxonia.pos.data.remote.createProduct
import com.amaxonia.pos.data.remote.getBrands
import com.amaxonia.pos.data.remote.getDepartments
import com.amaxonia.pos.data.remote.getFamilies
import com.amaxonia.pos.data.remote.getItemOptions
import com.amaxonia.pos.data.remote.getItemStock
import com.amaxonia.pos.data.remote.getLines
import com.amaxonia.pos.data.remote.getProductById
import com.amaxonia.pos.data.remote.getSections
import com.amaxonia.pos.data.remote.getSubFamilies
import com.amaxonia.pos.data.remote.updateProduct
import com.amaxonia.pos.data.sync.OfflineSyncScope
import com.amaxonia.pos.domain.model.Product
import com.amaxonia.pos.domain.model.ProductStock
import com.amaxonia.pos.domain.model.options.ProductOptionGroup
import com.amaxonia.pos.domain.repository.Department
import com.amaxonia.pos.domain.repository.ImageUrlResolver
import com.amaxonia.pos.domain.repository.ProductRepository
import kotlinx.serialization.encodeToString

/**
 * Repositorio offline-first de productos. La política offline/online y la
 * lectura de caché viven en [ProductFetchPolicy]/[ProductPageCache]; aquí
 * queda el mapeo de la interfaz y los flujos con estado propio (producto por
 * id, stock, guardado).
 */
class OfflineFirstProductRepository(
    apiService: ApiService,
    localStore: LocalStore,
    productDao: ProductDao,
    networkMonitor: NetworkMonitor,
    offlineScopeProvider: suspend () -> OfflineSyncScope = { OfflineSyncScope.ALL },
    imageUrlResolver: ImageUrlResolver? = null,
    private val productOptionsDao: ProductOptionsDao? = null,
) : ProductRepository {
    private val fetch = ProductFetchPolicy(apiService, localStore, productDao, networkMonitor, offlineScopeProvider)
    private val cache = ProductPageCache(productDao)
    private val localStore = localStore
    private val apiService = apiService
    private val productDao = productDao
    private val networkMonitor = networkMonitor
    private val offlineScopeProvider = offlineScopeProvider
    private val imageUrlResolver = imageUrlResolver

    override suspend fun getDepartments(): Result<List<Department>> =
        fetch.onlineCatalog { token ->
            apiService.getDepartments(token).map { Department(it.id, it.name) }
        }

    override suspend fun getSections(departmentId: Int): Result<List<Department>> =
        fetch.onlineCatalog { token ->
            apiService.getSections(token, departmentId).map { Department(it.id, it.name) }
        }

    override suspend fun getFamilies(sectionId: Int): Result<List<Department>> =
        fetch.onlineCatalog { token ->
            apiService.getFamilies(token, sectionId).map { Department(it.id, it.name) }
        }

    override suspend fun getSubFamilies(familyId: Int): Result<List<Department>> =
        fetch.onlineCatalog { token ->
            apiService.getSubFamilies(token, familyId).map { Department(it.id, it.name) }
        }

    override suspend fun getBrands(): Result<List<Department>> =
        fetch.onlineCatalog { token ->
            apiService.getBrands(token).map { Department(it.id, it.name) }
        }

    override suspend fun getLines(brandId: Int): Result<List<Department>> =
        fetch.onlineCatalog { token ->
            apiService.getLines(token, brandId).map { Department(it.id, it.name) }
        }

    override suspend fun getAllProducts(
        page: Int,
        pageSize: Int,
    ): Result<List<Product>> =
        fetch.pageWithCacheFallback(
            ProductFetchQuery(limit = pageSize, offset = pageOffset(page, pageSize)),
        )

    override suspend fun getAllProducts(): Result<List<Product>> = getAllProducts(null)

    override suspend fun getAllProducts(departmentId: Int?): Result<List<Product>> {
        val scope = offlineScopeProvider()
        if (!networkMonitor.isOnline()) {
            val cached = cache.fullCatalog(scope)
            return if (cached.isNotEmpty()) Result.success(cached) else Result.failure(IllegalStateException(NO_COMPANY_ERROR))
        }
        return fetch
            .pageWithCacheFallback(
                ProductFetchQuery(limit = FULL_CATALOG_PAGE_SIZE, offset = 0, departmentId = departmentId),
            ).recoverCatching { error -> cache.fullCatalog(scope).ifEmpty { throw error } }
    }

    override suspend fun getAllProducts(
        page: Int,
        pageSize: Int,
        filter: com.amaxonia.pos.domain.repository.ProductCatalogFilter?,
    ): Result<List<Product>> =
        fetch.pageWithCacheFallback(
            ProductFetchQuery(
                limit = pageSize,
                offset = pageOffset(page, pageSize),
                departmentId = filter?.departmentId,
                itemType = filter?.itemType,
                warehouseIds = filter?.warehouseIds,
            ),
        )

    override suspend fun getAllProducts(
        departmentId: Int?,
        page: Int,
        pageSize: Int,
        itemType: String?,
    ): Result<List<Product>> =
        getAllProducts(
            page = page,
            pageSize = pageSize,
            filter =
                com.amaxonia.pos.domain.repository.ProductCatalogFilter(
                    departmentId = departmentId,
                    itemType = itemType,
                ),
        )

    override suspend fun getProductById(id: String): Result<Product> {
        val token = localStore.readCompanySession()?.token
        val cachedProduct = productDao.getById(id)?.toDomain()

        return when {
            !token.isNullOrBlank() && networkMonitor.isOnline() ->
                runCatching {
                    val remoteProduct = apiService.getProductById(token, id)
                    productDao.insertAll(listOf(remoteProduct.toEntity()))
                    remoteProduct.toDomain()
                }.recoverCatching {
                    cachedProduct ?: throw it
                }
            cachedProduct != null -> Result.success(cachedProduct)
            else -> Result.failure(IllegalArgumentException("Producto no encontrado"))
        }
    }

    override suspend fun getProductStock(id: String): Result<ProductStock> {
        val token = localStore.readCompanySession()?.token
        return when {
            token.isNullOrBlank() -> Result.failure(IllegalStateException(NO_COMPANY_ERROR))
            !networkMonitor.isOnline() -> Result.failure(IllegalStateException("Sin conexión para consultar stock por almacén"))
            else -> runCatching { apiService.getItemStock(token, id).toDomain() }
        }
    }

    override suspend fun getProductOptions(
        productId: String,
        almacenId: Int?,
    ): Result<List<ProductOptionGroup>> {
        val session = localStore.readCompanySession()
        val token = session?.token
        val adminDb = session?.company?.adminDb.orEmpty()
        val isOnline = networkMonitor.isOnline() && !token.isNullOrBlank()

        if (isOnline) {
            val remoteResult =
                runCatching {
                    apiService.getItemOptions(token!!, productId, almacenId).grupos.map {
                        it.toDomain(adminDb = adminDb, imageUrlResolver = imageUrlResolver)
                    }
                }
            if (remoteResult.isSuccess) {
                val groups = remoteResult.getOrThrow()
                productOptionsDao?.let { dao ->
                    runCatching {
                        val json = AppJson.encodeToString(groups)
                        dao.upsert(ProductOptionsEntity(productId, json, System.currentTimeMillis()))
                    }
                }
                return remoteResult
            }
        }

        // Fallback offline: buscar en la base de datos local
        val cached = productOptionsDao?.getByProductId(productId)
        if (cached != null) {
            val groups =
                runCatching {
                    AppJson.decodeFromString<List<ProductOptionGroup>>(cached.optionsJson)
                }.getOrNull()
            if (!groups.isNullOrEmpty()) {
                return Result.success(groups)
            }
        }

        return Result.failure(
            IllegalStateException("No hay opciones sincronizadas para este producto en modo offline"),
        )
    }

    override suspend fun searchProducts(query: String): Result<List<Product>> =
        fetch.pageWithCacheFallback(
            ProductFetchQuery(limit = SEARCH_PAGE_SIZE, offset = 0, query = query),
        )

    override suspend fun searchProducts(
        query: String,
        page: Int,
        pageSize: Int,
    ): Result<List<Product>> =
        fetch.pageWithCacheFallback(
            ProductFetchQuery(limit = pageSize, offset = pageOffset(page, pageSize), query = query),
        )

    override suspend fun searchProducts(
        query: String,
        page: Int,
        pageSize: Int,
        filter: com.amaxonia.pos.domain.repository.ProductCatalogFilter?,
    ): Result<List<Product>> {
        // Escaneo de código de barras (hot path): match exacto indexado
        // primero, offline y en <50ms; si no hay match, búsqueda normal.
        val trimmed = query.trim()
        if (trimmed.length >= MIN_BARCODE_LENGTH && trimmed.all { it.isDigit() }) {
            productDao.getByBarcode(trimmed)?.let { exact ->
                val exactProduct = exact.toDomain()
                val matchesType =
                    when (filter?.itemType?.uppercase()) {
                        "SERVICE" -> exactProduct.isService
                        "PRODUCT" -> !exactProduct.isService
                        else -> true
                    }
                if (matchesType) {
                    return Result.success(listOf(exactProduct))
                }
            }
        }
        return fetch.pageWithCacheFallback(
            ProductFetchQuery(
                limit = pageSize,
                offset = pageOffset(page, pageSize),
                query = query,
                departmentId = filter?.departmentId,
                itemType = filter?.itemType,
                warehouseIds = filter?.warehouseIds,
            ),
        )
    }

    override suspend fun searchProducts(
        query: String,
        departmentId: Int?,
        page: Int,
        pageSize: Int,
        itemType: String?,
    ): Result<List<Product>> =
        searchProducts(
            query = query,
            page = page,
            pageSize = pageSize,
            filter =
                com.amaxonia.pos.domain.repository.ProductCatalogFilter(
                    departmentId = departmentId,
                    itemType = itemType,
                ),
        )

    override suspend fun saveProduct(product: Product): Result<Unit> {
        val token =
            localStore.readCompanySession()?.token
                ?: return Result.failure(IllegalStateException(NO_COMPANY_ERROR))
        val request = product.toCreateRequest()
        return runCatching {
            val id = product.id.toIntOrNull()
            val saved =
                if (id == null) {
                    apiService.createProduct(token, request)
                } else {
                    apiService.updateProduct(token, id, request)
                }
            productDao.insertAll(listOf(saved.toEntity()))
        }
    }

    override suspend fun deleteProduct(id: String): Result<Unit> =
        Result.failure(UnsupportedOperationException("Eliminar productos no esta implementado"))

    private fun pageOffset(
        page: Int,
        pageSize: Int,
    ): Int = (page - 1).coerceAtLeast(0) * pageSize

    private companion object {
        const val MIN_BARCODE_LENGTH = 6

        const val NO_COMPANY_ERROR = "No hay empresa seleccionada"
        const val FULL_CATALOG_PAGE_SIZE = 500
        const val SEARCH_PAGE_SIZE = 100
    }
}
