package com.amaxonia.erp.ui.pos

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.SyncAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.SubcomposeAsyncImage
import com.amaxonia.erp.composition.DependencyContainer
import com.amaxonia.erp.data.remote.ImageUrlHelper
import com.amaxonia.erp.data.remote.dto.FormaPagoDto
import com.amaxonia.erp.domain.model.DenominacionesCatalogo
import com.amaxonia.erp.domain.model.MonedaDenominacion
import com.amaxonia.erp.ui.components.AdaptiveImage
import com.amaxonia.erp.ui.components.isLandscape
import com.amaxonia.erp.ui.components.isLargeScreen
import java.util.Locale

import com.amaxonia.erp.ui.theme.FlowHeaderGradient
import com.amaxonia.erp.ui.theme.FlowTableTextMuted

private val WebMintBg = Color(0xFFECFFFB)
private val WebMintBorder = Color(0xFFCCF9EF)
private val WebTealText = Color(0xFF006064)
private val WebNavyTotalBg = Color(0xFF2B4185)
private val WebAmberBroom = Color(0xFFF59E0B)
private val WebAmberBroomLight = Color(0xFFFFF4E0)
private val WebAmberBroomIcon = Color(0xFFC27803)
private val WebDangerRed = Color(0xFFEF4444)
private val WebSuccessGreen = Color(0xFF28A745)
private val WebBrandBlue = Color(0xFF2488FA)

@Composable
fun PaymentDialog(
    state: PosUiState,
    onReceivedAmountChange: (String) -> Unit = {},
    onSelectPaymentMethod: (FormaPagoDto) -> Unit = {},
    onSetPaymentMethodAmount: (Int, Double) -> Unit = { _, _ -> },
    onClearPayments: () -> Unit = {},
    onSelectActiveInput: (Int) -> Unit = {},
    onPaymentMethodBadgeClick: (FormaPagoDto) -> Unit = {},
    onPaymentMethodTextChange: (Int, String) -> Unit = { _, _ -> },
    onClearSinglePaymentMethod: (Int) -> Unit = {},
    onKeypadInput: (String) -> Unit = {},
    onToggleCashDenominations: () -> Unit = {},
    onAddCashDenomination: (Double) -> Unit = {},
    onConfirmPayment: () -> Unit,
    onDismiss: () -> Unit,
) {
    val total = state.summary.total
    val subtotal = state.summary.subtotal
    val tax = state.summary.tax
    val discount = state.summary.discountTotal
    val discountPercent = state.globalDiscountPercent

    val cashMethod = state.paymentMethods.firstOrNull { isCashPaymentMethod(it) }
    val cashMethodId = cashMethod?.idFormaPago
    val cashEntered = if (cashMethodId != null) (state.paymentsMap[cashMethodId] ?: 0.0) else 0.0
    val nonCashSum = state.paymentsMap.filter { it.key != cashMethodId }.values.sum()
    val cashNeeded = (total - nonCashSum).coerceAtLeast(0.0)
    val changeAmount = if (cashEntered > cashNeeded) (cashEntered - cashNeeded) else 0.0
    val totalAccumulated = nonCashSum + cashEntered
    val remainingBalance = (total - (nonCashSum + cashEntered.coerceAtMost(cashNeeded))).coerceAtLeast(0.0)

    val canProcess = !state.isProcessingSale && total > 0.0 && remainingBalance <= 0.001
    val isLandscape = isLandscape()
    val isLarge = isLargeScreen()

    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp
    val screenHeight = configuration.screenHeightDp

    val isSmallHeight = screenHeight < 560
    val isSmallDevice = screenWidth < 900 || screenHeight < 560
    val isExtraLarge = screenWidth >= 1150 && screenHeight >= 700

    val rightColumnWidth = if (isExtraLarge) 440.dp else if (isLarge) 380.dp else 330.dp

    var processingSeconds by remember { mutableIntStateOf(0) }

    LaunchedEffect(state.isProcessingSale) {
        if (!state.isProcessingSale) {
            processingSeconds = 0
            return@LaunchedEffect
        }
        processingSeconds = 0
        while (true) {
            delay(1_000)
            processingSeconds += 1
        }
    }

    Dialog(
        onDismissRequest = {
            if (!state.isProcessingSale) {
                onDismiss()
            }
        },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = !state.isProcessingSale,
            dismissOnClickOutside = !state.isProcessingSale,
        ),
    ) {
        Surface(
            modifier = Modifier
                .widthIn(max = if (isExtraLarge) 1240.dp else if (isLarge) 1100.dp else 980.dp)
                .fillMaxWidth(if (isLandscape) (if (isSmallDevice) 0.98f else 0.94f) else 0.98f)
                .fillMaxHeight(if (isLandscape) (if (isSmallHeight) 0.98f else 0.94f) else 0.98f)
                .clip(RoundedCornerShape(14.dp)),
            color = Color.White,
            shadowElevation = 16.dp,
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Column(modifier = Modifier.fillMaxSize()) {
                // ══════════════════════════════════════════════════════════════
                // 1. ENCABEZADO (1:1 Web: .kt-portlet__head amaxonia_module__head)
                // ══════════════════════════════════════════════════════════════
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(if (isSmallHeight) 46.dp else if (isLarge) 60.dp else 52.dp)
                        .background(FlowHeaderGradient)
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.MonetizationOn,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(if (isLarge) 28.dp else 24.dp),
                        )
                        Text(
                            text = "Métodos de Pago",
                            color = Color.White,
                            fontSize = if (isLarge) 20.sp else 18.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        // Botón Limpiar Todo (#btnLimpiar)
                        Box(
                            modifier = Modifier
                                .size(if (isLarge) 40.dp else 34.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(WebAmberBroom)
                                .clickable(enabled = !state.isProcessingSale) { onClearPayments() },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoFixHigh,
                                contentDescription = "Limpiar Todo",
                                tint = Color.White,
                                modifier = Modifier.size(if (isLarge) 22.dp else 18.dp),
                            )
                        }

                        // Botón Cerrar (#btnCerrar)
                        Box(
                            modifier = Modifier
                                .size(if (isLarge) 40.dp else 34.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(WebDangerRed)
                                .clickable(enabled = !state.isProcessingSale) { onDismiss() },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Cerrar",
                                tint = Color.White,
                                modifier = Modifier.size(if (isLarge) 24.dp else 20.dp),
                            )
                        }
                    }
                }

                // ══════════════════════════════════════════════════════════════
                // 2. CUERPO: MÉTODOS DE PAGO Y TOTALES / TECLADO
                // ══════════════════════════════════════════════════════════════
                if (isLandscape) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        // Columna Izquierda: Formas de Pago (.formapago-left)
                        Box(
                            modifier = Modifier
                                .weight(1.2f)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(12.dp))
                                .background(WebMintBg)
                                .border(1.dp, WebMintBorder, RoundedCornerShape(12.dp))
                                .padding(10.dp),
                        ) {
                            PaymentMethodsList(
                                state = state,
                                isLarge = isLarge,
                                isSmallDevice = isSmallDevice,
                                isSmallHeight = isSmallHeight,
                                onPaymentMethodBadgeClick = onPaymentMethodBadgeClick,
                                onSelectActiveInput = onSelectActiveInput,
                                onClearSinglePaymentMethod = onClearSinglePaymentMethod,
                                onToggleCashDenominations = onToggleCashDenominations,
                                onAddCashDenomination = onAddCashDenomination,
                            )
                        }

                        // Columna Derecha: Totales contables (.formapago-right) y Teclado
                        Column(
                            modifier = Modifier
                                .width(rightColumnWidth)
                                .fillMaxHeight()
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(if (isSmallHeight) 4.dp else 8.dp),
                        ) {
                            PaymentTotalsPanel(
                                subtotal = subtotal,
                                tax = tax,
                                discount = discount,
                                discountPercent = discountPercent,
                                total = total,
                                totalAccumulated = totalAccumulated,
                                remainingBalance = remainingBalance,
                                cashEntered = cashEntered,
                                changeAmount = changeAmount,
                                isLarge = isLarge,
                                isSmallHeight = isSmallHeight,
                            )

                            // Bloque Calculadora + Botón Procesar directamente encima
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(if (isSmallHeight) 4.dp else 6.dp),
                            ) {
                                // Botón Procesar (#btnAceptar) directamente encima del teclado
                                Button(
                                    onClick = onConfirmPayment,
                                    enabled = canProcess,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(if (isSmallHeight) 42.dp else if (isLarge) 54.dp else 48.dp),
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (canProcess) WebSuccessGreen else Color(0xFF94A3B8),
                                        disabledContainerColor = Color(0xFFCBD5E1),
                                    ),
                                ) {
                                    if (state.isProcessingSale) {
                                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
                                    } else {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Check,
                                                    contentDescription = null,
                                                    tint = Color.White,
                                                    modifier = Modifier.size(if (isSmallHeight) 20.dp else if (isLarge) 26.dp else 22.dp),
                                                )
                                                Text(
                                                    text = "Procesar",
                                                    color = Color.White,
                                                    fontSize = if (isSmallHeight) 16.sp else if (isLarge) 20.sp else 18.sp,
                                                    fontWeight = FontWeight.Bold,
                                                )
                                            }
                                            Text(
                                                text = "$ ${String.format(Locale.US, "%.2f", total)}",
                                                color = Color.White,
                                                fontSize = if (isSmallHeight) 18.sp else if (isLarge) 22.sp else 19.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                            )
                                        }
                                    }
                                }

                                // Teclado Numérico (#formapago-keyboard)
                                WebNumericKeypad(
                                    onKeypadInput = onKeypadInput,
                                    isLarge = isLarge,
                                    isSmallHeight = isSmallHeight,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }
                } else {
                    // Modo Vertical (Portrait)
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(10.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        PaymentTotalsPanel(
                            subtotal = subtotal,
                            tax = tax,
                            discount = discount,
                            discountPercent = discountPercent,
                            total = total,
                            totalAccumulated = totalAccumulated,
                            remainingBalance = remainingBalance,
                            cashEntered = cashEntered,
                            changeAmount = changeAmount,
                            isLarge = isLarge,
                            isSmallHeight = isSmallHeight,
                        )

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(WebMintBg)
                                .border(1.dp, WebMintBorder, RoundedCornerShape(12.dp))
                                .padding(10.dp),
                        ) {
                            PaymentMethodsList(
                                state = state,
                                isLarge = isLarge,
                                isSmallDevice = isSmallDevice,
                                isSmallHeight = isSmallHeight,
                                onPaymentMethodBadgeClick = onPaymentMethodBadgeClick,
                                onSelectActiveInput = onSelectActiveInput,
                                onClearSinglePaymentMethod = onClearSinglePaymentMethod,
                                onToggleCashDenominations = onToggleCashDenominations,
                                onAddCashDenomination = onAddCashDenomination,
                            )
                        }

                        Button(
                            onClick = onConfirmPayment,
                            enabled = canProcess,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(if (isSmallHeight) 44.dp else if (isLarge) 54.dp else 48.dp),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (canProcess) WebSuccessGreen else Color(0xFF94A3B8),
                                disabledContainerColor = Color(0xFFCBD5E1),
                            ),
                        ) {
                            if (state.isProcessingSale) {
                                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                            } else {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(if (isLarge) 26.dp else 22.dp),
                                        )
                                        Text(
                                            text = "Procesar",
                                            color = Color.White,
                                            fontSize = if (isLarge) 20.sp else 18.sp,
                                            fontWeight = FontWeight.Bold,
                                        )
                                    }
                                    Text(
                                        text = "$ ${String.format(Locale.US, "%.2f", total)}",
                                        color = Color.White,
                                        fontSize = if (isLarge) 22.sp else 19.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                    )
                                }
                            }
                        }

                        WebNumericKeypad(
                            onKeypadInput = onKeypadInput,
                            isLarge = isLarge,
                            isSmallHeight = isSmallHeight,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            if (state.isProcessingSale) {
                ProcessingPaymentOverlay(elapsedSeconds = processingSeconds)
            }
        }
    }
}
}

@Composable
private fun PaymentMethodsList(
    state: PosUiState,
    isLarge: Boolean = false,
    isSmallDevice: Boolean = false,
    isSmallHeight: Boolean = false,
    onPaymentMethodBadgeClick: (FormaPagoDto) -> Unit,
    onSelectActiveInput: (Int) -> Unit,
    onClearSinglePaymentMethod: (Int) -> Unit,
    onToggleCashDenominations: () -> Unit,
    onAddCashDenomination: (Double) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(if (isSmallHeight) 6.dp else 8.dp),
    ) {
        state.paymentMethods.forEach { method ->
            val isCash = isCashPaymentMethod(method)
            val isActive = state.activePaymentInputMethodId == method.idFormaPago
            val textVal = state.paymentInputTexts[method.idFormaPago]
                ?: state.paymentsMap[method.idFormaPago]?.let { if (it > 0) String.format(Locale.US, "%.2f", it) else "" }
                ?: ""

            PaymentMethodRow(
                method = method,
                isCash = isCash,
                isActive = isActive,
                amountText = textVal,
                isDenominationsExpanded = isCash && state.expandedCashDenominations,
                isLarge = isLarge,
                isSmallDevice = isSmallDevice,
                isSmallHeight = isSmallHeight,
                baseUrl = state.apiBaseUrl,
                countryCode = state.countryCode,
                companyDb = state.companyDb,
                onBadgeClick = { onPaymentMethodBadgeClick(method) },
                onInputClick = { onSelectActiveInput(method.idFormaPago) },
                onClearClick = { onClearSinglePaymentMethod(method.idFormaPago) },
                onToggleDenominations = onToggleCashDenominations,
                onAddDenomination = onAddCashDenomination,
            )
        }
    }
}

@Composable
private fun PaymentMethodRow(
    method: FormaPagoDto,
    isCash: Boolean,
    isActive: Boolean,
    amountText: String,
    isDenominationsExpanded: Boolean,
    isLarge: Boolean = false,
    isSmallDevice: Boolean = false,
    isSmallHeight: Boolean = false,
    baseUrl: String,
    countryCode: String,
    companyDb: String,
    onBadgeClick: () -> Unit,
    onInputClick: () -> Unit,
    onClearClick: () -> Unit,
    onToggleDenominations: () -> Unit,
    onAddDenomination: (Double) -> Unit,
) {
    val rowHeight = if (isSmallHeight) 42.dp else if (isLarge) 56.dp else 48.dp
    val badgeWidth = if (isLarge) 190.dp else if (isSmallDevice) 125.dp else 150.dp
    val actionBtnSize = if (isSmallHeight) 34.dp else if (isLarge) 44.dp else 38.dp
    val amountFontSize = if (isSmallHeight) 18.sp else if (isLarge) 24.sp else 20.sp

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (isActive) Color(0xFFF0FDF4) else Color.White)
            .border(
                width = if (isActive) 2.dp else 1.dp,
                color = if (isActive) WebBrandBlue else WebMintBorder,
                shape = RoundedCornerShape(10.dp),
            )
            .padding(if (isSmallHeight) 5.dp else if (isLarge) 8.dp else 6.dp),
        verticalArrangement = Arrangement.spacedBy(if (isSmallHeight) 4.dp else 6.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(rowHeight),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(if (isSmallDevice) 6.dp else 8.dp),
        ) {
            val imageUrl = remember(method.idFormaPago, method.imagen, baseUrl, countryCode, companyDb) {
                ImageUrlHelper.paymentMethodImageUrl(
                    baseUrl = baseUrl,
                    countryCode = countryCode,
                    companyDb = companyDb,
                    idFormaPago = method.idFormaPago,
                    imageValue = method.imagen,
                )
            }

            // 1. Botón Badge de la Forma de Pago (.btn-formapago)
            Box(
                modifier = Modifier
                    .width(badgeWidth)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFFE0F7FA))
                    .border(1.dp, Color(0xFFB2EBF2), RoundedCornerShape(8.dp))
                    .clickable { onBadgeClick() }
                    .padding(horizontal = if (isSmallDevice) 6.dp else 8.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(if (isSmallDevice) 4.dp else 8.dp),
                ) {
                    if (imageUrl.isNotBlank()) {
                        AdaptiveImage(
                            source = imageUrl,
                            contentDescription = method.descripcion,
                            modifier = Modifier
                                .size(if (isSmallHeight) 20.dp else if (isLarge) 30.dp else 24.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            contentScale = ContentScale.Fit,
                            fallback = {},
                        )
                    }
                    Text(
                        text = method.descripcion ?: method.codigo ?: "Método",
                        fontSize = if (isSmallDevice) 11.sp else if (isLarge) 14.sp else 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = WebTealText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            // 2. Input Group (.input-formapago)
            // Botón Limpiar Fila (.btn-warning / escoba)
            Box(
                modifier = Modifier
                    .size(actionBtnSize)
                    .clip(RoundedCornerShape(8.dp))
                    .background(WebAmberBroomLight)
                    .border(1.dp, Color(0xFFFFE082), RoundedCornerShape(8.dp))
                    .clickable { onClearClick() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.AutoFixHigh,
                    contentDescription = "Limpiar fila",
                    tint = WebAmberBroomIcon,
                    modifier = Modifier.size(if (isSmallHeight) 16.dp else if (isLarge) 20.dp else 18.dp),
                )
            }

            // Casilla de Monto (Input)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.White)
                    .border(
                        width = if (isActive) 1.5.dp else 1.dp,
                        color = if (isActive) WebBrandBlue else Color(0xFFE2E8F0),
                        shape = RoundedCornerShape(8.dp),
                    )
                    .clickable { onInputClick() }
                    .padding(horizontal = if (isSmallDevice) 8.dp else 12.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                if (amountText.isBlank()) {
                    Text(
                        text = "0.00",
                        fontSize = amountFontSize,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFFCBD5E1),
                    )
                } else {
                    Text(
                        text = amountText,
                        fontSize = amountFontSize,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A),
                    )
                }
            }

            // Botón Desplegable (.btn-accent) para Efectivo (Denominaciones)
            if (isCash) {
                Box(
                    modifier = Modifier
                        .size(actionBtnSize)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isDenominationsExpanded) Color(0xFF1E5FE0) else WebBrandBlue)
                        .clickable { onToggleDenominations() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (isDenominationsExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = "Desplegar billetes",
                        tint = Color.White,
                        modifier = Modifier.size(if (isSmallHeight) 20.dp else if (isLarge) 24.dp else 22.dp),
                    )
                }
            }
        }

        // 3. Desplegable de Denominaciones / Billetes (#moneda_denominacion)
        AnimatedVisibility(visible = isDenominationsExpanded) {
            val denominations = remember { DenominacionesCatalogo.getDenominacionesPanama() }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFFF1F5F9))
                    .padding(if (isLarge) 10.dp else 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "Billetes y Monedas (toca para sumar al efectivo):",
                    fontSize = if (isLarge) 13.sp else 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF475569),
                )

                // Billetes
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val bills = denominations.filter { !it.esMoneda }
                    for (item in bills) {
                        val itemImageUrl = remember(item.id, baseUrl, countryCode, companyDb) {
                            if (companyDb.isNotBlank()) {
                                ImageUrlHelper.denominationImageUrl(baseUrl, countryCode, companyDb, item.id)
                            } else {
                                ""
                            }
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(if (isLarge) 48.dp else 38.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFFD1FAE5))
                                .border(1.dp, Color(0xFF10B981), RoundedCornerShape(6.dp))
                                .clickable { onAddDenomination(item.valor) },
                            contentAlignment = Alignment.Center,
                        ) {
                            AdaptiveImage(
                                source = itemImageUrl,
                                contentDescription = item.denominacion,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(5.dp)),
                                contentScale = ContentScale.Crop,
                                fallback = {
                                    Text(
                                        text = "$${item.valor.toInt()}",
                                        fontSize = if (isLarge) 14.sp else 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF065F46),
                                    )
                                },
                            )
                        }
                    }
                }

                // Monedas
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val coins = denominations.filter { it.esMoneda }
                    for (item in coins) {
                        val itemImageUrl = remember(item.id, baseUrl, countryCode, companyDb) {
                            if (companyDb.isNotBlank()) {
                                ImageUrlHelper.denominationImageUrl(baseUrl, countryCode, companyDb, item.id)
                            } else {
                                ""
                            }
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(if (isLarge) 38.dp else 32.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFFE2E8F0))
                                .border(1.dp, Color(0xFF94A3B8), RoundedCornerShape(6.dp))
                                .clickable { onAddDenomination(item.valor) },
                            contentAlignment = Alignment.Center,
                        ) {
                            AdaptiveImage(
                                source = itemImageUrl,
                                contentDescription = item.denominacion,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(5.dp)),
                                contentScale = ContentScale.Fit,
                                fallback = {
                                    Text(
                                        text = "$${String.format(Locale.US, "%.2f", item.valor)}",
                                        fontSize = if (isLarge) 12.sp else 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF334155),
                                    )
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PaymentTotalsPanel(
    subtotal: Double,
    tax: Double,
    discount: Double,
    discountPercent: Double,
    total: Double,
    totalAccumulated: Double,
    remainingBalance: Double,
    cashEntered: Double,
    changeAmount: Double,
    isLarge: Boolean = false,
    isSmallHeight: Boolean = false,
) {
    var isDesgloseExpanded by remember { mutableStateOf(!isSmallHeight) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(if (isLarge) 6.dp else 4.dp),
    ) {
        // Total a Cancelar (Destacado en gradiente azul) con botón para colapsar/desplegar el desglose
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (isLarge) 54.dp else 46.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(
                    Brush.horizontalGradient(
                        listOf(Color(0xFF2488FA), Color(0xFF1E5FE0), Color(0xFF201B82))
                    )
                )
                .clickable { isDesgloseExpanded = !isDesgloseExpanded }
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.PointOfSale,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(if (isLarge) 24.dp else 20.dp),
                    )
                    Text(
                        text = "Total a Cancelar",
                        color = Color.White,
                        fontSize = if (isLarge) 17.sp else 15.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "$ ${String.format(Locale.US, "%.2f", total)}",
                        color = Color.White,
                        fontSize = if (isLarge) 24.sp else 20.sp,
                        fontWeight = FontWeight.ExtraBold,
                    )
                    Box(
                        modifier = Modifier
                            .size(if (isLarge) 28.dp else 24.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = if (isDesgloseExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = if (isDesgloseExpanded) "Ocultar desglose" else "Ver desglose",
                            tint = Color.White,
                            modifier = Modifier.size(if (isLarge) 20.dp else 16.dp),
                        )
                    }
                }
            }
        }

        // DESGLOSE ORIGINAL COMPLETO (Se oculta/muestra con animación)
        AnimatedVisibility(visible = isDesgloseExpanded) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(if (isLarge) 6.dp else 4.dp),
            ) {
                // Subtotal
                TotalRowCard(
                    label = "Subtotal",
                    icon = Icons.Default.PointOfSale,
                    value = String.format(Locale.US, "%.2f", subtotal),
                    isLarge = isLarge,
                )

                // Fila dual: Impuesto y Descuento
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        TotalRowCard(
                            label = "Impuesto",
                            icon = null,
                            value = String.format(Locale.US, "%.2f", tax),
                            isLarge = isLarge,
                        )
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        TotalRowCard(
                            label = if (discountPercent > 0.0) "${String.format(Locale.US, "%.0f", discountPercent)}% Desc." else "Descuento",
                            icon = null,
                            value = String.format(Locale.US, "%.2f", discount),
                            isLarge = isLarge,
                            bgColor = if (discount > 0.0) Color(0xFFDC2626) else WebNavyTotalBg,
                        )
                    }
                }

                // Acumulado
                TotalRowCard(
                    label = "Acumulado",
                    icon = Icons.Default.SyncAlt,
                    value = String.format(Locale.US, "%.2f", totalAccumulated),
                    isLarge = isLarge,
                )

                // Saldo (Verde si es 0, rojo si hay pendiente)
                TotalRowCard(
                    label = "Saldo",
                    icon = Icons.Default.MonetizationOn,
                    value = String.format(Locale.US, "%.2f", remainingBalance),
                    isLarge = isLarge,
                    valueColor = if (remainingBalance > 0.001) Color(0xFFFCA5A5) else Color(0xFF86EFAC),
                )

                // Efectivo Recibido
                TotalRowCard(
                    label = "Efectivo",
                    icon = Icons.Default.Payments,
                    value = String.format(Locale.US, "%.2f", cashEntered),
                    isLarge = isLarge,
                )

                // Cambio / Vuelto
                TotalRowCard(
                    label = "Cambio",
                    icon = Icons.Default.Receipt,
                    value = String.format(Locale.US, "%.2f", changeAmount),
                    isLarge = isLarge,
                    bgColor = if (changeAmount > 0.001) Color(0xFF166534) else WebNavyTotalBg,
                    valueColor = if (changeAmount > 0.001) Color(0xFFBBF7D0) else Color.White,
                )
            }
        }
    }
}

@Composable
private fun TotalRowCard(
    label: String,
    icon: ImageVector?,
    value: String,
    isLarge: Boolean = false,
    bgColor: Color = WebNavyTotalBg,
    valueColor: Color = Color.White,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(if (isLarge) 45.dp else 38.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .padding(horizontal = if (isLarge) 14.dp else 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.size(if (isLarge) 18.dp else 16.dp),
                    )
                }
                Text(
                    text = label,
                    color = Color.White,
                    fontSize = if (isLarge) 14.sp else 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Text(
                text = "$ $value",
                color = valueColor,
                fontSize = if (isLarge) 18.sp else 16.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun WebNumericKeypad(
    onKeypadInput: (String) -> Unit,
    isLarge: Boolean = false,
    isSmallHeight: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val buttonHeight = if (isSmallHeight) 36.dp else if (isLarge) 48.dp else 40.dp
    val keypadFontSize = if (isSmallHeight) 16.sp else if (isLarge) 21.sp else 18.sp
    val rowGap = if (isSmallHeight) 4.dp else if (isLarge) 6.dp else 5.dp

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFFF8FAFC))
            .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(10.dp))
            .padding(if (isSmallHeight) 5.dp else if (isLarge) 8.dp else 6.dp),
        verticalArrangement = Arrangement.spacedBy(rowGap),
    ) {
        // Fila 1: 7, 8, 9, ⌫
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(rowGap),
        ) {
            KeypadButton(text = "7", height = buttonHeight, fontSize = keypadFontSize, onClick = { onKeypadInput("7") }, modifier = Modifier.weight(1f))
            KeypadButton(text = "8", height = buttonHeight, fontSize = keypadFontSize, onClick = { onKeypadInput("8") }, modifier = Modifier.weight(1f))
            KeypadButton(text = "9", height = buttonHeight, fontSize = keypadFontSize, onClick = { onKeypadInput("9") }, modifier = Modifier.weight(1f))
            KeypadButton(
                text = "⌫",
                height = buttonHeight,
                fontSize = keypadFontSize,
                onClick = { onKeypadInput("Backspace") },
                modifier = Modifier.weight(1f),
                bgColor = Color(0xFFFEE2E2),
                textColor = Color(0xFFDC2626),
            )
        }

        // Fila 2: 4, 5, 6, C
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(rowGap),
        ) {
            KeypadButton(text = "4", height = buttonHeight, fontSize = keypadFontSize, onClick = { onKeypadInput("4") }, modifier = Modifier.weight(1f))
            KeypadButton(text = "5", height = buttonHeight, fontSize = keypadFontSize, onClick = { onKeypadInput("5") }, modifier = Modifier.weight(1f))
            KeypadButton(text = "6", height = buttonHeight, fontSize = keypadFontSize, onClick = { onKeypadInput("6") }, modifier = Modifier.weight(1f))
            KeypadButton(
                text = "C",
                height = buttonHeight,
                fontSize = keypadFontSize,
                onClick = { onKeypadInput("C") },
                modifier = Modifier.weight(1f),
                bgColor = Color(0xFFFEE2E2),
                textColor = Color(0xFFDC2626),
            )
        }

        // Fila 3: 1, 2, 3, Saldo
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(rowGap),
        ) {
            KeypadButton(text = "1", height = buttonHeight, fontSize = keypadFontSize, onClick = { onKeypadInput("1") }, modifier = Modifier.weight(1f))
            KeypadButton(text = "2", height = buttonHeight, fontSize = keypadFontSize, onClick = { onKeypadInput("2") }, modifier = Modifier.weight(1f))
            KeypadButton(text = "3", height = buttonHeight, fontSize = keypadFontSize, onClick = { onKeypadInput("3") }, modifier = Modifier.weight(1f))
            KeypadButton(
                text = "Saldo",
                height = buttonHeight,
                fontSize = if (isSmallHeight) 12.sp else if (isLarge) 15.sp else 13.sp,
                onClick = { onKeypadInput("Saldo") },
                modifier = Modifier.weight(1f),
                bgColor = Color(0xFFEFF6FF),
                textColor = WebBrandBlue,
            )
        }

        // Fila 4: 0 (span 2), . , OK
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(rowGap),
        ) {
            KeypadButton(text = "0", height = buttonHeight, fontSize = keypadFontSize, onClick = { onKeypadInput("0") }, modifier = Modifier.weight(2f))
            KeypadButton(text = ".", height = buttonHeight, fontSize = keypadFontSize, onClick = { onKeypadInput(".") }, modifier = Modifier.weight(1f))
            KeypadButton(
                text = "↵ OK",
                height = buttonHeight,
                fontSize = if (isSmallHeight) 12.sp else if (isLarge) 15.sp else 13.sp,
                onClick = { onKeypadInput("Enter") },
                modifier = Modifier.weight(1f),
                bgColor = Color(0xFFDCFCE7),
                textColor = Color(0xFF16A34A),
            )
        }
    }
}

@Composable
private fun KeypadButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    height: androidx.compose.ui.unit.Dp = 42.dp,
    bgColor: Color = Color.White,
    textColor: Color = Color(0xFF1E293B),
    fontSize: androidx.compose.ui.unit.TextUnit = 18.sp,
) {
    Box(
        modifier = modifier
            .height(height)
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .border(1.dp, Color(0xFFCBD5E1), RoundedCornerShape(8.dp))
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            fontSize = fontSize,
            fontWeight = FontWeight.Bold,
            color = textColor,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ProcessingPaymentOverlay(
    elapsedSeconds: Int,
) {
    val estimatedSeconds = 15
    val progress = (elapsedSeconds / estimatedSeconds.toFloat()).coerceIn(0.10f, 0.95f)
    val secondsLeft = (estimatedSeconds - elapsedSeconds).coerceAtLeast(2)

    val stageTitle = when {
        elapsedSeconds < 3 -> "Preparando la factura"
        elapsedSeconds < 8 -> "Conectando con facturación electrónica"
        elapsedSeconds < 18 -> "Esperando autorización fiscal"
        else -> "Finalizando validación"
    }

    val stageSubtitle = when {
        elapsedSeconds < 3 -> "Validando pago, caja e inventario local..."
        elapsedSeconds < 8 -> "Transmitiendo documento al PAC The Factory HKA..."
        elapsedSeconds < 18 -> "The Factory está procesando CUFE, número de control y código QR..."
        else -> "La respuesta está tardando un poco más de lo normal, por favor no cierres la pantalla."
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f))
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        ElevatedCard(
            modifier = Modifier
                .widthIn(max = 440.dp)
                .fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.elevatedCardColors(containerColor = Color.White),
            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 14.dp),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 28.dp, vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .background(Color(0xFFE8F1FF), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.size(60.dp),
                        strokeWidth = 6.dp,
                        color = WebBrandBlue,
                        trackColor = WebBrandBlue.copy(alpha = 0.16f),
                    )
                    Text(
                        text = "${elapsedSeconds}s",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = WebBrandBlue,
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = "Procesando cobro",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1E293B),
                )
                Text(
                    text = stageTitle,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = WebBrandBlue,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 6.dp),
                )
                Text(
                    text = stageSubtitle,
                    fontSize = 13.sp,
                    color = Color(0xFF64748B),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp),
                )

                Spacer(modifier = Modifier.height(22.dp))

                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(50)),
                    color = WebBrandBlue,
                    trackColor = Color(0xFFE2E8F0),
                )

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = if (elapsedSeconds < estimatedSeconds) {
                        "Tiempo estimado restante: ~${secondsLeft}s"
                    } else {
                        "Está tomando más de lo habitual, seguimos procesando."
                    },
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF94A3B8),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
