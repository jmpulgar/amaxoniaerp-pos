package com.amaxonia.pos.ui.navigation

import android.net.Uri
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
import com.amaxonia.pos.ui.dashboard.DashboardScreen
import com.amaxonia.pos.ui.history.HistoryScreen
import com.amaxonia.pos.ui.login.LoginScreen
import com.amaxonia.pos.ui.payment.PaymentScreen
import com.amaxonia.pos.ui.payment.SuccessScreen
import com.amaxonia.pos.ui.products.ProductFormScreen
import com.amaxonia.pos.ui.products.ProductListScreen
import com.amaxonia.pos.ui.reports.ReportsScreen
import com.amaxonia.pos.ui.sync.SyncScreen
import com.amaxonia.pos.ui.welcome.WelcomeScreen
import com.amaxonia.pos.data.sync.SyncScheduler
import kotlinx.coroutines.launch

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    // Obtenemos el repo aquí para inyectarlo en acciones rápidas
    val cartRepository = DependencyContainer.cartRepository

    NavHost(
        navController = navController,
        startDestination = "welcome"
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
                    navController.navigate("select_company") { popUpTo("login") { inclusive = true } }
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable("select_company") {
            CompanySelectionScreen(
                onCompanySelected = {
                    SyncScheduler.enqueueManual(context)
                    SyncScheduler.schedulePeriodic(context)
                    navController.navigate("dashboard") { popUpTo("select_company") { inclusive = true } }
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable("catalog_sync") {
            SyncScreen(
                onSyncCompleted = {
                    navController.navigate("dashboard") { popUpTo("catalog_sync") { inclusive = true } }
                }
            )
        }

        composable("dashboard") {
            DashboardScreen(
                onLogout = {
                    scope.launch {
                        DependencyContainer.authRepository.logout()
                        navController.navigate("welcome") { popUpTo(0) { inclusive = true } }
                    }
                },
                onNavigateToClients = { navController.navigate("clients_list") },
                onNavigateToProducts = { navController.navigate("products_list") },
                onNavigateToHistory = { navController.navigate("history") },
                onNavigateToReports = { navController.navigate("reports") },
                onNavigateToCart = { navController.navigate("cart") },
                // NUEVO: Al crear pedido nuevo, vamos a seleccionar cliente con modo selección
                onStartNewOrder = { navController.navigate("client_selection_mode") }
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

        // NUEVA RUTA: Selección de Cliente
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

        // --- NUEVA RUTA: CARRITO ---
        composable("cart") {
            CartScreen(
                onBack = { navController.popBackStack() },
                onCheckout = { total ->
                    // Navegar al pago enviando el total
                    navController.navigate("payment/$total")
                },
                onSelectClient = {
                    // Navegamos a la lista de clientes en modo selección
                    navController.navigate("client_selection_mode")
                }
            )
        }

        // Ruta para ir a Pagar
        composable(
            route = "payment/{total}",
            arguments = listOf(navArgument("total") { type = NavType.StringType })
        ) { backStackEntry ->
            val total = backStackEntry.arguments?.getString("total")?.toDoubleOrNull() ?: 0.0
            PaymentScreen(
                totalAmount = total,
                onBack = { navController.popBackStack() },
                onPaymentSuccess = { payload ->
                    val methods = Uri.encode(payload.paymentMethodsLabel)
                    val codFactura = Uri.encode(payload.codFactura)
                    // Navegar a éxito, eliminando la pantalla de pago del stack
                    navController.navigate("payment_success/${payload.changeDue}?methods=$methods&codFactura=$codFactura") {
                        popUpTo("dashboard") { inclusive = false }
                    }
                }
            )
        }

        // Ruta de Transacción Exitosa
        composable(
            route = "payment_success/{change}?methods={methods}&codFactura={codFactura}",
            arguments = listOf(
                navArgument("change") { type = NavType.StringType },
                navArgument("methods") { type = NavType.StringType; defaultValue = "" },
                navArgument("codFactura") { type = NavType.StringType; defaultValue = "" }
            )
        ) { backStackEntry ->
            val change = backStackEntry.arguments?.getString("change")?.toDoubleOrNull() ?: 0.0
            val methods = Uri.decode(backStackEntry.arguments?.getString("methods").orEmpty())
            val codFactura = Uri.decode(backStackEntry.arguments?.getString("codFactura").orEmpty())
            SuccessScreen(
                changeDue = change,
                paymentMethodsLabel = methods,
                codFactura = codFactura,
                onNextOrder = {
                    cartRepository.clearCart()
                    // Limpiar todo y volver al dashboard
                    navController.navigate("dashboard") {
                        popUpTo("dashboard") { inclusive = true }
                    }
                }
            )
        }
    }
}
