package com.amaxonia.kiosk.core.network

import android.content.Context
import android.content.SharedPreferences

class KioskTokenStorage(
    context: Context? = null,
) {
    private val prefs: SharedPreferences? =
        try {
            context?.getSharedPreferences("kiosk_secure_prefs", Context.MODE_PRIVATE)
        } catch (_: Exception) {
            null
        }

    private val inMemoryMap = mutableMapOf<String, String>()

    var deviceId: String?
        get() = prefs?.getString(KEY_DEVICE_ID, null) ?: inMemoryMap[KEY_DEVICE_ID]
        set(value) = save(KEY_DEVICE_ID, value)

    var deviceToken: String?
        get() = prefs?.getString(KEY_DEVICE_TOKEN, null) ?: inMemoryMap[KEY_DEVICE_TOKEN]
        set(value) = save(KEY_DEVICE_TOKEN, value)

    var deviceName: String?
        get() = prefs?.getString(KEY_DEVICE_NAME, null) ?: inMemoryMap[KEY_DEVICE_NAME]
        set(value) = save(KEY_DEVICE_NAME, value)

    var prefix: String?
        get() = prefs?.getString(KEY_PREFIX, null) ?: inMemoryMap[KEY_PREFIX]
        set(value) = save(KEY_PREFIX, value)

    var countryCode: String?
        get() = prefs?.getString(KEY_COUNTRY_CODE, null) ?: inMemoryMap[KEY_COUNTRY_CODE]
        set(value) = save(KEY_COUNTRY_CODE, value)

    var companyDb: String?
        get() = prefs?.getString(KEY_COMPANY_DB, null) ?: inMemoryMap[KEY_COMPANY_DB]
        set(value) = save(KEY_COMPANY_DB, value)

    var serverUrl: String?
        get() = prefs?.getString(KEY_SERVER_URL, null) ?: inMemoryMap[KEY_SERVER_URL]
        set(value) = save(KEY_SERVER_URL, value)

    var configEtag: String?
        get() = prefs?.getString(KEY_CONFIG_ETAG, null) ?: inMemoryMap[KEY_CONFIG_ETAG]
        set(value) = save(KEY_CONFIG_ETAG, value)

    var catalogEtag: String?
        get() = prefs?.getString(KEY_CATALOG_ETAG, null) ?: inMemoryMap[KEY_CATALOG_ETAG]
        set(value) = save(KEY_CATALOG_ETAG, value)

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
        prefs?.edit()?.clear()?.apply()
        inMemoryMap.clear()
    }

    private fun save(
        key: String,
        value: String?,
    ) {
        if (value == null) {
            inMemoryMap.remove(key)
            prefs?.edit()?.remove(key)?.apply()
        } else {
            inMemoryMap[key] = value
            prefs?.edit()?.putString(key, value)?.apply()
        }
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
