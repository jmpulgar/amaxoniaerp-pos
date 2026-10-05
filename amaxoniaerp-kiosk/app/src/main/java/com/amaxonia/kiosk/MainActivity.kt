package com.amaxonia.kiosk

import android.content.pm.ActivityInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.amaxonia.kiosk.di.AppGraph
import com.amaxonia.kiosk.ui.attract.AttractScreen
import com.amaxonia.kiosk.ui.attract.AttractViewModel
import com.amaxonia.kiosk.ui.customer.CustomerIdScreen
import com.amaxonia.kiosk.ui.customer.CustomerIdViewModel
import com.amaxonia.kiosk.ui.customizer.CustomizerScreen
import com.amaxonia.kiosk.ui.customizer.ProductCustomizerViewModel
import com.amaxonia.kiosk.ui.diningmode.DiningModeScreen
import com.amaxonia.kiosk.ui.menu.MenuScreen
import com.amaxonia.kiosk.ui.menu.MenuViewModel
import com.amaxonia.kiosk.ui.navigation.KioskDestinations
import com.amaxonia.kiosk.ui.pairing.PairingScreen
import com.amaxonia.kiosk.ui.pairing.PairingViewModel
import com.amaxonia.kiosk.ui.payment.OrderNumberScreen
import com.amaxonia.kiosk.ui.payment.PaymentScreen
import com.amaxonia.kiosk.ui.payment.PaymentViewModel
import com.amaxonia.kiosk.ui.review.ReviewScreen
import com.amaxonia.kiosk.ui.review.ReviewViewModel
import com.amaxonia.kiosk.ui.tabletent.TableTentScreen
import com.amaxonia.kiosk.ui.tabletent.TableTentViewModel
import com.amaxonia.kiosk.ui.theme.AmaxoniaKioskTheme

class MainActivity : ComponentActivity() {
    private val appGraph: AppGraph
        get() = (application as KioskApplication).appGraph

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Lock to vertical portrait (1080x1920)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT

        // Enable immersive full-screen kiosk mode
        enableImmersiveMode()

        setContent {
            AmaxoniaKioskTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    KioskRoot(appGraph = appGraph)
                }
            }
        }
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
    val startDestination =
        if (appGraph.tokenStorage.isPaired()) {
            KioskDestinations.ATTRACT
        } else {
            KioskDestinations.PAIRING
        }

    NavHost(
        navController = navController,
        startDestination = startDestination,
    ) {
        composable(KioskDestinations.PAIRING) {
            val viewModel =
                remember(appGraph) {
                    PairingViewModel(
                        apiClient = appGraph.apiClient,
                        initialServerUrl = appGraph.tokenStorage.serverUrl,
                    )
                }
            PairingScreen(
                viewModel = viewModel,
                onPairingSuccess = {
                    navController.navigate(KioskDestinations.ATTRACT) {
                        popUpTo(KioskDestinations.PAIRING) { inclusive = true }
                    }
                },
            )
        }

        composable(KioskDestinations.ATTRACT) {
            val attractViewModel =
                remember(appGraph) {
                    AttractViewModel(
                        apiClient = appGraph.apiClient,
                        tokenStorage = appGraph.tokenStorage,
                    )
                }
            AttractScreen(
                viewModel = attractViewModel,
                onStartOrder = {
                    navController.navigate(KioskDestinations.DINING_MODE)
                },
                onAdminUnlocked = {
                    navController.navigate(KioskDestinations.PAIRING)
                },
            )
        }

        composable(KioskDestinations.DINING_MODE) {
            val diningMode by appGraph.orderGraph.diningMode.collectAsStateWithLifecycle()
            DiningModeScreen(
                currentMode = diningMode,
                onModeSelected = { mode ->
                    appGraph.orderGraph.setDiningMode(mode)
                    navController.navigate(KioskDestinations.MENU)
                },
                onBack = {
                    appGraph.orderGraph.reset()
                    navController.navigate(KioskDestinations.ATTRACT) {
                        popUpTo(KioskDestinations.ATTRACT) { inclusive = true }
                    }
                },
            )
        }

        composable(KioskDestinations.MENU) {
            val menuViewModel =
                remember(appGraph) {
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
                onViewCart = {
                    navController.navigate(KioskDestinations.REVIEW)
                },
                onBackToAttract = {
                    appGraph.orderGraph.reset()
                    navController.navigate(KioskDestinations.ATTRACT) {
                        popUpTo(KioskDestinations.ATTRACT) { inclusive = true }
                    }
                },
            )
        }

        composable("${KioskDestinations.CUSTOMIZER}/{itemId}") { backStackEntry ->
            val itemId = backStackEntry.arguments?.getString("itemId")?.toIntOrNull()
            val item = appGraph.catalogState.value?.items?.find { it.id == itemId }
            if (item != null) {
                val customizerViewModel =
                    remember(item, appGraph) {
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
            val reviewViewModel =
                remember(appGraph) {
                    ReviewViewModel(
                        orderGraph = appGraph.orderGraph,
                    )
                }
            ReviewScreen(
                viewModel = reviewViewModel,
                onContinueShopping = {
                    navController.navigate(KioskDestinations.MENU) {
                        popUpTo(KioskDestinations.MENU) { inclusive = true }
                    }
                },
                onProceedToCheckout = {
                    navController.navigate(KioskDestinations.CUSTOMER_ID)
                },
                onCancelOrder = {
                    appGraph.orderGraph.reset()
                    navController.navigate(KioskDestinations.ATTRACT) {
                        popUpTo(KioskDestinations.ATTRACT) { inclusive = true }
                    }
                },
            )
        }

        composable(KioskDestinations.CUSTOMER_ID) {
            val customerViewModel =
                remember(appGraph) {
                    CustomerIdViewModel(
                        orderGraph = appGraph.orderGraph,
                    )
                }
            CustomerIdScreen(
                viewModel = customerViewModel,
                onCustomerConfirmed = {
                    if (appGraph.orderGraph.diningMode.value == "COMER_AQUI") {
                        navController.navigate(KioskDestinations.TABLE_TENT)
                    } else {
                        appGraph.orderGraph.setTableTent(null)
                        navController.navigate(KioskDestinations.PAYMENT)
                    }
                },
                onBack = {
                    navController.popBackStack()
                },
            )
        }

        composable(KioskDestinations.TABLE_TENT) {
            val tableTentViewModel =
                remember(appGraph) {
                    TableTentViewModel(
                        orderGraph = appGraph.orderGraph,
                    )
                }
            TableTentScreen(
                viewModel = tableTentViewModel,
                onConfirmed = {
                    navController.navigate(KioskDestinations.PAYMENT)
                },
                onBack = {
                    navController.popBackStack()
                },
            )
        }

        composable(KioskDestinations.PAYMENT) {
            val paymentViewModel =
                remember(appGraph) {
                    PaymentViewModel(
                        checkoutUseCase = appGraph.checkoutUseCase,
                        paymentTerminal = appGraph.paymentTerminal,
                        orderGraph = appGraph.orderGraph,
                        onOrderSuccess = { info ->
                            appGraph.completedOrderState.value = info
                            navController.navigate(KioskDestinations.ORDER_NUMBER) {
                                popUpTo(KioskDestinations.PAYMENT) { inclusive = true }
                            }
                        },
                    )
                }
            PaymentScreen(
                viewModel = paymentViewModel,
                onBack = {
                    navController.popBackStack()
                },
            )
        }

        composable(KioskDestinations.ORDER_NUMBER) {
            val completedOrder by appGraph.completedOrderState.collectAsStateWithLifecycle()
            val order = completedOrder
            if (order != null) {
                OrderNumberScreen(
                    orderInfo = order,
                    onFinish = {
                        appGraph.orderGraph.reset()
                        appGraph.completedOrderState.value = null
                        navController.navigate(KioskDestinations.ATTRACT) {
                            popUpTo(KioskDestinations.ATTRACT) { inclusive = true }
                        }
                    },
                )
            } else {
                LaunchedEffect(Unit) {
                    navController.navigate(KioskDestinations.ATTRACT) {
                        popUpTo(KioskDestinations.ATTRACT) { inclusive = true }
                    }
                }
            }
        }
    }
}
