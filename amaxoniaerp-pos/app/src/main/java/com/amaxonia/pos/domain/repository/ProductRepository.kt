package com.amaxonia.pos.domain.repository

import com.amaxonia.pos.domain.model.Product
import com.amaxonia.pos.domain.model.ProductStock
import kotlinx.coroutines.flow.Flow

data class Department(val id: Int, val name: String)

interface ProductRepository {
    suspend fun getDepartments(): Result<List<Department>>
    suspend fun getSections(departmentId: Int): Result<List<Department>>
    suspend fun getFamilies(sectionId: Int): Result<List<Department>>
    suspend fun getSubFamilies(familyId: Int): Result<List<Department>>
    suspend fun getBrands(): Result<List<Department>>
    suspend fun getLines(brandId: Int): Result<List<Department>>
    suspend fun getAllProducts(): Result<List<Product>>
    suspend fun getAllProducts(departmentId: Int?): Result<List<Product>>
    suspend fun getAllProducts(departmentId: Int?, page: Int, pageSize: Int): Result<List<Product>> {
        return getAllProducts(departmentId).map { products ->
            val startIndex = ((page - 1).coerceAtLeast(0)) * pageSize
            if (startIndex >= products.size) emptyList() else products.drop(startIndex).take(pageSize)
        }
    }
    suspend fun getAllProducts(page: Int, pageSize: Int): Result<List<Product>> {
        return getAllProducts().map { products ->
            val startIndex = ((page - 1).coerceAtLeast(0)) * pageSize
            if (startIndex >= products.size) emptyList() else products.drop(startIndex).take(pageSize)
        }
    }
    suspend fun getProductById(id: String): Result<Product>
    suspend fun getProductStock(id: String): Result<ProductStock>
    suspend fun searchProducts(query: String): Result<List<Product>>
    suspend fun searchProducts(query: String, page: Int, pageSize: Int): Result<List<Product>> {
        return searchProducts(query).map { products ->
            val startIndex = ((page - 1).coerceAtLeast(0)) * pageSize
            if (startIndex >= products.size) emptyList() else products.drop(startIndex).take(pageSize)
        }
    }
    suspend fun searchProducts(query: String, departmentId: Int?, page: Int, pageSize: Int): Result<List<Product>> {
        return searchProducts(query, page, pageSize)
    }
    suspend fun saveProduct(product: Product): Result<Unit>
    suspend fun deleteProduct(id: String): Result<Unit>
}
