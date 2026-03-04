package com.amaxonia.pos.ui.payment

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Wallet
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amaxonia.pos.domain.model.payment.FormaPago
import com.amaxonia.pos.ui.common.DependencyContainer
import com.amaxonia.pos.ui.common.injectedViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaymentScreen(
    totalAmount: Double,
    onBack: () -> Unit,
    onPaymentSuccess: (PaymentSuccessPayload) -> Unit
) {
    // Instancia del ViewModel usando inyección de dependencias
    val viewModel = injectedViewModel {
        PaymentViewModel(
            transactionRepository = DependencyContainer.transactionRepository,
            formaPagoRepository = DependencyContainer.formaPagoRepository,
            cajaRepository = DependencyContainer.cajaRepository,
            cartRepository = DependencyContainer.cartRepository,
            salesRepository = DependencyContainer.salesRepository,
            localStore = DependencyContainer.localStore
        )
    }

    LaunchedEffect(totalAmount) {
        viewModel.setTotalAmount(totalAmount)
    }

    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text("Método de Pago", color = Color(0xFF1565C0), fontWeight = FontWeight.Bold)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Atrás", tint = Color(0xFF1565C0))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color(0xFFF5F5F5))
        ) {
            // Total a Pagar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White)
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Total a pagar :", fontSize = 16.sp, color = Color.DarkGray)
                Text(
                    "$ ${state.totalAmountText}",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1565C0)
                )
            }

            Spacer(modifier = Modifier.height(2.dp))

            // Tabs (Efectivo / Tarjeta)
            Row(modifier = Modifier.fillMaxWidth().background(Color.White)) {
                PaymentTab(
                    title = "Efectivo",
                    isSelected = state.selectedMethod == PaymentMethod.CASH,
                    onClick = { viewModel.toggleMethod(PaymentMethod.CASH) }
                )
                PaymentTab(
                    title = "Tarjeta / Otro",
                    isSelected = state.selectedMethod == PaymentMethod.NON_CASH,
                    onClick = { viewModel.toggleMethod(PaymentMethod.NON_CASH) }
                )
            }

            if (state.isLoadingFormasPago) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color(0xFF1565C0))
                }
            } else if (state.formasPagoError != null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = state.formasPagoError ?: "No se pudieron cargar las formas de pago",
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 14.sp
                    )
                }
            } else if (state.selectedMethod == PaymentMethod.CASH) {
                if (state.formasPagoEfectivo.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("¡No disponible!", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.LightGray)
                    }
                } else {
                    CashPaymentContent(state, viewModel, onPaymentSuccess)
                }
            } else {
                if (state.formasPagoTarjetaOtro.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("¡No disponible!", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.LightGray)
                    }
                } else {
                    NonCashPaymentContent(state, viewModel, onPaymentSuccess)
                }
            }
        }

        if (state.isProcessingPayment) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Color.White)
            }
        }

        val paymentError = state.paymentError
        if (paymentError != null) {
            AlertDialog(
                onDismissRequest = { viewModel.clearPaymentError() },
                confirmButton = {
                    TextButton(onClick = { viewModel.clearPaymentError() }) {
                        Text("Entendido")
                    }
                },
                title = { Text("Error al cobrar") },
                text = { Text(paymentError) }
            )
        }
    }
}

@Composable
fun RowScope.PaymentTab(title: String, isSelected: Boolean, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .weight(1f)
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = title,
            fontSize = 18.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) Color(0xFF1A237E) else Color.Gray
        )
        if (isSelected) {
            Spacer(modifier = Modifier.height(8.dp))
            Divider(color = Color(0xFF1A237E), thickness = 3.dp, modifier = Modifier.width(60.dp))
        }
    }
}

@Composable
fun CashPaymentContent(
    state: PaymentState,
    viewModel: PaymentViewModel,
    onSuccess: (PaymentSuccessPayload) -> Unit
) {
    val missingCashAmountText = Money.format(
        (state.totalAmountMoney - state.tenderedAmountMoney)
            .coerceAtLeast(java.math.BigDecimal.ZERO)
    )

    val warningScale by animateFloatAsState(
        targetValue = if (state.showInsufficientReminder && !state.isPaymentEnough) 1.03f else 1f,
        label = "cashWarningScale"
    )

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {

        // Botón Monto Exacto
        Button(
            onClick = { viewModel.setExactAmount() },
            modifier = Modifier.fillMaxWidth().height(50.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color.White),
            shape = RoundedCornerShape(8.dp),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
        ) {
            Icon(Icons.Default.Wallet, contentDescription = null, tint = Color(0xFF1A237E))
            Spacer(modifier = Modifier.width(8.dp))
            Text("MONTO EXACTO", color = Color(0xFF1A237E), fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Display del input
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White, RoundedCornerShape(8.dp))
                .border(
                    width = if (state.showInsufficientReminder && !state.isPaymentEnough) 2.dp else 0.dp,
                    color = Color(0xFFD32F2F),
                    shape = RoundedCornerShape(8.dp)
                )
                .padding(16.dp)
        ) {
            Text("Monto recibido", color = Color.DarkGray)
            Spacer(modifier = Modifier.height(8.dp))
            Row {
                Text("$ ", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color(0xFFBBDEFB))
                Text(state.tenderedAmountText, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color(0xFFBBDEFB))
            }

            AnimatedVisibility(
                visible = state.showInsufficientReminder && !state.isPaymentEnough,
                enter = fadeIn() + slideInVertically(initialOffsetY = { it / 2 }),
                exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 2 })
            ) {
                Text(
                    text = "Faltan $ $missingCashAmountText para completar el pago",
                    color = Color(0xFFD32F2F),
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    modifier = Modifier
                        .padding(top = 10.dp)
                        .fillMaxWidth()
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // Teclado Numérico
        Column(modifier = Modifier.fillMaxWidth()) {
            val keys = listOf(
                listOf("1", "2", "3"),
                listOf("4", "5", "6"),
                listOf("7", "8", "9"),
                listOf("C", "0", "00")
            )

            Row(modifier = Modifier.fillMaxWidth().height(350.dp)) {
                // Números
                Column(modifier = Modifier.weight(3f)) {
                    keys.forEach { rowKeys ->
                        Row(modifier = Modifier.weight(1f)) {
                            rowKeys.forEach { key ->
                                KeypadButton(key, Modifier.weight(1f)) { viewModel.onKeyPadInput(key) }
                            }
                        }
                    }
                }

                // Columna Acción
                Column(modifier = Modifier.weight(1f)) {
                    // Backspace
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(4.dp)
                            .background(Color.White, RoundedCornerShape(8.dp))
                            .clickable { viewModel.onKeyPadInput("BACK") },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Backspace, contentDescription = "Borrar", tint = Color(0xFF1565C0))
                    }

                    // Botón COBRAR / ENTER
                    Button(
                        onClick = {
                            viewModel.processPayment(onSuccess = onSuccess)
                        },
                        modifier = Modifier
                            .weight(3f)
                            .fillMaxWidth()
                            .padding(4.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (state.showInsufficientReminder && !state.isPaymentEnough) Color(0xFFD32F2F) else Color(0xFF1565C0)
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Cobrar",
                            tint = Color.White,
                            modifier = Modifier.size((34 * warningScale).dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun KeypadButton(text: String, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .fillMaxHeight()
            .padding(4.dp)
            .background(Color.White, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(text, fontSize = 24.sp, color = Color.DarkGray)
    }
}

@Composable
fun NonCashPaymentContent(
    state: PaymentState,
    viewModel: PaymentViewModel,
    onSuccess: (PaymentSuccessPayload) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            "Formas disponibles",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF1A237E)
        )
        Spacer(modifier = Modifier.height(12.dp))

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(state.formasPagoTarjetaOtro, key = { it.idFormaPago }) { forma ->
                NonCashRow(
                    forma = forma,
                    value = state.nonCashAmountsInput[forma.idFormaPago].orEmpty(),
                    onValueChange = { viewModel.setNonCashAmount(forma.idFormaPago, it) },
                    onUseExactAmount = { viewModel.setExactAmountForNonCash(forma.idFormaPago) }
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
        Text(
            "Asignado: $ ${state.nonCashAssignedText}",
            fontSize = 14.sp,
            color = Color.DarkGray
        )
        Text(
            "Pendiente: $ ${state.nonCashPendingText}",
            fontSize = 14.sp,
            color = Color.DarkGray
        )

        Spacer(modifier = Modifier.height(12.dp))
        AnimatedVisibility(
            visible = state.showInsufficientReminder && !state.isPaymentEnough,
            enter = fadeIn() + slideInVertically(initialOffsetY = { it / 2 }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 2 })
        ) {
            Text(
                text = "Faltan $ ${state.nonCashPendingText} para completar el pago",
                color = Color(0xFFD32F2F),
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        Button(
            onClick = { viewModel.processPayment(onSuccess = onSuccess) },
            enabled = true,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (state.isPaymentEnough) Color(0xFF1565C0) else Color(0xFF90A4AE)
            ),
            shape = RoundedCornerShape(10.dp)
        ) {
            Text("COBRAR", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun NonCashRow(
    forma: FormaPago,
    value: String,
    onValueChange: (String) -> Unit,
    onUseExactAmount: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White, RoundedCornerShape(10.dp))
            .padding(12.dp)
            .clickable(onClick = onUseExactAmount),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        PaymentMethodIcon(forma = forma)
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                forma.descripcion ?: "Forma de pago",
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1A237E)
            )
            Text(
                forma.siglas ?: "",
                fontSize = 12.sp,
                color = Color.Gray
            )
        }
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            prefix = { Text("$ ") },
            singleLine = true,
            modifier = Modifier.width(140.dp)
        )
    }
}

@Composable
private fun PaymentMethodIcon(forma: FormaPago) {
    val decodedImage = remember(forma.imagen) { decodeBase64Image(forma.imagen) }
    val fallbackColor = remember(forma.siglas) { colorFromSigla(forma.siglas) }

    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(fallbackColor.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center
    ) {
        if (decodedImage != null) {
            Image(
                bitmap = decodedImage,
                contentDescription = forma.descripcion,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
            )
        } else {
            Icon(
                imageVector = fallbackIconForSigla(forma.siglas),
                contentDescription = forma.descripcion,
                tint = fallbackColor,
                modifier = Modifier.size(20.dp)
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

private fun fallbackIconForSigla(sigla: String?) = when (sigla?.uppercase()) {
    "TDC", "TDD" -> Icons.Default.CreditCard
    "TR", "DB", "CK", "BANK" -> Icons.Default.AccountBalance
    else -> Icons.Default.Wallet
}

private fun colorFromSigla(sigla: String?): Color {
    val palette = listOf(
        Color(0xFF1565C0),
        Color(0xFF00897B),
        Color(0xFF6A1B9A),
        Color(0xFFEF6C00),
        Color(0xFF2E7D32),
        Color(0xFFC62828)
    )
    val index = (sigla?.hashCode() ?: 0).let { if (it < 0) -it else it } % palette.size
    return palette[index]
}
