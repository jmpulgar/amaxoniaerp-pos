package com.amaxonia.kiosk.ui.customizer

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.RadioButtonChecked
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.amaxonia.kiosk.R
import com.amaxonia.kiosk.core.money.Money
import com.amaxonia.kiosk.core.network.KioskItemDto
import com.amaxonia.kiosk.core.network.KioskModifierGroupDto
import com.amaxonia.kiosk.core.network.KioskModifierOptionDto
import com.amaxonia.kiosk.ui.components.KioskButton
import com.amaxonia.kiosk.ui.components.KioskButtonStyle
import com.amaxonia.kiosk.ui.components.KioskCard
import com.amaxonia.kiosk.ui.components.KioskImage
import com.amaxonia.kiosk.ui.components.QuantityStepper
import com.amaxonia.kiosk.ui.components.bottomHairline
import com.amaxonia.kiosk.ui.components.foodGlyphFor
import com.amaxonia.kiosk.ui.components.rightHairline
import com.amaxonia.kiosk.ui.components.secondaryText
import com.amaxonia.kiosk.ui.components.topHairline
import com.amaxonia.kiosk.ui.theme.KioskColors
import com.amaxonia.kiosk.ui.theme.LocalKioskCanvas
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.math.BigDecimal

private const val ADDED_FEEDBACK_MS = 650L
private const val AUTO_ADVANCE_MS = 280L
private const val STEP_SWAP_MS = 220
private const val SOLD_OUT_ALPHA = 0.45f
private const val OPTION_COLUMNS = 3
private const val LANDSCAPE_OPTION_COLUMNS = 5
private val StepsPanelWidth = 250.dp

/**
 * Product customizer as a step-by-step wizard, the way fast-food self-order kiosks build a combo:
 * one modifier group per step ("Selecciona tu bebida", "Selecciona extra"…) with the steps listed on
 * the left, and a final "Revisar orden" step with quantity, kitchen note and "Agregar a mi orden".
 * Single-choice steps advance on their own once an option is picked.
 */
@Composable
fun CustomizerScreen(
    viewModel: ProductCustomizerViewModel,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    initialStep: Int = 0,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val item = uiState.item
    val groups = item.modifierGroups
    val reviewStep = groups.size
    var step by remember { mutableIntStateOf(initialStep.coerceIn(0, reviewStep)) }
    var showStepError by remember { mutableStateOf(false) }
    var added by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(added) {
        if (added) {
            delay(ADDED_FEEDBACK_MS)
            onDismiss()
        }
    }

    fun groupSatisfied(group: KioskModifierGroupDto): Boolean = (uiState.selectedOptions[group.id]?.size ?: 0) in group.min..group.max

    fun goTo(target: Int) {
        showStepError = false
        step = target.coerceIn(0, reviewStep)
    }

    val onNext: () -> Unit = {
        val group = groups.getOrNull(step)
        if (group != null && !groupSatisfied(group)) {
            showStepError = true
        } else {
            goTo(step + 1)
        }
    }
    val onAdd: () -> Unit = {
        if (!added) {
            val firstInvalid = groups.indexOfFirst { !groupSatisfied(it) }
            if (firstInvalid >= 0) {
                goTo(firstInvalid)
                showStepError = true
            } else {
                viewModel.addToCart(onSuccess = { added = true })
            }
        }
    }
    val onBack: () -> Unit = { if (step == 0) onDismiss() else goTo(step - 1) }

    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize()) {
            ProductHeader(item = item, unitPrice = uiState.unitPrice)
            Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                StepsPanel(
                    groups = groups,
                    currentStep = step,
                    isDone = { index -> index < step && groupSatisfied(groups[index]) },
                    onStepSelected = { target -> if (target <= step || groups.take(target).all(::groupSatisfied)) goTo(target) },
                    modifier = Modifier.width(StepsPanelWidth).fillMaxHeight(),
                )
                AnimatedContent(
                    targetState = step,
                    transitionSpec = {
                        val direction = if (targetState > initialState) 1 else -1
                        (fadeIn(tween(STEP_SWAP_MS)) + slideInHorizontally(tween(STEP_SWAP_MS)) { it / 10 * direction }) togetherWith
                            fadeOut(tween(STEP_SWAP_MS / 2))
                    },
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    label = "customizer_step",
                ) { current ->
                    val group = groups.getOrNull(current)
                    if (group != null) {
                        GroupStep(
                            group = group,
                            selectedOptions = uiState.selectedOptions[group.id].orEmpty(),
                            showError = showStepError && !groupSatisfied(group),
                            onOptionToggled = { option ->
                                val wasSelected = uiState.selectedOptions[group.id].orEmpty().any { it.id == option.id }
                                viewModel.toggleOption(group, option)
                                showStepError = false
                                // Single choice: picking an option moves on, as on the reference kiosk.
                                if (group.max == 1 && !wasSelected && !option.soldOut) {
                                    scope.launch {
                                        delay(AUTO_ADVANCE_MS)
                                        if (step == current) goTo(current + 1)
                                    }
                                }
                            },
                        )
                    } else {
                        ReviewStep(
                            item = item,
                            uiState = uiState,
                            onNoteChanged = viewModel::onNoteChanged,
                            onDecrement = viewModel::decrementQuantity,
                            onIncrement = viewModel::incrementQuantity,
                        )
                    }
                }
            }
            WizardBottomBar(
                isReview = step == reviewStep,
                added = added,
                totalText = uiState.totalPrice.toDisplayString(),
                onBack = onBack,
                onCancel = onDismiss,
                onNext = onNext,
                onAdd = onAdd,
            )
        }
    }
}

/** Product photo, name and price across the top of every step. */
@Composable
private fun ProductHeader(
    item: KioskItemDto,
    unitPrice: Money,
) {
    val colors = MaterialTheme.colorScheme
    val glyph = remember(item.name) { foodGlyphFor(item.name) }
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .bottomHairline(colors.outlineVariant)
                .padding(horizontal = 32.dp, vertical = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        KioskImage(
            url = item.imageUrl,
            contentDescription = item.name,
            contentScale = ContentScale.Fit,
            placeholderIcon = glyph,
            placeholderIconSize = 64.dp,
            modifier = Modifier.size(128.dp).clip(RoundedCornerShape(10.dp)),
        )
        Spacer(Modifier.width(28.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.name,
                style = MaterialTheme.typography.headlineMedium,
                color = colors.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Text(text = unitPrice.toDisplayString(), style = MaterialTheme.typography.titleLarge, color = colors.onSurfaceVariant)
        }
    }
}

/** Left list of steps: done (check), current (filled dot) and pending (empty dot), plus "Revisar orden". */
@Composable
private fun StepsPanel(
    groups: List<KioskModifierGroupDto>,
    currentStep: Int,
    isDone: (Int) -> Boolean,
    onStepSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val reviewLabel = stringResource(R.string.customizer_step_review)
    val labels = remember(groups, reviewLabel) { groups.map { it.name } + reviewLabel }
    LazyColumn(
        modifier = modifier.rightHairline(colors.outlineVariant),
        contentPadding = PaddingValues(vertical = 20.dp),
    ) {
        itemsIndexed(labels) { index, label ->
            val current = index == currentStep
            val done = index < groups.size && isDone(index)
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 104.dp)
                        .background(if (current) colors.surfaceVariant else Color.Transparent)
                        .clickable { onStepSelected(index) }
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector =
                        when {
                            done -> Icons.Rounded.CheckCircle
                            current -> Icons.Rounded.RadioButtonChecked
                            else -> Icons.Rounded.RadioButtonUnchecked
                        },
                    contentDescription = null,
                    tint =
                        when {
                            done -> KioskColors.success
                            current -> colors.primary
                            else -> colors.outline
                        },
                    modifier = Modifier.size(40.dp),
                )
                Spacer(Modifier.width(14.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = if (current) FontWeight.Black else FontWeight.Medium),
                    color = if (current || done) colors.onSurface else colors.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** One modifier group: "Selecciona …", its rule and the option tiles. */
@Composable
private fun GroupStep(
    group: KioskModifierGroupDto,
    selectedOptions: List<KioskModifierOptionDto>,
    showError: Boolean,
    onOptionToggled: (KioskModifierOptionDto) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val columns = if (LocalKioskCanvas.current.isLandscape) LANDSCAPE_OPTION_COLUMNS else OPTION_COLUMNS
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(start = 28.dp, end = 28.dp, top = 32.dp, bottom = 32.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(key = "title", span = { GridItemSpan(maxLineSpan) }) {
            Column(modifier = Modifier.padding(bottom = 8.dp)) {
                Text(
                    text = stepTitle(group.name),
                    style = MaterialTheme.typography.headlineLarge,
                    color = colors.onBackground,
                )
                Spacer(Modifier.height(6.dp))
                Text(text = requirementText(group), style = MaterialTheme.typography.titleSmall, color = colors.onSurfaceVariant)
                AnimatedVisibility(visible = showError) {
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
            }
        }
        items(group.options, key = { it.id }) { option ->
            OptionTile(
                option = option,
                isSelected = selectedOptions.any { it.id == option.id },
                groupName = group.name,
                onClick = { onOptionToggled(option) },
            )
        }
    }
}

/** "Selecciona bebida"; group names already phrased as a request ("Elige tu bebida") are kept. */
@Composable
private fun stepTitle(groupName: String): String {
    val lower = groupName.trim().lowercase()
    return if (ImperativePrefixes.any { lower.startsWith(it) }) {
        groupName
    } else {
        stringResource(R.string.customizer_select_group, groupName.trim().replaceFirstChar { it.lowercase() })
    }
}

private val ImperativePrefixes = listOf("elige", "selecciona", "escoge", "agrega", "choose", "select", "pick", "add")

@Composable
private fun requirementText(group: KioskModifierGroupDto): String {
    val required = group.min > 0
    return when {
        required && group.min == group.max -> stringResource(R.string.customizer_required_choose, group.min)
        required -> stringResource(R.string.customizer_required_min, group.min)
        group.max == 1 -> stringResource(R.string.customizer_optional_one)
        else -> stringResource(R.string.customizer_optional_up_to, group.max)
    }
}

/** Square option tile: illustration, name and extra cost; a check marks the chosen ones. */
@Composable
private fun OptionTile(
    option: KioskModifierOptionDto,
    isSelected: Boolean,
    groupName: String,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val extra = remember(option.extraPrice) { Money.fromString(option.extraPrice) }
    val glyph = remember(option.name, groupName) { foodGlyphFor(option.name, groupName) }
    KioskCard(
        modifier = Modifier.fillMaxWidth().graphicsLayer { alpha = if (option.soldOut) SOLD_OUT_ALPHA else 1f },
        selected = isSelected,
        onClick = if (option.soldOut) null else onClick,
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            KioskImage(
                url = null,
                contentDescription = null,
                placeholderIcon = glyph,
                placeholderIconSize = 80.dp,
                grayscale = option.soldOut,
                modifier = Modifier.fillMaxWidth().aspectRatio(1.25f).clip(RoundedCornerShape(8.dp)),
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = option.name,
                style = MaterialTheme.typography.titleSmall,
                color = colors.onSurface,
                textAlign = TextAlign.Center,
                minLines = 2,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text =
                    when {
                        option.soldOut -> stringResource(R.string.menu_sold_out)
                        extra.amount > BigDecimal.ZERO -> stringResource(R.string.customizer_extra_price, extra.toDisplayString())
                        else -> " "
                    },
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Black),
                color = if (option.soldOut) colors.error else colors.onSurfaceVariant,
            )
        }
        if (isSelected) {
            Icon(
                imageVector = Icons.Rounded.CheckCircle,
                contentDescription = null,
                tint = colors.secondary,
                modifier = Modifier.align(Alignment.TopEnd).padding(10.dp).size(48.dp).background(colors.surface, CircleShape),
            )
        }
    }
}

/** "Revisar orden": what was chosen, the kitchen note and the quantity. */
@Composable
private fun ReviewStep(
    item: KioskItemDto,
    uiState: CustomizerUiState,
    onNoteChanged: (String) -> Unit,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val chosen = remember(uiState.selectedOptions, item) { item.modifierGroups.flatMap { uiState.selectedOptions[it.id].orEmpty() } }
    val secondaryTotal = remember(uiState.totalPrice, uiState.currency) { uiState.totalPrice.secondaryText(uiState.currency) }
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 32.dp, vertical = 32.dp),
    ) {
        Text(text = stringResource(R.string.customizer_step_review), style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(24.dp))
        KioskCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(28.dp)) {
                Text(text = item.name, style = MaterialTheme.typography.titleLarge, color = colors.onSurface)
                if (!item.description.isNullOrBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(text = item.description, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                }
                if (chosen.isNotEmpty()) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp), thickness = 2.dp, color = colors.outlineVariant)
                    chosen.forEach { option ->
                        val extra = Money.fromString(option.extraPrice)
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Rounded.CheckCircle,
                                contentDescription = null,
                                tint = KioskColors.success,
                                modifier = Modifier.size(32.dp),
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = option.name,
                                style = MaterialTheme.typography.bodyLarge,
                                color = colors.onSurface,
                                modifier = Modifier.weight(1f),
                            )
                            if (extra.amount > BigDecimal.ZERO) {
                                Text(
                                    text = stringResource(R.string.customizer_extra_price, extra.toDisplayString()),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = colors.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(28.dp))
        Text(text = stringResource(R.string.customizer_kitchen_note), style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = uiState.note,
            onValueChange = onNoteChanged,
            placeholder = { Text(stringResource(R.string.customizer_note_placeholder), style = MaterialTheme.typography.bodyLarge) },
            textStyle = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.fillMaxWidth().heightIn(min = 96.dp),
            shape = MaterialTheme.shapes.small,
            singleLine = true,
        )
        Spacer(Modifier.height(32.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.customizer_quantity),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
            )
            QuantityStepper(
                quantity = uiState.quantity,
                onDecrement = onDecrement,
                onIncrement = onIncrement,
                decrementEnabled = uiState.quantity > 1,
            )
        }
        Spacer(Modifier.height(24.dp))
        HorizontalDivider(thickness = 2.dp, color = colors.outlineVariant)
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.review_total),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.weight(1f),
            )
            Column(horizontalAlignment = Alignment.End) {
                Text(text = uiState.totalPrice.toDisplayString(), style = MaterialTheme.typography.headlineLarge)
                if (secondaryTotal.isNotBlank()) {
                    Text(text = secondaryTotal, style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
                }
            }
        }
    }
}

/** "Atrás" and "Cancelar" on the left; "Siguiente" or "Agregar a mi orden" on the right. */
@Composable
private fun WizardBottomBar(
    isReview: Boolean,
    added: Boolean,
    totalText: String,
    onBack: () -> Unit,
    onCancel: () -> Unit,
    onNext: () -> Unit,
    onAdd: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Box(modifier = Modifier.fillMaxWidth().topHairline(colors.outlineVariant).padding(horizontal = 28.dp, vertical = 20.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            KioskButton(
                text = stringResource(R.string.customizer_back),
                onClick = onBack,
                style = KioskButtonStyle.Secondary,
                height = 104.dp,
                modifier = Modifier.weight(0.7f),
            )
            KioskButton(
                text = stringResource(R.string.btn_cancel),
                onClick = onCancel,
                style = KioskButtonStyle.Secondary,
                height = 104.dp,
                modifier = Modifier.weight(0.7f),
            )
            KioskButton(
                text =
                    when {
                        added -> stringResource(R.string.customizer_added)
                        isReview -> stringResource(R.string.customizer_add_to_order, totalText)
                        else -> stringResource(R.string.customizer_next)
                    },
                onClick = if (isReview) onAdd else onNext,
                icon = if (added) Icons.Rounded.CheckCircle else null,
                height = 104.dp,
                modifier = Modifier.weight(1.4f),
            )
        }
    }
}
