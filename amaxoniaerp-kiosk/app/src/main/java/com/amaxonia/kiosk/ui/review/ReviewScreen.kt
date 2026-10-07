package com.amaxonia.kiosk.ui.review

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Close
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.amaxonia.kiosk.R
import com.amaxonia.kiosk.core.money.Money
import com.amaxonia.kiosk.core.network.KioskCurrencyConfig
import com.amaxonia.kiosk.domain.cart.CartLine
import com.amaxonia.kiosk.ui.components.KioskButton
import com.amaxonia.kiosk.ui.components.KioskButtonStyle
import com.amaxonia.kiosk.ui.components.KioskCard
import com.amaxonia.kiosk.ui.components.KioskConfirmDialog
import com.amaxonia.kiosk.ui.components.KioskHeader
import com.amaxonia.kiosk.ui.components.KioskIconButton
import com.amaxonia.kiosk.ui.components.KioskImage
import com.amaxonia.kiosk.ui.components.KioskSegmentedControl
import com.amaxonia.kiosk.ui.components.KioskTouchTarget
import com.amaxonia.kiosk.ui.components.QuantityStepper
import com.amaxonia.kiosk.ui.components.foodGlyphFor
import com.amaxonia.kiosk.ui.components.secondaryText
import com.amaxonia.kiosk.ui.components.topHairline
import com.amaxonia.kiosk.ui.diningmode.MODE_DINE_IN
import com.amaxonia.kiosk.ui.diningmode.MODE_TAKEAWAY
import com.amaxonia.kiosk.ui.theme.LocalHighContrast
import com.amaxonia.kiosk.ui.theme.LocalKioskCanvas
import java.math.BigDecimal

private val LineImageSize = 152.dp
private val RemoveButtonSize = KioskTouchTarget
private val SidePanelWidth = 680.dp
private val StepperButtonSize = KioskTouchTarget

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
            KioskHeader(
                title = stringResource(R.string.review_title),
                onBack = onContinueShopping,
                actions = {
                    if (!uiState.isEmpty) {
                        KioskButton(
                            text = stringResource(R.string.review_clear_cart),
                            onClick = { showCancelDialog = true },
                            style = KioskButtonStyle.Ghost,
                            icon = Icons.Rounded.DeleteOutline,
                            contentColor = MaterialTheme.colorScheme.error,
                            height = KioskTouchTarget,
                        )
                    }
                },
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

@Composable
private fun CartLinesList(
    uiState: ReviewUiState,
    viewModel: ReviewViewModel,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 32.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item(key = "dining_mode") {
            KioskSegmentedControl(
                options =
                    listOf(
                        stringResource(R.string.dining_mode_dine_in_title) to Icons.Rounded.Restaurant,
                        stringResource(R.string.dining_mode_takeaway_title) to Icons.Rounded.ShoppingBag,
                    ),
                selectedIndex = if (uiState.diningMode == MODE_TAKEAWAY) 1 else 0,
                onSelect = { index -> viewModel.setDiningMode(if (index == 1) MODE_TAKEAWAY else MODE_DINE_IN) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        items(items = uiState.lines, key = { it.id }) { line ->
            CartLineCard(
                line = line,
                currency = uiState.currency,
                onIncrement = { viewModel.incrementQuantity(line.id) },
                onDecrement = { viewModel.decrementQuantity(line.id) },
                onRemove = { viewModel.removeLine(line.id) },
                modifier = Modifier.animateItem(),
            )
        }
    }
}

@Composable
private fun CartLineCard(
    line: CartLine,
    currency: KioskCurrencyConfig,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val lineTotal = line.lineTotal
    val secondaryTotal = remember(lineTotal, currency) { lineTotal.secondaryText(currency) }
    val glyph = remember(line.item.name) { foodGlyphFor(line.item.name) }

    KioskCard(modifier = modifier.fillMaxWidth()) {
        KioskIconButton(
            icon = Icons.Rounded.Close,
            contentDescription = stringResource(R.string.cd_remove),
            onClick = onRemove,
            size = RemoveButtonSize,
            containerColor = colors.surfaceVariant,
            contentColor = colors.onSurfaceVariant,
            modifier = Modifier.align(Alignment.TopEnd).padding(top = 16.dp, end = 16.dp),
        )
        Column(modifier = Modifier.padding(start = 24.dp, end = 28.dp, top = 24.dp, bottom = 24.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                KioskImage(
                    url = line.item.imageUrl,
                    contentDescription = line.item.name,
                    placeholderIcon = glyph,
                    placeholderIconSize = 72.dp,
                    modifier = Modifier.size(LineImageSize).clip(MaterialTheme.shapes.medium),
                )
                Spacer(modifier = Modifier.width(24.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = line.item.name,
                        style = MaterialTheme.typography.titleLarge,
                        color = colors.onSurface,
                        modifier = Modifier.padding(end = RemoveButtonSize),
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.review_unit_price, line.unitPrice.toDisplayString()),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                    )
                    if (line.selectedModifiers.isNotEmpty() || !line.note.isNullOrBlank()) {
                        Spacer(Modifier.height(12.dp))
                        LineDetails(line)
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                QuantityStepper(
                    quantity = line.quantity,
                    onDecrement = onDecrement,
                    onIncrement = onIncrement,
                    buttonSize = StepperButtonSize,
                    decrementIcon = if (line.quantity <= 1) Icons.Rounded.DeleteOutline else Icons.Rounded.Remove,
                )
                Spacer(Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.End) {
                    AnimatedContent(targetState = lineTotal.toDisplayString(), label = "line_total") { value ->
                        Text(
                            text = value,
                            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Black),
                            color = colors.primary,
                        )
                    }
                    if (secondaryTotal.isNotBlank()) {
                        Text(text = secondaryTotal, style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

/** Chosen modifiers (with their extra cost) and the kitchen note of a cart line. */
@Composable
private fun LineDetails(line: CartLine) {
    val colors = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        line.selectedModifiers.forEach { mod ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).background(colors.secondary, CircleShape))
                Spacer(Modifier.width(12.dp))
                Text(
                    text = mod.optionName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                if (mod.extraPrice.amount > BigDecimal.ZERO) {
                    Text(
                        text = stringResource(R.string.customizer_extra_price, mod.extraPrice.toDisplayString()),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.primary,
                    )
                }
            }
        }
        if (!line.note.isNullOrBlank()) {
            Text(
                text = stringResource(R.string.review_note, line.note),
                style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic),
                color = colors.onSurfaceVariant,
            )
        }
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
        shape =
            if (sidePanel) {
                MaterialTheme.shapes.large
            } else {
                RoundedCornerShape(0.dp)
            },
        border =
            when {
                LocalHighContrast.current -> BorderStroke(3.dp, colors.onSurface)
                sidePanel -> BorderStroke(2.dp, colors.outlineVariant)
                else -> null
            },
    ) {
        Column(modifier = Modifier.padding(horizontal = 40.dp, vertical = 28.dp)) {
            TotalsRow(
                label =
                    stringResource(R.string.review_subtotal) + " · " +
                        pluralStringResource(R.plurals.review_item_count, uiState.totalItemCount, uiState.totalItemCount),
                amount = uiState.subtotal,
            )
            TotalsRow(
                label = stringResource(if (uiState.usesItbms) R.string.review_tax_itbms else R.string.review_tax_iva),
                amount = uiState.estimatedTax,
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), thickness = 2.dp, color = colors.outline)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.review_total),
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.weight(1f),
                )
                Column(horizontalAlignment = Alignment.End) {
                    AnimatedContent(targetState = total.toDisplayString(), label = "review_total") { value ->
                        Text(text = value, style = MaterialTheme.typography.displaySmall, color = colors.onSurface)
                    }
                    if (secondaryTotal.isNotBlank()) {
                        Text(text = secondaryTotal, style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
            val pay: @Composable (Modifier) -> Unit = { buttonModifier ->
                KioskButton(
                    text = stringResource(R.string.review_pay, total.toDisplayString()),
                    onClick = onCheckout,
                    modifier = buttonModifier,
                    trailing = {
                        Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null, modifier = Modifier.size(40.dp))
                    },
                )
            }
            val keepShopping: @Composable (Modifier) -> Unit = { buttonModifier ->
                KioskButton(
                    text = stringResource(R.string.review_continue_shopping),
                    onClick = onContinueShopping,
                    style = KioskButtonStyle.Secondary,
                    modifier = buttonModifier,
                )
            }
            if (sidePanel) {
                pay(Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(20.dp))
                keepShopping(Modifier.fillMaxWidth())
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    keepShopping(Modifier.weight(0.85f))
                    pay(Modifier.weight(1.15f))
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
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(text = amount.toDisplayString(), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
    }
}
