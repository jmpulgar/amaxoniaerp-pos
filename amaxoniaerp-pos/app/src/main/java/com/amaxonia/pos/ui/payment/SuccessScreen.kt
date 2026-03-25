package com.amaxonia.pos.ui.payment

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amaxonia.pos.ui.common.DependencyContainer
import com.amaxonia.pos.ui.common.injectedViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SuccessScreen(
    transactionId: String,
    onPrintReceipt: suspend (String) -> Result<String>,
    onNextOrder: () -> Unit
) {
    val viewModel = injectedViewModel {
        PaymentSuccessViewModel(
            localStore = DependencyContainer.localStore,
            transactionId = transactionId
        )
    }

    val uiState by viewModel.state.collectAsState()
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

    LaunchedEffect(Unit) {
        visible = true
        scale.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing)
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
                message = "Usa SIGUIENTE ORDEN para finalizar y limpiar el estado de venta"
            )
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.primary
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.primary)
                .padding(paddingValues)
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                AnimatedVisibility(
                    visible = visible,
                    enter = fadeIn(animationSpec = tween(180)) + scaleIn(initialScale = 0.92f, animationSpec = tween(220))
                ) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        if (uiState.isLoading || payload == null) {
                            Column(
                                modifier = Modifier
                                    .padding(24.dp)
                                    .fillMaxWidth()
                                    .graphicsLayer {
                                        scaleX = scale.value
                                        scaleY = scale.value
                                    },
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.height(12.dp))
                                Text("Cargando detalle del recibo...", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        } else {
                            Column(
                                modifier = Modifier
                                    .padding(24.dp)
                                    .graphicsLayer {
                                        scaleX = scale.value
                                        scaleY = scale.value
                                    },
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(80.dp)
                                        .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(40.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                Text(
                                    "¡Transacción Exitosa!",
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )

                                Text(
                                    if (codFactura.isBlank()) "Factura generada correctamente" else "Factura: $codFactura",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 8.dp)
                                )

                                Spacer(modifier = Modifier.height(24.dp))

                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
                                        .padding(16.dp)
                                ) {
                                    Text(
                                        "Metodo de pago: ${paymentMethodsLabel.ifBlank { "N/A" }}",
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.align(Alignment.CenterHorizontally)
                                    )
                                    HorizontalDivider(
                                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.5f),
                                        modifier = Modifier.padding(vertical = 8.dp)
                                    )
                                    Text(
                                        "Cambio / Vuelto: $ ${Money.format(Money.fromDouble(changeDue))}",
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.align(Alignment.CenterHorizontally)
                                    )
                                }

                                Spacer(modifier = Modifier.height(24.dp))

                                Button(
                                    onClick = {},
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text("ENVIAR RECIBO", color = MaterialTheme.colorScheme.primary)
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
                        val feedback = result.getOrElse { error ->
                            error.message ?: "No se pudo imprimir el recibo"
                        }
                        snackbarHostState.showSnackbar(feedback)
                        isPrinting = false
                    }
                },
                enabled = !isPrinting,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onPrimary),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.onPrimary),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(if (isPrinting) "IMPRIMIENDO..." else "IMPRIMIR RECIBO")
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onNextOrder,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("SIGUIENTE ORDEN", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }
        }
    }
}
