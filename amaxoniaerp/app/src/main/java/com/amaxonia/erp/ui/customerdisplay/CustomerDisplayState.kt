package com.amaxonia.erp.ui.customerdisplay

import com.amaxonia.erp.ui.pos.CartItem
import com.amaxonia.erp.ui.pos.CompletedSaleInfo

data class CustomerDisplayState(
    val companyName: String = "",
    val branchName: String = "",
    val countryCode: String = "PA",
    val cartItems: List<CartItem> = emptyList(),
    val subtotal: Double = 0.0,
    val tax: Double = 0.0,
    val total: Double = 0.0,
    val clientName: String? = null,
    val isPaymentProcessing: Boolean = false,
    val paymentProcessingMessage: String? = null,
    val completedSaleInfo: CompletedSaleInfo? = null,
) {
    val isIdle: Boolean get() = cartItems.isEmpty() && completedSaleInfo == null && !isPaymentProcessing
    val isSuccess: Boolean get() = completedSaleInfo != null
}
