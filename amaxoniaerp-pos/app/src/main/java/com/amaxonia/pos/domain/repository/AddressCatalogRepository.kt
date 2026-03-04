package com.amaxonia.pos.domain.repository

import com.amaxonia.pos.domain.model.AddressLevel
import com.amaxonia.pos.domain.model.Country

interface AddressCatalogRepository {
    suspend fun getCountries(): List<Country>
    suspend fun getAddressLevel1(countryCode: String): List<AddressLevel>
    suspend fun getAddressLevel2(countryCode: String, level1Code: String): List<AddressLevel>
    suspend fun getAddressLevel3(countryCode: String, level2Code: String): List<AddressLevel>
}
