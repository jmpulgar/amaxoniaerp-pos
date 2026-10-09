package com.amaxonia.erp.ui.pos.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.filled.BusinessCenter
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ConfirmationNumber
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Engineering
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Stars
import androidx.compose.material.icons.filled.WorkOutline
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amaxonia.erp.domain.model.Client
import com.amaxonia.erp.domain.model.SellerSummary
import com.amaxonia.erp.ui.pos.PosCartSummary
import com.amaxonia.erp.ui.theme.FlowBrandBlue
import com.amaxonia.erp.ui.theme.FlowBrandGradient
import com.amaxonia.erp.ui.theme.FlowDiscountCoral
import com.amaxonia.erp.ui.theme.FlowDiscountGradient
import com.amaxonia.erp.ui.theme.FlowQuickAnticipo
import com.amaxonia.erp.ui.theme.FlowQuickApartado
import com.amaxonia.erp.ui.theme.FlowQuickCertificado
import com.amaxonia.erp.ui.theme.FlowQuickPreorden
import com.amaxonia.erp.ui.theme.FlowQuickPresupuesto
import com.amaxonia.erp.ui.theme.FlowQuickPromo
import com.amaxonia.erp.ui.theme.FlowQuickPuntos
import com.amaxonia.erp.ui.theme.FlowTableBorder
import java.util.Locale

/**
 * Controles de Facturación de la Columna Izquierda (Compactos 1:1 Web):
 * - Fila 1: Cliente & Vendedor
 * - Fila 2: Búsqueda de Producto + Fila horizontal scrolleable de 7 Botones de Ordenes
 * - Fila 3: Observación
 */
@Composable
fun PosInvoiceFormControls(
    client: Client?,
    seller: SellerSummary?,
    observationText: String,
    customClientName: String = "",
    onSelectClient: () -> Unit,
    onSelectSeller: () -> Unit,
    onSearchSellerByCode: (String) -> Unit = {},
    onOpenCustomClientNameDialog: () -> Unit = {},
    onAddProductByCode: (String) -> Unit,
    onObservationChange: (String) -> Unit,
    onQuickActionClick: (String) -> Unit = {},
    onOpenProductDialog: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var productInput by remember { mutableStateOf("") }

    val clientText = when {
        client?.code == "CF" && customClientName.isNotBlank() -> "${client.identification} - $customClientName"
        client != null -> "${client.identification} - ${client.name}"
        else -> "CF - CONSUMIDOR FINAL"
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        // --- Fila 1: Cliente & Vendedor ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            // Control Cliente
            PosInputPill(
                icon = Icons.Default.Person,
                badgeLabel = "Cliente",
                text = clientText,
                modifier = Modifier.weight(1.2f),
                onBadgeClick = onSelectClient,
                actionButtons = {
                    PosPillButton(icon = Icons.Default.Search, tooltip = "Buscar Cliente", onClick = onSelectClient)
                    PosPillButton(icon = Icons.Default.Edit, tooltip = "Facturar a nombre de", onClick = onOpenCustomClientNameDialog)
                }
            )

            // Control Vendedor
            PosInputPill(
                icon = Icons.Default.Engineering,
                badgeLabel = "Vendedor",
                prefixCode = seller?.id?.toString()?.padStart(3, '0') ?: "---",
                text = seller?.nombre ?: "Sin Asignar",
                modifier = Modifier.weight(1f),
                onBadgeClick = onSelectSeller,
                actionButtons = {
                    PosPillButton(icon = Icons.Default.Search, tooltip = "Buscar Vendedor", onClick = onSelectSeller)
                }
            )
        }

        // --- Fila 2: Producto & Botones de Acción Rápida con Scroll Horizontal ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Entrada directa de código / Selector de Producto
            PosEditableInputPill(
                icon = Icons.Default.Inventory2,
                badgeLabel = "Producto",
                placeholder = "Código o lector...",
                value = productInput,
                onValueChange = { productInput = it },
                onBadgeClick = { onOpenProductDialog(productInput) },
                onSubmit = {
                    if (productInput.isNotBlank()) {
                        onAddProductByCode(productInput)
                        productInput = ""
                    } else {
                        onOpenProductDialog("")
                    }
                },
                modifier = Modifier.weight(0.9f),
                actionButtons = {
                    PosPillButton(
                        icon = Icons.Default.Search,
                        tooltip = "Buscar Productos",
                        onClick = {
                            onOpenProductDialog(productInput)
                        }
                    )
                }
            )

            // 7 Botones Rápidos con desplazamiento horizontal suave
            Row(
                modifier = Modifier
                    .weight(1.3f)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                QuickActionButton(
                    label = "Pre-Orden",
                    icon = Icons.AutoMirrored.Filled.ListAlt,
                    tint = FlowQuickPreorden,
                    onClick = { onQuickActionClick("Pre-Orden") }
                )
                QuickActionButton(
                    label = "Apartado",
                    icon = Icons.Default.WorkOutline,
                    tint = FlowQuickApartado,
                    onClick = { onQuickActionClick("Apartado") }
                )
                QuickActionButton(
                    label = "Presupuesto",
                    icon = Icons.Default.Description,
                    tint = FlowQuickPresupuesto,
                    onClick = { onQuickActionClick("Presupuesto") }
                )
                QuickActionButton(
                    label = "Anticipo",
                    icon = Icons.Default.BusinessCenter,
                    tint = FlowQuickAnticipo,
                    onClick = { onQuickActionClick("Anticipo") }
                )
                QuickActionButton(
                    label = "Certificado",
                    icon = Icons.Default.ConfirmationNumber,
                    tint = FlowQuickCertificado,
                    onClick = { onQuickActionClick("Certificado") }
                )
                QuickActionButton(
                    label = "Puntos",
                    icon = Icons.Default.Stars,
                    tint = FlowQuickPuntos,
                    onClick = { onQuickActionClick("Puntos") }
                )
                QuickActionButton(
                    label = "Promoción",
                    icon = Icons.Default.CardGiftcard,
                    tint = FlowQuickPromo,
                    onClick = { onQuickActionClick("Promoción") }
                )
            }
        }

        // --- Fila 3: Observación ---
        PosEditableInputPill(
            icon = Icons.Default.Description,
            badgeLabel = "Observación",
            placeholder = "Observación o nota adicional...",
            value = observationText,
            onValueChange = onObservationChange,
            onSubmit = {},
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * Tarjetas de Acción de Totales de la Columna Derecha (Compactas 1:1 Web):
 * - Tarjeta Coral: Descuento acumulado
 * - Tarjeta Azul Real: Botón grande Pagar
 */
@Composable
fun PosTotalsActionCards(
    summary: PosCartSummary,
    globalDiscountPercent: Double,
    onOpenPayment: () -> Unit,
    onOpenGlobalDiscountDialog: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        // --- Tarjeta 1: Descuento (Roja / Coral con Degradado Flow) ---
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(34.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(FlowDiscountGradient)
                .clickable { onOpenGlobalDiscountDialog() }
                .padding(horizontal = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.White.copy(alpha = 0.25f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "DESC. ${String.format(Locale.US, "%.0f", globalDiscountPercent)}%",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = "$${String.format(Locale.US, "%.2f", summary.discountTotal)}",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }

        // --- Tarjeta 2: Pagar (Azul Real con Degradado Flow) ---
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(FlowBrandGradient)
                .clickable { onOpenPayment() }
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Pagar",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                )

                Text(
                    text = "$${String.format(Locale.US, "%.2f", summary.total)}",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

/**
 * Sección combinada para compatibilidad (Controles a la izquierda + Tarjetas a la derecha)
 */
@Composable
fun PosTopControlsSection(
    client: Client?,
    seller: SellerSummary?,
    summary: PosCartSummary,
    globalDiscountPercent: Double,
    observationText: String,
    customClientName: String = "",
    onSelectClient: () -> Unit,
    onSelectSeller: () -> Unit,
    onSearchSellerByCode: (String) -> Unit = {},
    onOpenCustomClientNameDialog: () -> Unit = {},
    onOpenGlobalDiscountDialog: () -> Unit = {},
    onAddProductByCode: (String) -> Unit,
    onObservationChange: (String) -> Unit,
    onOpenPayment: () -> Unit,
    onQuickActionClick: (String) -> Unit = {},
    onOpenProductDialog: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        PosInvoiceFormControls(
            client = client,
            seller = seller,
            observationText = observationText,
            customClientName = customClientName,
            onSelectClient = onSelectClient,
            onSelectSeller = onSelectSeller,
            onSearchSellerByCode = onSearchSellerByCode,
            onOpenCustomClientNameDialog = onOpenCustomClientNameDialog,
            onAddProductByCode = onAddProductByCode,
            onObservationChange = onObservationChange,
            onQuickActionClick = onQuickActionClick,
            onOpenProductDialog = onOpenProductDialog,
            modifier = Modifier.weight(1f),
        )

        PosTotalsActionCards(
            summary = summary,
            globalDiscountPercent = globalDiscountPercent,
            onOpenPayment = onOpenPayment,
            onOpenGlobalDiscountDialog = onOpenGlobalDiscountDialog,
            modifier = Modifier.width(220.dp),
        )
    }
}

// ═══════════════════════════════════════════════════════════════
// COMPONENTES AUXILIARES COMPACTOS (30-32dp)
// ═══════════════════════════════════════════════════════════════

@Composable
private fun PosInputPill(
    icon: ImageVector,
    badgeLabel: String,
    text: String,
    prefixCode: String? = null,
    onBadgeClick: () -> Unit = {},
    actionButtons: @Composable () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .height(32.dp)
            .clip(RoundedCornerShape(6.dp))
            .border(1.dp, FlowTableBorder, RoundedCornerShape(6.dp))
            .background(Color.White),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Badge izquierdo (gris azulado con icono y etiqueta)
        Row(
            modifier = Modifier
                .fillMaxHeight()
                .background(Color(0xFFEFF3F8))
                .clickable { onBadgeClick() }
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = badgeLabel,
                tint = FlowBrandBlue,
                modifier = Modifier.size(13.dp),
            )
            Text(
                text = badgeLabel,
                color = Color(0xFF334155),
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }

        // Si hay código de prefijo (e.g., 001)
        if (prefixCode != null) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(horizontal = 5.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = prefixCode,
                    color = Color(0xFF475569),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .fillMaxHeight(0.6f)
                    .background(FlowTableBorder)
            )
        }

        // Texto principal
        Text(
            text = text,
            color = Color(0xFF1E293B),
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 6.dp),
        )

        // Botones de acción anexados
        Row(
            modifier = Modifier.fillMaxHeight(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            actionButtons()
        }
    }
}

@Composable
private fun PosEditableInputPill(
    icon: ImageVector,
    badgeLabel: String,
    placeholder: String,
    value: String,
    onValueChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onBadgeClick: () -> Unit = {},
    modifier: Modifier = Modifier,
    actionButtons: @Composable () -> Unit = {},
) {
    Row(
        modifier = modifier
            .height(32.dp)
            .clip(RoundedCornerShape(6.dp))
            .border(1.dp, FlowTableBorder, RoundedCornerShape(6.dp))
            .background(Color.White),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Badge izquierdo
        Row(
            modifier = Modifier
                .fillMaxHeight()
                .background(Color(0xFFEFF3F8))
                .clickable { onBadgeClick() }
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = badgeLabel,
                tint = FlowBrandBlue,
                modifier = Modifier.size(13.dp),
            )
            Text(
                text = badgeLabel,
                color = Color(0xFF334155),
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }

        // Input de texto
        Box(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 6.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (value.isEmpty()) {
                Text(
                    text = placeholder,
                    color = Color(0xFF94A3B8),
                    fontSize = 11.sp,
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = TextStyle(
                    color = Color(0xFF1E293B),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Normal,
                ),
                cursorBrush = SolidColor(FlowBrandBlue),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Row(
            modifier = Modifier.fillMaxHeight(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            actionButtons()
        }
    }
}

@Composable
private fun PosPillButton(
    icon: ImageVector,
    tooltip: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxHeight()
            .width(28.dp)
            .background(FlowBrandBlue)
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = tooltip,
            tint = Color.White,
            modifier = Modifier.size(13.dp),
        )
    }
}

@Composable
private fun QuickActionButton(
    label: String,
    icon: ImageVector,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .height(28.dp)
            .clip(RoundedCornerShape(5.dp))
            .clickable { onClick() },
        shape = RoundedCornerShape(5.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = androidx.compose.foundation.BorderStroke(1.dp, FlowTableBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxHeight()
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = tint,
                modifier = Modifier.size(12.dp),
            )
            Text(
                text = label,
                color = Color(0xFF334155),
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
            )
        }
    }
}
