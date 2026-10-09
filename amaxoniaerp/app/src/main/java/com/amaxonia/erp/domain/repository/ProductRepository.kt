package com.amaxonia.erp.domain.repository

import com.amaxonia.erp.data.remote.dto.DepartmentDto
import com.amaxonia.erp.domain.model.Product

import kotlin.math.ceil

data class PagedProducts(
    val items: List<Product>,
    val totalCount: Int,
    val page: Int,
    val pageSize: Int,
) {
    val totalPages: Int get() = if (pageSize > 0 && totalCount > 0) maxOf(1, ceil(totalCount.toDouble() / pageSize).toInt()) else 1
    val hasNextPage: Boolean get() = page < totalPages
    val hasPrevPage: Boolean get() = page > 1
}

interface ProductRepository {
    suspend fun getAllProducts(page: Int = 1, pageSize: Int = 20, departmentId: Int? = null): Result<List<Product>>
    suspend fun searchProducts(query: String, page: Int = 1, pageSize: Int = 20): Result<List<Product>>
    suspend fun createProduct(product: Product, departmentId: Int): Result<Product>
    suspend fun updateProduct(id: String, product: Product, departmentId: Int): Result<Product>
    suspend fun getDepartments(): Result<List<DepartmentDto>>

    suspend fun getPagedProducts(
        page: Int = 1,
        pageSize: Int = 12,
        departmentId: Int? = null,
        search: String? = null,
    ): Result<PagedProducts> = runCatching {
        val trimmed = search?.trim().orEmpty()
        val list = if (trimmed.isBlank()) {
            getAllProducts(page, pageSize, departmentId).getOrThrow()
        } else {
            val searched = searchProducts(trimmed, page, pageSize).getOrThrow()
            if (searched.isEmpty()) {
                getAllProducts(page, pageSize, departmentId).getOrThrow().filter {
                    it.description.contains(trimmed, ignoreCase = true) ||
                    it.code.contains(trimmed, ignoreCase = true) ||
                    it.barcode1.contains(trimmed, ignoreCase = true) ||
                    it.reference.contains(trimmed, ignoreCase = true)
                }
            } else {
                searched
            }
        }
        val estimatedTotal = if (list.size < pageSize && page == 1) list.size else (page * pageSize + (if (list.size == pageSize) pageSize else 0))
        PagedProducts(
            items = list,
            totalCount = estimatedTotal,
            page = page,
            pageSize = pageSize,
        )
    }
}
