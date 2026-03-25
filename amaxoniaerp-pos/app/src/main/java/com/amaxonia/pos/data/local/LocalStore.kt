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
import com.amaxonia.pos.domain.model.caja.Caja
import com.amaxonia.pos.domain.model.ServerCountries
import com.amaxonia.pos.domain.model.ServerCountry
import com.amaxonia.pos.domain.model.printer.PrinterType
import com.amaxonia.pos.domain.model.printer.TheFactorySettings
import com.amaxonia.pos.ui.payment.PaymentSuccessPayload
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.encodeToString
import java.time.LocalDate

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore("amaxonia_pos")

class LocalStore(
    private val context: Context
) {
    private val authSnapshotKey = stringPreferencesKey("auth_snapshot")
    private val companySessionKey = stringPreferencesKey("company_session")
    private val productsKey = stringPreferencesKey("products_cache")
    private val clientsKey = stringPreferencesKey("clients_cache")
    private val selectedCountryKey = stringPreferencesKey("selected_country_code")
    private val selectedPrinterTypeKey = stringPreferencesKey("selected_printer_type")
    private val theFactoryIpKey = stringPreferencesKey("the_factory_ip")
    private val theFactoryPortKey = stringPreferencesKey("the_factory_port")
    private val theFactoryModeKey = stringPreferencesKey("the_factory_mode")
    private val theFactoryGatewayKey = stringPreferencesKey("the_factory_gateway_key")
    private val theFactoryGatewayLabelKey = stringPreferencesKey("the_factory_gateway_label")
    private val allowEditPricesKey = booleanPreferencesKey("allow_edit_prices")
    private val allowDiscountsKey = booleanPreferencesKey("allow_discounts")
    private val activeCajaKey = stringPreferencesKey("active_caja_snapshot")
    private val lastPaymentSuccessKey = stringPreferencesKey("last_payment_success")
    private val lastPaymentSuccessTransactionIdKey = stringPreferencesKey("last_payment_success_transaction_id")

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
            prefs.remove(activeCajaKey)
        }
    }

    suspend fun saveActiveCaja(caja: Caja) {
        val session = readCompanySession() ?: return
        val snapshot = ActiveCajaSnapshot(
            companyDb = session.company.adminDb,
            date = LocalDate.now().toString(),
            caja = caja
        )
        val json = AppJson.encodeToString(snapshot)
        context.dataStore.edit { prefs ->
            prefs[activeCajaKey] = json
        }
    }

    suspend fun readActiveCajaForToday(): Caja? {
        val json = context.dataStore.data.first()[activeCajaKey] ?: return null
        val snapshot = runCatching {
            AppJson.decodeFromString(ActiveCajaSnapshot.serializer(), json)
        }.getOrNull() ?: run {
            clearActiveCaja()
            return null
        }

        val session = readCompanySession()
        val isSameCompany = session?.company?.adminDb == snapshot.companyDb
        val isToday = snapshot.date == LocalDate.now().toString()
        if (!isSameCompany || !isToday) {
            clearActiveCaja()
            return null
        }
        return snapshot.caja
    }

    suspend fun clearActiveCaja() {
        context.dataStore.edit { prefs ->
            prefs.remove(activeCajaKey)
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

    suspend fun saveSelectedPrinterType(printerType: PrinterType) {
        context.dataStore.edit { prefs ->
            prefs[selectedPrinterTypeKey] = printerType.name
        }
    }

    suspend fun readSelectedPrinterType(): PrinterType {
        return selectedPrinterTypeFlow().first()
    }

    fun selectedPrinterTypeFlow(): Flow<PrinterType> {
        return context.dataStore.data.map { prefs ->
            prefs[selectedPrinterTypeKey]
                ?.let { storedValue -> PrinterType.entries.firstOrNull { it.name == storedValue } }
                ?: PrinterType.NONE
        }
    }

    suspend fun saveTheFactorySettings(settings: TheFactorySettings) {
        context.dataStore.edit { prefs ->
            prefs[theFactoryIpKey] = settings.ipAddress.trim()
            prefs[theFactoryPortKey] = settings.port.trim()
            prefs[theFactoryModeKey] = settings.openMode.trim()
            prefs[theFactoryGatewayKey] = settings.gatewayKey.trim()
            prefs[theFactoryGatewayLabelKey] = settings.gatewayLabel.trim()
        }
    }

    suspend fun readTheFactorySettings(): TheFactorySettings {
        return theFactorySettingsFlow().first()
    }

    fun theFactorySettingsFlow(): Flow<TheFactorySettings> {
        return context.dataStore.data.map { prefs ->
            TheFactorySettings(
                ipAddress = prefs[theFactoryIpKey].orEmpty(),
                port = prefs[theFactoryPortKey].orEmpty(),
                openMode = prefs[theFactoryModeKey].orEmpty(),
                gatewayKey = prefs[theFactoryGatewayKey].orEmpty(),
                gatewayLabel = prefs[theFactoryGatewayLabelKey].orEmpty()
            )
        }
    }

    suspend fun saveAllowEditPrices(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[allowEditPricesKey] = enabled
        }
    }

    suspend fun saveAllowDiscounts(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[allowDiscountsKey] = enabled
        }
    }

    fun allowEditPricesFlow(): Flow<Boolean> {
        return context.dataStore.data.map { prefs ->
            prefs[allowEditPricesKey] ?: false
        }
    }

    fun allowDiscountsFlow(): Flow<Boolean> {
        return context.dataStore.data.map { prefs ->
            prefs[allowDiscountsKey] ?: false
        }
    }

    suspend fun readAllowEditPrices(): Boolean = allowEditPricesFlow().first()
    suspend fun readAllowDiscounts(): Boolean = allowDiscountsFlow().first()

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

    // ============ PAYMENT SUCCESS ============

    suspend fun saveLastPaymentSuccess(payload: PaymentSuccessPayload) {
        val json = AppJson.encodeToString(PaymentSuccessPayload.serializer(), payload)
        context.dataStore.edit { prefs ->
            prefs[lastPaymentSuccessKey] = json
            prefs[lastPaymentSuccessTransactionIdKey] = payload.transactionId
        }
    }

    /**
     * Reads the cached payment success payload.
     * We cache only the most recent transaction to keep preferences storage small.
     */
    suspend fun readLastPaymentSuccess(transactionId: String): PaymentSuccessPayload? {
        val prefs = context.dataStore.data.first()
        val json = prefs[lastPaymentSuccessKey] ?: return null
        val cachedTransactionId = prefs[lastPaymentSuccessTransactionIdKey] ?: return null
        if (cachedTransactionId != transactionId) return null

        return runCatching {
            AppJson.decodeFromString(PaymentSuccessPayload.serializer(), json)
        }.getOrNull()
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
    val payrollDb: String,
    val rif: String = ""
)

@Serializable
data class ActiveCajaSnapshot(
    val companyDb: String,
    val date: String,
    val caja: Caja
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
        payrollDb = payrollDb,
        rif = rif.orEmpty()
    )
}
