package com.amaxonia.pos

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.amaxonia.pos.ui.navigation.AppNavigation
import com.amaxonia.pos.ui.theme.AmaxoniaPOSTheme
import com.amaxonia.pos.ui.common.DependencyContainer
import kotlinx.coroutines.runBlocking

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
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppNavigation(startDestination = startDestination)
                }
            }
        }
    }
}

