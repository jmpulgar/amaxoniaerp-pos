package com.amaxonia.pos.data.repository

import com.amaxonia.pos.data.local.LocalStore
import com.amaxonia.pos.data.local.db.ProductDao
import com.amaxonia.pos.data.local.db.ProductEntity
import com.amaxonia.pos.data.local.db.toDomain
import com.amaxonia.pos.data.local.db.toEntity
import com.amaxonia.pos.data.local.readCompanySession
import com.amaxonia.pos.data.remote.ApiService
import com.amaxonia.pos.data.remote.CatalogPage
import com.amaxonia.pos.data.remote.NetworkMonitor
import com.amaxonia.pos.data.remote.getProducts
import com.amaxonia.pos.data.sync.OfflineSyncScope
import com.amaxonia.pos.domain.model.Product
import kotlinx.coroutines.CancellationException

internal data class ProductFetchQuery(
    val limit: Int,
    val offset: Int,
    val departmentId: Int? = null,
    val query: String? = null,
    val itemType: String? = null,
    val warehouseIds: List<Int>? = null,
)

/**
 * Lector de páginas cacheadas de productos (Room) usado como fallback offline
 * del repositorio. Extraído de `OfflineFirstProductRepository` para mantener
 * la política de caché en un solo lugar.
 */
internal class ProductPageCache(
    private val productDao: ProductDao,
) {
    /** Lee la página cacheada según departamento/búsqueda (LIKE normalizado). */
    suspend fun page(
        fetchQuery: ProductFetchQuery,
        scope: OfflineSyncScope = OfflineSyncScope.ALL,
    ): List<Product> {
        val isServiceInt =
            when (fetchQuery.itemType?.uppercase()) {
                "SERVICE", "SERVICIO" -> 1
                "PRODUCT", "PRODUCTO" -> 0
                else -> null
            }
        val normalized =
            fetchQuery.query
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?.let { "%$it%" }
        val entities = queryPagedEntities(fetchQuery, normalized, isServiceInt, scope)
        return entities.map { it.toDomain() }
    }

    private suspend fun queryPagedEntities(
        fetchQuery: ProductFetchQuery,
        normalized: String?,
        isServiceInt: Int?,
        scope: OfflineSyncScope,
    ): List<ProductEntity> =
        if (normalized != null) {
            queryPagedWithNormalized(fetchQuery, normalized, isServiceInt, scope)
        } else {
            queryPagedWithoutNormalized(fetchQuery, isServiceInt, scope)
        }

    private suspend fun queryPagedWithNormalized(
        fetchQuery: ProductFetchQuery,
        normalized: String,
        isServiceInt: Int?,
        scope: OfflineSyncScope,
    ): List<ProductEntity> =
        when {
            fetchQuery.departmentId != null ->
                productDao.searchPagedByDepartment(
                    query = normalized,
                    departmentId = fetchQuery.departmentId,
                    limit = fetchQuery.limit,
                    offset = fetchQuery.offset,
                    isService = isServiceInt,
                )
            !scope.allProducts ->
                productDao.searchPagedByDepartments(
                    query = normalized,
                    departmentIds = scope.departmentIds.toList(),
                    limit = fetchQuery.limit,
                    offset = fetchQuery.offset,
                    isService = isServiceInt,
                )
            else ->
                productDao.searchPaged(
                    query = normalized,
                    limit = fetchQuery.limit,
                    offset = fetchQuery.offset,
                    isService = isServiceInt,
                )
        }

    private suspend fun queryPagedWithoutNormalized(
        fetchQuery: ProductFetchQuery,
        isServiceInt: Int?,
        scope: OfflineSyncScope,
    ): List<ProductEntity> =
        when {
            fetchQuery.departmentId != null ->
                productDao.getPagedByDepartment(
                    departmentId = fetchQuery.departmentId,
                    limit = fetchQuery.limit,
                    offset = fetchQuery.offset,
                    isService = isServiceInt,
                )
            !scope.allProducts ->
                productDao.getPagedByDepartments(
                    departmentIds = scope.departmentIds.toList(),
                    limit = fetchQuery.limit,
                    offset = fetchQuery.offset,
                    isService = isServiceInt,
                )
            else ->
                productDao.getPaged(
                    limit = fetchQuery.limit,
                    offset = fetchQuery.offset,
                    isService = isServiceInt,
                )
        }

    /** Catálogo completo cacheado (hasta [FULL_CACHE_SIZE] filas). */
    suspend fun fullCatalog(scope: OfflineSyncScope = OfflineSyncScope.ALL): List<Product> =
        if (scope.allProducts) {
            productDao.getPaged(limit = FULL_CACHE_SIZE, offset = 0).map { it.toDomain() }
        } else {
            productDao.getPagedByDepartments(scope.departmentIds.toList(), limit = FULL_CACHE_SIZE, offset = 0).map { it.toDomain() }
        }

    private companion object {
        const val FULL_CACHE_SIZE = 1000
    }
}

/**
 * Política offline/online de productos: online consulta la API y refresca la
 * caché Room; offline (o ante fallo remoto) sirve la caché local. Extraída del
 * repositorio para mantener una única implementación de la política.
 */
internal class ProductFetchPolicy(
    private val apiService: ApiService,
    private val localStore: LocalStore,
    private val productDao: ProductDao,
    networkMonitor: NetworkMonitor,
    private val offlineScopeProvider: suspend () -> OfflineSyncScope = { OfflineSyncScope.ALL },
) {
    private val cache = ProductPageCache(productDao)
    private val networkMonitor = networkMonitor

    /** Catálogos sin caché local: exige token y conexión, o falla con mensaje de negocio. */
    suspend fun <T> onlineCatalog(block: suspend (String) -> List<T>): Result<List<T>> {
        val token = localStore.readCompanySession()?.token
        return when {
            token.isNullOrBlank() -> Result.failure(IllegalStateException(NO_COMPANY_ERROR))
            !networkMonitor.isOnline() -> Result.failure(IllegalStateException("Sin conexión"))
            else -> runCatching { block(token) }
        }
    }

    /**
     * Página de productos (con departamento/búsqueda opcional): online refresca
     * caché y devuelve remoto; offline (o si el remoto falla) sirve la caché.
     */
    suspend fun pageWithCacheFallback(fetchQuery: ProductFetchQuery): Result<List<Product>> =
        when {
            !networkMonitor.isOnline() -> {
                val scope = offlineScopeProvider()
                Result.success(cache.page(fetchQuery, scope))
            }
            else -> remotePageWithCacheFallback(fetchQuery)
        }

    private suspend fun remotePageWithCacheFallback(fetchQuery: ProductFetchQuery): Result<List<Product>> {
        val token = localStore.readCompanySession()?.token
        if (token.isNullOrBlank()) return Result.failure(IllegalStateException(NO_COMPANY_ERROR))
        val scope = offlineScopeProvider()
        val deptIds = if (scope.allProducts || fetchQuery.departmentId != null) null else scope.departmentIds.toList()
        return runCatching {
            val response =
                apiService.getProducts(
                    token,
                    page =
                        CatalogPage(
                            limit = fetchQuery.limit,
                            offset = fetchQuery.offset,
                            search = fetchQuery.query,
                            departmentId = fetchQuery.departmentId,
                            departmentIds = deptIds,
                            itemType = fetchQuery.itemType,
                            warehouseIds = fetchQuery.warehouseIds,
                        ),
                )
            if (scope.enabled) {
                val entities = response.data.map { it.toEntity() }
                val entitiesToCache =
                    if (!scope.allProducts) {
                        entities.filter { it.department in scope.departmentIds }
                    } else {
                        entities
                    }
                if (entitiesToCache.isNotEmpty()) {
                    productDao.insertAll(entitiesToCache)
                }
            }
            response.data.map { it.toDomain() }
        }.recoverCatching { error ->
            if (error is CancellationException) throw error
            cache.page(fetchQuery, scope).ifEmpty { throw error }
        }
    }

    private companion object {
        const val NO_COMPANY_ERROR = "No hay empresa seleccionada"
    }
}
