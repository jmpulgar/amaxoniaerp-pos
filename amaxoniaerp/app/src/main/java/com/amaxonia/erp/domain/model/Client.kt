package com.amaxonia.erp.domain.model

enum class TaxpayerType(val label: String) {
    NATURAL("Natural"),
    JURIDICO("Jurídico"),
}

data class Client(
    val id: String = "",
    val code: String = "",
    val identification: String = "",
    val dv: String = "",
    val name: String = "",
    val lastName: String = "",
    val email: String = "",
    val phone: String = "",
    val address: String = "",
    val status: Boolean = true,
    val taxpayerType: TaxpayerType = TaxpayerType.NATURAL,
    val clientTypeId: Int = 1,
    val permiteCredito: Boolean = false,
    val diasCredito: Int = 0,
) {
    val fullName: String
        get() = if (lastName.isBlank()) name else "$name $lastName"
}
