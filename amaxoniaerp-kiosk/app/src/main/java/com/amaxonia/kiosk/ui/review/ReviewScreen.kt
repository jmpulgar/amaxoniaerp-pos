package com.amaxonia.kiosk.ui.review

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.ShoppingBag
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.amaxonia.kiosk.R
import com.amaxonia.kiosk.core.money.Money
import com.amaxonia.kiosk.core.network.KioskCurrencyConfig
import com.amaxonia.kiosk.domain.cart.CartLine
import com.amaxonia.kiosk.ui.components.KioskButton
import com.amaxonia.kiosk.ui.components.KioskButtonStyle
import com.amaxonia.kiosk.ui.components.KioskConfirmDialog
import com.amaxonia.kiosk.ui.components.KioskIconButton
import com.amaxonia.kiosk.ui.components.KioskImage
import com.amaxonia.kiosk.ui.components.bottomHairline
import com.amaxonia.kiosk.ui.components.foodGlyphFor
import com.amaxonia.kiosk.ui.components.kioskPressable
import com.amaxonia.kiosk.ui.components.secondaryText
import com.amaxonia.kiosk.ui.components.topHairline
import com.amaxonia.kiosk.ui.diningmode.MODE_DINE_IN
import com.amaxonia.kiosk.ui.diningmode.MODE_TAKEAWAY
import com.amaxonia.kiosk.ui.theme.LocalHighContrast
import com.amaxonia.kiosk.ui.theme.LocalKioskCanvas

private val LineImageSize = 96.dp
private val LineMinHeight = 128.dp
private val SidePanelWidth = 640.dp
private val StepperButtonSize = 64.dp
private val HeaderButtonSize = 72.dp
private val SegmentHeight = 72.dp
private val ActionButtonHeight = 104.dp

@Composable
fun ReviewScreen(
    viewModel: ReviewViewModel,
    onContinueShopping: () -> Unit,
    onProceedToCheckout: () -> Unit,
    onCancelOrder: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showCancelDialog by remember { mutableStateOf(false) }

    if (showCancelDialog) {
        KioskConfirmDialog(
            title = stringResource(R.string.cancel_order_title),
            message = stringResource(R.string.cancel_order_message),
            confirmText = stringResource(R.string.cancel_order_confirm),
            dismissText = stringResource(R.string.cancel_order_keep),
            icon = Icons.Rounded.DeleteOutline,
            onConfirm = {
                showCancelDialog = false
                viewModel.clearCart()
                onCancelOrder()
            },
            onDismiss = { showCancelDialog = false },
        )
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            ReviewHeader(
                uiState = uiState,
                onBack = onContinueShopping,
                onClear = { showCancelDialog = true },
                onDiningModeSelected = viewModel::setDiningMode,
            )

            if (uiState.isEmpty) {
                EmptyCartView(onExploreMenu = onContinueShopping, modifier = Modifier.weight(1f))
            } else if (LocalKioskCanvas.current.isLandscape) {
                // Landscape: lines on the left, totals and actions in a side panel on the right.
                Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    CartLinesList(uiState = uiState, viewModel = viewModel, modifier = Modifier.weight(1f).fillMaxHeight())
                    // A summary card anchored low on the right, where the hand already is.
                    Box(
                        modifier = Modifier.width(SidePanelWidth).fillMaxHeight().padding(top = 16.dp, end = 32.dp, bottom = 32.dp),
                        contentAlignment = Alignment.BottomCenter,
                    ) {
                        ReviewBottomBar(
                            uiState = uiState,
                            onContinueShopping = onContinueShopping,
                            onCheckout = onProceedToCheckout,
                            sidePanel = true,
                        )
                    }
                }
            } else {
                CartLinesList(uiState = uiState, viewModel = viewModel, modifier = Modifier.weight(1f).fillMaxWidth())
                ReviewBottomBar(
                    uiState = uiState,
                    onContinueShopping = onContinueShopping,
                    onCheckout = onProceedToCheckout,
                )
            }
        }
    }
}

/**
 * Compact header: back arrow, "Revisa tu orden" with the item count, "Vaciar", and a slim
 * "Comer aquí / Para llevar" switch underneath.
 */
@Composable
private fun ReviewHeader(
    uiState: ReviewUiState,
    onBack: () -> Unit,
    onClear: () -> Unit,
    onDiningModeSelected: (String) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth().bottomHairline(colors.outlineVariant),
        color = colors.surface,
        border = if (LocalHighContrast.current) BorderStroke(2.dp, colors.onSurface) else null,
    ) {
        Column(modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                KioskIconButton(
                    icon = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = stringResource(R.string.btn_back),
                    onClick = onBack,
                    size = HeaderButtonSize,
                )
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.review_title),
                        style = MaterialTheme.typography.headlineSmall,
                        color = colors.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (!uiState.isEmpty) {
                        Text(
                            text = pluralStringResource(R.plurals.review_item_count, uiState.totalItemCount, uiState.totalItemCount),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant,
                        )
                    }
                }
                if (!uiState.isEmpty) {
                    KioskButton(
                        text = stringResource(R.string.review_clear_cart),
                        onClick = onClear,
                        style = KioskButtonStyle.Ghost,
                        icon = Icons.Rounded.DeleteOutline,
                        contentColor = colors.error,
                        height = HeaderButtonSize,
                        textStyle = MaterialTheme.typography.titleSmall,
                    )
                }
            }
            if (!uiState.isEmpty) {
                Spacer(Modifier.height(16.dp))
                SlimSegmentedControl(
                    options =
                        listOf(
                            stringResource(R.string.dining_mode_dine_in_title) to Icons.Rounded.Restaurant,
                            stringResource(R.string.dining_mode_takeaway_title) to Icons.Rounded.ShoppingBag,
                        ),
                    selectedIndex = if (uiState.diningMode == MODE_TAKEAWAY) 1 else 0,
                    onSelect = { index -> onDiningModeSelected(if (index == 1) MODE_TAKEAWAY else MODE_DINE_IN) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/** A slim pill switch (72 dp) for the dining mode; the selected half is filled in Flow indigo. */
@Composable
private fun SlimSegmentedControl(
    options: List<Pair<String, ImageVector>>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val highContrast = LocalHighContrast.current
    Row(
        modifier =
            modifier
                .clip(RoundedCornerShape(percent = 50))
                .background(colors.surfaceVariant)
                .then(if (highContrast) Modifier.border(2.dp, colors.onSurface, RoundedCornerShape(percent = 50)) else Modifier)
                .padding(5.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        options.forEachIndexed { index, (label, icon) ->
            val selected = index == selectedIndex
            val container by animateColorAsState(if (selected) colors.primary else Color.Transparent, label = "segment_bg")
            val tint by animateColorAsState(if (selected) colors.onPrimary else colors.onSurfaceVariant, label = "segment_fg")
            Row(
                modifier =
                    Modifier
                        .weight(1f)
                        .height(SegmentHeight)
                        .clip(RoundedCornerShape(percent = 50))
                        .background(container)
                        .kioskPressable { onSelect(index) },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(28.dp))
                Spacer(Modifier.width(10.dp))
                Text(text = label, style = MaterialTheme.typography.titleSmall, color = tint, maxLines = 1)
            }
        }
    }
}

@Composable
private fun CartLinesList(
    uiState: ReviewUiState,
    viewModel: ReviewViewModel,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    LazyColumn(
        modifier = modifier.background(colors.surface),
        contentPadding = PaddingValues(vertical = 4.dp),
    ) {
        itemsIndexed(items = uiState.lines, key = { _, line -> line.id }) { index, line ->
            Column(modifier = Modifier.animateItem()) {
                if (index > 0) {
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 24.dp), thickness = 1.dp, color = colors.outlineVariant)
                }
                CartLineRow(
                    line = line,
                    currency = uiState.currency,
                    onIncrement = { viewModel.incrementQuantity(line.id) },
                    onDecrement = { viewModel.decrementQuantity(line.id) },
                )
            }
        }
    }
}

/**
 * One compact order line (~130 dp): photo, name with its choices on one grey line (and the kitchen
 * note), and on the right the line total above a pill stepper whose minus becomes a trash can at 1.
 */
@Composable
private fun CartLineRow(
    line: CartLine,
    currency: KioskCurrencyConfig,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val lineTotal = line.lineTotal
    val secondaryTotal = remember(lineTotal, currency) { lineTotal.secondaryText(currency) }
    val glyph = remember(line.item.name) { foodGlyphFor(line.item.name) }
    val choices = remember(line.selectedModifiers) { line.selectedModifiers.joinToString(" · ") { it.optionName } }

    Row(
        modifier = modifier.fillMaxWidth().heightIn(min = LineMinHeight).padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        KioskImage(
            url = line.item.imageUrl,
            contentDescription = line.item.name,
            placeholderIcon = glyph,
            placeholderIconSize = 48.dp,
            modifier = Modifier.size(LineImageSize).clip(RoundedCornerShape(16.dp)).background(colors.surfaceVariant),
        )
        Spacer(modifier = Modifier.width(20.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = line.item.name,
                style = MaterialTheme.typography.titleMedium,
                color = colors.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (choices.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = choices,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (!line.note.isNullOrBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = stringResource(R.string.review_note, line.note),
                    style = MaterialTheme.typography.bodySmall.copy(fontStyle = FontStyle.Italic),
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (line.quantity > 1) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = stringResource(R.string.review_unit_price, line.unitPrice.toDisplayString()),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )
            }
        }
        Spacer(modifier = Modifier.width(20.dp))
        Column(horizontalAlignment = Alignment.End) {
            AnimatedContent(targetState = lineTotal.toDisplayString(), label = "line_total") { value ->
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black),
                    color = colors.primary,
                )
            }
            if (secondaryTotal.isNotBlank()) {
                Text(text = secondaryTotal, style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
            }
            Spacer(Modifier.height(10.dp))
            PillStepper(quantity = line.quantity, onDecrement = onDecrement, onIncrement = onIncrement)
        }
    }
}

/** Compact [− 1 +] pill; at quantity 1 the minus is a red trash can that removes the line. */
@Composable
private fun PillStepper(
    quantity: Int,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val removes = quantity <= 1
    Row(
        modifier =
            Modifier
                .clip(RoundedCornerShape(percent = 50))
                .background(colors.surfaceVariant)
                .padding(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        KioskIconButton(
            icon = if (removes) Icons.Rounded.DeleteOutline else Icons.Rounded.Remove,
            contentDescription = stringResource(if (removes) R.string.cd_remove else R.string.cd_decrease),
            onClick = onDecrement,
            size = StepperButtonSize,
            containerColor = colors.surface,
            contentColor = if (removes) colors.error else colors.onSurface,
        )
        AnimatedContent(targetState = quantity, label = "line_qty") { value ->
            Text(
                text = value.toString(),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black),
                color = colors.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(min = 56.dp),
            )
        }
        KioskIconButton(
            icon = Icons.Rounded.Add,
            contentDescription = stringResource(R.string.cd_increase),
            onClick = onIncrement,
            size = StepperButtonSize,
            containerColor = colors.primary,
            contentColor = colors.onPrimary,
        )
    }
}

@Composable
private fun EmptyCartView(
    onExploreMenu: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Box(modifier = modifier.fillMaxSize().padding(48.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier.size(200.dp).background(colors.secondaryContainer, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.ShoppingBag, contentDescription = null, tint = colors.primary, modifier = Modifier.size(110.dp))
            }
            Spacer(modifier = Modifier.height(32.dp))
            Text(
                text = stringResource(R.string.review_empty_cart),
                style = MaterialTheme.typography.headlineLarge,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.review_empty_hint),
                style = MaterialTheme.typography.bodyLarge,
                color = colors.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(40.dp))
            KioskButton(text = stringResource(R.string.review_empty_cta), onClick = onExploreMenu)
        }
    }
}

@Composable
private fun ReviewBottomBar(
    uiState: ReviewUiState,
    onContinueShopping: () -> Unit,
    onCheckout: () -> Unit,
    sidePanel: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val total = uiState.estimatedTotal
    val secondaryTotal = remember(total, uiState.currency) { total.secondaryText(uiState.currency) }

    Surface(
        modifier = if (sidePanel) modifier.fillMaxWidth() else modifier.fillMaxWidth().topHairline(colors.outlineVariant),
        color = colors.surface,
        shape = if (sidePanel) MaterialTheme.shapes.large else RoundedCornerShape(0.dp),
        border =
            when {
                LocalHighContrast.current -> BorderStroke(3.dp, colors.onSurface)
                sidePanel -> BorderStroke(2.dp, colors.outlineVariant)
                else -> null
            },
    ) {
        Column(modifier = Modifier.padding(horizontal = 32.dp, vertical = 20.dp)) {
            // Subtotal and tax sit in a soft grey block; the total stands out beneath it.
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .background(colors.surfaceVariant, MaterialTheme.shapes.medium)
                        .padding(horizontal = 20.dp, vertical = 10.dp),
            ) {
                TotalsRow(label = stringResource(R.string.review_subtotal), amount = uiState.subtotal)
                TotalsRow(
                    label = stringResource(if (uiState.usesItbms) R.string.review_tax_itbms else R.string.review_tax_iva),
                    amount = uiState.estimatedTax,
                )
            }
            Row(modifier = Modifier.padding(top = 14.dp, bottom = 18.dp, start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.review_total),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.weight(1f),
                )
                Column(horizontalAlignment = Alignment.End) {
                    AnimatedContent(targetState = total.toDisplayString(), label = "review_total") { value ->
                        Text(text = value, style = MaterialTheme.typography.headlineLarge, color = colors.primary)
                    }
                    if (secondaryTotal.isNotBlank()) {
                        Text(text = secondaryTotal, style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
                    }
                }
            }
            val pay: @Composable (Modifier) -> Unit = { buttonModifier ->
                KioskButton(
                    text = stringResource(R.string.review_pay, total.toDisplayString()),
                    onClick = onCheckout,
                    height = ActionButtonHeight,
                    modifier = buttonModifier,
                    trailing = {
                        Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null, modifier = Modifier.size(36.dp))
                    },
                )
            }
            val keepShopping: @Composable (Modifier) -> Unit = { buttonModifier ->
                KioskButton(
                    text = stringResource(R.string.review_continue_shopping),
                    onClick = onContinueShopping,
                    style = KioskButtonStyle.Secondary,
                    icon = Icons.Rounded.Add,
                    height = ActionButtonHeight,
                    modifier = buttonModifier,
                )
            }
            if (sidePanel) {
                pay(Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(16.dp))
                keepShopping(Modifier.fillMaxWidth())
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    keepShopping(Modifier.weight(0.8f))
                    pay(Modifier.weight(1.2f))
                }
            }
        }
    }
}

@Composable
private fun TotalsRow(
    label: String,
    amount: Money,
) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(text = amount.toDisplayString(), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}
