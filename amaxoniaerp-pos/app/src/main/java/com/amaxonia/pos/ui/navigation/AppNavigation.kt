package com.amaxonia.pos.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.amaxonia.pos.ui.cart.CartScreen
import com.amaxonia.pos.ui.clients.ClientFormScreen
import com.amaxonia.pos.ui.clients.ClientListScreen
import com.amaxonia.pos.ui.clients.ClientSelectionScreen
import com.amaxonia.pos.ui.company.CompanySelectionScreen
import com.amaxonia.pos.ui.common.DependencyContainer
import com.amaxonia.pos.ui.caja.CierreCajaScreen
import com.amaxonia.pos.ui.dashboard.DashboardScreen
import com.amaxonia.pos.ui.history.HistoryScreen
import com.amaxonia.pos.ui.login.LoginScreen
import com.amaxonia.pos.ui.payment.PaymentScreen
import com.amaxonia.pos.ui.payment.SuccessScreen
import com.amaxonia.pos.ui.products.ProductFormScreen
import com.amaxonia.pos.ui.products.ProductListScreen
import com.amaxonia.pos.ui.reports.ReportsScreen
import com.amaxonia.pos.ui.drafts.DraftInvoicesScreen
import com.amaxonia.pos.ui.settings.SettingsScreen
import com.amaxonia.pos.ui.sync.SyncScreen
import com.amaxonia.pos.ui.welcome.WelcomeScreen
import com.amaxonia.pos.data.sync.SyncScheduler
import kotlinx.coroutines.launch

@Composable
fun AppNavigation(startDestination: String) {
    val navController = rememberNavController()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val cartRepository = DependencyContainer.cartRepository

    // Navega y limpia todo el back stack de forma consistente.
    fun navigateAndClearStack(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.id) { inclusive = false }
            launchSingleTop = true
        }
    }

    // Navegar desde el drawer: siempre deja el back stack limpio como [dashboard -> destino].
    // launchSingleTop evita duplicados, popUpTo(dashboard) evita acumulación.
    fun navigateFromDrawer(route: String) {
        if (navController.currentBackStackEntry?.destination?.route == route) return
        // Para evitar inconsistencias al hacer muchas navegaciones seguidas,
        // evitamos saveState/restoreState y mantenemos la pila consistente.
        navController.navigate(route) {
            popUpTo("dashboard") { inclusive = false }
            launchSingleTop = true
        }
    }

    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable("welcome") {
            WelcomeScreen(
                onLoginClick = { navController.navigate("login") },
                onRequestAccountClick = { }
            )
        }

        composable("login") {
            LoginScreen(
                onLoginSuccess = {
                    // Limpia todo el stack y va a select_company
                    navigateAndClearStack("select_company")
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable("select_company") {
            CompanySelectionScreen(
                onCompanySelected = {
                    SyncScheduler.enqueueManual(context)
                    SyncScheduler.schedulePeriodic(context)
                    // Limpia todo el stack y va a dashboard
                    navigateAndClearStack("dashboard")
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable("catalog_sync") {
            SyncScreen(
                onSyncCompleted = {
                    navigateAndClearStack("dashboard")
                }
            )
        }

        composable("dashboard") {
            DashboardScreen(
                onLogout = {
                    scope.launch {
                        DependencyContainer.authRepository.logout()
                        navigateAndClearStack("welcome")
                    }
                },
                // Todas las navegaciones del drawer pasan por navigateFromDrawer
                onNavigateToClients = { navigateFromDrawer("clients_list") },
                onNavigateToProducts = { navigateFromDrawer("products_list") },
                onNavigateToHistory = { navigateFromDrawer("history") },
                onNavigateToReports = { navigateFromDrawer("reports") },
                onNavigateToPrinterSettings = { navigateFromDrawer("printer_settings") },
                onNavigateToCart = { navController.navigate("cart") },
                onStartNewOrder = { navigateFromDrawer("client_selection_mode") },
                onNavigateToCierreCaja = { navigateFromDrawer("cierre_caja") },
                onNavigateToDraftInvoices = { navigateFromDrawer("draft_invoices") }
            )
        }

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
                }
            )
        }

        composable("cierre_caja") {
            CierreCajaScreen(
                onBack = { navController.popBackStack() },
                onCloseSuccess = {
                    navigateAndClearStack("dashboard")
                }
            )
        }

        composable("clients_list") {
            ClientListScreen(
                onBack = { navController.popBackStack() },
                onNavigateToForm = { clientId ->
                    val route = if (clientId != null) "client_form/$clientId" else "client_form/new"
                    navController.navigate(route)
                }
            )
        }

        composable(
            route = "client_form/{clientId}",
            arguments = listOf(navArgument("clientId") { type = NavType.StringType })
        ) { backStackEntry ->
            val idArg = backStackEntry.arguments?.getString("clientId")
            val clientId = if (idArg == "new") null else idArg
            ClientFormScreen(
                clientId = clientId,
                onBack = { navController.popBackStack() },
                onSaveSuccess = { navController.popBackStack() }
            )
        }

        composable("client_selection_mode") {
            ClientSelectionScreen(
                onBack = { navController.popBackStack() },
                onClientSelected = { client ->
                    cartRepository.setClient(client)
                    navController.popBackStack()
                }
            )
        }

        composable("products_list") {
            ProductListScreen(
                onBack = { navController.popBackStack() },
                onNavigateToForm = { productId ->
                    val route = if (productId != null) "product_form/$productId" else "product_form/new"
                    navController.navigate(route)
                }
            )
        }

        composable(
            route = "product_form/{productId}",
            arguments = listOf(navArgument("productId") { type = NavType.StringType })
        ) { backStackEntry ->
            val idArg = backStackEntry.arguments?.getString("productId")
            val productId = if (idArg == "new") null else idArg
            ProductFormScreen(
                productId = productId,
                onBack = { navController.popBackStack() },
                onSaveSuccess = { navController.popBackStack() }
            )
        }

        composable("history") {
            HistoryScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable("reports") {
            ReportsScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable("cart") {
            CartScreen(
                onBack = { navController.popBackStack() },
                onCheckout = { total ->
                    navController.navigate("payment/$total")
                },
                onSelectClient = {
                    navController.navigate("client_selection_mode")
                }
            )
        }

        composable(
            route = "payment/{total}",
            arguments = listOf(navArgument("total") { type = NavType.StringType })
        ) { backStackEntry ->
            val total = backStackEntry.arguments?.getString("total")?.toDoubleOrNull() ?: 0.0
            PaymentScreen(
                totalAmount = total,
                onBack = { navController.popBackStack() },
                onPaymentSuccess = { payload ->
                    scope.launch {
                        DependencyContainer.localStore.saveLastPaymentSuccess(payload)
                        navController.navigate("payment_success/${payload.transactionId}") {
                            popUpTo("dashboard") { inclusive = false }
                        }
                    }
                }
            )
        }

        composable(
            route = "payment_success/{transactionId}",
            arguments = listOf(
                navArgument("transactionId") { type = NavType.StringType; defaultValue = "" }
            )
        ) { backStackEntry ->
            SuccessScreen(
                transactionId = backStackEntry.arguments?.getString("transactionId").orEmpty(),
                onPrintReceipt = { trxId ->
                    val transactionResult = DependencyContainer.transactionRepository.getTransactionById(trxId)
                    if (transactionResult.isFailure) {
                        Result.failure(transactionResult.exceptionOrNull() ?: IllegalStateException("No se encontro la transaccion"))
                    } else {
                        val printer = DependencyContainer.printerFactory.getActivePrinter()
                        if (printer == null) {
                            Result.failure(IllegalStateException("No hay impresora configurada"))
                        } else {
                            val transaction = transactionResult.getOrThrow()
                            printer.printReceipt(transaction).fold(
                                onSuccess = { started ->
                                    if (started) {
                                        Result.success("Imprimiendo recibo...")
                                    } else {
                                        Result.failure(
                                            IllegalStateException("No se pudo iniciar la impresion del recibo")
                                        )
                                    }
                                },
                                onFailure = { error ->
                                    Result.failure(error)
                                }
                            )
                        }
                    }
                },
                onNextOrder = {
                    cartRepository.clearCart()
                    navigateAndClearStack("dashboard")
                }
            )
        }
    }
}
