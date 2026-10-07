package com.amaxonia.kiosk.data.config

import com.amaxonia.kiosk.core.network.KioskApiClient
import com.amaxonia.kiosk.core.network.KioskConfigResponse
import com.amaxonia.kiosk.core.network.KioskTokenStorage
import com.amaxonia.kiosk.core.network.NetworkResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File

private val ConfigJson = Json { ignoreUnknownKeys = true }

/**
 * Single source of the company kiosk config (`GET /config`): media, dining modes, dispatch and
 * enabled payment methods. The ETag is only sent when a config is already held, so a process
 * restart never ends up with a 304 and no data.
 *
 * With a [cacheFile] the last config survives restarts: the attract loop shows the banners right
 * away (and offline) while the server is revalidated. [onConfigLoaded] receives every config that
 * becomes current (used to preload the banners).
 */
class KioskConfigRepository(
    private val apiClient: KioskApiClient,
    private val tokenStorage: KioskTokenStorage,
    private val cacheFile: File? = null,
    private val onConfigLoaded: (KioskConfigResponse) -> Unit = {},
) {
    private val _config = MutableStateFlow<KioskConfigResponse?>(null)
    val config: StateFlow<KioskConfigResponse?> = _config.asStateFlow()

    @Volatile
    private var diskChecked = false

    suspend fun refresh(): NetworkResult<KioskConfigResponse> {
        restoreFromDisk()
        val etag = if (_config.value != null) tokenStorage.configEtag else null
        val result = apiClient.getConfig(etag)
        if (result is NetworkResult.Success) {
            _config.value = result.data
            onConfigLoaded(result.data)
            persist(result.data)
        }
        return result
    }

    /** Forgets the cached config (logout / caja change: the next company or caja may differ). */
    fun clear() {
        _config.value = null
        diskChecked = true
        cacheFile?.delete()
    }

    private suspend fun restoreFromDisk() {
        if (diskChecked || _config.value != null) return
        diskChecked = true
        val file = cacheFile
        val restored =
            withContext(Dispatchers.IO) {
                runCatching {
                    if (file?.isFile == true) ConfigJson.decodeFromString(KioskConfigResponse.serializer(), file.readText()) else null
                }.getOrNull()
            }
        if (restored != null && _config.value == null) {
            _config.value = restored
            onConfigLoaded(restored)
        }
    }

    private suspend fun persist(config: KioskConfigResponse) {
        val file = cacheFile ?: return
        withContext(Dispatchers.IO) {
            runCatching {
                val tmp = File(file.parentFile, "${file.name}.tmp")
                tmp.writeText(ConfigJson.encodeToString(KioskConfigResponse.serializer(), config))
                tmp.renameTo(file)
            }
        }
    }
}
