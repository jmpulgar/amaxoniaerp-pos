package com.amaxonia.kiosk.ui.payment

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Contactless
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.CreditCardOff
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.KeyboardDoubleArrowDown
import androidx.compose.material.icons.rounded.TimerOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.amaxonia.kiosk.R
import com.amaxonia.kiosk.ui.components.Depth
import com.amaxonia.kiosk.ui.components.KioskButton
import com.amaxonia.kiosk.ui.components.KioskButtonStyle
import com.amaxonia.kiosk.ui.components.KioskCard
import com.amaxonia.kiosk.ui.components.ScrollableFillColumn
import com.amaxonia.kiosk.ui.components.centeredMaxWidth
import com.amaxonia.kiosk.ui.components.softShadow
import com.amaxonia.kiosk.ui.paymentmethod.CardOptionLogo
import com.amaxonia.kiosk.ui.theme.FlowSuccess
import com.amaxonia.kiosk.ui.theme.KioskColors
import com.amaxonia.kiosk.ui.theme.LocalKioskCanvas

private const val ARROW_BOUNCE_MS = 700
private const val ARROW_TRAVEL_PX = 36f
private val ManualMaxWidth = 960.dp

/**
 * Card payment screen: amount huge, a pulsing card badge and an animated arrow pointing down to
 * the physical terminal, and a countdown ring. [onPaid] is invoked once the payment is registered.
 */
@Composable
fun PaymentScreen(
    viewModel: PaymentViewModel,
    onPaid: (CompletedOrderInfo) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val step = uiState.step

    LaunchedEffect(step) {
        if (step is PaymentStep.Completed) onPaid(step.info)
    }
    PaymentContent(
        uiState = uiState,
        onCancel = { viewModel.cancelPayment(onBack) },
        onRetry = viewModel::retry,
        onConfirmManual = viewModel::confirmManualPayment,
        modifier = modifier,
    )
}

/** Stateless card payment screen (rendered directly by screenshot tests). [onCancel] also changes method. */
@Composable
fun PaymentContent(
    uiState: PaymentUiState,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    onConfirmManual: () -> Unit = {},
) {
    val step = uiState.step
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize()) {
            PaymentHeader(
                title =
                    stringResource(
                        if (step == PaymentStep.AwaitingManualConfirmation) R.string.payflow_manual_title else R.string.payflow_card_title,
                    ),
                onBack = if (uiState.isPaymentInFlight) null else onCancel,
            )
            AnimatedContent(
                targetState = step.contentKey(),
                transitionSpec = { (fadeIn(tween(250)) + scaleIn(initialScale = 0.96f)) togetherWith fadeOut(tween(150)) },
                label = "card_step",
                modifier = Modifier.weight(1f),
            ) { key ->
                when (key) {
                    StepKey.QUOTING ->
                        PaymentBusyView(
                            title = stringResource(R.string.payflow_quoting_title),
                            body = stringResource(R.string.payflow_quoting_body),
                        )
                    StepKey.AWAITING -> {
                        val awaiting = uiState.step as? PaymentStep.AwaitingPayment
                        AwaitingCardView(
                            uiState = uiState,
                            secondsRemaining = awaiting?.secondsRemaining ?: 0,
                            totalSeconds = awaiting?.totalSeconds ?: CARD_PAYMENT_TIMEOUT_SECONDS,
                            onCancel = onCancel,
                        )
                    }
                    StepKey.MANUAL ->
                        ManualConfirmView(uiState = uiState, onConfirm = onConfirmManual, onChangeMethod = onCancel)
                    StepKey.PROCESSING, StepKey.COMPLETED ->
                        PaymentBusyView(
                            title = stringResource(R.string.payflow_processing_title),
                            body = stringResource(R.string.payflow_processing_body),
                            color = FlowSuccess,
                        )
                    StepKey.ERROR ->
                        CardErrorView(
                            step = uiState.step,
                            onRetry = onRetry,
                            onChangeMethod = onCancel,
                        )
                }
            }
        }
    }
}

private enum class StepKey { QUOTING, AWAITING, MANUAL, PROCESSING, COMPLETED, ERROR }

private fun PaymentStep.contentKey(): StepKey =
    when (this) {
        PaymentStep.Quoting -> StepKey.QUOTING
        is PaymentStep.AwaitingPayment -> StepKey.AWAITING
        PaymentStep.AwaitingManualConfirmation -> StepKey.MANUAL
        PaymentStep.Processing -> StepKey.PROCESSING
        is PaymentStep.Completed -> StepKey.COMPLETED
        is PaymentStep.Declined, is PaymentStep.Failed -> StepKey.ERROR
    }

@Composable
private fun AwaitingCardView(
    uiState: PaymentUiState,
    secondsRemaining: Int,
    totalSeconds: Int,
    onCancel: () -> Unit,
) {
    val pulse = rememberPulse()
    val transition = rememberInfiniteTransition(label = "arrow")
    val arrowOffset by transition.animateFloat(
        initialValue = 0f,
        targetValue = ARROW_TRAVEL_PX,
        animationSpec = infiniteRepeatable(tween(ARROW_BOUNCE_MS, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "arrow_offset",
    )
    if (LocalKioskCanvas.current.isLandscape) {
        // Landscape: amount, countdown and cancel on the left; the reader prompt on the right.
        // Scroll containers get inner padding so the soft shadows are not clipped at their edges.
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                AmountHero(total = uiState.totalAmount, currency = uiState.currency)
                Spacer(Modifier.height(48.dp))
                CancelWithCountdown(secondsRemaining, totalSeconds, arrowOffset, onCancel)
            }
            Column(
                modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CardReaderPrompt(pulse = pulse)
            }
        }
        return
    }

    ScrollableFillColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 48.dp, vertical = 24.dp),
    ) {
        AmountHero(total = uiState.totalAmount, currency = uiState.currency)

        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(vertical = 40.dp)) {
            CardReaderPrompt(pulse = pulse)
        }

        CancelWithCountdown(secondsRemaining, totalSeconds, arrowOffset, onCancel)
    }
}

/**
 * "Sin pasarela" confirmation: total, the chosen card method and a clear notice that no terminal is
 * connected (the payment is recorded manually), with "Confirmar pago" and "Cambiar forma de pago".
 */
@Composable
private fun ManualConfirmView(
    uiState: PaymentUiState,
    onConfirm: () -> Unit,
    onChangeMethod: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val option = uiState.cardOption
    ScrollableFillColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 48.dp, vertical = 24.dp),
    ) {
        AmountHero(total = uiState.totalAmount, currency = uiState.currency)
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp).centeredMaxWidth(ManualMaxWidth),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            KioskCard(modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.padding(32.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier.size(144.dp).background(colors.surfaceVariant, MaterialTheme.shapes.medium),
                        contentAlignment = Alignment.Center,
                    ) {
                        CardOptionLogo(dataUri = option?.image, size = 100.dp)
                    }
                    Spacer(Modifier.width(32.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.payflow_manual_method_label),
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = option?.name ?: stringResource(R.string.payflow_method_card_title),
                            style = MaterialTheme.typography.displaySmall,
                            color = colors.onSurface,
                        )
                    }
                }
            }
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .background(colors.primaryContainer, MaterialTheme.shapes.medium)
                        .padding(28.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Info,
                    contentDescription = null,
                    tint = colors.primary,
                    modifier = Modifier.size(56.dp),
                )
                Spacer(Modifier.width(24.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.payflow_manual_notice_title),
                        style = MaterialTheme.typography.titleLarge,
                        color = colors.onPrimaryContainer,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.payflow_manual_notice_body),
                        style = MaterialTheme.typography.bodyLarge,
                        color = colors.onPrimaryContainer,
                    )
                }
            }
        }
        Column(
            modifier = Modifier.fillMaxWidth().centeredMaxWidth(ManualMaxWidth),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            KioskButton(
                text = stringResource(R.string.payflow_manual_confirm),
                onClick = onConfirm,
                icon = Icons.Rounded.CheckCircle,
                modifier = Modifier.fillMaxWidth(),
            )
            KioskButton(
                text = stringResource(R.string.payflow_change_method),
                onClick = onChangeMethod,
                style = KioskButtonStyle.Secondary,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** Contactless "radar" (a breathing halo behind a brand-gradient disc) with the instructions. */
@Composable
private fun CardReaderPrompt(pulse: Float) {
    val colors = MaterialTheme.colorScheme
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(contentAlignment = Alignment.Center) {
            Box(
                modifier =
                    Modifier
                        .size(340.dp)
                        .graphicsLayer {
                            scaleX = pulse
                            scaleY = pulse
                        }.background(colors.primaryContainer, CircleShape),
            )
            Box(
                modifier =
                    Modifier
                        .size(250.dp)
                        .background(colors.secondaryContainer, CircleShape),
            )
            Box(
                modifier =
                    Modifier
                        .size(184.dp)
                        .softShadow(92.dp, Depth.Medium)
                        .background(KioskColors.ctaBrush, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Contactless,
                    contentDescription = null,
                    tint = colors.onPrimary,
                    modifier = Modifier.size(112.dp),
                )
            }
        }
        Spacer(Modifier.height(48.dp))
        Text(
            text = stringResource(R.string.payflow_card_instruction),
            style = MaterialTheme.typography.displaySmall,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.payflow_card_hint),
            style = MaterialTheme.typography.bodyLarge,
            color = colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/** Countdown ring + cancel button, and the bouncing arrow pointing at the terminal below the screen. */
@Composable
private fun CancelWithCountdown(
    secondsRemaining: Int,
    totalSeconds: Int,
    arrowOffset: Float,
    onCancel: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CountdownRing(secondsRemaining = secondsRemaining, totalSeconds = totalSeconds, size = 152.dp)
            Spacer(Modifier.width(32.dp))
            KioskButton(
                text = stringResource(R.string.payflow_card_cancel_operation),
                onClick = onCancel,
                style = KioskButtonStyle.Secondary,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(8.dp))
        // Arrow pointing at the payment terminal mounted below the screen.
        Icon(
            imageVector = Icons.Rounded.KeyboardDoubleArrowDown,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.secondary,
            modifier =
                Modifier
                    .size(112.dp)
                    .graphicsLayer { translationY = arrowOffset },
        )
    }
}

@Composable
private fun CardErrorView(
    step: PaymentStep,
    onRetry: () -> Unit,
    onChangeMethod: () -> Unit,
) {
    val (title, body, detail) =
        when (step) {
            is PaymentStep.Declined ->
                Triple(
                    stringResource(R.string.payflow_card_declined_title),
                    stringResource(R.string.payflow_card_declined_body),
                    step.reason,
                )
            is PaymentStep.Failed ->
                Triple(stringResource(R.string.payflow_card_failed_title), stringResource(step.reason.bodyRes()), step.message)
            else -> Triple("", "", "")
        }
    val icon =
        when {
            step is PaymentStep.Failed && step.reason == PaymentFailure.TIMEOUT -> Icons.Rounded.TimerOff
            step is PaymentStep.Declined -> Icons.Rounded.CreditCardOff
            else -> Icons.Rounded.CreditCard
        }
    PaymentStatusView(icon = icon, title = title, body = body, detail = detail) {
        KioskButton(
            text = stringResource(R.string.payflow_card_retry),
            onClick = onRetry,
            modifier = Modifier.fillMaxWidth(),
        )
        KioskButton(
            text = stringResource(R.string.payflow_change_method),
            onClick = onChangeMethod,
            style = KioskButtonStyle.Secondary,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private fun PaymentFailure.bodyRes(): Int =
    when (this) {
        PaymentFailure.QUOTE_FAILED -> R.string.payflow_card_quote_failed_body
        PaymentFailure.TIMEOUT -> R.string.payflow_card_timeout_body
        PaymentFailure.CANCELLED -> R.string.payflow_card_cancelled_body
        PaymentFailure.TERMINAL_ERROR -> R.string.payflow_card_terminal_error_body
    }
