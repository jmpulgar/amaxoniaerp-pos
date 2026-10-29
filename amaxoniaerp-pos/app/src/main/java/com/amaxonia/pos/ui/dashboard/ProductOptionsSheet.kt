package com.amaxonia.pos.ui.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import com.amaxonia.pos.domain.model.options.ProductComposition
import com.amaxonia.pos.domain.model.options.ProductOptionGroup
import com.amaxonia.pos.domain.model.options.ProductOptionItem
import com.amaxonia.pos.domain.model.options.SelectedOption
import com.amaxonia.pos.ui.common.components.PosGradientButton
import com.amaxonia.pos.ui.theme.PosPalette
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductOptionsSheet(
    product: DashboardProduct,
    groups: List<ProductOptionGroup>,
    isLoading: Boolean,
    errorMessage: String?,
    onConfirm: (quantity: Int, composition: ProductComposition) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var quantity by remember { mutableIntStateOf(1) }

    // Map: idGrupo -> (idGrupoItem -> veces seleccionada)
    var selectedOptions by remember(groups) {
        val initial = mutableMapOf<String, Map<String, Int>>()
        groups.filterNot { it.isIncluded }.forEach { group ->
            val groupMap = mutableMapOf<String, Int>()
            group.opciones.forEach { option ->
                if (option.isDefault && !option.isSoldOut) {
                    groupMap[option.idGrupoItem] = 1
                }
            }
            if (groupMap.isNotEmpty()) {
                initial[group.idGrupo] = groupMap.toMap()
            }
        }
        mutableStateOf<Map<String, Map<String, Int>>>(initial.toMap())
    }

    fun isGroupSatisfied(group: ProductOptionGroup): Boolean {
        if (group.isIncluded) return true
        val count = selectedOptions[group.idGrupo]?.values?.sum() ?: 0
        return count >= group.minimo && count <= group.maximo
    }

    val unsatisfiedGroup = groups.firstOrNull { !it.isIncluded && !isGroupSatisfied(it) }
    val isValid = unsatisfiedGroup == null

    // Calcular precio de extras
    var extraConIvaTotal = 0.0
    groups.filterNot { it.isIncluded }.forEach { group ->
        val groupSelections = selectedOptions[group.idGrupo].orEmpty()
        group.opciones.forEach { option ->
            val times = groupSelections[option.idGrupoItem] ?: 0
            if (times > 0) {
                extraConIvaTotal += option.precioExtra * times
            }
        }
    }
    val taxRate = product.taxRate
    val extraSinIvaTotal =
        if (product.isExempt || taxRate <= 0.0) {
            extraConIvaTotal
        } else {
            extraConIvaTotal / (1.0 + (taxRate / 100.0))
        }

    val unitPriceWithTax = product.price + extraConIvaTotal
    val totalPrice = unitPriceWithTax * quantity

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 18.dp)
                    .padding(bottom = 16.dp),
        ) {
            ProductOptionsHeader(
                product = product,
                onDismiss = onDismiss,
            )

            Spacer(Modifier.height(12.dp))

            when {
                isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(220.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(36.dp),
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                "Cargando opciones del producto…",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                errorMessage != null -> {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(180.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = errorMessage,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
                groups.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(160.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "Este producto no tiene opciones configuradas.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.weight(1f, fill = false).heightIn(max = 440.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        items(groups, key = { it.idGrupo }) { group ->
                            if (group.isIncluded) {
                                IncludedItemsSection(group = group)
                            } else {
                                val currentSelections = selectedOptions[group.idGrupo].orEmpty()
                                OptionGroupSection(
                                    group = group,
                                    currentSelections = currentSelections,
                                    onOptionToggle = { option ->
                                        selectedOptions =
                                            toggleOptionSelection(
                                                currentMap = selectedOptions,
                                                group = group,
                                                option = option,
                                            )
                                    },
                                    onOptionIncrement = { option ->
                                        selectedOptions =
                                            incrementOptionSelection(
                                                currentMap = selectedOptions,
                                                group = group,
                                                option = option,
                                            )
                                    },
                                    onOptionDecrement = { option ->
                                        selectedOptions =
                                            decrementOptionSelection(
                                                currentMap = selectedOptions,
                                                group = group,
                                                option = option,
                                            )
                                    },
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(12.dp))

            // Footer con Stepper y Botón
            ProductOptionsFooter(
                quantity = quantity,
                totalPrice = totalPrice,
                isValid = isValid,
                unsatisfiedGroupName = unsatisfiedGroup?.nombre,
                onQuantityChange = { quantity = it.coerceAtLeast(1) },
                onConfirm = {
                    val selectedList = mutableListOf<SelectedOption>()
                    groups.filterNot { it.isIncluded }.forEach { group ->
                        val groupMap = selectedOptions[group.idGrupo].orEmpty()
                        group.opciones.forEach { option ->
                            val times = groupMap[option.idGrupoItem] ?: 0
                            if (times > 0) {
                                selectedList.add(
                                    SelectedOption(
                                        idGrupo = group.idGrupo,
                                        grupo = group.nombre,
                                        tipo = group.tipo,
                                        idGrupoItem = option.idGrupoItem,
                                        idItem = option.idItem,
                                        codItem = option.codItem,
                                        nombre = option.nombre,
                                        precio = Math.round(option.precioExtra * times * 100.0) / 100.0,
                                        cantidad = option.cantidad * times,
                                        veces = times,
                                    ),
                                )
                            }
                        }
                    }
                    val composition =
                        ProductComposition(
                            idCombo = product.sourceProduct?.idCombo.orEmpty(),
                            opciones = selectedList,
                            extraConIva = Math.round(extraConIvaTotal * 100.0) / 100.0,
                            extraSinIva = Math.round(extraSinIvaTotal * 10000.0) / 10000.0,
                        )
                    onConfirm(quantity, composition)
                },
            )
        }
    }
}

private fun toggleOptionSelection(
    currentMap: Map<String, Map<String, Int>>,
    group: ProductOptionGroup,
    option: ProductOptionItem,
): Map<String, Map<String, Int>> {
    if (option.isSoldOut) return currentMap
    val mutableRoot = currentMap.mapValues { it.value.toMutableMap() }.toMutableMap()
    val groupSelections = mutableRoot.getOrPut(group.idGrupo) { mutableMapOf() }
    val currentTimes = groupSelections[option.idGrupoItem] ?: 0

    if (group.isSingleChoice) {
        if (currentTimes > 0 && group.minimo == 0) {
            groupSelections.remove(option.idGrupoItem)
        } else {
            groupSelections.clear()
            groupSelections[option.idGrupoItem] = 1
        }
    } else {
        if (currentTimes > 0) {
            groupSelections.remove(option.idGrupoItem)
        } else {
            val totalInGroup = groupSelections.values.sum()
            if (totalInGroup < group.maximo) {
                groupSelections[option.idGrupoItem] = 1
            }
        }
    }
    return mutableRoot
}

private fun incrementOptionSelection(
    currentMap: Map<String, Map<String, Int>>,
    group: ProductOptionGroup,
    option: ProductOptionItem,
): Map<String, Map<String, Int>> {
    if (option.isSoldOut) return currentMap
    val mutableRoot = currentMap.mapValues { it.value.toMutableMap() }.toMutableMap()
    val groupSelections = mutableRoot.getOrPut(group.idGrupo) { mutableMapOf() }
    val totalInGroup = groupSelections.values.sum()
    if (totalInGroup < group.maximo) {
        val currentTimes = groupSelections[option.idGrupoItem] ?: 0
        groupSelections[option.idGrupoItem] = currentTimes + 1
    }
    return mutableRoot
}

private fun decrementOptionSelection(
    currentMap: Map<String, Map<String, Int>>,
    group: ProductOptionGroup,
    option: ProductOptionItem,
): Map<String, Map<String, Int>> {
    val mutableRoot = currentMap.mapValues { it.value.toMutableMap() }.toMutableMap()
    val groupSelections = mutableRoot.getOrPut(group.idGrupo) { mutableMapOf() }
    val currentTimes = groupSelections[option.idGrupoItem] ?: 0
    if (currentTimes > 1) {
        groupSelections[option.idGrupoItem] = currentTimes - 1
    } else {
        groupSelections.remove(option.idGrupoItem)
    }
    return mutableRoot
}

@Composable
private fun ProductOptionsHeader(
    product: DashboardProduct,
    onDismiss: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!product.imageUrl.isNullOrBlank()) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.size(52.dp).clip(RoundedCornerShape(10.dp)),
            ) {
                SubcomposeAsyncImage(
                    model = product.imageUrl,
                    contentDescription = product.name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    loading = {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        }
                    },
                    error = {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            Icon(Icons.Default.Tune, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        }
                    },
                )
            }
        } else {
            Box(
                modifier =
                    Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.Tune,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = product.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "Precio base: $${String.format(Locale.US, "%.2f", product.price)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onDismiss) {
            Icon(Icons.Default.Close, contentDescription = "Cerrar")
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun IncludedItemsSection(group: ProductOptionGroup) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.25f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "Incluye",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
            Spacer(Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                group.opciones.forEach { item ->
                    val qtyText = if (item.cantidad > 1.0) "${item.cantidad.roundToInt()}x " else ""
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        ) {
                            if (!item.photoUrl.isNullOrBlank()) {
                                SubcomposeAsyncImage(
                                    model = item.photoUrl,
                                    contentDescription = item.nombre,
                                    modifier = Modifier.size(22.dp).clip(RoundedCornerShape(4.dp)),
                                    contentScale = ContentScale.Crop,
                                    error = {
                                        Icon(
                                            Icons.Default.Inventory2,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                            tint = MaterialTheme.colorScheme.outline,
                                        )
                                    },
                                )
                                Spacer(Modifier.width(6.dp))
                            }
                            Text(
                                text = "$qtyText${item.nombre}",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OptionGroupSection(
    group: ProductOptionGroup,
    currentSelections: Map<String, Int>,
    onOptionToggle: (ProductOptionItem) -> Unit,
    onOptionIncrement: (ProductOptionItem) -> Unit,
    onOptionDecrement: (ProductOptionItem) -> Unit,
) {
    val totalChosen = currentSelections.values.sum()
    val isSatisfied = totalChosen in group.minimo..group.maximo && totalChosen > 0
    val isPending = group.minimo > 0 && totalChosen < group.minimo

    val ruleText =
        when {
            group.minimo > 0 && group.minimo == group.maximo -> "Obligatorio · elige ${group.maximo}"
            group.minimo > 0 -> "Obligatorio · elige de ${group.minimo} a ${group.maximo}"
            group.maximo == 1 -> "Opcional · elige 1"
            else -> "Opcional · hasta ${group.maximo}"
        }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    text = group.nombre,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = ruleText,
                    style = MaterialTheme.typography.bodySmall,
                    color =
                        if (isPending) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                )
            }
            Surface(
                shape = RoundedCornerShape(12.dp),
                color =
                    when {
                        isSatisfied -> MaterialTheme.colorScheme.primaryContainer
                        isPending -> MaterialTheme.colorScheme.errorContainer
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    },
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (isSatisfied) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                    }
                    Text(
                        text = "$totalChosen/${group.maximo}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color =
                            when {
                                isSatisfied -> MaterialTheme.colorScheme.onPrimaryContainer
                                isPending -> MaterialTheme.colorScheme.onErrorContainer
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            },
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            group.opciones.forEach { option ->
                val times = currentSelections[option.idGrupoItem] ?: 0
                val isSelected = times > 0
                OptionItemCard(
                    option = option,
                    group = group,
                    times = times,
                    isSelected = isSelected,
                    onToggle = { onOptionToggle(option) },
                    onIncrement = { onOptionIncrement(option) },
                    onDecrement = { onOptionDecrement(option) },
                )
            }
        }
    }
}

@Composable
private fun OptionItemCard(
    option: ProductOptionItem,
    group: ProductOptionGroup,
    times: Int,
    isSelected: Boolean,
    onToggle: () -> Unit,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
) {
    val borderColor =
        when {
            option.isSoldOut -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            isSelected -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.outlineVariant
        }
    val containerColor =
        when {
            option.isSoldOut -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
            isSelected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
            else -> MaterialTheme.colorScheme.surface
        }

    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(if (isSelected) 1.5.dp else 1.dp, borderColor),
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .clickable(enabled = !option.isSoldOut, onClick = onToggle),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (group.isSingleChoice) {
                Icon(
                    imageVector = if (isSelected) Icons.Default.RadioButtonChecked else Icons.Default.RadioButtonUnchecked,
                    contentDescription = null,
                    tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            } else {
                Icon(
                    imageVector = if (isSelected) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank,
                    contentDescription = null,
                    tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }

            Spacer(Modifier.width(10.dp))

            if (!option.photoUrl.isNullOrBlank()) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)),
                ) {
                    SubcomposeAsyncImage(
                        model = option.photoUrl,
                        contentDescription = option.nombre,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                        loading = {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                            }
                        },
                        error = {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                Icon(
                                    Icons.Default.Inventory2,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.outline,
                                )
                            }
                        },
                    )
                }
                Spacer(Modifier.width(10.dp))
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = option.nombre,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    color =
                        if (option.isSoldOut) {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                )
                if (option.isSoldOut) {
                    Text(
                        text = "Agotado",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            // Precio o etiqueta
            if (option.precioExtra > 0.0) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                ) {
                    Text(
                        text = "+$${String.format(Locale.US, "%.2f", option.precioExtra)}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            } else if (group.tipo.equals("COMBO", ignoreCase = true)) {
                Text(
                    text = "Incluido",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Controles de incremento/decremento solo si la opción es un ítem repetible
            val isRepeatable = group.maximo > 1 && !option.idItem.isNullOrBlank()
            if (isRepeatable && isSelected) {
                Spacer(Modifier.width(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier =
                        Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                            .padding(2.dp),
                ) {
                    Box(
                        modifier =
                            Modifier
                                .size(26.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surface)
                                .clickable(onClick = onDecrement),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Default.Remove,
                            contentDescription = "Menos",
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    Text(
                        text = "$times",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp),
                    )
                    Box(
                        modifier =
                            Modifier
                                .size(26.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                                .clickable(onClick = onIncrement),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = "Más",
                            tint = PosPalette.FixedWhite,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProductOptionsFooter(
    quantity: Int,
    totalPrice: Double,
    isValid: Boolean,
    unsatisfiedGroupName: String?,
    onQuantityChange: (Int) -> Unit,
    onConfirm: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Stepper de cantidad general
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier =
                Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .padding(4.dp),
        ) {
            Box(
                modifier =
                    Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(if (quantity > 1) MaterialTheme.colorScheme.surface else Color.Transparent)
                        .clickable(enabled = quantity > 1) { onQuantityChange(quantity - 1) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.Remove,
                    contentDescription = "Disminuir",
                    modifier = Modifier.size(16.dp),
                    tint =
                        if (quantity > 1) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        },
                )
            }
            Text(
                text = "$quantity",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 12.dp),
            )
            Box(
                modifier =
                    Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .clickable { onQuantityChange(quantity + 1) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = "Aumentar",
                    modifier = Modifier.size(16.dp),
                    tint = PosPalette.FixedWhite,
                )
            }
        }

        // Monto Total
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = "Total",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "$${String.format(Locale.US, "%.2f", totalPrice)}",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }

    Spacer(Modifier.height(14.dp))

    val buttonText =
        if (isValid) {
            "Agregar al carrito • $${String.format(Locale.US, "%.2f", totalPrice)}"
        } else {
            "Elige ${unsatisfiedGroupName ?: "opciones"}"
        }

    PosGradientButton(
        onClick = onConfirm,
        enabled = isValid,
        modifier = Modifier.fillMaxWidth().height(50.dp),
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Text(
                text = buttonText,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            if (isValid) {
                Spacer(Modifier.width(8.dp))
                Icon(
                    Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}
