package com.amaxonia.erp.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.amaxonia.erp.BuildConfig
import com.amaxonia.erp.data.remote.AppJson
import com.amaxonia.erp.data.remote.dto.LoginResponse
import com.amaxonia.erp.domain.model.Caja
import com.amaxonia.erp.domain.model.ServerCountries
import com.amaxonia.erp.domain.model.ServerCountry
import com.amaxonia.erp.domain.model.printer.PrinterType
import com.amaxonia.erp.domain.model.printer.PrinterTypePolicy
import com.amaxonia.erp.domain.model.printer.TheFactorySettings
import com.amaxonia.erp.domain.repository.CountrySelectionStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import java.time.LocalDate

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore("amaxonia_erp_pos")

open class LocalStore(
    context: Context,
) : CountrySelectionStore {
    internal val dataStore: DataStore<Preferences> by lazy { context.applicationContext.dataStore }
    private val authSessionKey = stringPreferencesKey("auth_session")
    private val selectedCountryKey = stringPreferencesKey("selected_country_code")

    private val companySessionKey = stringPreferencesKey("company_session")
    private val activeCajaSnapshotKey = stringPreferencesKey("active_caja_snapshot")
    private val activeCajaIdKey = stringPreferencesKey("active_caja_id")
    private val activeCajaNameKey = stringPreferencesKey("active_caja_name")
    private val activeSucursalIdKey = stringPreferencesKey("active_sucursal_id")
    private val activeSucursalNameKey = stringPreferencesKey("active_sucursal_name")

    private val selectedPrinterTypeKey = stringPreferencesKey("selected_printer_type")
    private val theFactoryIpKey = stringPreferencesKey("the_factory_ip")
    private val theFactoryPortKey = stringPreferencesKey("the_factory_port")
    private val theFactoryModeKey = stringPreferencesKey("the_factory_mode")
    private val theFactoryGatewayKey = stringPreferencesKey("the_factory_gateway")
    private val theFactoryGatewayLabelKey = stringPreferencesKey("the_factory_gateway_label")
    private val theFactoryPrinterSerialKey = stringPreferencesKey("the_factory_printer_serial")
    private val allowEditPricesKey = booleanPreferencesKey("allow_edit_prices")
    private val allowDiscountsKey = booleanPreferencesKey("allow_discounts")
    private val customerDisplayEnabledKey = booleanPreferencesKey("customer_display_enabled")
    private val autoPrintReceiptKey = booleanPreferencesKey("auto_print_receipt")

    suspend fun saveAuthSession(response: LoginResponse) {
        val json = AppJson.encodeToString(response)
        dataStore.edit { prefs ->
            prefs[authSessionKey] = json
        }
    }

    suspend fun readAuthSession(): LoginResponse? {
        val json = dataStore.data.first()[authSessionKey] ?: return null
        return runCatching { AppJson.decodeFromString<LoginResponse>(json) }.getOrNull()
    }

    suspend fun clearAuthSession() {
        dataStore.edit { prefs ->
            prefs.remove(authSessionKey)
            prefs.remove(companySessionKey)
            prefs.remove(activeCajaSnapshotKey)
            prefs.remove(activeCajaIdKey)
            prefs.remove(activeCajaNameKey)
            prefs.remove(activeSucursalIdKey)
            prefs.remove(activeSucursalNameKey)
        }
    }

    suspend fun saveCompanySession(session: com.amaxonia.erp.domain.model.CompanySession) {
        val snapshot = CompanySessionSnapshot(
            token = session.token,
            companyId = session.company.id,
            companyName = session.company.name,
            adminDb = session.company.adminDb,
            accountingDb = session.company.accountingDb,
            payrollDb = session.company.payrollDb,
            rif = session.company.rif,
            countryCode = session.company.countryCode,
            userId = session.user.id,
            username = session.user.username,
            userRole = session.user.role,
        )
        val json = AppJson.encodeToString(snapshot)
        dataStore.edit { prefs ->
            prefs[companySessionKey] = json
        }
    }

    open suspend fun readCompanySession(): com.amaxonia.erp.domain.model.CompanySession? {
        val json = dataStore.data.first()[companySessionKey] ?: return null
        val snapshot = runCatching { AppJson.decodeFromString<CompanySessionSnapshot>(json) }.getOrNull() ?: return null
        return com.amaxonia.erp.domain.model.CompanySession(
            token = snapshot.token,
            company = com.amaxonia.erp.domain.model.SelectedCompany(
                id = snapshot.companyId,
                name = snapshot.companyName,
                adminDb = snapshot.adminDb,
                accountingDb = snapshot.accountingDb,
                payrollDb = snapshot.payrollDb,
                rif = snapshot.rif,
                countryCode = snapshot.countryCode,
            ),
            user = com.amaxonia.erp.domain.model.AuthUser(
                id = snapshot.userId,
                username = snapshot.username,
                role = snapshot.userRole,
            ),
        )
    }

    suspend fun clearCompanySession() {
        dataStore.edit { prefs ->
            prefs.remove(companySessionKey)
            prefs.remove(activeCajaSnapshotKey)
            prefs.remove(activeCajaIdKey)
            prefs.remove(activeCajaNameKey)
            prefs.remove(activeSucursalIdKey)
            prefs.remove(activeSucursalNameKey)
        }
    }

    open suspend fun saveActiveCaja(caja: Caja) {
        val session = readCompanySession()
        val snapshot = ActiveCajaSnapshot(
            companyDb = session?.company?.adminDb.orEmpty(),
            date = LocalDate.now().toString(),
            caja = caja,
        )
        val json = AppJson.encodeToString(snapshot)
        dataStore.edit { prefs ->
            prefs[activeCajaSnapshotKey] = json
            prefs[activeCajaIdKey] = caja.idCaja
            prefs[activeCajaNameKey] = caja.displayName
        }
    }

    open suspend fun readActiveCajaSnapshot(): Caja? {
        val json = dataStore.data.first()[activeCajaSnapshotKey] ?: return null
        val snapshot = runCatching { AppJson.decodeFromString<ActiveCajaSnapshot>(json) }.getOrNull() ?: return null
        val session = readCompanySession()
        val isSameCompany = session?.company?.adminDb.orEmpty() == snapshot.companyDb
        return if (isSameCompany) {
            snapshot.caja
        } else {
            clearActiveCaja()
            null
        }
    }

    open suspend fun readActiveCajaForToday(): Caja? = readActiveCajaSnapshot()

    open suspend fun clearActiveCaja() {
        dataStore.edit { prefs ->
            prefs.remove(activeCajaSnapshotKey)
            prefs.remove(activeCajaIdKey)
            prefs.remove(activeCajaNameKey)
        }
    }

    suspend fun saveActiveCaja(id: String, name: String) {
        saveActiveCaja(Caja(idCaja = id, caja = name, descripcion = name))
    }

    open suspend fun readActiveCaja(): Pair<String, String>? {
        val cachedCaja = readActiveCajaSnapshot()
        if (cachedCaja != null) {
            return Pair(cachedCaja.idCaja, cachedCaja.displayName)
        }
        val prefs = dataStore.data.first()
        val id = prefs[activeCajaIdKey] ?: return null
        val name = prefs[activeCajaNameKey] ?: ""
        return Pair(id, name)
    }

    suspend fun saveActiveSucursal(id: String, name: String) {
        dataStore.edit { prefs ->
            prefs[activeSucursalIdKey] = id
            prefs[activeSucursalNameKey] = name
        }
    }

    open suspend fun readActiveSucursal(): Pair<String, String>? {
        val prefs = dataStore.data.first()
        val id = prefs[activeSucursalIdKey] ?: return null
        val name = prefs[activeSucursalNameKey] ?: ""
        return Pair(id, name)
    }

    override suspend fun saveSelectedCountry(country: ServerCountry) {
        dataStore.edit { prefs ->
            prefs[selectedCountryKey] = country.code
        }
    }

    override suspend fun readSelectedCountry(): ServerCountry? {
        val code = dataStore.data.first()[selectedCountryKey] ?: return null
        return ServerCountries.fromCode(code)
    }

    fun selectedCountryFlow(): Flow<ServerCountry?> =
        dataStore.data.map { prefs ->
            val code = prefs[selectedCountryKey] ?: BuildConfig.DEFAULT_COUNTRY_CODE
            ServerCountries.fromCode(code)
        }

    suspend fun saveSelectedPrinterType(printerType: PrinterType) {
        val currentCountry = readSelectedCountry()
        PrinterTypePolicy.validate(currentCountry, printerType)
        dataStore.edit { prefs ->
            prefs[selectedPrinterTypeKey] = printerType.name
        }
    }

    suspend fun readSelectedPrinterType(): PrinterType = selectedPrinterTypeFlow().first()

    fun selectedPrinterTypeFlow(): Flow<PrinterType> =
        dataStore.data.map { prefs ->
            val code = prefs[selectedCountryKey] ?: BuildConfig.DEFAULT_COUNTRY_CODE
            val country = ServerCountries.fromCode(code)
            val defaultPrinter =
                when {
                    com.amaxonia.erp.data.printer.sunmi.SunmiDeviceDetector.isSunmiDevice() -> PrinterType.SUNMI_V2
                    com.amaxonia.erp.data.printer.imin.IminDeviceDetector.isIminDevice() -> PrinterType.IMIN_SWIFT
                    else -> PrinterType.NONE
                }
            val stored = prefs[selectedPrinterTypeKey]?.let { name ->
                PrinterType.entries.firstOrNull { it.name == name }
            } ?: defaultPrinter
            PrinterTypePolicy.coerce(country, stored)
        }

    suspend fun saveTheFactorySettings(settings: TheFactorySettings) {
        dataStore.edit { prefs ->
            prefs[theFactoryIpKey] = settings.ipAddress.trim()
            prefs[theFactoryPortKey] = settings.port.trim()
            prefs[theFactoryModeKey] = settings.openMode.trim()
            prefs[theFactoryGatewayKey] = settings.gatewayKey.trim()
            prefs[theFactoryGatewayLabelKey] = settings.gatewayLabel.trim()
            prefs[theFactoryPrinterSerialKey] = settings.printerSerial.trim()
        }
    }

    suspend fun readTheFactorySettings(): TheFactorySettings = theFactorySettingsFlow().first()

    fun theFactorySettingsFlow(): Flow<TheFactorySettings> =
        dataStore.data.map { prefs ->
            TheFactorySettings(
                ipAddress = prefs[theFactoryIpKey].orEmpty(),
                port = prefs[theFactoryPortKey].orEmpty(),
                openMode = prefs[theFactoryModeKey].orEmpty().ifBlank { "HKA20" },
                gatewayKey = prefs[theFactoryGatewayKey].orEmpty(),
                gatewayLabel = prefs[theFactoryGatewayLabelKey].orEmpty(),
                printerSerial = prefs[theFactoryPrinterSerialKey].orEmpty(),
            )
        }

    suspend fun saveAllowEditPrices(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[allowEditPricesKey] = enabled }
    }

    open fun allowEditPricesFlow(): Flow<Boolean> =
        dataStore.data.map { prefs -> prefs[allowEditPricesKey] ?: true }

    open suspend fun readAllowEditPrices(): Boolean = allowEditPricesFlow().first()

    suspend fun saveAllowDiscounts(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[allowDiscountsKey] = enabled }
    }

    open fun allowDiscountsFlow(): Flow<Boolean> =
        dataStore.data.map { prefs -> prefs[allowDiscountsKey] ?: true }

    open suspend fun readAllowDiscounts(): Boolean = allowDiscountsFlow().first()

    suspend fun saveCustomerDisplayEnabled(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[customerDisplayEnabledKey] = enabled }
    }

    open fun customerDisplayEnabledFlow(): Flow<Boolean> =
        dataStore.data.map { prefs -> prefs[customerDisplayEnabledKey] ?: true }

    open suspend fun readCustomerDisplayEnabled(): Boolean = customerDisplayEnabledFlow().first()

    suspend fun saveAutoPrintReceipt(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[autoPrintReceiptKey] = enabled }
    }

    open fun autoPrintReceiptFlow(): Flow<Boolean> =
        dataStore.data.map { prefs -> prefs[autoPrintReceiptKey] ?: true }

    open suspend fun readAutoPrintReceipt(): Boolean = autoPrintReceiptFlow().first()
}

@kotlinx.serialization.Serializable
data class CompanySessionSnapshot(
    val token: String,
    val companyId: Int,
    val companyName: String,
    val adminDb: String = "",
    val accountingDb: String = "",
    val payrollDb: String = "",
    val rif: String? = null,
    val countryCode: String? = null,
    val userId: Int,
    val username: String,
    val userRole: String,
)

@kotlinx.serialization.Serializable
data class ActiveCajaSnapshot(
    val companyDb: String,
    val date: String,
    val caja: Caja,
)
