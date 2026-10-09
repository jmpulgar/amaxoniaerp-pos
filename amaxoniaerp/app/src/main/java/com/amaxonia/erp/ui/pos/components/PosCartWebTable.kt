package com.amaxonia.erp.ui.pos.components

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.amaxonia.erp.ui.pos.CartItem
import com.amaxonia.erp.ui.pos.PosCartSummary
import com.amaxonia.erp.ui.theme.FlowBrandBlue
import com.amaxonia.erp.ui.theme.FlowTableBgHeader
import com.amaxonia.erp.ui.theme.FlowTableBorder
import com.amaxonia.erp.ui.theme.FlowTableTextDark
import com.amaxonia.erp.ui.theme.FlowTableTextMuted
import java.util.Locale

@Composable
fun PosCartWebTable(
    cart: List<CartItem>,
    summary: PosCartSummary,
    branchName: String,
    onIncrement: (String) -> Unit,
    onDecrement: (String) -> Unit,
    onRemove: (String) -> Unit,
    onEditPrice: (CartItem) -> Unit,
    onEditDiscount: (CartItem) -> Unit,
    onEditQuantity: (CartItem) -> Unit = {},
    onPriceLevelChange: (String, String) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(6.dp))
            .border(1.dp, FlowTableBorder, RoundedCornerShape(6.dp))
            .background(Color.White),
    ) {
        // --- 1. ENCABEZADO DE LA TABLA (Compacto 28dp, 1:1 Web POS) ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp)
                .background(FlowTableBgHeader)
                .border(width = 0.5.dp, color = FlowTableBorder),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TableCellHeader("#", Modifier.width(24.dp), align = TextAlign.Center)
            TableDivider()
            TableCellHeader("PRODUCTO", Modifier.weight(2.5f), align = TextAlign.Start)
            TableDivider()
            TableCellHeader("UND", Modifier.width(42.dp), align = TextAlign.Center)
            TableDivider()
            TableCellHeader("CANT", Modifier.width(72.dp), align = TextAlign.Center)
            TableDivider()
            TableCellHeader("DESC", Modifier.width(52.dp), align = TextAlign.End)
            TableDivider()
            TableCellHeader("PRECIO", Modifier.width(62.dp), align = TextAlign.End)
            TableDivider()
            TableCellHeader("LST", Modifier.width(34.dp), align = TextAlign.Center)
            TableDivider()
            TableCellHeader("ITBMS", Modifier.width(52.dp), align = TextAlign.End)
            TableDivider()
            TableCellHeader("IMPORTE", Modifier.width(68.dp), align = TextAlign.End)
            TableDivider()
            TableCellHeader("", Modifier.width(28.dp), align = TextAlign.Center)
        }

        // --- 2. CONTENIDO: FILAS DE PRODUCTOS O ESTADO VACÍO ---
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            if (cart.isEmpty()) {
                // Estado Vacío con icono de ojo tachado (igual que en la web)
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        imageVector = Icons.Default.VisibilityOff,
                        contentDescription = "Sin items",
                        tint = Color(0xFFCBD5E1),
                        modifier = Modifier.size(40.dp),
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "No hay registros disponibles",
                        color = Color(0xFF94A3B8),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    itemsIndexed(
                        cart,
                        key = { _, item ->
                            if (item.promocionId.isNullOrBlank()) item.product.id
                            else "${item.product.id}_${item.promocionId}_${item.promocionDetalleId}_${item.selectedPriceLabel}"
                        },
                    ) { index, item ->
                        val isEven = index % 2 == 0
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(40.dp)
                                .background(if (isEven) Color.White else Color(0xFFFAFAFC))
                                .border(width = 0.5.dp, color = Color(0xFFF1F5F9)),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            // # Índice
                            TableCellText(
                                text = (index + 1).toString(),
                                modifier = Modifier.width(24.dp),
                                align = TextAlign.Center,
                                color = FlowTableTextMuted,
                            )
                            TableDivider()

                            // Producto (Foto miniatura + Nombre + Referencia)
                            Row(
                                modifier = Modifier
                                    .weight(2.5f)
                                    .padding(horizontal = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                // Miniatura de Imagen de Producto (26dp)
                                Box(
                                    modifier = Modifier
                                        .size(26.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .border(0.5.dp, FlowTableBorder, RoundedCornerShape(4.dp))
                                        .background(Color(0xFFF8FAFC)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (item.product.photoUrl.isNotBlank()) {
                                        AsyncImage(
                                            model = item.product.photoUrl,
                                            contentDescription = item.product.description,
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop,
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Default.Image,
                                            contentDescription = null,
                                            tint = Color(0xFFCBD5E1),
                                            modifier = Modifier.size(14.dp),
                                        )
                                    }
                                }

                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clipToBounds(),
                                    verticalArrangement = Arrangement.Center,
                                ) {
                                    Text(
                                        text = item.product.description,
                                        fontSize = 11.sp,
                                        lineHeight = 12.sp,
                                        style = LocalTextStyle.current.copy(
                                            platformStyle = PlatformTextStyle(includeFontPadding = false),
                                        ),
                                        fontWeight = FontWeight.Medium,
                                        color = FlowTableTextDark,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    if (item.product.code.isNotBlank()) {
                                        Text(
                                            text = item.product.code,
                                            fontSize = 9.sp,
                                            lineHeight = 10.sp,
                                            style = LocalTextStyle.current.copy(
                                                platformStyle = PlatformTextStyle(includeFontPadding = false),
                                            ),
                                            color = FlowTableTextMuted,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                            }
                            TableDivider()

                            // Unidad
                            TableCellText(
                                text = item.product.unitPackage.ifBlank { "UND" },
                                modifier = Modifier.width(42.dp),
                                align = TextAlign.Center,
                                color = FlowTableTextMuted,
                            )
                            TableDivider()

                            // Cantidad con controles rápidos +/- y toque directo para keypad
                            val qtyText = if (item.quantity % 1.0 == 0.0) String.format(Locale.US, "%.0f", item.quantity) else String.format(Locale.US, "%.2f", item.quantity)
                            Row(
                                modifier = Modifier
                                    .width(72.dp)
                                    .padding(horizontal = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(18.dp)
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(Color(0xFFF1F5F9))
                                        .clickable { onDecrement(item.product.id) },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(Icons.Default.Remove, contentDescription = "-", tint = Color(0xFF64748B), modifier = Modifier.size(10.dp))
                                }

                                Text(
                                    text = qtyText,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = FlowTableTextDark,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(2.dp))
                                        .clickable { onEditQuantity(item) },
                                )

                                Box(
                                    modifier = Modifier
                                        .size(18.dp)
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(Color(0xFFF1F5F9))
                                        .clickable { onIncrement(item.product.id) },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = "+", tint = FlowBrandBlue, modifier = Modifier.size(10.dp))
                                }
                            }
                            TableDivider()

                            // Descuento
                            Text(
                                text = if (item.discountPercent > 0.0) "${String.format(Locale.US, "%.0f", item.discountPercent)}%" else "0%",
                                modifier = Modifier
                                    .width(52.dp)
                                    .clickable { onEditDiscount(item) }
                                    .padding(end = 4.dp),
                                fontSize = 11.sp,
                                textAlign = TextAlign.End,
                                color = if (item.discountPercent > 0.0) Color(0xFFDC2626) else FlowTableTextMuted,
                            )
                            TableDivider()

                            // Precio
                            Text(
                                text = String.format(Locale.US, "%.2f", item.unitPriceWithTax),
                                modifier = Modifier
                                    .width(62.dp)
                                    .clickable { onEditPrice(item) }
                                    .padding(end = 4.dp),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                textAlign = TextAlign.End,
                                color = FlowTableTextDark,
                            )
                            TableDivider()

                            // Lista de Precio (A, B, C...) con menú desplegable
                            var showPriceMenu by remember { mutableStateOf(false) }
                            Box(
                                modifier = Modifier
                                    .width(34.dp)
                                    .fillMaxHeight()
                                    .clickable { showPriceMenu = true },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = item.selectedPriceLabel,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = FlowBrandBlue,
                                    textAlign = TextAlign.Center,
                                )
                                DropdownMenu(
                                    expanded = showPriceMenu,
                                    onDismissRequest = { showPriceMenu = false },
                                ) {
                                    val priceLevels = if (item.product.prices.isNotEmpty()) {
                                        item.product.prices.map { it.label }
                                    } else {
                                        listOf("A", "B", "C", "D", "E")
                                    }
                                    priceLevels.forEach { label ->
                                        DropdownMenuItem(
                                            text = { Text("Lista $label") },
                                            onClick = {
                                                showPriceMenu = false
                                                onPriceLevelChange(item.product.id, label)
                                            },
                                        )
                                    }
                                }
                            }
                            TableDivider()

                            // ITBMS
                            TableCellText(
                                text = String.format(Locale.US, "%.2f", item.taxAmount),
                                modifier = Modifier
                                    .width(52.dp)
                                    .padding(end = 4.dp),
                                align = TextAlign.End,
                                color = FlowTableTextMuted,
                            )
                            TableDivider()

                            // Importe
                            TableCellText(
                                text = String.format(Locale.US, "%.2f", item.totalWithTax),
                                modifier = Modifier
                                    .width(68.dp)
                                    .padding(end = 4.dp),
                                align = TextAlign.End,
                                fontWeight = FontWeight.Bold,
                                color = FlowBrandBlue,
                            )
                            TableDivider()

                            // Botón Eliminar
                            Box(
                                modifier = Modifier
                                    .width(28.dp)
                                    .fillMaxHeight(),
                                contentAlignment = Alignment.Center,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(20.dp)
                                        .clip(RoundedCornerShape(3.dp))
                                        .clickable { onRemove(item.product.id) },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Eliminar",
                                        tint = Color(0xFFEF4444),
                                        modifier = Modifier.size(13.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // --- 3. PIE DE TABLA COMPACTO (Subtotales, Descuento y Total en barra de 30dp) ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(30.dp)
                .background(FlowTableBgHeader)
                .border(width = 0.5.dp, color = FlowTableBorder)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            // Izquierda: Conteo e info
            Text(
                text = "${summary.itemCount} items • $branchName",
                fontSize = 10.5.sp,
                color = Color(0xFF64748B),
                fontWeight = FontWeight.Medium,
            )

            // Derecha: Desglose financiero en línea
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "Sub: $${String.format(Locale.US, "%.2f", summary.subtotal)}",
                    fontSize = 10.5.sp,
                    color = Color(0xFF475569),
                    fontWeight = FontWeight.Medium,
                )
                if (summary.discountTotal > 0.0) {
                    Text(
                        text = "Desc: -$${String.format(Locale.US, "%.2f", summary.discountTotal)}",
                        fontSize = 10.5.sp,
                        color = Color(0xFFDC2626),
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Text(
                    text = "ITBMS: $${String.format(Locale.US, "%.2f", summary.tax)}",
                    fontSize = 10.5.sp,
                    color = Color(0xFF475569),
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = "TOTAL: $${String.format(Locale.US, "%.2f", summary.total)}",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = FlowBrandBlue,
                )
            }
        }
    }
}

@Composable
private fun TableCellHeader(
    text: String,
    modifier: Modifier = Modifier,
    align: TextAlign = TextAlign.Start,
) {
    Text(
        text = text,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        color = FlowTableTextMuted,
        textAlign = align,
        maxLines = 1,
        modifier = modifier.padding(horizontal = 4.dp),
    )
}

@Composable
private fun TableCellText(
    text: String,
    modifier: Modifier = Modifier,
    align: TextAlign = TextAlign.Start,
    color: Color = FlowTableTextDark,
    fontWeight: FontWeight = FontWeight.Normal,
) {
    Text(
        text = text,
        fontSize = 11.sp,
        fontWeight = fontWeight,
        color = color,
        textAlign = align,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier.padding(horizontal = 4.dp),
    )
}

@Composable
private fun TableDivider() {
    Box(
        modifier = Modifier
            .width(1.dp)
            .fillMaxHeight()
            .background(FlowTableBorder)
    )
}
