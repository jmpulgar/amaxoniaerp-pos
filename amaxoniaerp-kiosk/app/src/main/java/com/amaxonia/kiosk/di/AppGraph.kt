package com.amaxonia.kiosk.di

import android.content.Context
import android.util.Log
import com.amaxonia.kiosk.core.network.KioskApiClient
import com.amaxonia.kiosk.core.network.KioskCatalogResponse
import com.amaxonia.kiosk.core.network.KioskConfigResponse
import com.amaxonia.kiosk.core.network.KioskHttpClientFactory
import com.amaxonia.kiosk.core.network.KioskTokenStorage
import com.amaxonia.kiosk.data.config.KioskConfigRepository
import com.amaxonia.kiosk.data.db.KioskDatabase
import com.amaxonia.kiosk.data.db.PendingPaymentDao
import com.amaxonia.kiosk.domain.cart.OrderGraph
import com.amaxonia.kiosk.domain.checkout.CheckoutOrderUseCase
import com.amaxonia.kiosk.domain.checkout.CheckoutSession
import com.amaxonia.kiosk.domain.checkout.QuoteOrderUseCase
import com.amaxonia.kiosk.domain.checkout.YappyCheckoutUseCase
import com.amaxonia.kiosk.domain.flow.CheckoutFlowPolicy
import com.amaxonia.kiosk.domain.payment.PaymentMethod
import com.amaxonia.kiosk.domain.payment.PaymentTerminal
import com.amaxonia.kiosk.domain.session.SessionResetter
import com.amaxonia.kiosk.hardware.locktask.LockTaskController
import com.amaxonia.kiosk.hardware.printer.KioskPrinter
import com.amaxonia.kiosk.hardware.printer.SunmiPrinterManager
import com.amaxonia.kiosk.ui.accessibility.AccessibilityManager
import com.amaxonia.kiosk.ui.idle.IdleTimerManager
import com.amaxonia.kiosk.ui.payment.CompletedOrderInfo
import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

private const val TAG = "AppGraph"
private const val DEFAULT_DISPATCH = "RETIRO_MOSTRADOR"

/** Composition root (ADR-001): every dependency of the kiosk is built here, by hand. */
class AppGraph(
    val context: Context,
    val tokenStorage: KioskTokenStorage = KioskTokenStorage(context),
) {
    /** Application-lifetime scope for work that must outlive any screen (outbox sync, payment cleanup). */
    val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val httpClient: HttpClient = KioskHttpClientFactory.create(tokenStorage)
    val apiClient: KioskApiClient = KioskApiClient(httpClient, tokenStorage)
    val configRepository: KioskConfigRepository = KioskConfigRepository(apiClient, tokenStorage)

    /** Company kiosk config (payment methods, dispatch, dining modes…); null until first loaded. */
    val config: StateFlow<KioskConfigResponse?> get() = configRepository.config

    val orderGraph: OrderGraph = OrderGraph()
    val catalogState: MutableStateFlow<KioskCatalogResponse?> = MutableStateFlow(null)
    val completedOrderState: MutableStateFlow<CompletedOrderInfo?> = MutableStateFlow(null)
    val database: KioskDatabase = KioskDatabase.build(context)
    val pendingPaymentDao: PendingPaymentDao = database.pendingPaymentDao()
    val paymentTerminal: PaymentTerminal = FlavorModule.paymentTerminal()
    val printer: KioskPrinter = SunmiPrinterManager(context, appScope)
    val checkoutUseCase: CheckoutOrderUseCase =
        CheckoutOrderUseCase(
            apiClient = apiClient,
            paymentTerminal = paymentTerminal,
            pendingPaymentDao = pendingPaymentDao,
        )
    val yappyCheckoutUseCase: YappyCheckoutUseCase = YappyCheckoutUseCase(apiClient, checkoutUseCase)
    val checkoutSession: CheckoutSession = CheckoutSession(paymentTerminal, yappyCheckoutUseCase, appScope)
    val quoteOrderUseCase: QuoteOrderUseCase = QuoteOrderUseCase(checkoutUseCase, checkoutSession)
    val accessibilityManager: AccessibilityManager = AccessibilityManager()
    val idleTimerManager: IdleTimerManager = IdleTimerManager()
    val lockTaskController: LockTaskController = LockTaskController(context)

    private val sessionResetter =
        SessionResetter(
            orderGraph = orderGraph,
            completedOrderState = completedOrderState,
            checkoutSession = checkoutSession,
            accessibilityManager = accessibilityManager,
            idleTimerManager = idleTimerManager,
            onSessionEnded = ::syncPendingPayments,
        )

    /** Dispatch mode of the company (RETIRO_MOSTRADOR / IMPRESORA_COCINA / MESAS). */
    val dispatch: String
        get() = config.value?.dispatch ?: DEFAULT_DISPATCH

    val availablePaymentMethods: List<PaymentMethod>
        get() = CheckoutFlowPolicy.availablePaymentMethods(config.value, paymentTerminal.isAvailable)

    /** Startup work kept off the main thread: open the encrypted store and retry unsynced payments. */
    fun start() {
        appScope.launch(Dispatchers.IO) {
            tokenStorage.warmUp()
            syncPendingPayments()
        }
        config
            .filterNotNull()
            .onEach { orderGraph.setCurrencyConfig(it.currency) }
            .launchIn(appScope)
    }

    /** Clears everything tied to the current customer. Every path back to Attract goes through here. */
    fun resetSession() = sessionResetter.resetSession()

    /** Retries paid-but-unregistered orders from the Room outbox. */
    fun syncPendingPayments() {
        appScope.launch(Dispatchers.IO) {
            runCatching { checkoutUseCase.syncPendingPayments() }
                .onSuccess { results ->
                    if (results.isNotEmpty()) {
                        Log.i(TAG, "Outbox sync: ${results.count { it.success }}/${results.size} registered")
                    }
                }.onFailure { Log.e(TAG, "Outbox sync failed: ${it.message}", it) }
        }
    }
}
