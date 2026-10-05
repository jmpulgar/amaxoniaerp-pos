package com.amaxonia.erp.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class PagedResponse<T>(
    val data: List<T> = emptyList(),
    val total: Long = 0,
)
