package com.amaxonia.kiosk.ui.customizer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddShoppingCart
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.amaxonia.kiosk.R
import com.amaxonia.kiosk.core.money.Money
import com.amaxonia.kiosk.core.network.KioskItemDto
import com.amaxonia.kiosk.core.network.KioskModifierGroupDto
import com.amaxonia.kiosk.core.network.KioskModifierOptionDto
import com.amaxonia.kiosk.ui.components.KioskButton
import com.amaxonia.kiosk.ui.components.KioskCard
import com.amaxonia.kiosk.ui.components.KioskIconButton
import com.amaxonia.kiosk.ui.components.KioskImage
import com.amaxonia.kiosk.ui.components.PricePill
import com.amaxonia.kiosk.ui.components.QuantityStepper
import com.amaxonia.kiosk.ui.components.centeredMaxWidth
import com.amaxonia.kiosk.ui.components.foodGlyphFor
import com.amaxonia.kiosk.ui.components.secondaryText
import com.amaxonia.kiosk.ui.theme.KioskColors
import com.amaxonia.kiosk.ui.theme.LocalHighContrast
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.math.BigDecimal

private const val ADDED_FEEDBACK_MS = 650L
private const val OPTIONS_PER_ROW = 2
private const val SOLD_OUT_ALPHA = 0.45f
private const val HERO_FRACTION = 0.36f
private const val LANDSCAPE_HERO_FRACTION = 0.5f
private const val LANDSCAPE_HERO_WEIGHT = 0.4f
private const val HERO_GLYPH_RATIO = 0.4f
private val MinHeroHeight = 320.dp
private val MaxHeroHeight = 560.dp
private val HeroSheetOverlap = 48.dp

/** Number of list items rendered before the first modifier group (the hero). */
private const val GROUPS_LIST_OFFSET = 1

@Composable
fun CustomizerScreen(
    viewModel: ProductCustomizerViewModel,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val item = uiState.item
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var added by remember { mutableStateOf(false) }

    LaunchedEffect(added) {
        if (added) {
            delay(ADDED_FEEDBACK_MS)
            onDismiss()
        }
    }

    val onAdd: () -> Unit = {
        if (!added) {
            if (uiState.isValid) {
                viewModel.addToCart(onSuccess = { added = true })
            } else {
                viewModel.addToCart(onSuccess = {})
                val firstInvalid =
                    item.modifierGroups.indexOfFirst { group ->
                        val count = uiState.selectedOptions[group.id]?.size ?: 0
                        count < group.min || count > group.max
                    }
                if (firstInvalid >= 0) {
                    scope.launch { listState.animateScrollToItem(firstInvalid + GROUPS_LIST_OFFSET) }
                }
            }
        }
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()) {
                // The photo gives way to the options when the screen is short ("pantalla baja").
                val landscape = maxWidth > maxHeight
                val heroHeight =
                    if (landscape) {
                        (maxHeight * LANDSCAPE_HERO_FRACTION).coerceIn(MinHeroHeight, MaxHeroHeight)
                    } else {
                        (maxHeight * HERO_FRACTION).coerceIn(MinHeroHeight, MaxHeroHeight)
                    }
                val options: LazyListScope.() -> Unit = {
                    itemsIndexed(item.modifierGroups, key = { _, group -> group.id }) { _, group ->
                        ModifierGroupSection(
                            group = group,
                            selectedOptions = uiState.selectedOptions[group.id].orEmpty(),
                            hasError = uiState.validationErrors.containsKey(group.id),
                            onOptionToggled = { option -> viewModel.toggleOption(group, option) },
                        )
                    }
                    item(key = "note") {
                        NoteSection(note = uiState.note, onNoteChanged = viewModel::onNoteChanged)
                    }
                }
                if (landscape) {
                    // Landscape: the product stays visible on the left while the options scroll on the right.
                    Row(modifier = Modifier.fillMaxSize()) {
                        Column(
                            modifier =
                                Modifier
                                    .weight(LANDSCAPE_HERO_WEIGHT)
                                    .fillMaxHeight()
                                    .verticalScroll(rememberScrollState()),
                        ) {
                            ProductHero(item = item, imageHeight = heroHeight)
                        }
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.weight(1f - LANDSCAPE_HERO_WEIGHT).fillMaxHeight(),
                            contentPadding = PaddingValues(bottom = 40.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            // Keeps the groups at GROUPS_LIST_OFFSET, like the portrait list with its hero.
                            item(key = "top") { Spacer(Modifier.height(16.dp)) }
                            options()
                        }
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 40.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        item(key = "hero") { ProductHero(item = item, imageHeight = heroHeight) }
                        options()
                    }
                }

                KioskIconButton(
                    icon = Icons.Rounded.Close,
                    contentDescription = stringResource(R.string.cd_close),
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.TopStart).padding(28.dp),
                )
            }

            CustomizerBottomBar(
                quantity = uiState.quantity,
                totalPrice = uiState.totalPrice,
                secondaryTotal = uiState.totalPrice.secondaryText(uiState.currency),
                isValid = uiState.isValid,
                added = added,
                onDecrement = viewModel::decrementQuantity,
                onIncrement = viewModel::incrementQuantity,
                onAdd = onAdd,
            )
        }
    }
}

@Composable
private fun ProductHero(
    item: KioskItemDto,
    imageHeight: Dp,
) {
    val colors = MaterialTheme.colorScheme
    val glyph = remember(item.name) { foodGlyphFor(item.name) }
    Box(modifier = Modifier.fillMaxWidth()) {
        KioskImage(
            url = item.imageUrl,
            contentDescription = item.name,
            placeholderIcon = glyph,
            placeholderIconSize = imageHeight * HERO_GLYPH_RATIO,
            modifier = Modifier.fillMaxWidth().height(imageHeight),
        )
        Surface(
            modifier = Modifier.fillMaxWidth().padding(top = imageHeight - HeroSheetOverlap),
            shape = RoundedCornerShape(topStart = 48.dp, topEnd = 48.dp),
            color = colors.background,
        ) {
            Column(modifier = Modifier.padding(start = 40.dp, end = 40.dp, top = 40.dp, bottom = 8.dp)) {
                Row(verticalAlignment = Alignment.Top) {
                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.displaySmall,
                        color = colors.onBackground,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(24.dp))
                    PricePill(
                        text = Money.fromString(item.price).toDisplayString(),
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Black),
                    )
                }
                if (!item.description.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = item.description,
                        style = MaterialTheme.typography.bodyLarge,
                        color = colors.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun ModifierGroupSection(
    group: KioskModifierGroupDto,
    selectedOptions: List<KioskModifierOptionDto>,
    hasError: Boolean,
    onOptionToggled: (KioskModifierOptionDto) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val required = group.min > 0
    val satisfied = selectedOptions.size in group.min..group.max
    val rows = remember(group.options) { group.options.chunked(OPTIONS_PER_ROW) }

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = group.name,
                style = MaterialTheme.typography.headlineSmall,
                color = colors.onBackground,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(16.dp))
            RequirementPill(group = group, required = required, satisfied = satisfied && selectedOptions.isNotEmpty())
        }

        AnimatedVisibility(
            visible = hasError && !satisfied,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Row(modifier = Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.ErrorOutline, contentDescription = null, tint = colors.error, modifier = Modifier.size(32.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    text =
                        if (group.min <= 1) {
                            stringResource(R.string.customizer_group_error_one)
                        } else {
                            stringResource(R.string.customizer_group_error_min, group.min)
                        },
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.error,
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
            rows.forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    row.forEach { option ->
                        OptionTile(
                            option = option,
                            isSelected = selectedOptions.any { it.id == option.id },
                            singleChoice = group.max == 1,
                            onClick = { onOptionToggled(option) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    repeat(OPTIONS_PER_ROW - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun RequirementPill(
    group: KioskModifierGroupDto,
    required: Boolean,
    satisfied: Boolean,
) {
    val colors = MaterialTheme.colorScheme
    val (container, content) =
        when {
            required && satisfied -> KioskColors.success to Color.White
            required -> colors.primary to colors.onPrimary
            else -> colors.surfaceVariant to colors.onSurfaceVariant
        }
    val text =
        when {
            required && group.min == group.max -> stringResource(R.string.customizer_required_choose, group.min)
            required -> stringResource(R.string.customizer_required_min, group.min)
            group.max == 1 -> stringResource(R.string.customizer_optional_one)
            else -> stringResource(R.string.customizer_optional_up_to, group.max)
        }
    Surface(shape = RoundedCornerShape(percent = 50), color = container, contentColor = content) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (required && satisfied) {
                Icon(Icons.Rounded.CheckCircle, contentDescription = null, modifier = Modifier.size(28.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(text = text, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun OptionTile(
    option: KioskModifierOptionDto,
    isSelected: Boolean,
    singleChoice: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val extra = remember(option.extraPrice) { Money.fromString(option.extraPrice) }

    KioskCard(
        modifier =
            modifier
                .heightIn(min = 150.dp)
                .graphicsLayer { alpha = if (option.soldOut) SOLD_OUT_ALPHA else 1f },
        selected = isSelected,
        onClick = if (option.soldOut) null else onClick,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 150.dp).padding(horizontal = 24.dp, vertical = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SelectionIndicator(selected = isSelected, round = singleChoice)
            Spacer(Modifier.width(20.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = option.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                when {
                    option.soldOut ->
                        Text(
                            text = stringResource(R.string.menu_sold_out),
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.error,
                        )
                    extra.amount > BigDecimal.ZERO ->
                        Text(
                            text = stringResource(R.string.customizer_extra_price, extra.toDisplayString()),
                            style = MaterialTheme.typography.titleSmall,
                            color = colors.primary,
                        )
                }
            }
        }
    }
}

@Composable
private fun SelectionIndicator(
    selected: Boolean,
    round: Boolean,
) {
    val colors = MaterialTheme.colorScheme
    val shape = if (round) CircleShape else RoundedCornerShape(12.dp)
    val fill by animateColorAsState(if (selected) colors.secondary else Color.Transparent, label = "indicator_fill")
    val checkScale by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "indicator_check",
    )
    Box(
        modifier =
            Modifier
                .size(52.dp)
                .background(fill, shape)
                .border(BorderStroke(4.dp, if (selected) colors.secondary else colors.outline), shape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Rounded.Check,
            contentDescription = null,
            tint = colors.onPrimary,
            modifier =
                Modifier
                    .size(36.dp)
                    .graphicsLayer {
                        scaleX = checkScale
                        scaleY = checkScale
                    },
        )
    }
}

@Composable
private fun NoteSection(
    note: String,
    onNoteChanged: (String) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 20.dp)) {
        Text(
            text = stringResource(R.string.customizer_kitchen_note),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(
            value = note,
            onValueChange = onNoteChanged,
            placeholder = { Text(stringResource(R.string.customizer_note_placeholder), style = MaterialTheme.typography.bodyLarge) },
            textStyle = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.fillMaxWidth().heightIn(min = 96.dp),
            shape = MaterialTheme.shapes.medium,
            singleLine = true,
        )
    }
}

@Composable
private fun CustomizerBottomBar(
    quantity: Int,
    totalPrice: Money,
    secondaryTotal: String,
    isValid: Boolean,
    added: Boolean,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
    onAdd: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = colors.surface,
        shadowElevation = 20.dp,
        shape = RoundedCornerShape(topStart = 40.dp, topEnd = 40.dp),
        border = if (LocalHighContrast.current) BorderStroke(3.dp, colors.onSurface) else null,
    ) {
        Column(modifier = Modifier.centeredMaxWidth().padding(horizontal = 28.dp, vertical = 24.dp)) {
            AnimatedVisibility(visible = !isValid) {
                Text(
                    text = stringResource(R.string.customizer_missing_required),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.error,
                    modifier = Modifier.padding(bottom = 16.dp, start = 8.dp),
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                QuantityStepper(
                    quantity = quantity,
                    onDecrement = onDecrement,
                    onIncrement = onIncrement,
                    decrementEnabled = quantity > 1,
                )
                Spacer(Modifier.width(24.dp))
                KioskButton(
                    text =
                        if (added) {
                            stringResource(R.string.customizer_added)
                        } else {
                            stringResource(R.string.customizer_add_to_cart, totalPrice.toDisplayString())
                        },
                    onClick = onAdd,
                    icon = if (added) Icons.Rounded.CheckCircle else Icons.Rounded.AddShoppingCart,
                    dimmed = !isValid,
                    modifier = Modifier.weight(1f),
                )
            }
            if (secondaryTotal.isNotBlank()) {
                Text(
                    text = secondaryTotal,
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.End).padding(top = 8.dp, end = 24.dp),
                )
            }
        }
    }
}
