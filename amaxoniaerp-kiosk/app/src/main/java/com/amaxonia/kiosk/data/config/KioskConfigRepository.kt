package com.amaxonia.kiosk.data.config

import com.amaxonia.kiosk.core.network.KioskApiClient
import com.amaxonia.kiosk.core.network.KioskConfigResponse
import com.amaxonia.kiosk.core.network.KioskTokenStorage
import com.amaxonia.kiosk.core.network.NetworkResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Single in-memory source of the company kiosk config (`GET /config`): media, dining modes,
 * dispatch and enabled payment methods. The ETag is only sent when a config is already held in
 * memory, so a process restart never ends up with a 304 and no data.
 */
class KioskConfigRepository(
    private val apiClient: KioskApiClient,
    private val tokenStorage: KioskTokenStorage,
) {
    private val _config = MutableStateFlow<KioskConfigResponse?>(null)
    val config: StateFlow<KioskConfigResponse?> = _config.asStateFlow()

    suspend fun refresh(): NetworkResult<KioskConfigResponse> {
        val etag = if (_config.value != null) tokenStorage.configEtag else null
        val result = apiClient.getConfig(etag)
        if (result is NetworkResult.Success) {
            _config.value = result.data
        }
        return result
    }
}
