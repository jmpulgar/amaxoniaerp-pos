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
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Wallet
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.amaxonia.pos.core.logging.SafeLog
import com.amaxonia.pos.domain.model.money.Money
import com.amaxonia.pos.domain.model.payment.FormaPago
import com.amaxonia.pos.domain.model.payment.PaymentSuccessPayload
import com.amaxonia.pos.domain.usecase.payment.LoadPaymentContextUseCase
import com.amaxonia.pos.domain.usecase.payment.LoadPaymentCountryUseCase
import com.amaxonia.pos.ui.common.DependencyContainer
import com.amaxonia.pos.ui.common.injectedViewModel
import com.amaxonia.pos.ui.theme.PaymentMethodColors
import com.amaxonia.pos.ui.theme.PosPalette
import kotlinx.coroutines.delay

private const val SECONDARY_CURRENCY_LABEL = "Bs."

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaymentScreen(
    totalAmount: Double,
    onBack: () -> Unit,
    onPaymentSuccess: (PaymentSuccessPayload) -> Unit,
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
                executePaymentFlow = DependencyContainer.executePaymentFlowUseCase,
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

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text("Método de Pago", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Atrás", tint = MaterialTheme.colorScheme.primary)
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .background(MaterialTheme.colorScheme.background),
        ) {
            // Total a Pagar
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Total a pagar :", fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        "$ ${state.totalAmountText}",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    if (state.isMultiCurrency && state.totalAmountBsText.isNotBlank()) {
                        Text(
                            "$SECONDARY_CURRENCY_LABEL ${state.totalAmountBsText}",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            // Tabs (Efectivo / Tarjeta)
            Row(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)) {
                PaymentTab(
                    title = "Efectivo",
                    isSelected = state.selectedMethod == PaymentMethod.CASH,
                    onClick = { viewModel.onAction(PaymentUiAction.SelectMethod(PaymentMethod.CASH)) },
                )
                PaymentTab(
                    title = "Tarjeta / Otro",
                    isSelected = state.selectedMethod == PaymentMethod.NON_CASH,
                    onClick = { viewModel.onAction(PaymentUiAction.SelectMethod(PaymentMethod.NON_CASH)) },
                )
            }

            if (state.isLoadingFormasPago) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            } else if (state.formasPagoError != null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = state.formasPagoError ?: "No se pudieron cargar las formas de pago",
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 14.sp,
                    )
                }
            } else if (state.selectedMethod == PaymentMethod.CASH) {
                if (state.formasPagoEfectivo.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("¡No disponible!", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.outline)
                    }
                } else {
                    CashPaymentContent(state, viewModel)
                }
            } else {
                if (state.formasPagoTarjetaOtro.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("¡No disponible!", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.outline)
                    }
                } else {
                    NonCashPaymentContent(state, viewModel)
                }
            }
        }

        if (state.isProcessingPayment) {
            ProcessingPaymentOverlay(
                elapsedSeconds = processingSeconds,
                statusMessage = state.gatewayStatusMessage,
            )
        }

        val paymentError = state.paymentError
        if (paymentError != null) {
            AlertDialog(
                onDismissRequest = { viewModel.onAction(PaymentUiAction.ClearPaymentError) },
                confirmButton = {
                    TextButton(onClick = { viewModel.onAction(PaymentUiAction.ClearPaymentError) }) {
                        Text("Entendido")
                    }
                },
                title = { Text("Error al cobrar") },
                text = { Text(paymentError) },
            )
        }

        val duplicate = state.duplicateInvoice
        if (duplicate != null) {
            AlertDialog(
                onDismissRequest = { viewModel.onAction(PaymentUiAction.DismissDuplicateInvoice) },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.onAction(PaymentUiAction.DismissDuplicateInvoice)
                        onBack()
                    }) {
                        Text("Reconciliar")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.onAction(PaymentUiAction.DismissDuplicateInvoice) }) {
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
            shape = RoundedCornerShape(28.dp),
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
                    shape = RoundedCornerShape(16.dp),
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

@Composable
fun RowScope.PaymentTab(
    title: String,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .weight(1f)
                .clickable(onClick = onClick)
                .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = title,
            fontSize = 18.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (isSelected) {
            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.primary, thickness = 3.dp, modifier = Modifier.width(60.dp))
        }
    }
}

@Composable
fun CashPaymentContent(
    state: PaymentState,
    viewModel: PaymentViewModel,
) {
    val missingCashAmountText =
        Money.format(
            (state.totalAmountMoney - state.assignedAmountMoney)
                .coerceAtLeastZero(),
        )

    val warningScale by animateFloatAsState(
        targetValue = if (state.showInsufficientReminder && !state.isPaymentEnough) 1.03f else 1f,
        label = "cashWarningScale",
    )

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        // Botón Monto Exacto
        Button(
            onClick = { viewModel.onAction(PaymentUiAction.SetExactAmount) },
            modifier = Modifier.fillMaxWidth().height(50.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(8.dp),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp),
        ) {
            Icon(Icons.Default.Wallet, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(8.dp))
            Text("MONTO EXACTO", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Display del input
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                    .border(
                        width = if (state.showInsufficientReminder && !state.isPaymentEnough) 2.dp else 0.dp,
                        color = MaterialTheme.colorScheme.error,
                        shape = RoundedCornerShape(8.dp),
                    ).padding(16.dp),
        ) {
            Text("Monto recibido", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(8.dp))
            Row {
                Text("$ ", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Text(state.tenderedAmountText, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }
            if (state.isMultiCurrency && state.tenderedAmountBsText.isNotBlank()) {
                Text(
                    "$SECONDARY_CURRENCY_LABEL ${state.tenderedAmountBsText}",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (state.nonCashAssignedMoney > Money.ZERO) {
                Text(
                    "Otras formas: $ ${state.nonCashAssignedText}",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            AnimatedVisibility(
                visible = state.showInsufficientReminder && !state.isPaymentEnough,
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

        Spacer(modifier = Modifier.weight(1f))

        // Teclado Numérico
        Column(modifier = Modifier.fillMaxWidth()) {
            val keys =
                listOf(
                    listOf("1", "2", "3"),
                    listOf("4", "5", "6"),
                    listOf("7", "8", "9"),
                    listOf("C", "0", "."),
                )

            Row(modifier = Modifier.fillMaxWidth().height(350.dp)) {
                // Números
                Column(modifier = Modifier.weight(3f)) {
                    keys.forEach { rowKeys ->
                        Row(modifier = Modifier.weight(1f)) {
                            rowKeys.forEach { key ->
                                KeypadButton(key, Modifier.weight(1f)) {
                                    viewModel.onAction(PaymentUiAction.KeyPadInput(key))
                                }
                            }
                        }
                    }
                }

                // Columna Acción
                Column(modifier = Modifier.weight(1f)) {
                    // Backspace
                    Box(
                        modifier =
                            Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .padding(4.dp)
                                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                                .clickable { viewModel.onAction(PaymentUiAction.KeyPadInput("BACK")) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Backspace, contentDescription = "Borrar", tint = MaterialTheme.colorScheme.primary)
                    }

                    // Botón COBRAR / ENTER
                    Button(
                        onClick = {
                            viewModel.onAction(PaymentUiAction.ProcessPayment)
                        },
                        enabled = !state.isProcessingPayment,
                        modifier =
                            Modifier
                                .weight(3f)
                                .fillMaxWidth()
                                .padding(4.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors =
                            ButtonDefaults.buttonColors(
                                containerColor =
                                    if (state.showInsufficientReminder &&
                                        !state.isPaymentEnough
                                    ) {
                                        MaterialTheme.colorScheme.error
                                    } else {
                                        MaterialTheme.colorScheme.primary
                                    },
                            ),
                    ) {
                        if (state.isProcessingPayment) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(28.dp),
                                strokeWidth = 3.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Cobrar",
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size((34 * warningScale).dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun KeypadButton(
    text: String,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            modifier
                .fillMaxHeight()
                .padding(4.dp)
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, fontSize = 24.sp, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
fun NonCashPaymentContent(
    state: PaymentState,
    viewModel: PaymentViewModel,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(16.dp),
    ) {
        Text(
            "Formas disponibles",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.height(12.dp))

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(state.formasPagoTarjetaOtro, key = { it.idFormaPago }) { forma ->
                NonCashRow(
                    forma = forma,
                    value = state.nonCashAmountsInput[forma.idFormaPago].orEmpty(),
                    onValueChange = {
                        viewModel.onAction(PaymentUiAction.SetNonCashAmount(forma.idFormaPago, it))
                    },
                    onUseExactAmount = {
                        viewModel.onAction(PaymentUiAction.SetExactNonCashAmount(forma.idFormaPago))
                    },
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
        Text(
            buildString {
                append("Asignado: $ ${state.nonCashAssignedText}")
                if (state.isMultiCurrency && state.nonCashAssignedBsText.isNotBlank()) {
                    append(" ($SECONDARY_CURRENCY_LABEL ${state.nonCashAssignedBsText})")
                }
            },
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            buildString {
                append("Pendiente: $ ${state.nonCashPendingText}")
                if (state.isMultiCurrency && state.nonCashPendingBsText.isNotBlank()) {
                    append(" ($SECONDARY_CURRENCY_LABEL ${state.nonCashPendingBsText})")
                }
            },
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(12.dp))
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
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }

        Button(
            onClick = { viewModel.onAction(PaymentUiAction.ProcessPayment) },
            enabled = !state.isProcessingPayment,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            colors =
                ButtonDefaults.buttonColors(
                    containerColor = if (state.isPaymentEnough) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                ),
            shape = RoundedCornerShape(10.dp),
        ) {
            if (state.isProcessingPayment) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text("PROCESANDO...", fontWeight = FontWeight.Bold)
            } else {
                Text("COBRAR", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun NonCashRow(
    forma: FormaPago,
    value: String,
    onValueChange: (String) -> Unit,
    onUseExactAmount: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(10.dp))
                .padding(12.dp)
                .clickable(onClick = onUseExactAmount),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PaymentMethodIcon(forma = forma)
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                forma.descripcion ?: "Forma de pago",
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                forma.siglas ?: "",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            prefix = { Text("$ ") },
            singleLine = true,
            modifier = Modifier.width(140.dp),
        )
    }
}

@Composable
private fun PaymentMethodIcon(forma: FormaPago) {
    val decodedImage = remember(forma.imagen) { decodeBase64Image(forma.imagen) }
    val fallbackColor = remember(forma.siglas) { colorFromSigla(forma.siglas) }

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

private fun fallbackIconForSigla(sigla: String?) =
    when (sigla?.uppercase()) {
        "TDC", "TDD" -> Icons.Default.CreditCard
        "TR", "DB", "CK", "BANK" -> Icons.Default.AccountBalance
        else -> Icons.Default.Wallet
    }

private fun colorFromSigla(sigla: String?): Color {
    val palette = PaymentMethodColors
    val index = (sigla?.hashCode() ?: 0).let { if (it < 0) -it else it } % palette.size
    return palette[index]
}
