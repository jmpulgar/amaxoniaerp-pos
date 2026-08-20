package com.amaxonia.pos.ui.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.amaxonia.pos.core.logging.SafeLog
import com.amaxonia.pos.ui.common.components.AdaptiveAmountOptions
import com.amaxonia.pos.ui.common.components.AdaptiveAmountText
import com.amaxonia.pos.ui.theme.PosPalette
import com.amaxonia.pos.ui.theme.PosTextStyles
import java.util.Locale

/** Tarjeta de producto en cuadrícula con imagen flexible y acciones. */
@Composable
fun ProductCard(
    product: DashboardProduct,
    onAddClick: () -> Unit,
    onQuantityClick: () -> Unit,
) {
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth().height(240.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp).fillMaxSize()) {
            ProductCardImage(product = product, modifier = Modifier.weight(1f))
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = product.name,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (!product.code.isNullOrBlank()) {
                Text(
                    text = "Ref: ${product.code}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            // Precio en su propia fila (adaptive: montos grandes encogen sin recortarse).
            AdaptiveAmountText(
                text = "$${String.format(Locale.getDefault(), "%.2f", product.price)}",
                baseStyle = PosTextStyles.priceTileLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.fillMaxWidth(),
                options =
                    AdaptiveAmountOptions(
                        minFontSizeSp = 12f,
                        maxLines = 1,
                    ),
            )
            Spacer(modifier = Modifier.height(6.dp))
            // Acciones alineadas a la derecha; targets ≥48dp vía minimum interactive.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ProductTileActions(
                    onAddClick = onAddClick,
                    onQuantityClick = onQuantityClick,
                )
            }
        }
    }
}

/**
 * Imagen flexible de la tarjeta: absorbe el alto restante para que la tarjeta nunca
 * desborde en columnas estrechas (320dp → 2 columnas de ~138dp).
 */
@Composable
private fun ProductCardImage(
    product: DashboardProduct,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.small)
                .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        ProductImageContent(product = product, fallbackIconSize = 44.dp, logImageLoad = true)
    }
}

/** Fila compacta de producto para el modo lista. */
@Composable
fun ProductListRow(
    product: DashboardProduct,
    onAddClick: () -> Unit,
    onQuantityClick: () -> Unit,
) {
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ProductListRowImage(product = product)
            Spacer(Modifier.width(12.dp))
            ProductListRowInfo(product = product, modifier = Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                ProductTileActions(
                    onAddClick = onAddClick,
                    onQuantityClick = onQuantityClick,
                )
            }
        }
    }
}

/** Miniatura cuadrada del producto en modo lista. */
@Composable
private fun ProductListRowImage(product: DashboardProduct) {
    Box(
        modifier =
            Modifier
                .size(56.dp)
                .clip(MaterialTheme.shapes.small)
                .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        ProductImageContent(product = product, fallbackIconSize = 28.dp, logImageLoad = false)
    }
}

/** Nombre, referencia y precio adaptativo de la fila. */
@Composable
private fun ProductListRowInfo(
    product: DashboardProduct,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = product.name,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (!product.code.isNullOrBlank()) {
            Text(
                text = "Ref: ${product.code}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        } else {
            Spacer(Modifier.height(4.dp))
        }
        AdaptiveAmountText(
            text = "$${String.format(Locale.getDefault(), "%.2f", product.price)}",
            baseStyle = PosTextStyles.priceTileLarge,
            color = MaterialTheme.colorScheme.primary,
            options =
                AdaptiveAmountOptions(
                    minFontSizeSp = 13f,
                    maxLines = 1,
                ),
        )
    }
}

/** Imagen remota o ícono de respaldo cuando no hay imagen. */
@Composable
private fun ProductImageContent(
    product: DashboardProduct,
    fallbackIconSize: Dp,
    logImageLoad: Boolean,
) {
    if (!product.imageUrl.isNullOrBlank()) {
        AsyncImage(
            model = product.imageUrl,
            contentDescription = product.name,
            modifier = Modifier.fillMaxSize(),
            onError =
                if (logImageLoad) {
                    { SafeLog.w("ProductImage", "Dashboard product image load failed") }
                } else {
                    null
                },
            onSuccess =
                if (logImageLoad) {
                    { SafeLog.d("ProductImage", "Dashboard product image loaded") }
                } else {
                    null
                },
        )
    } else {
        Icon(
            imageVector = Icons.Default.Inventory,
            contentDescription = "Sin imagen",
            tint = MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(fallbackIconSize),
        )
    }
}

/** Botones de cantidad y agregar compartidos por tarjeta y fila. */
@Composable
private fun ProductTileActions(
    onAddClick: () -> Unit,
    onQuantityClick: () -> Unit,
) {
    IconButton(
        onClick = onQuantityClick,
        modifier =
            Modifier
                .size(40.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.small),
    ) {
        Icon(
            Icons.Default.Edit,
            contentDescription = "Elegir cantidad",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp),
        )
    }
    Spacer(modifier = Modifier.width(8.dp))
    IconButton(
        onClick = onAddClick,
        modifier =
            Modifier
                .size(40.dp)
                .background(MaterialTheme.colorScheme.primary, MaterialTheme.shapes.small),
    ) {
        Icon(
            Icons.Default.Add,
            contentDescription = "Agregar una unidad",
            tint = PosPalette.FixedWhite,
            modifier = Modifier.size(20.dp),
        )
    }
}
