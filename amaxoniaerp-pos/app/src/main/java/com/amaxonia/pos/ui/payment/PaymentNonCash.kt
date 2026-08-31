package com.amaxonia.pos.ui.payment

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Wallet
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amaxonia.pos.domain.model.money.Money
import com.amaxonia.pos.domain.model.payment.FormaPago
import com.amaxonia.pos.ui.common.components.AdaptiveAmountOptions
import com.amaxonia.pos.ui.common.components.AdaptiveAmountText
import com.amaxonia.pos.ui.common.components.PosEmptyState
import com.amaxonia.pos.ui.theme.PosTextStyles
import com.amaxonia.pos.ui.theme.paymentMethodColor

/**
 * Paneles de cobro con medios no-efectivo (tarjetas, otros y CxC).
 * Movidos 1:1 desde PaymentScreen.kt; comportamiento y contratos intactos.
 */
@Composable
internal fun NonCashSummaryPanel(
    state: PaymentState,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.Wallet,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                "Resumen del cobro",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.40f),
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                NonCashSummaryLines(state = state)
            }
        }
        InsufficientFundsReminder(state = state)
    }
}

/** Líneas del resumen: efectivo asignado, tarjeta/otro, total asignado y saldo pendiente. */
@Composable
private fun NonCashSummaryLines(state: PaymentState) {
    // Pago mixto: visibilidad del efectivo ya asignado desde su pestaña.
    if (state.tenderedAmountMoney > Money.ZERO) {
        PaymentSummaryLine(
            label = "Efectivo",
            value = "$ ${state.tenderedAmountText}",
            secondary =
                state.tenderedAmountBsText
                    .takeIf { state.isMultiCurrency && it.isNotBlank() }
                    ?.let { "$SECONDARY_CURRENCY_LABEL $it" },
            emphasized = false,
        )
    }
    if (state.nonCashAssignedMoney > Money.ZERO) {
        PaymentSummaryLine(
            label = "Tarjeta / Otro",
            value = "$ ${state.nonCashAssignedText}",
            secondary =
                state.nonCashAssignedBsText
                    .takeIf { state.isMultiCurrency && it.isNotBlank() }
                    ?.let { "$SECONDARY_CURRENCY_LABEL $it" },
            emphasized = false,
        )
    }
    PaymentSummaryLine(
        label = "Total asignado",
        value = "$ ${state.assignedText}",
        secondary =
            state.assignedBsText
                .takeIf { state.isMultiCurrency && it.isNotBlank() }
                ?.let { "$SECONDARY_CURRENCY_LABEL $it" },
        emphasized = state.isPaymentEnough,
    )
    PaymentSummaryLine(
        label = "Saldo restante",
        value = "$ ${state.nonCashPendingText}",
        secondary =
            state.nonCashPendingBsText
                .takeIf { state.isMultiCurrency && it.isNotBlank() }
                ?.let { "$SECONDARY_CURRENCY_LABEL $it" },
        emphasized = !state.isPaymentEnough,
    )
}

/** Recordatorio animado del faltante para completar el pago mixto. */
@Composable
private fun InsufficientFundsReminder(state: PaymentState) {
    AnimatedVisibility(
        visible = state.showInsufficientReminder && !state.isPaymentEnough,
        enter = fadeIn() + slideInVertically(initialOffsetY = { it / 2 }),
        exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 2 }),
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.50f),
            modifier = Modifier.padding(top = 8.dp).fillMaxWidth(),
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
                    text =
                        buildString {
                            append("Faltan $ ${state.nonCashPendingText} para completar el pago")
                            if (state.isMultiCurrency && state.nonCashPendingBsText.isNotBlank()) {
                                append(" ($SECONDARY_CURRENCY_LABEL ${state.nonCashPendingBsText})")
                            }
                        },
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.5.sp,
                )
            }
        }
    }
}

@Composable
internal fun NonCashListPanel(
    state: PaymentState,
    onAction: (PaymentUiAction) -> Unit,
    fillRemaining: Boolean,
    modifier: Modifier = Modifier,
    narrow: Boolean = false,
) {
    Column(modifier = modifier) {
        if (state.formasPagoTarjetaOtro.isEmpty()) {
            PosEmptyState(
                icon = Icons.Default.CreditCard,
                title = "Otros medios no disponibles",
                message = "No hay tarjetas u otras formas de pago configuradas para esta caja.",
                modifier = Modifier.fillMaxWidth(),
            )
            return
        }

        NonCashFormasHeader(count = state.formasPagoTarjetaOtro.size)
        val listModifier = if (fillRemaining) Modifier.weight(1f) else Modifier
        LazyColumn(
            modifier = listModifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(state.formasPagoTarjetaOtro, key = { it.idFormaPago }) { forma ->
                NonCashRow(
                    forma = forma,
                    value = state.nonCashAmountsInput[forma.idFormaPago].orEmpty(),
                    pendingAmount = state.nonCashPendingText,
                    canUseCredit = state.canUseCredit,
                    narrow = narrow,
                    onValueChange = {
                        onAction(PaymentUiAction.SetNonCashAmount(forma.idFormaPago, it))
                    },
                    onUseExactAmount = {
                        onAction(PaymentUiAction.SetExactNonCashAmount(forma.idFormaPago))
                    },
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        PrimaryCtaButton(
            state = state,
            warningScale = 1f,
            isInsufficient = state.showInsufficientReminder && !state.isPaymentEnough,
            onClick = { onAction(PaymentUiAction.ProcessPayment) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Encabezado "Formas disponibles" con badge de cantidad. */
@Composable
private fun NonCashFormasHeader(count: Int) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            "Formas disponibles",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
            maxLines = 1,
        )
        Surface(
            shape = RoundedCornerShape(50),
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ) {
            Text(
                text = "$count disponibles",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
            )
        }
    }
    Spacer(modifier = Modifier.height(10.dp))
}

/** Campo de monto + botón "Completar $X" compartido por las filas de formas de pago. */
@Composable
internal fun AmountInputRow(
    value: String,
    pendingAmount: String,
    onValueChange: (String) -> Unit,
    onUseExactAmount: () -> Unit,
    narrow: Boolean = false,
) {
    val isPending = pendingAmount.isNotBlank() && pendingAmount != "0.00"
    if (narrow) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                label = { Text("Monto asignado") },
                prefix = { Text("$ ") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
            )
            ExactAmountButton(
                pendingAmount = pendingAmount,
                enabled = isPending,
                onClick = onUseExactAmount,
                modifier = Modifier.fillMaxWidth().height(48.dp),
            )
        }
    } else {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                label = { Text("Monto asignado") },
                prefix = { Text("$ ") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f),
            )
            Spacer(modifier = Modifier.width(10.dp))
            ExactAmountButton(
                pendingAmount = pendingAmount,
                enabled = isPending,
                onClick = onUseExactAmount,
                modifier = Modifier.height(52.dp).defaultMinSize(minWidth = 132.dp),
            )
        }
    }
}

@Composable
private fun ExactAmountButton(
    pendingAmount: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FilledTonalButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
    ) {
        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = "Completar \$$pendingAmount",
            maxLines = 1,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
internal fun PaymentSummaryLine(
    label: String,
    value: String,
    secondary: String?,
    emphasized: Boolean,
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(horizontalAlignment = Alignment.End) {
            AdaptiveAmountText(
                text = value,
                baseStyle =
                    MaterialTheme.typography.titleMedium.copy(
                        fontWeight = if (emphasized) FontWeight.Bold else FontWeight.Medium,
                    ),
                color =
                    if (emphasized) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                modifier = Modifier.widthIn(max = 180.dp),
                options =
                    AdaptiveAmountOptions(
                        minFontSizeSp = 11f,
                        maxLines = 1,
                    ),
            )
            secondary?.let {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.50f),
                    modifier = Modifier.padding(top = 2.dp),
                ) {
                    Text(
                        text = it,
                        style = PosTextStyles.amountSecondary.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                    )
                }
            }
        }
    }
}

@Composable
internal fun PaymentMethodIcon(forma: FormaPago) {
    val decodedImage = remember(forma.imagen) { decodeBase64Image(forma.imagen) }
    val fallbackColor = remember(forma.siglas) { paymentMethodColor(forma.siglas) }

    Box(
        modifier =
            Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(fallbackColor.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center,
    ) {
        if (decodedImage != null) {
            Image(
                bitmap = decodedImage,
                contentDescription = forma.descripcion,
                modifier =
                    Modifier
                        .fillMaxSize()
                        .clip(CircleShape),
            )
        } else {
            Icon(
                imageVector = fallbackIconForSigla(forma.siglas),
                contentDescription = forma.descripcion,
                tint = fallbackColor,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

internal fun decodeBase64Image(imageData: String?): ImageBitmap? {
    if (imageData.isNullOrBlank()) return null
    return try {
        val normalized = imageData.substringAfter("base64,", imageData).trim()
        if (normalized.isBlank()) {
            null
        } else {
            val bytes = Base64.decode(normalized, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
        }
    } catch (_: IllegalArgumentException) {
        null
    }
}

internal fun fallbackIconForSigla(sigla: String?): ImageVector =
    when (sigla?.uppercase()) {
        "TDC", "TDD" -> Icons.Default.CreditCard
        "TR", "DB", "CK", "BANK" -> Icons.Default.AccountBalance
        "CXC" -> Icons.Default.Wallet
        else -> Icons.Default.Wallet
    }
