package com.amaxonia.erp.domain.repository

import com.amaxonia.erp.data.remote.dto.DepartmentDto
import com.amaxonia.erp.domain.model.Product

interface ProductRepository {
    suspend fun getAllProducts(page: Int = 1, pageSize: Int = 20, departmentId: Int? = null): Result<List<Product>>
    suspend fun searchProducts(query: String, page: Int = 1, pageSize: Int = 20): Result<List<Product>>
    suspend fun createProduct(product: Product, departmentId: Int): Result<Product>
    suspend fun updateProduct(id: String, product: Product, departmentId: Int): Result<Product>
    suspend fun getDepartments(): Result<List<DepartmentDto>>
}
