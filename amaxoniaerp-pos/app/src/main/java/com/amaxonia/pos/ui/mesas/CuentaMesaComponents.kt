@file:Suppress("LongMethod", "MagicNumber")

package com.amaxonia.pos.ui.mesas

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.RestaurantMenu
import androidx.compose.material.icons.filled.Splitscreen
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.amaxonia.pos.domain.model.mesas.CuentaMesaResponse
import com.amaxonia.pos.domain.model.mesas.EstadoCuentaMesa
import com.amaxonia.pos.domain.model.mesas.PedidoMesa
import com.amaxonia.pos.ui.common.components.AdaptiveAmountOptions
import com.amaxonia.pos.ui.common.components.AdaptiveAmountText
import com.amaxonia.pos.ui.common.components.PosEmptyState
import com.amaxonia.pos.ui.common.components.PosFeedbackCard
import com.amaxonia.pos.ui.common.components.PosStatusBadge
import com.amaxonia.pos.ui.common.components.PosVisualAction
import com.amaxonia.pos.ui.common.components.PosVisualTone
import java.util.Locale

/** Selector principal de modo: Cuenta Completa vs Dividir Cuenta */
@Composable
fun CuentaModoSelector(
    modoSeleccionado: CuentaModo,
    totalCompleto: Double,
    totalDisponibleProductos: Int,
    cuentasActivasCount: Int,
    onModoChange: (CuentaModo) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Tarjeta Modo 1: Cuenta Completa
        ModoDecisionCard(
            isSelected = modoSeleccionado == CuentaModo.COMPLETA,
            title = "Cuenta Completa",
            subtitle = if (totalDisponibleProductos > 0) "$totalDisponibleProductos productos" else "Todo el consumo",
            icon = Icons.AutoMirrored.Filled.ReceiptLong,
            amount = totalCompleto,
            modifier = Modifier.weight(1f),
            onClick = { onModoChange(CuentaModo.COMPLETA) },
        )

        // Tarjeta Modo 2: Dividir Cuenta
        ModoDecisionCard(
            isSelected = modoSeleccionado == CuentaModo.DIVIDIR,
            title = "Dividir Cuenta",
            subtitle = "Por persona o ítems",
            icon = Icons.Default.Splitscreen,
            badge = if (cuentasActivasCount > 0) "$cuentasActivasCount creadas" else null,
            modifier = Modifier.weight(1f),
            onClick = { onModoChange(CuentaModo.DIVIDIR) },
        )
    }
}

@Composable
private fun ModoDecisionCard(
    isSelected: Boolean,
    title: String,
    subtitle: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    amount: Double? = null,
    badge: String? = null,
    onClick: () -> Unit,
) {
    val containerColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
        label = "containerColor",
    )
    val contentColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
        label = "contentColor",
    )
    val subtitleColor by animateColorAsState(
        targetValue =
            if (isSelected) {
                MaterialTheme.colorScheme.onPrimary.copy(
                    alpha = 0.85f,
                )
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        label = "subtitleColor",
    )
    val borderColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant

    Card(
        modifier =
            modifier
                .clip(MaterialTheme.shapes.extraLarge)
                .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(if (isSelected) 2.dp else 1.dp, borderColor),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 4.dp else 0.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    shape = CircleShape,
                    color =
                        if (isSelected) {
                            MaterialTheme.colorScheme.onPrimary.copy(
                                alpha = 0.2f,
                            )
                        } else {
                            MaterialTheme.colorScheme.primaryContainer
                        },
                    contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(40.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(22.dp))
                    }
                }

                if (isSelected) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.onPrimary,
                        contentColor = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                    }
                } else if (badge != null) {
                    PosStatusBadge(label = badge, tone = PosVisualTone.Pending)
                }
            }

            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = contentColor,
            )

            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = subtitleColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            if (amount != null) {
                AdaptiveAmountText(
                    text = formatMoney(amount),
                    baseStyle = MaterialTheme.typography.titleLarge,
                    color = contentColor,
                    options =
                        AdaptiveAmountOptions(
                            fontWeight = FontWeight.ExtraBold,
                            minFontSizeSp = 14f,
                            maxLines = 1,
                        ),
                )
            }
        }
    }
}

/** Tarjeta compacta del cliente asignado */
@Composable
fun CuentaClienteBar(
    clientName: String?,
    onSelectClient: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Person,
                contentDescription = null,
                tint = if (clientName != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Cliente para factura",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = clientName ?: "Consumidor Final (Sin asignar)",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (clientName != null) FontWeight.Bold else FontWeight.Normal,
                    color = if (clientName != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            TextButton(
                onClick = onSelectClient,
                modifier = Modifier.heightIn(min = 36.dp),
            ) {
                Text(
                    text = if (clientName == null) "Asignar" else "Cambiar",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

/** Banner / Alerta cuando hay cuentas activas pendientes de cobro */
@Composable
fun CuentaActivasBanner(
    cuentasActivasCount: Int,
    onClickVerCuentas: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.large)
                .clickable(onClick = onClickVerCuentas),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
        shape = MaterialTheme.shapes.large,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ReceiptLong,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.size(24.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "$cuentasActivasCount cuenta(s) por cobrar",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
                Text(
                    text = "Toca para ver el detalle y realizar el cobro",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f),
                )
            }
            FilledTonalButton(
                onClick = onClickVerCuentas,
                colors =
                    ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    ),
            ) {
                Text("Ver")
            }
        }
    }
}

/** Lista de resumen de consumo (Modo Cuenta Completa) */
@Composable
fun CuentaConsumoResumen(
    pedidos: List<PedidoMesa>,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "Consumo pendiente de la mesa",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            if (pedidos.isEmpty()) {
                PosEmptyState(
                    icon = Icons.Default.RestaurantMenu,
                    title = "Sin productos pendientes",
                    message = "Todo el consumo de esta mesa ya fue facturado o no hay pedidos entregados.",
                )
            } else {
                pedidos.forEach { pedido ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.padding(end = 10.dp),
                        ) {
                            Text(
                                text = "${formatQuantity(pedido.cantidadPendiente)}×",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = pedido.itemDescripcion.ifBlank { "Producto ${pedido.productoId}" },
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (pedido.itemCantidad > 0) {
                                val unitPrice = pedido.itemTotalConIva / pedido.itemCantidad
                                Text(
                                    text = "c/u ${formatMoney(unitPrice)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }

                        val lineTotal =
                            if (pedido.itemCantidad > 0) {
                                (pedido.itemTotalConIva / pedido.itemCantidad) * pedido.cantidadPendiente
                            } else {
                                0.0
                            }

                        AdaptiveAmountText(
                            text = formatMoney(lineTotal),
                            baseStyle = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            options =
                                AdaptiveAmountOptions(
                                    fontWeight = FontWeight.SemiBold,
                                    minFontSizeSp = 12f,
                                    maxLines = 1,
                                    textAlign = TextAlign.End,
                                ),
                        )
                    }
                }
            }
        }
    }
}

/** Item interactivo para selección y división de cuenta */
@Composable
fun SplitProductInteractiveCard(
    pedido: PedidoMesa,
    disponible: Double,
    selectedQuantity: Double,
    onToggle: () -> Unit,
    onIncrease: () -> Unit,
    onDecrease: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isSelected = selectedQuantity > 0.0
    val unitPrice = if (pedido.itemCantidad > 0) pedido.itemTotalConIva / pedido.itemCantidad else 0.0
    val lineSubtotal = unitPrice * (if (isSelected) selectedQuantity else disponible)

    val containerColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surface,
        label = "splitCardBg",
    )
    val borderColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant

    OutlinedCard(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.outlinedCardColors(containerColor = containerColor),
        border = BorderStroke(if (isSelected) 1.5.dp else 1.dp, borderColor),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onToggle() },
                    colors =
                        CheckboxDefaults.colors(
                            checkedColor = MaterialTheme.colorScheme.primary,
                            uncheckedColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                )

                Spacer(Modifier.width(6.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = pedido.itemDescripcion.ifBlank { "Producto ${pedido.productoId}" },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "Disponible: ${formatQuantity(disponible)} · c/u ${formatMoney(unitPrice)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Spacer(Modifier.width(8.dp))

                AdaptiveAmountText(
                    text = formatMoney(lineSubtotal),
                    baseStyle = MaterialTheme.typography.titleSmall,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    options =
                        AdaptiveAmountOptions(
                            fontWeight = FontWeight.Bold,
                            minFontSizeSp = 12f,
                            maxLines = 1,
                            textAlign = TextAlign.End,
                        ),
                )
            }

            AnimatedVisibility(
                visible = isSelected,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .background(
                                MaterialTheme.colorScheme.surface,
                                RoundedCornerShape(10.dp),
                            ).padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Cantidad a dividir:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        IconButton(
                            onClick = onDecrease,
                            modifier = Modifier.size(36.dp),
                            colors =
                                IconButtonDefaults.iconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                ),
                        ) {
                            Icon(
                                imageVector = if (selectedQuantity <= 1.0) Icons.Default.Close else Icons.Default.Remove,
                                contentDescription = "Disminuir",
                                modifier = Modifier.size(18.dp),
                                tint =
                                    if (selectedQuantity <=
                                        1.0
                                    ) {
                                        MaterialTheme.colorScheme.error
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.height(36.dp),
                        ) {
                            Box(
                                modifier = Modifier.padding(horizontal = 14.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = formatQuantity(selectedQuantity),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                            }
                        }

                        IconButton(
                            onClick = onIncrease,
                            enabled = selectedQuantity < disponible,
                            modifier = Modifier.size(36.dp),
                            colors =
                                IconButtonDefaults.iconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary,
                                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
                                ),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Aumentar",
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Bottom Sheet modal para consultar el Historial de Cuentas */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CuentaHistoricoBottomSheet(
    historicas: List<CuentaMesaResponse>,
    sheetState: SheetState = rememberModalBottomSheetState(),
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(26.dp),
                    )
                    Text(
                        text = "Historial de Cuentas",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                }

                PosStatusBadge(
                    label = "${historicas.size} finalizadas",
                    tone = PosVisualTone.Neutral,
                )
            }

            HorizontalDivider()

            if (historicas.isEmpty()) {
                PosEmptyState(
                    icon = Icons.Default.History,
                    title = "Sin historial",
                    message = "Aún no se han completado ni cancelado cuentas en esta sesión.",
                )
            } else {
                historicas.forEach { cuenta ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                        shape = MaterialTheme.shapes.large,
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "Cuenta #${cuenta.numeroCuenta}",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                )

                                PosStatusBadge(
                                    label = if (cuenta.estado == EstadoCuentaMesa.PAGADA) "Pagada" else "Cancelada",
                                    tone = if (cuenta.estado == EstadoCuentaMesa.PAGADA) PosVisualTone.Success else PosVisualTone.Error,
                                )
                            }

                            cuenta.codFactura?.takeIf { it.isNotBlank() }?.let { cod ->
                                Text(
                                    text = "Factura: $cod",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }

                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "${cuenta.detalle.size} productos",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    text = formatMoney(cuenta.total),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Bottom Sheet modal para administrar y cobrar las Cuentas Activas */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CuentaActivasBottomSheet(
    cuentasActivas: List<CuentaMesaResponse>,
    canPay: Boolean,
    onPay: (CuentaMesaResponse) -> Unit,
    onCancel: (CuentaMesaResponse) -> Unit,
    onSelectClient: () -> Unit,
    sheetState: SheetState = rememberModalBottomSheetState(),
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ReceiptLong,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(26.dp),
                    )
                    Text(
                        text = "Cuentas por Cobrar",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                }

                PosStatusBadge(
                    label = "${cuentasActivas.size} activas",
                    tone = PosVisualTone.Pending,
                )
            }

            if (!canPay) {
                PosFeedbackCard(
                    title = "Cliente requerido",
                    message = "Asocia el cliente para poder cobrar estas cuentas.",
                    tone = PosVisualTone.Info,
                    action = PosVisualAction(label = "Asignar Cliente", onClick = onSelectClient),
                )
            }

            HorizontalDivider()

            if (cuentasActivas.isEmpty()) {
                PosEmptyState(
                    icon = Icons.AutoMirrored.Filled.ReceiptLong,
                    title = "Sin cuentas activas",
                    message = "Crea una cuenta completa o divide el consumo para comenzar el cobro.",
                )
            } else {
                cuentasActivas.forEach { cuenta ->
                    CuentaActivaCard(
                        cuenta = cuenta,
                        canPay = canPay,
                        onPay = { onPay(cuenta) },
                        onCancel = { onCancel(cuenta) },
                    )
                }
            }
        }
    }
}

fun formatMoney(value: Double): String = "$ ${String.format(Locale.US, "%.2f", value)}"

fun formatQuantity(value: Double): String =
    if (value % 1.0 == 0.0) {
        value.toLong().toString()
    } else {
        String.format(Locale.US, "%.3f", value).trimEnd('0').trimEnd('.')
    }
