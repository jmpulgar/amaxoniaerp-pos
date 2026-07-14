package com.amaxonia.pos.domain.model

data class DraftInvoice(
    val id: String,
    val clientId: String? = null,
    val clientFirstName: String? = null,
    val clientLastName: String? = null,
    val sellerId: Int = 0,
    val sellerName: String? = null,
    val itemsJson: String,
    val total: Double,
    val itemCount: Int,
    val createdAt: Long,
)
