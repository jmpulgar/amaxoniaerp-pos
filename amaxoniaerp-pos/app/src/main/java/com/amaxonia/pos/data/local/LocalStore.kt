package com.amaxonia.pos.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.amaxonia.pos.data.local.security.AndroidKeystoreSecureKeyValueStore
import com.amaxonia.pos.data.local.security.SecureKeyValueStore
import com.amaxonia.pos.data.local.security.VerifiedSecureValueWriter
import com.amaxonia.pos.data.remote.dto.CompanyDetailsDto
import com.amaxonia.pos.data.remote.dto.LoginResponse
import com.amaxonia.pos.domain.model.ServerCountries
import com.amaxonia.pos.domain.model.ServerCountry
import com.amaxonia.pos.domain.model.caja.Caja
import com.amaxonia.pos.domain.model.mesas.Area
import com.amaxonia.pos.domain.model.mesas.AreasResult
import com.amaxonia.pos.domain.model.mesas.Mesa
import com.amaxonia.pos.domain.model.mesas.MesasResult
import com.amaxonia.pos.domain.model.payment.FormaPago
import com.amaxonia.pos.domain.model.printer.PrinterType
import com.amaxonia.pos.domain.model.printer.PrinterTypePolicy
import com.amaxonia.pos.domain.model.tenant.SaleTenant
import com.amaxonia.pos.domain.repository.CompanyTokenReader
import com.amaxonia.pos.domain.repository.CountrySelectionStore
import com.amaxonia.pos.domain.repository.PaymentSessionReader
import com.amaxonia.pos.domain.repository.SalonConfigCache
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore("amaxonia_pos")

/**
 * Almacenamiento local del POS. Los miembros no-override viven como extensiones en
 * archivos del mismo package (LocalStoreAuthSession, LocalStoreSalonCajaCache,
 * LocalStoreCatalogCache, LocalStorePosSettings, LocalStorePaymentSuccess).
 */
class LocalStore(
    context: Context,
    internal val secureStore: SecureKeyValueStore = AndroidKeystoreSecureKeyValueStore(context),
) : CountrySelectionStore,
    PaymentSessionReader,
    SalonConfigCache,
    CompanyTokenReader {
    internal val dataStore = context.applicationContext.dataStore
    internal val secureWriter = VerifiedSecureValueWriter(secureStore)
    internal val authSnapshotKey = stringPreferencesKey("auth_snapshot")
    internal val companySessionKey = stringPreferencesKey("company_session")
    internal val productsKey = stringPreferencesKey("products_cache")
    internal val clientsKey = stringPreferencesKey("clients_cache")
    internal val selectedCountryKey = stringPreferencesKey("selected_country_code")
    internal val selectedPrinterTypeKey = stringPreferencesKey("selected_printer_type")
    internal val theFactoryIpKey = stringPreferencesKey("the_factory_ip")
    internal val theFactoryPortKey = stringPreferencesKey("the_factory_port")
    internal val theFactoryModeKey = stringPreferencesKey("the_factory_mode")
    internal val theFactoryGatewayKey = stringPreferencesKey("the_factory_gateway_key")
    internal val theFactoryGatewayLabelKey = stringPreferencesKey("the_factory_gateway_label")
    internal val theFactoryPrinterSerialKey = stringPreferencesKey("the_factory_printer_serial")
    internal val allowEditPricesKey = booleanPreferencesKey("allow_edit_prices")
    internal val allowDiscountsKey = booleanPreferencesKey("allow_discounts")
    internal val activeCajaKey = stringPreferencesKey("active_caja_snapshot")
    internal val formasPagoKey = stringPreferencesKey("formas_pago_snapshot")
    internal val areasKey = stringPreferencesKey("areas_snapshot")
    internal val mesasKey = stringPreferencesKey("mesas_snapshot")
    internal val lastPaymentSuccessKey = stringPreferencesKey("last_payment_success")
    internal val lastPaymentSuccessTransactionIdKey = stringPreferencesKey("last_payment_success_transaction_id")

    // ============ ÁREAS Y MESAS (configuración de salón) ============

    override suspend fun companyToken(): String? = readCompanySession()?.token

    /**
     * Guarda las áreas de una caja reemplazando por completo el snapshot anterior, de modo que
     * un área desactivada en el administrativo deje de aparecer tras refrescar.
     *
     * Además poda las mesas cacheadas de áreas que ya no existen, para que no queden mesas
     * huérfanas accesibles offline.
     */
    override suspend fun cacheAreas(
        cajaId: String,
        sucursalId: Int,
        areas: List<Area>,
    ) {
        val session = readCompanySession() ?: return
        val snapshot =
            AreasSnapshot(
                companyDb = session.company.adminDb,
                cajaId = cajaId,
                sucursalId = sucursalId,
                areas = areas,
            )
        val validAreaIds = areas.map { it.id }.toSet()
        dataStore.edit { prefs ->
            prefs[areasKey] = AppJson.encodeToString(snapshot)
            val cached = prefs[mesasKey]?.let { decodeMesasSnapshot(it) }
            if (cached != null) {
                prefs[mesasKey] =
                    AppJson.encodeToString(
                        cached.copy(plansByArea = cached.plansByArea.filterKeys { it in validAreaIds }),
                    )
            }
        }
    }

    /** Devuelve el snapshot solo si coincide la empresa y la caja; si no, no hay caché válida. */
    override suspend fun readCachedAreas(cajaId: String): AreasResult? {
        val companyDb = readCompanySession()?.company?.adminDb
        val snapshot =
            dataStore.data
                .first()[areasKey]
                ?.let { decodeAreasSnapshot(it) }
                ?.takeIf { it.companyDb == companyDb && it.cajaId == cajaId }

        return snapshot?.let {
            AreasResult(sucursalId = it.sucursalId, areas = it.areas, fromCache = true)
        }
    }

    override suspend fun cacheMesas(
        cajaId: String,
        areaId: Int,
        lienzo: com.amaxonia.pos.domain.model.mesas.Lienzo,
        imagenUrl: String?,
        mesas: List<Mesa>,
    ) {
        val session = readCompanySession() ?: return
        val companyDb = session.company.adminDb
        dataStore.edit { prefs ->
            val current = prefs[mesasKey]?.let { decodeMesasSnapshot(it) }
            // Si cambió la empresa o la caja se descarta lo anterior en bloque.
            val base =
                current?.takeIf { it.companyDb == companyDb && it.cajaId == cajaId }
                    ?: MesasSnapshot(companyDb = companyDb, cajaId = cajaId, plansByArea = emptyMap())
            prefs[mesasKey] =
                AppJson.encodeToString(
                    base.copy(
                        plansByArea =
                            base.plansByArea +
                                (
                                    areaId to
                                        AreaPlan(
                                            lienzo = lienzo,
                                            imagenUrl = imagenUrl,
                                            mesas = mesas,
                                        )
                                ),
                    ),
                )
        }
    }

    override suspend fun readCachedMesas(
        cajaId: String,
        areaId: Int,
    ): MesasResult? {
        val companyDb = readCompanySession()?.company?.adminDb
        val plan =
            dataStore.data
                .first()[mesasKey]
                ?.let { decodeMesasSnapshot(it) }
                ?.takeIf { it.companyDb == companyDb && it.cajaId == cajaId }
                ?.plansByArea
                ?.get(areaId)
                ?: return null

        return MesasResult(
            areaId = areaId,
            lienzo = plan.lienzo,
            imagenUrl = plan.imagenUrl,
            mesas = plan.mesas,
            fromCache = true,
        )
    }

    override suspend fun currentCountryCode(): String =
        readSelectedCountry()?.code ?: ServerCountries.fromCode(com.amaxonia.pos.BuildConfig.DEFAULT_COUNTRY_CODE)?.code ?: "VE"

    override suspend fun currentUsername(): String = readAuthSnapshot()?.user?.username ?: "POS"

    override suspend fun currentTenant(): SaleTenant? =
        readCompanySession()?.let { session ->
            SaleTenant(
                tenantId = SaleTenant.idFor(session.company.id),
                companyId = session.company.id,
                label = session.company.name,
                adminDb = session.company.adminDb,
                contableDb = session.company.accountingDb,
                nominaDb = session.company.payrollDb,
                countryCode = session.company.countryCode.ifBlank { currentCountryCode() },
            )
        }

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

    internal companion object {
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
    val countryCode: String = "",
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

/**
 * Última configuración válida de áreas descargada. Se indexa por `cajaId` (no por sucursal)
 * porque la caja es lo que el POS conoce antes de pedir datos, y determina la sucursal 1:1.
 * `sucursalId` se guarda solo para mostrarlo y auditar coherencia.
 */
@Serializable
data class AreasSnapshot(
    val companyDb: String,
    val cajaId: String,
    val sucursalId: Int,
    val areas: List<Area>,
)

/**
 * Plan de mesas de un área cacheado: dimensiones del lienzo, URL del dibujo de fondo (lo que
 * el administrativo configuró) y la geometría de las mesas. Se guarda como unidad atómica
 * porque el lienzo/imagen pertenecen al área, no a cada mesa.
 */
@Serializable
data class AreaPlan(
    val lienzo: com.amaxonia.pos.domain.model.mesas.Lienzo =
        com.amaxonia.pos.domain.model.mesas
            .Lienzo(),
    val imagenUrl: String? = null,
    val mesas: List<Mesa> = emptyList(),
)

@Serializable
data class MesasSnapshot(
    val companyDb: String,
    val cajaId: String,
    val plansByArea: Map<Int, AreaPlan> = emptyMap(),
) {
    /**
     * Compatibilidad con snapshots anteriores a la fase del plano: si el JSON viejo trae
     * `mesasByArea`, [NullableUrlSerializer]/kotlinx lo ignoran (AppJson.ignoreUnknownKeys =
     * true) y aquí reconstruimos el campo con defaults seguros para no perder las mesas.
     *
     * En la práctica un usuario con snapshot viejo verá, al refrescar, el plano rellenarse
     * con lienzo e imagen nuevos; si entra offline antes de refrescar, aún podrá seleccionar.
     */
    val mesasByArea: Map<Int, List<Mesa>>
        get() = plansByArea.mapValues { it.value.mesas }
}

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

fun CompanyDetailsDto.toSnapshot(fallbackCountryCode: String = ""): CompanyDetailsSnapshot =
    CompanyDetailsSnapshot(
        id = id,
        name = name,
        adminDb = adminDb,
        accountingDb = accountingDb,
        payrollDb = payrollDb,
        rif = rif.orEmpty(),
        countryCode = countryCode?.takeIf { it.isNotBlank() } ?: fallbackCountryCode,
    )
