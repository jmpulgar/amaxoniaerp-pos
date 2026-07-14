package com.amaxonia.pos.domain.model

import java.util.UUID

enum class TaxpayerType(
    val label: String,
) {
    NATURAL("Natural"),
    JURIDICO("Jurídico"),
}

enum class ForeignIdType(
    val label: String,
) {
    TRIBUTARIA("Identificación Tributaria"),
    PASAPORTE("Pasaporte"),
}

data class Client(
    val id: String = UUID.randomUUID().toString(),
    val code: String = "",
    val clientTypeId: Int = 1,
    val taxpayerType: TaxpayerType = TaxpayerType.NATURAL,
    val ruc: String = "",
    val cedula: String = "",
    val dv: String = "",
    val firstName: String = "",
    val lastName: String = "",
    val email: String = "",
    val phone: String = "",
    val registerDate: String = "",
    val status: Boolean = true,
    val foreignIdType: ForeignIdType = ForeignIdType.PASAPORTE,
    val foreignIdNumber: String = "",
    val country: String = "Panamá",
    val countryId: Int = 170,
    val province: String = "",
    val district: String = "",
    val corregimiento: String = "",
    val addressLevel1: String = "",
    val addressLevel2: String = "",
    val addressLevel3: String = "",
    val addressDetail: String = "",
    val city: String = "",
    val locality: String = "",
    val sector: String = "",
    val urbanization: String = "",
    val street: String = "",
    val photoFilename: String = "",
)
