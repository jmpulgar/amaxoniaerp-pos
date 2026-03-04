package com.amaxonia.pos.ui.clients

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.amaxonia.pos.domain.model.Client
import com.amaxonia.pos.data.local.LocalStore
import com.amaxonia.pos.data.remote.ApiService
import com.amaxonia.pos.data.remote.NetworkMonitor
import com.amaxonia.pos.domain.model.AddressLevel
import com.amaxonia.pos.domain.model.ClientTypeOption
import com.amaxonia.pos.domain.model.Country
import com.amaxonia.pos.domain.model.TaxpayerType
import com.amaxonia.pos.domain.repository.AddressCatalogRepository
import com.amaxonia.pos.domain.repository.ClientRepository
import com.amaxonia.pos.domain.repository.ClientTypeRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ClientFormViewModel(
    private val clientRepository: ClientRepository,
    private val addressCatalogRepository: AddressCatalogRepository,
    private val clientTypeRepository: ClientTypeRepository,
    private val apiService: ApiService,
    private val localStore: LocalStore,
    private val networkMonitor: NetworkMonitor
) : ViewModel() {

    private val _state = MutableStateFlow(ClientFormState())
    val state: StateFlow<ClientFormState> = _state.asStateFlow()

    private var cachedLevel1: List<AddressLevel> = emptyList()
    private var cachedLevel2: List<AddressLevel> = emptyList()
    private var cachedLevel3: List<AddressLevel> = emptyList()

    fun loadClient(clientId: String?) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }

            // 1. LANZAR CARGA ONLINE (FUEGO Y OLVIDO)
            // No usamos .join() para no bloquear la UI. Que carguen en paralelo.
            launch { loadOnlineCatalogs(clientId) }

            // 2. CARGAR CLIENTE INMEDIATAMENTE
            if (clientId == null) {
                _state.update {
                    it.copy(
                        isLoading = false,
                        isEditMode = false,
                        client = Client(id = ""),
                        error = null
                    )
                }
            } else {
                clientRepository.getClientById(clientId).fold(
                    onSuccess = { client ->
                        // Mostramos el cliente YA, aunque los catálogos sigan cargando
                        _state.update {
                            it.copy(
                                isLoading = false,
                                isEditMode = true,
                                client = client,
                                error = null
                            )
                        }
                    },
                    onFailure = { exception ->
                        _state.update { it.copy(isLoading = false, error = exception.message) }
                    }
                )
            }
        }
    }

    private suspend fun loadOnlineCatalogs(clientId: String?) {
        val isOnline = networkMonitor.isOnline()
        val token = localStore.readCompanySession()?.token

        if (isOnline && !token.isNullOrBlank()) {
            try {
                withContext(Dispatchers.IO) {
                    // Peticiones paralelas
                    val countriesDeferred = async { fetchAllCountries(token) }
                    val typesDeferred = async { fetchAllClientTypes(token) }

                    // Niveles de dirección (Traemos todo de una vez para cachear)
                    val l1Deferred = async { fetchAddressLevels(token, 1) }
                    val l2Deferred = async { fetchAddressLevels(token, 2) }
                    val l3Deferred = async { fetchAddressLevels(token, 3) }

                    val countries = countriesDeferred.await()
                    val types = typesDeferred.await()

                    // Actualizamos UI con lo básico (Países y Tipos)
                    _state.update { state ->
                        // Intentamos arreglar el nombre del país del cliente ahora que tenemos la lista
                        val currentClient = state.client
                        val fixedClient = if (clientId != null && currentClient.countryId != 0) {
                            val countryName = countries.find { it.id == currentClient.countryId }?.name
                            if (countryName != null) currentClient.copy(country = countryName) else currentClient
                        } else currentClient

                        state.copy(
                            countries = countries,
                            clientTypes = types,
                            client = fixedClient
                        )
                    }

                    // Guardamos niveles en memoria
                    cachedLevel1 = l1Deferred.await()
                    cachedLevel2 = l2Deferred.await()
                    cachedLevel3 = l3Deferred.await()

                    // Ahora refrescamos los dropdowns de dirección del cliente si ya tiene datos
                    refreshAddressOptions()
                }
            } catch (e: Exception) {
                e.printStackTrace() // Log para ver si falla el JSON
                // Fallback silencioso a local
                loadLocalCatalogs()
            }
        } else {
            loadLocalCatalogs()
        }
    }

    private suspend fun loadLocalCatalogs() {
        val countries = addressCatalogRepository.getCountries()
        val types = clientTypeRepository.getClientTypes()
        _state.update { it.copy(countries = countries, clientTypes = types) }
    }

    // --- MANEJO DE SELECCIONES ---

    fun onCountrySelected(country: Country) {
        viewModelScope.launch {
            // Filtrado en memoria (Instantáneo)
            val level1 = if (cachedLevel1.isNotEmpty()) {
                cachedLevel1.filter { it.countryCode == country.iso }
            } else {
                addressCatalogRepository.getAddressLevel1(country.iso)
            }

            _state.update { state ->
                state.copy(
                    client = state.client.copy(
                        countryId = country.id,
                        country = country.name,
                        addressLevel1 = "",
                        addressLevel2 = "",
                        addressLevel3 = ""
                    ),
                    addressLevel1Options = level1,
                    addressLevel2Options = emptyList(),
                    addressLevel3Options = emptyList()
                )
            }
        }
    }

    fun onAddressLevel1Selected(level: AddressLevel) {
        val countryCode = selectedCountryCode()
        val level2 = if (cachedLevel2.isNotEmpty()) {
            cachedLevel2.filter { it.countryCode == countryCode && it.code.startsWith(level.code) }
        } else emptyList() // Si no hay caché, no bloqueamos (o llamar repo si es crítico)

        _state.update { state ->
            state.copy(
                client = state.client.copy(
                    addressLevel1 = level.code,
                    addressLevel2 = "",
                    addressLevel3 = ""
                ),
                addressLevel2Options = level2,
                addressLevel3Options = emptyList()
            )
        }
    }

    fun onAddressLevel2Selected(level: AddressLevel) {
        val countryCode = selectedCountryCode()
        val level3 = if (cachedLevel3.isNotEmpty()) {
            cachedLevel3.filter { it.countryCode == countryCode && it.code.startsWith(level.code) }
        } else emptyList()

        _state.update { state ->
            state.copy(
                client = state.client.copy(addressLevel2 = level.code, addressLevel3 = ""),
                addressLevel3Options = level3
            )
        }
    }

    fun onAddressLevel3Selected(level: AddressLevel) {
        _state.update { it.copy(client = it.client.copy(addressLevel3 = level.code)) }
    }

    fun onClientTypeSelected(type: ClientTypeOption) {
        _state.update { s ->
            val updatedClient = s.client.copy(
                clientTypeId = type.id,
                taxpayerType = if (type.id == 2 || type.id == 3) TaxpayerType.JURIDICO else s.client.taxpayerType
            )
            s.copy(client = updatedClient)
        }
    }

    fun onTaxpayerTypeChange(type: TaxpayerType) {
        val currentTypeId = _state.value.client.clientTypeId
        if (currentTypeId == 2 || currentTypeId == 3) return
        _state.update { it.copy(client = it.client.copy(taxpayerType = type)) }
    }

    fun updateField(block: Client.() -> Client) {
        _state.update { it.copy(client = it.client.block()) }
    }

    fun saveClient(onSuccess: () -> Unit) {
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true) }
            clientRepository.saveClient(_state.value.client).fold(
                onSuccess = {
                    _state.update { it.copy(isSaving = false) }
                    onSuccess()
                },
                onFailure = { ex ->
                    _state.update { it.copy(isSaving = false, error = ex.message) }
                }
            )
        }
    }

    // --- HELPERS API ---
    private suspend fun fetchAllCountries(token: String): List<Country> {
        return apiService.getCountries(token, limit = 1000, offset = 0, includeTotal = false)
            .map { Country(it.id, it.iso, it.name) }
    }

    private suspend fun fetchAllClientTypes(token: String): List<ClientTypeOption> {
        return apiService.getClientTypes(token, limit = 1000, offset = 0, includeTotal = false)
            .map { ClientTypeOption(it.id, it.name) }
    }

    private suspend fun fetchAddressLevels(token: String, level: Int): List<AddressLevel> {
        return apiService.getAddressLevels(token, level, limit = 1000, offset = 0, includeTotal = false)
            .map { AddressLevel(it.countryCode, it.code, it.name) }
    }

    private fun refreshAddressOptions() {
        val currentState = _state.value
        val client = currentState.client
        val selectedCountry = currentState.countries.firstOrNull { it.id == client.countryId } ?: return

        // Simulamos la selección para llenar las listas basadas en la caché que acabamos de bajar
        val level1Opts = cachedLevel1.filter { it.countryCode == selectedCountry.iso }
        val level2Opts = cachedLevel2.filter { it.countryCode == selectedCountry.iso && it.code.startsWith(client.addressLevel1) }
        val level3Opts = cachedLevel3.filter { it.countryCode == selectedCountry.iso && it.code.startsWith(client.addressLevel2) }

        _state.update {
            it.copy(
                addressLevel1Options = level1Opts,
                addressLevel2Options = level2Opts,
                addressLevel3Options = level3Opts
            )
        }
    }

    private fun selectedCountryCode(): String {
        return _state.value.countries.firstOrNull { it.id == _state.value.client.countryId }?.iso.orEmpty()
    }
}
