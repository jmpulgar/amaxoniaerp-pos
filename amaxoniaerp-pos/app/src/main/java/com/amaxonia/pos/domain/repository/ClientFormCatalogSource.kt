package com.amaxonia.pos.domain.repository

import com.amaxonia.pos.domain.model.AddressLevel
import com.amaxonia.pos.domain.model.ClientTypeOption
import com.amaxonia.pos.domain.model.Country

data class ClientFormCatalogs(
    val countries: List<Country>,
    val clientTypes: List<ClientTypeOption>,
    val level1: List<AddressLevel>,
    val level2: List<AddressLevel>,
    val level3: List<AddressLevel>,
)

fun interface ClientFormCatalogSource {
    suspend fun load(): ClientFormCatalogs
}
