package com.amaxonia.pos.data.remote

import com.amaxonia.pos.domain.model.ServerCountry
import com.amaxonia.pos.domain.repository.ServerEnvironment

class ApiServerEnvironment(
    private val configManager: ApiConfigManager,
    private val apiClient: ApiClient,
) : ServerEnvironment {
    override fun selectCountry(country: ServerCountry) {
        configManager.updateBaseUrl(country)
        apiClient.recreateClient()
    }
}
