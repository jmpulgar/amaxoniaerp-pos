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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
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
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.amaxonia.pos.domain.model.money.Money
import com.amaxonia.pos.ui.common.DependencyContainer
import com.amaxonia.pos.ui.common.injectedViewModel
import kotlinx.coroutines.launch

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

    LaunchedEffect(Unit) {
        visible = true
        scale.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing),
        )
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
                message = "Usa SIGUIENTE ORDEN para finalizar y limpiar el estado de venta",
            )
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { paddingValues ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(paddingValues)
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
                        shape = RoundedCornerShape(28.dp),
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
                                Text("Cargando detalle del recibo...", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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

                                Spacer(modifier = Modifier.height(18.dp))

                                Text(
                                    "Transacción exitosa",
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )

                                Text(
                                    if (codFactura.isBlank()) "Factura generada correctamente" else "Factura: $codFactura",
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 8.dp),
                                )

                                if (feError.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Row(
                                        modifier =
                                            Modifier
                                                .fillMaxWidth()
                                                .background(MaterialTheme.colorScheme.errorContainer, RoundedCornerShape(16.dp))
                                                .padding(14.dp),
                                        verticalAlignment = Alignment.Top,
                                    ) {
                                        Icon(
                                            Icons.Default.Warning,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onErrorContainer,
                                            modifier = Modifier.size(22.dp),
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column {
                                            Text(
                                                "Factura creada, FEL rechazada",
                                                color = MaterialTheme.colorScheme.onErrorContainer,
                                                fontWeight = FontWeight.Bold,
                                            )
                                            Text(
                                                feError,
                                                color = MaterialTheme.colorScheme.onErrorContainer,
                                                fontSize = 13.sp,
                                                modifier = Modifier.padding(top = 4.dp),
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(24.dp))

                                Column(
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(20.dp))
                                            .padding(16.dp),
                                ) {
                                    Text(
                                        "Metodo de pago: ${paymentMethodsLabel.ifBlank { "N/A" }}",
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.align(Alignment.CenterHorizontally),
                                    )
                                    HorizontalDivider(
                                        color = MaterialTheme.colorScheme.outlineVariant,
                                        modifier = Modifier.padding(vertical = 12.dp),
                                    )
                                    if (isMultiCurrency && totalBs > 0.0) {
                                        Text(
                                            "Total: $ ${Money.format(
                                                Money.fromDouble(changeDue.coerceAtLeast(0.0)),
                                            )} (${formatCurrencyLabel(
                                                abrMonedaSecundaria,
                                            )} ${String.format(java.util.Locale.getDefault(), "%.2f", totalBs)})",
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.align(Alignment.CenterHorizontally),
                                        )
                                    }
                                    Text(
                                        "Cambio / Vuelto: $ ${Money.format(
                                            Money.fromDouble(changeDue),
                                        )}${if (isMultiCurrency && changeDueBs > 0.0) {
                                            " (${formatCurrencyLabel(
                                                abrMonedaSecundaria,
                                            )} ${String.format(java.util.Locale.getDefault(), "%.2f", changeDueBs)})"
                                        } else {
                                            ""
                                        }}",
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.align(Alignment.CenterHorizontally),
                                    )
                                }

                                Spacer(modifier = Modifier.height(24.dp))

                                Button(
                                    onClick = { viewModel.sendReceiptEmail() },
                                    enabled = !isSendingReceiptEmail,
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                                    shape = RoundedCornerShape(16.dp),
                                ) {
                                    Text(
                                        if (isSendingReceiptEmail) "ENVIANDO..." else "ENVIAR RECIBO",
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
                shape = RoundedCornerShape(16.dp),
            ) {
                Text(if (isPrinting) "IMPRIMIENDO..." else "IMPRIMIR RECIBO")
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onNextOrder,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                shape = RoundedCornerShape(16.dp),
            ) {
                Text("SIGUIENTE ORDEN", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
            }
        }
    }
}
