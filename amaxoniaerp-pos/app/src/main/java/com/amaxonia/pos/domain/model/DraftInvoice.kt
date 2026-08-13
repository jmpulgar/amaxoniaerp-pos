package com.amaxonia.pos.domain.model

import com.amaxonia.pos.domain.model.sales.SaleTaxDto

data class SaleFinancialSnapshot(
    val subtotalGross: Double,
    val itemDiscounts: Double,
    val subtotalNet: Double,
    val tax: Double,
    val total: Double,
    val taxLines: List<SaleTaxDto> = emptyList(),
)

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
    val subtotalGross: Double = total,
    val itemDiscounts: Double = 0.0,
    val subtotalNet: Double = total,
    val tax: Double = 0.0,
)

val DraftInvoice.financialSnapshot: SaleFinancialSnapshot
    get() =
        SaleFinancialSnapshot(
            subtotalGross = subtotalGross,
            itemDiscounts = itemDiscounts,
            subtotalNet = subtotalNet,
            tax = tax,
            total = total,
        )
