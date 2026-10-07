package com.amaxonia.kiosk.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.amaxonia.kiosk.ui.theme.KioskColors
import com.amaxonia.kiosk.ui.theme.LocalHighContrast

/** Minimum touch target for primary kiosk CTAs (spec §3: 120 dp). */
val KioskCtaHeight = 120.dp

/** Minimum touch target for secondary actions (spec §3: 88 dp). */
val KioskTouchTarget = 88.dp

/**
 * Widest a content column (forms, action bars) grows. The 1080 dp portrait canvas is narrower, so
 * this only kicks in on wide canvases (landscape kiosks, 16:10 tablets) to avoid edge-to-edge stretch.
 */
val KioskContentMaxWidth = 1200.dp

/** Fills the width up to [max] and centers itself in any extra room. */
fun Modifier.centeredMaxWidth(max: Dp = KioskContentMaxWidth): Modifier =
    this
        .fillMaxWidth()
        .wrapContentWidth(Alignment.CenterHorizontally)
        .widthIn(max = max)
        .fillMaxWidth()

private const val BADGE_POP_SCALE = 1.45f
private val BUTTON_RADIUS = 10.dp
private const val BUTTON_PRESSED_SCALE = 0.98f
private val CARD_BORDER = 2.dp
private val SELECTED_BORDER = 4.dp
private val HC_BORDER = 3.dp
private const val DISABLED_ALPHA = 0.4f
private const val OUTLINE_ALPHA = 0.55f

/**
 * Primary = solid brand indigo CTA; Accent = solid brand blue; Secondary = white with a grey outline;
 * Light = white with indigo text; Ghost = transparent outline.
 */
enum class KioskButtonStyle { Primary, Accent, Secondary, Light, Ghost }

/**
 * Springy press feedback shared by every tappable kiosk surface: scales down on press and
 * bounces back, which reads as "physical" on a large touch screen.
 */
@Composable
fun Modifier.kioskPressable(
    enabled: Boolean = true,
    pressedScale: Float = 0.96f,
    onClick: () -> Unit,
): Modifier {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) pressedScale else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "press_scale",
    )
    return this
        .scale(scale)
        .clickable(interactionSource = interaction, indication = null, enabled = enabled, onClick = onClick)
}

/**
 * Pill-shaped kiosk CTA.
 *
 * [dimmed] renders the disabled look while keeping the button tappable (used when a tap should
 * explain what is missing instead of doing nothing). [contentColor] overrides the text/icon color,
 * e.g. a white ghost button on top of a red bar.
 */
@Composable
fun KioskButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: KioskButtonStyle = KioskButtonStyle.Primary,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    height: Dp = KioskCtaHeight,
    trailing: (@Composable RowScope.() -> Unit)? = null,
    dimmed: Boolean = false,
    contentColor: Color = Color.Unspecified,
    textStyle: TextStyle = MaterialTheme.typography.labelLarge,
) {
    val colors = MaterialTheme.colorScheme
    val highContrast = LocalHighContrast.current
    val (container, defaultContent) =
        when (style) {
            KioskButtonStyle.Primary -> colors.primary to colors.onPrimary
            KioskButtonStyle.Accent -> colors.secondary to colors.onSecondary
            KioskButtonStyle.Secondary -> colors.surface to colors.onSurface
            KioskButtonStyle.Light -> colors.surface to colors.primary
            KioskButtonStyle.Ghost -> Color.Transparent to colors.onSurface
        }
    val content = if (contentColor != Color.Unspecified) contentColor else defaultContent
    // Self-order kiosk buttons: flat rectangles with softly rounded corners, no gradients or shadows.
    val shape = RoundedCornerShape(BUTTON_RADIUS)
    val border =
        when {
            highContrast -> BorderStroke(3.dp, if (style == KioskButtonStyle.Ghost) content else colors.onSurface)
            style == KioskButtonStyle.Secondary || style == KioskButtonStyle.Light ->
                BorderStroke(
                    2.dp,
                    colors.outline.copy(alpha = OUTLINE_ALPHA),
                )
            style == KioskButtonStyle.Ghost -> BorderStroke(2.dp, content.copy(alpha = 0.35f))
            else -> null
        }
    val active = enabled && !dimmed
    val alpha = if (active) 1f else DISABLED_ALPHA

    Surface(
        modifier =
            modifier
                .defaultMinSize(minHeight = height)
                .kioskPressable(enabled = enabled, pressedScale = BUTTON_PRESSED_SCALE, onClick = onClick),
        shape = shape,
        color = if (container == Color.Transparent) Color.Transparent else container.copy(alpha = alpha),
        contentColor = content.copy(alpha = alpha),
        border = border,
    ) {
        Row(
            modifier =
                Modifier
                    .height(height)
                    .padding(horizontal = 36.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            if (icon != null) {
                Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(40.dp))
                Spacer(Modifier.width(16.dp))
            }
            Text(
                text = text,
                style = textStyle,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (trailing != null) {
                Spacer(Modifier.width(16.dp))
                trailing()
            }
        }
    }
}

/** Flat white tile with a thin grey border — the base for products, options and choices. */
@Composable
fun KioskCard(
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val highContrast = LocalHighContrast.current
    val shape = MaterialTheme.shapes.medium
    val borderColor =
        when {
            selected -> if (highContrast) colors.primary else colors.secondary
            highContrast -> colors.onSurface
            else -> colors.outlineVariant
        }
    val borderWidth =
        when {
            selected -> SELECTED_BORDER
            highContrast -> HC_BORDER
            else -> CARD_BORDER
        }
    Box(
        modifier =
            modifier
                .then(if (onClick != null) Modifier.kioskPressable(pressedScale = BUTTON_PRESSED_SCALE, onClick = onClick) else Modifier)
                .clip(shape)
                .background(colors.surface, shape)
                .outline(width = borderWidth, color = borderColor, shape = shape),
        content = content,
    )
}

/**
 * Price tag: bold indigo on a pale indigo pill, so prices pop on white cards without competing
 * with CTAs. [emphasized] uses the brand CTA gradient with white text (hero prices).
 */
@Composable
fun PricePill(
    text: String,
    modifier: Modifier = Modifier,
    emphasized: Boolean = false,
    style: TextStyle = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black),
) {
    val colors = MaterialTheme.colorScheme
    val highContrast = LocalHighContrast.current
    val shape = RoundedCornerShape(percent = 50)
    val background =
        if (emphasized) {
            Modifier.background(KioskColors.ctaBrush, shape)
        } else {
            Modifier.background(colors.primaryContainer, shape)
        }
    Text(
        text = text,
        style = style,
        color = if (emphasized) Color.White else colors.primary,
        maxLines = 1,
        modifier =
            modifier
                .then(background)
                .outline(if (highContrast) 2.dp else 0.dp, colors.onSurface, shape)
                .padding(horizontal = 22.dp, vertical = 8.dp),
    )
}

/** Round badge with a count (cart items, step numbers). Pops every time the count changes. */
@Composable
fun CountBadge(
    count: Int,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
) {
    val bump = remember { Animatable(1f) }
    var previous by remember { mutableIntStateOf(count) }
    LaunchedEffect(count) {
        if (count != previous) {
            previous = count
            bump.snapTo(BADGE_POP_SCALE)
            bump.animateTo(1f, spring(dampingRatio = Spring.DampingRatioHighBouncy, stiffness = Spring.StiffnessMediumLow))
        }
    }
    val highContrast = LocalHighContrast.current
    val pill = RoundedCornerShape(percent = 50)
    Box(
        modifier =
            modifier
                .graphicsLayer {
                    scaleX = bump.value
                    scaleY = bump.value
                }
                .defaultMinSize(minWidth = size, minHeight = size)
                .background(MaterialTheme.colorScheme.secondary, pill)
                .outline(if (highContrast) 2.dp else 0.dp, MaterialTheme.colorScheme.onSecondary, pill)
                .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Black),
            color = MaterialTheme.colorScheme.onSecondary,
        )
    }
}
