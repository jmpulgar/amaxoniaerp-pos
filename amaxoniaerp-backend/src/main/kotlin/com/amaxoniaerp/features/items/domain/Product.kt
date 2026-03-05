package com.amaxoniaerp.features.items.domain

import kotlinx.serialization.Serializable

@Serializable
data class Product(
    val id: String = "",
    val code: String = "",
    val reference: String = "",
    val description: String = "",
    val barcode1: String = "",
    val barcode2: String = "",
    val barcode3: String = "",
    val photoUrl: String = "",
    val department: String = "",
    val section: String = "",
    val family: String = "",
    val subFamily: String = "",
    val brand: String = "",
    val line: String = "",
    val gobSegment: String = "",
    val gobFamily: String = "",
    val isExempt: Boolean = false,
    val taxRate: Double = 7.0,
    val costActual: Double = 0.0,
    val costAverage: Double = 0.0,
    val costPrevious: Double = 0.0,
    val costCIF: Double = 0.0,
    val costFOB: Double = 0.0,
    val costProcessed: Double = 0.0,
    val commissionPercent: Double = 0.0,
    val costEuroOrigin: Double = 0.0,
    val costFranco: Double = 0.0,
    val prices: List<PriceLevel> = emptyList(),
)

@Serializable
data class PriceLevel(
    val label: String,
    val price: Double = 0.0,
    val utilityPercent: Double = 0.0,
    val pricePlusUtility: Double = 0.0,
    val pricePlusTax: Double = 0.0,
    val discountPercent: Double = 0.0,
)

@Serializable
data class CreateProductRequest(
    // Campos base comunes
    val code: String,
    val name: String,           // descripcion1
    val description: String? = null,  // descripcion2
    val reference: String? = null,
    val barcode: String = "",
    val barcode2: String? = null,
    val barcode3: String? = null,

    // Categorización
    val departmentId: Int = 0,
    val sectionId: Int = 0,
    val familyId: Int = 0,
    val subfamilyId: Int = 0,
    val brandId: Int = 0,
    val lineId: Int = 0,

    // Precios
    val price1: Double = 0.0,
    val utility1: Double = 0.0,
    val priceWithTax1: Double = 0.0,

    val price2: Double = 0.0,
    val utility2: Double = 0.0,
    val priceWithTax2: Double = 0.0,

    val price3: Double = 0.0,
    val utility3: Double = 0.0,
    val priceWithTax3: Double = 0.0,

    val price4: Double = 0.0,
    val utility4: Double = 0.0,
    val priceWithTax4: Double = 0.0,

    val price5: Double = 0.0,
    val utility5: Double = 0.0,
    val priceWithTax5: Double = 0.0,

    // Costos e impuestos
    val currentCost: Double = 0.0,
    val isTaxExempt: Boolean = false,
    val taxRate: Double = 0.0,

    // Existencias
    val totalStock: Int = 0,

    // ============== CAMPOS ESPECÍFICOS POR PAÍS (nullable) ==============

    // Venezuela (TYPE_B) - Multimoneda y Balanza
    val isScale: Boolean? = null,
    val baseCurrencyId: Int? = null,

    // Panamá (TYPE_A) - Kits y Gobierno
    val kitDetails: String? = null,           // 'T' o 'F'
    val governmentSegmentId: Int? = null,
    val governmentFamilyId: Int? = null,
)

@Serializable
data class ProductsListResponse(
    val data: List<Product>,
    val total: Long,
)

@Serializable
data class DepartmentItemResponse(
    val id: Int,
    val name: String
)

@Serializable
data class DepartmentsApiResponse(
    val data: List<DepartmentItemResponse>
)

@Serializable
data class BestSellerItemResponse(
    val id: String,
    val name: String,
    val price: Double,
    val salesCount: Int,
    val photoUrl: String = ""
)

@Serializable
data class BestSellersApiResponse(
    val data: List<BestSellerItemResponse>
)
