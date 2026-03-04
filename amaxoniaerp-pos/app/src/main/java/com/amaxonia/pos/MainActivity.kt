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

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DependencyContainer.initialize(applicationContext)
        enableEdgeToEdge()
        setContent {
            AmaxoniaPOSTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppNavigation()
                }
            }
        }
    }
}
