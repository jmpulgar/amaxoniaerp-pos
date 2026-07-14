package com.amaxonia.pos.data.repository

import com.amaxonia.pos.data.local.db.AddressLevel1Dao
import com.amaxonia.pos.data.local.db.AddressLevel2Dao
import com.amaxonia.pos.data.local.db.AddressLevel3Dao
import com.amaxonia.pos.data.local.db.CountryDao
import com.amaxonia.pos.data.local.db.toDomain
import com.amaxonia.pos.domain.model.AddressLevel
import com.amaxonia.pos.domain.model.Country
import com.amaxonia.pos.domain.repository.AddressCatalogRepository

class LocalAddressCatalogRepository(
    private val countryDao: CountryDao,
    private val addressLevel1Dao: AddressLevel1Dao,
    private val addressLevel2Dao: AddressLevel2Dao,
    private val addressLevel3Dao: AddressLevel3Dao,
) : AddressCatalogRepository {
    override suspend fun getCountries(): List<Country> = countryDao.getAll().map { it.toDomain() }

    override suspend fun getAddressLevel1(countryCode: String): List<AddressLevel> {
        if (countryCode.isBlank()) return emptyList()
        return addressLevel1Dao.getByCountry(countryCode).map { it.toDomain() }
    }

    override suspend fun getAddressLevel2(
        countryCode: String,
        level1Code: String,
    ): List<AddressLevel> {
        if (countryCode.isBlank() || level1Code.isBlank()) return emptyList()
        return addressLevel2Dao.getByLevel1(countryCode, level1Code).map { it.toDomain() }
    }

    override suspend fun getAddressLevel3(
        countryCode: String,
        level2Code: String,
    ): List<AddressLevel> {
        if (countryCode.isBlank() || level2Code.isBlank()) return emptyList()
        return addressLevel3Dao.getByLevel2(countryCode, level2Code).map { it.toDomain() }
    }
}
