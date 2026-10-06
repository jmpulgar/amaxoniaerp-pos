package com.amaxonia.kiosk.core.network

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val TAG = "KioskTokenStorage"

/**
 * Stores the kiosk device credentials in encrypted preferences.
 *
 * The Android Keystore on API 28 (and some vendor keystores) can fail after an OTA, a restore or
 * key invalidation (KeyStoreException, AEADBadTagException, InvalidProtocolBufferException…).
 * Every open, read and write is guarded: on failure the corrupted file and master key are deleted
 * and recreated. The worst case is losing the pairing (the kiosk shows the pairing screen again);
 * the app never crashes. Opening is lazy — call [warmUp] off the main thread at startup.
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

    var deviceId: String?
        get() = read(KEY_DEVICE_ID)
        set(value) = save(KEY_DEVICE_ID, value)

    var deviceToken: String?
        get() = read(KEY_DEVICE_TOKEN)
        set(value) = save(KEY_DEVICE_TOKEN, value)

    var deviceName: String?
        get() = read(KEY_DEVICE_NAME)
        set(value) = save(KEY_DEVICE_NAME, value)

    var prefix: String?
        get() = read(KEY_PREFIX)
        set(value) = save(KEY_PREFIX, value)

    var countryCode: String?
        get() = read(KEY_COUNTRY_CODE)
        set(value) = save(KEY_COUNTRY_CODE, value)

    var companyDb: String?
        get() = read(KEY_COMPANY_DB)
        set(value) = save(KEY_COMPANY_DB, value)

    var serverUrl: String?
        get() = read(KEY_SERVER_URL)
        set(value) = save(KEY_SERVER_URL, value)

    var configEtag: String?
        get() = read(KEY_CONFIG_ETAG)
        set(value) = save(KEY_CONFIG_ETAG, value)

    var catalogEtag: String?
        get() = read(KEY_CATALOG_ETAG)
        set(value) = save(KEY_CATALOG_ETAG, value)

    /** Opens (and if needed recovers) the encrypted store. Blocking: call from a background thread. */
    fun warmUp() {
        prefs()
        _isReady.value = true
    }

    fun isPaired(): Boolean = !deviceToken.isNullOrBlank() && !deviceId.isNullOrBlank()

    fun savePairing(credentials: KioskDeviceCredentials) {
        this.deviceId = credentials.deviceId
        this.deviceToken = credentials.deviceToken
        this.deviceName = credentials.deviceName
        this.prefix = credentials.prefix
        this.countryCode = credentials.countryCode
        this.companyDb = credentials.companyDb
        this.serverUrl = credentials.serverUrl
    }

    fun clear() {
        synchronized(lock) { inMemoryMap.clear() }
        guarded { prefs()?.edit()?.clear()?.apply() }
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
        private const val KEY_DEVICE_ID = "kiosk_device_id"
        private const val KEY_DEVICE_TOKEN = "kiosk_device_token"
        private const val KEY_DEVICE_NAME = "kiosk_device_name"
        private const val KEY_PREFIX = "kiosk_prefix"
        private const val KEY_COUNTRY_CODE = "kiosk_country_code"
        private const val KEY_COMPANY_DB = "kiosk_company_db"
        private const val KEY_SERVER_URL = "kiosk_server_url"
        private const val KEY_CONFIG_ETAG = "kiosk_config_etag"
        private const val KEY_CATALOG_ETAG = "kiosk_catalog_etag"
    }
}

data class KioskDeviceCredentials(
    val deviceId: String,
    val deviceToken: String,
    val deviceName: String,
    val prefix: String,
    val countryCode: String,
    val companyDb: String,
    val serverUrl: String,
)
