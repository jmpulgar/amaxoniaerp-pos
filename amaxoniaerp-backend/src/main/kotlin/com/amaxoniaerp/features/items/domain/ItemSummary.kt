package com.amaxoniaerp.features.items.domain

import kotlinx.serialization.Serializable

@Serializable
data class ItemSummary(
    val id: Long,
    val code: String,
    val reference: String?,
)
