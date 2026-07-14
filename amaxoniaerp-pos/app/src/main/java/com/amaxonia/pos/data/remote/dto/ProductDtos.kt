package com.amaxonia.pos.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class ProductDto(
    val id: String? = null,
    val code: String? = null,
    val description: String? = null,
    val reference: String? = null,
    val barcode1: String? = null,
    val barcode2: String? = null,
    val barcode3: String? = null,
    val photoUrl: String? = null,
    val department: String? = null,
    val section: String? = null,
    val family: String? = null,
    val subFamily: String? = null,
    val brand: String? = null,
    val line: String? = null,
    val gobSegment: String? = null,
    val gobFamily: String? = null,
    val isExempt: Boolean? = null,
    val taxRate: Double? = null,
    val costActual: Double? = null,
    val costAverage: Double? = null,
    val costPrevious: Double? = null,
    val costCIF: Double? = null,
    val costFOB: Double? = null,
    val costProcessed: Double? = null,
    val commissionPercent: Double? = null,
    val costEuroOrigin: Double? = null,
    val costFranco: Double? = null,
    val unitPackage: String? = null,
    val bulkQuantity: Double? = null,
    val portionUnit: String? = null,
    val unitOrPackage: String? = null,
    val prices: List<PriceDto> = emptyList(),
)

@Serializable
data class PriceDto(
    val label: String,
    val price: Double = 0.0,
    val utilityPercent: Double = 0.0,
    val pricePlusUtility: Double = 0.0,
    val pricePlusTax: Double = 0.0,
    val unitPrice: Double = 0.0,
    val unitPricePlusTax: Double = 0.0,
    val discountPercent: Double = 0.0,
)

@Serializable
data class ItemLotInfoDto(
    val idLoteItem: Int,
    val codigoLoteItem: String,
    val vencimiento: String? = null,
    val disponibilidad: Int,
    val idAlmacen: Int = 1,
)

@Serializable
data class ItemLotsResponseDto(
    val itemId: Int,
    val poseeConfiguracionLote: Boolean,
    val lotes: List<ItemLotInfoDto> = emptyList(),
)

@Serializable
data class CreateProductRequest(
    val code: String,
    val name: String,
    val description: String? = null,
    val reference: String? = null,
    val barcode: String = "",
    val barcode2: String? = null,
    val barcode3: String? = null,
    val departmentId: Int = 0,
    val sectionId: Int = 0,
    val familyId: Int = 0,
    val subfamilyId: Int = 0,
    val brandId: Int = 0,
    val lineId: Int = 0,
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
    val currentCost: Double = 0.0,
    val isTaxExempt: Boolean = false,
    val taxRate: Double = 0.0,
    val totalStock: Int = 0,
)
