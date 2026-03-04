package com.amaxonia.pos.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.amaxonia.pos.data.remote.dto.ClientDto
import com.amaxonia.pos.data.remote.dto.CompanyDetailsDto
import com.amaxonia.pos.data.remote.dto.LoginResponse
import com.amaxonia.pos.data.remote.dto.ProductDto
import com.amaxonia.pos.domain.model.ServerCountries
import com.amaxonia.pos.domain.model.ServerCountry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.encodeToString

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore("amaxonia_pos")

class LocalStore(
    private val context: Context
) {
    private val authSnapshotKey = stringPreferencesKey("auth_snapshot")
    private val companySessionKey = stringPreferencesKey("company_session")
    private val productsKey = stringPreferencesKey("products_cache")
    private val clientsKey = stringPreferencesKey("clients_cache")
    private val selectedCountryKey = stringPreferencesKey("selected_country_code")

    suspend fun saveAuthSnapshot(snapshot: AuthSnapshot) {
        val json = AppJson.encodeToString(snapshot)
        context.dataStore.edit { prefs ->
            prefs[authSnapshotKey] = json
        }
    }

    suspend fun readAuthSnapshot(): AuthSnapshot? {
        val json = context.dataStore.data.first()[authSnapshotKey] ?: return null
        return runCatching { AppJson.decodeFromString(AuthSnapshot.serializer(), json) }.getOrNull()
    }

    suspend fun saveCompanySession(session: CompanySessionSnapshot) {
        val json = AppJson.encodeToString(session)
        context.dataStore.edit { prefs ->
            prefs[companySessionKey] = json
        }
    }

    suspend fun readCompanySession(): CompanySessionSnapshot? {
        val json = context.dataStore.data.first()[companySessionKey] ?: return null
        return runCatching { AppJson.decodeFromString(CompanySessionSnapshot.serializer(), json) }.getOrNull()
    }

    suspend fun clearAuthSession() {
        context.dataStore.edit { prefs ->
            prefs.remove(authSnapshotKey)
            prefs.remove(companySessionKey)
        }
    }

    suspend fun isInitialSyncCompleted(companyId: Int): Boolean {
        val key = booleanPreferencesKey("initial_sync_completed_$companyId")
        return context.dataStore.data.first()[key] ?: false
    }

    suspend fun setInitialSyncCompleted(companyId: Int, completed: Boolean) {
        val key = booleanPreferencesKey("initial_sync_completed_$companyId")
        context.dataStore.edit { prefs ->
            prefs[key] = completed
        }
    }

    suspend fun saveProducts(products: List<ProductDto>) {
        val json = AppJson.encodeToString(products)
        context.dataStore.edit { prefs ->
            prefs[productsKey] = json
        }
    }

    suspend fun readProducts(): List<ProductDto> {
        val json = context.dataStore.data.first()[productsKey] ?: return emptyList()
        return runCatching { AppJson.decodeFromString(ListSerializer(ProductDto.serializer()), json) }
            .getOrDefault(emptyList())
    }

    suspend fun saveClients(clients: List<ClientDto>) {
        val json = AppJson.encodeToString(clients)
        context.dataStore.edit { prefs ->
            prefs[clientsKey] = json
        }
    }

    suspend fun readClients(): List<ClientDto> {
        val json = context.dataStore.data.first()[clientsKey] ?: return emptyList()
        return runCatching { AppJson.decodeFromString(ListSerializer(ClientDto.serializer()), json) }
            .getOrDefault(emptyList())
    }

    // ============ COUNTRY SELECTION ============

    /**
     * Guarda el país seleccionado por el usuario
     */
    suspend fun saveSelectedCountry(country: ServerCountry) {
        context.dataStore.edit { prefs ->
            prefs[selectedCountryKey] = country.code
        }
    }

    /**
     * Lee el país seleccionado previamente
     */
    suspend fun readSelectedCountry(): ServerCountry? {
        val code = context.dataStore.data.first()[selectedCountryKey] ?: return null
        return ServerCountries.fromCode(code)
    }

    /**
     * Flow observable del país seleccionado
     */
    fun selectedCountryFlow(): Flow<ServerCountry?> {
        return context.dataStore.data.map { prefs ->
            prefs[selectedCountryKey]?.let { ServerCountries.fromCode(it) }
        }
    }

    /**
     * Limpia la selección de país (logout o reset)
     */
    suspend fun clearSelectedCountry() {
        context.dataStore.edit { prefs ->
            prefs.remove(selectedCountryKey)
        }
    }
}

@Serializable
data class AuthSnapshot(
    val token: String,
    val user: AuthUserSnapshot,
    val companies: List<CompanySnapshot>
)

@Serializable
data class AuthUserSnapshot(
    val id: Int,
    val username: String,
    val role: String
)

@Serializable
data class CompanySnapshot(
    val id: Int,
    val name: String,
    val rif: String? = null
)

@Serializable
data class CompanySessionSnapshot(
    val token: String,
    val company: CompanyDetailsSnapshot
)

@Serializable
data class CompanyDetailsSnapshot(
    val id: Int,
    val name: String,
    val adminDb: String,
    val accountingDb: String,
    val payrollDb: String
)

fun LoginResponse.toSnapshot(): AuthSnapshot {
    return AuthSnapshot(
        token = token,
        user = AuthUserSnapshot(
            id = user.id,
            username = user.username,
            role = user.role
        ),
        companies = companies.map { CompanySnapshot(id = it.id, name = it.name, rif = it.rif) }
    )
}

fun CompanyDetailsDto.toSnapshot(): CompanyDetailsSnapshot {
    return CompanyDetailsSnapshot(
        id = id,
        name = name,
        adminDb = adminDb,
        accountingDb = accountingDb,
        payrollDb = payrollDb
    )
}
