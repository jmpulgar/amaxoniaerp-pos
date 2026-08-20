package com.amaxonia.pos.ui.settings

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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amaxonia.pos.composition.AppGraph
import com.amaxonia.pos.domain.model.Transaction
import com.amaxonia.pos.domain.model.TransactionStatus
import com.amaxonia.pos.domain.model.printer.PrintResult
import com.amaxonia.pos.domain.model.printer.PrinterType
import com.amaxonia.pos.ui.theme.SuccessGreen

/** Forma de píldora del indicador de estado de impresora configurada. */
private val STATUS_PILL_SHAPE = RoundedCornerShape(50)

/** Fila con la impresora activa y su indicador de estado. */
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
                text = "Impresora Actual",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = selectedPrinterType.displayName,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        // Status indicator
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
                .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(
            text = if (isConfigured) "Configurada" else "No activa",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = pillTextColor,
        )
    }
}

/** Botón que dispara la prueba de impresión. */
@Composable
internal fun TestPrintButton(
    isTestingPrint: Boolean,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        enabled = !isTestingPrint,
        modifier =
            Modifier
                .fillMaxWidth()
                .height(50.dp),
        shape = RoundedCornerShape(14.dp),
        colors =
            ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
            ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp),
    ) {
        if (isTestingPrint) {
            CircularProgressIndicator(
                color = MaterialTheme.colorScheme.onPrimary,
                strokeWidth = 2.dp,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                "Enviando prueba...",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
            )
        } else {
            Icon(
                Icons.Rounded.Print,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                "Probar Impresion",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
            )
        }
    }
}

/** Ejecuta la prueba de impresión según el tipo seleccionado (flujo original preservado). */
internal suspend fun runPrintTest(
    printerType: PrinterType,
    brandPrintTestMessage: String,
    viewModel: SettingsViewModel,
    snackbarHostState: SnackbarHostState,
) {
    if (printerType == PrinterType.THE_FACTORY_HKA) {
        val saveResult = viewModel.persistTheFactorySettings(requireGatewaySelection = false)
        if (saveResult.isFailure) return
    }
    if (printerType == PrinterType.SUNMI_V2) {
        printSunmiTestTicket(brandPrintTestMessage, snackbarHostState)
    } else {
        printGenericTestReceipt(snackbarHostState)
    }
}

/** Imprime el mensaje de prueba por la impresora de ticket SUNMI activa. */
private suspend fun printSunmiTestTicket(
    brandPrintTestMessage: String,
    snackbarHostState: SnackbarHostState,
) {
    val ticketPrinter = AppGraph.settings.activeTicketPrinter()
    val result = ticketPrinter?.printText(brandPrintTestMessage)
    if (result is PrintResult.Success) {
        snackbarHostState.showSnackbar(
            "Impresion de prueba enviada correctamente",
            duration = SnackbarDuration.Short,
        )
    } else {
        snackbarHostState.showSnackbar(
            (result as? PrintResult.Error)?.message
                ?: "Impresora SUNMI no disponible",
            duration = SnackbarDuration.Long,
        )
    }
}

/** Imprime un recibo de prueba por la impresora activa (genérica o fiscal). */
private suspend fun printGenericTestReceipt(snackbarHostState: SnackbarHostState) {
    val printer = AppGraph.settings.activePrinter()
    if (printer == null) {
        snackbarHostState.showSnackbar(
            "Impresora no disponible. Verifica la conexion.",
            duration = SnackbarDuration.Short,
        )
        return
    }
    val testTransaction =
        Transaction(
            id = "test-print",
            invoiceNumber = "TEST-001",
            time = "Ahora",
            amount = 0.01,
            dateHeader = "Prueba",
            status = TransactionStatus.PAID,
        )
    printer.printReceipt(testTransaction).fold(
        onSuccess = {
            snackbarHostState.showSnackbar(
                "Impresion de prueba enviada correctamente",
                duration = SnackbarDuration.Short,
            )
        },
        onFailure = { e ->
            snackbarHostState.showSnackbar(
                "Error: ${e.message}",
                duration = SnackbarDuration.Long,
            )
        },
    )
}
