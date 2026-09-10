package com.amaxonia.pos.ui.cart

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.Percent
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amaxonia.pos.ui.common.components.AdaptiveAmountOptions
import com.amaxonia.pos.ui.common.components.AdaptiveAmountText
import com.amaxonia.pos.ui.common.components.QuantityStepper
import com.amaxonia.pos.ui.theme.PosTextStyles
import java.util.Locale

/** Máximo de dígitos aceptados al escribir una cantidad manual en el carrito. */
internal const val MAX_QUANTITY_DIGITS = 5

internal fun sanitizeQuantityInput(value: String): String =
    value
        .filter { it.isDigit() }
        .trimStart('0')
        .ifBlank { "" }
        .take(MAX_QUANTITY_DIGITS)

/** Acciones de edición (precio/descuento) del renglón de carrito. */
class CartItemEditActions(
    val onEditPrice: () -> Unit,
    val onEditDiscount: () -> Unit,
    val onPriceLevelChange: (String) -> Unit = {},
)

class CartItemActions(
    val onIncrease: () -> Unit,
    val onDecrease: () -> Unit,
    val onRemove: () -> Unit,
    val onUnitChange: (String) -> Unit,
    val onQuantityChange: (Int) -> Unit,
    val edit: CartItemEditActions,
) {
    val onPriceLevelChange: (String) -> Unit
        get() = edit.onPriceLevelChange
}

@Composable
fun CartItemRow(
    item: com.amaxonia.pos.domain.model.CartItem,
    actions: CartItemActions,
    allowEditPrice: Boolean,
    allowDiscount: Boolean,
) {
    androidx.compose.material3.ElevatedCard(
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.5.dp),
        shape =
            androidx.compose.foundation.shape
                .RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(14.dp).fillMaxWidth()) {
            CartItemHeaderRow(item = item, onRemove = actions.onRemove)

            Spacer(modifier = Modifier.height(6.dp))

            CartItemPriceRow(item = item)

            Spacer(modifier = Modifier.height(10.dp))

            CartItemQuantityRow(
                item = item,
                actions = actions,
                allowEditPrice = allowEditPrice,
                allowDiscount = allowDiscount,
            )

            if (!item.isPromotionLine) {
                Spacer(modifier = Modifier.height(8.dp))
                CartItemPriceLevelSelector(
                    item = item,
                    onPriceLevelChange = actions.onPriceLevelChange,
                )
            }

            if (item.product.canSwitchUnit) {
                Spacer(modifier = Modifier.height(6.dp))
                CartItemUnitSelector(item = item, onUnitChange = actions.onUnitChange)
            }

            CartItemFootnotes(item = item)
        }
    }
}

/**
 * Estado editable de la cantidad del renglón: texto del stepper, validación y
 * transiciones hacia los callbacks del carrito. Se recrea al cambiar ítem/cantidad.
 */
private class CartQuantityController(
    initialQuantity: Int,
    private val onQuantityChange: (Int) -> Unit,
    private val onDecrease: () -> Unit,
    private val onIncrease: () -> Unit,
    private val onRemove: () -> Unit,
) {
    var text by mutableStateOf(initialQuantity.toString())
    val typedQuantity: Int get() = text.toIntOrNull() ?: 0
    val isError: Boolean get() = text.isNotBlank() && typedQuantity < 1

    fun onTextChange(value: String) {
        text = sanitizeQuantityInput(value)
        text.toIntOrNull()?.takeIf { it >= 1 }?.let(onQuantityChange)
    }

    fun decrease(currentQuantity: Int) {
        val next = currentQuantity - 1
        if (next <= 0) {
            onRemove()
        } else {
            text = next.toString()
            onDecrease()
        }
    }

    fun increase(currentQuantity: Int) {
        text = (currentQuantity + 1).toString()
        onIncrease()
    }

    fun done() {
        if (typedQuantity >= 1) onQuantityChange(typedQuantity)
    }
}

/** Fila 1: descripción (flexible) + eliminar (target ≥48dp vía minimum interactive). */
@Composable
private fun CartItemHeaderRow(
    item: com.amaxonia.pos.domain.model.CartItem,
    onRemove: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            item.product.description,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        IconButton(
            onClick = onRemove,
            modifier = Modifier.size(36.dp),
        ) {
            Icon(
                Icons.Default.Delete,
                contentDescription = "Quitar del carrito",
                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.80f),
                modifier = Modifier.size(19.dp),
            )
        }
    }
}

/** Fila 2: precio unitario (flexible) + total de línea en badge destacado. */
@Composable
private fun CartItemPriceRow(item: com.amaxonia.pos.domain.model.CartItem) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "$ ${String.format(
                Locale.getDefault(),
                "%.2f",
                item.unitPriceWithTax,
            )} / ${item.displayUnitLabel.lowercase()}",
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        Spacer(modifier = Modifier.weight(1f))
        Surface(
            shape =
                androidx.compose.foundation.shape
                    .RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.50f),
        ) {
            AdaptiveAmountText(
                text = "$ ${String.format(Locale.getDefault(), "%.2f", item.total)}",
                baseStyle = PosTextStyles.priceTileLarge.copy(fontSize = 16.sp),
                color = MaterialTheme.colorScheme.primary,
                options = AdaptiveAmountOptions(minFontSizeSp = 12f),
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
    }
}

/**
 * Fila 3: stepper (flexible) + acciones de precio/descuento con targets ≥48dp
 * y botones tonales redondeados.
 */
@Composable
private fun CartItemQuantityRow(
    item: com.amaxonia.pos.domain.model.CartItem,
    actions: CartItemActions,
    allowEditPrice: Boolean,
    allowDiscount: Boolean,
) {
    val controller =
        remember(item.product.id, item.quantity) {
            CartQuantityController(
                initialQuantity = item.quantity,
                onQuantityChange = actions.onQuantityChange,
                onDecrease = actions.onDecrease,
                onIncrease = actions.onIncrease,
                onRemove = actions.onRemove,
            )
        }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        QuantityStepper(
            quantityText = controller.text,
            onQuantityTextChange = controller::onTextChange,
            onDecrease = { controller.decrease(item.quantity) },
            onIncrease = { controller.increase(item.quantity) },
            onDone = controller::done,
            isError = controller.isError,
            label = "Cantidad",
            modifier = Modifier.weight(1f),
        )
        CartItemEditButtons(
            allowEditPrice = allowEditPrice,
            allowDiscount = allowDiscount,
            onEditPrice = actions.edit.onEditPrice,
            onEditDiscount = actions.edit.onEditDiscount,
        )
    }
}

@Composable
private fun CartItemEditButtons(
    allowEditPrice: Boolean,
    allowDiscount: Boolean,
    onEditPrice: () -> Unit,
    onEditDiscount: () -> Unit,
) {
    if (allowEditPrice) {
        Surface(
            shape =
                androidx.compose.foundation.shape
                    .RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
            modifier = Modifier.size(40.dp),
        ) {
            IconButton(onClick = onEditPrice, modifier = Modifier.fillMaxSize()) {
                Icon(
                    Icons.Default.Edit,
                    contentDescription = "Editar precio",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
    if (allowDiscount) {
        Surface(
            shape =
                androidx.compose.foundation.shape
                    .RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.55f),
            modifier = Modifier.size(40.dp),
        ) {
            IconButton(onClick = onEditDiscount, modifier = Modifier.fillMaxSize()) {
                Icon(
                    Icons.Default.Percent,
                    contentDescription = "Aplicar descuento",
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun CartItemPriceLevelSelector(
    item: com.amaxonia.pos.domain.model.CartItem,
    onPriceLevelChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var priceMenuExpanded by remember { mutableStateOf(false) }
    val availableLevels =
        remember(item.product.prices) {
            val positive = item.product.prices.filter { it.pricePlusTax > 0.0 || it.price > 0.0 }
            if (positive.isNotEmpty()) positive else item.product.prices
        }

    val currentLabelText =
        if (item.isManualPrice) {
            "Precio Manual"
        } else {
            "Lista ${item.selectedPriceLabel}"
        }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier,
    ) {
        Text(
            "Precio:",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.width(6.dp))
        Box {
            AssistChip(
                onClick = { priceMenuExpanded = true },
                label = {
                    Text(
                        currentLabelText,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                    )
                },
                leadingIcon = {
                    Icon(
                        Icons.Default.LocalOffer,
                        contentDescription = "Cambiar lista de precios",
                        modifier = Modifier.size(14.dp),
                    )
                },
                shape =
                    androidx.compose.foundation.shape
                        .RoundedCornerShape(8.dp),
            )
            DropdownMenu(
                expanded = priceMenuExpanded,
                onDismissRequest = { priceMenuExpanded = false },
            ) {
                availableLevels.forEach { level ->
                    val isSelected =
                        !item.isManualPrice && item.selectedPriceLabel.equals(level.label, ignoreCase = true)
                    val levelPrice =
                        if (item.itemUnitPackage == "EMPAQUE" || item.product.bulkQuantity <= 1.0) {
                            level.pricePlusTax.takeIf { it > 0.0 } ?: level.price
                        } else {
                            level.unitPricePlusTax.takeIf { it > 0.0 } ?: level.unitPrice.takeIf { it > 0.0 } ?: level.pricePlusTax
                        }
                    val formattedPrice = String.format(Locale.getDefault(), "%.2f", levelPrice)

                    DropdownMenuItem(
                        text = {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    "Lista ${level.label}",
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color =
                                        if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                                Text(
                                    "$ $formattedPrice",
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 13.sp,
                                )
                            }
                        },
                        onClick = {
                            priceMenuExpanded = false
                            onPriceLevelChange(level.label)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun CartItemUnitSelector(
    item: com.amaxonia.pos.domain.model.CartItem,
    onUnitChange: (String) -> Unit,
) {
    var unitMenuExpanded by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            "Unidad:",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.width(8.dp))
        Box {
            AssistChip(
                onClick = { unitMenuExpanded = true },
                label = { Text(item.displayUnitLabel, fontWeight = FontWeight.Bold) },
                leadingIcon = {
                    Icon(Icons.Default.Autorenew, contentDescription = null, modifier = Modifier.size(15.dp))
                },
                shape =
                    androidx.compose.foundation.shape
                        .RoundedCornerShape(8.dp),
            )
            DropdownMenu(
                expanded = unitMenuExpanded,
                onDismissRequest = { unitMenuExpanded = false },
            ) {
                DropdownMenuItem(
                    text = { Text("UNIDAD") },
                    onClick = {
                        unitMenuExpanded = false
                        onUnitChange("UNIDAD")
                    },
                )
                DropdownMenuItem(
                    text = { Text(item.product.packageLabel) },
                    onClick = {
                        unitMenuExpanded = false
                        onUnitChange("EMPAQUE")
                    },
                )
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            "Total unidades: ${String.format(Locale.getDefault(), "%.2f", item.quantityTotal)}",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
        )
    }
}

/** Descuento y lotes asignados, secciones condicionales al pie de la tarjeta. */
@Composable
private fun CartItemFootnotes(item: com.amaxonia.pos.domain.model.CartItem) {
    // Descuento (condicional)
    if (item.discountPercent > 0.0) {
        Surface(
            shape =
                androidx.compose.foundation.shape
                    .RoundedCornerShape(6.dp),
            color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.40f),
            modifier = Modifier.padding(top = 6.dp),
        ) {
            Text(
                "Desc: ${String.format(
                    Locale.getDefault(),
                    "%.2f",
                    item.discountPercent,
                )}% (-$ ${String.format(Locale.getDefault(), "%.2f", item.discountAmountWithoutTax)})",
                color = MaterialTheme.colorScheme.onTertiaryContainer,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            )
        }
    }

    // Lotes asignados (condicional)
    if (item.lotAssignments.isNotEmpty()) {
        Spacer(modifier = Modifier.height(6.dp))
        item.lotAssignments.forEach { lot ->
            val expiry = if (!lot.vencimiento.isNullOrBlank()) " · Vence: ${lot.vencimiento}" else ""
            Surface(
                shape =
                    androidx.compose.foundation.shape
                        .RoundedCornerShape(6.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                modifier = Modifier.padding(top = 2.dp),
            ) {
                Text(
                    "Lote: ${lot.codigoLote} (${lot.cantidad} uds$expiry)",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }
        }
    }
}
