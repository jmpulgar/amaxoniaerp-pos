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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.SyncAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.amaxonia.erp.data.remote.dto.FormaPagoDto
import com.amaxonia.erp.ui.components.isLandscape
import java.util.Locale

import com.amaxonia.erp.ui.theme.FlowHeaderGradient

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

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(if (isLandscape) 0.90f else 0.98f)
                .fillMaxHeight(if (isLandscape) 0.94f else 0.98f)
                .clip(RoundedCornerShape(14.dp)),
            color = Color.White,
            shadowElevation = 16.dp,
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // ══════════════════════════════════════════════════════════════
                // 1. ENCABEZADO (1:1 Web: .kt-portlet__head amaxonia_module__head)
                // ══════════════════════════════════════════════════════════════
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
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
                            modifier = Modifier.size(24.dp),
                        )
                        Text(
                            text = "Métodos de Pago",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        // Botón Limpiar Todo (#btnLimpiar)
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(WebAmberBroom)
                                .clickable { onClearPayments() },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoFixHigh,
                                contentDescription = "Limpiar Todo",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp),
                            )
                        }

                        // Botón Cerrar (#btnCerrar)
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(WebDangerRed)
                                .clickable { onDismiss() },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Cerrar",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp),
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
                                .width(430.dp)
                                .fillMaxHeight(),
                            verticalArrangement = Arrangement.SpaceBetween,
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
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            // Botón Procesar (#btnAceptar)
                            Button(
                                onClick = onConfirmPayment,
                                enabled = canProcess,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp),
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
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center,
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(28.dp),
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text(
                                            text = "Procesar",
                                            color = Color.White,
                                            fontSize = 22.sp,
                                            fontWeight = FontWeight.Bold,
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            // Teclado Numérico (#formapago-keyboard)
                            WebNumericKeypad(
                                onKeypadInput = onKeypadInput,
                                modifier = Modifier.fillMaxWidth(),
                            )
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
                                .height(52.dp),
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
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center,
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(28.dp),
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = "Procesar",
                                        color = Color.White,
                                        fontSize = 22.sp,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                            }
                        }

                        WebNumericKeypad(
                            onKeypadInput = onKeypadInput,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PaymentMethodsList(
    state: PosUiState,
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
        verticalArrangement = Arrangement.spacedBy(8.dp),
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
    onBadgeClick: () -> Unit,
    onInputClick: () -> Unit,
    onClearClick: () -> Unit,
    onToggleDenominations: () -> Unit,
    onAddDenomination: (Double) -> Unit,
) {
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
            .padding(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            // 1. Botón Badge de la Forma de Pago (.btn-formapago)
            Box(
                modifier = Modifier
                    .width(150.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFFE0F7FA))
                    .border(1.dp, Color(0xFFB2EBF2), RoundedCornerShape(8.dp))
                    .clickable { onBadgeClick() }
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        imageVector = getPaymentIcon(method),
                        contentDescription = null,
                        tint = WebTealText,
                        modifier = Modifier.size(22.dp),
                    )
                    Text(
                        text = method.descripcion ?: method.codigo ?: "Método",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = WebTealText,
                        maxLines = 1,
                    )
                }
            }

            // 2. Input Group (.input-formapago)
            // Botón Limpiar Fila (.btn-warning / escoba)
            Box(
                modifier = Modifier
                    .size(38.dp)
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
                    modifier = Modifier.size(18.dp),
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
                    .padding(horizontal = 10.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                if (amountText.isBlank()) {
                    Text(
                        text = "0.00",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFFCBD5E1),
                    )
                } else {
                    Text(
                        text = amountText,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A),
                    )
                }
            }

            // Botón Desplegable (.btn-accent) para Efectivo (Denominaciones)
            if (isCash) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isDenominationsExpanded) Color(0xFF1E5FE0) else WebBrandBlue)
                        .clickable { onToggleDenominations() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (isDenominationsExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = "Desplegar billetes",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        }

        // 3. Desplegable de Denominaciones / Billetes (#moneda_denominacion)
        AnimatedVisibility(visible = isDenominationsExpanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFFF1F5F9))
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = "Billetes y Monedas (toca para sumar al efectivo):",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF475569),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    listOf(1.0, 5.0, 10.0, 20.0, 50.0, 100.0).forEach { bill ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(32.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFFD1FAE5))
                                .border(1.dp, Color(0xFF10B981), RoundedCornerShape(6.dp))
                                .clickable { onAddDenomination(bill) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "$${bill.toInt()}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF065F46),
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
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        // Subtotal
        TotalRowCard(
            label = "Subtotal",
            icon = Icons.Default.PointOfSale,
            value = String.format(Locale.US, "%.2f", subtotal),
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
                )
            }
            Box(modifier = Modifier.weight(1f)) {
                TotalRowCard(
                    label = if (discountPercent > 0.0) "${String.format(Locale.US, "%.0f", discountPercent)}% Desc." else "Descuento",
                    icon = null,
                    value = String.format(Locale.US, "%.2f", discount),
                    bgColor = if (discount > 0.0) Color(0xFFDC2626) else WebNavyTotalBg,
                )
            }
        }

        // Total a Cancelar (Destacado en gradiente azul)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(
                    Brush.horizontalGradient(
                        listOf(Color(0xFF2488FA), Color(0xFF1E5FE0), Color(0xFF201B82))
                    )
                )
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
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        text = "Total",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Text(
                    text = "$ ${String.format(Locale.US, "%.2f", total)}",
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        // Acumulado
        TotalRowCard(
            label = "Acumulado",
            icon = Icons.Default.SyncAlt,
            value = String.format(Locale.US, "%.2f", totalAccumulated),
        )

        // Saldo (Verde si es 0, rojo si hay pendiente)
        TotalRowCard(
            label = "Saldo",
            icon = Icons.Default.MonetizationOn,
            value = String.format(Locale.US, "%.2f", remainingBalance),
            valueColor = if (remainingBalance > 0.001) Color(0xFFFCA5A5) else Color(0xFF86EFAC),
        )

        // Efectivo Recibido
        TotalRowCard(
            label = "Efectivo",
            icon = Icons.Default.Payments,
            value = String.format(Locale.US, "%.2f", cashEntered),
        )

        // Cambio / Vuelto
        TotalRowCard(
            label = "Cambio",
            icon = Icons.Default.Receipt,
            value = String.format(Locale.US, "%.2f", changeAmount),
            bgColor = if (changeAmount > 0.001) Color(0xFF166534) else WebNavyTotalBg,
            valueColor = if (changeAmount > 0.001) Color(0xFFBBF7D0) else Color.White,
        )
    }
}

@Composable
private fun TotalRowCard(
    label: String,
    icon: ImageVector?,
    value: String,
    bgColor: Color = WebNavyTotalBg,
    valueColor: Color = Color.White,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(38.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
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
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.size(16.dp),
                    )
                }
                Text(
                    text = label,
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Text(
                text = "$ $value",
                color = valueColor,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun WebNumericKeypad(
    onKeypadInput: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFFF8FAFC))
            .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(10.dp))
            .padding(6.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        // Fila 1: 7, 8, 9, ⌫
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            KeypadButton(text = "7", onClick = { onKeypadInput("7") }, modifier = Modifier.weight(1f))
            KeypadButton(text = "8", onClick = { onKeypadInput("8") }, modifier = Modifier.weight(1f))
            KeypadButton(text = "9", onClick = { onKeypadInput("9") }, modifier = Modifier.weight(1f))
            KeypadButton(
                text = "⌫",
                onClick = { onKeypadInput("Backspace") },
                modifier = Modifier.weight(1f),
                bgColor = Color(0xFFFEE2E2),
                textColor = Color(0xFFDC2626),
            )
        }

        // Fila 2: 4, 5, 6, C
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            KeypadButton(text = "4", onClick = { onKeypadInput("4") }, modifier = Modifier.weight(1f))
            KeypadButton(text = "5", onClick = { onKeypadInput("5") }, modifier = Modifier.weight(1f))
            KeypadButton(text = "6", onClick = { onKeypadInput("6") }, modifier = Modifier.weight(1f))
            KeypadButton(
                text = "C",
                onClick = { onKeypadInput("C") },
                modifier = Modifier.weight(1f),
                bgColor = Color(0xFFFEE2E2),
                textColor = Color(0xFFDC2626),
            )
        }

        // Fila 3: 1, 2, 3, Saldo
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            KeypadButton(text = "1", onClick = { onKeypadInput("1") }, modifier = Modifier.weight(1f))
            KeypadButton(text = "2", onClick = { onKeypadInput("2") }, modifier = Modifier.weight(1f))
            KeypadButton(text = "3", onClick = { onKeypadInput("3") }, modifier = Modifier.weight(1f))
            KeypadButton(
                text = "Saldo",
                onClick = { onKeypadInput("Saldo") },
                modifier = Modifier.weight(1f),
                bgColor = Color(0xFFEFF6FF),
                textColor = WebBrandBlue,
                fontSize = 13.sp,
            )
        }

        // Fila 4: 0 (span 2), . , OK
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            KeypadButton(text = "0", onClick = { onKeypadInput("0") }, modifier = Modifier.weight(2f))
            KeypadButton(text = ".", onClick = { onKeypadInput(".") }, modifier = Modifier.weight(1f))
            KeypadButton(
                text = "↵ OK",
                onClick = { onKeypadInput("Enter") },
                modifier = Modifier.weight(1f),
                bgColor = Color(0xFFDCFCE7),
                textColor = Color(0xFF16A34A),
                fontSize = 13.sp,
            )
        }
    }
}

@Composable
private fun KeypadButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    bgColor: Color = Color.White,
    textColor: Color = Color(0xFF1E293B),
    fontSize: androidx.compose.ui.unit.TextUnit = 18.sp,
) {
    Box(
        modifier = modifier
            .height(42.dp)
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

private fun getPaymentIcon(method: FormaPagoDto): ImageVector {
    val sig = method.siglas.orEmpty().uppercase()
    val cod = method.codigo.orEmpty().uppercase()
    val desc = method.descripcion.orEmpty().uppercase()

    return when {
        sig == "CASH" || sig == "EF" || cod == "EFECTIVO" || desc.contains("EFECTIVO") -> Icons.Default.Payments
        sig == "TDC" || sig == "TDD" || cod.contains("TARJ") || desc.contains("TARJETA") -> Icons.Default.CreditCard
        sig == "TR" || cod.contains("TRANS") || desc.contains("TRANSFERENCIA") -> Icons.Default.AccountBalance
        sig == "CK" || cod.contains("CHEQUE") || desc.contains("CHEQUE") -> Icons.Default.Receipt
        else -> Icons.Default.MonetizationOn
    }
}
