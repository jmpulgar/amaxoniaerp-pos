package com.amaxonia.pos.data.repository

import com.amaxonia.pos.data.local.LocalStore
import com.amaxonia.pos.data.local.db.ProductDao
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
        departmentId: Int?,
        query: String?,
        limit: Int,
        offset: Int,
        itemType: String? = null,
        scope: OfflineSyncScope = OfflineSyncScope.ALL,
    ): List<Product> {
        val isServiceInt =
            when (itemType?.uppercase()) {
                "SERVICE", "SERVICIO" -> 1
                "PRODUCT", "PRODUCTO" -> 0
                else -> null
            }
        val normalized = query?.trim()?.takeIf { it.isNotEmpty() }?.let { "%$it%" }
        val entities =
            if (normalized != null) {
                when {
                    departmentId != null ->
                        productDao.searchPagedByDepartment(normalized, departmentId, limit = limit, offset = offset, isService = isServiceInt)
                    !scope.allProducts ->
                        productDao.searchPagedByDepartments(normalized, scope.departmentIds.toList(), limit = limit, offset = offset, isService = isServiceInt)
                    else ->
                        productDao.searchPaged(normalized, limit = limit, offset = offset, isService = isServiceInt)
                }
            } else {
                when {
                    departmentId != null ->
                        productDao.getPagedByDepartment(departmentId, limit = limit, offset = offset, isService = isServiceInt)
                    !scope.allProducts ->
                        productDao.getPagedByDepartments(scope.departmentIds.toList(), limit = limit, offset = offset, isService = isServiceInt)
                    else ->
                        productDao.getPaged(limit = limit, offset = offset, isService = isServiceInt)
                }
            }
        return entities.map { it.toDomain() }
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
    suspend fun pageWithCacheFallback(
        departmentId: Int?,
        query: String?,
        limit: Int,
        offset: Int,
        itemType: String? = null,
    ): Result<List<Product>> =
        when {
            !networkMonitor.isOnline() -> {
                val scope = offlineScopeProvider()
                Result.success(cache.page(departmentId, query, limit, offset, itemType, scope))
            }
            else -> remotePageWithCacheFallback(departmentId, query, limit, offset, itemType)
        }

    private suspend fun remotePageWithCacheFallback(
        departmentId: Int?,
        query: String?,
        limit: Int,
        offset: Int,
        itemType: String? = null,
    ): Result<List<Product>> {
        val token = localStore.readCompanySession()?.token
        if (token.isNullOrBlank()) return Result.failure(IllegalStateException(NO_COMPANY_ERROR))
        val scope = offlineScopeProvider()
        val deptIds = if (scope.allProducts || departmentId != null) null else scope.departmentIds.toList()
        return runCatching {
            val response =
                apiService.getProducts(
                    token,
                    page = CatalogPage(limit = limit, offset = offset, search = query),
                    departmentId = departmentId,
                    departmentIds = deptIds,
                    itemType = itemType,
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
            cache.page(departmentId, query, limit, offset, itemType, scope).ifEmpty { throw error }
        }
    }

    private companion object {
        const val NO_COMPANY_ERROR = "No hay empresa seleccionada"
    }
}
