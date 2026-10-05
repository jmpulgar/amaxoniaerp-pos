package com.amaxonia.kiosk.di

import android.content.Context
import com.amaxonia.kiosk.core.network.KioskApiClient
import com.amaxonia.kiosk.core.network.KioskCatalogResponse
import com.amaxonia.kiosk.core.network.KioskHttpClientFactory
import com.amaxonia.kiosk.core.network.KioskTokenStorage
import com.amaxonia.kiosk.data.db.KioskDatabase
import com.amaxonia.kiosk.data.db.PendingPaymentDao
import com.amaxonia.kiosk.domain.cart.OrderGraph
import com.amaxonia.kiosk.domain.checkout.CheckoutOrderUseCase
import com.amaxonia.kiosk.domain.payment.DevMockPaymentTerminal
import com.amaxonia.kiosk.domain.payment.PaymentTerminal
import com.amaxonia.kiosk.ui.payment.CompletedOrderInfo
import io.ktor.client.HttpClient
import kotlinx.coroutines.flow.MutableStateFlow

class AppGraph(
    val context: Context,
    val tokenStorage: KioskTokenStorage = KioskTokenStorage(context),
) {
    val httpClient: HttpClient = KioskHttpClientFactory.create(tokenStorage)
    val apiClient: KioskApiClient = KioskApiClient(httpClient, tokenStorage)
    val orderGraph: OrderGraph = OrderGraph()
    val catalogState: MutableStateFlow<KioskCatalogResponse?> = MutableStateFlow(null)
    val completedOrderState: MutableStateFlow<CompletedOrderInfo?> = MutableStateFlow(null)
    val database: KioskDatabase = KioskDatabase.build(context)
    val pendingPaymentDao: PendingPaymentDao = database.pendingPaymentDao()
    val paymentTerminal: PaymentTerminal = DevMockPaymentTerminal()
    val checkoutUseCase: CheckoutOrderUseCase =
        CheckoutOrderUseCase(
            apiClient = apiClient,
            paymentTerminal = paymentTerminal,
            pendingPaymentDao = pendingPaymentDao,
        )
}
