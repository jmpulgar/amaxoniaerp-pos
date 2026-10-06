package com.amaxonia.kiosk.ui.menu

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.ShoppingBag
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.amaxonia.kiosk.R
import com.amaxonia.kiosk.core.money.Money
import com.amaxonia.kiosk.core.network.KioskCategoryDto
import com.amaxonia.kiosk.core.network.KioskCurrencyConfig
import com.amaxonia.kiosk.core.network.KioskItemDto
import com.amaxonia.kiosk.ui.components.BrandWordmark
import com.amaxonia.kiosk.ui.components.CountBadge
import com.amaxonia.kiosk.ui.components.Depth
import com.amaxonia.kiosk.ui.components.KioskButton
import com.amaxonia.kiosk.ui.components.KioskButtonStyle
import com.amaxonia.kiosk.ui.components.KioskCard
import com.amaxonia.kiosk.ui.components.KioskConfirmDialog
import com.amaxonia.kiosk.ui.components.KioskHeader
import com.amaxonia.kiosk.ui.components.KioskImage
import com.amaxonia.kiosk.ui.components.KioskToast
import com.amaxonia.kiosk.ui.components.KioskTouchTarget
import com.amaxonia.kiosk.ui.components.foodGlyphFor
import com.amaxonia.kiosk.ui.components.kioskPressable
import com.amaxonia.kiosk.ui.components.outline
import com.amaxonia.kiosk.ui.components.secondaryText
import com.amaxonia.kiosk.ui.components.softShadow
import com.amaxonia.kiosk.ui.diningmode.MODE_TAKEAWAY
import com.amaxonia.kiosk.ui.theme.KioskColors
import com.amaxonia.kiosk.ui.theme.LocalHighContrast
import kotlinx.coroutines.delay

private const val TOAST_DURATION_MS = 1400L
private const val CATEGORY_SWAP_IN_MS = 260
private const val CATEGORY_SWAP_OUT_MS = 120
private const val SOLD_OUT_CONTENT_ALPHA = 0.6f
private const val PRODUCT_IMAGE_RATIO = 1.12f
private const val MUTED_ALPHA = 0.8f
private val CategoryRailWidth = 240.dp

/** Bottom padding so the last row scrolls clear of the floating cart bar. */
private val CartBarClearance = 220.dp
private val SoldOutVeil = Color(0x99121212)

@Composable
fun MenuScreen(
    viewModel: MenuViewModel,
    onOpenCustomizer: (Int) -> Unit,
    onViewCart: () -> Unit,
    onBackToAttract: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val diningMode by viewModel.orderGraph.diningMode.collectAsStateWithLifecycle()
    MenuContent(
        uiState = uiState,
        diningMode = diningMode,
        onCategorySelected = viewModel::selectCategory,
        onProductClicked = { item -> viewModel.onProductClicked(item, onOpenCustomizer) },
        onRetry = { viewModel.loadCatalog() },
        onViewCart = onViewCart,
        onBackToAttract = onBackToAttract,
        modifier = modifier,
    )
}

/** Stateless menu (rendered directly by screenshot tests). */
@Composable
fun MenuContent(
    uiState: MenuUiState,
    diningMode: String,
    onCategorySelected: (Int) -> Unit,
    onProductClicked: (KioskItemDto) -> Unit,
    onRetry: () -> Unit,
    onViewCart: () -> Unit,
    onBackToAttract: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showCancelDialog by remember { mutableStateOf(false) }

    // "¡Agregado!" feedback whenever the cart grows while the menu is visible.
    var lastCount by remember { mutableIntStateOf(-1) }
    var toastVisible by remember { mutableStateOf(false) }
    LaunchedEffect(uiState.cartItemCount) {
        val count = uiState.cartItemCount
        val grew = lastCount in 0 until count
        lastCount = count
        if (grew) {
            toastVisible = true
            delay(TOAST_DURATION_MS)
            toastVisible = false
        }
    }

    if (showCancelDialog) {
        KioskConfirmDialog(
            title = stringResource(R.string.cancel_order_title),
            message = stringResource(R.string.cancel_order_message),
            confirmText = stringResource(R.string.cancel_order_confirm),
            dismissText = stringResource(R.string.cancel_order_keep),
            onConfirm = {
                showCancelDialog = false
                onBackToAttract()
            },
            onDismiss = { showCancelDialog = false },
        )
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                KioskHeader(
                    title = null,
                    leading = { BrandWordmark(height = 80.dp) },
                    actions = { DiningModeChip(isTakeaway = diningMode == MODE_TAKEAWAY) },
                )
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    when {
                        uiState.isLoading && uiState.items.isEmpty() ->
                            CircularProgressIndicator(
                                modifier = Modifier.size(96.dp).align(Alignment.Center),
                                strokeWidth = 8.dp,
                            )
                        uiState.items.isEmpty() && uiState.errorMessage != null ->
                            CatalogError(onRetry = onRetry, modifier = Modifier.align(Alignment.Center))
                        else ->
                            Row(modifier = Modifier.fillMaxSize()) {
                                CategoryRail(
                                    categories = uiState.categories,
                                    selectedCategoryId = uiState.selectedCategoryId,
                                    onCategorySelected = onCategorySelected,
                                    modifier = Modifier.width(CategoryRailWidth).fillMaxHeight(),
                                )
                                ProductArea(
                                    uiState = uiState,
                                    onProductClicked = onProductClicked,
                                    modifier = Modifier.weight(1f).fillMaxHeight(),
                                )
                            }
                    }
                }
            }

            // The cart bar floats over the grid so its rounded top corners reveal the menu behind it.
            Column(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
                KioskToast(
                    visible = toastVisible,
                    text = stringResource(R.string.menu_added_toast),
                    modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 24.dp, start = CategoryRailWidth),
                )
                CartBottomBar(
                    itemCount = uiState.cartItemCount,
                    total = uiState.cartSubtotal,
                    currency = uiState.currency,
                    onViewCart = onViewCart,
                    onCancel = {
                        if (uiState.cartItemCount > 0) showCancelDialog = true else onBackToAttract()
                    },
                )
            }
        }
    }
}

@Composable
private fun DiningModeChip(isTakeaway: Boolean) {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(percent = 50),
        color = colors.secondaryContainer,
        contentColor = colors.onSecondaryContainer,
    ) {
        Row(
            modifier = Modifier.heightIn(min = 72.dp).padding(horizontal = 28.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (isTakeaway) Icons.Rounded.ShoppingBag else Icons.Rounded.Restaurant,
                contentDescription = null,
                modifier = Modifier.size(36.dp),
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text =
                    stringResource(
                        if (isTakeaway) R.string.dining_mode_takeaway_title else R.string.dining_mode_dine_in_title,
                    ),
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}

@Composable
private fun CatalogError(
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.padding(48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            Icons.Rounded.CloudOff,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(120.dp),
        )
        Spacer(Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.menu_load_error),
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(32.dp))
        KioskButton(text = stringResource(R.string.btn_retry), onClick = onRetry)
    }
}

@Composable
private fun CategoryRail(
    categories: List<KioskCategoryDto>,
    selectedCategoryId: Int?,
    onCategorySelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface,
        border = if (LocalHighContrast.current) BorderStroke(2.dp, MaterialTheme.colorScheme.onSurface) else null,
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 20.dp, bottom = CartBarClearance),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(categories, key = { it.id }) { cat ->
                CategoryRailItem(
                    category = cat,
                    isSelected = cat.id == selectedCategoryId,
                    onClick = { onCategorySelected(cat.id) },
                )
            }
        }
    }
}

@Composable
private fun CategoryRailItem(
    category: KioskCategoryDto,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val highContrast = LocalHighContrast.current
    val background by animateColorAsState(
        if (isSelected) colors.primaryContainer else Color.Transparent,
        label = "rail_bg",
    )
    val indicatorWidth by animateDpAsState(if (isSelected) 8.dp else 0.dp, label = "rail_indicator")
    val glyph = remember(category.name) { foodGlyphFor(category.name) }
    val disc = if (isSelected) KioskColors.ctaBrush else KioskColors.softBrush

    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 184.dp)
                .padding(end = 16.dp)
                .clip(RoundedCornerShape(topEnd = 36.dp, bottomEnd = 36.dp))
                .background(background)
                .kioskPressable(onClick = onClick),
    ) {
        Box(
            modifier =
                Modifier
                    .align(Alignment.CenterStart)
                    .width(indicatorWidth)
                    .height(112.dp)
                    .background(colors.primary, RoundedCornerShape(topEnd = 8.dp, bottomEnd = 8.dp)),
        )
        Column(
            modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 4.dp, top = 20.dp, bottom = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(108.dp)
                        .then(if (isSelected) Modifier.softShadow(54.dp, Depth.Low) else Modifier)
                        .background(disc, CircleShape)
                        .outline(if (highContrast) 2.dp else 0.dp, colors.onSurface, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (!category.iconUrl.isNullOrBlank()) {
                    KioskImage(
                        url = category.iconUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        placeholderIcon = glyph,
                        placeholderIconSize = 56.dp,
                        modifier = Modifier.size(84.dp).clip(CircleShape),
                    )
                } else {
                    Icon(
                        imageVector = glyph,
                        contentDescription = null,
                        tint = if (isSelected) colors.onPrimary else colors.primary,
                        modifier = Modifier.size(56.dp),
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = category.name,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = if (isSelected) FontWeight.Black else FontWeight.Bold),
                color = if (isSelected) colors.onPrimaryContainer else colors.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ProductArea(
    uiState: MenuUiState,
    onProductClicked: (KioskItemDto) -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedContent(
        targetState = uiState.selectedCategoryId,
        transitionSpec = {
            (fadeIn(tween(CATEGORY_SWAP_IN_MS)) + slideInVertically(tween(CATEGORY_SWAP_IN_MS)) { it / 12 }) togetherWith
                fadeOut(tween(CATEGORY_SWAP_OUT_MS))
        },
        modifier = modifier,
        label = "category_swap",
    ) { categoryId ->
        val items =
            remember(uiState.items, categoryId) {
                if (categoryId == null) uiState.items else uiState.items.filter { it.categoryId == categoryId }
            }
        val categoryName = uiState.categories.firstOrNull { it.id == categoryId }?.name ?: stringResource(R.string.menu_title)

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalArrangement = Arrangement.spacedBy(28.dp),
            contentPadding = PaddingValues(start = 28.dp, end = 32.dp, top = 32.dp, bottom = CartBarClearance),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "header", span = { GridItemSpan(maxLineSpan) }) {
                Column {
                    Text(
                        text = categoryName,
                        style = MaterialTheme.typography.displaySmall,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = pluralStringResource(R.plurals.menu_product_count, items.size, items.size),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (items.isEmpty()) {
                item(key = "empty", span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        text = stringResource(R.string.menu_empty_products),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 48.dp),
                    )
                }
            }
            items(items, key = { it.id }) { item ->
                ProductCard(
                    item = item,
                    categoryName = categoryName,
                    currency = uiState.currency,
                    onClick = { onProductClicked(item) },
                )
            }
        }
    }
}

@Composable
private fun ProductCard(
    item: KioskItemDto,
    categoryName: String,
    currency: KioskCurrencyConfig,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val money = remember(item.price) { Money.fromString(item.price) }
    val secondaryText = remember(money, currency) { money.secondaryText(currency) }
    val glyph = remember(item.name, categoryName) { foodGlyphFor(item.name, categoryName) }
    val imageShape = RoundedCornerShape(topStart = 36.dp, topEnd = 36.dp)
    val contentAlpha = if (item.soldOut) SOLD_OUT_CONTENT_ALPHA else 1f

    KioskCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = if (item.soldOut) null else onClick,
    ) {
        Column {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(PRODUCT_IMAGE_RATIO)
                        .clip(imageShape),
            ) {
                KioskImage(
                    url = item.imageUrl,
                    contentDescription = item.name,
                    grayscale = item.soldOut,
                    placeholderIcon = glyph,
                    placeholderIconSize = 128.dp,
                    modifier = Modifier.fillMaxSize(),
                )
                if (item.soldOut) {
                    SoldOutOverlay()
                }
            }

            Column(
                modifier =
                    Modifier
                        .padding(start = 24.dp, end = 20.dp, top = 20.dp, bottom = 24.dp)
                        .graphicsLayer { alpha = contentAlpha },
            ) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.titleLarge,
                    color = colors.onSurface,
                    minLines = 2,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = money.toDisplayString(),
                            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Black),
                            color = colors.primary,
                            maxLines = 1,
                        )
                        if (secondaryText.isNotBlank()) {
                            Text(
                                text = secondaryText,
                                style = MaterialTheme.typography.labelMedium,
                                color = colors.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                    }
                    if (!item.soldOut) {
                        AddRoundButton()
                    }
                }
            }
        }
    }
}

/** Decorative "+" (the whole card is the touch target, so this is not separately clickable). */
@Composable
private fun AddRoundButton() {
    Box(
        modifier =
            Modifier
                .size(80.dp)
                .softShadow(40.dp, Depth.Low)
                .background(KioskColors.ctaBrush, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Rounded.Add,
            contentDescription = stringResource(R.string.cd_add),
            tint = Color.White,
            modifier = Modifier.size(48.dp),
        )
    }
}

/** Sold out: a calm dark veil with a white pill (red is reserved for errors). */
@Composable
private fun BoxScope.SoldOutOverlay() {
    val pill = RoundedCornerShape(percent = 50)
    Box(modifier = Modifier.matchParentSize().background(SoldOutVeil))
    Text(
        text = stringResource(R.string.menu_sold_out),
        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black),
        color = MaterialTheme.colorScheme.onSurface,
        modifier =
            Modifier
                .align(Alignment.Center)
                .background(MaterialTheme.colorScheme.surface, pill)
                .outline(if (LocalHighContrast.current) 3.dp else 0.dp, MaterialTheme.colorScheme.onSurface, pill)
                .padding(horizontal = 32.dp, vertical = 14.dp),
    )
}

@Composable
private fun CartBottomBar(
    itemCount: Int,
    total: Money,
    currency: KioskCurrencyConfig,
    onViewCart: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val highContrast = LocalHighContrast.current
    val secondaryTotal = remember(total, currency) { total.secondaryText(currency) }
    val shape = RoundedCornerShape(topStart = 40.dp, topEnd = 40.dp)
    val onBar = Color.White
    val muted = onBar.copy(alpha = if (highContrast) 1f else MUTED_ALPHA)

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .softShadow(40.dp, Depth.High)
                .background(KioskColors.deepBrush, shape)
                .padding(horizontal = 28.dp, vertical = 28.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        KioskButton(
            text = stringResource(R.string.menu_cancel_order),
            onClick = onCancel,
            style = KioskButtonStyle.Ghost,
            contentColor = onBar,
            height = KioskTouchTarget,
        )

        Spacer(Modifier.weight(1f))

        Box(modifier = Modifier.size(96.dp)) {
            Icon(
                imageVector = Icons.Rounded.ShoppingBag,
                contentDescription = stringResource(R.string.cd_cart),
                tint = onBar,
                modifier = Modifier.size(72.dp).align(Alignment.BottomStart),
            )
            CountBadge(
                count = itemCount,
                size = 52.dp,
                modifier = Modifier.align(Alignment.TopEnd).offset(x = 6.dp),
            )
        }

        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = stringResource(R.string.review_total),
                style = MaterialTheme.typography.labelMedium,
                color = muted,
            )
            AnimatedContent(targetState = total.toDisplayString(), label = "cart_total") { value ->
                Text(text = value, style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Black), color = onBar)
            }
            if (secondaryTotal.isNotBlank()) {
                Text(text = secondaryTotal, style = MaterialTheme.typography.labelSmall, color = muted)
            }
        }

        KioskButton(
            text = stringResource(R.string.menu_view_cart),
            onClick = onViewCart,
            style = KioskButtonStyle.Light,
            enabled = itemCount > 0,
            trailing = {
                Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null, modifier = Modifier.size(40.dp))
            },
        )
    }
}
