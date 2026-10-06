package com.amaxonia.kiosk.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.amaxonia.kiosk.R
import com.amaxonia.kiosk.ui.theme.KioskColors
import com.amaxonia.kiosk.ui.theme.KioskDialog
import com.amaxonia.kiosk.ui.theme.LocalHighContrast

private val GrayscaleFilter = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })
private const val BREATH_PERIOD_MS = 1100
private const val RING_START_ANGLE = -90f
private const val FULL_SWEEP = 360f
private const val STAGGER_BASE_DELAY_MS = 80
private const val STAGGER_STEP_MS = 110
private const val STAGGER_DURATION_MS = 420
private const val QTY_ANIM_MS = 180
private val KEY_RADIUS = 28.dp

/** Gentle infinite "breathing" scale, used to draw the eye to the main CTA on idle screens. */
@Composable
fun Modifier.breathing(
    minScale: Float = 1f,
    maxScale: Float = 1.06f,
    periodMillis: Int = BREATH_PERIOD_MS,
): Modifier {
    val transition = rememberInfiniteTransition(label = "breathing")
    val scale by transition.animateFloat(
        initialValue = minScale,
        targetValue = maxScale,
        animationSpec = infiniteRepeatable(tween(periodMillis, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "breathing_scale",
    )
    return this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/** Reveals [content] with a slide-up + fade that is delayed by [index] (staggered list entrance). */
@Composable
fun StaggeredReveal(
    index: Int,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val state = remember { MutableTransitionState(false).apply { targetState = true } }
    val delay = STAGGER_BASE_DELAY_MS + index * STAGGER_STEP_MS
    AnimatedVisibility(
        visibleState = state,
        modifier = modifier,
        enter =
            fadeIn(tween(STAGGER_DURATION_MS, delayMillis = delay)) +
                slideInVertically(tween(STAGGER_DURATION_MS, delayMillis = delay, easing = FastOutSlowInEasing)) { it / 3 },
    ) {
        content()
    }
}

/**
 * Vertically scrollable column that is at least as tall as its viewport, so [verticalArrangement]
 * (e.g. SpaceBetween to keep actions in the lower part of the screen) still works, while content
 * scrolls instead of overflowing when the height shrinks (accessible "low reach" mode).
 */
@Composable
fun ScrollableFillColumn(
    modifier: Modifier = Modifier,
    verticalArrangement: Arrangement.Vertical = Arrangement.SpaceBetween,
    horizontalAlignment: Alignment.Horizontal = Alignment.CenterHorizontally,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    BoxWithConstraints(modifier = modifier) {
        val viewport = maxHeight
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .heightIn(min = viewport)
                    .padding(contentPadding),
            verticalArrangement = verticalArrangement,
            horizontalAlignment = horizontalAlignment,
            content = content,
        )
    }
}

/** Round icon-only button (≥ 88 dp) with springy press. */
@Composable
fun KioskIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = KioskTouchTarget,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    enabled: Boolean = true,
) {
    val highContrast = LocalHighContrast.current
    val alpha = if (enabled) 1f else 0.35f
    Surface(
        modifier =
            modifier
                .size(size)
                .kioskPressable(enabled = enabled, onClick = onClick)
                .then(if (enabled && containerColor != Color.Transparent) Modifier.softShadow(size, Depth.Low) else Modifier),
        shape = CircleShape,
        color = containerColor.copy(alpha = containerColor.alpha * alpha),
        contentColor = contentColor.copy(alpha = alpha),
        border =
            when {
                highContrast -> BorderStroke(3.dp, MaterialTheme.colorScheme.onSurface)
                containerColor == MaterialTheme.colorScheme.surface -> BorderStroke(2.dp, MaterialTheme.colorScheme.outlineVariant)
                else -> null
            },
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(imageVector = icon, contentDescription = contentDescription, modifier = Modifier.size(size * 0.45f))
        }
    }
}

/** App header: optional back button, title (or custom leading content) and trailing actions. */
@Composable
fun KioskHeader(
    title: String?,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    leading: (@Composable RowScope.() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Surface(
        modifier = modifier.fillMaxWidth().softShadow(0.dp, Depth.Low),
        color = MaterialTheme.colorScheme.surface,
        border = if (LocalHighContrast.current) BorderStroke(2.dp, MaterialTheme.colorScheme.onSurface) else null,
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp)
                    .padding(horizontal = 32.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            if (onBack != null) {
                KioskIconButton(
                    icon = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = stringResource(R.string.btn_back),
                    onClick = onBack,
                )
            }
            if (leading != null) {
                leading()
            }
            if (title != null) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            } else {
                Spacer(Modifier.weight(1f))
            }
            actions()
        }
    }
}

/**
 * Brand logo: the configured remote logo when available, otherwise the flavor's `brand_logo`
 * artwork. The artwork has dark lettering, so on dark backgrounds it sits on a white plate.
 */
@Composable
fun BrandWordmark(
    modifier: Modifier = Modifier,
    logoUrl: String? = null,
    onDark: Boolean = false,
    height: Dp = 72.dp,
) {
    if (!logoUrl.isNullOrBlank()) {
        AsyncImage(
            model = logoUrl,
            contentDescription = stringResource(R.string.brand_name),
            modifier = modifier.height(height),
        )
        return
    }
    val logo =
        @Composable { imageModifier: Modifier ->
            Image(
                painter = painterResource(R.drawable.brand_logo),
                contentDescription = stringResource(R.string.brand_name),
                contentScale = ContentScale.Fit,
                modifier = imageModifier.height(height),
            )
        }
    if (onDark) {
        Box(
            modifier =
                modifier
                    .shadow(elevation = 8.dp, shape = RoundedCornerShape(height / 3), clip = false)
                    .background(Color.White, RoundedCornerShape(height / 3))
                    .padding(horizontal = height / 3, vertical = height / 6),
            contentAlignment = Alignment.Center,
        ) {
            logo(Modifier)
        }
    } else {
        logo(modifier)
    }
}

/**
 * Remote image with crossfade. Until (or unless) the photo loads, an illustrated [FoodArt]
 * placeholder with [placeholderIcon] is shown. [grayscale] desaturates the photo (sold-out items).
 */
@Composable
fun KioskImage(
    url: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    grayscale: Boolean = false,
    placeholderIcon: ImageVector = Icons.Rounded.Restaurant,
    placeholderIconSize: Dp = 72.dp,
) {
    var loaded by remember(url) { mutableStateOf(false) }
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        if (!loaded) {
            FoodArt(
                glyph = placeholderIcon,
                glyphSize = placeholderIconSize,
                grayscale = grayscale,
                modifier = Modifier.fillMaxSize(),
            )
        }
        if (!url.isNullOrBlank()) {
            val context = LocalContext.current
            val request = remember(url) { ImageRequest.Builder(context).data(url).crossfade(true).build() }
            AsyncImage(
                model = request,
                contentDescription = contentDescription,
                contentScale = contentScale,
                colorFilter = if (grayscale) GrayscaleFilter else null,
                onSuccess = { loaded = true },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/** −  qty  + stepper with big round buttons and an animated number. */
@Composable
fun QuantityStepper(
    quantity: Int,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
    modifier: Modifier = Modifier,
    decrementEnabled: Boolean = true,
    incrementEnabled: Boolean = true,
    buttonSize: Dp = KioskTouchTarget,
    decrementIcon: ImageVector = Icons.Rounded.Remove,
) {
    val colors = MaterialTheme.colorScheme
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        KioskIconButton(
            icon = decrementIcon,
            contentDescription = stringResource(R.string.cd_decrease),
            onClick = onDecrement,
            enabled = decrementEnabled,
            size = buttonSize,
        )
        AnimatedContent(
            targetState = quantity,
            transitionSpec = {
                val up = targetState > initialState
                (slideInVertically(tween(QTY_ANIM_MS)) { if (up) it else -it } + fadeIn(tween(QTY_ANIM_MS))) togetherWith
                    (slideOutVertically(tween(QTY_ANIM_MS)) { if (up) -it else it } + fadeOut(tween(QTY_ANIM_MS)))
            },
            modifier = Modifier.widthIn(min = buttonSize),
            contentAlignment = Alignment.Center,
            label = "qty",
        ) { value ->
            Text(
                text = value.toString(),
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Black),
                color = colors.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 12.dp),
            )
        }
        KioskIconButton(
            icon = Icons.Rounded.Add,
            contentDescription = stringResource(R.string.cd_increase),
            onClick = onIncrement,
            enabled = incrementEnabled,
            size = buttonSize,
            containerColor = colors.primary,
            contentColor = colors.onPrimary,
        )
    }
}

/**
 * Giant 3×4 numeric keypad. Keys stretch to the available width and are at least [keyHeight] tall.
 * The bottom-left key is configurable ([extraKey], e.g. "-" for RUC or "C" to clear).
 */
@Composable
fun NumericKeypad(
    onDigit: (Char) -> Unit,
    onBackspace: () -> Unit,
    modifier: Modifier = Modifier,
    extraKey: String? = null,
    onExtraKey: () -> Unit = {},
    keyHeight: Dp = KioskCtaHeight,
    spacing: Dp = 20.dp,
) {
    val rows = remember { listOf("123", "456", "789") }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(spacing)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(spacing)) {
                row.forEach { digit ->
                    KeypadKey(onClick = { onDigit(digit) }, height = keyHeight) {
                        KeypadLabel(digit.toString())
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(spacing)) {
            if (extraKey != null) {
                KeypadKey(onClick = onExtraKey, height = keyHeight, subtle = true) {
                    KeypadLabel(extraKey)
                }
            } else {
                Spacer(Modifier.weight(1f))
            }
            KeypadKey(onClick = { onDigit('0') }, height = keyHeight) {
                KeypadLabel("0")
            }
            KeypadKey(onClick = onBackspace, height = keyHeight, subtle = true) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.Backspace,
                    contentDescription = stringResource(R.string.cd_backspace),
                    modifier = Modifier.size(48.dp),
                )
            }
        }
    }
}

@Composable
private fun RowScope.KeypadKey(
    onClick: () -> Unit,
    height: Dp,
    subtle: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val highContrast = LocalHighContrast.current
    val shape = MaterialTheme.shapes.medium
    Box(
        modifier =
            Modifier
                .weight(1f)
                .height(height)
                .kioskPressable(pressedScale = 0.92f, onClick = onClick)
                .then(if (subtle) Modifier else Modifier.softShadow(KEY_RADIUS, Depth.Low))
                .background(if (subtle) colors.surfaceVariant else colors.surface, shape)
                .outline(if (highContrast) 3.dp else 0.dp, colors.onSurface, shape),
        contentAlignment = Alignment.Center,
    ) {
        CompositionLocalProvider(
            LocalContentColor provides colors.onSurface,
        ) {
            content()
        }
    }
}

@Composable
private fun KeypadLabel(text: String) {
    Text(text = text, style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.onSurface)
}

/** Circular countdown ring; [progress] is 1 → 0. The sweep is smoothed between ticks. */
@Composable
fun CountdownRing(
    progress: Float,
    modifier: Modifier = Modifier,
    size: Dp = 200.dp,
    strokeWidth: Dp = 18.dp,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val animated by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 1000, easing = LinearEasing),
        label = "countdown_ring",
    )
    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = strokeWidth.toPx()
            val inset = stroke / 2
            val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
            drawArc(
                color = trackColor,
                startAngle = 0f,
                sweepAngle = FULL_SWEEP,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = stroke),
            )
            drawArc(
                color = color,
                startAngle = RING_START_ANGLE,
                sweepAngle = FULL_SWEEP * animated,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }
        content()
    }
}

/** Floating confirmation pill ("¡Agregado!") that pops in and out. */
@Composable
fun KioskToast(
    visible: Boolean,
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Rounded.CheckCircle,
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = scaleIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy), initialScale = 0.6f) + fadeIn(),
        exit = scaleOut(targetScale = 0.8f) + fadeOut(),
    ) {
        Surface(
            shape = RoundedCornerShape(percent = 50),
            color = MaterialTheme.colorScheme.inverseSurface,
            contentColor = MaterialTheme.colorScheme.inverseOnSurface,
            shadowElevation = 10.dp,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 36.dp, vertical = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(icon, contentDescription = null, tint = KioskColors.successOnDark, modifier = Modifier.size(44.dp))
                Spacer(Modifier.width(16.dp))
                Text(text = text, style = MaterialTheme.typography.titleLarge)
            }
        }
    }
}

/** Two-or-more option pill selector (e.g. dining mode). */
@Composable
fun KioskSegmentedControl(
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
                .background(colors.surfaceVariant, RoundedCornerShape(percent = 50))
                .outline(if (highContrast) 2.dp else 0.dp, colors.onSurface, RoundedCornerShape(percent = 50))
                .padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        options.forEachIndexed { index, (label, icon) ->
            val selected = index == selectedIndex
            Row(
                modifier =
                    Modifier
                        .weight(1f)
                        .defaultMinSize(minHeight = KioskTouchTarget)
                        .clip(RoundedCornerShape(percent = 50))
                        .background(if (selected) colors.primary else Color.Transparent)
                        .kioskPressable { onSelect(index) }
                        .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                val tint = if (selected) colors.onPrimary else colors.onSurfaceVariant
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(32.dp))
                Spacer(Modifier.width(12.dp))
                Text(text = label, style = MaterialTheme.typography.titleMedium, color = tint, maxLines = 1)
            }
        }
    }
}

/** Big, friendly confirmation dialog with stacked giant buttons. */
@Composable
fun KioskConfirmDialog(
    title: String,
    message: String,
    confirmText: String,
    dismissText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    icon: ImageVector? = null,
) {
    KioskDialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.widthIn(max = 860.dp).padding(horizontal = 48.dp),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surface,
            border = if (LocalHighContrast.current) BorderStroke(4.dp, MaterialTheme.colorScheme.onSurface) else null,
            shadowElevation = 16.dp,
        ) {
            Column(
                modifier = Modifier.padding(48.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (icon != null) {
                    Box(
                        modifier = Modifier.size(120.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(64.dp),
                        )
                    }
                    Spacer(Modifier.height(28.dp))
                }
                Text(text = title, style = MaterialTheme.typography.headlineLarge, textAlign = TextAlign.Center)
                Spacer(Modifier.height(16.dp))
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(40.dp))
                KioskButton(text = dismissText, onClick = onDismiss, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(16.dp))
                KioskButton(
                    text = confirmText,
                    onClick = onConfirm,
                    style = KioskButtonStyle.Secondary,
                    height = KioskTouchTarget,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
