package com.amaxonia.erp.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Print
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amaxonia.erp.domain.model.printer.PrinterType
import com.amaxonia.erp.ui.theme.SuccessGreen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

private val STATUS_PILL_SHAPE = RoundedCornerShape(50)

@Composable
internal fun CurrentPrinterRow(selectedPrinterType: PrinterType) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.Print,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp),
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Impresora Seleccionada",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = selectedPrinterType.displayName,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        PrinterStatusPill(selectedPrinterType = selectedPrinterType)
    }
}

@Composable
private fun PrinterStatusPill(selectedPrinterType: PrinterType) {
    val isConfigured = selectedPrinterType != PrinterType.NONE
    val pillColor =
        if (isConfigured) {
            SuccessGreen.copy(alpha = 0.12f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        }
    val pillTextColor =
        if (isConfigured) {
            SuccessGreen
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        }
    Box(
        modifier =
            Modifier
                .clip(STATUS_PILL_SHAPE)
                .background(pillColor)
                .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(
            text = if (isConfigured) "Configurada" else "Sin impresora",
            color = pillTextColor,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
internal fun PrintTestSection(
    selectedPrinterType: PrinterType,
    viewModel: SettingsViewModel,
    scope: CoroutineScope,
    snackbarHostState: SnackbarHostState,
) {
    var isPrintingTest by remember { mutableStateOf(false) }

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
            CurrentPrinterRow(selectedPrinterType = selectedPrinterType)

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    if (selectedPrinterType == PrinterType.NONE) {
                        scope.launch {
                            snackbarHostState.showSnackbar("Selecciona un tipo de impresora primero", duration = SnackbarDuration.Short)
                        }
                        return@Button
                    }
                    isPrintingTest = true
                    scope.launch {
                        val result = viewModel.printTestReceipt()
                        snackbarHostState.showSnackbar(result, duration = SnackbarDuration.Short)
                        isPrintingTest = false
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                enabled = !isPrintingTest && selectedPrinterType != PrinterType.NONE,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            ) {
                if (isPrintingTest) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Icon(
                        Icons.Rounded.Print,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Imprimir Ticket de Prueba")
                }
            }
        }
    }
}
