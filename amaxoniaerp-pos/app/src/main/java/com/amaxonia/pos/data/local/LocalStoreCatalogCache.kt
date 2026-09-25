package com.amaxonia.pos.data.local

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import com.amaxonia.pos.data.remote.dto.ClientDto
import com.amaxonia.pos.data.remote.dto.ProductDto
import kotlinx.coroutines.flow.first
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.encodeToString

// Cachés de catálogo sobre LocalStore: productos/clientes offline y bandera de
// sincronización inicial por compañía. Extensiones sobre LocalStore: mismo
// comportamiento que cuando eran miembros.

suspend fun LocalStore.saveProducts(products: List<ProductDto>) {
    val json = AppJson.encodeToString(products)
    dataStore.edit { prefs ->
        prefs[productsKey] = json
    }
}

suspend fun LocalStore.readProducts(): List<ProductDto> {
    val json = dataStore.data.first()[productsKey] ?: return emptyList()
    return runCatching { AppJson.decodeFromString(ListSerializer(ProductDto.serializer()), json) }
        .getOrDefault(emptyList())
}

suspend fun LocalStore.saveClients(clients: List<ClientDto>) {
    val json = AppJson.encodeToString(clients)
    dataStore.edit { prefs ->
        prefs[clientsKey] = json
    }
}

suspend fun LocalStore.readClients(): List<ClientDto> {
    val json = dataStore.data.first()[clientsKey] ?: return emptyList()
    return runCatching { AppJson.decodeFromString(ListSerializer(ClientDto.serializer()), json) }
        .getOrDefault(emptyList())
}

suspend fun LocalStore.isInitialSyncCompleted(companyId: Int): Boolean {
    val key = booleanPreferencesKey("initial_sync_completed_$companyId")
    return dataStore.data.first()[key] ?: false
}

suspend fun LocalStore.setInitialSyncCompleted(
    companyId: Int,
    completed: Boolean,
) {
    val key = booleanPreferencesKey("initial_sync_completed_$companyId")
    dataStore.edit { prefs ->
        prefs[key] = completed
    }
}

/**
 * Limpia los cachés de productos y clientes en DataStore, así como los indicadores
 * de sincronización inicial de todas las empresas.
 */
suspend fun LocalStore.clearCatalogCache() {
    dataStore.edit { prefs ->
        prefs.remove(productsKey)
        prefs.remove(clientsKey)
        val initialSyncKeys = prefs.asMap().keys.filter { it.name.startsWith("initial_sync_completed_") }
        initialSyncKeys.forEach { prefs.remove(it) }
    }
}
