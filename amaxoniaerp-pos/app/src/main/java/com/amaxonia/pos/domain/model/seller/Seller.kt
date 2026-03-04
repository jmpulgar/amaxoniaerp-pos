package com.amaxonia.pos.domain.model.seller

import kotlinx.serialization.Serializable

@Serializable
data class Seller(
    val id: Int,
    val nombre: String,
)
