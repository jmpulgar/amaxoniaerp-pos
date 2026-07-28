package com.amaxonia.pos.ui.payment

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.amaxonia.pos.core.device.DeviceClass
import com.amaxonia.pos.domain.model.money.Money
import com.amaxonia.pos.ui.common.DependencyContainer
import com.amaxonia.pos.ui.common.LocalDeviceClass
import com.amaxonia.pos.ui.common.components.PosFeedbackCard
import com.amaxonia.pos.ui.common.components.PosStatusBadge
import com.amaxonia.pos.ui.common.components.PosVisualTone
import com.amaxonia.pos.ui.common.injectedViewModel
import com.amaxonia.pos.ui.common.isLandscape
import com.amaxonia.pos.ui.theme.PosTextStyles
import kotlinx.coroutines.launch

private const val RING_INITIAL_SCALE = 0.6f
private const val RING_INITIAL_ALPHA = 0.5f
private const val RING_TARGET_SCALE = 1.6f
private const val RING_ANIMATION_DURATION_MS = 600

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

    var visible by remember { mutableStateOf(false) }
    val scale = remember { Animatable(0.85f) }
    val ringScale = remember { Animatable(RING_INITIAL_SCALE) }
    val ringAlpha = remember { Animatable(RING_INITIAL_ALPHA) }
    var isPrinting by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val changeDue = payload?.changeDue ?: 0.0
    val paymentMethodsLabel = payload?.paymentMethodsLabel.orEmpty()
    val codFactura = payload?.codFactura.orEmpty()
    val receiptPrintMessage = payload?.receiptPrintMessage.orEmpty()
    val isMultiCurrency = payload?.isMultiCurrency == true
    val abrMonedaSecundaria = payload?.abrMonedaSecundaria.orEmpty()
    val totalBs = payload?.totalBs ?: 0.0
    val changeDueBs = payload?.changeDueBs ?: 0.0
    val isSendingReceiptEmail = uiState.isSendingReceiptEmail
    val feError = payload?.feError.orEmpty()
    val tableSessionClosed = payload?.tableSessionClosed == true

    LaunchedEffect(Unit) {
        visible = true
        scale.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing),
        )
    }

    LaunchedEffect(Unit) {
        ringScale.animateTo(
            RING_TARGET_SCALE,
            animationSpec = tween(durationMillis = RING_ANIMATION_DURATION_MS, easing = FastOutSlowInEasing),
        )
    }

    LaunchedEffect(Unit) {
        ringAlpha.animateTo(0f, animationSpec = tween(durationMillis = RING_ANIMATION_DURATION_MS))
    }

    LaunchedEffect(uiState.errorMessage) {
        val msg = uiState.errorMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
        }
    }

    LaunchedEffect(receiptPrintMessage) {
        if (receiptPrintMessage.isNotBlank()) {
            snackbarHostState.showSnackbar(receiptPrintMessage)
        }
    }

    BackHandler(enabled = true) {
        scope.launch {
            snackbarHostState.showSnackbar(
                message = "Usa Nueva orden para finalizar y limpiar el estado de venta",
            )
        }
    }

    val isTabletLandscape = LocalDeviceClass.current == DeviceClass.TABLET && isLandscape()

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.TopCenter) {
            Column(
                modifier =
                    Modifier
                        .fillMaxHeight()
                        .then(if (isTabletLandscape) Modifier.widthIn(max = 560.dp) else Modifier.fillMaxWidth())
                        .background(MaterialTheme.colorScheme.background)
                        .padding(horizontal = 24.dp, vertical = 16.dp)
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
                        enter = fadeIn(animationSpec = tween(180)) + scaleIn(initialScale = 0.92f, animationSpec = tween(220)),
                    ) {
                        ElevatedCard(
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.extraLarge,
                            colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 6.dp),
                        ) {
                            if (uiState.isLoading || payload == null) {
                                Column(
                                    modifier =
                                        Modifier
                                            .padding(24.dp)
                                            .fillMaxWidth()
                                            .graphicsLayer {
                                                scaleX = scale.value
                                                scaleY = scale.value
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
                            } else {
                                Column(
                                    modifier =
                                        Modifier
                                            .padding(28.dp)
                                            .graphicsLayer {
                                                scaleX = scale.value
                                                scaleY = scale.value
                                            },
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Box(
                                            modifier =
                                                Modifier
                                                    .size(92.dp)
                                                    .graphicsLayer {
                                                        scaleX = ringScale.value
                                                        scaleY = ringScale.value
                                                        alpha = ringAlpha.value
                                                    }.background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                                        )
                                        Box(
                                            modifier =
                                                Modifier
                                                    .size(92.dp)
                                                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Icon(
                                                Icons.Default.Check,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                                modifier = Modifier.size(46.dp),
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(18.dp))

                                    Text(
                                        "Pago confirmado",
                                        style = MaterialTheme.typography.headlineSmall,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )

                                    Text(
                                        if (codFactura.isBlank()) "Factura generada correctamente" else "Factura: $codFactura",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(top = 8.dp),
                                    )

                                    if (feError.isNotBlank()) {
                                        Spacer(modifier = Modifier.height(16.dp))
                                        PosFeedbackCard(
                                            title = "Factura creada, confirmación fiscal pendiente",
                                            message = feError,
                                            tone = PosVisualTone.Warning,
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(18.dp))

                                    CompletionStatus(
                                        codFactura = codFactura,
                                        hasFiscalError = feError.isNotBlank(),
                                        tableSessionClosed = tableSessionClosed,
                                    )

                                    Spacer(modifier = Modifier.height(18.dp))

                                    Column(
                                        modifier =
                                            Modifier
                                                .fillMaxWidth()
                                                .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.large)
                                                .padding(16.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                    ) {
                                        Text(
                                            "Método de pago: ${paymentMethodsLabel.ifBlank { "N/A" }}",
                                            color = MaterialTheme.colorScheme.onSurface,
                                            fontWeight = FontWeight.Bold,
                                        )
                                        HorizontalDivider(
                                            color = MaterialTheme.colorScheme.outlineVariant,
                                            modifier = Modifier.padding(vertical = 12.dp),
                                        )
                                        if (isMultiCurrency && totalBs > 0.0) {
                                            Text(
                                                "Total en ${formatCurrencyLabel(abrMonedaSecundaria)} " +
                                                    String.format(java.util.Locale.getDefault(), "%.2f", totalBs),
                                                style = PosTextStyles.amountSecondary,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                            Spacer(modifier = Modifier.height(8.dp))
                                        }
                                        Text(
                                            "Cambio / Vuelto",
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        Text(
                                            "$ ${Money.format(Money.fromDouble(changeDue))}",
                                            style = PosTextStyles.totalDisplay,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                        if (isMultiCurrency && changeDueBs > 0.0) {
                                            Text(
                                                "${formatCurrencyLabel(abrMonedaSecundaria)} ${
                                                    String.format(java.util.Locale.getDefault(), "%.2f", changeDueBs)
                                                }",
                                                style = PosTextStyles.amountSecondary,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(24.dp))

                                    Button(
                                        onClick = { viewModel.sendReceiptEmail() },
                                        enabled = !isSendingReceiptEmail,
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                                        shape = MaterialTheme.shapes.medium,
                                    ) {
                                        Icon(Icons.Default.Email, contentDescription = null)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            if (isSendingReceiptEmail) "Enviando…" else "Enviar recibo",
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            fontWeight = FontWeight.Bold,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedButton(
                    onClick = {
                        if (transactionId.isBlank()) {
                            scope.launch {
                                snackbarHostState.showSnackbar("No hay transaccion para imprimir")
                            }
                            return@OutlinedButton
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
                    enabled = !isPrinting,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Icon(Icons.Default.Print, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (isPrinting) "Imprimiendo…" else "Imprimir recibo")
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = onNextOrder,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Text("Nueva orden", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            }
        }
    }
}

@Composable
private fun CompletionStatus(
    codFactura: String,
    hasFiscalError: Boolean,
    tableSessionClosed: Boolean,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.large)
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = "Resumen del cierre",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        PosStatusBadge(
            label = "Pago aprobado",
            tone = PosVisualTone.Success,
            icon = Icons.Default.Check,
        )
        PosStatusBadge(
            label =
                when {
                    hasFiscalError -> "Confirmación fiscal pendiente"
                    codFactura.isBlank() -> "Factura registrada"
                    else -> "Factura $codFactura confirmada"
                },
            tone = if (hasFiscalError) PosVisualTone.Warning else PosVisualTone.Success,
            icon = if (hasFiscalError) Icons.Default.Warning else Icons.AutoMirrored.Filled.ReceiptLong,
        )
        PosStatusBadge(
            label = if (tableSessionClosed) "Mesa cerrada y disponible" else "Mesa con saldo o consumo pendiente",
            tone = if (tableSessionClosed) PosVisualTone.Success else PosVisualTone.Info,
            icon = Icons.Default.TableRestaurant,
        )
    }
}
