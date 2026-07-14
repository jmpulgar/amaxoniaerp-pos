package com.amaxonia.pos

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.amaxonia.pos.core.logging.SafeLog
import com.amaxonia.pos.data.printer.RapidPayBridge
import com.amaxonia.pos.ui.common.DependencyContainer
import com.amaxonia.pos.ui.navigation.AppNavigation
import com.amaxonia.pos.ui.theme.PosTheme
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
        if (BuildConfig.DEBUG) CrashLogger.setup(this)
        DependencyContainer.initialize(applicationContext)
        enableEdgeToEdge()

        // Handle Rapid Pay result if this onCreate was triggered by HKA POS re-launching us
        handleRapidPayResult(intent, source = "onCreate")

        // Determinar la ruta inicial ANTES de renderizar, de forma bloqueante.
        // DataStore usa .first() que es una sola lectura de disco; es rápido y seguro aquí.
        val startDestination =
            runBlocking {
                val auth = DependencyContainer.localStore.readAuthSnapshot()
                val company = DependencyContainer.localStore.readCompanySession()
                when {
                    auth != null && company != null -> "dashboard"
                    auth != null -> "select_company"
                    else -> "welcome"
                }
            }

        setContent {
            PosTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    AppNavigation(startDestination = startDestination)
                }
            }
        }
    }

    /**
     * Called when the Activity is re-launched while already running (launchMode="singleTask").
     * This is how the HKA POS app returns results — it re-launches our MainActivity with extras.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        SafeLog.d(TAG, "Rapid Pay result intent received")
        handleRapidPayResult(intent, source = "onNewIntent")
    }

    /**
     * Checks the intent for Rapid Pay result extras and delivers them to the bridge.
     */
    private fun handleRapidPayResult(
        intent: Intent?,
        source: String,
    ) {
        if (intent == null) return

        if (intent.getStringExtra(EXTRA_RESULT_CODE) == null) return

        SafeLog.d(TAG, "Rapid Pay result received from $source")

        if (!RapidPayBridge.hasPendingRequest()) {
            SafeLog.w(TAG, "Rapid Pay result ignored because no request is pending")
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
