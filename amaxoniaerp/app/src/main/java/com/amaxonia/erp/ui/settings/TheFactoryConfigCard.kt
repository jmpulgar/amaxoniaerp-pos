package com.amaxonia.erp.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.amaxonia.erp.domain.model.printer.TheFactorySettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

private class TheFactoryProgressState {
    var isSaving: Boolean by mutableStateOf(false)
    var isTestingConnection: Boolean by mutableStateOf(false)
    var isCheckingStatus: Boolean by mutableStateOf(false)
}

@Composable
private fun rememberTheFactoryProgressState(): TheFactoryProgressState = remember { TheFactoryProgressState() }

@Composable
internal fun TheFactoryConfigCard(
    viewModel: SettingsViewModel,
    scope: CoroutineScope,
    snackbarHostState: SnackbarHostState,
) {
    val settings by viewModel.theFactorySettings.collectAsStateWithLifecycle()
    val progress = rememberTheFactoryProgressState()

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
        ) {
            Text(
                text = "Configuración Impresora Fiscal The Factory HKA",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "Ingresa la IP y puerto de red de la impresora fiscal (SENIAT)",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
            )

            OutlinedTextField(
                value = settings.ipAddress,
                onValueChange = viewModel::onTheFactoryIpChanged,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Dirección IP (ej. 192.168.1.150)") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = settings.port,
                onValueChange = viewModel::onTheFactoryPortChanged,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Puerto TCP (ej. 1111)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                shape = RoundedCornerShape(12.dp),
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = settings.printerSerial,
                onValueChange = viewModel::onTheFactorySerialChanged,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Serial de la Impresora (ej. HKA0000001)") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    progress.isSaving = true
                    scope.launch {
                        viewModel.persistTheFactorySettings(showSuccessMessage = true)
                        progress.isSaving = false
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                enabled = !progress.isSaving,
            ) {
                if (progress.isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Text("Guardar Parámetros HKA")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = {
                        progress.isTestingConnection = true
                        scope.launch {
                            viewModel.persistTheFactorySettings(requireGatewaySelection = false)
                            val msg = viewModel.testHkaConnection()
                            snackbarHostState.showSnackbar(msg, duration = SnackbarDuration.Short)
                            progress.isTestingConnection = false
                        }
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    enabled = !progress.isTestingConnection,
                ) {
                    if (progress.isTestingConnection) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Probar TCP", fontSize = 12.sp)
                    }
                }

                OutlinedButton(
                    onClick = {
                        progress.isCheckingStatus = true
                        scope.launch {
                            viewModel.persistTheFactorySettings(requireGatewaySelection = false)
                            val msg = viewModel.checkHkaPrinterStatus()
                            snackbarHostState.showSnackbar(msg, duration = SnackbarDuration.Long)
                            progress.isCheckingStatus = false
                        }
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    enabled = !progress.isCheckingStatus,
                ) {
                    if (progress.isCheckingStatus) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Estado Fiscal", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}
