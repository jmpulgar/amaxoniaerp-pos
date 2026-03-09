package com.amaxonia.pos

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.amaxonia.pos.ui.navigation.AppNavigation
import com.amaxonia.pos.ui.theme.AmaxoniaPOSTheme
import com.amaxonia.pos.ui.common.DependencyContainer
import kotlinx.coroutines.runBlocking
import org.json.JSONObject

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CrashLogger.setup(this)
        DependencyContainer.initialize(applicationContext)
        enableEdgeToEdge()

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

        handleTheFactoryResult(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleTheFactoryResult(intent)
    }

    private fun handleTheFactoryResult(intent: Intent?) {
        val safeIntent = intent ?: return
        if (!safeIntent.hasExtra(EXTRA_CODE_RAPID_PAY)) return

        val code = safeIntent.getStringExtra(EXTRA_CODE_RAPID_PAY).orEmpty()
        val message = buildTheFactoryMessage(
            code = code,
            rawMessage = safeIntent.getStringExtra(EXTRA_MESSAGE_RAPID_PAY),
            rawResult = safeIntent.getStringExtra(EXTRA_RESULT_RAPID_PAY)
        )

        if (message.isNotBlank()) {
            Log.i("TheFactoryResult", "code=$code message=$message")
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        }
    }

    private fun buildTheFactoryMessage(
        code: String,
        rawMessage: String?,
        rawResult: String?
    ): String {
        val fallbackMessage = rawMessage?.takeIf { it.isNotBlank() }
        val parsedResultMessage = rawResult
            ?.takeIf { it.isNotBlank() }
            ?.let(::extractTheFactoryMessage)

        return when (code) {
            OK_RAPID_PAY -> parsedResultMessage ?: "The Factory proceso la impresion"
            NO_OK_RAPID_PAY -> fallbackMessage ?: parsedResultMessage ?: "The Factory rechazo la impresion"
            else -> fallbackMessage ?: parsedResultMessage ?: "The Factory devolvio una respuesta desconocida"
        }
    }

    private fun extractTheFactoryMessage(rawResult: String): String? {
        return runCatching {
            val resultJson = JSONObject(rawResult)
            val responseMessage = resultJson.optString("responseMessage").trim()
            val nestedJson = resultJson.optString("json").trim()

            if (nestedJson.isNotEmpty()) {
                val nestedObject = JSONObject(nestedJson)
                val receipt = nestedObject.optString("reciboPrint").trim()
                when {
                    responseMessage.isNotEmpty() && receipt.isNotEmpty() -> "$responseMessage\n$receipt"
                    responseMessage.isNotEmpty() -> responseMessage
                    receipt.isNotEmpty() -> receipt
                    else -> null
                }
            } else {
                responseMessage.ifEmpty { null }
            }
        }.getOrNull()
    }

    private companion object {
        const val EXTRA_RESULT_RAPID_PAY = "resultRapidPay"
        const val EXTRA_MESSAGE_RAPID_PAY = "messageRapidPay"
        const val EXTRA_CODE_RAPID_PAY = "codeRapidPay"
        const val OK_RAPID_PAY = "200"
        const val NO_OK_RAPID_PAY = "400"
    }
}
