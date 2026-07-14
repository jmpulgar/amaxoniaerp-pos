package com.amaxonia.pos.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class PagedResponse<T>(
    val data: List<T>,
    val total: Long,
)
