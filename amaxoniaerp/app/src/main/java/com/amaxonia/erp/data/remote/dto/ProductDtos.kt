package com.amaxonia.erp.data.remote.dto

import kotlinx.serialization.SerialName
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
    @SerialName("photo_url")
    val photoUrlSnake: String? = null,
    @SerialName("foto")
    val foto: String? = null,
    @SerialName("foto1")
    val foto1: String? = null,
    val department: String? = null,
    val isExempt: Boolean? = null,
    val taxRate: Double? = null,
    val costActual: Double? = null,
    val unitPackage: String? = null,
    val prices: List<PriceDto> = emptyList(),
) {
    val rawPhoto: String
        get() = photoUrl?.takeIf { it.isNotBlank() }
            ?: photoUrlSnake?.takeIf { it.isNotBlank() }
            ?: foto?.takeIf { it.isNotBlank() }
            ?: foto1?.takeIf { it.isNotBlank() }
            ?: ""
}

@Serializable
data class PriceDto(
    val label: String,
    val price: Double = 0.0,
    val utilityPercent: Double = 0.0,
    val pricePlusUtility: Double = 0.0,
    val pricePlusTax: Double = 0.0,
)

@Serializable
data class CreateProductRequest(
    val code: String,
    val description: String,
    val departmentId: Int = 1,
    val barcode1: String = "",
    val price: Double = 0.0,
    val taxRate: Double = 0.0,
    val isExempt: Boolean = false,
)

@Serializable
data class DepartmentDto(
    val id: Int = 0,
    val name: String = "",
    val nombre: String? = null,
    val descripcion: String? = null,
) {
    val displayName: String
        get() = name.ifBlank { nombre ?: descripcion ?: "Departamento $id" }
}

@Serializable
data class DepartmentsResponse(
    val data: List<DepartmentDto> = emptyList(),
)
