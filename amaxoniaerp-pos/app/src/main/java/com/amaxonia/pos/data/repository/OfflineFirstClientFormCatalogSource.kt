package com.amaxonia.pos.data.repository

import com.amaxonia.pos.data.local.LocalStore
import com.amaxonia.pos.data.remote.ApiService
import com.amaxonia.pos.data.remote.NetworkMonitor
import com.amaxonia.pos.domain.model.AddressLevel
import com.amaxonia.pos.domain.model.ClientTypeOption
import com.amaxonia.pos.domain.model.Country
import com.amaxonia.pos.domain.repository.AddressCatalogRepository
import com.amaxonia.pos.domain.repository.ClientFormCatalogSource
import com.amaxonia.pos.domain.repository.ClientFormCatalogs
import com.amaxonia.pos.domain.repository.ClientTypeRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

class OfflineFirstClientFormCatalogSource(
    private val apiService: ApiService,
    private val localStore: LocalStore,
    private val networkMonitor: NetworkMonitor,
    private val localAddressCatalogs: AddressCatalogRepository,
    private val localClientTypes: ClientTypeRepository,
) : ClientFormCatalogSource {
    override suspend fun load(): ClientFormCatalogs {
        val token = localStore.readCompanySession()?.token
        if (!networkMonitor.isOnline() || token.isNullOrBlank()) return loadLocal()

        return runCatching { loadRemote(token) }.getOrElse { loadLocal() }
    }

    private suspend fun loadRemote(token: String): ClientFormCatalogs =
        coroutineScope {
            val countries = async { apiService.getCountries(token, 1000, 0, false) }
            val types = async { apiService.getClientTypes(token, 1000, 0, false) }
            val level1 = async { apiService.getAddressLevels(token, 1, 1000, 0, false) }
            val level2 = async { apiService.getAddressLevels(token, 2, 1000, 0, false) }
            val level3 = async { apiService.getAddressLevels(token, 3, 1000, 0, false) }
            ClientFormCatalogs(
                countries = countries.await().map { Country(it.id, it.iso, it.name) },
                clientTypes = types.await().map { ClientTypeOption(it.id, it.name) },
                level1 = level1.await().map { AddressLevel(it.countryCode, it.code, it.name) },
                level2 = level2.await().map { AddressLevel(it.countryCode, it.code, it.name) },
                level3 = level3.await().map { AddressLevel(it.countryCode, it.code, it.name) },
            )
        }

    private suspend fun loadLocal(): ClientFormCatalogs =
        ClientFormCatalogs(
            countries = localAddressCatalogs.getCountries(),
            clientTypes = localClientTypes.getClientTypes(),
            level1 = emptyList(),
            level2 = emptyList(),
            level3 = emptyList(),
        )
}
