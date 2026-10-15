package com.amaxonia.erp.ui.pos.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fastfood
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.amaxonia.erp.domain.model.Product
import com.amaxonia.erp.domain.model.ProductOptionGroup
import com.amaxonia.erp.domain.model.ProductOptionItem
import com.amaxonia.erp.domain.model.SelectedOption
import com.amaxonia.erp.ui.components.isLargeScreen
import java.util.Locale

/**
 * Modal de Selección de Opciones (Combo, Modificadores, Extras)
 * Estilos fieles 1:1 a flow-app.css (.fcp-modal--opciones, .fcp-head, .fcp-body, .fcp-grupo, .fcp-opcion, .fcp-foot)
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProductOptionsDialog(
    product: Product,
    groups: List<ProductOptionGroup>,
    isLoading: Boolean,
    errorMessage: String? = null,
    initialSelections: List<SelectedOption> = emptyList(),
    initialQuantity: Double = 1.0,
    onDismiss: () -> Unit,
    onConfirm: (List<SelectedOption>, Double) -> Unit,
) {
    // Mapa: optionId -> veces seleccionada
    var timesMap by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    var quantity by remember { mutableDoubleStateOf(initialQuantity.coerceAtLeast(1.0)) }

    // Inicializar con las opciones previas o por defecto que no estén agotadas
    LaunchedEffect(groups, initialSelections) {
        if (groups.isNotEmpty() && timesMap.isEmpty()) {
            val initial = mutableMapOf<String, Int>()
            if (initialSelections.isNotEmpty()) {
                initialSelections.forEach { opt ->
                    initial[opt.idGrupoItem] = opt.veces.coerceAtLeast(1)
                }
            } else {
                groups.forEach { group ->
                    if (!group.isIncluded) {
                        group.options.forEach { option ->
                            if (option.isDefault && !option.isOutOfStock) {
                                initial[option.id] = 1
                            }
                        }
                    }
                }
            }
            timesMap = initial
            quantity = initialQuantity.coerceAtLeast(1.0)
        }
    }

    // Calcular grupos que faltan por completar obligatorios
    fun getMissingMandatoryGroups(): List<ProductOptionGroup> {
        return groups.filter { group ->
            if (group.isIncluded) false
            else {
                val count = group.options.sumOf { timesMap[it.id] ?: 0 }
                count < group.min
            }
        }
    }

    // Calcular costo extra unitario acumulado por las opciones elegidas
    val extraUnitPrice = groups.sumOf { group ->
        group.options.sumOf { option ->
            val v = if (group.isIncluded) 1 else (timesMap[option.id] ?: 0)
            if (v > 0) option.extraPrice * v else 0.0
        }
    }

    val isLarge = isLargeScreen()
    val missingGroups = getMissingMandatoryGroups()
    val isReadyToAdd = missingGroups.isEmpty() && !isLoading
    val totalPrice = (product.mainPrice + extraUnitPrice) * quantity

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .widthIn(max = if (isLarge) 1020.dp else 900.dp)
                .fillMaxWidth(if (isLarge) 0.94f else 0.92f)
                .fillMaxHeight(if (isLarge) 0.90f else 0.88f),
            shape = RoundedCornerShape(22.dp),
            color = Color.White,
            shadowElevation = 16.dp,
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // ==========================================
                // 1. CABECERA (.fcp-head)
                // Gradiente azul Flow: 135deg #2488FA -> #201B82
                // ==========================================
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.linearGradient(
                                colors = listOf(Color(0xFF2488FA), Color(0xFF201B82)),
                            ),
                        )
                        .padding(horizontal = if (isLarge) 18.dp else 16.dp, vertical = if (isLarge) 16.dp else 14.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(if (isLarge) 14.dp else 12.dp),
                    ) {
                        // Imagen o Ícono (.fcp-head__img / .fcp-head__icon)
                        Box(
                            modifier = Modifier
                                .size(if (isLarge) 60.dp else 52.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color.White.copy(alpha = 0.20f))
                                .border(1.5.dp, Color.White.copy(alpha = 0.35f), RoundedCornerShape(14.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (product.photoUrl.isNotBlank()) {
                                AsyncImage(
                                    model = product.photoUrl,
                                    contentDescription = product.description,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop,
                                )
                            } else {
                                Icon(
                                    imageVector = if (product.isComboArticle) Icons.Default.Fastfood else Icons.Default.Tune,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(if (isLarge) 30.dp else 26.dp),
                                )
                            }
                        }

                        // Textos (.fcp-head__text)
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text(
                                    text = product.description,
                                    color = Color.White,
                                    fontSize = if (isLarge) 18.5.sp else 16.5.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false),
                                )
                                val badgeText = if (product.isComboArticle) "Combo" else "Opciones"
                                val badgeBg = if (product.isComboArticle) Color(0xFFFFF8EB) else Color(0xFFEEF5FF)
                                val badgeColor = if (product.isComboArticle) Color(0xFF92400E) else Color(0xFF201B82)
                                Surface(
                                    shape = RoundedCornerShape(999.dp),
                                    color = badgeBg,
                                ) {
                                    Text(
                                        text = badgeText,
                                        fontSize = if (isLarge) 10.5.sp else 9.5.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = badgeColor,
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                                    )
                                }
                            }
                            val priceDesc = if (product.isExempt || product.taxRate <= 0.0) {
                                "Precio $${String.format(Locale.US, "%.2f", product.mainPrice)}"
                            } else {
                                val sinIva = product.mainPrice / (1.0 + product.taxRate / 100.0)
                                "Precio $${String.format(Locale.US, "%.2f", sinIva)} + ITBMS ${String.format(Locale.US, "%.1f", product.taxRate)}%"
                            }
                            Text(
                                text = priceDesc,
                                color = Color.White.copy(alpha = 0.85f),
                                fontSize = if (isLarge) 13.5.sp else 12.5.sp,
                                fontWeight = FontWeight.Normal,
                            )
                        }

                        // Botón de cerrar (.fcp-icon-btn)
                        Box(
                            modifier = Modifier
                                .size(if (isLarge) 40.dp else 36.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.White.copy(alpha = 0.16f))
                                .clickable { onDismiss() },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Cerrar",
                                tint = Color.White,
                                modifier = Modifier.size(if (isLarge) 20.dp else 18.dp),
                            )
                        }
                    }
                }

                // ==========================================
                // 2. CUERPO DE OPCIONES (.fcp-body)
                // Fondo lienzo #F4F5F8
                // ==========================================
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(Color(0xFFF4F5F8)),
                ) {
                    if (isLoading) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                CircularProgressIndicator(
                                    color = Color(0xFF2488FA),
                                    strokeWidth = 3.dp,
                                    modifier = Modifier.size(32.dp),
                                )
                                val loadingMsg = if (product.isComboArticle) "Cargando opciones del combo..." else "Cargando opciones del producto..."
                                Text(
                                    text = loadingMsg,
                                    color = Color(0xFF64748B),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                )
                            }
                        }
                    } else if (!errorMessage.isNullOrBlank()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                text = errorMessage,
                                color = Color(0xFFE11D48),
                                fontSize = 13.5.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(24.dp),
                            )
                        }
                    } else if (groups.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            val emptyMsg = if (product.isComboArticle) "Este combo no posee opciones configuradas." else "Este producto no posee opciones configuradas."
                            Text(
                                text = emptyMsg,
                                color = Color(0xFF94A3B8),
                                fontSize = 13.5.sp,
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(14.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            items(groups, key = { it.id }) { group ->
                                if (group.isIncluded) {
                                    // ------------------------------------------
                                    // Bloque "Siempre Incluye" (.fcp-incluye)
                                    // ------------------------------------------
                                    Card(
                                        shape = RoundedCornerShape(16.dp),
                                        colors = CardDefaults.cardColors(containerColor = Color(0xFFECFDF5)),
                                        border = BorderStroke(1.dp, Color(0xFFA7F3D0)),
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Column(modifier = Modifier.padding(if (isLarge) 16.dp else 12.dp)) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.CheckCircle,
                                                    contentDescription = null,
                                                    tint = Color(0xFF047857),
                                                    modifier = Modifier.size(if (isLarge) 18.dp else 15.dp),
                                                )
                                                Text(
                                                    text = "INCLUYE",
                                                    color = Color(0xFF047857),
                                                    fontSize = if (isLarge) 13.sp else 11.5.sp,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    letterSpacing = 0.5.sp,
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(if (isLarge) 10.dp else 8.dp))
                                            FlowRow(
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                            ) {
                                                group.options.forEach { opt ->
                                                    Surface(
                                                        shape = RoundedCornerShape(999.dp),
                                                        color = Color.White,
                                                        border = BorderStroke(0.5.dp, Color(0xFFA7F3D0)),
                                                    ) {
                                                        Row(
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                            modifier = Modifier.padding(
                                                                start = if (opt.photoUrl.isNotBlank()) 4.dp else (if (isLarge) 12.dp else 10.dp),
                                                                end = if (isLarge) 12.dp else 10.dp,
                                                                top = if (isLarge) 4.dp else 3.dp,
                                                                bottom = if (isLarge) 4.dp else 3.dp,
                                                            ),
                                                        ) {
                                                            if (opt.photoUrl.isNotBlank()) {
                                                                Box(
                                                                    modifier = Modifier
                                                                        .size(if (isLarge) 32.dp else 26.dp)
                                                                        .clip(CircleShape)
                                                                        .background(Color(0xFFF4F5F8)),
                                                                    contentAlignment = Alignment.Center,
                                                                ) {
                                                                    AsyncImage(
                                                                        model = opt.photoUrl,
                                                                        contentDescription = opt.name,
                                                                        modifier = Modifier.fillMaxSize(),
                                                                        contentScale = ContentScale.Crop,
                                                                    )
                                                                }
                                                            }
                                                            Text(
                                                                text = (if (opt.quantity > 1) "${opt.quantity.toInt()}× " else "") + opt.name,
                                                                fontSize = if (isLarge) 13.5.sp else 12.sp,
                                                                fontWeight = FontWeight.SemiBold,
                                                                color = Color(0xFF0F0C3D),
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                } else {
                                    // ------------------------------------------
                                    // Bloque de Grupo (.fcp-grupo)
                                    // ------------------------------------------
                                    val currentCount = group.options.sumOf { timesMap[it.id] ?: 0 }
                                    val isPending = group.min > 0 && currentCount < group.min
                                    val isComplete = currentCount >= group.min && currentCount > 0

                                    val borderColor = when {
                                        isPending -> Color(0xFFFDE7B8)
                                        isComplete -> Color(0xFFA7F3D0)
                                        else -> Color.Transparent
                                    }

                                    Card(
                                        shape = RoundedCornerShape(16.dp),
                                        colors = CardDefaults.cardColors(containerColor = Color.White),
                                        border = BorderStroke(if (isPending || isComplete) 1.5.dp else 0.5.dp, if (isPending || isComplete) borderColor else Color(0xFFE4E9F0)),
                                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Column(modifier = Modifier.padding(if (isLarge) 16.dp else 14.dp)) {
                                            // Encabezado del grupo (.fcp-grupo__head)
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.Top,
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = group.name,
                                                        fontSize = if (isLarge) 17.sp else 15.sp,
                                                        fontWeight = FontWeight.ExtraBold,
                                                        color = Color(0xFF0F0C3D),
                                                    )
                                                    val ruleText = if (group.min > 0) {
                                                        if (group.min == group.max) "Obligatorio · elige ${group.max}"
                                                        else "Obligatorio · elige de ${group.min} a ${group.max}"
                                                    } else {
                                                        if (group.max == 1) "Opcional · elige 1"
                                                        else "Opcional · hasta ${group.max}"
                                                    }
                                                    Text(
                                                        text = ruleText,
                                                        fontSize = if (isLarge) 13.sp else 11.5.sp,
                                                        fontWeight = if (isPending) FontWeight.Bold else FontWeight.Normal,
                                                        color = if (isPending) Color(0xFFDB8F14) else Color(0xFF64748B),
                                                    )
                                                }

                                                // Badge de estado (.fcp-grupo__estado)
                                                Surface(
                                                    shape = RoundedCornerShape(999.dp),
                                                    color = if (isComplete) Color(0xFFECFDF5) else Color(0xFFF4F5F8),
                                                ) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                        modifier = Modifier.padding(horizontal = if (isLarge) 10.dp else 8.dp, vertical = if (isLarge) 4.dp else 3.dp),
                                                    ) {
                                                        if (isComplete) {
                                                            Icon(
                                                                imageVector = Icons.Default.Check,
                                                                contentDescription = null,
                                                                tint = Color(0xFF047857),
                                                                modifier = Modifier.size(if (isLarge) 13.dp else 11.dp),
                                                            )
                                                        }
                                                        Text(
                                                            text = "$currentCount/${group.max}",
                                                            fontSize = if (isLarge) 12.5.sp else 11.sp,
                                                            fontWeight = FontWeight.ExtraBold,
                                                            color = if (isComplete) Color(0xFF047857) else Color(0xFF64748B),
                                                        )
                                                    }
                                                }
                                            }

                                            Spacer(modifier = Modifier.height(if (isLarge) 12.dp else 10.dp))

                                            // Lista de Opciones (Adaptable: 3 columnas amplias en pantallas grandes, 2 en medianas)
                                            val columnsCount = if (isLarge) 3 else 2
                                            Column(verticalArrangement = Arrangement.spacedBy(if (isLarge) 10.dp else 8.dp)) {
                                                group.options.chunked(columnsCount).forEach { rowOptions ->
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.spacedBy(if (isLarge) 10.dp else 8.dp),
                                                    ) {
                                                        rowOptions.forEach { opt ->
                                                            Box(modifier = Modifier.weight(1f)) {
                                                                val optTimes = timesMap[opt.id] ?: 0
                                                                val isSelected = optTimes > 0

                                                                Surface(
                                                                    shape = RoundedCornerShape(12.dp),
                                                                    color = if (isSelected) Color(0xFFEEF5FF) else Color.White,
                                                                    border = BorderStroke(
                                                                        width = if (isSelected) 1.5.dp else 1.dp,
                                                                        color = if (isSelected) Color(0xFF2488FA) else Color(0xFFE4E9F0),
                                                                    ),
                                                                    modifier = Modifier
                                                                        .fillMaxWidth()
                                                                        .clip(RoundedCornerShape(12.dp))
                                                                        .clickable(enabled = !opt.isOutOfStock) {
                                                                            val currentGroupCount = group.options.sumOf { timesMap[it.id] ?: 0 }
                                                                            if (group.max == 1) {
                                                                                // Reemplazar o desmarcar
                                                                                val next = timesMap.toMutableMap()
                                                                                group.options.forEach { next.remove(it.id) }
                                                                                if (optTimes == 0) {
                                                                                    next[opt.id] = 1
                                                                                }
                                                                                timesMap = next
                                                                            } else {
                                                                                // Repetible o múltiple
                                                                                if (currentGroupCount < group.max) {
                                                                                    timesMap = timesMap + (opt.id to (optTimes + 1))
                                                                                } else if (optTimes > 0) {
                                                                                    timesMap = timesMap - opt.id
                                                                                }
                                                                            }
                                                                        },
                                                                ) {
                                                                    Row(
                                                                        modifier = Modifier
                                                                            .fillMaxWidth()
                                                                            .padding(horizontal = if (isLarge) 10.dp else 8.dp, vertical = if (isLarge) 10.dp else 8.dp),
                                                                        verticalAlignment = Alignment.CenterVertically,
                                                                        horizontalArrangement = Arrangement.spacedBy(if (isLarge) 9.dp else 7.dp),
                                                                    ) {
                                                                        // Miniatura de Imagen (.fcp-opcion__img) o Indicador Selección (.fcp-opcion__marca)
                                                                        if (opt.photoUrl.isNotBlank()) {
                                                                            Box(
                                                                                modifier = Modifier
                                                                                    .size(if (isLarge) 46.dp else 38.dp)
                                                                                    .clip(RoundedCornerShape(8.dp))
                                                                                    .background(Color(0xFFF4F5F8)),
                                                                                contentAlignment = Alignment.Center,
                                                                            ) {
                                                                                AsyncImage(
                                                                                    model = opt.photoUrl,
                                                                                    contentDescription = opt.name,
                                                                                    modifier = Modifier.fillMaxSize(),
                                                                                    contentScale = ContentScale.Crop,
                                                                                )
                                                                                if (isSelected) {
                                                                                    Box(
                                                                                        modifier = Modifier
                                                                                            .align(Alignment.BottomEnd)
                                                                                            .padding(1.dp)
                                                                                            .size(if (isLarge) 16.dp else 14.dp)
                                                                                            .clip(CircleShape)
                                                                                            .background(Color(0xFF2488FA))
                                                                                            .border(1.dp, Color.White, CircleShape),
                                                                                        contentAlignment = Alignment.Center,
                                                                                    ) {
                                                                                        Icon(
                                                                                            imageVector = Icons.Default.Check,
                                                                                            contentDescription = null,
                                                                                            tint = Color.White,
                                                                                            modifier = Modifier.size(if (isLarge) 11.dp else 9.dp),
                                                                                        )
                                                                                    }
                                                                                }
                                                                            }
                                                                        } else {
                                                                            Box(
                                                                                modifier = Modifier
                                                                                    .size(if (isLarge) 24.dp else 20.dp)
                                                                                    .clip(if (group.max == 1) CircleShape else RoundedCornerShape(6.dp))
                                                                                    .background(if (isSelected) Color(0xFF2488FA) else Color(0xFFF4F5F8))
                                                                                    .border(
                                                                                        1.dp,
                                                                                        if (isSelected) Color(0xFF2488FA) else Color(0xFFCBD5E1),
                                                                                        if (group.max == 1) CircleShape else RoundedCornerShape(6.dp),
                                                                                    ),
                                                                                contentAlignment = Alignment.Center,
                                                                            ) {
                                                                                if (isSelected) {
                                                                                    Icon(
                                                                                        imageVector = Icons.Default.Check,
                                                                                        contentDescription = null,
                                                                                        tint = Color.White,
                                                                                        modifier = Modifier.size(if (isLarge) 14.dp else 12.dp),
                                                                                    )
                                                                                }
                                                                            }
                                                                        }

                                                                        // Textos de opción
                                                                        Column(modifier = Modifier.weight(1f)) {
                                                                            Text(
                                                                                text = opt.name,
                                                                                fontSize = if (isLarge) 14.sp else 11.5.sp,
                                                                                fontWeight = FontWeight.Bold,
                                                                                color = if (opt.isOutOfStock) Color(0xFF94A3B8) else Color(0xFF0F0C3D),
                                                                                maxLines = 2,
                                                                                overflow = TextOverflow.Ellipsis,
                                                                                lineHeight = if (isLarge) 17.sp else 13.5.sp,
                                                                            )
                                                                            val subLabel = when {
                                                                                opt.isOutOfStock -> "Agotado"
                                                                                opt.extraPrice > 0 -> "+ $${String.format(Locale.US, "%.2f", opt.extraPrice)}"
                                                                                group.isCombo -> "Incluido"
                                                                                else -> ""
                                                                            }
                                                                            if (subLabel.isNotBlank()) {
                                                                                Text(
                                                                                    text = subLabel,
                                                                                    fontSize = if (isLarge) 12.sp else 10.5.sp,
                                                                                    fontWeight = FontWeight.SemiBold,
                                                                                    color = if (opt.isOutOfStock) Color(0xFFE11D48) else if (isSelected) Color(0xFF201B82) else Color(0xFF64748B),
                                                                                )
                                                                            }
                                                                        }

                                                                        // Contador multiplicador si > 1
                                                                        if (optTimes > 1) {
                                                                            Surface(
                                                                                shape = RoundedCornerShape(999.dp),
                                                                                color = Color(0xFF2488FA),
                                                                            ) {
                                                                                Text(
                                                                                    text = "×$optTimes",
                                                                                    color = Color.White,
                                                                                    fontSize = if (isLarge) 11.sp else 10.sp,
                                                                                    fontWeight = FontWeight.ExtraBold,
                                                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                                                )
                                                                            }
                                                                        }

                                                                        // Botón de restar si repetible y seleccionado
                                                                        if (isSelected && group.max > 1) {
                                                                            Box(
                                                                                modifier = Modifier
                                                                                    .size(if (isLarge) 26.dp else 22.dp)
                                                                                    .clip(CircleShape)
                                                                                    .background(Color.White)
                                                                                    .border(1.dp, Color(0xFFDCEBFF), CircleShape)
                                                                                    .clickable {
                                                                                        if (optTimes > 1) {
                                                                                            timesMap = timesMap + (opt.id to (optTimes - 1))
                                                                                        } else {
                                                                                            timesMap = timesMap - opt.id
                                                                                        }
                                                                                    },
                                                                                contentAlignment = Alignment.Center,
                                                                            ) {
                                                                                Icon(
                                                                                    imageVector = Icons.Default.Remove,
                                                                                    contentDescription = "Quitar uno",
                                                                                    tint = Color(0xFF201B82),
                                                                                    modifier = Modifier.size(if (isLarge) 14.dp else 12.dp),
                                                                                )
                                                                            }
                                                                        }
                                                                    }
                                                                }
                                                            }
                                                        }
                                                        // Rellenar espacios vacíos en la última fila para mantener el ancho uniforme de columnas
                                                        repeat(columnsCount - rowOptions.size) {
                                                            Spacer(modifier = Modifier.weight(1f))
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // ==========================================
                // 3. PIE (.fcp-foot)
                // Stepper + Botón Agregar
                // ==========================================
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = Color.White,
                    shadowElevation = 8.dp,
                    border = BorderStroke(1.dp, Color(0xFFE4E9F0)),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = if (isLarge) 18.dp else 14.dp, vertical = if (isLarge) 12.dp else 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        // Stepper de Cantidad (.fcp-stepper)
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFFF4F5F8),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(2.dp),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(if (isLarge) 42.dp else 34.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Color.White)
                                        .clickable {
                                            if (quantity > 1.0) quantity -= 1.0
                                        },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Remove,
                                        contentDescription = "Menos",
                                        tint = Color(0xFF0F0C3D),
                                        modifier = Modifier.size(if (isLarge) 18.dp else 16.dp),
                                    )
                                }

                                Text(
                                    text = "${quantity.toInt()}",
                                    fontSize = if (isLarge) 17.sp else 15.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color(0xFF0F0C3D),
                                    modifier = Modifier.padding(horizontal = if (isLarge) 14.dp else 12.dp),
                                )

                                Box(
                                    modifier = Modifier
                                        .size(if (isLarge) 42.dp else 34.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Color.White)
                                        .clickable { quantity += 1.0 },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = "Más",
                                        tint = Color(0xFF0F0C3D),
                                        modifier = Modifier.size(if (isLarge) 18.dp else 16.dp),
                                    )
                                }
                            }
                        }

                        // Botón Agregar (.fcp-btn--pri)
                        Button(
                            onClick = {
                                if (isReadyToAdd) {
                                    // Recopilar lista final de SelectedOption
                                    val result = mutableListOf<SelectedOption>()
                                    groups.forEach { group ->
                                        group.options.forEach { opt ->
                                            val v = if (group.isIncluded) 1 else (timesMap[opt.id] ?: 0)
                                            if (v > 0) {
                                                result.add(
                                                    SelectedOption(
                                                        idGrupo = group.id,
                                                        grupo = group.name,
                                                        tipo = group.type,
                                                        idGrupoItem = opt.id,
                                                        idItem = opt.productId,
                                                        codItem = opt.productCode,
                                                        nombre = opt.name,
                                                        precio = opt.extraPrice * v,
                                                        cantidad = opt.quantity * v,
                                                        veces = v,
                                                    ),
                                                )
                                            }
                                        }
                                    }
                                    onConfirm(result, quantity)
                                }
                            },
                            enabled = isReadyToAdd,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF2488FA),
                                disabledContainerColor = Color(0xFFD5DDE8),
                                contentColor = Color.White,
                                disabledContentColor = Color(0xFF475569),
                            ),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(if (isLarge) 52.dp else 44.dp),
                            contentPadding = PaddingValues(horizontal = if (isLarge) 18.dp else 14.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ShoppingCart,
                                        contentDescription = null,
                                        modifier = Modifier.size(if (isLarge) 20.dp else 17.dp),
                                    )
                                    val label = if (missingGroups.isNotEmpty()) {
                                        "Elige ${missingGroups.first().name.lowercase()}"
                                    } else {
                                        if (quantity > 1) "Agregar ${quantity.toInt()}" else "Agregar"
                                    }
                                    Text(
                                        text = label,
                                        fontSize = if (isLarge) 15.5.sp else 13.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }

                                if (isReadyToAdd) {
                                    Text(
                                        text = "$${String.format(Locale.US, "%.2f", totalPrice)}",
                                        fontSize = if (isLarge) 17.sp else 14.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
