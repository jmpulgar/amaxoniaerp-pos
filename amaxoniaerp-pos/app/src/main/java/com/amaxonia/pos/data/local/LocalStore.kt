package com.amaxonia.pos.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.amaxonia.pos.core.logging.SafeLog
import com.amaxonia.pos.data.local.security.AndroidKeystoreSecureKeyValueStore
import com.amaxonia.pos.data.local.security.SecureKeyValueStore
import com.amaxonia.pos.data.local.security.VerifiedSecureValueWriter
import com.amaxonia.pos.data.remote.dto.ClientDto
import com.amaxonia.pos.data.remote.dto.CompanyDetailsDto
import com.amaxonia.pos.data.remote.dto.LoginResponse
import com.amaxonia.pos.data.remote.dto.ProductDto
import com.amaxonia.pos.domain.model.ServerCountries
import com.amaxonia.pos.domain.model.ServerCountry
import com.amaxonia.pos.domain.model.caja.Caja
import com.amaxonia.pos.domain.model.payment.FormaPago
import com.amaxonia.pos.domain.model.payment.PaymentSuccessPayload
import com.amaxonia.pos.domain.model.printer.PrinterType
import com.amaxonia.pos.domain.model.printer.PrinterTypePolicy
import com.amaxonia.pos.domain.model.printer.TheFactorySettings
import com.amaxonia.pos.domain.repository.CountrySelectionStore
import com.amaxonia.pos.domain.repository.PaymentSessionReader
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.encodeToString
import java.time.LocalDate

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore("amaxonia_pos")

class LocalStore(
    context: Context,
    private val secureStore: SecureKeyValueStore = AndroidKeystoreSecureKeyValueStore(context),
) : CountrySelectionStore,
    PaymentSessionReader {
    private val dataStore = context.applicationContext.dataStore
    private val secureWriter = VerifiedSecureValueWriter(secureStore)
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
    private val theFactoryPrinterSerialKey = stringPreferencesKey("the_factory_printer_serial")
    private val allowEditPricesKey = booleanPreferencesKey("allow_edit_prices")
    private val allowDiscountsKey = booleanPreferencesKey("allow_discounts")
    private val activeCajaKey = stringPreferencesKey("active_caja_snapshot")
    private val formasPagoKey = stringPreferencesKey("formas_pago_snapshot")
    private val lastPaymentSuccessKey = stringPreferencesKey("last_payment_success")
    private val lastPaymentSuccessTransactionIdKey = stringPreferencesKey("last_payment_success_transaction_id")

    suspend fun saveAuthSnapshot(snapshot: AuthSnapshot) {
        val json = AppJson.encodeToString(snapshot)
        writeSecureAndRemoveLegacy(SECURE_AUTH_SNAPSHOT, authSnapshotKey, json)
    }

    suspend fun readAuthSnapshot(): AuthSnapshot? {
        val json = readSecureOrMigrate(SECURE_AUTH_SNAPSHOT, authSnapshotKey) ?: return null
        return runCatching { AppJson.decodeFromString(AuthSnapshot.serializer(), json) }
            .onFailure { SafeLog.e(TAG, "Stored authentication snapshot is invalid", it) }
            .getOrNull()
    }

    suspend fun saveCompanySession(session: CompanySessionSnapshot) {
        val json = AppJson.encodeToString(session)
        writeSecureAndRemoveLegacy(SECURE_COMPANY_SESSION, companySessionKey, json)
    }

    suspend fun readCompanySession(): CompanySessionSnapshot? {
        val json = readSecureOrMigrate(SECURE_COMPANY_SESSION, companySessionKey) ?: return null
        return runCatching { AppJson.decodeFromString(CompanySessionSnapshot.serializer(), json) }
            .onFailure { SafeLog.e(TAG, "Stored company session is invalid", it) }
            .getOrNull()
    }

    suspend fun clearAuthSession() {
        removeSecureValue(SECURE_AUTH_SNAPSHOT)
        removeSecureValue(SECURE_COMPANY_SESSION)
        dataStore.edit { prefs ->
            prefs.remove(authSnapshotKey)
            prefs.remove(companySessionKey)
            prefs.remove(activeCajaKey)
        }
    }

    suspend fun saveActiveCaja(caja: Caja) {
        val session = readCompanySession() ?: return
        val snapshot =
            ActiveCajaSnapshot(
                companyDb = session.company.adminDb,
                date = LocalDate.now().toString(),
                caja = caja,
            )
        val json = AppJson.encodeToString(snapshot)
        dataStore.edit { prefs ->
            prefs[activeCajaKey] = json
        }
    }

    suspend fun readActiveCajaForToday(): Caja? {
        val json = dataStore.data.first()[activeCajaKey] ?: return null
        val snapshot =
            runCatching {
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
        dataStore.edit { prefs ->
            prefs.remove(activeCajaKey)
        }
    }

    suspend fun saveFormasPago(
        cajaId: String?,
        formasPago: List<FormaPago>,
    ) {
        val session = readCompanySession() ?: return
        val snapshot =
            FormasPagoSnapshot(
                companyDb = session.company.adminDb,
                cajaId = cajaId,
                formasPago = formasPago,
            )
        val json = AppJson.encodeToString(snapshot)
        dataStore.edit { prefs ->
            prefs[formasPagoKey] = json
        }
    }

    suspend fun readFormasPago(cajaId: String?): List<FormaPago> {
        val json = dataStore.data.first()[formasPagoKey] ?: return emptyList()
        val snapshot =
            runCatching {
                AppJson.decodeFromString(FormasPagoSnapshot.serializer(), json)
            }.getOrNull() ?: return emptyList()

        val session = readCompanySession()
        val isSameCompany = session?.company?.adminDb == snapshot.companyDb
        val isSameCaja = snapshot.cajaId == cajaId
        return if (isSameCompany && isSameCaja) snapshot.formasPago else emptyList()
    }

    override suspend fun currentCountryCode(): String = readSelectedCountry()?.code ?: "VE"

    override suspend fun currentUsername(): String = readAuthSnapshot()?.user?.username ?: "POS"

    suspend fun isInitialSyncCompleted(companyId: Int): Boolean {
        val key = booleanPreferencesKey("initial_sync_completed_$companyId")
        return dataStore.data.first()[key] ?: false
    }

    suspend fun setInitialSyncCompleted(
        companyId: Int,
        completed: Boolean,
    ) {
        val key = booleanPreferencesKey("initial_sync_completed_$companyId")
        dataStore.edit { prefs ->
            prefs[key] = completed
        }
    }

    suspend fun saveProducts(products: List<ProductDto>) {
        val json = AppJson.encodeToString(products)
        dataStore.edit { prefs ->
            prefs[productsKey] = json
        }
    }

    suspend fun readProducts(): List<ProductDto> {
        val json = dataStore.data.first()[productsKey] ?: return emptyList()
        return runCatching { AppJson.decodeFromString(ListSerializer(ProductDto.serializer()), json) }
            .getOrDefault(emptyList())
    }

    suspend fun saveClients(clients: List<ClientDto>) {
        val json = AppJson.encodeToString(clients)
        dataStore.edit { prefs ->
            prefs[clientsKey] = json
        }
    }

    suspend fun readClients(): List<ClientDto> {
        val json = dataStore.data.first()[clientsKey] ?: return emptyList()
        return runCatching { AppJson.decodeFromString(ListSerializer(ClientDto.serializer()), json) }
            .getOrDefault(emptyList())
    }

    suspend fun saveSelectedPrinterType(printerType: PrinterType) {
        PrinterTypePolicy.validate(readSelectedCountry(), printerType)
        dataStore.edit { prefs ->
            prefs[selectedPrinterTypeKey] = printerType.name
        }
    }

    suspend fun readSelectedPrinterType(): PrinterType = selectedPrinterTypeFlow().first()

    fun selectedPrinterTypeFlow(): Flow<PrinterType> =
        dataStore.data.map { prefs ->
            val country = prefs[selectedCountryKey]?.let { ServerCountries.fromCode(it) }
            val storedPrinter =
                prefs[selectedPrinterTypeKey]
                    ?.let { storedValue -> PrinterType.entries.firstOrNull { it.name == storedValue } }
                    ?: PrinterType.NONE
            PrinterTypePolicy.coerce(country, storedPrinter)
        }

    suspend fun saveTheFactorySettings(settings: TheFactorySettings) {
        val gatewayKey = settings.gatewayKey.trim()
        if (gatewayKey.isEmpty()) {
            removeSecureValue(SECURE_GATEWAY_KEY)
        } else {
            secureWriter.write(SECURE_GATEWAY_KEY, gatewayKey)
        }
        dataStore.edit { prefs ->
            prefs[theFactoryIpKey] = settings.ipAddress.trim()
            prefs[theFactoryPortKey] = settings.port.trim()
            prefs[theFactoryModeKey] = settings.openMode.trim()
            prefs.remove(theFactoryGatewayKey)
            prefs[theFactoryGatewayLabelKey] = settings.gatewayLabel.trim()
            prefs[theFactoryPrinterSerialKey] = settings.printerSerial.trim()
        }
    }

    suspend fun readTheFactorySettings(): TheFactorySettings = theFactorySettingsFlow().first()

    fun theFactorySettingsFlow(): Flow<TheFactorySettings> =
        flow {
            readSecureOrMigrate(SECURE_GATEWAY_KEY, theFactoryGatewayKey)
            emitAll(
                dataStore.data.map { prefs ->
                    TheFactorySettings(
                        ipAddress = prefs[theFactoryIpKey].orEmpty(),
                        port = prefs[theFactoryPortKey].orEmpty(),
                        openMode = prefs[theFactoryModeKey].orEmpty(),
                        gatewayKey = readSecureValue(SECURE_GATEWAY_KEY).orEmpty(),
                        gatewayLabel = prefs[theFactoryGatewayLabelKey].orEmpty(),
                        printerSerial = prefs[theFactoryPrinterSerialKey].orEmpty(),
                    )
                },
            )
        }

    private suspend fun writeSecureAndRemoveLegacy(
        secureKey: String,
        legacyKey: Preferences.Key<String>,
        value: String,
    ) {
        secureWriter.write(secureKey, value)
        dataStore.edit { prefs -> prefs.remove(legacyKey) }
    }

    private suspend fun readSecureOrMigrate(
        secureKey: String,
        legacyKey: Preferences.Key<String>,
    ): String? {
        readSecureValue(secureKey)?.let { secureValue ->
            if (dataStore.data.first()[legacyKey] != null) {
                dataStore.edit { prefs -> prefs.remove(legacyKey) }
            }
            return secureValue
        }

        val legacyValue = dataStore.data.first()[legacyKey] ?: return null
        return try {
            secureWriter.write(secureKey, legacyValue)
            dataStore.edit { prefs -> prefs.remove(legacyKey) }
            legacyValue
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            SafeLog.e(TAG, "Secure migration failed; legacy value was preserved", error)
            legacyValue
        }
    }

    private fun readSecureValue(key: String): String? =
        runCatching { secureStore.readString(key) }
            .onFailure { SafeLog.e(TAG, "Secure value could not be read", it) }
            .getOrNull()

    private fun removeSecureValue(key: String) {
        runCatching { secureStore.remove(key) }
            .onFailure { SafeLog.e(TAG, "Secure value could not be removed", it) }
            .getOrThrow()
    }

    suspend fun saveAllowEditPrices(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[allowEditPricesKey] = enabled
        }
    }

    suspend fun saveAllowDiscounts(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[allowDiscountsKey] = enabled
        }
    }

    fun allowEditPricesFlow(): Flow<Boolean> =
        dataStore.data.map { prefs ->
            prefs[allowEditPricesKey] ?: false
        }

    fun allowDiscountsFlow(): Flow<Boolean> =
        dataStore.data.map { prefs ->
            prefs[allowDiscountsKey] ?: false
        }

    suspend fun readAllowEditPrices(): Boolean = allowEditPricesFlow().first()

    suspend fun readAllowDiscounts(): Boolean = allowDiscountsFlow().first()

    // ============ COUNTRY SELECTION ============

    /**
     * Guarda el país seleccionado por el usuario
     */
    override suspend fun saveSelectedCountry(country: ServerCountry) {
        dataStore.edit { prefs ->
            prefs[selectedCountryKey] = country.code
            val storedPrinter =
                prefs[selectedPrinterTypeKey]
                    ?.let { storedValue -> PrinterType.entries.firstOrNull { it.name == storedValue } }
                    ?: PrinterType.NONE
            val coercedPrinter = PrinterTypePolicy.coerce(country, storedPrinter)
            if (coercedPrinter != storedPrinter) {
                prefs[selectedPrinterTypeKey] = coercedPrinter.name
            }
        }
    }

    /**
     * Lee el país seleccionado previamente
     */
    override suspend fun readSelectedCountry(): ServerCountry? {
        val code = dataStore.data.first()[selectedCountryKey] ?: return null
        return ServerCountries.fromCode(code)
    }

    /**
     * Flow observable del país seleccionado
     */
    fun selectedCountryFlow(): Flow<ServerCountry?> =
        dataStore.data.map { prefs ->
            prefs[selectedCountryKey]?.let { ServerCountries.fromCode(it) }
        }

    /**
     * Limpia la selección de país (logout o reset)
     */
    suspend fun clearSelectedCountry() {
        dataStore.edit { prefs ->
            prefs.remove(selectedCountryKey)
        }
    }

    // ============ PAYMENT SUCCESS ============

    suspend fun saveLastPaymentSuccess(payload: PaymentSuccessPayload) {
        val json = AppJson.encodeToString(PaymentSuccessPayload.serializer(), payload)
        dataStore.edit { prefs ->
            prefs[lastPaymentSuccessKey] = json
            prefs[lastPaymentSuccessTransactionIdKey] = payload.transactionId
        }
    }

    /**
     * Reads the cached payment success payload.
     * We cache only the most recent transaction to keep preferences storage small.
     */
    suspend fun readLastPaymentSuccess(transactionId: String): PaymentSuccessPayload? {
        val prefs = dataStore.data.first()
        val json = prefs[lastPaymentSuccessKey] ?: return null
        val cachedTransactionId = prefs[lastPaymentSuccessTransactionIdKey] ?: return null
        if (cachedTransactionId != transactionId) return null

        return runCatching {
            AppJson.decodeFromString(PaymentSuccessPayload.serializer(), json)
        }.getOrNull()
    }

    private companion object {
        const val TAG = "LocalStore"
        const val SECURE_AUTH_SNAPSHOT = "auth_snapshot_v1"
        const val SECURE_COMPANY_SESSION = "company_session_v1"
        const val SECURE_GATEWAY_KEY = "gateway_key_v1"
    }
}

@Serializable
data class AuthSnapshot(
    val token: String,
    val user: AuthUserSnapshot,
    val companies: List<CompanySnapshot>,
)

@Serializable
data class AuthUserSnapshot(
    val id: Int,
    val username: String,
    val role: String,
)

@Serializable
data class CompanySnapshot(
    val id: Int,
    val name: String,
    val rif: String? = null,
)

@Serializable
data class CompanySessionSnapshot(
    val token: String,
    val company: CompanyDetailsSnapshot,
)

@Serializable
data class CompanyDetailsSnapshot(
    val id: Int,
    val name: String,
    val adminDb: String,
    val accountingDb: String,
    val payrollDb: String,
    val rif: String = "",
)

@Serializable
data class ActiveCajaSnapshot(
    val companyDb: String,
    val date: String,
    val caja: Caja,
)

@Serializable
data class FormasPagoSnapshot(
    val companyDb: String,
    val cajaId: String?,
    val formasPago: List<FormaPago>,
)

fun LoginResponse.toSnapshot(): AuthSnapshot =
    AuthSnapshot(
        token = token,
        user =
            AuthUserSnapshot(
                id = user.id,
                username = user.username,
                role = user.role,
            ),
        companies = companies.map { CompanySnapshot(id = it.id, name = it.name, rif = it.rif) },
    )

fun CompanyDetailsDto.toSnapshot(): CompanyDetailsSnapshot =
    CompanyDetailsSnapshot(
        id = id,
        name = name,
        adminDb = adminDb,
        accountingDb = accountingDb,
        payrollDb = payrollDb,
        rif = rif.orEmpty(),
    )
