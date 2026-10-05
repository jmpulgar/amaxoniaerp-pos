package com.amaxonia.kiosk.ui.pairing

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.collectLatest

@Composable
fun PairingScreen(
    viewModel: PairingViewModel,
    onPairingSuccess: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current

    LaunchedEffect(Unit) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is PairingEvent.Success -> onPairingSuccess()
            }
        }
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier =
                    Modifier
                        .width(620.dp)
                        .padding(horizontal = 32.dp, vertical = 48.dp)
                        .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                // Header
                Text(
                    text = "AMAXONIA",
                    style =
                        MaterialTheme.typography.labelLarge.copy(
                            letterSpacing = 4.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        ),
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Emparejamiento de Kiosco",
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Ingrese los datos de conexión y el código generado en el panel administrativo para registrar este dispositivo.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(modifier = Modifier.height(36.dp))

                // Error Card
                if (uiState.errorMessage != null) {
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
                        colors =
                            CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                            ),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Default.ErrorOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = uiState.errorMessage.orEmpty(),
                                style =
                                    MaterialTheme.typography.bodyMedium.copy(
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                    ),
                            )
                        }
                    }
                }

                // Country Selector
                Text(
                    text = "País",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    CountryOption(
                        label = "Panamá (PA)",
                        selected = uiState.countryCode == "PA",
                        onClick = { viewModel.onCountryCodeChanged("PA") },
                        modifier = Modifier.weight(1f),
                    )
                    CountryOption(
                        label = "Venezuela (VE)",
                        selected = uiState.countryCode == "VE",
                        onClick = { viewModel.onCountryCodeChanged("VE") },
                        modifier = Modifier.weight(1f),
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Server URL
                OutlinedTextField(
                    value = uiState.serverUrl,
                    onValueChange = viewModel::onServerUrlChanged,
                    label = { Text("URL del Servidor") },
                    leadingIcon = {
                        Icon(Icons.Default.Dns, contentDescription = null)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions =
                        KeyboardOptions(
                            imeAction = ImeAction.Next,
                        ),
                    shape = RoundedCornerShape(12.dp),
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Company DB
                OutlinedTextField(
                    value = uiState.companyDb,
                    onValueChange = viewModel::onCompanyDbChanged,
                    label = { Text("Base de Datos de Empresa") },
                    leadingIcon = {
                        Icon(Icons.Default.Storage, contentDescription = null)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions =
                        KeyboardOptions(
                            imeAction = ImeAction.Next,
                        ),
                    shape = RoundedCornerShape(12.dp),
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Pairing Code
                OutlinedTextField(
                    value = uiState.pairingCode,
                    onValueChange = viewModel::onPairingCodeChanged,
                    label = { Text("Código de Emparejamiento (8 caracteres)") },
                    leadingIcon = {
                        Icon(Icons.Default.Key, contentDescription = null)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    textStyle =
                        MaterialTheme.typography.titleLarge.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 4.sp,
                            textAlign = TextAlign.Center,
                        ),
                    keyboardOptions =
                        KeyboardOptions(
                            capitalization = KeyboardCapitalization.Characters,
                            imeAction = ImeAction.Done,
                        ),
                    keyboardActions =
                        KeyboardActions(
                            onDone = {
                                focusManager.clearFocus()
                                viewModel.submitPairing()
                            },
                        ),
                    colors =
                        OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                        ),
                    shape = RoundedCornerShape(12.dp),
                )

                Spacer(modifier = Modifier.height(32.dp))

                // Submit Button
                Button(
                    onClick = {
                        focusManager.clearFocus()
                        viewModel.submitPairing()
                    },
                    enabled = !uiState.isLoading,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    if (uiState.isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Text(
                            text = "Emparejar Dispositivo",
                            style =
                                MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                ),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CountryOption(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val borderColor =
        if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    val backgroundColor =
        if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface

    Box(
        modifier =
            modifier
                .height(52.dp)
                .clip(RoundedCornerShape(12.dp))
                .border(width = if (selected) 2.dp else 1.dp, color = borderColor, shape = RoundedCornerShape(12.dp))
                .background(backgroundColor)
                .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = Icons.Default.Public,
                contentDescription = null,
                tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = label,
                style =
                    MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                    ),
            )
        }
    }
}
