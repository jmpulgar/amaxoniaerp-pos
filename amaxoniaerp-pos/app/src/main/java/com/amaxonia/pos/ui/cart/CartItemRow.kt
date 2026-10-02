package com.amaxonia.pos.ui.cart

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.Percent
import androidx.compose.material.icons.filled.Remove
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import com.amaxonia.pos.domain.model.CartItem
import com.amaxonia.pos.ui.common.components.AdaptiveAmountOptions
import com.amaxonia.pos.ui.common.components.AdaptiveAmountText
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
    item: CartItem,
    imageUrl: String = "",
    actions: CartItemActions,
    allowEditPrice: Boolean,
    allowDiscount: Boolean,
) {
    ElevatedCard(
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp).fillMaxWidth()) {
            // Sección Superior: Miniatura 44x44dp + (Título, Total, Metadata y Eliminar)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
            ) {
                ProductThumbnail(
                    imageUrl = imageUrl,
                    description = item.product.description,
                    modifier = Modifier.size(44.dp),
                )

                Spacer(modifier = Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    // Nivel 1: Título + Total de línea
                    CartItemTopRow(item = item)

                    Spacer(modifier = Modifier.height(3.dp))

                    // Nivel 2: Metadata (Precio unitario, Lista, Unidad) + Eliminar
                    CartItemMetadataRow(
                        item = item,
                        onRemove = actions.onRemove,
                        onPriceLevelChange = actions.onPriceLevelChange,
                        onUnitChange = actions.onUnitChange,
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Nivel 3: Stepper compacto + Acciones de edición / Descuento
            CartItemBottomRow(
                item = item,
                actions = actions,
                allowEditPrice = allowEditPrice,
                allowDiscount = allowDiscount,
            )

            // Lotes asignados (condicional)
            CartItemLotFootnotes(item = item)
        }
    }
}

/** Miniatura del producto con carga asíncrona y placeholder simétrico. */
@Composable
private fun ProductThumbnail(
    imageUrl: String,
    description: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        modifier = modifier.clip(RoundedCornerShape(8.dp)),
    ) {
        if (imageUrl.isNotBlank()) {
            SubcomposeAsyncImage(
                model = imageUrl,
                contentDescription = description,
                modifier = Modifier.fillMaxSize(),
                loading = {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Icon(
                            Icons.Default.Inventory2,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.40f),
                            modifier = Modifier.size(20.dp),
                        )
                    }
                },
                error = {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Icon(
                            Icons.Default.Inventory2,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.40f),
                            modifier = Modifier.size(20.dp),
                        )
                    }
                },
                success = {
                    SubcomposeAsyncImageContent(
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                },
            )
        } else {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Icon(
                    Icons.Default.Inventory2,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.40f),
                    modifier = Modifier.size(20.dp),
                )
            }
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

/** Nivel 1: Descripción del producto + Total destacado de línea. */
@Composable
private fun CartItemTopRow(
    item: CartItem,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = item.product.description,
            fontWeight = FontWeight.Bold,
            fontSize = 13.5.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurface,
            lineHeight = 17.sp,
            modifier = Modifier.weight(1f).padding(end = 6.dp),
        )
        AdaptiveAmountText(
            text = "$ ${String.format(Locale.getDefault(), "%.2f", item.total)}",
            baseStyle = PosTextStyles.priceTileLarge.copy(fontSize = 15.5.sp, fontWeight = FontWeight.ExtraBold),
            color = MaterialTheme.colorScheme.primary,
            options = AdaptiveAmountOptions(minFontSizeSp = 11f),
        )
    }
}

/** Nivel 2: Precio unitario con IVA + Chips compactos (Lista / Unidad) + Eliminar. */
@Composable
private fun CartItemMetadataRow(
    item: CartItem,
    onRemove: () -> Unit,
    onPriceLevelChange: (String) -> Unit,
    onUnitChange: (String) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Text(
                text = "$ ${String.format(
                    Locale.getDefault(),
                    "%.2f",
                    item.unitPriceWithTax,
                )}/${item.displayUnitLabel.lowercase()}",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            if (!item.isPromotionLine) {
                CompactPriceLevelChip(item = item, onPriceLevelChange = onPriceLevelChange)
            }

            if (item.product.canSwitchUnit) {
                CompactUnitChip(item = item, onUnitChange = onUnitChange)
            }
        }

        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f),
            modifier = Modifier.size(24.dp),
        ) {
            IconButton(onClick = onRemove, modifier = Modifier.fillMaxSize()) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Quitar del carrito",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(13.dp),
                )
            }
        }
    }
}

/** Selector compacto en cápsula para la lista de precios. */
@Composable
private fun CompactPriceLevelChip(
    item: CartItem,
    onPriceLevelChange: (String) -> Unit,
) {
    var priceMenuExpanded by remember { mutableStateOf(false) }
    val availableLevels =
        remember(item.product.prices) {
            val positive = item.product.prices.filter { it.pricePlusTax > 0.0 || it.price > 0.0 }
            if (positive.isNotEmpty()) positive else item.product.prices
        }
    val currentLabelText =
        if (item.isManualPrice) "Manual" else item.selectedPriceLabel

    Box {
        Surface(
            shape = RoundedCornerShape(6.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.60f),
            modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { priceMenuExpanded = true },
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
            ) {
                Icon(
                    Icons.Default.LocalOffer,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(11.dp),
                )
                Spacer(modifier = Modifier.width(3.dp))
                Text(
                    text = currentLabelText,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Icon(
                    Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(13.dp),
                )
            }
        }
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
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
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

/** Selector compacto en cápsula para el cambio de unidad. */
@Composable
private fun CompactUnitChip(
    item: CartItem,
    onUnitChange: (String) -> Unit,
) {
    var unitMenuExpanded by remember { mutableStateOf(false) }
    Box {
        Surface(
            shape = RoundedCornerShape(6.dp),
            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.50f),
            modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { unitMenuExpanded = true },
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
            ) {
                Icon(
                    Icons.Default.Autorenew,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(11.dp),
                )
                Spacer(modifier = Modifier.width(3.dp))
                Text(
                    text = item.displayUnitLabel,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.5.sp,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Icon(
                    Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(13.dp),
                )
            }
        }
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
}

/** Nivel 3: Stepper de cantidad compacto (32dp) + Descuento + Botones de precio/descuento. */
@Composable
private fun CartItemBottomRow(
    item: CartItem,
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
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        CompactQuantityStepper(
            quantityText = controller.text,
            onQuantityTextChange = controller::onTextChange,
            onDecrease = { controller.decrease(item.quantity) },
            onIncrease = { controller.increase(item.quantity) },
            onDone = controller::done,
            isError = controller.isError,
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (item.discountPercent > 0.0) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.50f),
                ) {
                    Text(
                        "-${String.format(Locale.getDefault(), "%.0f", item.discountPercent)}%",
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                    )
                }
            }

            if (allowEditPrice) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                    modifier = Modifier.size(32.dp),
                ) {
                    IconButton(onClick = actions.edit.onEditPrice, modifier = Modifier.fillMaxSize()) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "Editar precio",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(15.dp),
                        )
                    }
                }
            }

            if (allowDiscount) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.55f),
                    modifier = Modifier.size(32.dp),
                ) {
                    IconButton(onClick = actions.edit.onEditDiscount, modifier = Modifier.fillMaxSize()) {
                        Icon(
                            Icons.Default.Percent,
                            contentDescription = "Aplicar descuento",
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(15.dp),
                        )
                    }
                }
            }
        }
    }
}

/** Stepper compacto de 32dp de altura sin etiquetas flotantes pesadas. */
@Composable
private fun CompactQuantityStepper(
    quantityText: String,
    onQuantityTextChange: (String) -> Unit,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    onDone: () -> Unit,
    isError: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.70f),
            modifier = Modifier.size(32.dp),
        ) {
            IconButton(onClick = onDecrease, modifier = Modifier.fillMaxSize()) {
                Icon(
                    Icons.Default.Remove,
                    contentDescription = "Disminuir cantidad",
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(16.dp),
                )
            }
        }

        BasicTextField(
            value = quantityText,
            onValueChange = onQuantityTextChange,
            singleLine = true,
            textStyle =
                TextStyle(
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                ),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onDone() }),
            modifier =
                Modifier
                    .width(42.dp)
                    .height(32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (isError) {
                            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.50f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.50f)
                        },
                    ),
            decorationBox = { innerTextField ->
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    innerTextField()
                }
            },
        )

        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(32.dp),
        ) {
            IconButton(onClick = onIncrease, modifier = Modifier.fillMaxSize()) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = "Aumentar cantidad",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

/** Lotes asignados condicionales al pie de la tarjeta. */
@Composable
private fun CartItemLotFootnotes(item: CartItem) {
    if (item.lotAssignments.isNotEmpty()) {
        Spacer(modifier = Modifier.height(4.dp))
        item.lotAssignments.forEach { lot ->
            val expiry = if (!lot.vencimiento.isNullOrBlank()) " · Vence: ${lot.vencimiento}" else ""
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                modifier = Modifier.padding(top = 2.dp),
            ) {
                Text(
                    "Lote: ${lot.codigoLote} (${lot.cantidad} uds$expiry)",
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }
    }
}
