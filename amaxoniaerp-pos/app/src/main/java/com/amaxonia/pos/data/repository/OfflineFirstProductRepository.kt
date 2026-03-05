package com.amaxonia.pos.data.repository

import com.amaxonia.pos.data.local.LocalStore
import com.amaxonia.pos.data.local.db.ProductDao
import com.amaxonia.pos.data.local.db.toDomain
import com.amaxonia.pos.data.local.db.toEntity
import com.amaxonia.pos.data.remote.ApiService
import com.amaxonia.pos.data.remote.NetworkMonitor
import com.amaxonia.pos.domain.model.Product
import com.amaxonia.pos.domain.model.ProductStock
import com.amaxonia.pos.domain.repository.Department
import com.amaxonia.pos.domain.repository.ProductRepository

class OfflineFirstProductRepository(
    private val apiService: ApiService,
    private val localStore: LocalStore,
    private val productDao: ProductDao,
    private val networkMonitor: NetworkMonitor
) : ProductRepository {
    override suspend fun getDepartments(): Result<List<Department>> {
        val token = localStore.readCompanySession()?.token
        if (token.isNullOrBlank()) {
            return Result.failure(IllegalStateException("No hay empresa seleccionada"))
        }
        if (!networkMonitor.isOnline()) {
            return Result.failure(IllegalStateException("Sin conexión"))
        }
        return runCatching {
            apiService.getDepartments(token).map { Department(it.id, it.name) }
        }
    }

    override suspend fun getSections(departmentId: Int): Result<List<Department>> {
        val token = localStore.readCompanySession()?.token
            ?: return Result.failure(IllegalStateException("No hay empresa seleccionada"))
        if (!networkMonitor.isOnline()) {
            return Result.failure(IllegalStateException("Sin conexión"))
        }
        return runCatching {
            apiService.getSections(token, departmentId).map { Department(it.id, it.name) }
        }
    }

    override suspend fun getFamilies(sectionId: Int): Result<List<Department>> {
        val token = localStore.readCompanySession()?.token
            ?: return Result.failure(IllegalStateException("No hay empresa seleccionada"))
        if (!networkMonitor.isOnline()) {
            return Result.failure(IllegalStateException("Sin conexión"))
        }
        return runCatching {
            apiService.getFamilies(token, sectionId).map { Department(it.id, it.name) }
        }
    }

    override suspend fun getSubFamilies(familyId: Int): Result<List<Department>> {
        val token = localStore.readCompanySession()?.token
            ?: return Result.failure(IllegalStateException("No hay empresa seleccionada"))
        if (!networkMonitor.isOnline()) {
            return Result.failure(IllegalStateException("Sin conexión"))
        }
        return runCatching {
            apiService.getSubFamilies(token, familyId).map { Department(it.id, it.name) }
        }
    }

    override suspend fun getBrands(): Result<List<Department>> {
        val token = localStore.readCompanySession()?.token
            ?: return Result.failure(IllegalStateException("No hay empresa seleccionada"))
        if (!networkMonitor.isOnline()) {
            return Result.failure(IllegalStateException("Sin conexión"))
        }
        return runCatching {
            apiService.getBrands(token).map { Department(it.id, it.name) }
        }
    }

    override suspend fun getLines(brandId: Int): Result<List<Department>> {
        val token = localStore.readCompanySession()?.token
            ?: return Result.failure(IllegalStateException("No hay empresa seleccionada"))
        if (!networkMonitor.isOnline()) {
            return Result.failure(IllegalStateException("Sin conexión"))
        }
        return runCatching {
            apiService.getLines(token, brandId).map { Department(it.id, it.name) }
        }
    }

    override suspend fun getAllProducts(page: Int, pageSize: Int): Result<List<Product>> {
        val token = localStore.readCompanySession()?.token
        val offset = (page - 1).coerceAtLeast(0) * pageSize
        if (!networkMonitor.isOnline()) {
            val cached = productDao.getPaged(pageSize, offset).map { it.toDomain() }
            return Result.success(cached)
        }
        if (token.isNullOrBlank()) {
            return Result.failure(IllegalStateException("No hay empresa seleccionada"))
        }
        return runCatching {
            val response = apiService.getProducts(token, limit = pageSize, offset = offset, search = null)
            productDao.insertAll(response.data.map { it.toEntity() })
            response.data.map { it.toDomain() }
        }.recoverCatching { error ->
            val cached = productDao.getPaged(pageSize, offset).map { it.toDomain() }
            if (cached.isNotEmpty()) cached else throw error
        }
    }

    override suspend fun getAllProducts(): Result<List<Product>> = getAllProducts(null)

    override suspend fun getAllProducts(departmentId: Int?): Result<List<Product>> {
        val token = localStore.readCompanySession()?.token
        if (!networkMonitor.isOnline()) {
            val cached = productDao.getPaged(limit = 1000, offset = 0).map { it.toDomain() }
            return if (cached.isNotEmpty()) Result.success(cached) else {
                Result.failure(IllegalStateException("No hay empresa seleccionada"))
            }
        }
        if (token.isNullOrBlank()) {
            return Result.failure(IllegalStateException("No hay empresa seleccionada"))
        }
        return runCatching {
            val response = apiService.getProducts(
                token,
                limit = 500,
                offset = 0,
                search = null,
                departmentId = departmentId
            )
            productDao.insertAll(response.data.map { it.toEntity() })
            response.data.map { it.toDomain() }
        }.recoverCatching { error ->
            val cached = productDao.getPaged(limit = 1000, offset = 0).map { it.toDomain() }
            if (cached.isNotEmpty()) cached else throw error
        }
    }

    override suspend fun getProductById(id: String): Result<Product> {
        val token = localStore.readCompanySession()?.token
        val cachedProduct = productDao.getById(id)?.toDomain()

        if (!token.isNullOrBlank() && networkMonitor.isOnline()) {
            return runCatching {
                val remoteProduct = apiService.getProductById(token, id)
                productDao.insertAll(listOf(remoteProduct.toEntity()))
                remoteProduct.toDomain()
            }.recoverCatching {
                cachedProduct ?: throw it
            }
        }

        return if (cachedProduct != null) {
            Result.success(cachedProduct)
        } else {
            Result.failure(IllegalArgumentException("Producto no encontrado"))
        }
    }

    override suspend fun getProductStock(id: String): Result<ProductStock> {
        val token = localStore.readCompanySession()?.token
            ?: return Result.failure(IllegalStateException("No hay empresa seleccionada"))

        if (!networkMonitor.isOnline()) {
            return Result.failure(IllegalStateException("Sin conexión para consultar stock por almacén"))
        }

        return runCatching {
            apiService.getItemStock(token, id).toDomain()
        }
    }

    override suspend fun searchProducts(query: String): Result<List<Product>> {
        val token = localStore.readCompanySession()?.token
        if (!networkMonitor.isOnline()) {
            val cached = productDao.searchPaged(normalizeQuery(query), limit = 100, offset = 0).map { it.toDomain() }
            return Result.success(cached)
        }
        if (token.isNullOrBlank()) {
            return Result.failure(IllegalStateException("No hay empresa seleccionada"))
        }
        return runCatching {
            val response = apiService.getProducts(token, limit = 100, offset = 0, search = query)
            productDao.insertAll(response.data.map { it.toEntity() })
            response.data.map { it.toDomain() }
        }.recoverCatching { error ->
            val cached = productDao.searchPaged(normalizeQuery(query), limit = 100, offset = 0).map { it.toDomain() }
            if (cached.isNotEmpty()) cached else throw error
        }
    }

    override suspend fun searchProducts(query: String, page: Int, pageSize: Int): Result<List<Product>> {
        val token = localStore.readCompanySession()?.token
        val offset = (page - 1).coerceAtLeast(0) * pageSize
        if (!networkMonitor.isOnline()) {
            val cached = productDao.searchPaged(normalizeQuery(query), limit = pageSize, offset = offset)
            return Result.success(cached.map { it.toDomain() })
        }
        if (token.isNullOrBlank()) {
            return Result.failure(IllegalStateException("No hay empresa seleccionada"))
        }
        return runCatching {
            val response = apiService.getProducts(token, limit = pageSize, offset = offset, search = query)
            productDao.insertAll(response.data.map { it.toEntity() })
            response.data.map { it.toDomain() }
        }.recoverCatching { error ->
            val cached = productDao.searchPaged(normalizeQuery(query), limit = pageSize, offset = offset)
            val mapped = cached.map { it.toDomain() }
            if (mapped.isNotEmpty()) mapped else throw error
        }
    }

    override suspend fun saveProduct(product: Product): Result<Unit> {
        val token = localStore.readCompanySession()?.token
            ?: return Result.failure(IllegalStateException("No hay empresa seleccionada"))
        val request = product.toCreateRequest()
        return runCatching {
            val id = product.id.toIntOrNull()
            val saved = if (id == null) {
                apiService.createProduct(token, request)
            } else {
                apiService.updateProduct(token, id, request)
            }
            productDao.insertAll(listOf(saved.toEntity()))
        }
    }

    override suspend fun deleteProduct(id: String): Result<Unit> {
        return Result.failure(UnsupportedOperationException("Eliminar productos no esta implementado"))
    }

    private fun normalizeQuery(query: String): String {
        val normalized = query.trim()
        return if (normalized.isEmpty()) "%" else "%$normalized%"
    }
}
