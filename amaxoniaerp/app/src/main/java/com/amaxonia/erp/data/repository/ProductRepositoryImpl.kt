package com.amaxonia.erp.data.repository

import com.amaxonia.erp.BuildConfig
import com.amaxonia.erp.data.local.LocalStore
import com.amaxonia.erp.data.local.db.DepartmentDao
import com.amaxonia.erp.data.local.db.DepartmentEntity
import com.amaxonia.erp.data.local.db.ProductDao
import com.amaxonia.erp.data.local.db.ProductEntity
import com.amaxonia.erp.data.remote.ApiService
import com.amaxonia.erp.data.remote.ImageUrlHelper
import com.amaxonia.erp.data.remote.NetworkMonitor
import com.amaxonia.erp.data.remote.createProduct
import com.amaxonia.erp.data.remote.dto.CreateProductRequest
import com.amaxonia.erp.data.remote.dto.DepartmentDto
import com.amaxonia.erp.data.remote.dto.ProductDto
import com.amaxonia.erp.data.remote.getDepartments
import com.amaxonia.erp.data.remote.getProducts
import com.amaxonia.erp.data.remote.updateProduct
import com.amaxonia.erp.data.sync.OfflineSyncScope
import com.amaxonia.erp.data.sync.OfflineSyncSettingsStore
import com.amaxonia.erp.data.sync.toDomain
import com.amaxonia.erp.domain.model.PriceLevel
import com.amaxonia.erp.domain.model.Product
import com.amaxonia.erp.domain.repository.PagedProducts
import com.amaxonia.erp.domain.repository.ProductRepository

class ProductRepositoryImpl(
    private val apiService: ApiService,
    private val localStore: LocalStore,
    private val productDao: ProductDao? = null,
    private val departmentDao: DepartmentDao? = null,
    private val networkMonitor: NetworkMonitor? = null,
    private val scopeStore: OfflineSyncSettingsStore? = null,
) : ProductRepository {

    private suspend fun requireToken(): String {
        return localStore.readCompanySession()?.token
            ?: error("No hay sesión de empresa activa")
    }

    private suspend fun resolveImageContext(): Triple<String, String, String> {
        val session = localStore.readCompanySession()
        val countryCode = session?.company?.countryCode.takeIf { !it.isNullOrBlank() }
            ?: localStore.readSelectedCountry()?.code
            ?: BuildConfig.DEFAULT_COUNTRY_CODE
        val companyDb = session?.company?.adminDb.orEmpty()
        return Triple(apiService.baseUrl, countryCode, companyDb)
    }

    private suspend fun currentScope(): OfflineSyncScope =
        runCatching { scopeStore?.load() }.getOrNull() ?: OfflineSyncScope.ALL

    override suspend fun getAllProducts(page: Int, pageSize: Int, departmentId: Int?): Result<List<Product>> {
        val scope = currentScope()
        val isOnline = networkMonitor?.isOnline() ?: true
        val offset = (page - 1).coerceAtLeast(0) * pageSize
        val (baseUrl, countryCode, companyDb) = resolveImageContext()

        if (!isOnline && productDao != null) {
            val entities = when {
                departmentId != null -> productDao.getPagedByDepartment(departmentId, pageSize, offset)
                !scope.allProducts && scope.departmentIds.isNotEmpty() ->
                    productDao.getPagedByDepartments(scope.departmentIds.toList(), pageSize, offset)
                else -> productDao.getPaged(pageSize, offset)
            }
            return Result.success(entities.map { it.toDomain(baseUrl, countryCode, companyDb) })
        }

        return runCatching {
            val token = requireToken()
            val response = apiService.getProducts(
                token = token,
                limit = pageSize,
                offset = offset,
                departmentId = departmentId,
            )
            if (scope.enabled && productDao != null) {
                val entities = response.data.map { it.toEntity() }
                val toCache = if (!scope.allProducts && scope.departmentIds.isNotEmpty()) {
                    entities.filter { it.department in scope.departmentIds }
                } else {
                    entities
                }
                if (toCache.isNotEmpty()) {
                    runCatching { productDao?.insertAll(toCache) }
                }
            }
            response.data.map { it.toDomain(baseUrl, countryCode, companyDb) }
        }.recoverCatching { error ->
            if (productDao != null) {
                val entities = when {
                    departmentId != null -> productDao.getPagedByDepartment(departmentId, pageSize, offset)
                    !scope.allProducts && scope.departmentIds.isNotEmpty() ->
                        productDao.getPagedByDepartments(scope.departmentIds.toList(), pageSize, offset)
                    else -> productDao.getPaged(pageSize, offset)
                }
                if (entities.isNotEmpty()) {
                    entities.map { it.toDomain(baseUrl, countryCode, companyDb) }
                } else {
                    throw error
                }
            } else {
                throw error
            }
        }
    }

    override suspend fun getPagedProducts(
        page: Int,
        pageSize: Int,
        departmentId: Int?,
        search: String?,
    ): Result<PagedProducts> {
        val scope = currentScope()
        val isOnline = networkMonitor?.isOnline() ?: true
        val offset = (page - 1).coerceAtLeast(0) * pageSize
        val (baseUrl, countryCode, companyDb) = resolveImageContext()
        val query = search?.trim().orEmpty()

        if (!isOnline && productDao != null) {
            val entities = when {
                query.isNotEmpty() && departmentId != null ->
                    productDao.searchPagedByDepartment(query, departmentId, pageSize, offset)
                query.isNotEmpty() ->
                    productDao.searchPaged(query, pageSize, offset)
                departmentId != null ->
                    productDao.getPagedByDepartment(departmentId, pageSize, offset)
                !scope.allProducts && scope.departmentIds.isNotEmpty() ->
                    productDao.getPagedByDepartments(scope.departmentIds.toList(), pageSize, offset)
                else ->
                    productDao.getPaged(pageSize, offset)
            }
            val total = when {
                query.isNotEmpty() && departmentId != null -> productDao.countSearchByDepartment(query, departmentId)
                query.isNotEmpty() -> productDao.countSearch(query)
                departmentId != null -> productDao.countByDepartment(departmentId)
                else -> productDao.count()
            }
            return Result.success(
                PagedProducts(
                    items = entities.map { it.toDomain(baseUrl, countryCode, companyDb) },
                    totalCount = total,
                    page = page,
                    pageSize = pageSize,
                ),
            )
        }

        return runCatching {
            val token = requireToken()
            val response = apiService.getProducts(
                token = token,
                limit = pageSize,
                offset = offset,
                search = query.takeIf { it.isNotBlank() },
                departmentId = departmentId,
            )
            if (scope.enabled && productDao != null) {
                val entities = response.data.map { it.toEntity() }
                val toCache = if (!scope.allProducts && scope.departmentIds.isNotEmpty()) {
                    entities.filter { it.department in scope.departmentIds }
                } else {
                    entities
                }
                if (toCache.isNotEmpty()) {
                    runCatching { productDao?.insertAll(toCache) }
                }
            }
            PagedProducts(
                items = response.data.map { it.toDomain(baseUrl, countryCode, companyDb) },
                totalCount = response.total.toInt(),
                page = page,
                pageSize = pageSize,
            )
        }.recoverCatching { error ->
            if (productDao != null) {
                val entities = when {
                    query.isNotEmpty() && departmentId != null ->
                        productDao.searchPagedByDepartment(query, departmentId, pageSize, offset)
                    query.isNotEmpty() ->
                        productDao.searchPaged(query, pageSize, offset)
                    departmentId != null ->
                        productDao.getPagedByDepartment(departmentId, pageSize, offset)
                    !scope.allProducts && scope.departmentIds.isNotEmpty() ->
                        productDao.getPagedByDepartments(scope.departmentIds.toList(), pageSize, offset)
                    else ->
                        productDao.getPaged(pageSize, offset)
                }
                val total = when {
                    query.isNotEmpty() && departmentId != null -> productDao.countSearchByDepartment(query, departmentId)
                    query.isNotEmpty() -> productDao.countSearch(query)
                    departmentId != null -> productDao.countByDepartment(departmentId)
                    else -> productDao.count()
                }
                if (entities.isNotEmpty() || total > 0) {
                    PagedProducts(
                        items = entities.map { it.toDomain(baseUrl, countryCode, companyDb) },
                        totalCount = total,
                        page = page,
                        pageSize = pageSize,
                    )
                } else {
                    throw error
                }
            } else {
                throw error
            }
        }
    }

    override suspend fun searchProducts(query: String, page: Int, pageSize: Int): Result<List<Product>> {
        val (baseUrl, countryCode, companyDb) = resolveImageContext()
        val trimmed = query.trim()

        // Hot path de código de barras: match exacto offline inmediato
        if (trimmed.length >= 6 && trimmed.all { it.isDigit() } && productDao != null) {
            productDao.getByBarcode(trimmed)?.let { exact ->
                return Result.success(listOf(exact.toDomain(baseUrl, countryCode, companyDb)))
            }
        }

        val scope = currentScope()
        val isOnline = networkMonitor?.isOnline() ?: true
        val offset = (page - 1).coerceAtLeast(0) * pageSize

        if (!isOnline && productDao != null) {
            val entities = if (!scope.allProducts && scope.departmentIds.isNotEmpty()) {
                productDao.searchPagedByDepartments(query, scope.departmentIds.toList(), pageSize, offset)
            } else {
                productDao.searchPaged(query, pageSize, offset)
            }
            return Result.success(entities.map { it.toDomain(baseUrl, countryCode, companyDb) })
        }

        return runCatching {
            val token = requireToken()
            val response = apiService.getProducts(
                token = token,
                limit = pageSize,
                offset = offset,
                search = query,
            )
            if (scope.enabled && productDao != null) {
                val entities = response.data.map { it.toEntity() }
                val toCache = if (!scope.allProducts && scope.departmentIds.isNotEmpty()) {
                    entities.filter { it.department in scope.departmentIds }
                } else {
                    entities
                }
                if (toCache.isNotEmpty()) {
                    runCatching { productDao?.insertAll(toCache) }
                }
            }
            response.data.map { it.toDomain(baseUrl, countryCode, companyDb) }
        }.recoverCatching { error ->
            if (productDao != null) {
                val entities = if (!scope.allProducts && scope.departmentIds.isNotEmpty()) {
                    productDao.searchPagedByDepartments(query, scope.departmentIds.toList(), pageSize, offset)
                } else {
                    productDao.searchPaged(query, pageSize, offset)
                }
                if (entities.isNotEmpty()) {
                    entities.map { it.toDomain(baseUrl, countryCode, companyDb) }
                } else {
                    throw error
                }
            } else {
                throw error
            }
        }
    }

    override suspend fun createProduct(product: Product, departmentId: Int): Result<Product> =
        runCatching {
            val token = requireToken()
            val request = CreateProductRequest(
                code = product.code,
                description = product.description,
                departmentId = departmentId,
                barcode1 = product.barcode1,
                price = product.mainPrice,
                taxRate = product.taxRate,
                isExempt = product.isExempt,
            )
            val response = apiService.createProduct(token, request)
            if (productDao != null) {
                runCatching { productDao?.insertAll(listOf(response.toEntity())) }
            }
            val (baseUrl, countryCode, companyDb) = resolveImageContext()
            response.toDomain(baseUrl, countryCode, companyDb)
        }

    override suspend fun updateProduct(id: String, product: Product, departmentId: Int): Result<Product> =
        runCatching {
            val token = requireToken()
            val request = CreateProductRequest(
                code = product.code,
                description = product.description,
                departmentId = departmentId,
                barcode1 = product.barcode1,
                price = product.mainPrice,
                taxRate = product.taxRate,
                isExempt = product.isExempt,
            )
            val response = apiService.updateProduct(token, id, request)
            if (productDao != null) {
                runCatching { productDao?.insertAll(listOf(response.toEntity())) }
            }
            val (baseUrl, countryCode, companyDb) = resolveImageContext()
            response.toDomain(baseUrl, countryCode, companyDb)
        }

    override suspend fun getDepartments(): Result<List<DepartmentDto>> =
        runCatching {
            val isOnline = networkMonitor?.isOnline() ?: true
            if (isOnline) {
                val token = requireToken()
                val remote = apiService.getDepartments(token)
                if (remote.isNotEmpty()) {
                    departmentDao?.insertAll(remote.map { DepartmentEntity(it.id, it.displayName) })
                    return@runCatching remote
                }
            }
            // Modo offline o API vacía: leer de Room
            val local = departmentDao?.getAll().orEmpty()
            if (local.isNotEmpty()) {
                local.map { DepartmentDto(id = it.id, name = it.name) }
            } else {
                val distinct = productDao?.getDistinctDepartmentIds().orEmpty()
                distinct.map { DepartmentDto(id = it, name = "Departamento $it") }
            }
        }.recoverCatching { error ->
            val local = departmentDao?.getAll().orEmpty()
            if (local.isNotEmpty()) {
                local.map { DepartmentDto(id = it.id, name = it.name) }
            } else {
                val distinct = productDao?.getDistinctDepartmentIds().orEmpty()
                if (distinct.isNotEmpty()) {
                    distinct.map { DepartmentDto(id = it, name = "Departamento $it") }
                } else {
                    throw error
                }
            }
        }
}

private fun ProductDto.toDomain(
    baseUrl: String,
    countryCode: String,
    companyDb: String,
): Product {
    val photo = ImageUrlHelper.productImageUrl(
        baseUrl = baseUrl,
        countryCode = countryCode,
        companyDb = companyDb,
        photoPath = rawPhoto,
    )
    return Product(
        id = id ?: code ?: "",
        code = code ?: "",
        description = description ?: "",
        reference = reference ?: "",
        barcode1 = barcode1 ?: "",
        barcode2 = barcode2 ?: "",
        barcode3 = barcode3 ?: "",
        photoUrl = photo,
        department = department ?: "",
        isExempt = isExempt ?: false,
        taxRate = taxRate ?: 0.0,
        costActual = costActual ?: 0.0,
        unitPackage = unitPackage ?: "UNIDAD",
        prices = prices.map {
            PriceLevel(
                label = it.label,
                price = it.price,
                utilityPercent = it.utilityPercent,
                pricePlusUtility = it.pricePlusUtility,
                pricePlusTax = it.pricePlusTax,
            )
        },
    )
}

private fun ProductDto.toEntity(): ProductEntity {
    val deptId = department?.toIntOrNull() ?: 0
    return ProductEntity(
        id = id ?: code ?: "",
        code = code ?: "",
        description = description ?: "",
        reference = reference ?: "",
        barcode1 = barcode1 ?: "",
        barcode2 = barcode2 ?: "",
        barcode3 = barcode3 ?: "",
        department = deptId,
        isExempt = isExempt ?: false,
        taxRate = taxRate ?: 0.0,
        costActual = costActual ?: 0.0,
        unitPackage = unitPackage ?: "UNIDAD",
        bulkQuantity = 0.0,
        portionUnit = "",
        unitOrPackage = "U",
        estatus = "A",
        isService = false,
        prices = prices.map {
            PriceLevel(
                label = it.label,
                price = it.price,
                utilityPercent = it.utilityPercent,
                pricePlusUtility = it.pricePlusUtility,
                pricePlusTax = it.pricePlusTax,
            )
        },
    )
}
