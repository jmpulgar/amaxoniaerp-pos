package com.amaxonia.pos.ui.sync

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amaxonia.pos.data.sync.SyncScheduler
import com.amaxonia.pos.ui.common.DependencyContainer
import com.amaxonia.pos.ui.common.injectedViewModel
import com.amaxonia.pos.ui.theme.AmaxoniaBlue

@Composable
fun SyncScreen(
    viewModel: SyncViewModel = injectedViewModel {
        SyncViewModel(DependencyContainer.catalogSyncer)
    },
    onSyncCompleted: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.startSyncIfNeeded {
            SyncScheduler.schedulePeriodic(context)
            onSyncCompleted()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Text(
                text = "Descargando catálogo...",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = AmaxoniaBlue
            )
            Spacer(modifier = Modifier.height(16.dp))
            if (state.isLoading) {
                CircularProgressIndicator(color = AmaxoniaBlue)
            } else if (state.error != null) {
                Text(
                    text = state.error ?: "Error al sincronizar",
                    color = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(onClick = {
                    viewModel.retry {
                        SyncScheduler.schedulePeriodic(context)
                        onSyncCompleted()
                    }
                }) {
                    Text("Reintentar")
                }
            }
        }
    }
}
