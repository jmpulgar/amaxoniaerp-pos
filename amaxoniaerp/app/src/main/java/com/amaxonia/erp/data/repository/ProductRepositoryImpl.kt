package com.amaxonia.erp.data.repository

import com.amaxonia.erp.data.local.LocalStore
import com.amaxonia.erp.data.remote.ApiService
import com.amaxonia.erp.data.remote.createProduct
import com.amaxonia.erp.data.remote.dto.CreateProductRequest
import com.amaxonia.erp.data.remote.dto.DepartmentDto
import com.amaxonia.erp.data.remote.dto.ProductDto
import com.amaxonia.erp.data.remote.getDepartments
import com.amaxonia.erp.data.remote.getProducts
import com.amaxonia.erp.data.remote.updateProduct
import com.amaxonia.erp.domain.model.PriceLevel
import com.amaxonia.erp.domain.model.Product
import com.amaxonia.erp.domain.repository.ProductRepository

class ProductRepositoryImpl(
    private val apiService: ApiService,
    private val localStore: LocalStore,
) : ProductRepository {

    private suspend fun requireToken(): String {
        return localStore.readCompanySession()?.token
            ?: error("No hay sesión de empresa activa")
    }

    override suspend fun getAllProducts(page: Int, pageSize: Int, departmentId: Int?): Result<List<Product>> =
        runCatching {
            val token = requireToken()
            val offset = (page - 1).coerceAtLeast(0) * pageSize
            val response = apiService.getProducts(
                token = token,
                limit = pageSize,
                offset = offset,
                departmentId = departmentId,
            )
            response.data.map { it.toDomain() }
        }

    override suspend fun searchProducts(query: String, page: Int, pageSize: Int): Result<List<Product>> =
        runCatching {
            val token = requireToken()
            val offset = (page - 1).coerceAtLeast(0) * pageSize
            val response = apiService.getProducts(
                token = token,
                limit = pageSize,
                offset = offset,
                search = query,
            )
            response.data.map { it.toDomain() }
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
            response.toDomain()
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
            response.toDomain()
        }

    override suspend fun getDepartments(): Result<List<DepartmentDto>> =
        runCatching {
            val token = requireToken()
            apiService.getDepartments(token)
        }
}

private fun ProductDto.toDomain(): Product =
    Product(
        id = id ?: code ?: "",
        code = code ?: "",
        description = description ?: "",
        reference = reference ?: "",
        barcode1 = barcode1 ?: "",
        photoUrl = photoUrl ?: "",
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
