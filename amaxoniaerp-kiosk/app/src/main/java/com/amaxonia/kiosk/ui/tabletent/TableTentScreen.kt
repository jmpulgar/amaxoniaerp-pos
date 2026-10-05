package com.amaxonia.kiosk.ui.tabletent

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TableTentScreen(
    viewModel: TableTentViewModel,
    onConfirmed: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopAppBar(
                title = {
                    Text(
                        text = "Identificador de Mesa",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Regresar",
                        )
                    }
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
            )

            Column(
                modifier =
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 48.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                // Header Info
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Introduce el número de tu portamesa",
                        style =
                            MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.ExtraBold,
                                textAlign = TextAlign.Center,
                            ),
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Toma un identificador ubicado al lado del kiosco para llevar tu comida a la mesa",
                        style =
                            MaterialTheme.typography.bodyLarge.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                            ),
                    )

                    Spacer(modifier = Modifier.height(28.dp))

                    // Big Number Display
                    Box(
                        modifier =
                            Modifier
                                .size(width = 240.dp, height = 100.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                .border(
                                    width = 2.dp,
                                    color =
                                        if (uiState.isValid) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.outlineVariant
                                        },
                                    shape = RoundedCornerShape(20.dp),
                                ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = uiState.tentNumber.ifEmpty { "---" },
                            style =
                                MaterialTheme.typography.displayMedium.copy(
                                    fontWeight = FontWeight.Black,
                                    color =
                                        if (uiState.isValid) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                        },
                                    letterSpacing = 4.dp.value.spValueToEm(),
                                ),
                        )
                    }
                }

                // Numeric Keypad (3x4)
                NumericKeypad(
                    onDigit = viewModel::onDigit,
                    onBackspace = viewModel::onBackspace,
                    onClear = viewModel::onClear,
                )

                // Actions
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Button(
                        onClick = { viewModel.confirm(onConfirmed) },
                        enabled = uiState.isValid,
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    ) {
                        Text(
                            text = if (uiState.isValid) "Confirmar Mesa #${uiState.tentNumber}" else "Confirmar Portamesa",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        )
                    }

                    OutlinedButton(
                        onClick = { viewModel.skip(onConfirmed) },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Text(
                            text = "No tengo portamesa / Retirar en Mostrador",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NumericKeypad(
    onDigit: (Char) -> Unit,
    onBackspace: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val rows =
        listOf(
            listOf('1', '2', '3'),
            listOf('4', '5', '6'),
            listOf('7', '8', '9'),
        )

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                row.forEach { digit ->
                    KeypadButton(
                        text = digit.toString(),
                        onClick = { onDigit(digit) },
                    )
                }
            }
        }

        // Bottom row: Clear, 0, Backspace
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            KeypadButton(
                text = "C",
                textColor = MaterialTheme.colorScheme.error,
                onClick = onClear,
            )
            KeypadButton(
                text = "0",
                onClick = { onDigit('0') },
            )
            KeypadIconButton(
                icon = Icons.AutoMirrored.Filled.Backspace,
                onClick = onBackspace,
            )
        }
    }
}

@Composable
private fun KeypadButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    textColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    Box(
        modifier =
            modifier
                .size(76.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style =
                MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = textColor,
                ),
        )
    }
}

@Composable
private fun KeypadIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .size(76.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = "Borrar",
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(28.dp),
        )
    }
}

private fun Float.spValueToEm(): androidx.compose.ui.unit.TextUnit {
    return androidx.compose.ui.unit.TextUnit(this, androidx.compose.ui.unit.TextUnitType.Sp)
}
