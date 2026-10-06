package com.amaxonia.kiosk

import android.app.Activity
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.amaxonia.kiosk.core.network.KioskSessionEvent
import com.amaxonia.kiosk.di.AppGraph
import com.amaxonia.kiosk.domain.flow.CheckoutFlowPolicy
import com.amaxonia.kiosk.domain.payment.PaymentMethod
import com.amaxonia.kiosk.hardware.printer.ReceiptFormatter
import com.amaxonia.kiosk.ui.accessibility.AccessibilityActions
import com.amaxonia.kiosk.ui.accessibility.KioskChrome
import com.amaxonia.kiosk.ui.admin.AdminMenuDialog
import com.amaxonia.kiosk.ui.attract.AttractScreen
import com.amaxonia.kiosk.ui.attract.AttractViewModel
import com.amaxonia.kiosk.ui.cajasetup.CajaSetupScreen
import com.amaxonia.kiosk.ui.cajasetup.CajaSetupViewModel
import com.amaxonia.kiosk.ui.customer.CustomerIdScreen
import com.amaxonia.kiosk.ui.customer.CustomerIdViewModel
import com.amaxonia.kiosk.ui.customizer.CustomizerScreen
import com.amaxonia.kiosk.ui.customizer.ProductCustomizerViewModel
import com.amaxonia.kiosk.ui.diningmode.DiningModeScreen
import com.amaxonia.kiosk.ui.idle.IdleWarningDialog
import com.amaxonia.kiosk.ui.login.LoginScreen
import com.amaxonia.kiosk.ui.login.LoginViewModel
import com.amaxonia.kiosk.ui.menu.MenuScreen
import com.amaxonia.kiosk.ui.menu.MenuViewModel
import com.amaxonia.kiosk.ui.navigation.KioskDestinations
import com.amaxonia.kiosk.ui.navigation.kioskEnterTransition
import com.amaxonia.kiosk.ui.navigation.kioskExitTransition
import com.amaxonia.kiosk.ui.navigation.kioskPopEnterTransition
import com.amaxonia.kiosk.ui.navigation.kioskPopExitTransition
import com.amaxonia.kiosk.ui.navigation.navigateAsRoot
import com.amaxonia.kiosk.ui.payment.CompletedOrderInfo
import com.amaxonia.kiosk.ui.payment.OrderNumberScreen
import com.amaxonia.kiosk.ui.payment.PaymentScreen
import com.amaxonia.kiosk.ui.payment.PaymentViewModel
import com.amaxonia.kiosk.ui.payment.YappyPaymentScreen
import com.amaxonia.kiosk.ui.payment.YappyPaymentViewModel
import com.amaxonia.kiosk.ui.paymentmethod.PaymentMethodScreen
import com.amaxonia.kiosk.ui.paymentmethod.PaymentMethodViewModel
import com.amaxonia.kiosk.ui.review.ReviewScreen
import com.amaxonia.kiosk.ui.review.ReviewViewModel
import com.amaxonia.kiosk.ui.tabletent.TableTentScreen
import com.amaxonia.kiosk.ui.tabletent.TableTentViewModel
import com.amaxonia.kiosk.ui.theme.AmaxoniaKioskTheme
import com.amaxonia.kiosk.ui.theme.KioskScaledCanvas
import kotlinx.coroutines.launch
import java.util.Locale

class MainActivity : ComponentActivity() {
    private val appGraph: AppGraph
        get() = (application as KioskApplication).appGraph

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        appGraph.idleTimerManager.start(lifecycleScope)
        if (appGraph.lockTaskController.isDeviceOwner()) {
            appGraph.lockTaskController.startLockTask(this)
        }

        // Follow the rotation configured on the device (portrait K2, or a landscape kiosk), as its
        // launcher does; the UI adapts to either through KioskScaledCanvas.
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_USER

        // Enable immersive full-screen kiosk mode
        enableImmersiveMode()

        setContent {
            KioskRoot(appGraph = appGraph)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        appGraph.idleTimerManager.stop()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            enableImmersiveMode()
        }
    }

    private fun enableImmersiveMode() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
    }
}

@Composable
fun KioskRoot(appGraph: AppGraph) {
    val navController = rememberNavController()
    val accessibilityState by appGraph.accessibilityManager.state.collectAsStateWithLifecycle()
    val idleTimerState by appGraph.idleTimerManager.state.collectAsStateWithLifecycle()
    val storageReady by appGraph.tokenStorage.isReady.collectAsStateWithLifecycle()

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val isOrderingScreen = currentRoute != null && currentRoute !in KioskDestinations.idleExemptRoutes

    LaunchedEffect(isOrderingScreen) {
        appGraph.idleTimerManager.setEnabled(isOrderingScreen)
    }

    LaunchedEffect(navController) {
        appGraph.idleTimerManager.onTimeoutExpired = {
            appGraph.resetSession()
            navController.navigateAsRoot(KioskDestinations.ATTRACT)
        }
    }

    val currentConfig = LocalConfiguration.current
    val localizedConfig =
        remember(accessibilityState.language, currentConfig) {
            Configuration(currentConfig).apply {
                setLocale(Locale.forLanguageTag(accessibilityState.language.code))
            }
        }
    val context = LocalContext.current
    val localizedContext =
        remember(localizedConfig, context) {
            context.createConfigurationContext(localizedConfig)
        }

    CompositionLocalProvider(
        LocalConfiguration provides localizedConfig,
        LocalContext provides localizedContext,
    ) {
        // Maps the 1080x1920 design canvas onto the real window, whatever its density or font scale.
        KioskScaledCanvas {
            AmaxoniaKioskTheme(highContrast = accessibilityState.isHighContrast) {
                Surface(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                awaitPointerEventScope {
                                    while (true) {
                                        awaitPointerEvent(PointerEventPass.Initial)
                                        appGraph.idleTimerManager.onUserActivity()
                                    }
                                }
                            },
                    color = MaterialTheme.colorScheme.background,
                ) {
                    if (!storageReady) {
                        // Encrypted storage is opening off the main thread; a blank canvas for a few ms.
                        Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
                        return@Surface
                    }
                    Box(modifier = Modifier.fillMaxSize()) {
                        KioskChrome(
                            state = accessibilityState,
                            isOrderingScreen = isOrderingScreen,
                            actions =
                                AccessibilityActions(
                                    onToggleAccessibleMode = appGraph.accessibilityManager::toggleAccessibleMode,
                                    onToggleHighContrast = appGraph.accessibilityManager::toggleHighContrast,
                                    onToggleLanguage = appGraph.accessibilityManager::toggleLanguage,
                                ),
                        ) {
                            KioskNavHost(
                                navController = navController,
                                appGraph = appGraph,
                            )
                        }

                        if (idleTimerState.isWarningVisible) {
                            IdleWarningDialog(
                                remainingSeconds = idleTimerState.remainingSeconds,
                                onContinue = { appGraph.idleTimerManager.continueOrdering() },
                                onCancel = { appGraph.idleTimerManager.cancelOrder() },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Pauses the idle timer while a payment runs on its own timeout; always resumes when the screen leaves. */
@Composable
private fun PauseIdleWhile(
    appGraph: AppGraph,
    paused: Boolean,
) {
    LaunchedEffect(paused) { appGraph.idleTimerManager.setPaused(paused) }
    DisposableEffect(Unit) {
        onDispose { appGraph.idleTimerManager.setPaused(false) }
    }
}

@Suppress("CyclomaticComplexMethod", "LongMethod")
@Composable
fun KioskNavHost(
    navController: NavHostController,
    appGraph: AppGraph,
) {
    val startDestination =
        remember {
            when {
                !appGraph.tokenStorage.isLoggedIn() -> KioskDestinations.LOGIN
                !appGraph.tokenStorage.hasCaja() -> KioskDestinations.CAJA_SETUP
                else -> KioskDestinations.ATTRACT
            }
        }

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var showAdminMenu by remember { mutableStateOf(false) }
    // Set when the server rejected the configured caja, so the caja screen explains why it is back.
    var cajaRejected by remember { mutableStateOf(false) }

    // A kiosk call answered 401 (session revoked) or 400 "Caja del kiosco no válida": the API client
    // already cleared storage; drop in-memory data and send the operator to the right setup screen.
    LaunchedEffect(navController) {
        appGraph.apiClient.sessionEvents.collect { event ->
            showAdminMenu = false
            appGraph.forgetKioskData()
            when (event) {
                KioskSessionEvent.LoggedOut -> navController.navigateAsRoot(KioskDestinations.LOGIN)
                KioskSessionEvent.InvalidCaja -> {
                    cajaRejected = true
                    navController.navigateAsRoot(KioskDestinations.CAJA_SETUP)
                }
            }
        }
    }
    val isLockTaskActive by appGraph.lockTaskController.isLockTaskActive.collectAsStateWithLifecycle()
    val completedOrder by appGraph.completedOrderState.collectAsStateWithLifecycle()
    val catalog by appGraph.catalogState.collectAsStateWithLifecycle()
    val accessibilityState by appGraph.accessibilityManager.state.collectAsStateWithLifecycle()

    // Single exit path back to Attract: clear the customer session, then the whole back stack.
    val endSession: () -> Unit = {
        appGraph.resetSession()
        navController.navigateAsRoot(KioskDestinations.ATTRACT)
    }
    val completeOrder: (CompletedOrderInfo) -> Unit = { info ->
        appGraph.completedOrderState.value = info
        navController.navigateAsRoot(KioskDestinations.ORDER_NUMBER)
    }

    if (showAdminMenu) {
        AdminMenuDialog(
            isLockTaskActive = isLockTaskActive,
            hasLastOrder = completedOrder != null,
            sessionLabel =
                listOfNotNull(appGraph.tokenStorage.cajaName, appGraph.tokenStorage.companyName)
                    .filter { it.isNotBlank() }
                    .joinToString(" · ")
                    .ifBlank { null },
            onChangeCaja = {
                showAdminMenu = false
                cajaRejected = false
                appGraph.changeCaja()
                navController.navigateAsRoot(KioskDestinations.CAJA_SETUP)
            },
            onLogout = {
                showAdminMenu = false
                appGraph.logout()
                navController.navigateAsRoot(KioskDestinations.LOGIN)
            },
            onTestPrint = {
                showAdminMenu = false
                coroutineScope.launch {
                    appGraph.printer.printReceipt(ReceiptFormatter.createDiagnosticReceipt())
                }
            },
            onReprintLastReceipt = {
                showAdminMenu = false
                completedOrder?.let { order ->
                    coroutineScope.launch {
                        appGraph.printer.printReceipt(order.paymentResponse)
                    }
                }
            },
            onToggleLockTask = {
                val activity = context as? Activity
                if (activity != null) {
                    if (isLockTaskActive) {
                        appGraph.lockTaskController.stopLockTask(activity)
                    } else {
                        appGraph.lockTaskController.startLockTask(activity)
                    }
                }
            },
            onDismiss = { showAdminMenu = false },
        )
    }

    NavHost(
        navController = navController,
        startDestination = startDestination,
        enterTransition = { kioskEnterTransition() },
        exitTransition = { kioskExitTransition() },
        popEnterTransition = { kioskPopEnterTransition() },
        popExitTransition = { kioskPopExitTransition() },
    ) {
        composable(KioskDestinations.LOGIN) {
            // Under lock task there is nowhere to go back to.
            BackHandler {}
            val viewModel =
                viewModel {
                    LoginViewModel(
                        apiClient = appGraph.apiClient,
                        tokenStorage = appGraph.tokenStorage,
                        defaultServerUrl = BuildConfig.DEFAULT_SERVER_URL,
                        defaultCountryCode = BuildConfig.DEFAULT_COUNTRY_CODE,
                    )
                }
            LoginScreen(
                viewModel = viewModel,
                onLoggedIn = {
                    appGraph.forgetKioskData()
                    cajaRejected = false
                    navController.navigateAsRoot(KioskDestinations.CAJA_SETUP)
                },
            )
        }

        composable(KioskDestinations.CAJA_SETUP) {
            BackHandler {}
            val viewModel =
                viewModel {
                    CajaSetupViewModel(
                        apiClient = appGraph.apiClient,
                        tokenStorage = appGraph.tokenStorage,
                        previousCajaInvalid = cajaRejected,
                    )
                }
            CajaSetupScreen(
                viewModel = viewModel,
                onStarted = {
                    cajaRejected = false
                    appGraph.forgetKioskData()
                    navController.navigateAsRoot(KioskDestinations.ATTRACT)
                },
                onLoggedOut = {
                    appGraph.forgetKioskData()
                    navController.navigateAsRoot(KioskDestinations.LOGIN)
                },
            )
        }

        composable(KioskDestinations.ATTRACT) {
            BackHandler {}
            val attractViewModel =
                viewModel {
                    AttractViewModel(
                        apiClient = appGraph.apiClient,
                        tokenStorage = appGraph.tokenStorage,
                        configRepository = appGraph.configRepository,
                    )
                }
            AttractScreen(
                viewModel = attractViewModel,
                onStartOrder = {
                    val singleMode = CheckoutFlowPolicy.singleDiningMode(appGraph.config.value)
                    if (singleMode != null) {
                        appGraph.orderGraph.setDiningMode(singleMode)
                        navController.navigate(KioskDestinations.MENU)
                    } else {
                        navController.navigate(KioskDestinations.DINING_MODE)
                    }
                },
                onAdminUnlocked = { showAdminMenu = true },
                language = accessibilityState.language,
                onLanguageSelected = appGraph.accessibilityManager::setLanguage,
            )
        }

        composable(KioskDestinations.DINING_MODE) {
            BackHandler(onBack = endSession)
            val diningMode by appGraph.orderGraph.diningMode.collectAsStateWithLifecycle()
            DiningModeScreen(
                currentMode = diningMode,
                onModeSelected = { mode ->
                    appGraph.orderGraph.setDiningMode(mode)
                    navController.navigate(KioskDestinations.MENU)
                },
                onBack = endSession,
            )
        }

        composable(KioskDestinations.MENU) {
            BackHandler(onBack = endSession)
            val menuViewModel =
                viewModel {
                    MenuViewModel(
                        apiClient = appGraph.apiClient,
                        tokenStorage = appGraph.tokenStorage,
                        orderGraph = appGraph.orderGraph,
                        catalogState = appGraph.catalogState,
                    )
                }
            MenuScreen(
                viewModel = menuViewModel,
                onOpenCustomizer = { itemId ->
                    navController.navigate("${KioskDestinations.CUSTOMIZER}/$itemId")
                },
                onViewCart = { navController.navigate(KioskDestinations.REVIEW) },
                onBackToAttract = endSession,
            )
        }

        composable("${KioskDestinations.CUSTOMIZER}/{itemId}") { backStackEntry ->
            val itemId = backStackEntry.arguments?.getString("itemId")?.toIntOrNull()
            val item = catalog?.items?.find { it.id == itemId }
            if (item != null) {
                BackHandler { navController.popBackStack() }
                val customizerViewModel =
                    viewModel {
                        ProductCustomizerViewModel(
                            item = item,
                            orderGraph = appGraph.orderGraph,
                        )
                    }
                CustomizerScreen(
                    viewModel = customizerViewModel,
                    onDismiss = { navController.popBackStack() },
                )
            } else {
                LaunchedEffect(Unit) {
                    navController.popBackStack()
                }
            }
        }

        composable(KioskDestinations.REVIEW) {
            val continueShopping: () -> Unit = {
                navController.navigate(KioskDestinations.MENU) {
                    popUpTo(KioskDestinations.MENU) { inclusive = true }
                }
            }
            BackHandler(onBack = continueShopping)
            val reviewViewModel = viewModel { ReviewViewModel(orderGraph = appGraph.orderGraph) }
            ReviewScreen(
                viewModel = reviewViewModel,
                onContinueShopping = continueShopping,
                onProceedToCheckout = {
                    val diningMode = appGraph.orderGraph.diningMode.value
                    if (CheckoutFlowPolicy.needsTableTent(appGraph.config.value, diningMode)) {
                        navController.navigate(KioskDestinations.TABLE_TENT)
                    } else {
                        appGraph.orderGraph.setTableTent(null)
                        navController.navigate(KioskDestinations.CUSTOMER_ID)
                    }
                },
                onCancelOrder = endSession,
            )
        }

        composable(KioskDestinations.TABLE_TENT) {
            BackHandler { navController.popBackStack() }
            val tableTentViewModel = viewModel { TableTentViewModel(orderGraph = appGraph.orderGraph) }
            TableTentScreen(
                viewModel = tableTentViewModel,
                onConfirmed = { navController.navigate(KioskDestinations.CUSTOMER_ID) },
                onBack = { navController.popBackStack() },
            )
        }

        composable(KioskDestinations.CUSTOMER_ID) {
            BackHandler { navController.popBackStack() }
            val customerViewModel = viewModel { CustomerIdViewModel(orderGraph = appGraph.orderGraph) }
            CustomerIdScreen(
                viewModel = customerViewModel,
                onCustomerConfirmed = { navController.navigate(KioskDestinations.PAYMENT_METHOD) },
                onBack = { navController.popBackStack() },
            )
        }

        composable(KioskDestinations.PAYMENT_METHOD) {
            BackHandler { navController.popBackStack() }
            val paymentMethodViewModel =
                viewModel {
                    PaymentMethodViewModel(
                        quoteOrder = appGraph.quoteOrderUseCase,
                        orderGraph = appGraph.orderGraph,
                        methods = appGraph.availablePaymentMethods,
                    )
                }
            PaymentMethodScreen(
                viewModel = paymentMethodViewModel,
                onMethodSelected = { method, skipped ->
                    val route =
                        when (method) {
                            PaymentMethod.CARD -> KioskDestinations.PAYMENT
                            PaymentMethod.YAPPY -> KioskDestinations.YAPPY_PAYMENT
                        }
                    navController.navigate(route) {
                        if (skipped) popUpTo(KioskDestinations.PAYMENT_METHOD) { inclusive = true }
                    }
                },
                onBack = { navController.popBackStack() },
                onBackToOrder = { navController.popBackStack(KioskDestinations.REVIEW, inclusive = false) },
            )
        }

        composable(KioskDestinations.PAYMENT) {
            val paymentViewModel =
                viewModel {
                    PaymentViewModel(
                        checkoutUseCase = appGraph.checkoutUseCase,
                        quoteOrder = appGraph.quoteOrderUseCase,
                        paymentTerminal = appGraph.paymentTerminal,
                        checkoutSession = appGraph.checkoutSession,
                        orderGraph = appGraph.orderGraph,
                        dispatch = appGraph.dispatch,
                    )
                }
            val paymentState by paymentViewModel.uiState.collectAsStateWithLifecycle()
            PauseIdleWhile(appGraph, paymentState.isPaymentInFlight)
            // Back = the on-screen cancel (cancels the terminal); ignored once the card is approved.
            BackHandler { paymentViewModel.cancelPayment { navController.popBackStack() } }
            PaymentScreen(
                viewModel = paymentViewModel,
                onPaid = completeOrder,
                onBack = { navController.popBackStack() },
            )
        }

        composable(KioskDestinations.YAPPY_PAYMENT) {
            val yappyViewModel =
                viewModel {
                    YappyPaymentViewModel(
                        yappyCheckout = appGraph.yappyCheckoutUseCase,
                        quoteOrder = appGraph.quoteOrderUseCase,
                        checkoutSession = appGraph.checkoutSession,
                        orderGraph = appGraph.orderGraph,
                        dispatch = appGraph.dispatch,
                        canChangeMethod = appGraph.availablePaymentMethods.size > 1,
                    )
                }
            val yappyState by yappyViewModel.uiState.collectAsStateWithLifecycle()
            PauseIdleWhile(appGraph, yappyState.isPaymentInFlight)
            // Back = the on-screen cancel (DELETEs the charge first).
            BackHandler { yappyViewModel.leave { navController.popBackStack() } }
            YappyPaymentScreen(
                viewModel = yappyViewModel,
                onPaid = completeOrder,
                onBack = { navController.popBackStack() },
            )
        }

        composable(KioskDestinations.ORDER_NUMBER) {
            // Captured once: the session reset on exit must not recompose this screen into a redirect.
            val order = remember { completedOrder }
            BackHandler(onBack = endSession)
            if (order != null) {
                var printFailed by remember { mutableStateOf(false) }
                var printing by remember { mutableStateOf(true) }
                LaunchedEffect(order.orderNumber) {
                    printFailed = appGraph.printer.printReceipt(order.paymentResponse).isFailure
                    printing = false
                }
                OrderNumberScreen(
                    orderInfo = order,
                    onFinish = endSession,
                    receiptPrintFailed = printFailed,
                    receiptPrinting = printing,
                )
            } else {
                LaunchedEffect(Unit) { endSession() }
            }
        }
    }
}
