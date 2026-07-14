package com.amaxonia.pos.data.remote

import com.amaxonia.pos.domain.model.ServerCountry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Gestiona la URL base de la API (única para todos los países) y el país seleccionado (VE/PA).
 */
class ApiConfigManager {
    private val _currentCountry = MutableStateFlow<ServerCountry?>(null)
    val currentCountry: StateFlow<ServerCountry?> = _currentCountry.asStateFlow()

    private val _baseUrl = MutableStateFlow(DEFAULT_BASE_URL)
    val baseUrl: StateFlow<String> = _baseUrl.asStateFlow()

    companion object {
        /** Una sola URL para el backend; debe coincidir con ApiConfig.baseUrl. */
        val DEFAULT_BASE_URL: String = ApiConfig.baseUrl

        // Singleton instance para acceso global cuando sea necesario
        @Volatile
        private var instance: ApiConfigManager? = null

        fun getInstance(): ApiConfigManager =
            instance ?: synchronized(this) {
                instance ?: ApiConfigManager().also { instance = it }
            }
    }

    /**
     * Actualiza la URL base según el país seleccionado
     * Esta función debe llamarse ANTES de realizar cualquier petición de login
     */
    fun updateBaseUrl(country: ServerCountry) {
        _currentCountry.value = country
        _baseUrl.value = country.baseUrl.takeIf { it.isNotBlank() } ?: DEFAULT_BASE_URL
    }

    /**
     * Actualiza directamente la URL base (para casos especiales o testing)
     */
    fun updateBaseUrl(url: String) {
        _baseUrl.value = url
    }

    /**
     * Obtiene el país actualmente seleccionado
     */
    fun getCurrentCountry(): ServerCountry? = _currentCountry.value

    /**
     * Obtiene el código de país actual para enviar en headers
     */
    fun getCurrentCountryCode(): String = _currentCountry.value?.code ?: "VE"

    /**
     * Verifica si se ha seleccionado un país
     */
    fun hasCountrySelected(): Boolean = _currentCountry.value != null

    /**
     * Resetea la configuración a valores por defecto
     */
    fun reset() {
        _currentCountry.value = null
        _baseUrl.value = DEFAULT_BASE_URL
    }
}
