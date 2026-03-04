package com.amaxonia.pos.data.repository

import com.amaxonia.pos.domain.model.PriceLevel
import com.amaxonia.pos.domain.model.Product
import com.amaxonia.pos.domain.model.ProductStock
import com.amaxonia.pos.domain.model.ProductWarehouseStock
import com.amaxonia.pos.domain.model.generateDefaultPrices
import com.amaxonia.pos.domain.repository.Department
import com.amaxonia.pos.domain.repository.ProductRepository
import kotlinx.coroutines.delay
import kotlin.random.Random

class MockProductRepository : ProductRepository {
    private val mockProducts = mutableListOf<Product>()
    private var shouldFail = false
    private val failureRate = 0.1

    init {
        generateMockProducts()
    }

    override suspend fun getDepartments(): Result<List<Department>> {
        return Result.success(listOf(
            Department(1, "Departamento 1"),
            Department(2, "Departamento 2")
        ))
    }

    override suspend fun getAllProducts(): Result<List<Product>> {
        simulateNetworkDelay()
        if (shouldSimulateError()) {
            return Result.failure(Exception("Error al cargar productos desde el servidor"))
        }
        return Result.success(mockProducts.toList())
    }

    override suspend fun getAllProducts(departmentId: Int?): Result<List<Product>> {
        simulateNetworkDelay()
        if (shouldSimulateError()) {
            return Result.failure(Exception("Error al cargar productos desde el servidor"))
        }
        return Result.success(mockProducts.toList())
    }

    override suspend fun getAllProducts(page: Int, pageSize: Int): Result<List<Product>> {
        simulateNetworkDelay()
        if (shouldSimulateError()) {
            return Result.failure(Exception("Error al cargar productos desde el servidor"))
        }
        return Result.success(paginate(mockProducts, page, pageSize))
    }

    override suspend fun getProductById(id: String): Result<Product> {
        simulateNetworkDelay()
        if (shouldSimulateError()) {
            return Result.failure(Exception("Error al obtener el producto"))
        }
        val product = mockProducts.find { it.id == id }
        return if (product != null) {
            Result.success(product)
        } else {
            Result.failure(Exception("Producto no encontrado"))
        }
    }

    override suspend fun getProductStock(id: String): Result<ProductStock> {
        simulateNetworkDelay()
        val product = mockProducts.find { it.id == id }
            ?: return Result.failure(Exception("Producto no encontrado"))

        val available = ((1..20).random() + Random.nextDouble()).let { String.format("%.2f", it).toDouble() }
        return Result.success(
            ProductStock(
                itemId = product.id,
                stockTotalDisponible = available,
                almacenes = listOf(
                    ProductWarehouseStock(
                        almacenId = 1,
                        almacenNombre = "Principal",
                        almacenTipo = "NORMAL",
                        cantidad = available + 2,
                        cantidadMuestra = 0.0,
                        cantidadPrecomprometida = 2.0,
                        cantidadDisponible = available,
                        stockMinimo = 1.0,
                        stockMaximo = 50.0
                    )
                )
            )
        )
    }

    override suspend fun searchProducts(query: String): Result<List<Product>> {
        simulateNetworkDelay()
        if (shouldSimulateError()) {
            return Result.failure(Exception("Error en la búsqueda"))
        }
        val filtered = mockProducts.filter {
            it.description.contains(query, ignoreCase = true) ||
                    it.code.contains(query, ignoreCase = true) ||
                    it.reference.contains(query, ignoreCase = true)
        }
        return Result.success(filtered)
    }

    override suspend fun searchProducts(query: String, page: Int, pageSize: Int): Result<List<Product>> {
        simulateNetworkDelay()
        if (shouldSimulateError()) {
            return Result.failure(Exception("Error en la bǧsqueda"))
        }
        val filtered = mockProducts.filter {
            it.description.contains(query, ignoreCase = true) ||
                    it.code.contains(query, ignoreCase = true) ||
                    it.reference.contains(query, ignoreCase = true)
        }
        return Result.success(paginate(filtered, page, pageSize))
    }

    override suspend fun saveProduct(product: Product): Result<Unit> {
        simulateNetworkDelay()
        if (shouldSimulateError()) {
            return Result.failure(Exception("Error al guardar el producto"))
        }
        val existingIndex = mockProducts.indexOfFirst { it.id == product.id }
        if (existingIndex >= 0) {
            mockProducts[existingIndex] = product
        } else {
            mockProducts.add(product)
        }
        return Result.success(Unit)
    }

    override suspend fun deleteProduct(id: String): Result<Unit> {
        simulateNetworkDelay()
        if (shouldSimulateError()) {
            return Result.failure(Exception("Error al eliminar el producto"))
        }
        val removed = mockProducts.removeIf { it.id == id }
        return if (removed) {
            Result.success(Unit)
        } else {
            Result.failure(Exception("Producto no encontrado"))
        }
    }

    private suspend fun simulateNetworkDelay() {
        delay((300..1500).random().toLong())
    }

    private fun shouldSimulateError(): Boolean {
        return Random.nextFloat() < failureRate
    }

    private fun generateMockProducts() {
        mockProducts.clear()
        (1..200).forEach { i ->
            mockProducts.add(
                Product(
                    code = "PROD-${i.toString().padStart(4, '0')}",
                    description = "Producto Ejemplo $i",
                    reference = "REF-$i",
                    costActual = 10.0 + i,
                    prices = generateDefaultPrices().map { level ->
                        level.copy(pricePlusTax = (10.0 + i) * 1.5)
                    }
                )
            )
        }
    }

    private fun paginate(products: List<Product>, page: Int, pageSize: Int): List<Product> {
        val startIndex = ((page - 1).coerceAtLeast(0)) * pageSize
        if (startIndex >= products.size) return emptyList()
        return products.drop(startIndex).take(pageSize)
    }
}
