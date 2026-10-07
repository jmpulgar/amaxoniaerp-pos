package com.amaxonia.erp.ui.pos

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Wallet
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.amaxonia.erp.data.remote.dto.FormaPagoDto
import com.amaxonia.erp.ui.components.PosGradientButton
import com.amaxonia.erp.ui.components.isLandscape
import com.amaxonia.erp.ui.theme.AccentPurple
import com.amaxonia.erp.ui.theme.ConfirmedContainer
import com.amaxonia.erp.ui.theme.ConfirmedContent
import com.amaxonia.erp.ui.theme.InfoBlue
import com.amaxonia.erp.ui.theme.InfoCyan
import com.amaxonia.erp.ui.theme.PaymentTeal
import com.amaxonia.erp.ui.theme.PosExtraShapes
import com.amaxonia.erp.ui.theme.PosPalette
import com.amaxonia.erp.ui.theme.SuccessGreen
import com.amaxonia.erp.ui.theme.WarningOrange
import com.amaxonia.erp.ui.util.forceShowKeyboardOnTouch
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.floor

@Composable
fun PaymentDialog(
    state: PosUiState,
    onReceivedAmountChange: (String) -> Unit,
    onSelectPaymentMethod: (FormaPagoDto) -> Unit,
    onConfirmPayment: () -> Unit,
    onDismiss: () -> Unit,
) {
    val isLandscape = isLandscape()
    val selectedMethod = state.selectedPaymentMethod
    val isCash = isCashPaymentMethod(selectedMethod)
    val receivedAmount = state.receivedAmountText.toDoubleOrNull() ?: 0.0
    val isPaymentValid = if (isCash) {
        receivedAmount >= state.summary.total && state.summary.total > 0.0
    } else {
        state.summary.total > 0.0
    }

    var elapsedSeconds by remember { mutableIntStateOf(0) }
    LaunchedEffect(state.isProcessingSale) {
        if (!state.isProcessingSale) {
            elapsedSeconds = 0
            return@LaunchedEffect
        }
        elapsedSeconds = 0
        while (true) {
            delay(1_000)
            elapsedSeconds += 1
        }
    }

    val effectiveTaxLabel = remember(state.cart) {
        val rates = state.cart.map { it.taxRate }.distinct().filter { it > 0.0 }
        when {
            rates.isEmpty() -> "IVA (0%)"
            rates.size == 1 -> "IVA (${String.format(Locale.US, "%.0f", rates.first())}%)"
            else -> "Impuestos / IVA"
        }
    }

    Dialog(
        onDismissRequest = { if (!state.isProcessingSale) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                modifier = Modifier
                    .padding(16.dp)
                    .then(
                        if (isLandscape) {
                            Modifier
                                .widthIn(min = 680.dp, max = 860.dp)
                                .fillMaxHeight(0.92f)
                        } else {
                            Modifier
                                .fillMaxWidth(0.96f)
                                .fillMaxHeight(0.92f)
                        },
                    ),
            ) {
                Column(
                    modifier = Modifier
                        .padding(20.dp)
                        .fillMaxHeight(),
                ) {
                    // Cabecera del Diálogo
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Text(
                                text = "Cobro",
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                text = "Selecciona la forma de pago y confirma la transacción",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(
                            onClick = onDismiss,
                            enabled = !state.isProcessingSale,
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Cerrar",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    if (isLandscape) {
                        PaymentLandscapeContent(
                            state = state,
                            isCash = isCash,
                            selectedMethod = selectedMethod,
                            effectiveTaxLabel = effectiveTaxLabel,
                            isPaymentValid = isPaymentValid,
                            onReceivedAmountChange = onReceivedAmountChange,
                            onSelectPaymentMethod = onSelectPaymentMethod,
                            onConfirmPayment = onConfirmPayment,
                            onDismiss = onDismiss,
                        )
                    } else {
                        PaymentPortraitContent(
                            state = state,
                            isCash = isCash,
                            selectedMethod = selectedMethod,
                            effectiveTaxLabel = effectiveTaxLabel,
                            isPaymentValid = isPaymentValid,
                            onReceivedAmountChange = onReceivedAmountChange,
                            onSelectPaymentMethod = onSelectPaymentMethod,
                            onConfirmPayment = onConfirmPayment,
                            onDismiss = onDismiss,
                        )
                    }
                }
            }

            // Overlay animado de procesamiento de cobro
            if (state.isProcessingSale) {
                ProcessingPaymentOverlay(
                    elapsedSeconds = elapsedSeconds,
                )
            }
        }
    }
}

@Composable
private fun PaymentLandscapeContent(
    state: PosUiState,
    isCash: Boolean,
    selectedMethod: FormaPagoDto?,
    effectiveTaxLabel: String,
    isPaymentValid: Boolean,
    onReceivedAmountChange: (String) -> Unit,
    onSelectPaymentMethod: (FormaPagoDto) -> Unit,
    onConfirmPayment: () -> Unit,
    onDismiss: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Columna Izquierda: Desglose Financiero (Fijo) + Formas de Pago (Scroll independiente)
        Column(
            modifier = Modifier
                .weight(1.05f)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            FinancialBreakdown(
                summary = state.summary,
                effectiveTaxLabel = effectiveTaxLabel,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Forma de Pago (${state.paymentMethods.size}):",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (state.paymentMethods.size > 4) {
                    Text(
                        text = "Desliza para ver más",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Contenedor de Formas de Pago con Scroll Independiente
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                state.paymentMethods.chunked(2).forEach { rowMethods ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        rowMethods.forEach { method ->
                            PaymentMethodCard(
                                method = method,
                                isSelected = state.selectedPaymentMethod?.idFormaPago == method.idFormaPago,
                                onClick = { onSelectPaymentMethod(method) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        if (rowMethods.size == 1) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }

        // Columna Derecha: Detalle de Pago (Efectivo / No-Efectivo) + Acciones
        Column(
            modifier = Modifier
                .weight(0.95f)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (isCash) {
                CashPaymentSection(
                    total = state.summary.total,
                    receivedAmountText = state.receivedAmountText,
                    changeAmount = state.changeAmount,
                    onReceivedAmountChange = onReceivedAmountChange,
                    onSetExactAmount = {
                        onReceivedAmountChange(formatAmount(state.summary.total))
                    },
                )
            } else {
                NonCashPaymentSection(
                    method = selectedMethod,
                    total = state.summary.total,
                )
            }

            if (state.errorMessage != null) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = state.errorMessage,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(10.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f, fill = false))

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PosGradientButton(
                    text = "Confirmar y Facturar",
                    onClick = onConfirmPayment,
                    enabled = !state.isProcessingSale && isPaymentValid,
                    loading = state.isProcessingSale,
                    shape = PosExtraShapes.InputRadius,
                    modifier = Modifier.fillMaxWidth(),
                )

                OutlinedButton(
                    onClick = onDismiss,
                    enabled = !state.isProcessingSale,
                    shape = PosExtraShapes.InputRadius,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Cancelar")
                }
            }
        }
    }
}

@Composable
private fun PaymentPortraitContent(
    state: PosUiState,
    isCash: Boolean,
    selectedMethod: FormaPagoDto?,
    effectiveTaxLabel: String,
    isPaymentValid: Boolean,
    onReceivedAmountChange: (String) -> Unit,
    onSelectPaymentMethod: (FormaPagoDto) -> Unit,
    onConfirmPayment: () -> Unit,
    onDismiss: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        FinancialBreakdown(
            summary = state.summary,
            effectiveTaxLabel = effectiveTaxLabel,
            compact = true,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Forma de Pago (${state.paymentMethods.size}):",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (state.paymentMethods.size > 4) {
                Text(
                    text = "Desliza para ver más",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // Contenedor acotado para formas de pago
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 210.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            state.paymentMethods.chunked(2).forEach { rowMethods ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    rowMethods.forEach { method ->
                        PaymentMethodCard(
                            method = method,
                            isSelected = state.selectedPaymentMethod?.idFormaPago == method.idFormaPago,
                            onClick = { onSelectPaymentMethod(method) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (rowMethods.size == 1) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }

        if (isCash) {
            CashPaymentSection(
                total = state.summary.total,
                receivedAmountText = state.receivedAmountText,
                changeAmount = state.changeAmount,
                onReceivedAmountChange = onReceivedAmountChange,
                onSetExactAmount = {
                    onReceivedAmountChange(formatAmount(state.summary.total))
                },
            )
        } else {
            NonCashPaymentSection(
                method = selectedMethod,
                total = state.summary.total,
            )
        }

        if (state.errorMessage != null) {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = state.errorMessage,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(10.dp),
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PosGradientButton(
                text = "Confirmar y Facturar",
                onClick = onConfirmPayment,
                enabled = !state.isProcessingSale && isPaymentValid,
                loading = state.isProcessingSale,
                shape = PosExtraShapes.InputRadius,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedButton(
                onClick = onDismiss,
                enabled = !state.isProcessingSale,
                shape = PosExtraShapes.InputRadius,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Cancelar")
            }
        }
    }
}

@Composable
internal fun FinancialBreakdown(
    summary: PosCartSummary,
    effectiveTaxLabel: String,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.40f),
        shape = RoundedCornerShape(14.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(
                horizontal = if (compact) 12.dp else 14.dp,
                vertical = if (compact) 8.dp else 10.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            BreakdownRow(
                label = "Subtotal Bruto",
                amount = summary.grossSubtotal,
                compact = compact,
            )
            if (summary.discountTotal > 0.0) {
                BreakdownRow(
                    label = "Descuento",
                    amount = summary.discountTotal,
                    isDiscount = true,
                    compact = compact,
                )
                BreakdownRow(
                    label = "Subtotal Neto",
                    amount = summary.subtotal,
                    compact = compact,
                )
            }
            BreakdownRow(
                label = effectiveTaxLabel,
                amount = summary.tax,
                compact = compact,
            )
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 4.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
            )
            BreakdownRow(
                label = "Total a Pagar",
                amount = summary.total,
                emphasize = true,
                compact = compact,
            )
        }
    }
}

@Composable
private fun BreakdownRow(
    label: String,
    amount: Double,
    isDiscount: Boolean = false,
    emphasize: Boolean = false,
    compact: Boolean = false,
) {
    val labelStyle = when {
        emphasize -> MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
        compact -> MaterialTheme.typography.bodySmall
        else -> MaterialTheme.typography.bodyMedium
    }
    val amountStyle = when {
        emphasize -> MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold)
        compact -> MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold)
        else -> MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
    }
    val amountColor = when {
        isDiscount -> SuccessGreen
        emphasize -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurface
    }
    val labelColor = when {
        isDiscount -> SuccessGreen
        emphasize -> MaterialTheme.colorScheme.onSurface
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = labelStyle,
            color = labelColor,
        )
        val textPrefix = if (isDiscount) "-$" else "$"
        Text(
            text = "$textPrefix${formatAmount(amount)}",
            style = amountStyle,
            color = amountColor,
        )
    }
}

@Composable
private fun PaymentMethodCard(
    method: FormaPagoDto,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val icon = paymentMethodIcon(method)
    val color = paymentMethodColor(method)
    val isCash = isCashPaymentMethod(method)

    Surface(
        onClick = onClick,
        modifier = modifier.clip(RoundedCornerShape(14.dp)),
        shape = RoundedCornerShape(14.dp),
        color = if (isSelected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
        } else {
            MaterialTheme.colorScheme.surface
        },
        border = BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = if (isSelected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            },
        ),
        shadowElevation = if (isSelected) 3.dp else 1.dp,
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 10.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(20.dp),
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = method.descripcion ?: method.codigo ?: "Pago",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val subtitle = method.siglas?.uppercase()?.takeIf { it.isNotBlank() }
                    ?: if (isCash) "EFECTIVO" else method.codigo.orEmpty()
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            if (isSelected) {
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Seleccionado",
                        tint = PosPalette.FixedWhite,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun CashPaymentSection(
    total: Double,
    receivedAmountText: String,
    changeAmount: Double,
    onReceivedAmountChange: (String) -> Unit,
    onSetExactAmount: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val received = receivedAmountText.toDoubleOrNull() ?: 0.0
    val isSufficient = received >= total && total > 0.0
    val isMissing = !isSufficient && received > 0.0 && receivedAmountText.isNotBlank()
    val missingAmount = (total - received).coerceAtLeast(0.0)
    val suggestedBills = remember(total) { calculateSuggestedBills(total) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Botón de cobro exacto superior
        Button(
            onClick = onSetExactAmount,
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            ),
        ) {
            Icon(
                imageVector = Icons.Default.Wallet,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Cobro exacto: $${formatAmount(total)}",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            )
        }

        // Chips de sugerencia de billetes rápidos
        if (suggestedBills.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Billetes sugeridos:",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    suggestedBills.forEach { bill ->
                        Surface(
                            onClick = { onReceivedAmountChange(formatAmount(bill)) },
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                        ) {
                            Text(
                                text = "$${formatAmount(bill)}",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            )
                        }
                    }
                }
            }
        }

        // Campo de monto recibido
        OutlinedTextField(
            value = receivedAmountText,
            onValueChange = onReceivedAmountChange,
            label = { Text("Monto Recibido ($)", fontSize = 12.sp) },
            leadingIcon = {
                Text(
                    "$",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 12.dp),
                )
            },
            trailingIcon = {
                if (receivedAmountText.isNotBlank()) {
                    IconButton(onClick = { onReceivedAmountChange("") }) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = "Limpiar monto",
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier
                .fillMaxWidth()
                .forceShowKeyboardOnTouch(),
            shape = PosExtraShapes.InputRadius,
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
        )

        // Banner animado de cambio / vuelto
        AnimatedVisibility(
            visible = isSufficient && changeAmount >= 0.0,
            enter = fadeIn() + slideInVertically(initialOffsetY = { it / 2 }) + scaleIn(initialScale = 0.95f),
            exit = fadeOut(),
        ) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = ConfirmedContainer,
                border = BorderStroke(1.dp, ConfirmedContent.copy(alpha = 0.3f)),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(SuccessGreen),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = PosPalette.FixedWhite,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Su cambio (vuelto)",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = SuccessGreen,
                        )
                    }
                    Text(
                        text = "$${formatAmount(changeAmount)}",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                        color = SuccessGreen,
                    )
                }
            }
        }

        // Alerta animada de monto insuficiente
        AnimatedVisibility(
            visible = isMissing,
            enter = fadeIn() + slideInVertically(initialOffsetY = { it / 2 }),
            exit = fadeOut(),
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Faltan $${formatAmount(missingAmount)} para completar el pago",
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun NonCashPaymentSection(
    method: FormaPagoDto?,
    total: Double,
    modifier: Modifier = Modifier,
) {
    val icon = paymentMethodIcon(method)
    val color = paymentMethodColor(method)

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.50f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(color.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = color,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = method?.descripcion ?: method?.codigo ?: "Pago con Tarjeta / Transferencia",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "Monto a procesar: $${formatAmount(total)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "El pago se procesará por el monto exacto sin generar cambio.",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
        }
    }
}

@Composable
internal fun ProcessingPaymentOverlay(
    elapsedSeconds: Int,
    statusMessage: String? = null,
) {
    val estimatedSeconds = 30
    val progress = (elapsedSeconds / estimatedSeconds.toFloat()).coerceIn(0.08f, 0.94f)
    val secondsLeft = (estimatedSeconds - elapsedSeconds).coerceAtLeast(3)

    val stageTitle = when {
        elapsedSeconds < 4 -> "Preparando la factura"
        elapsedSeconds < 10 -> "Conectando con facturación electrónica"
        elapsedSeconds < 24 -> "Esperando autorización fiscal"
        else -> "Últimos segundos de validación"
    }
    val stageSubtitle = statusMessage?.takeIf { it.isNotBlank() } ?: when {
        elapsedSeconds < 4 -> "Validando pago, caja e inventario..."
        elapsedSeconds < 10 -> "Enviando el documento fiscal al servidor..."
        elapsedSeconds < 24 -> "Procesando CUFE, QR y registro contable..."
        else -> "La respuesta está tardando un poco más de lo normal, seguimos esperando."
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PosPalette.FixedBlack.copy(alpha = 0.50f))
            .padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        ElevatedCard(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 10.dp),
            modifier = Modifier
                .widthIn(max = if (isLandscape()) 460.dp else 360.dp)
                .fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Indicador Circular con Segundos Transcurridos
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.size(54.dp),
                        strokeWidth = 5.dp,
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                    )
                    Text(
                        text = "${elapsedSeconds}s",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Procesando cobro",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stageTitle,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Text(
                    text = stageSubtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 6.dp),
                )

                Spacer(modifier = Modifier.height(18.dp))

                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(50)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = if (elapsedSeconds < estimatedSeconds) {
                        "Tiempo estimado restante: ${secondsLeft}s"
                    } else {
                        "Está tomando más de lo habitual, seguimos procesando."
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(modifier = Modifier.height(12.dp))

                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Text(
                        text = "Evita tocar atrás o cerrar la app.",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }
        }
    }
}

internal fun calculateSuggestedBills(total: Double): List<Double> {
    if (total <= 0.0) return emptyList()
    val suggestions = sortedSetOf<Double>()

    val ceilVal = ceil(total)
    if (ceilVal > total) {
        suggestions.add(ceilVal)
    }

    val standardDenominations = listOf(1.0, 5.0, 10.0, 20.0, 50.0, 100.0)
    for (bill in standardDenominations) {
        if (bill > total) {
            suggestions.add(bill)
        }
    }

    if (total > 10.0) {
        val next5 = (floor(total / 5.0) + 1) * 5.0
        if (next5 > total) suggestions.add(next5)
        val next10 = (floor(total / 10.0) + 1) * 10.0
        if (next10 > total) suggestions.add(next10)
    }

    return suggestions.toList().take(5)
}

internal fun paymentMethodIcon(method: FormaPagoDto?): ImageVector {
    if (method == null) return Icons.Default.Payments
    val sig = method.siglas?.uppercase().orEmpty()
    val desc = method.descripcion?.uppercase().orEmpty()
    val cod = method.codigo?.uppercase().orEmpty()
    return when {
        isCashPaymentMethod(method) -> Icons.Default.Payments
        sig.contains("TDC") || sig.contains("TDD") || desc.contains("TARJETA") || cod.contains("TARJETA") -> Icons.Default.CreditCard
        sig.contains("TR") || sig.contains("BANK") || desc.contains("TRANS") || cod.contains("TRANS") -> Icons.Default.AccountBalance
        sig.contains("CXC") || desc.contains("CRÉDITO") || desc.contains("CREDITO") -> Icons.AutoMirrored.Filled.ReceiptLong
        else -> Icons.Default.Payments
    }
}

internal fun paymentMethodColor(method: FormaPagoDto?): Color {
    if (method == null) return PaymentTeal
    val sig = method.siglas?.uppercase().orEmpty()
    val desc = method.descripcion?.uppercase().orEmpty()
    val cod = method.codigo?.uppercase().orEmpty()
    return when {
        isCashPaymentMethod(method) -> SuccessGreen
        sig.contains("TDC") || desc.contains("CRÉDITO") || cod.contains("CREDITO") -> AccentPurple
        sig.contains("TDD") || desc.contains("DÉBITO") || desc.contains("DEBITO") || desc.contains("TARJETA") -> InfoBlue
        sig.contains("TR") || desc.contains("TRANS") -> PaymentTeal
        sig.contains("CXC") -> WarningOrange
        else -> InfoCyan
    }
}

private fun formatAmount(amount: Double): String =
    String.format(Locale.US, "%.2f", amount)
