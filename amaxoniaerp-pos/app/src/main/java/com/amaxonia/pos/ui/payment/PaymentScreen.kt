@file:Suppress("CyclomaticComplexMethod", "LongMethod", "MagicNumber", "LongParameterList")

package com.amaxonia.pos.ui.payment

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AddCard
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.filled.Wallet
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.amaxonia.pos.core.logging.SafeLog
import com.amaxonia.pos.domain.model.SaleFinancialSnapshot
import com.amaxonia.pos.domain.model.money.Money
import com.amaxonia.pos.domain.model.payment.FormaPago
import com.amaxonia.pos.domain.model.payment.PaymentSuccessPayload
import com.amaxonia.pos.domain.usecase.payment.LoadPaymentContextUseCase
import com.amaxonia.pos.domain.usecase.payment.LoadPaymentCountryUseCase
import com.amaxonia.pos.ui.common.DependencyContainer
import com.amaxonia.pos.ui.common.components.AdaptiveAmountText
import com.amaxonia.pos.ui.common.components.Keypad
import com.amaxonia.pos.ui.common.components.KeypadDisplay
import com.amaxonia.pos.ui.common.components.KeypadKey
import com.amaxonia.pos.ui.common.components.PosEmptyState
import com.amaxonia.pos.ui.common.components.PosFeedbackCard
import com.amaxonia.pos.ui.common.components.PosLoadingState
import com.amaxonia.pos.ui.common.components.PosVisualTone
import com.amaxonia.pos.ui.common.injectedViewModel
import com.amaxonia.pos.ui.common.isLandscape
import com.amaxonia.pos.ui.theme.PosPalette
import com.amaxonia.pos.ui.theme.PosTextStyles
import com.amaxonia.pos.ui.theme.paymentMethodColor
import kotlinx.coroutines.delay

private const val SECONDARY_CURRENCY_LABEL = "Bs."
private val COMPACT_WIDTH_THRESHOLD = 600.dp
private val COMFORTABLE_HEIGHT_THRESHOLD = 600.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaymentScreen(
    totalAmount: Double,
    onBack: () -> Unit,
    onPaymentSuccess: (PaymentSuccessPayload) -> Unit,
    onNavigateToApertura: () -> Unit = onBack,
) {
    // Instancia del ViewModel usando inyección de dependencias
    val viewModel =
        injectedViewModel {
            PaymentViewModel(
                loadPaymentContext =
                    LoadPaymentContextUseCase(
                        DependencyContainer.cajaRepository,
                        DependencyContainer.formaPagoRepository,
                    ),
                loadPaymentCountry = LoadPaymentCountryUseCase(DependencyContainer.localStore),
                validatePayment = DependencyContainer.validatePaymentUseCase,
                buildPaymentDetails = DependencyContainer.buildPaymentDetailsUseCase,
                paymentOperation = DependencyContainer.paymentOperation,
                selectedClient = DependencyContainer.cartRepository.selectedClient,
                tableAccountPaymentReader = DependencyContainer.tableAccountPaymentHolder,
                cartFinancialSnapshot = DependencyContainer.cartRepository.financialSnapshot,
            )
        }

    val context = LocalContext.current

    LaunchedEffect(totalAmount) {
        viewModel.onAction(PaymentUiAction.SetTotalAmount(totalAmount))
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var processingSeconds by remember { mutableIntStateOf(0) }

    LaunchedEffect(state.isProcessingPayment) {
        if (!state.isProcessingPayment) {
            processingSeconds = 0
            return@LaunchedEffect
        }

        processingSeconds = 0
        while (true) {
            delay(1_000)
            processingSeconds += 1
        }
    }

    // Collect one-shot UI effects and launch the HKA POS app when requested.
    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is PaymentUiEffect.LaunchGateway -> {
                    val payload = effect.payload
                    val intent =
                        Intent().apply {
                            component = ComponentName(payload.packageName, payload.activityClassName)
                            putExtra("commandRapidPay", payload.encryptedCommand)
                            putExtra("colorBackgroundLoading", payload.backgroundColor)
                            putExtra("colorText", payload.textColor)
                            putExtra("messageRapidPay", payload.message)
                        }
                    SafeLog.d("PaymentScreen", "Launching external payment gateway")
                    try {
                        context.startActivity(intent)
                    } catch (e: ActivityNotFoundException) {
                        SafeLog.e("PaymentScreen", "Unable to launch external payment gateway", e)
                    }
                }
            }
        }
    }

    LaunchedEffect(state.receiptPrintMessage) {
        val message = state.receiptPrintMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.onAction(PaymentUiAction.ClearReceiptPrintMessage)
    }

    // Reaccionar al payload de éxito para navegar (reactivo al estado, no al callback)
    LaunchedEffect(state.successPayload) {
        val payload = state.successPayload ?: return@LaunchedEffect
        viewModel.onAction(PaymentUiAction.ClearSuccessPayload)
        onPaymentSuccess(payload)
    }

    val onAction: (PaymentUiAction) -> Unit = viewModel::onAction

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "Cobro",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            "Selecciona cómo pagar",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                colors =
                    TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                    ),
            )
        },
    ) { padding ->
        BoxWithConstraints(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .background(MaterialTheme.colorScheme.background),
        ) {
            when {
                state.isLoadingFormasPago ->
                    PosLoadingState(
                        message = "Cargando formas de pago…",
                        modifier = Modifier.fillMaxSize(),
                    )
                state.formasPagoError != null ->
                    Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                        PosFeedbackCard(
                            title = "Formas de pago no disponibles",
                            message = state.formasPagoError ?: "No se pudieron cargar las formas de pago",
                            tone = PosVisualTone.Error,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                isLandscape() -> PaymentLandscape(state = state, onAction = onAction)
                maxWidth < COMPACT_WIDTH_THRESHOLD ->
                    when (state.selectedMethod) {
                        PaymentMethod.CASH -> CashPaymentCompact(state = state, onAction = onAction, maxHeight = maxHeight)
                        PaymentMethod.NON_CASH -> NonCashPaymentCompact(state = state, onAction = onAction)
                    }
                else -> PaymentWide(state = state, onAction = onAction)
            }

            if (state.isProcessingPayment) {
                ProcessingPaymentOverlay(
                    elapsedSeconds = processingSeconds,
                    statusMessage = state.gatewayStatusMessage,
                )
            }

            val paymentError = state.paymentError
            if (paymentError != null) {
                val isMissingCajaSequence =
                    paymentError ==
                        com.amaxonia.pos.domain.usecase.payment.PaymentValidationFailure.MissingCajaSequence.message
                AlertDialog(
                    onDismissRequest = { onAction(PaymentUiAction.ClearPaymentError) },
                    confirmButton = {
                        if (isMissingCajaSequence) {
                            TextButton(onClick = {
                                onAction(PaymentUiAction.ClearPaymentError)
                                onNavigateToApertura()
                            }) {
                                Text("Aperturar caja")
                            }
                        } else {
                            TextButton(onClick = { onAction(PaymentUiAction.ClearPaymentError) }) {
                                Text("Entendido")
                            }
                        }
                    },
                    dismissButton = {
                        if (isMissingCajaSequence) {
                            TextButton(onClick = { onAction(PaymentUiAction.ClearPaymentError) }) {
                                Text("Cancelar")
                            }
                        }
                    },
                    title = { Text("Error al cobrar") },
                    text = { Text(paymentError) },
                )
            }

            val duplicate = state.duplicateInvoice
            if (duplicate != null) {
                AlertDialog(
                    onDismissRequest = { onAction(PaymentUiAction.DismissDuplicateInvoice) },
                    confirmButton = {
                        TextButton(onClick = {
                            onAction(PaymentUiAction.DismissDuplicateInvoice)
                            onBack()
                        }) {
                            Text("Reconciliar")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { onAction(PaymentUiAction.DismissDuplicateInvoice) }) {
                            Text("Revisión manual")
                        }
                    },
                    title = { Text("Factura duplicada") },
                    text = {
                        Column {
                            Text(duplicate.reason)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "Esta operación ya fue registrada en un intento anterior. " +
                                    "Verifica en el historial de facturas antes de volver a cobrar.",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    },
                )
            }
        }
    }
}

@Composable
internal fun PaymentHeader(
    state: PaymentState,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val heroStyle =
        if (compact) {
            MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold)
        } else {
            PosTextStyles.totalDisplay
        }
    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(horizontal = if (compact) 14.dp else 16.dp, vertical = if (compact) 10.dp else 14.dp)) {
            if (!compact) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ) {
                        Box(modifier = Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                            Icon(Icons.AutoMirrored.Filled.ReceiptLong, contentDescription = null, modifier = Modifier.size(22.dp))
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Total a pagar",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        HeroAmount(state = state, style = heroStyle)
                    }
                }
            } else {
                Text(
                    "Total a pagar",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(2.dp))
                HeroAmount(state = state, style = heroStyle)
            }

            Spacer(modifier = Modifier.height(if (compact) 10.dp else 12.dp))
            FinancialBreakdown(
                snapshot = state.financialSnapshot,
                totalFallback = state.totalAmountMoney,
                taxLabel = state.effectiveTaxLabel,
                isMultiCurrency = state.isMultiCurrency,
                tasa = state.tasa,
                compact = compact,
            )
        }
    }
}

@Composable
private fun HeroAmount(
    state: PaymentState,
    style: androidx.compose.ui.text.TextStyle,
) {
    AdaptiveAmountText(
        text = "$ ${state.totalAmountText}",
        baseStyle = style,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.fillMaxWidth(),
        options = com.amaxonia.pos.ui.common.components.AdaptiveAmountOptions(
            minFontSizeSp = 18f,
        ),
    )
    if (state.isMultiCurrency && state.totalAmountBsText.isNotBlank()) {
        Text(
            "$SECONDARY_CURRENCY_LABEL ${state.totalAmountBsText}",
            style = PosTextStyles.amountSecondary,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
internal fun FinancialBreakdown(
    snapshot: SaleFinancialSnapshot?,
    totalFallback: Money,
    taxLabel: String,
    isMultiCurrency: Boolean,
    tasa: Double,
    compact: Boolean = false,
) {
    val subtotal = snapshot?.subtotalGross ?: totalFallback.toDouble()
    val discount = snapshot?.itemDiscounts ?: 0.0
    val tax = snapshot?.tax ?: 0.0
    val total = snapshot?.total ?: totalFallback.toDouble()

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(horizontal = if (compact) 12.dp else 14.dp, vertical = if (compact) 8.dp else 10.dp)) {
            BreakdownRow(label = "Subtotal", amount = subtotal, compact = compact, isMultiCurrency = isMultiCurrency, tasa = tasa)
            BreakdownRow(
                label = "Descuento",
                amount = discount,
                compact = compact,
                isMultiCurrency = isMultiCurrency,
                tasa = tasa,
                emphasize = discount > 0.0,
            )
            BreakdownRow(label = taxLabel, amount = tax, compact = compact, isMultiCurrency = isMultiCurrency, tasa = tasa)
            Spacer(modifier = Modifier.height(4.dp))
            BreakdownRow(
                label = "Total",
                amount = total,
                compact = compact,
                emphasize = true,
                isMultiCurrency = isMultiCurrency,
                tasa = tasa,
            )
        }
    }
}

@Composable
private fun BreakdownRow(
    label: String,
    amount: Double,
    emphasize: Boolean = false,
    compact: Boolean = false,
    isMultiCurrency: Boolean = false,
    tasa: Double = 0.0,
) {
    val moneyAmount = Money.fromDouble(amount)
    val labelStyle =
        when {
            emphasize -> MaterialTheme.typography.titleSmall
            compact -> MaterialTheme.typography.bodySmall
            else -> MaterialTheme.typography.bodyMedium
        }
    val amountStyle =
        when {
            emphasize -> MaterialTheme.typography.titleLarge
            compact -> MaterialTheme.typography.titleSmall
            else -> MaterialTheme.typography.titleMedium
        }
    val externalAmountStyle =
        when {
            emphasize -> MaterialTheme.typography.titleMedium
            compact -> MaterialTheme.typography.titleSmall
            else -> MaterialTheme.typography.bodyMedium
        }
    Row(
        modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = if (compact) 20.dp else 24.dp).padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = labelStyle,
            color = if (emphasize) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (emphasize) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
        )
        Column(horizontalAlignment = Alignment.End) {
            AdaptiveAmountText(
                text = "$ ${Money.format(moneyAmount)}",
                baseStyle =
                    (if (emphasize) amountStyle else externalAmountStyle).copy(
                        fontWeight = if (emphasize) FontWeight.ExtraBold else FontWeight.Medium,
                    ),
                color = if (emphasize) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.widthIn(max = 220.dp),
                options = com.amaxonia.pos.ui.common.components.AdaptiveAmountOptions(
                    minFontSizeSp = 11f,
                ),
            )
            if (isMultiCurrency && tasa > 0.0 && amount > 0.0) {
                val converted = moneyAmount.times(java.math.BigDecimal.valueOf(tasa))
                Text(
                    text = "$SECONDARY_CURRENCY_LABEL ${Money.format(converted)}",
                    style = PosTextStyles.amountSecondary,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
internal fun PaymentMethodSelectorRow(
    selectedMethod: PaymentMethod,
    cashEnabled: Boolean,
    nonCashEnabled: Boolean,
    onSelect: (PaymentMethod) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        MethodCard(
            modifier = Modifier.weight(1f),
            title = "Efectivo",
            subtitle = "Billetes / monedas",
            icon = Icons.Default.Payments,
            selected = selectedMethod == PaymentMethod.CASH,
            enabled = cashEnabled,
            onClick = { onSelect(PaymentMethod.CASH) },
        )
        MethodCard(
            modifier = Modifier.weight(1f),
            title = "Tarjeta / Otro",
            subtitle = "TDD · TDC · Transferencia",
            icon = Icons.Default.AddCard,
            selected = selectedMethod == PaymentMethod.NON_CASH,
            enabled = nonCashEnabled,
            onClick = { onSelect(PaymentMethod.NON_CASH) },
        )
    }
}

@Composable
private fun MethodCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val borderColor =
        if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.outlineVariant
        }
    val containerColor =
        if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surface
        }
    val contentColor =
        if (selected) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurface
        }
    Card(
        modifier =
            modifier
                .height(72.dp)
                .defaultMinSize(minHeight = 64.dp)
                .clickable(
                    enabled = enabled,
                    interactionSource = interactionSource,
                    indication = ripple(),
                    onClick = onClick,
                ),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = if (pressed && enabled) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f) else containerColor,
            contentColor = contentColor,
        ),
        border =
            BorderStroke(
                width = if (selected) 2.dp else 1.dp,
                color = borderColor,
            ),
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(
                            if (selected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            },
                        ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = contentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (selected) contentColor else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (selected) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(24.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}

private fun keypadHeightFor(maxHeight: Dp): Dp =
    (maxHeight.value * 0.40f).coerceIn(196f, 300f).dp

@Composable
internal fun PaymentWide(
    state: PaymentState,
    onAction: (PaymentUiAction) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().widthIn(max = 980.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        PaymentHeader(
            state = state,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        )
        PaymentMethodSelectorRow(
            selectedMethod = state.selectedMethod,
            cashEnabled = state.formasPagoEfectivo.isNotEmpty(),
            nonCashEnabled = state.formasPagoTarjetaOtro.isNotEmpty() || state.isLoadingFormasPago,
            onSelect = { onAction(PaymentUiAction.SelectMethod(it)) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        )
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                when (state.selectedMethod) {
                    PaymentMethod.CASH -> CashAmountPanel(state = state, onAction = onAction, modifier = Modifier.fillMaxWidth())
                    PaymentMethod.NON_CASH -> NonCashSummaryPanel(state = state, modifier = Modifier.fillMaxWidth())
                }
            }
            Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                when (state.selectedMethod) {
                    PaymentMethod.CASH ->
                        CashKeypadBlock(
                            state = state,
                            onAction = onAction,
                            fillRemaining = true,
                            modifier = Modifier.fillMaxHeight(),
                        )
                    PaymentMethod.NON_CASH ->
                        NonCashListPanel(
                            state = state,
                            onAction = onAction,
                            fillRemaining = true,
                            modifier = Modifier.fillMaxHeight(),
                        )
                }
            }
        }
    }
}

@Composable
internal fun PaymentLandscape(
    state: PaymentState,
    onAction: (PaymentUiAction) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxSize().padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(
            modifier = Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PaymentHeader(state = state, compact = true, modifier = Modifier.fillMaxWidth())
            PaymentMethodSelectorRow(
                selectedMethod = state.selectedMethod,
                cashEnabled = state.formasPagoEfectivo.isNotEmpty(),
                nonCashEnabled = state.formasPagoTarjetaOtro.isNotEmpty() || state.isLoadingFormasPago,
                onSelect = { onAction(PaymentUiAction.SelectMethod(it)) },
                modifier = Modifier.fillMaxWidth(),
            )
            when (state.selectedMethod) {
                PaymentMethod.CASH -> CashAmountPanel(state = state, onAction = onAction, modifier = Modifier.fillMaxWidth())
                PaymentMethod.NON_CASH -> NonCashSummaryPanel(state = state, modifier = Modifier.fillMaxWidth())
            }
            Spacer(modifier = Modifier.height(4.dp))
        }
        Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
            when (state.selectedMethod) {
                PaymentMethod.CASH ->
                    CashKeypadBlock(
                        state = state,
                        onAction = onAction,
                        fillRemaining = true,
                        modifier = Modifier.fillMaxHeight(),
                    )
                PaymentMethod.NON_CASH ->
                    NonCashListPanel(
                        state = state,
                        onAction = onAction,
                        fillRemaining = true,
                        modifier = Modifier.fillMaxHeight(),
                    )
            }
        }
    }
}

@Composable
internal fun CashPaymentCompact(
    state: PaymentState,
    onAction: (PaymentUiAction) -> Unit,
    maxHeight: Dp,
) {
    val keypadHeight = keypadHeightFor(maxHeight)
    if (maxHeight >= COMFORTABLE_HEIGHT_THRESHOLD) {
        Column(modifier = Modifier.fillMaxSize()) {
            PaymentHeader(
                state = state,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            )
            PaymentMethodSelectorRow(
                selectedMethod = state.selectedMethod,
                cashEnabled = state.formasPagoEfectivo.isNotEmpty(),
                nonCashEnabled = state.formasPagoTarjetaOtro.isNotEmpty() || state.isLoadingFormasPago,
                onSelect = { onAction(PaymentUiAction.SelectMethod(it)) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            )
            CashAmountPanel(
                state = state,
                onAction = onAction,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            )
            Spacer(modifier = Modifier.weight(1f))
            CashKeypadBlock(
                state = state,
                onAction = onAction,
                fillRemaining = false,
                keypadHeight = keypadHeight,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 12.dp),
            )
        }
        return
    }

    val scrollState = rememberScrollState()
    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(scrollState),
        ) {
            PaymentHeader(
                state = state,
                compact = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
            PaymentMethodSelectorRow(
                selectedMethod = state.selectedMethod,
                cashEnabled = state.formasPagoEfectivo.isNotEmpty(),
                nonCashEnabled = state.formasPagoTarjetaOtro.isNotEmpty() || state.isLoadingFormasPago,
                onSelect = { onAction(PaymentUiAction.SelectMethod(it)) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            )
            CashAmountPanel(
                state = state,
                onAction = onAction,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
        CashKeypadBlock(
            state = state,
            onAction = onAction,
            fillRemaining = false,
            keypadHeight = keypadHeight,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 12.dp),
        )
    }
}

@Composable
internal fun NonCashPaymentCompact(
    state: PaymentState,
    onAction: (PaymentUiAction) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        PaymentHeader(
            state = state,
            compact = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        )
        PaymentMethodSelectorRow(
            selectedMethod = state.selectedMethod,
            cashEnabled = state.formasPagoEfectivo.isNotEmpty(),
            nonCashEnabled = state.formasPagoTarjetaOtro.isNotEmpty() || state.isLoadingFormasPago,
            onSelect = { onAction(PaymentUiAction.SelectMethod(it)) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        )
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            NonCashSummaryPanel(state = state, modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(12.dp))
            NonCashListPanel(state = state, onAction = onAction, fillRemaining = true, modifier = Modifier.weight(1f).fillMaxWidth())
        }
    }
}

@Composable
private fun CashAmountPanel(
    state: PaymentState,
    onAction: (PaymentUiAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val missingCashAmountText =
        Money.format(
            (state.totalAmountMoney - state.assignedAmountMoney)
                .coerceAtLeastZero(),
        )
    val isInsufficient = state.showInsufficientReminder && !state.isPaymentEnough
    val isPositive = state.tenderedAmountMoney > Money.ZERO && state.isPaymentEnough

    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Monto recibido",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
            FilledTonalButton(
                onClick = { onAction(PaymentUiAction.SetExactAmount) },
                modifier = Modifier.height(44.dp),
                contentPadding = ButtonDefaults.ContentPadding,
            ) {
                Icon(Icons.Default.PointOfSale, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Monto exacto", maxLines = 1)
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        KeypadDisplay(
            label = "Recibido",
            amountText = state.tenderedAmountText,
            isError = isInsufficient,
            isPositive = isPositive,
            secondaryLine =
                (state.isMultiCurrency && state.tenderedAmountBsText.isNotBlank()).let { hasSecondary ->
                    if (hasSecondary) "$SECONDARY_CURRENCY_LABEL ${state.tenderedAmountBsText}" else null
                },
        ) {
            if (state.nonCashAssignedMoney > Money.ZERO) {
                Text(
                    "Otras formas: $ ${state.nonCashAssignedText}",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            AnimatedVisibility(
                visible = isInsufficient,
                enter = fadeIn() + slideInVertically(initialOffsetY = { it / 2 }),
                exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 2 }),
            ) {
                Text(
                    text =
                        buildString {
                            append("Faltan $ $missingCashAmountText para completar el pago")
                            if (state.isMultiCurrency && state.missingCashBsText.isNotBlank()) {
                                append(" ($SECONDARY_CURRENCY_LABEL ${state.missingCashBsText})")
                            }
                        },
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    modifier =
                        Modifier
                            .padding(top = 10.dp)
                            .fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun CashKeypadBlock(
    state: PaymentState,
    onAction: (PaymentUiAction) -> Unit,
    fillRemaining: Boolean,
    modifier: Modifier = Modifier,
    keypadHeight: Dp = 280.dp,
) {
    val warningScale by animateFloatAsState(
        targetValue = if (state.showInsufficientReminder && !state.isPaymentEnough) 1.03f else 1f,
        label = "cashWarningScale",
    )
    val isInsufficient = state.showInsufficientReminder && !state.isPaymentEnough

    Column(modifier = modifier) {
        Keypad(
            onKey = { key -> onAction(PaymentUiAction.KeyPadInput(key)) },
            modifier = if (fillRemaining) Modifier.weight(1f) else Modifier,
            height = if (fillRemaining) null else keypadHeight,
            actionColumn = {
                KeypadKey(
                    text = "Borrar",
                    modifier = Modifier.fillMaxHeight(),
                    icon = Icons.AutoMirrored.Filled.Backspace,
                ) { onAction(PaymentUiAction.KeyPadInput("BACK")) }
            },
        )
        Spacer(modifier = Modifier.height(12.dp))
        PrimaryCtaButton(
            state = state,
            warningScale = warningScale,
            isInsufficient = isInsufficient,
            onClick = { onAction(PaymentUiAction.ProcessPayment) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
internal fun PrimaryCtaButton(
    state: PaymentState,
    warningScale: Float,
    isInsufficient: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val heroColor =
        if (isInsufficient) {
            MaterialTheme.colorScheme.error
        } else {
            MaterialTheme.colorScheme.primary
        }
    val onHeroColor = MaterialTheme.colorScheme.onPrimary
    Button(
        onClick = onClick,
        enabled = !state.isProcessingPayment,
        modifier =
            modifier
                .height(60.dp)
                .defaultMinSize(minHeight = 56.dp),
        shape = MaterialTheme.shapes.medium,
        colors =
            ButtonDefaults.buttonColors(
                containerColor = heroColor,
                contentColor = onHeroColor,
            ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp, pressedElevation = 4.dp),
    ) {
        if (state.isProcessingPayment) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                strokeWidth = 3.dp,
                color = onHeroColor,
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                "Procesando…",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
        } else {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = onHeroColor,
                modifier = Modifier.size(((22 * warningScale).dp)),
            )
            Spacer(modifier = Modifier.width(10.dp))
            AdaptiveAmountText(
                text = "Cobrar $ ${state.totalAmountText}",
                baseStyle =
                    MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.ExtraBold,
                        textAlign = TextAlign.Center,
                    ),
                color = onHeroColor,
                modifier = Modifier.weight(1f),
                options = com.amaxonia.pos.ui.common.components.AdaptiveAmountOptions(
                    minFontSizeSp = 14f,
                ),
            )
        }
    }
}

@Composable
private fun NonCashSummaryPanel(
    state: PaymentState,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            "Resumen del cobro",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PaymentSummaryLine(
                    label = "Asignado",
                    value = "$ ${state.nonCashAssignedText}",
                    secondary =
                        state.nonCashAssignedBsText
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
        }
        AnimatedVisibility(
            visible = state.showInsufficientReminder && !state.isPaymentEnough,
            enter = fadeIn() + slideInVertically(initialOffsetY = { it / 2 }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 2 }),
        ) {
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
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun NonCashListPanel(
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

        Text(
            "Formas disponibles",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
        Spacer(modifier = Modifier.height(8.dp))

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

@Composable
fun NonCashRow(
    forma: FormaPago,
    value: String,
    pendingAmount: String,
    canUseCredit: Boolean,
    onValueChange: (String) -> Unit,
    onUseExactAmount: () -> Unit,
    narrow: Boolean = false,
) {
    val isCxc = forma.siglas?.trim()?.equals("CXC", ignoreCase = true) == true
    val isPending = pendingAmount.isNotBlank() && pendingAmount != "0.00"
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder(),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PaymentMethodIcon(forma = forma)
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        forma.descripcion ?: "Forma de pago",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val subtitle =
                        when {
                            isCxc && canUseCredit -> "Cuenta por cobrar · crédito"
                            isCxc -> "Cuenta por cobrar"
                            else -> forma.siglas.orEmpty()
                        }
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (narrow) {
                OutlinedTextField(
                    value = value,
                    onValueChange = onValueChange,
                    label = { Text("Monto asignado") },
                    prefix = { Text("$ ") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                FilledTonalButton(
                    onClick = onUseExactAmount,
                    enabled = isPending,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Completar \$$pendingAmount",
                        maxLines = 1,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
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
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    FilledTonalButton(
                        onClick = onUseExactAmount,
                        enabled = isPending,
                        modifier = Modifier.height(52.dp).defaultMinSize(minWidth = 132.dp),
                        shape = MaterialTheme.shapes.medium,
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
            }
        }
    }
}

@Composable
private fun PaymentSummaryLine(
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
                options = com.amaxonia.pos.ui.common.components.AdaptiveAmountOptions(
                    minFontSizeSp = 11f,
                    maxLines = 1,
                ),
            )
            secondary?.let {
                Text(
                    text = it,
                    style = PosTextStyles.amountSecondary,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun PaymentMethodIcon(forma: FormaPago) {
    val decodedImage = remember(forma.imagen) { decodeBase64Image(forma.imagen) }
    val fallbackColor = remember(forma.siglas) { paymentMethodColor(forma.siglas) }

    Box(
        modifier =
            Modifier
                .size(40.dp)
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
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

private fun decodeBase64Image(imageData: String?): ImageBitmap? {
    if (imageData.isNullOrBlank()) return null
    return try {
        val normalized = imageData.substringAfter("base64,", imageData).trim()
        if (normalized.isBlank()) return null
        val bytes = Base64.decode(normalized, Base64.DEFAULT)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
    } catch (_: IllegalArgumentException) {
        null
    }
}

private fun fallbackIconForSigla(sigla: String?): ImageVector =
    when (sigla?.uppercase()) {
        "TDC", "TDD" -> Icons.Default.CreditCard
        "TR", "DB", "CK", "BANK" -> Icons.Default.AccountBalance
        "CXC" -> Icons.Default.Wallet
        else -> Icons.Default.Wallet
    }

@Composable
private fun ProcessingPaymentOverlay(
    elapsedSeconds: Int,
    statusMessage: String?,
) {
    val estimatedSeconds = 30
    val progress = (elapsedSeconds / estimatedSeconds.toFloat()).coerceIn(0.08f, 0.94f)
    val secondsLeft = (estimatedSeconds - elapsedSeconds).coerceAtLeast(3)
    val stageTitle =
        when {
            elapsedSeconds < 4 -> "Preparando la factura"
            elapsedSeconds < 10 -> "Conectando con facturación electrónica"
            elapsedSeconds < 24 -> "Esperando autorización de la DGI"
            else -> "Últimos segundos de validación"
        }
    val stageSubtitle =
        statusMessage?.takeIf { it.isNotBlank() } ?: when {
            elapsedSeconds < 4 -> "Validando pago, caja e inventario..."
            elapsedSeconds < 10 -> "Enviando el documento al proveedor fiscal..."
            elapsedSeconds < 24 -> "TheFactory está procesando el CUFE y el QR..."
            else -> "La respuesta está tardando un poco más de lo normal, seguimos esperando."
        }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(PosPalette.FixedBlack.copy(alpha = 0.36f))
                .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge,
            colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 10.dp),
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier =
                        Modifier
                            .size(74.dp)
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
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                Text(
                    text = "Procesando cobro",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stageTitle,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 6.dp),
                )
                Text(
                    text = stageSubtitle,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )

                Spacer(modifier = Modifier.height(18.dp))

                LinearProgressIndicator(
                    progress = { progress },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(50)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text =
                        if (elapsedSeconds < estimatedSeconds) {
                            "Tiempo estimado restante: ${secondsLeft}s"
                        } else {
                            "Está tomando más de lo habitual, no cierres la pantalla."
                        },
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(modifier = Modifier.height(12.dp))

                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Text(
                        text = "La venta se está registrando. Evita tocar atrás o cerrar la app.",
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
    }
}
