package com.amaxonia.erp.domain.repository

import com.amaxonia.erp.domain.model.ServerCountry

interface CountrySelectionStore {
    suspend fun saveSelectedCountry(country: ServerCountry)

    suspend fun readSelectedCountry(): ServerCountry?
}
