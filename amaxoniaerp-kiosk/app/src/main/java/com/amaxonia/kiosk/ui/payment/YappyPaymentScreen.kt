package com.amaxonia.kiosk.ui.payment

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.TimerOff
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.amaxonia.kiosk.R
import com.amaxonia.kiosk.ui.components.Depth
import com.amaxonia.kiosk.ui.components.KioskButton
import com.amaxonia.kiosk.ui.components.KioskButtonStyle
import com.amaxonia.kiosk.ui.components.KioskCard
import com.amaxonia.kiosk.ui.components.ScrollableFillColumn
import com.amaxonia.kiosk.ui.components.softShadow
import com.amaxonia.kiosk.ui.theme.FlowSuccess
import com.amaxonia.kiosk.ui.theme.SurfaceWhite
import com.amaxonia.kiosk.ui.theme.YappyBlue
import com.amaxonia.kiosk.ui.theme.YappyOrange
import kotlinx.coroutines.delay

private const val SUCCESS_HOLD_MS = 1_800L
private val QrChromeReserve = 760.dp
private val MinQrSize = 400.dp
private val MaxQrSize = 600.dp
private val LandscapeQrReserve = 120.dp

/**
 * Yappy payment: giant QR, 3-step instructions, amount, countdown ring and a pulsing waiting
 * state; success check animation before [onPaid]; clear declined/expired errors with
 * "Generar nuevo código" / "Cambiar método de pago". Cancel deletes the charge before [onBack].
 */
@Composable
fun YappyPaymentScreen(
    viewModel: YappyPaymentViewModel,
    onPaid: (CompletedOrderInfo) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val step = uiState.step

    LaunchedEffect(step) {
        if (step is YappyStep.Success) {
            delay(SUCCESS_HOLD_MS)
            onPaid(step.info)
        }
    }
    YappyPaymentContent(
        uiState = uiState,
        onLeave = { viewModel.leave(onBack) },
        onNewCode = viewModel::generateCode,
        modifier = modifier,
    )
}

/** Stateless Yappy payment screen (rendered directly by screenshot tests). */
@Composable
fun YappyPaymentContent(
    uiState: YappyUiState,
    onLeave: () -> Unit,
    onNewCode: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val step = uiState.step
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize()) {
            PaymentHeader(title = stringResource(R.string.payflow_yappy_title))
            AnimatedContent(
                targetState = step.contentKey(),
                transitionSpec = { (fadeIn(tween(250)) + scaleIn(initialScale = 0.96f)) togetherWith fadeOut(tween(150)) },
                label = "yappy_step",
                modifier = Modifier.weight(1f),
            ) { key ->
                when (key) {
                    YappyKey.CREATING ->
                        PaymentBusyView(title = stringResource(R.string.payflow_yappy_generating), color = YappyBlue)
                    YappyKey.AWAITING -> {
                        val awaiting = uiState.step as? YappyStep.AwaitingScan
                        if (awaiting != null) {
                            AwaitingScanView(
                                uiState = uiState,
                                awaiting = awaiting,
                                onCancel = onLeave,
                            )
                        }
                    }
                    YappyKey.CONFIRMING ->
                        PaymentBusyView(title = stringResource(R.string.payflow_yappy_confirming), color = FlowSuccess)
                    YappyKey.CANCELLING ->
                        PaymentBusyView(title = stringResource(R.string.payflow_yappy_cancelling), color = YappyBlue)
                    YappyKey.SUCCESS ->
                        PaymentSuccessView(
                            title = stringResource(R.string.payflow_yappy_success),
                            body = stringResource(R.string.payflow_success_body),
                        )
                    YappyKey.ERROR -> {
                        val error = uiState.step as? YappyStep.Error
                        if (error != null) {
                            YappyErrorView(
                                error = error,
                                canChangeMethod = uiState.canChangeMethod,
                                onNewCode = onNewCode,
                                onLeave = onLeave,
                            )
                        }
                    }
                }
            }
        }
    }
}

private enum class YappyKey { CREATING, AWAITING, CONFIRMING, CANCELLING, SUCCESS, ERROR }

private fun YappyStep.contentKey(): YappyKey =
    when (this) {
        YappyStep.Creating -> YappyKey.CREATING
        is YappyStep.AwaitingScan -> YappyKey.AWAITING
        YappyStep.Confirming -> YappyKey.CONFIRMING
        YappyStep.Cancelling -> YappyKey.CANCELLING
        is YappyStep.Success -> YappyKey.SUCCESS
        is YappyStep.Error -> YappyKey.ERROR
    }

@Composable
private fun AwaitingScanView(
    uiState: YappyUiState,
    awaiting: YappyStep.AwaitingScan,
    onCancel: () -> Unit,
) {
    val pulse = rememberPulse(from = 0.98f, to = 1.02f)
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        if (maxWidth > maxHeight) {
            // Landscape: the QR fills the height on the left; amount, steps and actions on the right.
            val qrSize = (maxHeight - LandscapeQrReserve).coerceIn(MinQrSize, MaxQrSize)
            Row(
                modifier = Modifier.fillMaxSize().padding(start = 48.dp, end = 24.dp, top = 24.dp, bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                QrCard(qrHash = awaiting.qrHash, qrSize = qrSize, pulse = pulse)
                Column(
                    // Inner padding keeps the banner shadow inside the scroll container's clip.
                    modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(28.dp),
                ) {
                    YappyAmountBanner(uiState)
                    InstructionSteps()
                    WaitingAndActions(uiState = uiState, awaiting = awaiting, onCancel = onCancel)
                }
            }
            return@BoxWithConstraints
        }
        // The QR is as large as the room allows (it shrinks in "pantalla baja"), never below a scannable size.
        val qrSize = (maxHeight - QrChromeReserve).coerceIn(MinQrSize, MaxQrSize)
        ScrollableFillColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 48.dp, end = 48.dp, top = 16.dp, bottom = 32.dp),
        ) {
            YappyAmountBanner(uiState)
            QrCard(qrHash = awaiting.qrHash, qrSize = qrSize, pulse = pulse, modifier = Modifier.padding(vertical = 28.dp))
            InstructionSteps()
            WaitingAndActions(
                uiState = uiState,
                awaiting = awaiting,
                onCancel = onCancel,
                modifier = Modifier.padding(top = 28.dp),
            )
        }
    }
}

@Composable
private fun QrCard(
    qrHash: String,
    qrSize: Dp,
    pulse: Float,
    modifier: Modifier = Modifier,
) {
    KioskCard(
        modifier =
            modifier.graphicsLayer {
                scaleX = pulse
                scaleY = pulse
            },
    ) {
        Box(modifier = Modifier.padding(24.dp).size(qrSize), contentAlignment = Alignment.Center) {
            QrImage(content = qrHash)
        }
    }
}

@Composable
private fun InstructionSteps() {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        InstructionStep(1, stringResource(R.string.payflow_yappy_step_open))
        InstructionStep(2, stringResource(R.string.payflow_yappy_step_scan))
        InstructionStep(3, stringResource(R.string.payflow_yappy_step_confirm))
    }
}

/** "Waiting for your payment" with the countdown, then cancel / change method. */
@Composable
private fun WaitingAndActions(
    uiState: YappyUiState,
    awaiting: YappyStep.AwaitingScan,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.large)
                    .padding(horizontal = 32.dp, vertical = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CountdownRing(
                secondsRemaining = awaiting.secondsRemaining,
                totalSeconds = awaiting.totalSeconds,
                size = 128.dp,
                color = YappyBlue,
            )
            Spacer(Modifier.width(32.dp))
            Text(
                text = stringResource(R.string.payflow_yappy_waiting),
                style = MaterialTheme.typography.headlineMedium,
                color = YappyBlue,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(24.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            KioskButton(
                text = stringResource(R.string.payflow_cancel),
                onClick = onCancel,
                style = KioskButtonStyle.Secondary,
                modifier = Modifier.weight(1f),
            )
            if (uiState.canChangeMethod) {
                KioskButton(
                    text = stringResource(R.string.payflow_change_method),
                    onClick = onCancel,
                    style = KioskButtonStyle.Secondary,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun YappyAmountBanner(uiState: YappyUiState) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .softShadow(36.dp, Depth.Medium, tint = YappyBlue)
                .background(YappyBlue, MaterialTheme.shapes.large)
                .padding(horizontal = 40.dp, vertical = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        YappyWordmark()
        Spacer(Modifier.weight(1f))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = stringResource(R.string.payflow_total_label).uppercase(),
                style = MaterialTheme.typography.titleSmall.copy(letterSpacing = 2.sp),
                color = SurfaceWhite.copy(alpha = 0.8f),
            )
            Text(
                text = uiState.totalAmount?.toDisplayString() ?: "—",
                style = MaterialTheme.typography.displayMedium,
                color = SurfaceWhite,
            )
        }
    }
}

@Composable
fun YappyWordmark(modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = stringResource(R.string.payflow_method_yappy_title),
            style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Black),
            color = SurfaceWhite,
        )
        Spacer(Modifier.width(8.dp))
        Box(modifier = Modifier.size(20.dp).background(YappyOrange, CircleShape))
    }
}

@Composable
private fun QrImage(content: String) {
    val bitmap by rememberQrBitmap(content)
    val qr = bitmap
    if (qr == null) {
        CircularProgressIndicator(color = YappyBlue, modifier = Modifier.size(96.dp))
    } else {
        Image(
            bitmap = qr,
            contentDescription = stringResource(R.string.payflow_yappy_qr_description),
            filterQuality = FilterQuality.None,
            modifier = Modifier.fillMaxSize().background(SurfaceWhite, RoundedCornerShape(12.dp)),
        )
    }
}

@Composable
private fun InstructionStep(
    number: Int,
    label: String,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(280.dp)) {
        Box(
            modifier = Modifier.size(64.dp).background(YappyOrange, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = number.toString(), style = MaterialTheme.typography.headlineMedium, color = SurfaceWhite)
        }
        Spacer(Modifier.height(10.dp))
        Text(text = label, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
    }
}

@Composable
private fun YappyErrorView(
    error: YappyStep.Error,
    canChangeMethod: Boolean,
    onNewCode: () -> Unit,
    onLeave: () -> Unit,
) {
    val content = error.kind.content()
    PaymentStatusView(
        icon = content.icon,
        title = stringResource(content.title),
        body = stringResource(content.body),
        iconColor = if (error.kind == YappyErrorKind.EXPIRED) YappyOrange else MaterialTheme.colorScheme.error,
    ) {
        if (error.kind != YappyErrorKind.NOT_CONFIGURED) {
            KioskButton(
                text = stringResource(R.string.payflow_yappy_new_code),
                onClick = onNewCode,
                icon = Icons.Rounded.QrCode2,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        KioskButton(
            text = stringResource(if (canChangeMethod) R.string.payflow_change_method else R.string.payflow_cancel),
            onClick = onLeave,
            style = KioskButtonStyle.Secondary,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private class ErrorContent(
    val icon: ImageVector,
    val title: Int,
    val body: Int,
)

private fun YappyErrorKind.content(): ErrorContent =
    when (this) {
        YappyErrorKind.DECLINED ->
            ErrorContent(Icons.Rounded.ErrorOutline, R.string.payflow_yappy_declined_title, R.string.payflow_yappy_declined_body)
        YappyErrorKind.EXPIRED ->
            ErrorContent(Icons.Rounded.TimerOff, R.string.payflow_yappy_expired_title, R.string.payflow_yappy_expired_body)
        YappyErrorKind.ORDER_EXPIRED ->
            ErrorContent(Icons.Rounded.TimerOff, R.string.payflow_yappy_order_expired_title, R.string.payflow_yappy_order_expired_body)
        YappyErrorKind.NOT_CONFIGURED ->
            ErrorContent(
                Icons.Rounded.ErrorOutline,
                R.string.payflow_yappy_not_configured_title,
                R.string.payflow_yappy_not_configured_body,
            )
        YappyErrorKind.UPSTREAM ->
            ErrorContent(Icons.Rounded.ErrorOutline, R.string.payflow_yappy_upstream_title, R.string.payflow_yappy_upstream_body)
        YappyErrorKind.NETWORK ->
            ErrorContent(Icons.Rounded.WifiOff, R.string.payflow_yappy_network_title, R.string.payflow_yappy_network_body)
    }
