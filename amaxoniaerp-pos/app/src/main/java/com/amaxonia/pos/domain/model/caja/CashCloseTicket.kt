package com.amaxonia.pos.domain.model.caja

import com.amaxonia.pos.domain.model.printer.TicketDocument

interface CashCloseTicketFormatter {
    val paymentLabels: List<String>

    fun format(payload: CashCloseTicketPayload): TicketDocument

    fun format(
        payload: CashCloseTicketPayload,
        countryCode: String,
    ): TicketDocument = format(payload)
}

data class CashCloseTicketPayload(
    val companyName: String,
    val companyRuc: String,
    val companyAddress: String,
    val companyPhone: String,
    val sellerName: String,
    val cashRegisterName: String,
    val branchName: String,
    val summary: CierreCajaSummary,
    val paymentAmounts: Map<String, Double>,
    val generalPayments: Double,
    val promotionLines: List<CashClosePromotionLine>,
    val inventoryLines: List<CashCloseInventoryLine>,
    val qrPayload: String,
)

data class CashClosePromotionLine(
    val promotionCode: String,
    val promotionName: String,
    val soldTimes: Double,
)

data class CashCloseInventoryLine(
    val productCode: String,
    val productName: String,
    val initialQuantity: Double,
    val soldQuantity: Double,
    val realQuantity: Double,
)
