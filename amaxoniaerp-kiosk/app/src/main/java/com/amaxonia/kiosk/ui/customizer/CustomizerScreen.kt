package com.amaxonia.kiosk.ui.customizer

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckBox
import androidx.compose.material.icons.rounded.CheckBoxOutlineBlank
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.RadioButtonChecked
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import com.amaxonia.kiosk.ui.components.foodGlyphFor
import com.amaxonia.kiosk.ui.components.secondaryText
import com.amaxonia.kiosk.ui.components.sentenceCase
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
private const val UNREACHABLE_STEP_ALPHA = 0.5f
private const val CHANGE_BORDER_ALPHA = 0.35f
private val StepsPanelWidth = 270.dp
private val WizardButtonHeight = 104.dp
private val StepMarkerSize = 44.dp
private val ChoiceTileHeight = 180.dp

/**
 * Product customizer as a step-by-step wizard, the way fast-food self-order kiosks build a combo:
 * one modifier group per step ("Selecciona tu bebida", "Selecciona extra"…) with the steps listed on
 * the left, and a final "Revisar orden" step with quantity, kitchen note and "Agregar a mi orden".
 * Single-choice steps advance on their own once an option is picked. Any step already reached can be
 * reopened from the step list or from the "Cambiar" links of the review step; choices are kept.
 */
@Composable
fun CustomizerScreen(
    viewModel: ProductCustomizerViewModel,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    optionImageUrl: (KioskModifierOptionDto) -> String? = { null },
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val item = uiState.item
    val groups = item.modifierGroups
    val step = uiState.currentStep
    var added by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(added) {
        if (added) {
            delay(ADDED_FEEDBACK_MS)
            onDismiss()
        }
    }

    val onAdd: () -> Unit = {
        if (!added) viewModel.addToCart(onSuccess = { added = true })
    }

    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize()) {
            ProductHeader(
                item = item,
                unitPrice = uiState.unitPrice,
                stepNumber = step + 1,
                stepCount = uiState.stepCount,
            )
            Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                StepsPanel(
                    uiState = uiState,
                    onStepSelected = { target -> viewModel.goToStep(target) },
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
                            optionImageUrl = optionImageUrl,
                            showError = uiState.showStepError && !uiState.isGroupSatisfied(group),
                            onOptionToggled = { option ->
                                val wasSelected = uiState.selectedOptions[group.id].orEmpty().any { it.id == option.id }
                                viewModel.toggleOption(group, option)
                                // Single choice: picking an option moves on, as on the reference kiosk.
                                if (group.max == 1 && !wasSelected && !option.soldOut) {
                                    scope.launch {
                                        delay(AUTO_ADVANCE_MS)
                                        viewModel.advanceFrom(current)
                                    }
                                }
                            },
                        )
                    } else {
                        ReviewStep(
                            item = item,
                            uiState = uiState,
                            onEditStep = { index -> viewModel.goToStep(index) },
                            onNoteChanged = viewModel::onNoteChanged,
                            onDecrement = viewModel::decrementQuantity,
                            onIncrement = viewModel::incrementQuantity,
                        )
                    }
                }
            }
            WizardBottomBar(
                isFirstStep = step == 0,
                isReview = uiState.isOnReviewStep,
                added = added,
                totalText = uiState.totalPrice.toDisplayString(),
                onBack = { viewModel.previousStep() },
                onCancel = onDismiss,
                onNext = { viewModel.nextStep() },
                onAdd = onAdd,
            )
        }
    }
}

/** Product photo, name and price across the top of every step, with "Paso 2 de 4" and a progress bar. */
@Composable
private fun ProductHeader(
    item: KioskItemDto,
    unitPrice: Money,
    stepNumber: Int,
    stepCount: Int,
) {
    val colors = MaterialTheme.colorScheme
    val glyph = remember(item.name) { foodGlyphFor(item.name) }
    val progress by animateFloatAsState(
        targetValue = stepNumber.toFloat() / stepCount.coerceAtLeast(1),
        animationSpec = tween(STEP_SWAP_MS),
        label = "step_progress",
    )
    Surface(modifier = Modifier.fillMaxWidth(), color = colors.surface, shadowElevation = 12.dp) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                KioskImage(
                    url = item.imageUrl,
                    contentDescription = item.name,
                    contentScale = ContentScale.Fit,
                    placeholderIcon = glyph,
                    placeholderIconSize = 72.dp,
                    modifier =
                        Modifier
                            .size(140.dp)
                            .clip(MaterialTheme.shapes.medium)
                            .background(colors.background)
                            .padding(10.dp),
                )
                Spacer(Modifier.width(24.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.headlineSmall,
                        color = colors.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = unitPrice.toDisplayString(),
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black),
                            color = colors.primary,
                        )
                        Spacer(Modifier.width(16.dp))
                        Text(
                            text = stringResource(R.string.customizer_step_progress, stepNumber, stepCount),
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.onSecondaryContainer,
                            maxLines = 1,
                            modifier =
                                Modifier
                                    .background(colors.secondaryContainer, RoundedCornerShape(percent = 50))
                                    .padding(horizontal = 16.dp, vertical = 6.dp),
                        )
                    }
                }
            }
            // Brand progress bar along the header's bottom edge.
            Box(modifier = Modifier.fillMaxWidth().height(8.dp).background(colors.surfaceVariant)) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth(progress)
                            .fillMaxHeight()
                            .background(KioskColors.heroBrush, RoundedCornerShape(topEndPercent = 50, bottomEndPercent = 50)),
                )
            }
        }
    }
}

/**
 * Left list of steps: done (check), current (filled dot) and pending (empty dot), plus "Revisar orden".
 * Done steps show what was chosen; every reachable step can be tapped to go back and fix it.
 */
@Composable
private fun StepsPanel(
    uiState: CustomizerUiState,
    onStepSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val groups = uiState.item.modifierGroups
    val reviewLabel = stringResource(R.string.customizer_step_review)
    val labels = remember(groups, reviewLabel) { groups.map { sentenceCase(it.name) } + reviewLabel }
    LazyColumn(
        modifier = modifier.background(colors.surface),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        itemsIndexed(labels) { index, label ->
            val current = index == uiState.currentStep
            val done = !current && uiState.isStepDone(index)
            val reachable = uiState.canGoToStep(index)
            val summary =
                groups
                    .getOrNull(index)
                    ?.let { group -> uiState.selectedOptions[group.id].orEmpty().joinToString(" · ") { it.name } }
                    .orEmpty()
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 96.dp)
                        .clip(MaterialTheme.shapes.medium)
                        .background(if (current) colors.primaryContainer else Color.Transparent)
                        .clickable(enabled = reachable && !current) { onStepSelected(index) }
                        .graphicsLayer { alpha = if (reachable || current) 1f else UNREACHABLE_STEP_ALPHA }
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StepMarker(number = index + 1, done = done, current = current)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        text = label,
                        style =
                            MaterialTheme.typography.titleSmall.copy(
                                fontWeight = if (current) FontWeight.Black else FontWeight.SemiBold,
                            ),
                        color =
                            when {
                                current -> colors.primary
                                done -> colors.onSurface
                                else -> colors.onSurfaceVariant
                            },
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (done && summary.isNotEmpty()) {
                        Text(
                            text = summary,
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

/** Step number in a circle: green check when done, filled indigo when current, outlined otherwise. */
@Composable
private fun StepMarker(
    number: Int,
    done: Boolean,
    current: Boolean,
) {
    val colors = MaterialTheme.colorScheme
    val background =
        when {
            done -> KioskColors.success
            current -> colors.primary
            else -> colors.surface
        }
    Box(
        modifier =
            Modifier
                .size(StepMarkerSize)
                .background(background, CircleShape)
                .then(if (!done && !current) Modifier.border(2.dp, colors.outline, CircleShape) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        if (done) {
            Icon(Icons.Rounded.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp))
        } else {
            Text(
                text = number.toString(),
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Black),
                color = if (current) colors.onPrimary else colors.onSurfaceVariant,
            )
        }
    }
}

/** One modifier group: "Selecciona …", its rule and the option tiles. */
@Composable
private fun GroupStep(
    group: KioskModifierGroupDto,
    selectedOptions: List<KioskModifierOptionDto>,
    optionImageUrl: (KioskModifierOptionDto) -> String?,
    showError: Boolean,
    onOptionToggled: (KioskModifierOptionDto) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val columns = if (LocalKioskCanvas.current.isLandscape) LANDSCAPE_OPTION_COLUMNS else OPTION_COLUMNS
    val imageUrls = remember(group, optionImageUrl) { group.options.associate { it.id to optionImageUrl(it) } }
    // Photo tiles only when the group has photos; otherwise clean text tiles (no repeated glyphs).
    val withPhotos = imageUrls.values.any { it != null }
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        horizontalArrangement = Arrangement.spacedBy(18.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
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
                Spacer(Modifier.height(12.dp))
                Text(
                    text = requirementText(group),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (group.min > 0) colors.onSecondaryContainer else colors.onSurfaceVariant,
                    modifier =
                        Modifier
                            .background(
                                if (group.min > 0) colors.secondaryContainer else colors.surfaceVariant,
                                RoundedCornerShape(percent = 50),
                            )
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                )
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
            val selected = selectedOptions.any { it.id == option.id }
            if (withPhotos) {
                OptionTile(
                    option = option,
                    imageUrl = imageUrls[option.id],
                    isSelected = selected,
                    groupName = group.name,
                    onClick = { onOptionToggled(option) },
                )
            } else {
                ChoiceTile(
                    option = option,
                    isSelected = selected,
                    multiple = group.max != 1,
                    onClick = { onOptionToggled(option) },
                )
            }
        }
    }
}

/**
 * "Selecciona bebida"; group names already phrased as a request ("Elige tu bebida") are kept.
 * All-caps ERP names are lowered first ("ACOMPAÑANTE" → "Selecciona acompañante").
 */
@Composable
private fun stepTitle(groupName: String): String {
    val name = sentenceCase(groupName)
    val lower = name.lowercase()
    return if (ImperativePrefixes.any { lower.startsWith(it) }) {
        name
    } else {
        stringResource(R.string.customizer_select_group, name.replaceFirstChar { it.lowercase() })
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

/** Photo option tile: product photo, name and extra cost; a check marks the chosen ones. */
@Composable
private fun OptionTile(
    option: KioskModifierOptionDto,
    imageUrl: String?,
    isSelected: Boolean,
    groupName: String,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val glyph = remember(option.name, groupName) { foodGlyphFor(option.name, groupName) }
    KioskCard(
        modifier = Modifier.fillMaxWidth().graphicsLayer { alpha = if (option.soldOut) SOLD_OUT_ALPHA else 1f },
        selected = isSelected,
        onClick = if (option.soldOut) null else onClick,
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            KioskImage(
                url = imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                placeholderIcon = glyph,
                placeholderIconSize = 72.dp,
                grayscale = option.soldOut,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(MaterialTheme.shapes.small)
                        .background(colors.background)
                        .padding(12.dp),
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
            OptionPrice(option)
        }
        if (isSelected) SelectedCheck()
    }
}

/**
 * Text-first option tile for groups without photos (meat doneness, "sin tomate"…): the name large
 * and centered with a radio/check indicator, so the grid reads cleanly instead of repeating glyphs.
 */
@Composable
private fun ChoiceTile(
    option: KioskModifierOptionDto,
    isSelected: Boolean,
    multiple: Boolean,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    KioskCard(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = ChoiceTileHeight)
                .graphicsLayer { alpha = if (option.soldOut) SOLD_OUT_ALPHA else 1f },
        selected = isSelected,
        onClick = if (option.soldOut) null else onClick,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().heightIn(min = ChoiceTileHeight).padding(horizontal = 16.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector =
                    when {
                        multiple && isSelected -> Icons.Rounded.CheckBox
                        multiple -> Icons.Rounded.CheckBoxOutlineBlank
                        isSelected -> Icons.Rounded.RadioButtonChecked
                        else -> Icons.Rounded.RadioButtonUnchecked
                    },
                contentDescription = null,
                tint = if (isSelected) colors.secondary else colors.outline,
                modifier = Modifier.size(40.dp),
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = option.name,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold),
                color = if (isSelected) colors.primary else colors.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            OptionPrice(option)
        }
    }
}

/** "+$0.47", "Agotado" or nothing, under the option name. */
@Composable
private fun OptionPrice(option: KioskModifierOptionDto) {
    val colors = MaterialTheme.colorScheme
    val extra = remember(option.extraPrice) { Money.fromString(option.extraPrice) }
    val text =
        when {
            option.soldOut -> stringResource(R.string.menu_sold_out)
            extra.amount > BigDecimal.ZERO -> stringResource(R.string.customizer_extra_price, extra.toDisplayString())
            else -> null
        }
    if (text != null) {
        Spacer(Modifier.height(4.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Black),
            color = if (option.soldOut) colors.error else colors.primary,
        )
    }
}

@Composable
private fun BoxScope.SelectedCheck() {
    val colors = MaterialTheme.colorScheme
    Icon(
        imageVector = Icons.Rounded.CheckCircle,
        contentDescription = null,
        tint = colors.secondary,
        modifier = Modifier.align(Alignment.TopEnd).padding(10.dp).size(48.dp).background(colors.surface, CircleShape),
    )
}

/** "Revisar orden": each chosen group with a "Cambiar" link, the kitchen note and the quantity. */
@Composable
private fun ReviewStep(
    item: KioskItemDto,
    uiState: CustomizerUiState,
    onEditStep: (Int) -> Unit,
    onNoteChanged: (String) -> Unit,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val secondaryTotal = remember(uiState.totalPrice, uiState.currency) { uiState.totalPrice.secondaryText(uiState.currency) }
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 32.dp, vertical = 32.dp),
    ) {
        Text(text = stringResource(R.string.customizer_step_review), style = MaterialTheme.typography.headlineLarge)
        if (!item.description.isNullOrBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(text = item.description, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
        }
        if (item.modifierGroups.isNotEmpty()) {
            Spacer(Modifier.height(24.dp))
            Text(
                text = stringResource(R.string.customizer_your_choices),
                style = MaterialTheme.typography.titleSmall,
                color = colors.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            KioskCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(vertical = 8.dp)) {
                    item.modifierGroups.forEachIndexed { index, group ->
                        if (index > 0) {
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 24.dp),
                                thickness = 1.dp,
                                color = colors.outlineVariant,
                            )
                        }
                        ChoiceSummaryRow(
                            group = group,
                            chosen = uiState.selectedOptions[group.id].orEmpty(),
                            onChange = { onEditStep(index) },
                        )
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
            shape = MaterialTheme.shapes.medium,
            singleLine = true,
            colors =
                OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = colors.surface,
                    unfocusedContainerColor = colors.surface,
                    unfocusedBorderColor = colors.outlineVariant,
                ),
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

/** One group on the review step: its name, what was chosen (with extra cost) and "Cambiar". */
@Composable
private fun ChoiceSummaryRow(
    group: KioskModifierGroupDto,
    chosen: List<KioskModifierOptionDto>,
    onChange: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val extras = remember(chosen) { chosen.fold(Money.ZERO) { acc, opt -> acc + Money.fromString(opt.extraPrice) } }
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onChange)
                .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = sentenceCase(group.name), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, maxLines = 1)
            Spacer(Modifier.height(2.dp))
            Text(
                text = chosen.joinToString(" · ") { it.name }.ifEmpty { stringResource(R.string.customizer_no_selection) },
                style = MaterialTheme.typography.titleMedium,
                color = if (chosen.isEmpty()) colors.onSurfaceVariant else colors.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (extras.amount > BigDecimal.ZERO) {
                Text(
                    text = stringResource(R.string.customizer_extra_price, extras.toDisplayString()),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.primary,
                )
            }
        }
        Spacer(Modifier.width(16.dp))
        Row(
            modifier =
                Modifier
                    .heightIn(min = 72.dp)
                    .border(2.dp, colors.primary.copy(alpha = CHANGE_BORDER_ALPHA), RoundedCornerShape(percent = 50))
                    .padding(horizontal = 22.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Edit, contentDescription = null, tint = colors.primary, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(8.dp))
            Text(text = stringResource(R.string.customizer_change), style = MaterialTheme.typography.titleSmall, color = colors.primary)
        }
    }
}

/**
 * Fixed action bar; buttons never move between steps so the customer always finds them in the same
 * place: "Cancelar" (left, quiet), "Atrás" (disabled on the first step) and the primary
 * "Siguiente" / "Agregar a mi orden" (right, widest).
 */
@Composable
private fun WizardBottomBar(
    isFirstStep: Boolean,
    isReview: Boolean,
    added: Boolean,
    totalText: String,
    onBack: () -> Unit,
    onCancel: () -> Unit,
    onNext: () -> Unit,
    onAdd: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Surface(modifier = Modifier.fillMaxWidth(), color = colors.surface, shadowElevation = 24.dp) {
        Row(
            modifier = Modifier.padding(horizontal = 28.dp, vertical = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            KioskButton(
                text = stringResource(R.string.btn_cancel),
                onClick = onCancel,
                style = KioskButtonStyle.Ghost,
                icon = Icons.Rounded.Close,
                contentColor = colors.onSurfaceVariant,
                height = WizardButtonHeight,
                modifier = Modifier.weight(0.8f),
            )
            KioskButton(
                text = stringResource(R.string.customizer_back),
                onClick = onBack,
                style = KioskButtonStyle.Secondary,
                icon = Icons.AutoMirrored.Rounded.ArrowBack,
                enabled = !isFirstStep && !added,
                height = WizardButtonHeight,
                modifier = Modifier.weight(0.9f),
            )
            val showArrow = !isReview && !added
            KioskButton(
                text =
                    when {
                        added -> stringResource(R.string.customizer_added)
                        isReview -> stringResource(R.string.customizer_add_to_order, totalText)
                        else -> stringResource(R.string.customizer_next)
                    },
                onClick = if (isReview) onAdd else onNext,
                icon = if (added) Icons.Rounded.CheckCircle else null,
                trailing =
                    if (showArrow) {
                        { Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null, modifier = Modifier.size(40.dp)) }
                    } else {
                        null
                    },
                height = WizardButtonHeight,
                modifier = Modifier.weight(1.5f),
            )
        }
    }
}
