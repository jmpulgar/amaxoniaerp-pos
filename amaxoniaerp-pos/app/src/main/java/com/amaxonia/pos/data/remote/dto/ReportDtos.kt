package com.amaxonia.pos.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class DepartmentDto(
    val id: Int,
    val name: String
)

@Serializable
data class DepartmentsResponse(
    val data: List<DepartmentDto>
)

@Serializable
data class BestSellerDto(
    val id: String,
    val name: String,
    val price: Double,
    val salesCount: Int,
    val photoUrl: String = ""
)

@Serializable
data class BestSellersResponse(
    val data: List<BestSellerDto>
)
