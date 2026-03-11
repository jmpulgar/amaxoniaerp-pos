package com.amaxonia.pos

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.amaxonia.pos.data.printer.RapidPayBridge
import com.amaxonia.pos.data.printer.RapidPayResult
import com.amaxonia.pos.ui.navigation.AppNavigation
import com.amaxonia.pos.ui.theme.AmaxoniaPOSTheme
import com.amaxonia.pos.ui.common.DependencyContainer
import kotlinx.coroutines.runBlocking

class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "MainActivity"

        // Intent extra keys from HKA POS (matches SDK Constants.java)
        private const val EXTRA_RESULT_CODE = "codeRapidPay"
        private const val EXTRA_RESULT_DATA = "resultRapidPay"
        private const val EXTRA_MESSAGE = "messageRapidPay"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CrashLogger.setup(this)
        DependencyContainer.initialize(applicationContext)
        enableEdgeToEdge()

        // Handle Rapid Pay result if this onCreate was triggered by HKA POS re-launching us
        handleRapidPayResult(intent)

        // Determinar la ruta inicial ANTES de renderizar, de forma bloqueante.
        // DataStore usa .first() que es una sola lectura de disco; es rápido y seguro aquí.
        val startDestination = runBlocking {
            val auth = DependencyContainer.localStore.readAuthSnapshot()
            val company = DependencyContainer.localStore.readCompanySession()
            when {
                auth != null && company != null -> "dashboard"
                auth != null -> "select_company"
                else -> "welcome"
            }
        }

        setContent {
            AmaxoniaPOSTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation(startDestination = startDestination)
                }
            }
        }

    }

    /**
     * Called when the Activity is re-launched while already running (launchMode="singleTop").
     * This is how the HKA POS app returns results — it re-launches our MainActivity with extras.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        Log.d(TAG, "onNewIntent() → extras: ${intent.extras?.keySet()?.joinToString()}")
        handleRapidPayResult(intent)
    }

    /**
     * Checks the intent for Rapid Pay result extras and delivers them to the bridge.
     */
    private fun handleRapidPayResult(intent: Intent?) {
        if (intent == null) return

        val code = intent.getStringExtra(EXTRA_RESULT_CODE) ?: return

        Log.d(TAG, "handleRapidPayResult() → codigo recibido: $code")

        if (!RapidPayBridge.hasPendingRequest()) {
            Log.w(TAG, "handleRapidPayResult() → no hay solicitud pendiente, ignorando resultado")
            return
        }

        val result = DependencyContainer.theFactoryRapidPayClient.parseResultIntent(intent)
        RapidPayBridge.deliverResult(result)

        // Clear the extras so they don't get re-processed on configuration change
        intent.removeExtra(EXTRA_RESULT_CODE)
        intent.removeExtra(EXTRA_RESULT_DATA)
        intent.removeExtra(EXTRA_MESSAGE)
    }
}
