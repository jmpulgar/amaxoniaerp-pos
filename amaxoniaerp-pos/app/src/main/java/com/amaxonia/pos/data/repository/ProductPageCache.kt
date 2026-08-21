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
import com.amaxonia.pos.domain.model.Product

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
    ): List<Product> {
        val normalized = query?.trim()?.let { if (it.isEmpty()) "%" else "%$it%" }
        val entities =
            if (normalized != null) {
                if (departmentId == null) {
                    productDao.searchPaged(normalized, limit = limit, offset = offset)
                } else {
                    productDao.searchPagedByDepartment(normalized, departmentId, limit = limit, offset = offset)
                }
            } else {
                if (departmentId == null) {
                    productDao.getPaged(limit = limit, offset = offset)
                } else {
                    productDao.getPagedByDepartment(departmentId, limit = limit, offset = offset)
                }
            }
        return entities.map { it.toDomain() }
    }

    /** Catálogo completo cacheado (hasta [FULL_CACHE_SIZE] filas). */
    suspend fun fullCatalog(): List<Product> = productDao.getPaged(limit = FULL_CACHE_SIZE, offset = 0).map { it.toDomain() }

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
    private val networkMonitor: NetworkMonitor,
) {
    private val cache = ProductPageCache(productDao)

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
    ): Result<List<Product>> =
        when {
            !networkMonitor.isOnline() -> Result.success(cache.page(departmentId, query, limit, offset))
            else -> remotePageWithCacheFallback(departmentId, query, limit, offset)
        }

    private suspend fun remotePageWithCacheFallback(
        departmentId: Int?,
        query: String?,
        limit: Int,
        offset: Int,
    ): Result<List<Product>> {
        val token = localStore.readCompanySession()?.token
        if (token.isNullOrBlank()) return Result.failure(IllegalStateException(NO_COMPANY_ERROR))
        return runCatching {
            val response =
                apiService.getProducts(
                    token,
                    page = CatalogPage(limit = limit, offset = offset, search = query),
                    departmentId = departmentId,
                )
            productDao.insertAll(response.data.map { it.toEntity() })
            response.data.map { it.toDomain() }
        }.recoverCatching { error ->
            cache.page(departmentId, query, limit, offset).ifEmpty { throw error }
        }
    }

    private companion object {
        const val NO_COMPANY_ERROR = "No hay empresa seleccionada"
    }
}
