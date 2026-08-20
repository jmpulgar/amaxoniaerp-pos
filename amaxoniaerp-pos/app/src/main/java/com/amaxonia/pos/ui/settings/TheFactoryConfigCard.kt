package com.amaxonia.pos.ui.settings

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import com.amaxonia.pos.domain.model.printer.GatewayOption
import com.amaxonia.pos.domain.model.printer.TheFactorySettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Flags de progreso de las acciones de configuración HKA. */
private class TheFactoryProgressState {
    var isSaving: Boolean by mutableStateOf(false)
    var isTestingConnection: Boolean by mutableStateOf(false)
    var isCheckingStatus: Boolean by mutableStateOf(false)
}

@Composable
private fun rememberTheFactoryProgressState(): TheFactoryProgressState = remember { TheFactoryProgressState() }

/** Tarjeta de configuración de The Factory HKA (IP/puerto/serial, pasarela y verificaciones). */
@Composable
internal fun TheFactoryConfigCard(
    viewModel: SettingsViewModel,
    scope: CoroutineScope,
    snackbarHostState: SnackbarHostState,
) {
    val settings by viewModel.theFactorySettings.collectAsStateWithLifecycle()
    val gatewayOptions by viewModel.gatewayOptions.collectAsStateWithLifecycle()
    val isLoadingGateways by viewModel.isLoadingGateways.collectAsStateWithLifecycle()
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
            SettingsSectionHeader(
                title = "Configuracion HKA POS",
                subtitle =
                    "Guarda la IP y el puerto que usa la app fiscal de The Factory " +
                        "para evitar que quede en espera al abrir.",
                bottomPadding = 16,
            )
            Text(
                text = "Modelo operativo para cobro + impresion: HKA20",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 12.dp),
            )

            TheFactoryConnectionFields(settings = settings, viewModel = viewModel)

            GatewaySelectionSection(
                gatewayKey = settings.gatewayKey,
                gatewayOptions = gatewayOptions,
                isLoadingGateways = isLoadingGateways,
                onRefresh = { loadGateways(scope, viewModel, snackbarHostState) },
                onGatewaySelected = viewModel::onGatewaySelected,
            )

            SaveHkaConfigButton(
                isSaving = progress.isSaving,
                onSave = { saveConfig(scope, viewModel, progress) },
            )

            Spacer(modifier = Modifier.height(12.dp))

            HkaVerifyButtons(
                isTestingConnection = progress.isTestingConnection,
                isCheckingStatus = progress.isCheckingStatus,
                onTestConnection = { testConnection(scope, viewModel, snackbarHostState, progress) },
                onCheckStatus = { checkStatus(scope, viewModel, snackbarHostState, progress) },
            )
        }
    }
}

/** Guarda IP/puerto y consulta las pasarelas disponibles. */
private fun loadGateways(
    scope: CoroutineScope,
    viewModel: SettingsViewModel,
    snackbarHostState: SnackbarHostState,
) {
    scope.launch {
        val saveResult = viewModel.persistTheFactorySettings(requireGatewaySelection = false)
        if (saveResult.isSuccess) {
            viewModel.loadGatewayOptions().onFailure { error ->
                snackbarHostState.showSnackbar(
                    error.message ?: "No se pudo consultar pasarelas",
                    duration = SnackbarDuration.Long,
                )
            }
        }
    }
}

/** Guarda la configuración HKA mostrando el mensaje de éxito. */
private fun saveConfig(
    scope: CoroutineScope,
    viewModel: SettingsViewModel,
    progress: TheFactoryProgressState,
) {
    progress.isSaving = true
    scope.launch {
        viewModel.persistTheFactorySettings(showSuccessMessage = true)
        progress.isSaving = false
    }
}

/** Guarda la configuración y prueba la conectividad TCP con HKA. */
private fun testConnection(
    scope: CoroutineScope,
    viewModel: SettingsViewModel,
    snackbarHostState: SnackbarHostState,
    progress: TheFactoryProgressState,
) {
    progress.isTestingConnection = true
    scope.launch {
        viewModel.persistTheFactorySettings(requireGatewaySelection = false)
        val msg = viewModel.testHkaConnection()
        snackbarHostState.showSnackbar(msg, duration = SnackbarDuration.Short)
        progress.isTestingConnection = false
    }
}

/** Guarda la configuración y consulta el estado fiscal de la impresora. */
private fun checkStatus(
    scope: CoroutineScope,
    viewModel: SettingsViewModel,
    snackbarHostState: SnackbarHostState,
    progress: TheFactoryProgressState,
) {
    progress.isCheckingStatus = true
    scope.launch {
        viewModel.persistTheFactorySettings(requireGatewaySelection = false)
        val msg = viewModel.checkHkaPrinterStatus()
        snackbarHostState.showSnackbar(msg, duration = SnackbarDuration.Long)
        progress.isCheckingStatus = false
    }
}

/** Campos de conexión de la app fiscal (IP, puerto y serial). */
@Composable
private fun TheFactoryConnectionFields(
    settings: TheFactorySettings,
    viewModel: SettingsViewModel,
) {
    OutlinedTextField(
        value = settings.ipAddress,
        onValueChange = viewModel::onTheFactoryIpChanged,
        modifier = Modifier.fillMaxWidth(),
        label = { Text("IP de HKA POS") },
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
        colors = theFactoryFieldColors(),
    )

    Spacer(modifier = Modifier.height(12.dp))

    OutlinedTextField(
        value = settings.port,
        onValueChange = viewModel::onTheFactoryPortChanged,
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Puerto") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        shape = RoundedCornerShape(12.dp),
        colors = theFactoryFieldColors(),
    )

    Spacer(modifier = Modifier.height(16.dp))

    OutlinedTextField(
        value = settings.printerSerial,
        onValueChange = viewModel::onTheFactorySerialChanged,
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Serial fiscal (iI*)") },
        placeholder = { Text("Ej: Z1B9999999") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
        shape = RoundedCornerShape(12.dp),
        colors = theFactoryFieldColors(),
    )

    Spacer(modifier = Modifier.height(6.dp))
    Text(
        text = "Opcional recomendado: si lo defines aqui, se usa para notas de credito fiscales.",
        fontSize = 12.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun theFactoryFieldColors() =
    OutlinedTextFieldDefaults.colors(
        focusedBorderColor = MaterialTheme.colorScheme.primary,
        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
    )

/** Selección de la pasarela de pago HKA20. */
@Composable
private fun GatewaySelectionSection(
    gatewayKey: String,
    gatewayOptions: List<GatewayOption>,
    isLoadingGateways: Boolean,
    onRefresh: () -> Unit,
    onGatewaySelected: (GatewayOption) -> Unit,
) {
    Spacer(modifier = Modifier.height(16.dp))

    Text(
        text = "Pasarela de pago (HKA20)",
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
        color = MaterialTheme.colorScheme.onSurface,
    )
    Text(
        text = "Selecciona aquí la pasarela que se usará al cobrar desde POS.",
        fontSize = 12.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 2.dp, bottom = 10.dp),
    )
    LoadGatewaysButton(isLoadingGateways = isLoadingGateways, onLoadGateways = onRefresh)
    Spacer(modifier = Modifier.height(10.dp))
    if (gatewayOptions.isEmpty()) {
        Text(
            text = "No hay pasarelas cargadas. Pulsa \"Consultar pasarelas disponibles\".",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    } else {
        GatewayOptionList(
            gatewayKey = gatewayKey,
            gatewayOptions = gatewayOptions,
            onGatewaySelected = onGatewaySelected,
        )
    }
}

/** Botón que consulta las pasarelas disponibles. */
@Composable
private fun LoadGatewaysButton(
    isLoadingGateways: Boolean,
    onLoadGateways: () -> Unit,
) {
    Button(
        onClick = onLoadGateways,
        enabled = !isLoadingGateways,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
    ) {
        if (isLoadingGateways) {
            CircularProgressIndicator(
                color = MaterialTheme.colorScheme.onSecondary,
                strokeWidth = 2.dp,
                modifier = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Consultando...")
        } else {
            Text("Consultar pasarelas disponibles")
        }
    }
}

/** Lista de pasarelas seleccionables. */
@Composable
private fun GatewayOptionList(
    gatewayKey: String,
    gatewayOptions: List<GatewayOption>,
    onGatewaySelected: (GatewayOption) -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        gatewayOptions.forEach { option ->
            OutlinedButton(
                onClick = { onGatewaySelected(option) },
                modifier = Modifier.fillMaxWidth(),
                border =
                    BorderStroke(
                        1.dp,
                        if (gatewayKey == option.key) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outline
                        },
                    ),
            ) {
                Text(
                    text = "${option.label} (${option.key})",
                    color =
                        if (gatewayKey == option.key) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                )
            }
        }
    }
}

/** Botón de guardado de la configuración HKA. */
@Composable
private fun SaveHkaConfigButton(
    isSaving: Boolean,
    onSave: () -> Unit,
) {
    Spacer(modifier = Modifier.height(16.dp))

    Button(
        onClick = onSave,
        enabled = !isSaving,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
    ) {
        if (isSaving) {
            CircularProgressIndicator(
                color = MaterialTheme.colorScheme.onPrimary,
                strokeWidth = 2.dp,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(10.dp))
        }
        Text(if (isSaving) "Guardando..." else "Guardar configuracion HKA")
    }
}

/** Botones de verificación de conexión y estado fiscal. */
@Composable
private fun HkaVerifyButtons(
    isTestingConnection: Boolean,
    isCheckingStatus: Boolean,
    onTestConnection: () -> Unit,
    onCheckStatus: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Verificar Conexion
        TestConnectionButton(
            isTesting = isTestingConnection,
            onClick = onTestConnection,
            modifier = Modifier.weight(1f),
        )

        // Verificar Estado
        CheckStatusButton(
            isChecking = isCheckingStatus,
            onClick = onCheckStatus,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun TestConnectionButton(
    isTesting: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        enabled = !isTesting,
        modifier = modifier.height(44.dp),
        shape = RoundedCornerShape(12.dp),
        colors =
            ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.tertiary,
                disabledContainerColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.5f),
            ),
    ) {
        if (isTesting) {
            CircularProgressIndicator(
                color = MaterialTheme.colorScheme.onTertiary,
                strokeWidth = 2.dp,
                modifier = Modifier.size(16.dp),
            )
        } else {
            Icon(
                Icons.Rounded.Wifi,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text("Conexion", fontSize = 13.sp)
        }
    }
}

@Composable
private fun CheckStatusButton(
    isChecking: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        enabled = !isChecking,
        modifier = modifier.height(44.dp),
        shape = RoundedCornerShape(12.dp),
        colors =
            ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.secondary,
                disabledContainerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f),
            ),
    ) {
        if (isChecking) {
            CircularProgressIndicator(
                color = MaterialTheme.colorScheme.onSecondary,
                strokeWidth = 2.dp,
                modifier = Modifier.size(16.dp),
            )
        } else {
            Icon(
                Icons.Rounded.Info,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text("Estado", fontSize = 13.sp)
        }
    }
}
