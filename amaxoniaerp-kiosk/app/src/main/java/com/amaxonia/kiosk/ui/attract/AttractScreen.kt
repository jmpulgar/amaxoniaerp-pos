package com.amaxonia.kiosk.ui.attract

import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Coffee
import androidx.compose.material.icons.rounded.Icecream
import androidx.compose.material.icons.rounded.LocalDrink
import androidx.compose.material.icons.rounded.LocalPizza
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LunchDining
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.amaxonia.kiosk.R
import com.amaxonia.kiosk.core.media.KioskMediaCache
import com.amaxonia.kiosk.core.network.KioskMediaItem
import com.amaxonia.kiosk.ui.accessibility.KioskLanguage
import com.amaxonia.kiosk.ui.components.BrandWordmark
import com.amaxonia.kiosk.ui.components.Depth
import com.amaxonia.kiosk.ui.components.FlowWaves
import com.amaxonia.kiosk.ui.components.KioskButton
import com.amaxonia.kiosk.ui.components.breathing
import com.amaxonia.kiosk.ui.components.brushTint
import com.amaxonia.kiosk.ui.components.kioskPressable
import com.amaxonia.kiosk.ui.components.softShadow
import com.amaxonia.kiosk.ui.theme.FlowBlue
import com.amaxonia.kiosk.ui.theme.FlowError
import com.amaxonia.kiosk.ui.theme.FlowGradient
import com.amaxonia.kiosk.ui.theme.FlowIndigo
import com.amaxonia.kiosk.ui.theme.FlowIndigoDeep
import com.amaxonia.kiosk.ui.theme.FlowIndigoSoft
import com.amaxonia.kiosk.ui.theme.FlowLavender
import com.amaxonia.kiosk.ui.theme.KioskColors
import com.amaxonia.kiosk.ui.theme.LocalHighContrast
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.sin

private const val DEFAULT_IMAGE_DURATION_SEC = 8
private const val SECONDS_TO_MILLIS = 1000L
private const val TAP_HINT_PERIOD_MS = 700
private const val SCRIM_ALPHA = 0.7f
private const val TOP_SCRIM_ALPHA = 0.45f
private const val ART_LOOP_MS = 6_000
private const val TWO_PI = (2 * PI).toFloat()
private const val BUBBLE_ALPHA = 0.14f
private const val BUBBLE_ICON_ALPHA = 0.85f
private const val HERO_TOP_FRACTION = 0.23f
private val BUBBLE_FLOAT = 14.dp
private val HeroPlateSize = 420.dp
private val WaveFrontBrush = Brush.verticalGradient(listOf(FlowIndigo, FlowIndigoDeep))

/**
 * Attract loop. [language] / [onLanguageSelected] are optional: when the host wires them, big ES/EN
 * pills are shown above the CTA so customers can switch language before starting.
 */
@Composable
fun AttractScreen(
    viewModel: AttractViewModel,
    onStartOrder: () -> Unit,
    onAdminUnlocked: () -> Unit,
    modifier: Modifier = Modifier,
    language: KioskLanguage? = null,
    onLanguageSelected: ((KioskLanguage) -> Unit)? = null,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    AttractContent(
        uiState = uiState,
        onStartOrder = onStartOrder,
        onSecretTap = viewModel::onSecretTap,
        onMediaFinished = viewModel::advanceToNextMedia,
        adminActions =
            AdminUnlockActions(
                onPasswordChanged = viewModel::onAdminPasswordChanged,
                onConfirm = { viewModel.submitAdminUnlock(onUnlocked = onAdminUnlocked) },
                onDismiss = viewModel::dismissAdminDialog,
            ),
        modifier = modifier,
        language = language,
        onLanguageSelected = onLanguageSelected,
    )
}

/** Callbacks of the hidden admin unlock dialog. */
class AdminUnlockActions(
    val onPasswordChanged: (String) -> Unit,
    val onConfirm: () -> Unit,
    val onDismiss: () -> Unit,
)

/** Stateless attract loop (rendered directly by screenshot tests). */
@Composable
fun AttractContent(
    uiState: AttractUiState,
    onStartOrder: () -> Unit,
    onSecretTap: () -> Unit,
    onMediaFinished: () -> Unit,
    adminActions: AdminUnlockActions,
    modifier: Modifier = Modifier,
    language: KioskLanguage? = null,
    onLanguageSelected: ((KioskLanguage) -> Unit)? = null,
) {
    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(Color.Black)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) {
                    if (!uiState.isOffline) {
                        onStartOrder()
                    }
                },
    ) {
        val current = uiState.currentMedia
        when {
            current != null && current.type.equals("VIDEO", ignoreCase = true) ->
                VideoAttractPlayer(
                    url = current.url,
                    onMediaEnded = { onMediaFinished() },
                )
            current != null && current.type.equals("IMAGE", ignoreCase = true) ->
                ImageAttractDisplay(
                    mediaItem = current,
                    onDurationExpired = { onMediaFinished() },
                )
            else -> FallbackAttractDisplay(brandColorHex = uiState.brandColor)
        }

        // Top scrim + brand logo (5 quick taps on the logo open the admin unlock dialog).
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(320.dp)
                    .background(Brush.verticalGradient(listOf(FlowIndigoDeep.copy(alpha = TOP_SCRIM_ALPHA), Color.Transparent))),
        )
        Box(
            modifier =
                Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 56.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { onSecretTap() }
                    .padding(16.dp),
        ) {
            if (!uiState.logoUrl.isNullOrBlank()) {
                AsyncImage(
                    model = uiState.logoUrl,
                    contentDescription = stringResource(R.string.brand_name),
                    modifier = Modifier.height(112.dp),
                )
            } else {
                BrandWordmark(onDark = true, height = 96.dp)
            }
        }

        // Bottom scrim with language pills and the giant CTA.
        Column(
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, FlowIndigoDeep.copy(alpha = SCRIM_ALPHA))))
                    .padding(start = 56.dp, end = 56.dp, top = 220.dp, bottom = 72.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AnimatedVisibility(visible = !uiState.isOffline, enter = fadeIn(), exit = fadeOut()) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    TouchToOrderCta(onClick = onStartOrder)
                    if (onLanguageSelected != null) {
                        Spacer(Modifier.height(40.dp))
                        LanguagePills(selected = language, onSelected = onLanguageSelected)
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = uiState.isOffline,
            enter = fadeIn() + scaleIn(initialScale = 0.9f),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center),
        ) {
            OfflineBanner()
        }

        if (uiState.isAdminDialogOpen) {
            AdminUnlockDialog(
                password = uiState.adminPassword,
                isLoading = uiState.isAdminLoading,
                errorMessage = uiState.adminErrorMessage,
                onPasswordChanged = adminActions.onPasswordChanged,
                onConfirm = adminActions.onConfirm,
                onDismiss = adminActions.onDismiss,
            )
        }
    }
}

@Composable
private fun TouchToOrderCta(onClick: () -> Unit) {
    val transition = rememberInfiniteTransition(label = "tap_hint")
    val tapOffset by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(TAP_HINT_PERIOD_MS, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "tap_offset",
    )
    val density = LocalDensity.current
    val tapTravelPx = with(density) { 14.dp.toPx() }
    val highContrast = LocalHighContrast.current

    Surface(
        modifier =
            Modifier
                .fillMaxWidth()
                .breathing(maxScale = 1.03f)
                .kioskPressable(onClick = onClick)
                .softShadow(100.dp, Depth.High, tint = FlowIndigoDeep),
        shape = RoundedCornerShape(percent = 50),
        color = Color.White,
        contentColor = FlowIndigo,
        border = if (highContrast) BorderStroke(6.dp, Color.Black) else null,
    ) {
        Row(
            modifier =
                Modifier
                    .height(200.dp)
                    .padding(start = 40.dp, end = 56.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Box(
                modifier = Modifier.size(136.dp).background(KioskColors.ctaBrush, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.TouchApp,
                    contentDescription = null,
                    tint = Color.White,
                    modifier =
                        Modifier
                            .size(80.dp)
                            .graphicsLayer { translationY = tapOffset * tapTravelPx },
                )
            }
            Spacer(Modifier.width(40.dp))
            Text(
                text = stringResource(R.string.attract_touch_to_start),
                style = MaterialTheme.typography.displaySmall,
                color = if (highContrast) Color.Black else FlowIndigo,
                maxLines = 2,
                modifier = Modifier.weight(1f, fill = false),
            )
        }
    }
}

@Composable
private fun LanguagePills(
    selected: KioskLanguage?,
    onSelected: (KioskLanguage) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        LanguagePill(
            flag = stringResource(R.string.lang_flag_es),
            label = stringResource(R.string.a11y_spanish),
            selected = selected == KioskLanguage.SPANISH,
            onClick = { onSelected(KioskLanguage.SPANISH) },
        )
        LanguagePill(
            flag = stringResource(R.string.lang_flag_en),
            label = stringResource(R.string.a11y_english),
            selected = selected == KioskLanguage.ENGLISH,
            onClick = { onSelected(KioskLanguage.ENGLISH) },
        )
    }
}

@Composable
private fun LanguagePill(
    flag: String,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.kioskPressable(onClick = onClick),
        shape = RoundedCornerShape(percent = 50),
        color = if (selected) Color.White else FlowIndigoDeep.copy(alpha = 0.45f),
        contentColor = if (selected) FlowIndigo else Color.White,
        border = BorderStroke(if (selected) 5.dp else 3.dp, if (selected) FlowBlue else Color.White.copy(alpha = 0.7f)),
    ) {
        Row(
            modifier = Modifier.height(96.dp).padding(horizontal = 36.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = flag, fontSize = 40.sp)
            Spacer(Modifier.width(16.dp))
            Text(text = label, style = MaterialTheme.typography.titleLarge)
        }
    }
}

@Composable
private fun OfflineBanner() {
    Surface(
        modifier = Modifier.padding(56.dp),
        shape = MaterialTheme.shapes.extraLarge,
        color = FlowError,
        contentColor = Color.White,
        border = BorderStroke(4.dp, Color.White),
        shadowElevation = 16.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 56.dp, vertical = 64.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier.size(140.dp).background(Color.White, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.WifiOff, contentDescription = null, tint = FlowError, modifier = Modifier.size(80.dp))
            }
            Spacer(Modifier.height(32.dp))
            Text(
                text = stringResource(R.string.attract_out_of_service_title),
                style = MaterialTheme.typography.displaySmall,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.attract_out_of_service),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun VideoAttractPlayer(
    url: String,
    onMediaEnded: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val exoPlayer =
        remember(url) {
            val cacheFactory = KioskMediaCache.getCacheDataSourceFactory(context)
            val mediaSourceFactory = DefaultMediaSourceFactory(cacheFactory)

            ExoPlayer.Builder(context)
                .setMediaSourceFactory(mediaSourceFactory)
                .build()
                .apply {
                    volume = 0f // Silent attract loop
                    repeatMode = Player.REPEAT_MODE_OFF
                    addListener(
                        object : Player.Listener {
                            override fun onPlaybackStateChanged(playbackState: Int) {
                                if (playbackState == Player.STATE_ENDED) {
                                    onMediaEnded()
                                }
                            }
                        },
                    )
                    setMediaItem(MediaItem.fromUri(url))
                    prepare()
                    playWhenReady = true
                }
        }

    DisposableEffect(exoPlayer) {
        onDispose {
            exoPlayer.release()
        }
    }

    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                player = exoPlayer
                useController = false
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                layoutParams =
                    ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
            }
        },
        modifier = modifier.fillMaxSize(),
    )
}

@Composable
private fun ImageAttractDisplay(
    mediaItem: KioskMediaItem,
    onDurationExpired: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val durationSec = if (mediaItem.durationSec > 0) mediaItem.durationSec else DEFAULT_IMAGE_DURATION_SEC

    LaunchedEffect(mediaItem.url) {
        delay(durationSec * SECONDS_TO_MILLIS)
        onDurationExpired()
    }

    AsyncImage(
        model = mediaItem.url,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier.fillMaxSize(),
    )
}

/** A floating food "bubble" on the fallback attract artwork (fractions of the screen). */
private class FoodBubble(
    val icon: ImageVector,
    val x: Float,
    val y: Float,
    val size: Dp,
    val phase: Float,
)

private val FoodBubbles =
    listOf(
        FoodBubble(Icons.Rounded.LocalDrink, x = 0.06f, y = 0.17f, size = 196.dp, phase = 0f),
        FoodBubble(Icons.Rounded.Icecream, x = 0.74f, y = 0.13f, size = 220.dp, phase = 1.7f),
        FoodBubble(Icons.Rounded.LocalPizza, x = 0.80f, y = 0.40f, size = 168.dp, phase = 3.1f),
        FoodBubble(Icons.Rounded.Coffee, x = 0.03f, y = 0.44f, size = 152.dp, phase = 4.4f),
    )

/**
 * Branded artwork used when no attract media is configured: the Flow gradient, a hero "plate" with
 * a burger, floating food bubbles and animated waves anchoring the call to action. One infinite
 * transition drives everything and is only read at draw time (no recomposition per frame).
 */
@Composable
private fun FallbackAttractDisplay(
    brandColorHex: String?,
    modifier: Modifier = Modifier,
) {
    val brandColor = remember(brandColorHex) { parseHexColor(brandColorHex, default = FlowIndigo) }
    val background = remember(brandColor) { Brush.linearGradient(listOf(brandColor, FlowGradient[1], FlowGradient[2])) }
    val transition = rememberInfiniteTransition(label = "attract_art")
    val time by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(ART_LOOP_MS, easing = LinearEasing), RepeatMode.Restart),
        label = "attract_time",
    )
    val density = LocalDensity.current
    val floatPx = with(density) { BUBBLE_FLOAT.toPx() }

    BoxWithConstraints(modifier = modifier.fillMaxSize().background(background)) {
        FoodBubbles.forEach { bubble ->
            Box(
                modifier =
                    Modifier
                        .offset(x = maxWidth * bubble.x, y = maxHeight * bubble.y)
                        .size(bubble.size)
                        .graphicsLayer { translationY = sin(time * TWO_PI + bubble.phase) * floatPx }
                        .background(Color.White.copy(alpha = BUBBLE_ALPHA), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = bubble.icon,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = BUBBLE_ICON_ALPHA),
                    modifier = Modifier.size(bubble.size * 0.5f),
                )
            }
        }

        Column(
            modifier = Modifier.fillMaxWidth().padding(top = maxHeight * HERO_TOP_FRACTION, start = 64.dp, end = 64.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(HeroPlateSize)
                        .graphicsLayer { translationY = sin(time * TWO_PI) * floatPx * 0.6f }
                        .softShadow(HeroPlateSize, Depth.High, tint = FlowIndigoDeep)
                        .background(Color.White, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier.size(HeroPlateSize - 56.dp).background(KioskColors.softBrush, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.LunchDining,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(232.dp).brushTint(KioskColors.heroBrush),
                    )
                }
            }
            Spacer(modifier = Modifier.height(56.dp))
            Text(
                text = stringResource(R.string.attract_welcome),
                style = MaterialTheme.typography.displayLarge.copy(fontSize = 128.sp, lineHeight = 132.sp),
                color = Color.White,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.attract_tagline),
                style = MaterialTheme.typography.headlineMedium,
                color = FlowIndigoSoft,
                textAlign = TextAlign.Center,
            )
        }

        FlowWaves(
            modifier = Modifier.fillMaxWidth().height(520.dp).align(Alignment.BottomCenter),
            drift = { time },
            frontBrush = WaveFrontBrush,
            backColor = FlowLavender.copy(alpha = 0.45f),
            amplitude = 30.dp,
        )
    }
}

@Composable
private fun AdminUnlockDialog(
    password: String,
    isLoading: Boolean,
    errorMessage: String?,
    onPasswordChanged: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.large,
        icon = { Icon(Icons.Rounded.Lock, contentDescription = null, modifier = Modifier.size(48.dp)) },
        title = { Text(stringResource(R.string.admin_unlock_title), style = MaterialTheme.typography.headlineSmall) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.admin_unlock_message),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = onPasswordChanged,
                    label = { Text(stringResource(R.string.admin_unlock_password)) },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.titleLarge,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { onConfirm() }),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = errorMessage,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(48.dp), strokeWidth = 4.dp)
            } else {
                KioskButton(
                    text = stringResource(R.string.admin_unlock_confirm),
                    onClick = onConfirm,
                    height = 88.dp,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isLoading) {
                Text(stringResource(R.string.btn_cancel), style = MaterialTheme.typography.titleMedium)
            }
        },
    )
}

private const val HEX_COLOR_LENGTH = 7

private fun parseHexColor(
    hex: String?,
    default: Color,
): Color {
    if (hex.isNullOrBlank() || !hex.startsWith("#") || hex.length != HEX_COLOR_LENGTH) return default
    return try {
        val colorInt = android.graphics.Color.parseColor(hex)
        Color(colorInt)
    } catch (_: Exception) {
        default
    }
}
