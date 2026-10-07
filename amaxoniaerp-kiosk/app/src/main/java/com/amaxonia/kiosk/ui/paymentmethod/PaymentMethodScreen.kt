package com.amaxonia.kiosk.ui.paymentmethod

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.PhoneIphone
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.StoreMallDirectory
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.amaxonia.kiosk.R
import com.amaxonia.kiosk.core.network.KioskCardOption
import com.amaxonia.kiosk.domain.payment.PaymentMethod
import com.amaxonia.kiosk.ui.components.Depth
import com.amaxonia.kiosk.ui.components.KioskButton
import com.amaxonia.kiosk.ui.components.KioskButtonStyle
import com.amaxonia.kiosk.ui.components.KioskCard
import com.amaxonia.kiosk.ui.components.centeredMaxWidth
import com.amaxonia.kiosk.ui.components.kioskPressable
import com.amaxonia.kiosk.ui.components.softShadow
import com.amaxonia.kiosk.ui.payment.AmountHero
import com.amaxonia.kiosk.ui.payment.PaymentBusyView
import com.amaxonia.kiosk.ui.payment.PaymentHeader
import com.amaxonia.kiosk.ui.payment.PaymentStatusView
import com.amaxonia.kiosk.ui.payment.YappyWordmark
import com.amaxonia.kiosk.ui.theme.KioskColors
import com.amaxonia.kiosk.ui.theme.SurfaceWhite
import com.amaxonia.kiosk.ui.theme.YappyBlue
import com.amaxonia.kiosk.ui.theme.YappyOrange

private const val CARD_STAGGER_MS = 90
private val TileGap = 28.dp
private val HeroReserve = 420.dp
private val MinTileHeight = 260.dp
private val MaxTileHeight = 560.dp
private val LandscapeHeroReserve = 380.dp
private val LandscapeMaxWidth = 1600.dp
private val CompactTileHeight = 180.dp
private val BadgeSize = 200.dp
private val CompactBadgeSize = 136.dp
private const val MAX_TILES_PER_ROW = 2

/**
 * "¿Cómo deseas pagar?": quotes the order (spec §3 Quote step) and offers the available methods as
 * giant cards (CARD as one tile per company card method when the config lists them). With a single
 * choice it forwards immediately via [onMethodSelected] (skip = true).
 */
@Composable
fun PaymentMethodScreen(
    viewModel: PaymentMethodViewModel,
    onMethodSelected: (method: PaymentMethod, cardOption: KioskCardOption?, skipped: Boolean) -> Unit,
    onBack: () -> Unit,
    onBackToOrder: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Re-validate the quote every time the screen is shown again (e.g. back from an expired payment).
    LaunchedEffect(Unit) { viewModel.ensureQuote() }
    LaunchedEffect(uiState.autoSelectedMethod) {
        uiState.autoSelectedMethod?.let { onMethodSelected(it, uiState.autoSelectedCardOption, true) }
    }
    PaymentMethodContent(
        uiState = uiState,
        onSelect = { method, cardOption -> onMethodSelected(method, cardOption, false) },
        onRetry = viewModel::ensureQuote,
        onBack = onBack,
        onBackToOrder = onBackToOrder,
        modifier = modifier,
    )
}

/** Stateless payment-method chooser (rendered directly by screenshot tests). */
@Composable
fun PaymentMethodContent(
    uiState: PaymentMethodUiState,
    onSelect: (PaymentMethod, KioskCardOption?) -> Unit,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    onBackToOrder: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize()) {
            PaymentHeader(title = stringResource(R.string.payflow_method_title), onBack = onBack)
            Box(modifier = Modifier.weight(1f)) {
                when (val step = uiState.step) {
                    PaymentMethodStep.Quoting ->
                        PaymentBusyView(
                            title = stringResource(R.string.payflow_quoting_title),
                            body = stringResource(R.string.payflow_quoting_body),
                        )
                    PaymentMethodStep.Ready ->
                        if (uiState.autoSelectedMethod == null) {
                            MethodChoices(uiState = uiState, onSelect = onSelect)
                        }
                    is PaymentMethodStep.QuoteFailed ->
                        PaymentStatusView(
                            icon = Icons.Rounded.CloudOff,
                            title = stringResource(R.string.payflow_quote_failed_title),
                            body = stringResource(R.string.payflow_quote_failed_body),
                            detail = step.message,
                        ) {
                            KioskButton(
                                text = stringResource(R.string.payflow_retry),
                                onClick = onRetry,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            KioskButton(
                                text = stringResource(R.string.payflow_back_to_order),
                                onClick = onBackToOrder,
                                style = KioskButtonStyle.Secondary,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    PaymentMethodStep.NoMethodsAvailable ->
                        PaymentStatusView(
                            icon = Icons.Rounded.StoreMallDirectory,
                            title = stringResource(R.string.payflow_no_methods_title),
                            body = stringResource(R.string.payflow_no_methods_body),
                        ) {
                            KioskButton(
                                text = stringResource(R.string.payflow_back_to_order),
                                onClick = onBackToOrder,
                                style = KioskButtonStyle.Secondary,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                }
            }
        }
    }
}

/** One tile on the chooser: a company card method (or the generic card when none is listed), or Yappy. */
private data class MethodChoice(
    val method: PaymentMethod,
    val cardOption: KioskCardOption? = null,
)

private fun PaymentMethodUiState.choices(): List<MethodChoice> =
    methods.flatMap { method ->
        if (method == PaymentMethod.CARD && cardOptions.isNotEmpty()) {
            cardOptions.map { MethodChoice(method, it) }
        } else {
            listOf(MethodChoice(method))
        }
    }

@Composable
private fun MethodChoices(
    uiState: PaymentMethodUiState,
    onSelect: (PaymentMethod, KioskCardOption?) -> Unit,
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    val choices = remember(uiState.methods, uiState.cardOptions) { uiState.choices() }
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        // Tiles share the room left under the amount (bottom 60 % of the screen), within sane bounds.
        val choiceCount = choices.size.coerceAtLeast(1)
        // Landscape: the tiles sit side by side under the amount (two per row when there are more).
        val sideBySide = maxWidth > maxHeight && choiceCount > 1
        val perRow = if (sideBySide) minOf(choiceCount, MAX_TILES_PER_ROW) else 1
        val rows = (choiceCount + perRow - 1) / perRow
        // Three or more stacked tiles (e.g. VISA, MASTERCARD, débito and Yappy) get a denser layout.
        val compact = rows > 2
        val tileHeight =
            if (sideBySide) {
                ((maxHeight - LandscapeHeroReserve) / rows - TileGap).coerceIn(CompactTileHeight, MaxTileHeight)
            } else {
                ((maxHeight - HeroReserve) / choiceCount - TileGap).coerceIn(
                    if (compact) CompactTileHeight else MinTileHeight,
                    MaxTileHeight,
                )
            }
        val tile: @Composable (MethodChoice, Modifier) -> Unit = { choice, tileModifier ->
            val onClick = { onSelect(choice.method, choice.cardOption) }
            when {
                choice.cardOption != null ->
                    CardOptionTile(
                        option = choice.cardOption,
                        height = tileHeight,
                        compact = compact,
                        onClick = onClick,
                        modifier = tileModifier,
                    )
                choice.method == PaymentMethod.CARD -> CardMethodTile(height = tileHeight, onClick = onClick, modifier = tileModifier)
                else -> YappyMethodTile(height = tileHeight, onClick = onClick, modifier = tileModifier)
            }
        }
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 48.dp, vertical = 24.dp)
                    .centeredMaxWidth(LandscapeMaxWidth),
            verticalArrangement = Arrangement.spacedBy(TileGap),
        ) {
            AmountHero(total = uiState.total, currency = uiState.currency)
            Text(
                text = stringResource(R.string.payflow_method_choose),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 16.dp, start = 8.dp),
            )
            val reveal: @Composable (Int, Modifier, @Composable () -> Unit) -> Unit = { index, revealModifier, content ->
                AnimatedVisibility(
                    visible = visible,
                    modifier = revealModifier,
                    enter =
                        fadeIn(tween(durationMillis = 300, delayMillis = index * CARD_STAGGER_MS)) +
                            slideInVertically(tween(durationMillis = 350, delayMillis = index * CARD_STAGGER_MS)) { it / 3 },
                ) {
                    content()
                }
            }
            if (sideBySide) {
                choices.chunked(perRow).forEachIndexed { rowIndex, rowChoices ->
                    Row(horizontalArrangement = Arrangement.spacedBy(TileGap)) {
                        rowChoices.forEachIndexed { index, choice ->
                            reveal(rowIndex * perRow + index, Modifier.weight(1f)) { tile(choice, Modifier) }
                        }
                        // A short last row keeps the same tile width as the full rows above it.
                        repeat(perRow - rowChoices.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            } else {
                choices.forEachIndexed { index, choice ->
                    reveal(index, Modifier) { tile(choice, Modifier) }
                }
            }
        }
    }
}

@Composable
private fun CardMethodTile(
    height: Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    KioskCard(modifier = modifier.fillMaxWidth().height(height), onClick = onClick) {
        MethodTileContent(
            title = stringResource(R.string.payflow_method_card_title),
            subtitle = stringResource(R.string.payflow_method_card_subtitle),
            badge = KioskColors.ctaBrush,
            contentColor = MaterialTheme.colorScheme.onSurface,
            subtitleColor = MaterialTheme.colorScheme.onSurfaceVariant,
            arrowContainer = MaterialTheme.colorScheme.primaryContainer,
            arrowColor = MaterialTheme.colorScheme.primary,
        ) {
            Icon(
                Icons.Rounded.CreditCard,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(104.dp),
            )
        }
    }
}

/** A company card method (VISA, MASTERCARD...): flat white tile with the ERP logo and its name. */
@Composable
private fun CardOptionTile(
    option: KioskCardOption,
    height: Dp,
    compact: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val badgeSize = if (compact) CompactBadgeSize else BadgeSize
    KioskCard(modifier = modifier.fillMaxWidth().height(height), onClick = onClick) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 44.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(badgeSize).background(colors.surfaceVariant, MaterialTheme.shapes.medium),
                contentAlignment = Alignment.Center,
            ) {
                CardOptionLogo(dataUri = option.image, size = badgeSize * 0.7f)
            }
            Spacer(Modifier.width(40.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = option.name,
                    style = if (compact) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.displaySmall,
                    color = colors.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.payflow_method_card_option_subtitle),
                    style = MaterialTheme.typography.titleLarge,
                    color = colors.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(24.dp))
            Box(
                modifier = Modifier.size(if (compact) 72.dp else 96.dp).background(colors.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.AutoMirrored.Rounded.ArrowForward,
                    contentDescription = null,
                    tint = colors.primary,
                    modifier = Modifier.size(if (compact) 44.dp else 56.dp),
                )
            }
        }
    }
}

@Composable
private fun YappyMethodTile(
    height: Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = MaterialTheme.shapes.large
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .height(height)
                .kioskPressable(onClick = onClick)
                .softShadow(36.dp, Depth.Medium, tint = YappyBlue)
                .background(YappyBlue, shape),
    ) {
        MethodTileContent(
            title = null,
            subtitle = stringResource(R.string.payflow_method_yappy_subtitle),
            badge = SolidColor(YappyOrange),
            contentColor = SurfaceWhite,
            subtitleColor = SurfaceWhite.copy(alpha = 0.85f),
            arrowContainer = SurfaceWhite.copy(alpha = 0.14f),
            arrowColor = YappyOrange,
            titleSlot = { YappyWordmark() },
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.PhoneIphone, contentDescription = null, tint = SurfaceWhite, modifier = Modifier.size(112.dp))
                Icon(Icons.Rounded.QrCode2, contentDescription = null, tint = SurfaceWhite, modifier = Modifier.size(48.dp))
            }
        }
    }
}

@Composable
private fun MethodTileContent(
    title: String?,
    subtitle: String,
    badge: Brush,
    contentColor: Color,
    subtitleColor: Color,
    arrowContainer: Color,
    arrowColor: Color,
    titleSlot: (@Composable () -> Unit)? = null,
    badgeContent: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxSize().padding(horizontal = 44.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(200.dp).background(badge, CircleShape), contentAlignment = Alignment.Center) {
            badgeContent()
        }
        Spacer(Modifier.width(40.dp))
        Column(modifier = Modifier.weight(1f)) {
            if (titleSlot != null) {
                titleSlot()
            } else if (title != null) {
                Text(text = title, style = MaterialTheme.typography.displaySmall, color = contentColor)
            }
            Spacer(Modifier.height(8.dp))
            Text(text = subtitle, style = MaterialTheme.typography.headlineSmall, color = subtitleColor)
        }
        Spacer(Modifier.width(24.dp))
        Box(
            modifier = Modifier.size(96.dp).background(arrowContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.AutoMirrored.Rounded.ArrowForward,
                contentDescription = null,
                tint = arrowColor,
                modifier = Modifier.size(56.dp),
            )
        }
    }
}
