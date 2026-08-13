package com.amaxonia.pos.domain.usecase.payment

enum class PaymentCondition(
    val wireValue: String,
) {
    CONTADO("contado"),
    CREDITO("credito"),
}
