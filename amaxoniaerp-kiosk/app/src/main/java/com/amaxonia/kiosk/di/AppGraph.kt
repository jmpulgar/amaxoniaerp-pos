package com.amaxonia.kiosk.di

import android.content.Context
import com.amaxonia.kiosk.core.network.KioskApiClient
import com.amaxonia.kiosk.core.network.KioskCatalogResponse
import com.amaxonia.kiosk.core.network.KioskHttpClientFactory
import com.amaxonia.kiosk.core.network.KioskTokenStorage
import com.amaxonia.kiosk.domain.cart.OrderGraph
import io.ktor.client.HttpClient
import kotlinx.coroutines.flow.MutableStateFlow

class AppGraph(
    val context: Context,
    val tokenStorage: KioskTokenStorage = KioskTokenStorage(context),
    val httpClient: HttpClient = KioskHttpClientFactory.create(tokenStorage),
    val apiClient: KioskApiClient = KioskApiClient(httpClient, tokenStorage),
    val orderGraph: OrderGraph = OrderGraph(),
    val catalogState: MutableStateFlow<KioskCatalogResponse?> = MutableStateFlow(null),
)
