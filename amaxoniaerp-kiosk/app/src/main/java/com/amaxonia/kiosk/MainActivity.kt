package com.amaxonia.kiosk

import android.content.pm.ActivityInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.amaxonia.kiosk.di.AppGraph
import com.amaxonia.kiosk.ui.attract.AttractScreen
import com.amaxonia.kiosk.ui.attract.AttractViewModel
import com.amaxonia.kiosk.ui.navigation.KioskDestinations
import com.amaxonia.kiosk.ui.pairing.PairingScreen
import com.amaxonia.kiosk.ui.pairing.PairingViewModel
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
                    navController.navigate(KioskDestinations.MENU)
                },
                onAdminUnlocked = {
                    navController.navigate(KioskDestinations.PAIRING)
                },
            )
        }

        composable(KioskDestinations.MENU) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Menú Principal (A5)",
                    style = MaterialTheme.typography.headlineLarge,
                )
            }
        }
    }
}
