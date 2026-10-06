package com.amaxonia.kiosk.ui.payment

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Receipt
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.ShoppingBag
import androidx.compose.material.icons.rounded.TableRestaurant
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amaxonia.kiosk.R
import com.amaxonia.kiosk.ui.components.CountdownRing
import com.amaxonia.kiosk.ui.components.Depth
import com.amaxonia.kiosk.ui.components.FlowWaves
import com.amaxonia.kiosk.ui.components.KioskButton
import com.amaxonia.kiosk.ui.components.KioskCard
import com.amaxonia.kiosk.ui.components.ScrollableFillColumn
import com.amaxonia.kiosk.ui.components.outline
import com.amaxonia.kiosk.ui.components.softShadow
import com.amaxonia.kiosk.ui.diningmode.MODE_DINE_IN
import com.amaxonia.kiosk.ui.theme.FlowBlue
import com.amaxonia.kiosk.ui.theme.FlowBlueDeep
import com.amaxonia.kiosk.ui.theme.FlowLavender
import com.amaxonia.kiosk.ui.theme.FlowSuccess
import com.amaxonia.kiosk.ui.theme.KioskColors
import com.amaxonia.kiosk.ui.theme.LocalHighContrast
import com.amaxonia.kiosk.ui.theme.LocalKioskCanvas
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlin.random.Random

private const val AUTO_FINISH_SECONDS = 15
private const val INTERVAL_MS = 1000L
private const val CHECK_DELAY_MS = 150L
private const val CONFETTI_COUNT = 36
private const val CONFETTI_FALL_MS = 3200
private const val CONFETTI_SEED = 42
private val HeroHeight = 700.dp
private const val LANDSCAPE_HERO_WEIGHT = 0.42f
private val ConfettiColors = listOf(FlowBlue, FlowLavender, FlowSuccess, Color(0xFFFFFFFF), Color(0xFF8EC5FF))

/**
 * Order confirmation. [receiptPrintFailed] swaps the "take your receipt" hint for a
 * "ask for your receipt at the counter" warning (wired by the host when printing fails).
 */
@Composable
fun OrderNumberScreen(
    orderInfo: CompletedOrderInfo,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier,
    receiptPrintFailed: Boolean = false,
    receiptPrinting: Boolean = false,
) {
    var secondsRemaining by remember { mutableIntStateOf(AUTO_FINISH_SECONDS) }
    val printing by rememberUpdatedState(receiptPrinting)

    // Restarted when printing fails, so the "ask at the counter" note stays readable for a full countdown.
    LaunchedEffect(receiptPrintFailed) {
        secondsRemaining = AUTO_FINISH_SECONDS
        while (secondsRemaining > 0) {
            delay(INTERVAL_MS)
            secondsRemaining -= 1
        }
        // Never leave while the receipt is still being printed: its outcome decides what the customer must do.
        snapshotFlow { printing }.first { !it }
        onFinish()
    }

    val colors = MaterialTheme.colorScheme
    val highContrast = LocalHighContrast.current

    if (LocalKioskCanvas.current.isLandscape) {
        LandscapeOrderNumber(orderInfo, onFinish, secondsRemaining, receiptPrintFailed, modifier)
        return
    }

    Surface(modifier = modifier.fillMaxSize(), color = colors.background) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Hero band behind the header, with a wavy edge melting into the page.
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(HeroHeight)
                        .background(KioskColors.heroBrush),
            ) {
                if (!highContrast) {
                    Confetti(modifier = Modifier.fillMaxSize())
                    FlowWaves(
                        modifier = Modifier.fillMaxWidth().height(150.dp).align(Alignment.BottomCenter),
                        frontBrush = SolidColor(colors.background),
                        backColor = Color.White.copy(alpha = 0.25f),
                        amplitude = 20.dp,
                    )
                }
            }

            Column(modifier = Modifier.fillMaxSize()) {
                ScrollableFillColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    verticalArrangement = Arrangement.SpaceBetween,
                    contentPadding = PaddingValues(start = 64.dp, end = 64.dp, top = 72.dp, bottom = 24.dp),
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        SuccessCheck()
                        Spacer(Modifier.height(32.dp))
                        Text(
                            text = stringResource(R.string.order_number_title),
                            style = MaterialTheme.typography.displayLarge,
                            color = Color.White,
                            textAlign = TextAlign.Center,
                        )
                        Text(
                            text = stringResource(R.string.order_number_subtitle),
                            style = MaterialTheme.typography.headlineSmall,
                            color = Color.White,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(56.dp))
                        OrderTicket(orderInfo = orderInfo)
                    }
                    Box(modifier = Modifier.padding(top = 48.dp)) {
                        NextStepsCard(orderInfo = orderInfo, printFailed = receiptPrintFailed)
                    }
                    // Third (empty) slot: SpaceBetween then centers the card in the room left below the ticket.
                    Spacer(Modifier.height(0.dp))
                }

                FinishBar(
                    secondsRemaining = secondsRemaining,
                    onFinish = onFinish,
                    modifier = Modifier.padding(start = 48.dp, end = 48.dp, top = 8.dp, bottom = 32.dp),
                )
            }
        }
    }
}

/** Landscape: the celebration on a full-height hero panel, the ticket and next steps beside it. */
@Composable
private fun LandscapeOrderNumber(
    orderInfo: CompletedOrderInfo,
    onFinish: () -> Unit,
    secondsRemaining: Int,
    receiptPrintFailed: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Surface(modifier = modifier.fillMaxSize(), color = colors.background) {
        Row(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier =
                    Modifier
                        .weight(LANDSCAPE_HERO_WEIGHT)
                        .fillMaxHeight()
                        .background(KioskColors.heroBrush),
                contentAlignment = Alignment.Center,
            ) {
                if (!LocalHighContrast.current) {
                    Confetti(modifier = Modifier.fillMaxSize())
                }
                Column(
                    modifier = Modifier.padding(horizontal = 56.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    SuccessCheck()
                    Spacer(Modifier.height(32.dp))
                    Text(
                        text = stringResource(R.string.order_number_title),
                        style = MaterialTheme.typography.displayLarge,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text = stringResource(R.string.order_number_subtitle),
                        style = MaterialTheme.typography.headlineSmall,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            ScrollableFillColumn(
                modifier = Modifier.weight(1f - LANDSCAPE_HERO_WEIGHT).fillMaxHeight(),
                verticalArrangement = Arrangement.Center,
                contentPadding = PaddingValues(horizontal = 72.dp, vertical = 40.dp),
            ) {
                OrderTicket(orderInfo = orderInfo)
                Spacer(Modifier.height(32.dp))
                NextStepsCard(orderInfo = orderInfo, printFailed = receiptPrintFailed)
                Spacer(Modifier.height(32.dp))
                FinishBar(secondsRemaining = secondsRemaining, onFinish = onFinish)
            }
        }
    }
}

/** Auto-finish countdown, the "Finalizar" CTA and the auto-close hint. */
@Composable
private fun FinishBar(
    secondsRemaining: Int,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CountdownRing(
                progress = secondsRemaining / AUTO_FINISH_SECONDS.toFloat(),
                size = 120.dp,
                strokeWidth = 12.dp,
            ) {
                Text(text = secondsRemaining.toString(), style = MaterialTheme.typography.headlineMedium)
            }
            Spacer(Modifier.width(32.dp))
            KioskButton(
                text = stringResource(R.string.order_number_finish),
                onClick = onFinish,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.order_number_auto_close, secondsRemaining),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SuccessCheck() {
    val scale = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(CHECK_DELAY_MS)
        scale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
    }
    Box(
        modifier =
            Modifier
                .size(176.dp)
                .graphicsLayer {
                    scaleX = scale.value
                    scaleY = scale.value
                }
                .background(Color.White, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier.size(144.dp).background(KioskColors.success, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(104.dp))
        }
    }
}

/** Brand-gradient "ticket" with side notches and a dashed tear line. */
@Composable
private fun OrderTicket(orderInfo: CompletedOrderInfo) {
    val colors = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.extraLarge
    val notch = 64.dp
    Box(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .softShadow(48.dp, Depth.High, tint = FlowBlueDeep)
                    .background(KioskColors.deepBrush, shape)
                    .outline(if (LocalHighContrast.current) 4.dp else 0.dp, Color.White, shape)
                    .padding(horizontal = 40.dp, vertical = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.order_number_label).uppercase(),
                style = MaterialTheme.typography.titleMedium.copy(letterSpacing = 3.sp),
                color = Color.White,
            )
            Text(
                text = orderInfo.orderNumber,
                style = MaterialTheme.typography.displayLarge.copy(fontSize = 188.sp, lineHeight = 196.sp),
                color = Color.White,
                maxLines = 1,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))
            Canvas(modifier = Modifier.fillMaxWidth().height(4.dp)) {
                drawLine(
                    color = Color.White.copy(alpha = 0.6f),
                    start = Offset(0f, size.height / 2),
                    end = Offset(size.width, size.height / 2),
                    strokeWidth = size.height,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(24f, 18f)),
                )
            }
            Spacer(Modifier.height(28.dp))
            val (icon, label) = destinationChip(orderInfo)
            Surface(shape = RoundedCornerShape(percent = 50), color = Color.White, contentColor = colors.primary) {
                Row(
                    modifier = Modifier.padding(horizontal = 32.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(icon, contentDescription = null, modifier = Modifier.size(40.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(text = label, style = MaterialTheme.typography.titleLarge)
                }
            }
        }
        // Side notches punched with the page background color.
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .offset(x = -notch / 2)
                .size(notch)
                .background(colors.background, CircleShape),
        )
        Box(
            Modifier
                .align(Alignment.CenterEnd)
                .offset(x = notch / 2)
                .size(notch)
                .background(colors.background, CircleShape),
        )
    }
}

@Composable
private fun destinationChip(orderInfo: CompletedOrderInfo): Pair<ImageVector, String> =
    when {
        orderInfo.tableTent != null ->
            Icons.Rounded.TableRestaurant to stringResource(R.string.order_number_table_chip, orderInfo.tableTent)
        orderInfo.diningMode == MODE_DINE_IN ->
            Icons.Rounded.Restaurant to stringResource(R.string.dining_mode_dine_in_title)
        else -> Icons.Rounded.ShoppingBag to stringResource(R.string.dining_mode_takeaway_title)
    }

/** "What happens now": where the order arrives and the receipt hint (or the print-failure warning). */
@Composable
private fun NextStepsCard(
    orderInfo: CompletedOrderInfo,
    printFailed: Boolean,
) {
    val colors = MaterialTheme.colorScheme
    KioskCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(horizontal = 36.dp, vertical = 32.dp)) {
            NextStepRow(
                icon = if (orderInfo.tableTent != null) Icons.Rounded.TableRestaurant else Icons.Rounded.Campaign,
                text =
                    if (orderInfo.tableTent != null) {
                        stringResource(R.string.order_number_table_dest, orderInfo.tableTent)
                    } else {
                        stringResource(R.string.order_number_counter_dest)
                    },
                tint = colors.primary,
                container = colors.primaryContainer,
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 24.dp), thickness = 2.dp, color = colors.outlineVariant)
            NextStepRow(
                icon = if (printFailed) Icons.Rounded.ErrorOutline else Icons.Rounded.Receipt,
                text = stringResource(if (printFailed) R.string.order_number_print_failed else R.string.order_number_take_receipt),
                tint = if (printFailed) colors.error else colors.primary,
                container = if (printFailed) colors.errorContainer else colors.primaryContainer,
            )
        }
    }
}

@Composable
private fun NextStepRow(
    icon: ImageVector,
    text: String,
    tint: Color,
    container: Color,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier.size(88.dp).background(container, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(48.dp))
        }
        Spacer(Modifier.width(28.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.headlineSmall,
            color = if (tint == MaterialTheme.colorScheme.error) tint else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
    }
}

private class ConfettiPiece(
    val x: Float,
    val delay: Float,
    val size: Float,
    val drift: Float,
    val color: Color,
)

/** Cheap confetti: precomputed pieces driven by one animated float (no per-frame allocation). */
@Composable
private fun Confetti(modifier: Modifier = Modifier) {
    val pieces =
        remember {
            val random = Random(CONFETTI_SEED)
            List(CONFETTI_COUNT) { index ->
                ConfettiPiece(
                    x = random.nextFloat(),
                    delay = random.nextFloat(),
                    size = 10f + random.nextFloat() * 14f,
                    drift = (random.nextFloat() - 0.5f) * 80f,
                    color = ConfettiColors[index % ConfettiColors.size],
                )
            }
        }
    val transition = rememberInfiniteTransition(label = "confetti")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(CONFETTI_FALL_MS, easing = LinearEasing), RepeatMode.Restart),
        label = "confetti_progress",
    )
    Canvas(modifier = modifier) {
        pieces.forEach { piece ->
            val t = (progress + piece.delay) % 1f
            val y = t * size.height
            val x = piece.x * size.width + piece.drift * t
            drawRect(
                color = piece.color.copy(alpha = 1f - t),
                topLeft = Offset(x, y),
                size = Size(piece.size, piece.size * 0.6f),
            )
        }
    }
}
