package com.amaxonia.pos.ui.navigation

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.amaxonia.pos.composition.AppGraph
import com.amaxonia.pos.core.logging.SafeLog
import com.amaxonia.pos.domain.repository.CartRepository
import com.amaxonia.pos.domain.repository.TableAccountPayment
import com.amaxonia.pos.ui.caja.CierreCajaScreen
import com.amaxonia.pos.ui.cart.CartScreen
import com.amaxonia.pos.ui.clients.ClientFormScreen
import com.amaxonia.pos.ui.clients.ClientListScreen
import com.amaxonia.pos.ui.clients.ClientSelectionScreen
import com.amaxonia.pos.ui.company.CompanySelectionScreen
import com.amaxonia.pos.ui.creditnotes.CreditNotesScreen
import com.amaxonia.pos.ui.dashboard.DashboardScreen
import com.amaxonia.pos.ui.drafts.DraftInvoicesScreen
import com.amaxonia.pos.ui.history.HistoryScreen
import com.amaxonia.pos.ui.login.LoginScreen
import com.amaxonia.pos.ui.mesas.AreasMesasScreen
import com.amaxonia.pos.ui.mesas.ComandaScreen
import com.amaxonia.pos.ui.mesas.CuentaMesaScreen
import com.amaxonia.pos.ui.payment.PaymentScreen
import com.amaxonia.pos.ui.payment.SuccessScreen
import com.amaxonia.pos.ui.products.ProductFormScreen
import com.amaxonia.pos.ui.products.ProductListScreen
import com.amaxonia.pos.ui.reports.ReportsScreen
import com.amaxonia.pos.ui.settings.SettingsScreen
import com.amaxonia.pos.ui.sync.SyncScreen
import com.amaxonia.pos.ui.theme.OfflineRed
import com.amaxonia.pos.ui.theme.OnlineGreen
import com.amaxonia.pos.ui.theme.PosPalette
import com.amaxonia.pos.ui.welcome.WelcomeScreen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

private const val NAV_LOG_TAG = "AppNavigation"

@Composable
fun AppNavigation(startDestination: String) {
    val navController = rememberNavController()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val cartRepository = AppGraph.dashboard.cartRepository
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(navController) {
        navController.currentBackStackEntryFlow.collect { entry ->
            SafeLog.d(NAV_LOG_TAG, "Navigation destination changed")
        }
    }

    ConnectivityEffect(context = context, snackbarHostState = snackbarHostState)

    Box(modifier = Modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.fillMaxSize(),
        ) {
            welcomeDestination(navController)
            loginDestination(navController)
            companySelectionDestination(navController, context)
            syncDestination(navController)
            dashboardDestination(navController, scope)
            mesasDestinations(navController)
            comandaDestination(navController)
            cuentaMesaDestination(navController, cartRepository)
            operationsDestinations(navController)
            clientsDestinations(navController, cartRepository)
            productsDestinations(navController)
            cartDestination(navController)
            paymentDestinations(navController, scope, cartRepository)
        }

        ConnectivitySnackbarHost(
            snackbarHostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

/** Muestra snackbars de conectividad y reenvía facturas pendientes al volver online. */
@Composable
private fun ConnectivityEffect(
    context: Context,
    snackbarHostState: SnackbarHostState,
) {
    val isOnline by AppGraph.networkMonitor.isOnlineFlow.collectAsStateWithLifecycle(
        initialValue = AppGraph.networkMonitor.isOnline(),
    )
    var hasSeenConnectivityState by remember { mutableStateOf(false) }

    LaunchedEffect(isOnline) {
        if (!hasSeenConnectivityState) {
            hasSeenConnectivityState = true
            if (!isOnline) {
                snackbarHostState.showSnackbar(
                    message = "Sin conexión. Puedes seguir trabajando offline.",
                    duration = SnackbarDuration.Long,
                )
            }
            return@LaunchedEffect
        }

        snackbarHostState.showSnackbar(
            message =
                if (isOnline) {
                    AppGraph.sync.enqueuePendingInvoices(context)
                    "Conexión restaurada. Reenviando pendientes..."
                } else {
                    "Sin conexión. Puedes seguir trabajando offline."
                },
            duration = SnackbarDuration.Long,
        )
    }
}

/** Snackbar host con colores según el tipo de mensaje de conectividad. */
@Composable
private fun ConnectivitySnackbarHost(
    snackbarHostState: SnackbarHostState,
    modifier: Modifier,
) {
    SnackbarHost(
        hostState = snackbarHostState,
        modifier = modifier,
    ) { snackbarData ->
        val message = snackbarData.visuals.message
        val isOnlineMessage = message.startsWith("Conexión restaurada")
        val isOfflineMessage = message.startsWith("Sin conexión")
        Snackbar(
            snackbarData = snackbarData,
            containerColor =
                when {
                    isOnlineMessage -> OnlineGreen
                    isOfflineMessage -> OfflineRed
                    else -> MaterialTheme.colorScheme.inverseSurface
                },
            contentColor =
                when {
                    isOnlineMessage || isOfflineMessage -> PosPalette.FixedWhite
                    else -> MaterialTheme.colorScheme.inverseOnSurface
                },
            actionColor = PosPalette.FixedWhite,
            shape = RoundedCornerShape(18.dp),
        )
    }
}

// Navega y limpia todo el back stack de forma consistente.
private fun NavController.navigateAndClearStack(route: String) {
    navigate(route) {
        popUpTo(graph.id) { inclusive = false }
        launchSingleTop = true
    }
}

// Navegar desde el drawer: siempre deja el back stack limpio como [dashboard -> destino].
// launchSingleTop evita duplicados, popUpTo(dashboard) evita acumulación.
private fun NavController.navigateFromDrawer(route: String) {
    if (currentBackStackEntry?.destination?.route == route) return
    // Para evitar inconsistencias al hacer muchas navegaciones seguidas,
    // evitamos saveState/restoreState y mantenemos la pila consistente.
    navigate(route) {
        popUpTo("dashboard") { inclusive = false }
        launchSingleTop = true
    }
}

private fun NavGraphBuilder.welcomeDestination(navController: NavController) {
    composable("welcome") {
        WelcomeScreen(
            onLoginClick = { navController.navigate("login") },
            onRequestAccountClick = { },
        )
    }
}

private fun NavGraphBuilder.loginDestination(navController: NavController) {
    composable("login") {
        LoginScreen(
            onLoginSuccess = {
                // Limpia todo el stack y va a select_company
                navController.navigateAndClearStack("select_company")
            },
            onBack = { navController.popBackStack() },
        )
    }
}

private fun NavGraphBuilder.companySelectionDestination(
    navController: NavController,
    context: Context,
) {
    composable("select_company") {
        CompanySelectionScreen(
            onCompanySelected = {
                AppGraph.sync.enqueueManual(context)
                AppGraph.sync.schedulePeriodic(context)
                // Limpia todo el stack y va a dashboard
                navController.navigateAndClearStack("dashboard")
            },
            onBack = { navController.popBackStack() },
        )
    }
}

private fun NavGraphBuilder.syncDestination(navController: NavController) {
    composable("catalog_sync") {
        SyncScreen(
            onSyncCompleted = {
                navController.navigateAndClearStack("dashboard")
            },
        )
    }
}

private fun NavGraphBuilder.dashboardDestination(
    navController: NavController,
    scope: CoroutineScope,
) {
    composable("dashboard") {
        DashboardScreen(
            onLogout = {
                scope.launch {
                    AppGraph.dashboard.logout()
                    navController.navigateAndClearStack("welcome")
                }
            },
            // Todas las navegaciones del drawer pasan por navigateFromDrawer
            onNavigateToClients = { navController.navigateFromDrawer("clients_list") },
            onNavigateToProducts = { navController.navigateFromDrawer("products_list") },
            onNavigateToHistory = { navController.navigateFromDrawer("history") },
            onNavigateToCreditNotes = { navController.navigateFromDrawer("credit_notes") },
            onNavigateToReports = { navController.navigateFromDrawer("reports") },
            onNavigateToPrinterSettings = { navController.navigateFromDrawer("printer_settings") },
            onNavigateToCart = { navController.navigate("cart") },
            onStartNewOrder = { navController.navigateFromDrawer("client_selection_mode") },
            onNavigateToCierreCaja = { navController.navigateFromDrawer("cierre_caja") },
            onNavigateToDraftInvoices = { navController.navigateFromDrawer("draft_invoices") },
            onNavigateToAreasMesas = { navController.navigateFromDrawer("areas_mesas") },
        )
    }
}

/** Selector de áreas y mesas. */
private fun NavGraphBuilder.mesasDestinations(navController: NavController) {
    composable("areas_mesas") {
        AreasMesasScreen(
            onBack = { navController.popBackStack() },
            onSelectCaja = {
                // Sin caja no hay sucursal: se reutiliza el selector de caja del Dashboard.
                AppGraph.dashboard.requestCajaSelectorOnDashboard()
                navController.navigateAndClearStack("dashboard")
            },
            // Fase 3 - Comanda: al tener sesión activa para la mesa, navegamos a la
            // pantalla de comanda/pedido. La sesión se acaba de abrir o ya existía.
            onTableConfirmed = { mesa ->
                SafeLog.d(
                    NAV_LOG_TAG,
                    "Sesión ya gestionada en pantalla; mesa ${mesa.id} lista para comanda",
                )
            },
            onComenzarPedido = { mesa, sesionId ->
                navController.navigate("comanda/${mesa.areaId}/${mesa.id}/$sesionId")
            },
        )
    }
}

/** Comanda/pedido de una mesa con sesión activa. */
private fun NavGraphBuilder.comandaDestination(navController: NavController) {
    composable(
        "comanda/{areaId}/{mesaId}/{sesionId}",
        arguments =
            listOf(
                navArgument("areaId") { type = NavType.IntType },
                navArgument("mesaId") { type = NavType.IntType },
                navArgument("sesionId") { type = NavType.IntType },
            ),
    ) { entry ->
        val areaId = entry.arguments?.getInt("areaId") ?: return@composable
        val mesaId = entry.arguments?.getInt("mesaId") ?: return@composable
        val sesionId = entry.arguments?.getInt("sesionId") ?: return@composable
        val selected = AppGraph.mesas.selectedTableHolder.selectedTable.value
        val mesaNombre = selected?.mesa?.displayName ?: "Mesa $mesaId"
        val comandaViewModel =
            remember {
                AppGraph.mesas.comandaViewModel(areaId, mesaId, sesionId)
            }
        val cartViewModel =
            remember {
                AppGraph.cart.cartViewModel()
            }
        ComandaScreen(
            mesaNombre = mesaNombre,
            sesionId = sesionId,
            viewModel = comandaViewModel,
            cartViewModel = cartViewModel,
            onBack = { navController.popBackStack() },
            onCuenta = {
                navController.navigate("cuenta_mesa/$areaId/$mesaId/$sesionId")
            },
        )
    }
}

/** Cuenta de la mesa con cliente y pago. */
private fun NavGraphBuilder.cuentaMesaDestination(
    navController: NavController,
    cartRepository: CartRepository,
) {
    composable(
        "cuenta_mesa/{areaId}/{mesaId}/{sesionId}",
        arguments =
            listOf(
                navArgument("areaId") { type = NavType.IntType },
                navArgument("mesaId") { type = NavType.IntType },
                navArgument("sesionId") { type = NavType.IntType },
            ),
    ) { entry ->
        val areaId = entry.arguments?.getInt("areaId") ?: return@composable
        val mesaId = entry.arguments?.getInt("mesaId") ?: return@composable
        val sesionId = entry.arguments?.getInt("sesionId") ?: return@composable
        val selected = AppGraph.mesas.selectedTableHolder.selectedTable.value
        val client by cartRepository.selectedClient.collectAsStateWithLifecycle()
        val cuentaViewModel =
            remember(areaId, mesaId, sesionId) {
                AppGraph.mesas.cuentaMesaViewModel(areaId, mesaId, sesionId)
            }
        CuentaMesaScreen(
            mesaNombre = selected?.mesa?.displayName ?: "Mesa $mesaId",
            clientName =
                client?.let { "${it.firstName} ${it.lastName}".trim().ifBlank { it.code } },
            viewModel = cuentaViewModel,
            onBack = { navController.popBackStack() },
            onSelectClient = { navController.navigate("client_selection_mode") },
            onPay = { cuenta ->
                AppGraph.mesas.selectTableAccountForPayment(
                    TableAccountPayment(areaId, mesaId, sesionId, cuenta),
                )
                navController.navigate("payment/${cuenta.total}")
            },
        )
    }
}

/** Destinations operativos: settings, borradores, cierre de caja e historiales. */
private fun NavGraphBuilder.operationsDestinations(navController: NavController) {
    composable("printer_settings") {
        SettingsScreen(onBack = { navController.popBackStack() })
    }

    composable("draft_invoices") {
        DraftInvoicesScreen(
            onBack = { navController.popBackStack() },
            onDraftLoaded = {
                // Al cargar borrador, ir al carrito
                navController.navigate("cart") {
                    popUpTo("dashboard") { inclusive = false }
                }
            },
        )
    }

    composable("cierre_caja") {
        CierreCajaScreen(
            onBack = { navController.popBackStack() },
            onCloseSuccess = {
                navController.navigateAndClearStack("dashboard")
            },
            onOpenNewCaja = {
                // Pide al Dashboard abrir el diálogo de apertura al recibir foco.
                AppGraph.dashboard.requestAperturaOnDashboard()
                navController.navigateAndClearStack("dashboard")
            },
        )
    }

    composable("history") {
        HistoryScreen(
            onBack = { navController.popBackStack() },
        )
    }

    composable("credit_notes") {
        CreditNotesScreen(
            onBack = { navController.popBackStack() },
        )
    }

    composable("reports") {
        ReportsScreen(
            onBack = { navController.popBackStack() },
        )
    }
}

/** Destinations de clientes: listado, formulario y selección para el carrito. */
private fun NavGraphBuilder.clientsDestinations(
    navController: NavController,
    cartRepository: CartRepository,
) {
    composable("clients_list") {
        ClientListScreen(
            onBack = { navController.popBackStack() },
            onNavigateToForm = { clientId ->
                val route = if (clientId != null) "client_form/$clientId" else "client_form/new"
                navController.navigate(route)
            },
        )
    }

    composable(
        route = "client_form/{clientId}",
        arguments = listOf(navArgument("clientId") { type = NavType.StringType }),
    ) { backStackEntry ->
        val idArg = backStackEntry.arguments?.getString("clientId")
        val clientId = if (idArg == "new") null else idArg
        ClientFormScreen(
            clientId = clientId,
            onBack = { navController.popBackStack() },
            onSaveSuccess = { navController.popBackStack() },
        )
    }

    composable("client_selection_mode") {
        ClientSelectionScreen(
            onBack = { navController.popBackStack() },
            onClientSelected = { client ->
                cartRepository.setClient(client)
                navController.popBackStack()
            },
        )
    }
}

/** Destinations de productos: listado y formulario. */
private fun NavGraphBuilder.productsDestinations(navController: NavController) {
    composable("products_list") {
        ProductListScreen(
            onBack = { navController.popBackStack() },
            onNavigateToForm = { productId ->
                val route = if (productId != null) "product_form/$productId" else "product_form/new"
                navController.navigate(route)
            },
        )
    }

    composable(
        route = "product_form/{productId}",
        arguments = listOf(navArgument("productId") { type = NavType.StringType }),
    ) { backStackEntry ->
        val idArg = backStackEntry.arguments?.getString("productId")
        val productId = if (idArg == "new") null else idArg
        ProductFormScreen(
            productId = productId,
            onBack = { navController.popBackStack() },
            onSaveSuccess = { navController.popBackStack() },
        )
    }
}

private fun NavGraphBuilder.cartDestination(navController: NavController) {
    composable("cart") {
        CartScreen(
            onBack = {
                val currentRoute = navController.currentBackStackEntry?.destination?.route
                SafeLog.d(NAV_LOG_TAG, "Cart back navigation requested")

                if (currentRoute != "cart") {
                    SafeLog.w(NAV_LOG_TAG, "Ignoring stale cart back callback")
                    return@CartScreen
                }

                if (!navController.popBackStack()) {
                    SafeLog.w(NAV_LOG_TAG, "Cart back stack was empty; restoring dashboard")
                    navController.navigateAndClearStack("dashboard")
                }
            },
            onCheckout = { total ->
                navController.navigate("payment/$total")
            },
            onSelectClient = {
                navController.navigate("client_selection_mode")
            },
        )
    }
}

/** Destinations del flujo de pago: cobro y pantalla de éxito. */
private fun NavGraphBuilder.paymentDestinations(
    navController: NavController,
    scope: CoroutineScope,
    cartRepository: CartRepository,
) {
    composable(
        route = "payment/{total}",
        arguments = listOf(navArgument("total") { type = NavType.StringType }),
    ) { backStackEntry ->
        val total = backStackEntry.arguments?.getString("total")?.toDoubleOrNull() ?: 0.0
        PaymentScreen(
            totalAmount = total,
            onBack = { navController.popBackStack() },
            onPaymentSuccess = { payload ->
                scope.launch {
                    AppGraph.payment.handlePaymentSuccess(payload)
                    navController.navigate("payment_success/${payload.transactionId}") {
                        popUpTo("dashboard") { inclusive = false }
                    }
                }
            },
            onNavigateToApertura = {
                AppGraph.dashboard.requestAperturaOnDashboard()
                navController.navigateAndClearStack("dashboard")
            },
        )
    }

    composable(
        route = "payment_success/{transactionId}",
        arguments =
            listOf(
                navArgument("transactionId") {
                    type = NavType.StringType
                    defaultValue = ""
                },
            ),
    ) { backStackEntry ->
        SuccessScreen(
            transactionId = backStackEntry.arguments?.getString("transactionId").orEmpty(),
            onPrintReceipt = { trxId ->
                AppGraph.payment.printSuccessReceipt(trxId)
            },
            onNextOrder = {
                cartRepository.clearCart()
                navController.navigateAndClearStack("dashboard")
            },
        )
    }
}
