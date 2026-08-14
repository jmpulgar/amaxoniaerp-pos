package com.amaxonia.pos.ui.caja

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Assignment
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.AttachMoney
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PointOfSale
import androidx.compose.material.icons.rounded.Receipt
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.ShoppingCart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.amaxonia.pos.domain.model.caja.CierreCajaPaymentLine
import com.amaxonia.pos.domain.model.caja.CierreCajaSummary
import com.amaxonia.pos.ui.common.DependencyContainer
import com.amaxonia.pos.ui.common.components.AdaptiveAmountText
import com.amaxonia.pos.ui.common.injectedViewModel
import com.amaxonia.pos.ui.theme.AccentPurple
import com.amaxonia.pos.ui.theme.PosPalette
import com.amaxonia.pos.ui.theme.SuccessGreen
import com.amaxonia.pos.ui.theme.WarningOrange

/** Acciones del cierre agrupadas para mantener las firmas de los composables pequeñas. */
internal class CierreCajaActions(
    val onConfirmClose: () -> Unit,
    val onPrintReportX: () -> Unit,
    val onPrintReportZ: () -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
// Raíz Compose conserva alcance de diálogo, snackbar y transición de estados.
@Suppress("LongMethod")
fun CierreCajaScreen(
    viewModel: CierreCajaViewModel =
        injectedViewModel {
            CierreCajaViewModel(
                DependencyContainer.cajaRepository,
                DependencyContainer.cashClosePrintingService,
                DependencyContainer.cashCloseTicketPayloadBuilder,
            )
        },
    onBack: () -> Unit,
    onCloseSuccess: () -> Unit,
    onOpenNewCaja: () -> Unit = onCloseSuccess,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isPrintingX by viewModel.isPrintingReportX.collectAsStateWithLifecycle()
    val isPrintingZ by viewModel.isPrintingReportZ.collectAsStateWithLifecycle()
    val reportMessage by viewModel.reportMessage.collectAsStateWithLifecycle()
    val showCloseTicketPrompt by viewModel.showCloseTicketPrompt.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(reportMessage) {
        reportMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearReportMessage()
        }
    }

    if (showCloseTicketPrompt) {
        AlertDialog(
            onDismissRequest = viewModel::dismissCloseTicketPrompt,
            title = { Text("Imprimir cierre de caja") },
            text = { Text("¿Deseas imprimir el ticket con el resumen del cierre de caja?") },
            confirmButton = {
                TextButton(onClick = { viewModel.confirmClose(printTicket = true) }) {
                    Text("Imprimir")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.confirmClose(printTicket = false) }) {
                    Text("No imprimir")
                }
            },
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Cierre de Caja",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Volver",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        AnimatedContent(
            targetState = uiState,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "cierreCajaTransition",
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding),
        ) { state ->
            when (state) {
                is CierreCajaUiState.Loading -> LoadingContent()
                is CierreCajaUiState.Ready ->
                    ReadyContent(
                        summary = state.summary,
                        isClosing = false,
                        isPrintingReportX = isPrintingX,
                        isPrintingReportZ = isPrintingZ,
                        showReportButtons = viewModel.hasActivePrinter,
                        actions =
                            CierreCajaActions(
                                onConfirmClose = { viewModel.requestClose() },
                                onPrintReportX = { viewModel.printReportX() },
                                onPrintReportZ = { viewModel.printReportZ() },
                            ),
                    )
                is CierreCajaUiState.Closing ->
                    ReadyContent(
                        summary = state.summary,
                        isClosing = true,
                        isPrintingReportX = false,
                        isPrintingReportZ = false,
                        showReportButtons = viewModel.hasActivePrinter,
                        actions =
                            CierreCajaActions(
                                onConfirmClose = {},
                                onPrintReportX = {},
                                onPrintReportZ = {},
                            ),
                    )
                is CierreCajaUiState.Success ->
                    SuccessContent(
                        message = state.message,
                        onDone = onCloseSuccess,
                        onOpenNewCaja = onOpenNewCaja,
                    )
                is CierreCajaUiState.Error ->
                    ErrorContent(
                        message = state.message,
                        hasSummary = state.summary != null,
                        onRetry = { viewModel.loadSummary() },
                        onRetryClose = { viewModel.requestClose() },
                    )
            }
        }
    }
}

// ---------- Loading ----------

@Composable
private fun LoadingContent() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "Cargando resumen de caja...",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 14.sp,
            )
        }
    }
}

// ---------- Ready / Closing ----------

@Composable
internal fun ReadyContent(
    summary: CierreCajaSummary,
    isClosing: Boolean,
    isPrintingReportX: Boolean,
    isPrintingReportZ: Boolean,
    showReportButtons: Boolean,
    actions: CierreCajaActions,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
    ) {
        Spacer(modifier = Modifier.height(8.dp))
        CajaHeaderCard(summary = summary)
        Spacer(modifier = Modifier.height(20.dp))

        SectionTitle("Resumen de Ventas")
        Spacer(modifier = Modifier.height(12.dp))
        VentasSummaryCard(summary = summary)
        Spacer(modifier = Modifier.height(20.dp))

        SectionTitle("Desglose por Método de Pago")
        Spacer(modifier = Modifier.height(12.dp))
        PaymentBreakdownCard(summary = summary)
        Spacer(modifier = Modifier.height(20.dp))

        ExpectedCloseCard(summary = summary)
        Spacer(modifier = Modifier.height(32.dp))

        if (showReportButtons) {
            ReportButtons(
                isClosing = isClosing,
                isPrintingReportX = isPrintingReportX,
                isPrintingReportZ = isPrintingReportZ,
                onPrintReportX = actions.onPrintReportX,
                onPrintReportZ = actions.onPrintReportZ,
            )
            Spacer(modifier = Modifier.height(16.dp))
        }
        ConfirmCloseButton(isClosing = isClosing, onConfirmClose = actions.onConfirmClose)
    }
}

@Composable
private fun CajaHeaderCard(summary: CierreCajaSummary) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.primary),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 4.dp),
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier =
                        Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(PosPalette.FixedWhite.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Rounded.PointOfSale,
                        contentDescription = null,
                        tint = PosPalette.FixedWhite,
                        modifier = Modifier.size(24.dp),
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = summary.cajaName,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = PosPalette.FixedWhite,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 4.dp),
                    ) {
                        Icon(
                            Icons.Rounded.Schedule,
                            contentDescription = null,
                            tint = PosPalette.FixedWhite.copy(alpha = 0.7f),
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Abierta: ${summary.openedAt}",
                            fontSize = 12.sp,
                            color = PosPalette.FixedWhite.copy(alpha = 0.7f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
            HorizontalDivider(color = PosPalette.FixedWhite.copy(alpha = 0.2f))
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                MiniStat(
                    label = "Monto Apertura",
                    value = formatMoney(summary.openAmount),
                    color = PosPalette.FixedWhite,
                    modifier = Modifier.weight(1f),
                )
                MiniStat(
                    label = "Transacciones",
                    value = "${summary.transactionCount}",
                    color = PosPalette.FixedWhite,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun VentasSummaryCard(summary: CierreCajaSummary) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            SummaryRow(
                icon = Icons.Rounded.ShoppingCart,
                iconTint = MaterialTheme.colorScheme.primary,
                label = "Total Ventas",
                value = formatMoney(summary.totalSales),
                valueColor = MaterialTheme.colorScheme.primary,
                isBold = true,
            )
        }
    }
}

@Composable
private fun PaymentBreakdownCard(summary: CierreCajaSummary) {
    val lines = resolvePaymentLines(summary)
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            lines.forEachIndexed { index, line ->
                val (icon, tint) = paymentLineVisual(line.siglas)
                SummaryRow(
                    icon = icon,
                    iconTint = tint,
                    label = line.label,
                    value = formatMoney(line.amount),
                    valueColor = tint,
                )
                if (index < lines.lastIndex) {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 14.dp),
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                }
            }
        }
    }
}

/** Fallback legado cuando el resumen no trae líneas: las sintetiza de los totales agregados. */
private fun resolvePaymentLines(summary: CierreCajaSummary): List<CierreCajaPaymentLine> =
    summary.paymentLines.ifEmpty {
        listOf(
            CierreCajaPaymentLine(1, "Efectivo", "CASH", summary.totalCash),
            CierreCajaPaymentLine(2, "Tarjeta", "TARJETA", summary.totalCard),
            CierreCajaPaymentLine(3, "Otros", "OT", summary.totalOther),
        ).filter { it.amount > 0.0 }
    }

private fun paymentLineVisual(siglas: String): Pair<ImageVector, Color> =
    when (siglas.uppercase()) {
        "CASH", "EF", "EFE", "EFECTIVO" -> Icons.Rounded.AttachMoney to SuccessGreen
        "TDC", "TARJETA", "PV", "POS", "DB", "DEBITO", "CR", "CREDITO" -> Icons.Rounded.CreditCard to AccentPurple
        else -> Icons.Rounded.AccountBalance to WarningOrange
    }

@Composable
private fun ExpectedCloseCard(summary: CierreCajaSummary) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f, fill = false),
            ) {
                Icon(
                    Icons.Rounded.Receipt,
                    contentDescription = null,
                    tint = SuccessGreen,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    "Cierre Esperado",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            // El número más importante de la pantalla: adaptive para que importes
            // enormes jamás se recorten en 320dp.
            AdaptiveAmountText(
                text = formatMoney(summary.expectedClose),
                baseStyle =
                    MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.ExtraBold,
                        textAlign = androidx.compose.ui.text.style.TextAlign.End,
                    ),
                color = MaterialTheme.colorScheme.tertiary,
                minFontSizeSp = 14f,
                modifier = Modifier.weight(1f, fill = false).padding(start = 12.dp),
            )
        }
    }
}

@Composable
private fun ReportButtons(
    isClosing: Boolean,
    isPrintingReportX: Boolean,
    isPrintingReportZ: Boolean,
    onPrintReportX: () -> Unit,
    onPrintReportZ: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedButton(
            onClick = onPrintReportX,
            enabled = !isClosing && !isPrintingReportX && !isPrintingReportZ,
            modifier =
                Modifier
                    .weight(1f)
                    .height(52.dp),
            shape = RoundedCornerShape(14.dp),
            colors =
                ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.primary,
                    disabledContentColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                ),
        ) {
            if (isPrintingReportX) {
                CircularProgressIndicator(
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(18.dp),
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.width(8.dp))
            } else {
                Icon(
                    Icons.AutoMirrored.Rounded.Assignment,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                if (isPrintingReportX) "Imprimiendo..." else "Reporte X",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
            )
        }

        OutlinedButton(
            onClick = onPrintReportZ,
            enabled = !isClosing && !isPrintingReportX && !isPrintingReportZ,
            modifier =
                Modifier
                    .weight(1f)
                    .height(52.dp),
            shape = RoundedCornerShape(14.dp),
            colors =
                ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error,
                    disabledContentColor = MaterialTheme.colorScheme.error.copy(alpha = 0.4f),
                ),
        ) {
            if (isPrintingReportZ) {
                CircularProgressIndicator(
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(18.dp),
                    color = MaterialTheme.colorScheme.error,
                )
                Spacer(modifier = Modifier.width(8.dp))
            } else {
                Icon(
                    Icons.Rounded.Description,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                if (isPrintingReportZ) "Imprimiendo..." else "Reporte Z",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
            )
        }
    }
}

@Composable
private fun ConfirmCloseButton(
    isClosing: Boolean,
    onConfirmClose: () -> Unit,
) {
    Button(
        onClick = onConfirmClose,
        enabled = !isClosing,
        modifier =
            Modifier
                .fillMaxWidth()
                .height(56.dp),
        shape = RoundedCornerShape(16.dp),
        colors =
            ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
                disabledContainerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.5f),
            ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp),
    ) {
        if (isClosing) {
            CircularProgressIndicator(
                color = PosPalette.FixedWhite,
                strokeWidth = 2.dp,
                modifier = Modifier.size(22.dp),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                "Cerrando Caja...",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
            )
        } else {
            Icon(
                Icons.Rounded.Lock,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                "Confirmar Cierre de Caja",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
            )
        }
    }
}

// ---------- Success ----------

@Composable
// Composable cohesivo y lineal; extraer fragmentos no reduce estado ni riesgo.
@Suppress("LongMethod")
private fun SuccessContent(
    message: String,
    onDone: () -> Unit,
    onOpenNewCaja: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier =
                Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(SuccessGreen.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.CheckCircle,
                contentDescription = null,
                tint = SuccessGreen,
                modifier = Modifier.size(56.dp),
            )
        }
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            "Caja Cerrada Exitosamente",
            fontWeight = FontWeight.Bold,
            fontSize = 24.sp,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            message,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            "Acabaste de cerrar la caja, ¿deseas abrir una nueva?",
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = onOpenNewCaja,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen),
        ) {
            Icon(Icons.Rounded.PointOfSale, contentDescription = null, tint = PosPalette.FixedWhite)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Abrir caja ahora", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = PosPalette.FixedWhite)
        }
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedButton(
            onClick = onDone,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            shape = RoundedCornerShape(14.dp),
        ) {
            Text("Volver al Dashboard", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
    }
}

// ---------- Error ----------

@Composable
private fun ErrorContent(
    message: String,
    hasSummary: Boolean,
    onRetry: () -> Unit,
    onRetryClose: () -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        ElevatedCard(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
            shape = RoundedCornerShape(16.dp),
            colors =
                CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                ),
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    fontSize = 14.sp,
                )
                Spacer(modifier = Modifier.height(20.dp))
                if (hasSummary) {
                    TextButton(onClick = onRetryClose) {
                        Text("Reintentar Cierre")
                    }
                } else {
                    TextButton(onClick = onRetry) {
                        Text("Reintentar")
                    }
                }
            }
        }
    }
}

// ---------- Helper Composables ----------

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        fontWeight = FontWeight.Bold,
        fontSize = 16.sp,
        color = MaterialTheme.colorScheme.onSurface,
    )
}

@Composable
private fun MiniStat(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = color.copy(alpha = 0.7f),
            maxLines = 1,
        )
        AdaptiveAmountText(
            text = value,
            baseStyle =
                MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                ),
            color = color,
            minFontSizeSp = 12f,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun SummaryRow(
    icon: ImageVector,
    iconTint: Color,
    label: String,
    value: String,
    valueColor: Color,
    isBold: Boolean = false,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f, fill = false),
        ) {
            Box(
                modifier =
                    Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(iconTint.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Text(
                text = label,
                style = if (isBold) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium,
                fontWeight = if (isBold) FontWeight.Bold else FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
        }
        // Monto adaptive: etiquetas largas + importes enormes nunca desbordan en 320dp.
        AdaptiveAmountText(
            text = value,
            baseStyle =
                (
                    if (isBold) {
                        MaterialTheme.typography.titleMedium
                    } else {
                        MaterialTheme.typography.titleSmall
                    }
                ).copy(
                    fontWeight = FontWeight.Bold,
                    textAlign = androidx.compose.ui.text.style.TextAlign.End,
                ),
            color = valueColor,
            minFontSizeSp = 12f,
            modifier = Modifier.weight(1f, fill = false).padding(start = 12.dp),
        )
    }
}

private fun formatMoney(amount: Double): String = "$ ${String.format(java.util.Locale.getDefault(), "%.2f", amount)}"
