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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.amaxonia.kiosk.R
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

/**
 * "¿Cómo deseas pagar?": quotes the order (spec §3 Quote step) and offers the available methods as
 * giant cards. With a single method it forwards immediately via [onMethodSelected] (skip = true).
 */
@Composable
fun PaymentMethodScreen(
    viewModel: PaymentMethodViewModel,
    onMethodSelected: (method: PaymentMethod, skipped: Boolean) -> Unit,
    onBack: () -> Unit,
    onBackToOrder: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Re-validate the quote every time the screen is shown again (e.g. back from an expired payment).
    LaunchedEffect(Unit) { viewModel.ensureQuote() }
    LaunchedEffect(uiState.autoSelectedMethod) {
        uiState.autoSelectedMethod?.let { onMethodSelected(it, true) }
    }
    PaymentMethodContent(
        uiState = uiState,
        onSelect = { onMethodSelected(it, false) },
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
    onSelect: (PaymentMethod) -> Unit,
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

@Composable
private fun MethodChoices(
    uiState: PaymentMethodUiState,
    onSelect: (PaymentMethod) -> Unit,
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        // Tiles share the room left under the amount (bottom 60 % of the screen), within sane bounds.
        val methodCount = uiState.methods.size.coerceAtLeast(1)
        // Landscape: the tiles sit side by side under the amount and share its full width.
        val sideBySide = maxWidth > maxHeight && methodCount > 1
        val tileHeight =
            if (sideBySide) {
                (maxHeight - LandscapeHeroReserve).coerceIn(MinTileHeight, MaxTileHeight)
            } else {
                ((maxHeight - HeroReserve) / methodCount - TileGap).coerceIn(MinTileHeight, MaxTileHeight)
            }
        val tile: @Composable (PaymentMethod, Modifier) -> Unit = { method, tileModifier ->
            when (method) {
                PaymentMethod.CARD -> CardMethodTile(height = tileHeight, onClick = { onSelect(method) }, modifier = tileModifier)
                PaymentMethod.YAPPY -> YappyMethodTile(height = tileHeight, onClick = { onSelect(method) }, modifier = tileModifier)
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
                Row(horizontalArrangement = Arrangement.spacedBy(TileGap)) {
                    uiState.methods.forEachIndexed { index, method ->
                        reveal(index, Modifier.weight(1f)) { tile(method, Modifier) }
                    }
                }
            } else {
                uiState.methods.forEachIndexed { index, method ->
                    reveal(index, Modifier) { tile(method, Modifier) }
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
