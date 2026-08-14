package com.amaxonia.pos.ui.payment

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.TableRestaurant
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.amaxonia.pos.domain.model.money.Money
import com.amaxonia.pos.domain.model.payment.PaymentSuccessPayload
import com.amaxonia.pos.ui.common.DependencyContainer
import com.amaxonia.pos.ui.common.components.AdaptiveAmountText
import com.amaxonia.pos.ui.common.components.PosFeedbackCard
import com.amaxonia.pos.ui.common.components.PosStatusBadge
import com.amaxonia.pos.ui.common.components.PosVisualTone
import com.amaxonia.pos.ui.common.injectedViewModel
import kotlinx.coroutines.launch

private const val RING_INITIAL_SCALE = 0.6f
private const val RING_INITIAL_ALPHA = 0.55f
private const val RING_TARGET_SCALE = 1.7f
private const val RING_ANIMATION_DURATION_MS = 650
private const val CARD_INITIAL_SCALE = 0.85f
private const val CARD_SCALE_ANIMATION_DURATION_MS = 260
private val WIDE_CONTENT_MAX_WIDTH = 560.dp

/** Acciones de la pantalla agrupadas para mantener las firmas de los composables pequeñas. */
internal class SuccessActions(
    val onPrintReceipt: () -> Unit,
    val onSendReceiptEmail: () -> Unit,
    val onNextOrder: () -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SuccessScreen(
    transactionId: String,
    onPrintReceipt: suspend (String) -> Result<String>,
    onNextOrder: () -> Unit,
) {
    val viewModel =
        injectedViewModel {
            PaymentSuccessViewModel(
                paymentSuccessRepository = DependencyContainer.posConfigurationRepository,
                salesRepository = DependencyContainer.salesRepository,
                transactionId = transactionId,
            )
        }

    val uiState by viewModel.state.collectAsStateWithLifecycle()
    val payload = uiState.payload

    var isPrinting by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(uiState.errorMessage) {
        val msg = uiState.errorMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
        }
    }

    LaunchedEffect(payload?.receiptPrintMessage) {
        val message = payload?.receiptPrintMessage.orEmpty()
        if (message.isNotBlank()) {
            snackbarHostState.showSnackbar(message)
        }
    }

    BackHandler(enabled = true) {
        scope.launch {
            snackbarHostState.showSnackbar(
                message = "Usa Nueva orden para finalizar y limpiar el estado de venta",
            )
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.TopCenter) {
            SuccessContent(
                isLoading = uiState.isLoading || payload == null,
                payload = payload,
                isSendingReceiptEmail = uiState.isSendingReceiptEmail,
                isPrinting = isPrinting,
                actions =
                    SuccessActions(
                        onPrintReceipt = {
                            if (transactionId.isBlank()) {
                                scope.launch { snackbarHostState.showSnackbar("No hay transaccion para imprimir") }
                                return@SuccessActions
                            }
                            scope.launch {
                                isPrinting = true
                                val result = onPrintReceipt(transactionId)
                                val feedback =
                                    result.getOrElse { error ->
                                        error.message ?: "No se pudo imprimir el recibo"
                                    }
                                snackbarHostState.showSnackbar(feedback)
                                isPrinting = false
                            }
                        },
                        onSendReceiptEmail = { viewModel.sendReceiptEmail() },
                        onNextOrder = onNextOrder,
                    ),
            )
        }
    }
}

/**
 * Presentation layer of the payment-success screen: owns only the celebratory
 * scale/ring animation. All data, navigation, printing and email wiring arrive as parameters.
 */
@Composable
internal fun SuccessContent(
    isLoading: Boolean,
    payload: PaymentSuccessPayload?,
    isSendingReceiptEmail: Boolean,
    isPrinting: Boolean,
    actions: SuccessActions,
    modifier: Modifier = Modifier,
) {
    var visible by remember { mutableStateOf(false) }
    val scale = remember { Animatable(CARD_INITIAL_SCALE) }
    val ringScale = remember { Animatable(RING_INITIAL_SCALE) }
    val ringAlpha = remember { Animatable(RING_INITIAL_ALPHA) }

    LaunchedEffect(Unit) {
        visible = true
        scale.animateTo(
            targetValue = 1f,
            animationSpec = tween(CARD_SCALE_ANIMATION_DURATION_MS, easing = FastOutSlowInEasing),
        )
    }
    LaunchedEffect(Unit) {
        ringScale.animateTo(
            RING_TARGET_SCALE,
            animationSpec = tween(RING_ANIMATION_DURATION_MS, easing = FastOutSlowInEasing),
        )
    }
    LaunchedEffect(Unit) {
        ringAlpha.animateTo(0f, animationSpec = tween(RING_ANIMATION_DURATION_MS))
    }

    Column(
        modifier =
            modifier
                .fillMaxHeight()
                .fillMaxWidth()
                .widthIn(max = WIDE_CONTENT_MAX_WIDTH)
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 20.dp, vertical = 16.dp)
                .navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(animationSpec = tween(CARD_SCALE_ANIMATION_DURATION_MS)) +
                    scaleIn(initialScale = 0.92f, animationSpec = tween(220)),
            ) {
                if (isLoading) {
                    LoadingConfirmation(scale = scale.value)
                } else {
                    SuccessCard(
                        payload = requireNotNull(payload) { "SuccessCard requires a loaded payload" },
                        scale = scale.value,
                        ringScale = ringScale.value,
                        ringAlpha = ringAlpha.value,
                    )
                }
            }
        }

        SecondaryReceiptActions(
            isPrinting = isPrinting,
            isSendingReceiptEmail = isSendingReceiptEmail,
            onPrintReceipt = actions.onPrintReceipt,
            onSendReceiptEmail = actions.onSendReceiptEmail,
        )
        Spacer(modifier = Modifier.height(12.dp))
        NextOrderButton(onNextOrder = actions.onNextOrder)
    }
}

@Composable
private fun LoadingConfirmation(scale: Float) {
    Column(
        modifier =
            Modifier
                .padding(24.dp)
                .fillMaxWidth()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            "Confirmando factura y cierre…",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SuccessCard(
    payload: PaymentSuccessPayload,
    scale: Float,
    ringScale: Float,
    ringAlpha: Float,
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 6.dp),
    ) {
        Column(
            modifier =
                Modifier
                    .padding(24.dp)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            SuccessMark(ringScale = ringScale, ringAlpha = ringAlpha)

            Spacer(modifier = Modifier.height(18.dp))

            Text(
                "Pago confirmado",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )

            Text(
                if (payload.codFactura.isBlank()) "Factura generada correctamente" else "Factura: ${payload.codFactura}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            payload.feError?.takeIf { it.isNotBlank() }?.let { feError ->
                Spacer(modifier = Modifier.height(16.dp))
                PosFeedbackCard(
                    title = "Factura creada, confirmación fiscal pendiente",
                    message = feError,
                    tone = PosVisualTone.Warning,
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            CompletionStatus(payload = payload)

            Spacer(modifier = Modifier.height(16.dp))

            ChangeSummary(payload = payload)
        }
    }
}

@Composable
private fun SuccessMark(
    ringScale: Float,
    ringAlpha: Float,
) {
    Box(contentAlignment = Alignment.Center) {
        // Expanding confirmation ring.
        Box(
            modifier =
                Modifier
                    .size(92.dp)
                    .graphicsLayer {
                        scaleX = ringScale
                        scaleY = ringScale
                        alpha = ringAlpha
                    }.background(MaterialTheme.colorScheme.primary.copy(alpha = 0.22f), CircleShape),
        )
        // Confident filled success disc with the check.
        Surface(
            modifier = Modifier.size(84.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            shadowElevation = 4.dp,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier.size(44.dp),
                )
            }
        }
    }
}

@Composable
private fun CompletionStatus(payload: PaymentSuccessPayload) {
    val hasFiscalError = !payload.feError.isNullOrBlank()
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        PosStatusBadge(
            label = "Pago aprobado",
            tone = PosVisualTone.Success,
            icon = Icons.Default.Check,
        )
        PosStatusBadge(
            label =
                when {
                    hasFiscalError -> "Confirmación fiscal pendiente"
                    payload.codFactura.isBlank() -> "Factura registrada"
                    else -> "Factura ${payload.codFactura} confirmada"
                },
            tone = if (hasFiscalError) PosVisualTone.Warning else PosVisualTone.Success,
            icon = if (hasFiscalError) Icons.Default.Warning else Icons.AutoMirrored.Filled.ReceiptLong,
        )
        PosStatusBadge(
            label =
                if (payload.tableSessionClosed) {
                    "Mesa cerrada y disponible"
                } else {
                    "Mesa con saldo o consumo pendiente"
                },
            tone = if (payload.tableSessionClosed) PosVisualTone.Success else PosVisualTone.Info,
            icon = Icons.Default.TableRestaurant,
        )
    }
}

@Composable
private fun ChangeSummary(payload: PaymentSuccessPayload) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            SummaryLine(
                label = "Método de pago",
                value = payload.paymentMethodsLabel.ifBlank { "N/A" },
            )
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant,
                modifier = Modifier.padding(vertical = 10.dp),
            )
            if (payload.isMultiCurrency && payload.totalBs > 0.0) {
                SummaryLine(
                    label = "Total ${formatCurrencyLabel(payload.abrMonedaSecundaria)}",
                    value = String.format(java.util.Locale.getDefault(), "%.2f", payload.totalBs),
                )
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant,
                    modifier = Modifier.padding(vertical = 10.dp),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    "Cambio / Vuelto",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                AdaptiveAmountText(
                    text = "$ ${Money.format(Money.fromDouble(payload.changeDue))}",
                    baseStyle =
                        MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.ExtraBold,
                        ),
                    color = MaterialTheme.colorScheme.primary,
                    minFontSizeSp = 16f,
                )
            }
            if (payload.isMultiCurrency && payload.changeDueBs > 0.0) {
                Text(
                    "${formatCurrencyLabel(payload.abrMonedaSecundaria)} ${
                        String.format(java.util.Locale.getDefault(), "%.2f", payload.changeDueBs)
                    }",
                    style = com.amaxonia.pos.ui.theme.PosTextStyles.amountSecondary,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}

@Composable
private fun SummaryLine(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        AdaptiveAmountText(
            text = value,
            baseStyle =
                MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.End,
                ),
            color = MaterialTheme.colorScheme.onSurface,
            minFontSizeSp = 12f,
            modifier = Modifier.weight(1f, fill = false).padding(start = 12.dp),
        )
    }
}

@Composable
private fun SecondaryReceiptActions(
    isPrinting: Boolean,
    isSendingReceiptEmail: Boolean,
    onPrintReceipt: () -> Unit,
    onSendReceiptEmail: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedButton(
            onClick = onPrintReceipt,
            enabled = !isPrinting,
            modifier =
                Modifier
                    .weight(1f)
                    .height(52.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
            shape = MaterialTheme.shapes.medium,
        ) {
            Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                if (isPrinting) "Imprimiendo…" else "Imprimir",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        FilledTonalButton(
            onClick = onSendReceiptEmail,
            enabled = !isSendingReceiptEmail,
            modifier =
                Modifier
                    .weight(1f)
                    .height(52.dp),
            shape = MaterialTheme.shapes.medium,
        ) {
            Icon(Icons.Default.Email, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                if (isSendingReceiptEmail) "Enviando…" else "Enviar recibo",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun NextOrderButton(onNextOrder: () -> Unit) {
    Button(
        onClick = onNextOrder,
        modifier =
            Modifier
                .fillMaxWidth()
                .height(60.dp),
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
        shape = MaterialTheme.shapes.medium,
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp, pressedElevation = 4.dp),
    ) {
        Text(
            "Nueva orden",
            color = MaterialTheme.colorScheme.onPrimary,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
        )
        Spacer(modifier = Modifier.width(8.dp))
        Icon(
            Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimary,
        )
    }
}
