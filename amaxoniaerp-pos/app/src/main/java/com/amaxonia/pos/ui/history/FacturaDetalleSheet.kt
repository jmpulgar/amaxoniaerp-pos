package com.amaxonia.pos.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Receipt
import androidx.compose.material.icons.rounded.ShoppingCart
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amaxonia.pos.domain.model.ElectronicInvoiceStatus
import com.amaxonia.pos.domain.model.Transaction
import com.amaxonia.pos.domain.model.sales.FacturaDetalleItemDto
import com.amaxonia.pos.ui.common.components.AdaptiveAmountOptions
import com.amaxonia.pos.ui.common.components.AdaptiveAmountText
import com.amaxonia.pos.ui.payment.formatCurrencyLabel
import java.util.Locale

/** Alto estimado de cada fila del detalle en la hoja inferior. */
private const val DETALLE_ROW_HEIGHT_DP = 72

/** Filas visibles como máximo dentro de la hoja de detalle. */
private const val VISIBLE_DETALLE_ROWS = 8

/** Contenido de la hoja inferior con el detalle de una factura. */
@Composable
internal fun FacturaDetalleSheetContent(
    transaction: Transaction?,
    items: List<FacturaDetalleItemDto>,
    isLoading: Boolean,
    error: String?,
    actionError: String? = null,
    actionMessage: String? = null,
    isResending: Boolean = false,
    onResend: (() -> Unit)? = null,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
    ) {
        // Header
        if (transaction != null) {
            FacturaDetalleHeader(transaction = transaction)

            // Client + payment info
            FacturaDetalleClientCard(transaction = transaction)

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(modifier = Modifier.height(12.dp))
        }

        // Q3: FE fallida/pendiente → acción de reenvío visible (paridad con el Web).
        if (actionError != null) {
            DetalleAccionBanner(text = actionError, isError = true)
            Spacer(modifier = Modifier.height(8.dp))
        }
        if (actionMessage != null) {
            DetalleAccionBanner(text = actionMessage, isError = false)
            Spacer(modifier = Modifier.height(8.dp))
        }
        if (transaction != null && onResend != null && transaction.esReenviable()) {
            FacturaDetalleResendButton(isResending = isResending, onResend = onResend)
            Spacer(modifier = Modifier.height(8.dp))
        }

        FacturaDetalleSectionTitle(
            isLoading = isLoading,
            itemCount = items.size,
        )

        Spacer(modifier = Modifier.height(12.dp))

        when {
            isLoading -> DetalleItemsLoading()
            error != null -> DetalleItemsError(error = error)
            items.isEmpty() -> DetalleItemsEmpty()
            else -> DetalleItemsBody(transaction = transaction, items = items)
        }
    }
}

/**
 * Q3: sólo una factura SIN CUFE (pendiente o fallida) y sincronizada es
 * reenviable — el mismo criterio del Web (`cufe IS NULL`).
 */
internal fun Transaction.esReenviable(): Boolean =
    id.isNotBlank() &&
        !id.startsWith("OFF-") &&
        (electronicStatus == ElectronicInvoiceStatus.PENDING || electronicStatus == ElectronicInvoiceStatus.FAILED)

/** Banner con el resultado de la última acción (reenvío FE). */
@Composable
private fun DetalleAccionBanner(
    text: String,
    isError: Boolean,
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors =
            CardDefaults.elevatedCardColors(
                containerColor =
                    if (isError) {
                        MaterialTheme.colorScheme.errorContainer
                    } else {
                        MaterialTheme.colorScheme.primaryContainer
                    },
            ),
    ) {
        Text(
            text = text,
            color =
                if (isError) {
                    MaterialTheme.colorScheme.onErrorContainer
                } else {
                    MaterialTheme.colorScheme.onPrimaryContainer
                },
            fontSize = 13.sp,
            modifier = Modifier.padding(12.dp),
        )
    }
}

/** Botón de reenvío FE con estado de progreso. */
@Composable
private fun FacturaDetalleResendButton(
    isResending: Boolean,
    onResend: () -> Unit,
) {
    Button(
        onClick = onResend,
        enabled = !isResending,
        modifier = Modifier.fillMaxWidth(),
    ) {
        if (isResending) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary,
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(
            text = if (isResending) "Reenviando..." else "Reenviar Factura Electrónica",
            fontSize = 14.sp,
        )
    }
}

/** Encabezado con número de factura, fecha y estado. */
@Composable
private fun FacturaDetalleHeader(transaction: Transaction) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.Receipt,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp),
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = transaction.invoiceNumber,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${transaction.dateHeader}  ${transaction.time}",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.width(8.dp))
                StatusBadge(status = transaction.status)
            }
        }
    }
}

/** Tarjeta con los datos del cliente y la forma de pago. */
@Composable
private fun FacturaDetalleClientCard(transaction: Transaction) {
    if (transaction.clienteNombre.isNotBlank() || transaction.formaPago.isNotBlank()) {
        Spacer(modifier = Modifier.height(12.dp))
        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 0.dp),
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                if (transaction.clienteNombre.isNotBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Rounded.Person,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = transaction.clienteNombre,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    if (transaction.clienteIdentificacion.isNotBlank()) {
                        Text(
                            text = transaction.clienteIdentificacion,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 24.dp),
                        )
                    }
                }
                if (transaction.formaPago.isNotBlank()) {
                    if (transaction.clienteNombre.isNotBlank()) {
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Rounded.ShoppingCart,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = transaction.formaPago.replaceFirstChar { it.uppercase() },
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }
    }
}

/** Título de la sección de productos con su cantidad. */
@Composable
private fun FacturaDetalleSectionTitle(
    isLoading: Boolean,
    itemCount: Int,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            Icons.Rounded.Inventory2,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "Productos",
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (!isLoading && itemCount > 0) {
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "($itemCount)",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DetalleItemsLoading() {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(120.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp),
                strokeWidth = 3.dp,
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                "Cargando productos...",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DetalleItemsError(error: String) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors =
            CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
            ),
    ) {
        Text(
            text = error,
            color = MaterialTheme.colorScheme.onErrorContainer,
            fontSize = 13.sp,
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Composable
private fun DetalleItemsEmpty() {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(80.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "Sin productos",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Lista de productos del detalle y fila de totales. */
@Composable
private fun DetalleItemsBody(
    transaction: Transaction?,
    items: List<FacturaDetalleItemDto>,
) {
    LazyColumn(
        modifier =
            Modifier
                .fillMaxWidth()
                .height((items.size.coerceAtMost(VISIBLE_DETALLE_ROWS) * DETALLE_ROW_HEIGHT_DP).dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(items, key = { it.id }) { item ->
            DetalleItemRow(item = item, currency = transaction?.currency ?: "USD")
        }
    }

    // Total row
    if (transaction != null) {
        FacturaDetalleTotalRow(transaction = transaction)
    }
}

/** Fila de total principal y referencia en moneda secundaria. */
@Composable
private fun FacturaDetalleTotalRow(transaction: Transaction) {
    Spacer(modifier = Modifier.height(12.dp))
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    Spacer(modifier = Modifier.height(12.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Total",
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Column(horizontalAlignment = Alignment.End) {
            AdaptiveAmountText(
                text = "${transaction.currency} ${String.format(Locale.getDefault(), "%.2f", transaction.amount)}",
                baseStyle =
                    MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                    ),
                color = MaterialTheme.colorScheme.primary,
                options =
                    AdaptiveAmountOptions(
                        minFontSizeSp = 13f,
                    ),
            )
            if (transaction.totalRef != null &&
                transaction.totalRef > 0.0 &&
                !transaction.abrMonedaSecundaria.isNullOrBlank()
            ) {
                Text(
                    text = "${formatCurrencyLabel(
                        transaction.abrMonedaSecundaria,
                    )} ${String.format(Locale.getDefault(), "%.2f", transaction.totalRef)}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Fila de un producto del detalle. */
@Composable
private fun DetalleItemRow(
    item: FacturaDetalleItemDto,
    currency: String,
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Qty badge
            DetalleQtyBadge(cantidad = item.cantidad)

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.descripcion,
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (item.codigo.isNotBlank()) {
                    Text(
                        text = item.codigo,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            DetalleItemAmounts(item = item, currency = currency)
        }
    }
}

/** Badge de cantidad (entera o decimal). */
@Composable
private fun DetalleQtyBadge(cantidad: Double) {
    Box(
        modifier =
            Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text =
                if (cantidad == cantidad.toLong().toDouble()) {
                    "${cantidad.toLong()}"
                } else {
                    String.format(Locale.getDefault(), "%.1f", cantidad)
                },
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

/** Importes de la línea (total con IVA y precio unitario). */
@Composable
private fun DetalleItemAmounts(
    item: FacturaDetalleItemDto,
    currency: String,
) {
    Column(horizontalAlignment = Alignment.End) {
        AdaptiveAmountText(
            text = "$currency ${String.format(Locale.getDefault(), "%.2f", item.totalConIva)}",
            baseStyle =
                MaterialTheme.typography.labelLarge.copy(
                    fontWeight = FontWeight.Bold,
                ),
            color = MaterialTheme.colorScheme.onSurface,
            options =
                AdaptiveAmountOptions(
                    minFontSizeSp = 11f,
                ),
        )
        Text(
            text = "c/u ${String.format(Locale.getDefault(), "%.2f", item.precioUnitario)}",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
