package com.amaxonia.kiosk.di

import android.content.Context
import com.amaxonia.kiosk.core.network.KioskApiClient
import com.amaxonia.kiosk.core.network.KioskHttpClientFactory
import com.amaxonia.kiosk.core.network.KioskTokenStorage
import com.amaxonia.kiosk.domain.cart.OrderGraph
import io.ktor.client.HttpClient

class AppGraph(
    val context: Context,
    val tokenStorage: KioskTokenStorage = KioskTokenStorage(context),
    val httpClient: HttpClient = KioskHttpClientFactory.create(tokenStorage),
    val apiClient: KioskApiClient = KioskApiClient(httpClient, tokenStorage),
    val orderGraph: OrderGraph = OrderGraph(),
)
