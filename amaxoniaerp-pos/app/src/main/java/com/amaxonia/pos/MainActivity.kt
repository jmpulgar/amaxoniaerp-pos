package com.amaxonia.pos

import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import com.amaxonia.pos.core.device.DeviceClass
import com.amaxonia.pos.core.device.deviceClassFor
import com.amaxonia.pos.core.logging.SafeLog
import com.amaxonia.pos.data.printer.RapidPayBridge
import com.amaxonia.pos.ui.common.DependencyContainer
import com.amaxonia.pos.ui.common.LocalDeviceClass
import com.amaxonia.pos.ui.common.rememberDeviceClass
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
        applyOrientationLock()
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
                CompositionLocalProvider(LocalDeviceClass provides rememberDeviceClass()) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background,
                    ) {
                        AppNavigation(startDestination = startDestination)
                    }
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

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        applyOrientationLock()
    }

    /**
     * Phones (and physical SUNMI terminals, which are small-screened) lock to portrait.
     * Tablets rotate freely. Re-applied on config changes to handle foldables/DeX resizing
     * across the tablet breakpoint.
     */
    private fun applyOrientationLock() {
        val smallestScreenWidthDp = resources.configuration.smallestScreenWidthDp
        requestedOrientation =
            when (deviceClassFor(smallestScreenWidthDp)) {
                DeviceClass.PHONE -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                DeviceClass.TABLET -> ActivityInfo.SCREEN_ORIENTATION_FULL_USER
            }
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

        val correlationId = RapidPayBridge.pendingCorrelationId()
        val responseCode = intent.getStringExtra(EXTRA_RESULT_CODE).orEmpty()
        val resultJson = intent.getStringExtra(EXTRA_RESULT_DATA)
        val message = intent.getStringExtra(EXTRA_MESSAGE)

        if (!RapidPayBridge.hasPendingRequest()) {
            SafeLog.w(TAG, "Rapid Pay result ignored because no request is pending")
            // Process died before onNewIntent — the in-memory Deferred is gone
            // but the callback Intent still reached the ledger row. Persist
            // the FULL HKA response (code + JSON + message) so an auditor can
            // reconcile the sale without losing the issuer's reference
            // (auditoría ítem 6 / HKA-002).
            if (correlationId != null) {
                markGatewayResolved(correlationId, responseCode, resultJson, message)
                // Auditoría ítem 10 (OBS-001): the callback arrived after the
                // in-memory bridge was reset by process death/recreate. This
                // is a late callback: it lands on disk but is not delivered
                // to any coroutine.
                com.amaxonia.pos.core.telemetry.SaleTelemetry.record(
                    event = com.amaxonia.pos.core.telemetry.SaleEvent.GATEWAY_LATE_CALLBACK,
                    idFactura = correlationId,
                    "responseCode" to responseCode,
                )
            }
            return
        }

        val result = DependencyContainer.theFactoryRapidPayClient.parseResultIntent(intent)
        RapidPayBridge.deliverResult(result)
        // Flip the transaction_log row to RESOLVED regardless of approved/denied.
        // The callback arrived — even a denied card clears the await; only a
        // no-show stays AWAITING for the watchdog to escalate.
        if (correlationId != null) {
            markGatewayResolved(correlationId, responseCode, resultJson, message)
            // Auditoría ítem 10 (OBS-001). We can detect a duplicate
            // callback cheaply: the bridge clears pendingResult after the
            // first delivery (RapidPayBridge.awaitResult finally block), so a
            // non-null correlationId landing here twice means HKA retried.
            com.amaxonia.pos.core.telemetry.SaleTelemetry.record(
                event = com.amaxonia.pos.core.telemetry.SaleEvent.GATEWAY_RESOLVED,
                idFactura = correlationId,
                "approved" to result.approved,
                "responseCode" to responseCode,
            )
        }

        // Clear the extras so they don't get re-processed on configuration change
        intent.removeExtra(EXTRA_RESULT_CODE)
        intent.removeExtra(EXTRA_RESULT_DATA)
        intent.removeExtra(EXTRA_MESSAGE)
    }

    private fun markGatewayResolved(
        correlationId: String,
        responseCode: String,
        rawResponse: String?,
        message: String?,
    ) {
        runBlocking {
            runCatching {
                DependencyContainer.gatewayCallbackLedger.markResolved(
                    correlationId = correlationId,
                    responseCode = responseCode,
                    rawResponse = rawResponse,
                    message = message,
                )
            }.onFailure { error ->
                SafeLog.w(TAG, "Failed to mark gateway resolved: ${error.message}")
            }
        }
    }
}
