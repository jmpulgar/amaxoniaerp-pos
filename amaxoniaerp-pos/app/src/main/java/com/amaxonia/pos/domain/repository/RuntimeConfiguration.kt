package com.amaxonia.pos.domain.repository

import com.amaxonia.pos.domain.model.ServerCountry

interface CountrySelectionStore {
    suspend fun readSelectedCountry(): ServerCountry?

    suspend fun saveSelectedCountry(country: ServerCountry)
}

fun interface ServerEnvironment {
    fun selectCountry(country: ServerCountry)
}
