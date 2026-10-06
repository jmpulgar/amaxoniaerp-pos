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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Storage
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.amaxonia.kiosk.R
import com.amaxonia.kiosk.ui.components.BrandWordmark
import com.amaxonia.kiosk.ui.components.KioskButton
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
                        .widthIn(max = 840.dp)
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp, vertical = 48.dp)
                        .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                // Header
                BrandWordmark(height = 88.dp)
                Spacer(modifier = Modifier.height(32.dp))
                Text(
                    text = stringResource(R.string.pairing_title),
                    style = MaterialTheme.typography.displaySmall,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.pairing_message),
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
                        shape = MaterialTheme.shapes.small,
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.ErrorOutline,
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
                    text = stringResource(R.string.pairing_country),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    CountryOption(
                        label = stringResource(R.string.pairing_country_pa),
                        selected = uiState.countryCode == "PA",
                        onClick = { viewModel.onCountryCodeChanged("PA") },
                        modifier = Modifier.weight(1f),
                    )
                    CountryOption(
                        label = stringResource(R.string.pairing_country_ve),
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
                    label = { Text(stringResource(R.string.pairing_server_url)) },
                    textStyle = MaterialTheme.typography.bodyLarge,
                    leadingIcon = {
                        Icon(Icons.Rounded.Dns, contentDescription = null)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions =
                        KeyboardOptions(
                            imeAction = ImeAction.Next,
                        ),
                    shape = MaterialTheme.shapes.small,
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Company DB
                OutlinedTextField(
                    value = uiState.companyDb,
                    onValueChange = viewModel::onCompanyDbChanged,
                    label = { Text(stringResource(R.string.pairing_company_db)) },
                    textStyle = MaterialTheme.typography.bodyLarge,
                    leadingIcon = {
                        Icon(Icons.Rounded.Storage, contentDescription = null)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions =
                        KeyboardOptions(
                            imeAction = ImeAction.Next,
                        ),
                    shape = MaterialTheme.shapes.small,
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Pairing Code
                OutlinedTextField(
                    value = uiState.pairingCode,
                    onValueChange = viewModel::onPairingCodeChanged,
                    label = { Text(stringResource(R.string.pairing_code)) },
                    leadingIcon = {
                        Icon(Icons.Rounded.Key, contentDescription = null)
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
                    shape = MaterialTheme.shapes.small,
                )

                Spacer(modifier = Modifier.height(40.dp))

                if (uiState.isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(88.dp), strokeWidth = 8.dp)
                } else {
                    KioskButton(
                        text = stringResource(R.string.pairing_submit),
                        onClick = {
                            focusManager.clearFocus()
                            viewModel.submitPairing()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
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
                .height(88.dp)
                .clip(MaterialTheme.shapes.small)
                .border(width = if (selected) 2.dp else 1.dp, color = borderColor, shape = MaterialTheme.shapes.small)
                .background(backgroundColor)
                .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.Public,
                contentDescription = null,
                tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = label,
                style =
                    MaterialTheme.typography.titleSmall.copy(
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                    ),
            )
        }
    }
}
