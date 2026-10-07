package com.amaxonia.kiosk.core.network

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val TAG = "KioskTokenStorage"

/**
 * Stores the kiosk session (system user login + company token + caja) in encrypted preferences.
 *
 * The Android Keystore on API 28 (and some vendor keystores) can fail after an OTA, a restore or
 * key invalidation (KeyStoreException, AEADBadTagException, InvalidProtocolBufferException…).
 * Every open, read and write is guarded: on failure the corrupted file and master key are deleted
 * and recreated. The worst case is losing the session (the kiosk shows the login screen again);
 * the app never crashes. Opening is lazy — call [warmUp] off the main thread at startup.
 *
 * Installs from 0.0.1/0.0.2 only hold the old pairing credentials (device id/token): [warmUp]
 * drops them, so those kiosks simply land on the login screen.
 */
class KioskTokenStorage(
    context: Context? = null,
    private val prefsFactory: SecurePrefsFactory? = context?.let { EncryptedPrefsFactory(it.applicationContext) },
) {
    private val lock = Any()
    private var prefs: SharedPreferences? = null
    private var opened = false
    private val inMemoryMap = mutableMapOf<String, String>()

    private val _isReady = MutableStateFlow(prefsFactory == null)

    /** True once the secure preferences were opened (or recovered). */
    val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

    /** Company token (`POST auth/company`); it does not expire, a revoked one answers 401. */
    var authToken: String?
        get() = read(KEY_AUTH_TOKEN)
        set(value) = save(KEY_AUTH_TOKEN, value)

    var userId: String?
        get() = read(KEY_USER_ID)
        set(value) = save(KEY_USER_ID, value)

    var username: String?
        get() = read(KEY_USERNAME)
        set(value) = save(KEY_USERNAME, value)

    var companyId: String?
        get() = read(KEY_COMPANY_ID)
        set(value) = save(KEY_COMPANY_ID, value)

    var companyName: String?
        get() = read(KEY_COMPANY_NAME)
        set(value) = save(KEY_COMPANY_NAME, value)

    /** Company admin database (`currentCompany.adminDb`), sent as `Company-DB` to `api/cajas`. */
    var companyDb: String?
        get() = read(KEY_COMPANY_DB)
        set(value) = save(KEY_COMPANY_DB, value)

    var countryCode: String?
        get() = read(KEY_COUNTRY_CODE)
        set(value) = save(KEY_COUNTRY_CODE, value)

    var serverUrl: String?
        get() = read(KEY_SERVER_URL)
        set(value) = save(KEY_SERVER_URL, value)

    /** `idCaja` of the register this kiosk sells on (sent as `X-Kiosk-Caja`). */
    var cajaId: String?
        get() = read(KEY_CAJA_ID)
        set(value) = save(KEY_CAJA_ID, value)

    var cajaName: String?
        get() = read(KEY_CAJA_NAME)
        set(value) = save(KEY_CAJA_NAME, value)

    /** Order number prefix (K1, K2…), sent as `X-Kiosk-Prefix`. */
    var prefix: String?
        get() = read(KEY_PREFIX)
        set(value) = save(KEY_PREFIX, value)

    var configEtag: String?
        get() = read(KEY_CONFIG_ETAG)
        set(value) = save(KEY_CONFIG_ETAG, value)

    var catalogEtag: String?
        get() = read(KEY_CATALOG_ETAG)
        set(value) = save(KEY_CATALOG_ETAG, value)

    /** Opens (and if needed recovers) the encrypted store. Blocking: call from a background thread. */
    fun warmUp() {
        prefs()
        dropLegacyPairing()
        _isReady.value = true
    }

    /** Logged in with a company selected (the caja may still be missing). */
    fun isLoggedIn(): Boolean = !authToken.isNullOrBlank() && !companyId.isNullOrBlank() && !companyDb.isNullOrBlank()

    /** Ready to sell: logged in and a caja + prefix configured on this device. */
    fun hasCaja(): Boolean = isLoggedIn() && !cajaId.isNullOrBlank() && !prefix.isNullOrBlank()

    fun saveSession(session: KioskSession) {
        authToken = session.token
        userId = session.userId.toString()
        username = session.username
        companyId = session.companyId.toString()
        companyName = session.companyName
        companyDb = session.companyDb
        countryCode = session.countryCode
        serverUrl = session.serverUrl
        // A new login always starts without a caja: the previous one may belong to another company.
        clearCaja()
    }

    fun saveCaja(
        cajaId: String,
        cajaName: String,
        prefix: String,
    ) {
        this.cajaId = cajaId
        this.cajaName = cajaName
        this.prefix = prefix
        configEtag = null
        catalogEtag = null
    }

    /** Forgets the caja ("Cambiar caja" / caja rejected by the server). The prefix stays as the next default. */
    fun clearCaja() {
        cajaId = null
        cajaName = null
        configEtag = null
        catalogEtag = null
    }

    /**
     * Logout / revoked token: drops credentials, company and caja. Keeps the server URL, the
     * country and the prefix so the operator does not have to type them again.
     */
    fun clearSession() {
        SESSION_KEYS.forEach { save(it, null) }
    }

    /** Wipes everything, server URL included. */
    fun clear() {
        synchronized(lock) { inMemoryMap.clear() }
        guarded { prefs()?.edit()?.clear()?.apply() }
    }

    private fun dropLegacyPairing() {
        if (LEGACY_KEYS.none { read(it) != null }) return
        Log.i(TAG, "Dropping legacy pairing credentials; the kiosk must log in")
        (LEGACY_KEYS + SESSION_KEYS).forEach { save(it, null) }
    }

    private fun read(key: String): String? {
        val stored = guarded { prefs()?.getString(key, null) }
        return stored ?: synchronized(lock) { inMemoryMap[key] }
    }

    private fun save(
        key: String,
        value: String?,
    ) {
        synchronized(lock) {
            if (value == null) inMemoryMap.remove(key) else inMemoryMap[key] = value
        }
        guarded {
            val editor = prefs()?.edit()
            if (value == null) editor?.remove(key)?.apply() else editor?.putString(key, value)?.apply()
        }
    }

    private fun prefs(): SharedPreferences? =
        synchronized(lock) {
            if (!opened) {
                prefs = prefsFactory?.let { openOrRecover(it) }
                opened = true
            }
            prefs
        }

    /** Runs a prefs operation; if the keystore/ciphertext is corrupted, recreates the store and returns null. */
    @Suppress("TooGenericExceptionCaught")
    private fun <T> guarded(block: () -> T?): T? =
        try {
            block()
        } catch (e: Exception) {
            Log.e(TAG, "Secure prefs access failed, recreating store: ${e.javaClass.simpleName}", e)
            synchronized(lock) { prefs = prefsFactory?.let(::recreate) }
            null
        }

    @Suppress("TooGenericExceptionCaught")
    private fun openOrRecover(factory: SecurePrefsFactory): SharedPreferences? =
        try {
            factory.create()
        } catch (e: Exception) {
            Log.e(TAG, "Secure prefs could not be opened, recreating: ${e.javaClass.simpleName}", e)
            recreate(factory)
        }

    @Suppress("TooGenericExceptionCaught")
    private fun recreate(factory: SecurePrefsFactory): SharedPreferences? =
        try {
            factory.destroy()
            factory.create()
        } catch (e: Exception) {
            Log.e(TAG, "Secure prefs unrecoverable, using in-memory storage: ${e.javaClass.simpleName}", e)
            null
        }

    companion object {
        const val KEY_AUTH_TOKEN = "kiosk_auth_token"
        const val KEY_USER_ID = "kiosk_user_id"
        const val KEY_USERNAME = "kiosk_username"
        const val KEY_COMPANY_ID = "kiosk_company_id"
        const val KEY_COMPANY_NAME = "kiosk_company_name"
        const val KEY_COMPANY_DB = "kiosk_company_db"
        const val KEY_COUNTRY_CODE = "kiosk_country_code"
        const val KEY_SERVER_URL = "kiosk_server_url"
        const val KEY_CAJA_ID = "kiosk_caja_id"
        const val KEY_CAJA_NAME = "kiosk_caja_name"
        const val KEY_PREFIX = "kiosk_prefix"
        const val KEY_CONFIG_ETAG = "kiosk_config_etag"
        const val KEY_CATALOG_ETAG = "kiosk_catalog_etag"

        /** 0.0.1/0.0.2 pairing credentials (`POST /api/v1/kiosk/pairing`, removed in 0.0.3). */
        val LEGACY_KEYS = listOf("kiosk_device_id", "kiosk_device_token", "kiosk_device_name")

        private val SESSION_KEYS =
            listOf(
                KEY_AUTH_TOKEN,
                KEY_USER_ID,
                KEY_USERNAME,
                KEY_COMPANY_ID,
                KEY_COMPANY_NAME,
                KEY_COMPANY_DB,
                KEY_CAJA_ID,
                KEY_CAJA_NAME,
                KEY_CONFIG_ETAG,
                KEY_CATALOG_ETAG,
            )
    }
}

/** Result of login + company selection, persisted by [KioskTokenStorage.saveSession]. */
data class KioskSession(
    val token: String,
    val userId: Int,
    val username: String,
    val companyId: Int,
    val companyName: String,
    val companyDb: String,
    val countryCode: String,
    val serverUrl: String,
)
