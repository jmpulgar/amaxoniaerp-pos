package com.amaxonia.kiosk.ui.payment

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amaxonia.kiosk.R
import com.amaxonia.kiosk.core.money.Money
import com.amaxonia.kiosk.core.network.KioskCurrencyConfig
import com.amaxonia.kiosk.ui.components.CheckoutStep
import com.amaxonia.kiosk.ui.components.CheckoutStepper
import com.amaxonia.kiosk.ui.components.Depth
import com.amaxonia.kiosk.ui.components.KioskHeader
import com.amaxonia.kiosk.ui.components.ScrollableFillColumn
import com.amaxonia.kiosk.ui.components.centeredMaxWidth
import com.amaxonia.kiosk.ui.components.softShadow
import com.amaxonia.kiosk.ui.theme.FlowBlueDeep
import com.amaxonia.kiosk.ui.theme.FlowSuccess
import com.amaxonia.kiosk.ui.theme.KioskColors
import com.amaxonia.kiosk.ui.theme.LocalHighContrast
import com.amaxonia.kiosk.ui.theme.SurfaceWhite
import java.math.BigDecimal

private const val URGENT_SECONDS = 15
private const val PULSE_MS = 900
private const val SECONDARY_DEFAULT_SYMBOL = "Bs"
private const val MUTED_ALPHA = 0.8f
private val LARGE_RING = 160.dp
private val ActionsMaxWidth = 960.dp

/** Title bar shared by the payment screens: the app header (optional back) plus the checkout stepper on "Pago". */
@Composable
fun PaymentHeader(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        KioskHeader(title = title, onBack = onBack)
        CheckoutStepper(
            steps =
                listOf(
                    stringResource(R.string.payflow_step_order),
                    stringResource(R.string.payflow_step_details),
                    stringResource(R.string.payflow_step_pay),
                ),
            currentIndex = CheckoutStep.PAY,
        )
    }
}

/**
 * The amount to pay, huge and white on the deep brand gradient, with the optional
 * secondary-currency reference underneath. [compact] trims paddings for tight layouts.
 */
@Composable
fun AmountHero(
    total: Money?,
    currency: KioskCurrencyConfig,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val secondary =
        remember(total, currency) {
            val rate = runCatching { BigDecimal(currency.rate) }.getOrDefault(BigDecimal.ZERO)
            if (total == null || currency.secondary == null) {
                ""
            } else {
                total.toSecondaryCurrency(
                    rate,
                    currency.secondary ?: SECONDARY_DEFAULT_SYMBOL,
                )
            }
        }
    val shape = MaterialTheme.shapes.large
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .softShadow(36.dp, Depth.High, tint = FlowBlueDeep)
                .background(KioskColors.deepBrush, shape)
                .padding(vertical = if (compact) 20.dp else 32.dp, horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.payflow_total_label).uppercase(),
            style = MaterialTheme.typography.titleMedium.copy(letterSpacing = 3.sp),
            color = SurfaceWhite.copy(alpha = if (LocalHighContrast.current) 1f else MUTED_ALPHA),
        )
        Text(
            text = total?.toDisplayString() ?: "—",
            style = if (compact) MaterialTheme.typography.displayMedium else MaterialTheme.typography.displayLarge,
            color = SurfaceWhite,
        )
        if (secondary.isNotBlank()) {
            Text(
                text = secondary,
                style = MaterialTheme.typography.titleMedium,
                color = SurfaceWhite.copy(alpha = MUTED_ALPHA),
            )
        }
    }
}

/** Circular countdown: the ring drains smoothly; turns urgent red in the last 15 s. */
@Composable
fun CountdownRing(
    secondsRemaining: Int,
    totalSeconds: Int,
    modifier: Modifier = Modifier,
    size: Dp = 168.dp,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    val fraction = if (totalSeconds > 0) secondsRemaining.toFloat() / totalSeconds else 0f
    val animated by animateFloatAsState(
        targetValue = fraction.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 1000, easing = LinearEasing),
        label = "countdown_fraction",
    )
    val urgent = secondsRemaining <= URGENT_SECONDS
    val ringColor = if (urgent) MaterialTheme.colorScheme.error else color
    val track = MaterialTheme.colorScheme.surfaceVariant
    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = 16.dp.toPx()
            val inset = stroke / 2
            val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
            drawArc(track, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
            drawArc(ringColor, -90f, 360f * animated, false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = secondsRemaining.toString(),
                style = if (size >= LARGE_RING) MaterialTheme.typography.displaySmall else MaterialTheme.typography.headlineMedium,
                color = if (urgent) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = stringResource(R.string.payflow_time_left),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Gentle breathing scale used for "waiting" states. */
@Composable
fun rememberPulse(
    from: Float = 0.94f,
    to: Float = 1.06f,
): Float {
    val transition = rememberInfiniteTransition(label = "pulse")
    val scale by transition.animateFloat(
        initialValue = from,
        targetValue = to,
        animationSpec = infiniteRepeatable(tween(PULSE_MS, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulse_scale",
    )
    return scale
}

/** Full-screen centered spinner with title/body (quoting, processing, cancelling). */
@Composable
fun PaymentBusyView(
    title: String,
    modifier: Modifier = Modifier,
    body: String? = null,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator(modifier = Modifier.size(120.dp), color = color, strokeWidth = 12.dp, strokeCap = StrokeCap.Round)
        Spacer(Modifier.height(48.dp))
        Text(text = title, style = MaterialTheme.typography.headlineLarge, textAlign = TextAlign.Center)
        if (body != null) {
            Spacer(Modifier.height(16.dp))
            Text(
                text = body,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** Error/status panel: big round icon, title, explanation and stacked giant actions. */
@Composable
fun PaymentStatusView(
    icon: ImageVector,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    detail: String? = null,
    iconColor: Color = MaterialTheme.colorScheme.error,
    actions: @Composable ColumnScope.() -> Unit,
) {
    val entrance = remember { Animatable(0.6f) }
    LaunchedEffect(Unit) {
        entrance.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
    }
    // Scrolls when the room is short (landscape, "pantalla baja"); actions never stretch edge to edge.
    ScrollableFillColumn(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        contentPadding = PaddingValues(horizontal = 64.dp, vertical = 48.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .size(200.dp)
                    .scale(entrance.value)
                    .background(iconColor.copy(alpha = 0.12f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(112.dp))
        }
        Spacer(Modifier.height(40.dp))
        Text(text = title, style = MaterialTheme.typography.displaySmall, textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        Text(
            text = body,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (!detail.isNullOrBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.height(56.dp))
        Column(
            modifier = Modifier.centeredMaxWidth(ActionsMaxWidth),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            content = actions,
        )
    }
}

/** Big green check that pops in with a spring and an expanding halo — shown right before the order number. */
@Composable
fun PaymentSuccessView(
    title: String,
    modifier: Modifier = Modifier,
    body: String? = null,
    color: Color = FlowSuccess,
) {
    val pop = remember { Animatable(0f) }
    val halo = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        pop.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow))
    }
    LaunchedEffect(Unit) {
        halo.animateTo(1f, tween(durationMillis = 900, easing = FastOutSlowInEasing))
    }
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(modifier = Modifier.size(360.dp), contentAlignment = Alignment.Center) {
            Box(
                modifier =
                    Modifier
                        .size(360.dp)
                        .graphicsLayer {
                            scaleX = 0.6f + 0.4f * halo.value
                            scaleY = 0.6f + 0.4f * halo.value
                            alpha = 1f - halo.value
                        }.background(color.copy(alpha = 0.35f), CircleShape),
            )
            Box(
                modifier =
                    Modifier
                        .size(272.dp)
                        .scale(pop.value)
                        .softShadow(136.dp, Depth.High, tint = color)
                        .background(SurfaceWhite, CircleShape)
                        .padding(16.dp)
                        .background(color, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = null,
                    tint = SurfaceWhite,
                    modifier = Modifier.size(160.dp),
                )
            }
        }
        Spacer(Modifier.height(48.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.displayMedium.copy(fontWeight = FontWeight.Black),
            color = color,
            textAlign = TextAlign.Center,
            modifier = Modifier.graphicsLayer { alpha = pop.value.coerceIn(0f, 1f) },
        )
        if (body != null) {
            Spacer(Modifier.height(16.dp))
            Text(
                text = body,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}
