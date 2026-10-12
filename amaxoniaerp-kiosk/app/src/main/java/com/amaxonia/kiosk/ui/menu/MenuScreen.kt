package com.amaxonia.kiosk.ui.menu

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Fastfood
import androidx.compose.material.icons.rounded.Home
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.amaxonia.kiosk.ui.components.KioskImage
import com.amaxonia.kiosk.ui.components.KioskToast
import com.amaxonia.kiosk.ui.components.displayName
import com.amaxonia.kiosk.ui.components.foodGlyphFor
import com.amaxonia.kiosk.ui.components.outline
import com.amaxonia.kiosk.ui.components.secondaryText
import com.amaxonia.kiosk.ui.components.softShadow
import com.amaxonia.kiosk.ui.components.topHairline
import com.amaxonia.kiosk.ui.diningmode.MODE_TAKEAWAY
import com.amaxonia.kiosk.ui.theme.KioskColors
import com.amaxonia.kiosk.ui.theme.LocalHighContrast
import com.amaxonia.kiosk.ui.theme.LocalKioskCanvas
import kotlinx.coroutines.delay

private const val TOAST_DURATION_MS = 1400L
private const val CATEGORY_SWAP_IN_MS = 160
private const val CATEGORY_SWAP_OUT_MS = 80
private const val SOLD_OUT_CONTENT_ALPHA = 0.5f
private const val PRODUCT_COLUMNS = 3
private const val LANDSCAPE_PRODUCT_COLUMNS = 5
private const val RECOMMENDED_ROWS = 2
private const val CATEGORY_IMAGE_ASPECT = 1.25f
private const val HERO_RING_ALPHA = 0.10f
private const val HERO_SUBTITLE_ALPHA = 0.88f
private const val SUGGESTION_COUNT = 6
private const val SCRIM_ALPHA = 0.55f
private val SideMenuWidth = 300.dp
private val SideIconSize = 76.dp
private val SideIconRadius = 16.dp
private val SideItemRadius = 20.dp
private val HeroHeight = 260.dp
private val HeroImageSize = 200.dp
private val AddBadgeSize = 60.dp
private val SoldOutVeil = Color(0x99FFFFFF)

/** Category names that make good "También te sugerimos" add-ons (sides, drinks, desserts, coffee). */
private val SuggestionKeywords =
    listOf(
        "acompa", "papa", "side", "fries", "bebida", "drink", "refresco", "soda", "jugo", "juice",
        "postre", "dessert", "helado", "café", "cafe", "coffee", "extra",
    )

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
        onShowHome = viewModel::showHome,
        onProductClicked = { item -> viewModel.onProductClicked(item, onOpenCustomizer) },
        onRetry = { viewModel.loadCatalog() },
        onViewCart = onViewCart,
        onBackToAttract = onBackToAttract,
        modifier = modifier,
    )
}

/**
 * Stateless menu (rendered directly by screenshot tests), laid out like a fast-food self-order
 * kiosk: a category side menu with "Inicio" on the left, the home page ("Descubre nuestro menú") or
 * a three-column product grid on the right, and the order bar (bag, total, "Ver mi orden") at the
 * bottom. "Ver mi orden" first offers add-ons ("También te sugerimos").
 */
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
    onShowHome: () -> Unit = {},
) {
    var showCancelDialog by remember { mutableStateOf(false) }
    var showSuggestions by remember { mutableStateOf(false) }
    val suggestions = remember(uiState.items, uiState.categories) { suggestionsFor(uiState) }

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
                                SideMenu(
                                    categories = uiState.categories,
                                    items = uiState.items,
                                    selectedCategoryId = uiState.selectedCategoryId,
                                    isTakeaway = diningMode == MODE_TAKEAWAY,
                                    onHome = onShowHome,
                                    onCategorySelected = onCategorySelected,
                                    modifier = Modifier.width(SideMenuWidth).fillMaxHeight(),
                                )
                                MainArea(
                                    uiState = uiState,
                                    onCategorySelected = onCategorySelected,
                                    onProductClicked = onProductClicked,
                                    modifier = Modifier.weight(1f).fillMaxHeight(),
                                )
                            }
                    }
                    KioskToast(
                        visible = toastVisible,
                        text = stringResource(R.string.menu_added_toast),
                        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp, start = SideMenuWidth),
                    )
                }
                OrderBar(
                    itemCount = uiState.cartItemCount,
                    total = uiState.cartSubtotal,
                    currency = uiState.currency,
                    onViewCart = {
                        if (suggestions.isNotEmpty()) showSuggestions = true else onViewCart()
                    },
                    onCancel = {
                        if (uiState.cartItemCount > 0) showCancelDialog = true else onBackToAttract()
                    },
                )
            }

            AnimatedVisibility(visible = showSuggestions, enter = fadeIn(), exit = fadeOut()) {
                SuggestionsOverlay(
                    suggestions = suggestions,
                    currency = uiState.currency,
                    onProductClicked = { item ->
                        if (item.modifierGroups.isNotEmpty()) showSuggestions = false
                        onProductClicked(item)
                    },
                    onDone = {
                        showSuggestions = false
                        onViewCart()
                    },
                )
            }
        }
    }
}

/** Up to [SUGGESTION_COUNT] available add-ons from side/drink/dessert categories. */
private fun suggestionsFor(uiState: MenuUiState): List<KioskItemDto> {
    val addOnCategories =
        uiState.categories
            .filter { category -> SuggestionKeywords.any { category.name.lowercase().contains(it) } }
            .map { it.id }
            .toSet()
    return uiState.items
        .filter { it.categoryId in addOnCategories && !it.soldOut }
        .take(SUGGESTION_COUNT)
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

/** Left side menu: logo, dining mode, "Inicio" and the category list. */
@Composable
private fun SideMenu(
    categories: List<KioskCategoryDto>,
    items: List<KioskItemDto>,
    selectedCategoryId: Int?,
    isTakeaway: Boolean,
    onHome: () -> Unit,
    onCategorySelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = modifier.softShadow(0.dp, Depth.Low),
        color = colors.surface,
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            item(key = "logo") {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, top = 28.dp, bottom = 16.dp),
                ) {
                    BrandWordmark(height = 64.dp)
                    Spacer(Modifier.height(18.dp))
                    DiningModeTag(isTakeaway = isTakeaway)
                }
            }
            item(key = "home") {
                SideMenuItem(
                    label = stringResource(R.string.menu_home),
                    selected = selectedCategoryId == null,
                    onClick = onHome,
                ) { selected ->
                    Box(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .background(if (selected) colors.primary else colors.surfaceVariant, RoundedCornerShape(SideIconRadius)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Rounded.Home,
                            contentDescription = null,
                            tint = if (selected) colors.onPrimary else colors.primary,
                            modifier = Modifier.size(40.dp),
                        )
                    }
                }
            }
            item(key = "menu_label") {
                Text(
                    text = stringResource(R.string.menu_title).uppercase(),
                    style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 2.sp),
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(start = 12.dp, top = 20.dp, bottom = 6.dp),
                )
            }
            items(categories, key = { it.id }) { category ->
                val imageUrl = remember(category, items) { categoryImageUrl(category, items) }
                val glyph = remember(category.name) { foodGlyphFor(category.name) }
                SideMenuItem(
                    label = displayName(category.name),
                    selected = category.id == selectedCategoryId,
                    onClick = { onCategorySelected(category.id) },
                ) { _ ->
                    KioskImage(
                        url = imageUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        placeholderIcon = glyph,
                        placeholderIconSize = 40.dp,
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(SideIconRadius)),
                        photoPadding = 6.dp,
                    )
                }
            }
        }
    }
}

/** Dine in / takeaway reminder under the logo, as a soft brand chip. */
@Composable
private fun DiningModeTag(isTakeaway: Boolean) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier =
            Modifier
                .background(colors.secondaryContainer, RoundedCornerShape(percent = 50))
                .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (isTakeaway) Icons.Rounded.ShoppingBag else Icons.Rounded.Restaurant,
            contentDescription = null,
            tint = colors.onSecondaryContainer,
            modifier = Modifier.size(26.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = stringResource(if (isTakeaway) R.string.dining_mode_takeaway_title else R.string.dining_mode_dine_in_title),
            style = MaterialTheme.typography.labelMedium,
            color = colors.onSecondaryContainer,
            maxLines = 1,
        )
    }
}

/** Rounded menu entry; the selected one is filled with the soft brand indigo. */
@Composable
private fun SideMenuItem(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    leading: @Composable (selected: Boolean) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val highContrast = LocalHighContrast.current
    val shape = RoundedCornerShape(SideItemRadius)
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 104.dp)
                .clip(shape)
                .background(if (selected) colors.primaryContainer else Color.Transparent)
                .outline(if (selected && highContrast) 3.dp else 0.dp, colors.onSurface, shape)
                .clickable(onClick = onClick)
                .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(SideIconSize), contentAlignment = Alignment.Center) {
            leading(selected)
        }
        Spacer(Modifier.width(14.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = if (selected) FontWeight.Black else FontWeight.SemiBold),
            color = if (selected) colors.primary else colors.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Home page or the selected category's product grid. */
@Composable
private fun MainArea(
    uiState: MenuUiState,
    onCategorySelected: (Int) -> Unit,
    onProductClicked: (KioskItemDto) -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedContent(
        targetState = uiState.selectedCategoryId,
        transitionSpec = { fadeIn(tween(CATEGORY_SWAP_IN_MS)) togetherWith fadeOut(tween(CATEGORY_SWAP_OUT_MS)) },
        modifier = modifier,
        label = "category_swap",
    ) { categoryId ->
        if (categoryId == null) {
            MenuHome(uiState = uiState, onCategorySelected = onCategorySelected, onProductClicked = onProductClicked)
        } else {
            CategoryProducts(uiState = uiState, categoryId = categoryId, onProductClicked = onProductClicked)
        }
    }
}

/** "Descubre nuestro menú": a tile per category, then a row of recommended products. */
@Composable
private fun MenuHome(
    uiState: MenuUiState,
    onCategorySelected: (Int) -> Unit,
    onProductClicked: (KioskItemDto) -> Unit,
) {
    val landscape = LocalKioskCanvas.current.isLandscape
    val columns = if (landscape) LANDSCAPE_PRODUCT_COLUMNS else PRODUCT_COLUMNS
    val recommended =
        remember(uiState.items, columns) {
            // Round-robin across categories so the rows show variety, two full rows at most.
            uiState.items
                .filterNot { it.soldOut }
                .groupBy { it.categoryId }
                .values
                .let { groups -> (0 until (groups.maxOfOrNull { it.size } ?: 0)).flatMap { i -> groups.mapNotNull { it.getOrNull(i) } } }
                .take(columns * RECOMMENDED_ROWS)
        }
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        contentPadding = PaddingValues(start = 28.dp, end = 28.dp, top = 28.dp, bottom = 40.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(key = "hero", span = { GridItemSpan(maxLineSpan) }) {
            // Featured photo: the first available product of the first category (usually the mains).
            val heroImage =
                remember(uiState.items, uiState.categories) {
                    uiState.categories.firstNotNullOfOrNull { category ->
                        uiState.items
                            .firstOrNull { it.categoryId == category.id && !it.soldOut && !it.imageUrl.isNullOrBlank() }
                            ?.imageUrl
                    }
                }
            MenuHero(imageUrl = heroImage)
        }
        item(key = "title", span = { GridItemSpan(maxLineSpan) }) {
            SectionTitle(stringResource(R.string.menu_discover), modifier = Modifier.padding(top = 12.dp))
        }
        items(uiState.categories, key = { "cat_${it.id}" }) { category ->
            CategoryTile(
                category = category,
                imageUrl = remember(category, uiState.items) { categoryImageUrl(category, uiState.items) },
                onClick = { onCategorySelected(category.id) },
            )
        }
        if (recommended.isNotEmpty()) {
            item(key = "recommended_title", span = { GridItemSpan(maxLineSpan) }) {
                SectionTitle(stringResource(R.string.menu_recommended), modifier = Modifier.padding(top = 16.dp))
            }
            items(recommended, key = { "rec_${it.id}" }) { item ->
                ProductCard(item = item, categoryName = categoryName(uiState, item), currency = uiState.currency, onClick = {
                    onProductClicked(item)
                })
            }
        }
    }
}

/**
 * Welcome banner on the brand gradient: the big question on the left and a featured product photo
 * on a white disc on the right (a food glyph while the catalog has no photos).
 */
@Composable
private fun MenuHero(imageUrl: String?) {
    val shape = MaterialTheme.shapes.large
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(HeroHeight)
                .softShadow(24.dp, Depth.High)
                .clip(shape)
                .background(KioskColors.heroBrush, shape),
    ) {
        // Decorative rings for depth on the gradient.
        Box(
            modifier =
                Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 120.dp, y = (-140).dp)
                    .size(420.dp)
                    .background(Color.White.copy(alpha = HERO_RING_ALPHA), CircleShape),
        )
        Row(
            modifier = Modifier.fillMaxSize().padding(start = 36.dp, end = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.menu_hero_title),
                    style = MaterialTheme.typography.headlineLarge,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = stringResource(R.string.menu_hero_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = HERO_SUBTITLE_ALPHA),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(20.dp))
            KioskImage(
                url = imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                placeholderIcon = Icons.Rounded.Fastfood,
                placeholderIconSize = 96.dp,
                modifier =
                    Modifier
                        .size(HeroImageSize)
                        .clip(CircleShape)
                        .background(Color.White),
                photoPadding = 26.dp,
            )
        }
    }
}

@Composable
private fun SectionTitle(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.headlineMedium,
        color = MaterialTheme.colorScheme.onBackground,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

/** Home tile: the category picture large on a soft plate, its name below with an arrow. */
@Composable
private fun CategoryTile(
    category: KioskCategoryDto,
    imageUrl: String?,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val glyph = remember(category.name) { foodGlyphFor(category.name) }
    KioskCard(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            KioskImage(
                url = imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                placeholderIcon = glyph,
                placeholderIconSize = 80.dp,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(CATEGORY_IMAGE_ASPECT)
                        .clip(MaterialTheme.shapes.small),
                photoPadding = 14.dp,
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 8.dp, top = 14.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = displayName(category.name),
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold),
                    color = colors.onSurface,
                    minLines = 2,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(6.dp))
                Icon(
                    Icons.AutoMirrored.Rounded.ArrowForward,
                    contentDescription = null,
                    tint = colors.primary,
                    modifier = Modifier.size(28.dp),
                )
            }
        }
    }
}

@Composable
private fun CategoryProducts(
    uiState: MenuUiState,
    categoryId: Int,
    onProductClicked: (KioskItemDto) -> Unit,
) {
    val items = remember(uiState.items, categoryId) { uiState.items.filter { it.categoryId == categoryId } }
    val categoryName = uiState.categories.firstOrNull { it.id == categoryId }?.name ?: stringResource(R.string.menu_title)
    val columns = if (LocalKioskCanvas.current.isLandscape) LANDSCAPE_PRODUCT_COLUMNS else PRODUCT_COLUMNS

    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        contentPadding = PaddingValues(start = 28.dp, end = 28.dp, top = 32.dp, bottom = 40.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(key = "header", span = { GridItemSpan(maxLineSpan) }) {
            Column(modifier = Modifier.padding(bottom = 4.dp)) {
                Text(
                    text = displayName(categoryName),
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(10.dp))
                // Short brand accent under the category title.
                Box(
                    modifier =
                        Modifier
                            .size(width = 72.dp, height = 8.dp)
                            .background(KioskColors.heroBrush, RoundedCornerShape(percent = 50)),
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
            ProductCard(item = item, categoryName = categoryName, currency = uiState.currency, onClick = { onProductClicked(item) })
        }
    }
}

/**
 * Product tile: big photo on a soft plate, name, and the price next to a round "+" — the whole
 * tile is the touch target.
 */
@Composable
private fun ProductCard(
    item: KioskItemDto,
    categoryName: String,
    currency: KioskCurrencyConfig,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme
    val money = remember(item.price) { Money.fromString(item.price) }
    val secondaryText = remember(money, currency) { money.secondaryText(currency) }
    val glyph = remember(item.name, categoryName) { foodGlyphFor(item.name, categoryName) }
    val contentAlpha = if (item.soldOut) SOLD_OUT_CONTENT_ALPHA else 1f

    KioskCard(
        modifier = modifier.fillMaxWidth(),
        selected = selected,
        onClick = if (item.soldOut) null else onClick,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp).graphicsLayer { alpha = contentAlpha },
        ) {
            KioskImage(
                url = item.imageUrl,
                contentDescription = item.name,
                contentScale = ContentScale.Fit,
                grayscale = item.soldOut,
                placeholderIcon = glyph,
                placeholderIconSize = 96.dp,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(MaterialTheme.shapes.small),
                photoPadding = 12.dp,
            )
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = item.name,
                style = MaterialTheme.typography.titleSmall,
                color = colors.onSurface,
                minLines = 2,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 6.dp),
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = money.toDisplayString(),
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black),
                        color = colors.primary,
                        maxLines = 1,
                    )
                    if (secondaryText.isNotBlank()) {
                        Text(
                            text = secondaryText,
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                }
                if (!item.soldOut) {
                    Box(
                        modifier = Modifier.size(AddBadgeSize).background(colors.primary, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null, tint = colors.onPrimary, modifier = Modifier.size(34.dp))
                    }
                }
            }
        }
        if (item.soldOut) SoldOutOverlay()
        if (selected) {
            Icon(
                imageVector = Icons.Rounded.CheckCircle,
                contentDescription = null,
                tint = colors.secondary,
                modifier = Modifier.align(Alignment.TopEnd).padding(10.dp).size(48.dp).background(colors.surface, CircleShape),
            )
        }
    }
}

/** Sold out: a white veil with a dark label (red is reserved for errors). */
@Composable
private fun BoxScope.SoldOutOverlay() {
    val shape = RoundedCornerShape(8.dp)
    Box(modifier = Modifier.matchParentSize().background(SoldOutVeil))
    Text(
        text = stringResource(R.string.menu_sold_out),
        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black),
        color = MaterialTheme.colorScheme.surface,
        modifier =
            Modifier
                .align(Alignment.Center)
                .background(MaterialTheme.colorScheme.onSurface, shape)
                .outline(if (LocalHighContrast.current) 3.dp else 0.dp, MaterialTheme.colorScheme.onSurface, shape)
                .padding(horizontal = 24.dp, vertical = 10.dp),
    )
}

/** Bottom order bar: bag with count and total on the left, "Ver mi orden" and "Cancelar orden" on the right. */
@Composable
private fun OrderBar(
    itemCount: Int,
    total: Money,
    currency: KioskCurrencyConfig,
    onViewCart: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val secondaryTotal = remember(total, currency) { total.secondaryText(currency) }
    Surface(
        modifier = modifier.fillMaxWidth().topHairline(if (LocalHighContrast.current) colors.onSurface else Color.Transparent),
        color = colors.surface,
        shadowElevation = 24.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(modifier = Modifier.size(112.dp)) {
                Box(
                    modifier = Modifier.size(96.dp).align(Alignment.BottomStart).background(colors.primaryContainer, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ShoppingBag,
                        contentDescription = stringResource(R.string.cd_cart),
                        tint = colors.primary,
                        modifier = Modifier.size(56.dp),
                    )
                }
                if (itemCount > 0) {
                    CountBadge(count = itemCount, size = 48.dp, modifier = Modifier.align(Alignment.TopEnd))
                }
            }
            Spacer(Modifier.width(20.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.menu_your_order),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.onSurfaceVariant,
                )
                AnimatedContent(targetState = total.toDisplayString(), label = "cart_total") { value ->
                    Text(text = value, style = MaterialTheme.typography.headlineLarge, color = colors.onSurface)
                }
                if (secondaryTotal.isNotBlank()) {
                    Text(text = secondaryTotal, style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
                }
            }
            Column(modifier = Modifier.width(500.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                KioskButton(
                    text = stringResource(R.string.menu_view_cart),
                    onClick = onViewCart,
                    enabled = itemCount > 0,
                    height = 104.dp,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                KioskButton(
                    text = stringResource(R.string.menu_cancel_order),
                    onClick = onCancel,
                    style = KioskButtonStyle.Ghost,
                    contentColor = colors.onSurfaceVariant,
                    height = 72.dp,
                    textStyle = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/** "También te sugerimos": add-ons offered before the order review, dismissed with "No, gracias". */
@Composable
internal fun SuggestionsOverlay(
    suggestions: List<KioskItemDto>,
    currency: KioskCurrencyConfig,
    onProductClicked: (KioskItemDto) -> Unit,
    onDone: () -> Unit,
) {
    var addedIds by remember { mutableStateOf(emptySet<Int>()) }
    val landscape = LocalKioskCanvas.current.isLandscape
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = SCRIM_ALPHA))
                // Swallows taps so nothing behind the sheet reacts.
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {}),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedVisibility(visible = true, enter = scaleIn(initialScale = 0.94f) + fadeIn()) {
            Surface(
                modifier = Modifier.padding(40.dp).widthIn(max = if (landscape) 1400.dp else 980.dp),
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surface,
            ) {
                Column(modifier = Modifier.padding(40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = stringResource(R.string.menu_suggestions_title),
                        style = MaterialTheme.typography.displaySmall,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(32.dp))
                    val columns = if (landscape) SUGGESTION_COUNT else PRODUCT_COLUMNS
                    suggestions.chunked(columns).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(bottom = 16.dp)) {
                            row.forEach { item ->
                                ProductCard(
                                    item = item,
                                    categoryName = "",
                                    currency = currency,
                                    selected = item.id in addedIds,
                                    onClick = {
                                        if (item.modifierGroups.isEmpty()) addedIds = addedIds + item.id
                                        onProductClicked(item)
                                    },
                                    modifier = Modifier.width(SuggestionCardWidth),
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                    KioskButton(
                        text =
                            stringResource(
                                if (addedIds.isEmpty()) R.string.menu_suggestions_skip else R.string.menu_suggestions_continue,
                            ),
                        onClick = onDone,
                        style = if (addedIds.isEmpty()) KioskButtonStyle.Secondary else KioskButtonStyle.Primary,
                        modifier = Modifier.widthIn(min = 560.dp),
                    )
                }
            }
        }
    }
}

private val SuggestionCardWidth: Dp = 270.dp

private fun categoryName(
    uiState: MenuUiState,
    item: KioskItemDto,
): String = uiState.categories.firstOrNull { it.id == item.categoryId }?.name.orEmpty()

/** The category's own icon, or else the photo of its first product. */
private fun categoryImageUrl(
    category: KioskCategoryDto,
    items: List<KioskItemDto>,
): String? =
    category.iconUrl?.takeIf { it.isNotBlank() }
        ?: items.firstOrNull { it.categoryId == category.id && !it.imageUrl.isNullOrBlank() }?.imageUrl
