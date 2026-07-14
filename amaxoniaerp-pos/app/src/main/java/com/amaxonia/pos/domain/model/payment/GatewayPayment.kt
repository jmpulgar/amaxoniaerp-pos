package com.amaxonia.pos.domain.model.payment

import com.amaxonia.pos.domain.model.TransactionPaymentMethod

const val EXTERNAL_GATEWAY_MARKER = "HKA_RAPID_PAY"

data class GatewayLaunchPayload(
    val packageName: String,
    val activityClassName: String,
    val encryptedCommand: ByteArray,
    val backgroundColor: String,
    val textColor: String,
    val message: String,
)

data class GatewayPaymentRequest(
    val methods: List<TransactionPaymentMethod>,
    val customerIdentifier: String,
    val exchangeRate: Double,
    val isMultiCurrency: Boolean,
)

data class GatewayApproval(
    val approved: Boolean,
    val message: String,
)
