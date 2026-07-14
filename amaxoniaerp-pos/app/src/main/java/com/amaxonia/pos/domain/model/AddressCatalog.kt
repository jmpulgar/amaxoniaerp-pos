package com.amaxonia.pos.domain.model

data class Country(
    val id: Int,
    val iso: String,
    val name: String,
)

data class AddressLevel(
    val countryCode: String,
    val code: String,
    val name: String,
)
