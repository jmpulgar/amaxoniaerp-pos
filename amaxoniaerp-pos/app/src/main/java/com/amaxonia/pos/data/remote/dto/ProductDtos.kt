package com.amaxonia.pos.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class ProductDto(
    val id: Int? = null,
    val code: String? = null,
    val description: String? = null,
    val reference: String? = null,
    val barcode1: String? = null,
    val photoUrl: String? = null,
    val department: Int? = null,
    val taxRate: Double? = null,
    val costActual: Double? = null,
    val prices: List<PriceDto> = emptyList()
)

@Serializable
data class PriceDto(
    val label: String,
    val price: Double = 0.0,
    val utilityPercent: Double = 0.0,
    val pricePlusTax: Double = 0.0,
    val discountPercent: Double = 0.0
)

@Serializable
data class CreateProductRequest(
    val code: String,
    val description: String,
    val reference: String,
    val barcode1: String,
    val department: Int,
    val taxRate: Double,
    val costActual: Double,
    val prices: List<PriceDto>
)
