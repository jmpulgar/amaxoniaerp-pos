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
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.ChatBubbleOutline
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
import com.amaxonia.erp.ui.components.isLargeScreen
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
    onEditKitchenNote: (CartItem) -> Unit = {},
    onEditOptions: (CartItem) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val isLarge = isLargeScreen()

    val headerFontSize = if (isLarge) 11.sp else 10.sp
    val cellFontSize = if (isLarge) 12.sp else 11.sp
    val indexColWidth = if (isLarge) 24.dp else 20.dp
    val cantColWidth = if (isLarge) 72.dp else 64.dp
    val descColWidth = if (isLarge) 40.dp else 36.dp
    val precioColWidth = if (isLarge) 64.dp else 58.dp
    val itbmsColWidth = if (isLarge) 44.dp else 40.dp
    val importeColWidth = if (isLarge) 64.dp else 58.dp
    val actionColWidth = if (isLarge) 68.dp else 58.dp

    Column(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(6.dp))
            .border(1.dp, FlowTableBorder, RoundedCornerShape(6.dp))
            .background(Color.White),
    ) {
        // --- 1. ENCABEZADO DE LA TABLA (Adaptable, más amplio en pantallas de 14-16") ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (isLarge) 34.dp else 28.dp)
                .background(FlowTableBgHeader)
                .border(width = 0.5.dp, color = FlowTableBorder),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TableCellHeader("#", Modifier.width(indexColWidth), fontSize = headerFontSize, align = TextAlign.Center)
            TableDivider()
            TableCellHeader("PRODUCTO", Modifier.weight(1f), fontSize = headerFontSize, align = TextAlign.Start)
            TableDivider()
            TableCellHeader("CANT", Modifier.width(cantColWidth), fontSize = headerFontSize, align = TextAlign.Center)
            TableDivider()
            TableCellHeader("DESC", Modifier.width(descColWidth), fontSize = headerFontSize, align = TextAlign.End)
            TableDivider()
            TableCellHeader("PRECIO", Modifier.width(precioColWidth), fontSize = headerFontSize, align = TextAlign.End)
            TableDivider()
            TableCellHeader("ITBMS", Modifier.width(itbmsColWidth), fontSize = headerFontSize, align = TextAlign.End)
            TableDivider()
            TableCellHeader("IMPORTE", Modifier.width(importeColWidth), fontSize = headerFontSize, align = TextAlign.End)
            TableDivider()
            TableCellHeader("", Modifier.width(actionColWidth), fontSize = headerFontSize, align = TextAlign.Center)
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
                        modifier = Modifier.size(if (isLarge) 48.dp else 40.dp),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "No hay registros disponibles",
                        color = Color(0xFF94A3B8),
                        fontSize = if (isLarge) 13.5.sp else 12.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    itemsIndexed(
                        cart,
                        key = { _, item -> item.cartItemId },
                    ) { index, item ->
                        val isEven = index % 2 == 0
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(if (isLarge) 58.dp else 50.dp)
                                .background(if (isEven) Color.White else Color(0xFFFAFAFC))
                                .border(width = 0.5.dp, color = Color(0xFFF1F5F9)),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            // # Índice
                            TableCellText(
                                text = (index + 1).toString(),
                                modifier = Modifier.width(indexColWidth),
                                fontSize = cellFontSize,
                                align = TextAlign.Center,
                                color = FlowTableTextMuted,
                            )
                            TableDivider()

                            // Producto (Foto miniatura + Nombre + Referencia)
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = if (isLarge) 6.dp else 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(if (isLarge) 8.dp else 6.dp),
                            ) {
                                // Miniatura de Imagen de Producto (36dp en pantallas grandes)
                                Box(
                                    modifier = Modifier
                                        .size(if (isLarge) 36.dp else 26.dp)
                                        .clip(RoundedCornerShape(if (isLarge) 6.dp else 4.dp))
                                        .border(0.5.dp, FlowTableBorder, RoundedCornerShape(if (isLarge) 6.dp else 4.dp))
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
                                            modifier = Modifier.size(if (isLarge) 18.dp else 14.dp),
                                        )
                                    }
                                }

                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clipToBounds(),
                                    verticalArrangement = Arrangement.Center,
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    ) {
                                        Text(
                                            text = item.product.description,
                                            fontSize = if (isLarge) 13.5.sp else 11.5.sp,
                                            lineHeight = if (isLarge) 16.sp else 13.sp,
                                            style = LocalTextStyle.current.copy(
                                                platformStyle = PlatformTextStyle(includeFontPadding = false),
                                            ),
                                            fontWeight = FontWeight.Bold,
                                            color = FlowTableTextDark,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f, fill = false),
                                        )
                                        if (item.product.isComboArticle) {
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(999.dp))
                                                    .background(Color(0xFFFFF8EB))
                                                    .padding(horizontal = if (isLarge) 5.dp else 4.dp, vertical = 1.dp),
                                                contentAlignment = Alignment.Center,
                                            ) {
                                                Text(
                                                    text = "Combo",
                                                    fontSize = if (isLarge) 9.5.sp else 8.sp,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    color = Color(0xFF92400E),
                                                )
                                            }
                                        }
                                    }
                                    if (item.hasOptions) {
                                        val incluidos = item.selectedOptions.filter { it.tipo.equals("INCLUIDO", ignoreCase = true) }
                                        val extras = item.selectedOptions.filter { !it.tipo.equals("INCLUIDO", ignoreCase = true) }

                                        val summaryParts = mutableListOf<String>()
                                        if (incluidos.isNotEmpty()) {
                                            summaryParts.add("Incluye: " + incluidos.joinToString(", ") { opt ->
                                                (if (opt.cantidad > 1) "${opt.cantidad.toInt()}× " else "") + opt.nombre
                                            })
                                        }
                                        if (extras.isNotEmpty()) {
                                            summaryParts.add(extras.joinToString(", ") { opt ->
                                                (if (opt.veces > 1) "${opt.veces}× " else "") + opt.nombre +
                                                    (if (opt.precio > 0) " (+$$${String.format(Locale.US, "%.2f", opt.precio)})" else "")
                                            })
                                        }
                                        Text(
                                            text = summaryParts.joinToString(" · "),
                                            fontSize = if (isLarge) 10.sp else 8.5.sp,
                                            lineHeight = if (isLarge) 12.sp else 10.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (extras.isNotEmpty()) Color(0xFF201B82) else Color(0xFF047857),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    } else {
                                        val unitText = item.product.unitPackage.ifBlank { "UND" }
                                        val codeText = item.product.code
                                        val refText = if (codeText.isNotBlank()) "$codeText • $unitText" else unitText
                                        Text(
                                            text = refText,
                                            fontSize = if (isLarge) 10.5.sp else 9.sp,
                                            lineHeight = if (isLarge) 12.sp else 10.sp,
                                            style = LocalTextStyle.current.copy(
                                                platformStyle = PlatformTextStyle(includeFontPadding = false),
                                            ),
                                            color = FlowTableTextMuted,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                    if (item.kitchenNote.isNotBlank()) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                                            modifier = Modifier.padding(top = 1.dp),
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.ChatBubbleOutline,
                                                contentDescription = null,
                                                tint = Color(0xFFEA580C),
                                                modifier = Modifier.size(if (isLarge) 11.dp else 9.5.dp),
                                            )
                                            Text(
                                                text = item.kitchenNote,
                                                fontSize = if (isLarge) 10.sp else 8.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFFEA580C),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                    }
                                }
                            }
                            TableDivider()

                            // Cantidad con controles rápidos +/- y toque directo para keypad
                            val qtyText = if (item.quantity % 1.0 == 0.0) String.format(Locale.US, "%.0f", item.quantity) else String.format(Locale.US, "%.2f", item.quantity)
                            Row(
                                modifier = Modifier
                                    .width(cantColWidth)
                                    .padding(horizontal = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(if (isLarge) 22.dp else 18.dp)
                                        .clip(RoundedCornerShape(if (isLarge) 4.dp else 3.dp))
                                        .background(Color(0xFFF1F5F9))
                                        .clickable { onDecrement(item.product.id) },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        Icons.Default.Remove,
                                        contentDescription = "-",
                                        tint = Color(0xFF64748B),
                                        modifier = Modifier.size(if (isLarge) 12.dp else 10.dp),
                                    )
                                }

                                Text(
                                    text = qtyText,
                                    fontSize = if (isLarge) 12.5.sp else 11.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = FlowTableTextDark,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(2.dp))
                                        .clickable { onEditQuantity(item) },
                                )

                                Box(
                                    modifier = Modifier
                                        .size(if (isLarge) 22.dp else 18.dp)
                                        .clip(RoundedCornerShape(if (isLarge) 4.dp else 3.dp))
                                        .background(Color(0xFFF1F5F9))
                                        .clickable { onIncrement(item.product.id) },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        Icons.Default.Add,
                                        contentDescription = "+",
                                        tint = FlowBrandBlue,
                                        modifier = Modifier.size(if (isLarge) 12.dp else 10.dp),
                                    )
                                }
                            }
                            TableDivider()

                            // Descuento
                            Text(
                                text = if (item.discountPercent > 0.0) "${String.format(Locale.US, "%.0f", item.discountPercent)}%" else "0%",
                                modifier = Modifier
                                    .width(descColWidth)
                                    .clickable { onEditDiscount(item) }
                                    .padding(end = 4.dp),
                                fontSize = cellFontSize,
                                textAlign = TextAlign.End,
                                color = if (item.discountPercent > 0.0) Color(0xFFDC2626) else FlowTableTextMuted,
                            )
                            TableDivider()

                            // Precio Base y Nivel de Lista (con DropdownMenu anclado directamente a la celda)
                            val displayUnitPrice = if (item.taxRate > 0.0) item.unitPriceWithoutTax else item.unitPriceWithTax
                            var isPriceLevelMenuOpen by remember { mutableStateOf(false) }

                            Box(
                                modifier = Modifier
                                    .width(precioColWidth)
                                    .fillMaxHeight()
                                    .padding(end = 4.dp),
                                contentAlignment = Alignment.CenterEnd,
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.End,
                                    verticalArrangement = Arrangement.Center,
                                ) {
                                    Text(
                                        text = String.format(Locale.US, "%.2f", displayUnitPrice),
                                        fontSize = cellFontSize,
                                        fontWeight = FontWeight.Bold,
                                        color = FlowTableTextDark,
                                        modifier = Modifier.clickable { onEditPrice(item) },
                                    )
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(3.dp))
                                            .clickable { isPriceLevelMenuOpen = true }
                                            .padding(horizontal = 2.dp, vertical = 1.dp),
                                    ) {
                                        Text(
                                            text = item.priceDisplayName,
                                            fontSize = if (isLarge) 10.sp else 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = FlowBrandBlue,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Icon(
                                            imageVector = Icons.Default.ArrowDropDown,
                                            contentDescription = "Cambiar lista de precio",
                                            tint = FlowBrandBlue,
                                            modifier = Modifier.size(if (isLarge) 14.dp else 12.dp),
                                        )
                                    }
                                }

                                // DropdownMenu anclado a esta celda específica
                                DropdownMenu(
                                    expanded = isPriceLevelMenuOpen,
                                    onDismissRequest = { isPriceLevelMenuOpen = false },
                                ) {
                                    val availableLevels = if (item.product.prices.isNotEmpty()) {
                                        item.product.prices
                                    } else {
                                        listOf(
                                            com.amaxonia.erp.domain.model.PriceLevel(label = "A", name = "Detal"),
                                            com.amaxonia.erp.domain.model.PriceLevel(label = "B", name = "Mayor"),
                                            com.amaxonia.erp.domain.model.PriceLevel(label = "C", name = "Distribuidor"),
                                        )
                                    }
                                    availableLevels.forEach { level ->
                                        val isSelected = item.selectedPriceLabel.equals(level.label, ignoreCase = true)
                                        val levelPrice = level.pricePlusTax.takeIf { it > 0.0 } ?: level.price
                                        DropdownMenuItem(
                                            text = {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically,
                                                ) {
                                                    Text(
                                                        text = level.displayName,
                                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                        color = if (isSelected) FlowBrandBlue else Color.Unspecified,
                                                        fontSize = 12.sp,
                                                    )
                                                    if (levelPrice > 0.0) {
                                                        Spacer(Modifier.width(12.dp))
                                                        Text(
                                                            text = "$${String.format(Locale.US, "%.2f", levelPrice)}",
                                                            color = FlowTableTextMuted,
                                                            fontSize = 11.5.sp,
                                                            fontWeight = FontWeight.SemiBold,
                                                        )
                                                    }
                                                }
                                            },
                                            onClick = {
                                                onPriceLevelChange(item.product.id, level.label)
                                                isPriceLevelMenuOpen = false
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
                                    .width(itbmsColWidth)
                                    .padding(end = 4.dp),
                                fontSize = cellFontSize,
                                align = TextAlign.End,
                                color = if (item.taxAmount > 0.0) FlowTableTextDark else FlowTableTextMuted,
                                fontWeight = if (item.taxAmount > 0.0) FontWeight.Medium else FontWeight.Normal,
                            )
                            TableDivider()

                            // Importe
                            TableCellText(
                                text = String.format(Locale.US, "%.2f", item.totalWithTax),
                                modifier = Modifier
                                    .width(importeColWidth)
                                    .padding(end = 4.dp),
                                fontSize = if (isLarge) 13.5.sp else 11.sp,
                                align = TextAlign.End,
                                fontWeight = FontWeight.ExtraBold,
                                color = FlowBrandBlue,
                            )
                            TableDivider()

                            // Acciones: Cuadrícula 2x2 fiel a Flow UI (flow-app.css td.acciones)
                            // Fila 1: Editar (Brand Blue #2488FA) + Eliminar (Danger Red #F43F5E)
                            // Fila 2: Nota de Cocina (Orange #EA580C) + Opciones/Combo (Indigo #4F46E5)
                            val buttonSize = if (isLarge) 24.dp else 20.dp
                            val iconSize = if (isLarge) 13.dp else 11.dp
                            val buttonRadius = if (isLarge) 6.dp else 5.dp
                            val canHaveOptions = item.hasOptions || item.product.requiresCompositionSelection || item.product.isComboArticle

                            Box(
                                modifier = Modifier
                                    .width(actionColWidth)
                                    .fillMaxHeight(),
                                contentAlignment = Alignment.Center,
                            ) {
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(if (isLarge) 3.dp else 2.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    // Fila 1: Editar y Eliminar
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(if (isLarge) 4.dp else 3.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        // Botón Editar (abre keypad/edición de cantidad del item)
                                        Box(
                                            modifier = Modifier
                                                .size(buttonSize)
                                                .clip(RoundedCornerShape(buttonRadius))
                                                .background(FlowBrandBlue)
                                                .clickable { onEditQuantity(item) },
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Edit,
                                                contentDescription = "Editar",
                                                tint = Color.White,
                                                modifier = Modifier.size(iconSize),
                                            )
                                        }

                                        // Botón Eliminar
                                        Box(
                                            modifier = Modifier
                                                .size(buttonSize)
                                                .clip(RoundedCornerShape(buttonRadius))
                                                .background(Color(0xFFF43F5E))
                                                .clickable { onRemove(item.product.id) },
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "Eliminar",
                                                tint = Color.White,
                                                modifier = Modifier.size(iconSize),
                                            )
                                        }
                                    }

                                    // Fila 2: Nota de Cocina y Modificadores / Opciones
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(if (isLarge) 4.dp else 3.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        // Botón Nota de Cocina
                                        val hasNote = item.kitchenNote.isNotBlank()
                                        Box(
                                            modifier = Modifier
                                                .size(buttonSize)
                                                .clip(RoundedCornerShape(buttonRadius))
                                                .background(if (hasNote) Color(0xFFFED7AA) else Color(0xFFFFF7ED))
                                                .border(
                                                    0.5.dp,
                                                    if (hasNote) Color(0xFFFDBA74) else Color(0xFFFED7AA),
                                                    RoundedCornerShape(buttonRadius),
                                                )
                                                .clickable { onEditKitchenNote(item) },
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.ChatBubbleOutline,
                                                contentDescription = "Nota de cocina",
                                                tint = Color(0xFFEA580C),
                                                modifier = Modifier.size(iconSize),
                                            )
                                        }

                                        // Botón Opciones / Combo (visible cuando aplica)
                                        if (canHaveOptions) {
                                            Box(
                                                modifier = Modifier
                                                    .size(buttonSize)
                                                    .clip(RoundedCornerShape(buttonRadius))
                                                    .background(Color(0xFFEEF0FF))
                                                    .border(0.5.dp, Color(0xFFC7D2FE), RoundedCornerShape(buttonRadius))
                                                    .clickable { onEditOptions(item) },
                                                contentAlignment = Alignment.Center,
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Tune,
                                                    contentDescription = "Opciones",
                                                    tint = Color(0xFF4F46E5),
                                                    modifier = Modifier.size(iconSize),
                                                )
                                            }
                                        } else {
                                            Spacer(modifier = Modifier.size(buttonSize))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // --- 3. PIE DE TABLA COMPACTO (Subtotales, Descuento y Total en barra de 30-36dp) ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (isLarge) 36.dp else 30.dp)
                .background(FlowTableBgHeader)
                .border(width = 0.5.dp, color = FlowTableBorder)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            // Izquierda: Conteo e info
            Text(
                text = "${summary.itemCount} items • $branchName",
                fontSize = if (isLarge) 12.sp else 10.5.sp,
                color = Color(0xFF64748B),
                fontWeight = FontWeight.Medium,
            )

            // Derecha: Desglose financiero en línea
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(if (isLarge) 12.dp else 8.dp),
            ) {
                Text(
                    text = "Sub: $${String.format(Locale.US, "%.2f", summary.subtotal)}",
                    fontSize = if (isLarge) 12.sp else 10.5.sp,
                    color = Color(0xFF475569),
                    fontWeight = FontWeight.Medium,
                )
                if (summary.discountTotal > 0.0) {
                    Text(
                        text = "Desc: -$${String.format(Locale.US, "%.2f", summary.discountTotal)}",
                        fontSize = if (isLarge) 12.sp else 10.5.sp,
                        color = Color(0xFFDC2626),
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Text(
                    text = "ITBMS: $${String.format(Locale.US, "%.2f", summary.tax)}",
                    fontSize = if (isLarge) 12.sp else 10.5.sp,
                    color = Color(0xFF475569),
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = "TOTAL: $${String.format(Locale.US, "%.2f", summary.total)}",
                    fontSize = if (isLarge) 14.5.sp else 12.sp,
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
    fontSize: androidx.compose.ui.unit.TextUnit = 10.sp,
    align: TextAlign = TextAlign.Start,
) {
    Text(
        text = text,
        fontSize = fontSize,
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
    fontSize: androidx.compose.ui.unit.TextUnit = 11.sp,
    align: TextAlign = TextAlign.Start,
    color: Color = FlowTableTextDark,
    fontWeight: FontWeight = FontWeight.Normal,
) {
    Text(
        text = text,
        fontSize = fontSize,
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
