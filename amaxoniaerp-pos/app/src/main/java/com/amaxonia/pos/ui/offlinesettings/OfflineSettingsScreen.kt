package com.amaxonia.pos.ui.offlinesettings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.amaxonia.pos.domain.repository.Department
import com.amaxonia.pos.domain.repository.OfflineSettingsStatus
import com.amaxonia.pos.domain.model.offline.OfflineCatalogEntry
import com.amaxonia.pos.domain.repository.OfflineSettingsUiModel

/**
 * "Ajustes Offline" (ADR-008): define qué parte del catálogo se sincroniza
 * para trabajar sin red — productos por departamento y clientes por
 * sucursal propietaria.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OfflineSettingsScreen(
    viewModel: OfflineSettingsViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.start()
        viewModel.refreshPreview()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Ajustes Offline", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver", tint = MaterialTheme.colorScheme.primary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            state.message?.let { message ->
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Text(
                        message,
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                        Text(
                            "Habilitar Catálogo Offline",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            if (state.syncEnabled) "El catálogo seleccionado se descargará para trabajar sin red."
                            else "Deshabilitado. El terminal consultará en línea para ahorrar recursos.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Switch(
                        checked = state.syncEnabled,
                        onCheckedChange = viewModel::setSyncEnabled,
                    )
                }
            }

            if (state.syncEnabled) {
                ScopeSection(
                    title = "Productos",
                    subtitle = "Qué productos estarán disponibles sin red",
                    modeAll = state.productModeAll,
                    onModeAllChange = viewModel::setProductModeAll,
                    preview = state.productPreview,
                    itemCount = state.departments.size,
                ) {
                    LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 220.dp)) {
                        items(state.departments, key = { it.id }) { department ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(
                                    checked = department.id in state.selectedDepartmentIds,
                                    onCheckedChange = { viewModel.toggleDepartment(department.id) },
                                )
                                Text(department.name, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }

                ScopeSection(
                    title = "Clientes",
                    subtitle = "Qué clientes estarán disponibles sin red",
                    modeAll = state.clientModeAll,
                    onModeAllChange = viewModel::setClientModeAll,
                    preview = state.clientPreview,
                    itemCount = state.sucursales.size,
                ) {
                    LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 220.dp)) {
                        items(state.sucursales, key = { it.id }) { sucursal ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(
                                    checked = sucursal.id in state.selectedSucursalIds,
                                    onCheckedChange = { viewModel.toggleSucursal(sucursal.id) },
                                )
                                Text(sucursal.nombre ?: sucursal.id, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }

            Button(
                onClick = viewModel::apply,
                enabled = state.status != OfflineSettingsStatus.APPLYING,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (state.status == OfflineSettingsStatus.APPLYING) {
                    CircularProgressIndicator(modifier = Modifier.padding(2.dp))
                } else {
                    Text(if (state.syncEnabled) "Aplicar y sincronizar" else "Guardar ajustes")
                }
            }
        }
    }
}

@Composable
private fun ScopeSection(
    title: String,
    subtitle: String,
    modeAll: Boolean,
    onModeAllChange: (Boolean) -> Unit,
    preview: Long?,
    itemCount: Int,
    content: @Composable () -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Text(subtitle, style = MaterialTheme.typography.bodySmall)
                }
                Switch(checked = modeAll, onCheckedChange = onModeAllChange)
            }
            Text(
                if (modeAll) "Se sincronizarán TODOS (recomendado si el catálogo es pequeño)." else "Solo lo seleccionado:",
                style = MaterialTheme.typography.bodySmall,
            )
            if (!modeAll) {
                if (itemCount == 0) {
                    Text("Sin elementos disponibles (conéctate para cargarlos).", style = MaterialTheme.typography.bodySmall)
                } else {
                    content()
                }
                preview?.let { count ->
                    Text("≈ $count elementos · se re-sincronizarán al aplicar", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
