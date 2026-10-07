package com.amaxonia.kiosk.ui.attract

import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.material.icons.rounded.SettingsSuggest
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.WifiOff
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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
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
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.amaxonia.kiosk.R
import com.amaxonia.kiosk.core.media.KioskImages
import com.amaxonia.kiosk.core.media.KioskMediaCache
import com.amaxonia.kiosk.core.network.KioskMediaItem
import com.amaxonia.kiosk.ui.accessibility.KioskLanguage
import com.amaxonia.kiosk.ui.components.BrandWordmark
import com.amaxonia.kiosk.ui.components.Depth
import com.amaxonia.kiosk.ui.components.FlowWaves
import com.amaxonia.kiosk.ui.components.KioskButton
import com.amaxonia.kiosk.ui.components.brushTint
import com.amaxonia.kiosk.ui.components.kioskPressable
import com.amaxonia.kiosk.ui.components.softShadow
import com.amaxonia.kiosk.ui.theme.FlowError
import com.amaxonia.kiosk.ui.theme.FlowGradient
import com.amaxonia.kiosk.ui.theme.FlowIndigo
import com.amaxonia.kiosk.ui.theme.FlowIndigoDeep
import com.amaxonia.kiosk.ui.theme.FlowIndigoSoft
import com.amaxonia.kiosk.ui.theme.FlowLavender
import com.amaxonia.kiosk.ui.theme.KioskColors
import com.amaxonia.kiosk.ui.theme.KioskDialog
import com.amaxonia.kiosk.ui.theme.LocalHighContrast
import com.amaxonia.kiosk.ui.theme.LocalKioskCanvas
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.sin

/** Banners are vertical posters, 2:3 (e.g. 1024 x 1536 or 1080 x 1620). */
private const val POSTER_ASPECT = 2f / 3f
private const val DEFAULT_IMAGE_DURATION_SEC = 8
private const val BANNER_FADE_MS = 450
private const val FAILED_BANNER_RETRY_MS = 1_500L
private const val SECONDS_TO_MILLIS = 1000L
private const val TAP_HINT_PERIOD_MS = 700
private const val ART_LOOP_MS = 6_000
private const val TWO_PI = (2 * PI).toFloat()
private const val BUBBLE_ALPHA = 0.14f
private const val BUBBLE_ICON_ALPHA = 0.85f
private const val HERO_TOP_FRACTION = 0.23f
private val BUBBLE_FLOAT = 14.dp
private val HeroPlateSize = 420.dp
private val LandscapePlateSize = 340.dp
private const val LANDSCAPE_HERO_TOP_FRACTION = 0.24f
private const val PLATE_GLYPH_FRACTION = 232f / 420f
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
    val landscape = LocalKioskCanvas.current.isLandscape
    val startIfOnline = { if (!uiState.isOffline) onStartOrder() }
    val poster =
        @Composable { posterModifier: Modifier ->
            // The promo poster (ERP banners) is itself a giant "touch to order" target.
            Box(
                modifier =
                    posterModifier
                        .clipToBounds()
                        .background(MaterialTheme.colorScheme.surface)
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = startIfOnline),
            ) {
                AttractPoster(uiState = uiState, onMediaFinished = onMediaFinished)
            }
        }
    val panel =
        @Composable { panelModifier: Modifier ->
            AttractStartPanel(
                isOffline = uiState.isOffline,
                logoUrl = uiState.logoUrl,
                onStartOrder = startIfOnline,
                onSecretTap = onSecretTap,
                language = language,
                onLanguageSelected = onLanguageSelected,
                landscape = landscape,
                modifier = panelModifier,
            )
        }

    Box(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        if (landscape) {
            // Landscape: the vertical poster on the left, the start panel on the right.
            Row(modifier = Modifier.fillMaxSize()) {
                poster(Modifier.fillMaxHeight().aspectRatio(POSTER_ASPECT))
                panel(Modifier.weight(1f).fillMaxHeight())
            }
        } else {
            // Portrait: a 2:3 poster across the top and the white start panel underneath.
            Column(modifier = Modifier.fillMaxSize()) {
                poster(Modifier.fillMaxWidth().aspectRatio(POSTER_ASPECT))
                panel(Modifier.weight(1f).fillMaxWidth())
            }
        }

        AnimatedVisibility(
            visible = uiState.isOffline,
            enter = fadeIn() + scaleIn(initialScale = 0.9f),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center),
        ) {
            OfflineBanner(message = uiState.outOfServiceMessage)
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

/** The ERP banners (images or videos) in a loop, or the branded poster when none are configured. */
@Composable
private fun AttractPoster(
    uiState: AttractUiState,
    onMediaFinished: () -> Unit,
) {
    val current = uiState.currentMedia
    when {
        current != null && (current.isVideo || current.isImage) ->
            BannerCarousel(
                media = current,
                mediaIndex = uiState.currentMediaIndex,
                loopSingle = uiState.mediaList.size == 1,
                onMediaFinished = onMediaFinished,
            )
        // First config load in progress: keep the plain surface instead of flashing the fallback art.
        uiState.isLoading -> Unit
        else -> FallbackAttractDisplay(brandColorHex = uiState.brandColor)
    }
}

private val KioskMediaItem.isVideo: Boolean get() = type.equals("VIDEO", ignoreCase = true)
private val KioskMediaItem.isImage: Boolean get() = type.equals("IMAGE", ignoreCase = true)

private fun KioskMediaItem.displayMillis(): Long = (if (durationSec > 0) durationSec else DEFAULT_IMAGE_DURATION_SEC) * SECONDS_TO_MILLIS

/** One banner of the carousel stack; it becomes visible only once its first frame is ready. */
private class BannerLayer(
    val id: Long,
    val mediaIndex: Int,
    val media: KioskMediaItem,
) {
    val alpha = Animatable(0f)
    var ready by mutableStateOf(false)
    var failed by mutableStateOf(false)
}

/**
 * Seamless banner loop: the next banner is composed on top, invisible, and only fades in once it
 * has actually loaded (image decoded / first video frame rendered). The previous banner stays on
 * screen underneath until then and is dropped afterwards, so changing banner never shows an empty
 * (black) frame. An image's display time starts when it becomes visible.
 */
@Composable
private fun BannerCarousel(
    media: KioskMediaItem,
    mediaIndex: Int,
    loopSingle: Boolean,
    onMediaFinished: () -> Unit,
) {
    val layers = remember { mutableStateListOf<BannerLayer>() }
    var nextId by remember { mutableLongStateOf(0L) }
    val finished by rememberUpdatedState(onMediaFinished)

    LaunchedEffect(mediaIndex, media) {
        val top = layers.lastOrNull()
        if (top == null || top.mediaIndex != mediaIndex || top.media != media) {
            layers += BannerLayer(id = nextId++, mediaIndex = mediaIndex, media = media)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        layers.forEach { layer ->
            key(layer.id) {
                BannerLayerEffects(layer = layer, layers = layers, onFinished = { finished() })
                val isTop = layer === layers.lastOrNull()
                Box(modifier = Modifier.fillMaxSize().graphicsLayer { alpha = layer.alpha.value }) {
                    if (layer.media.isVideo) {
                        VideoAttractPlayer(
                            url = layer.media.url,
                            loop = loopSingle,
                            onFirstFrame = { layer.ready = true },
                            onError = { layer.failed = true },
                            onMediaEnded = { if (isTop) finished() },
                        )
                    } else {
                        ImageAttractDisplay(
                            url = layer.media.url,
                            onLoaded = { layer.ready = true },
                            onError = { layer.failed = true },
                        )
                    }
                }
            }
        }
    }
}

/** Fades [layer] in once ready, drops the banners under it, then runs an image's display time. */
@Composable
private fun BannerLayerEffects(
    layer: BannerLayer,
    layers: SnapshotStateList<BannerLayer>,
    onFinished: () -> Unit,
) {
    LaunchedEffect(layer.ready, layer.failed) {
        when {
            layer.ready -> {
                // The very first banner appears at once; later ones cross-fade over the previous one.
                if (layers.firstOrNull() === layer) {
                    layer.alpha.snapTo(1f)
                } else {
                    layer.alpha.animateTo(1f, tween(BANNER_FADE_MS, easing = LinearOutSlowInEasing))
                }
                repeat(layers.indexOf(layer).coerceAtLeast(0)) { layers.removeAt(0) }
                if (layer.media.isImage) {
                    delay(layer.media.displayMillis())
                    if (layers.lastOrNull() === layer) onFinished()
                }
            }
            layer.failed -> {
                // Unreachable banner (offline, deleted): it stays invisible, the previous banner keeps
                // showing, and the loop moves on to the next one.
                delay(FAILED_BANNER_RETRY_MS)
                if (layers.lastOrNull() === layer) onFinished()
            }
        }
    }
}

/**
 * White panel under the poster: the brand logo (5 quick taps open the admin unlock dialog), the big
 * "start a new order" button and the language choice.
 */
@Composable
private fun AttractStartPanel(
    isOffline: Boolean,
    logoUrl: String?,
    onStartOrder: () -> Unit,
    onSecretTap: () -> Unit,
    language: KioskLanguage?,
    onLanguageSelected: ((KioskLanguage) -> Unit)?,
    landscape: Boolean,
    modifier: Modifier = Modifier,
) {
    val logo =
        @Composable { height: Dp ->
            Box(
                modifier =
                    Modifier
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onSecretTap)
                        .padding(8.dp),
            ) {
                BrandWordmark(logoUrl = logoUrl, height = height)
            }
        }
    val languages =
        @Composable {
            if (onLanguageSelected != null) {
                LanguagePills(selected = language, onSelected = onLanguageSelected)
            }
        }
    if (landscape) {
        Column(
            modifier = modifier.padding(horizontal = 64.dp, vertical = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            logo(140.dp)
            Spacer(Modifier.height(40.dp))
            WelcomeTexts(textAlign = TextAlign.Center, onDark = false)
            Spacer(Modifier.height(56.dp))
            if (!isOffline) StartOrderButton(onClick = onStartOrder, modifier = Modifier.fillMaxWidth().height(200.dp))
            Spacer(Modifier.height(32.dp))
            languages()
        }
    } else {
        Column(
            modifier = modifier.padding(horizontal = 32.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().height(176.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                logo(96.dp)
                Spacer(Modifier.width(24.dp))
                if (!isOffline) StartOrderButton(onClick = onStartOrder, modifier = Modifier.weight(1f).fillMaxHeight())
            }
            Spacer(Modifier.height(14.dp))
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { languages() }
        }
    }
}

/** The big brand button: "Iniciar una nueva orden" with a tapping hand that nudges the customer. */
@Composable
private fun StartOrderButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "tap_hint")
    val tapOffset by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(TAP_HINT_PERIOD_MS, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "tap_offset",
    )
    val tapTravelPx = with(LocalDensity.current) { 12.dp.toPx() }
    val highContrast = LocalHighContrast.current
    val colors = MaterialTheme.colorScheme

    Surface(
        modifier = modifier.kioskPressable(pressedScale = 0.98f, onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = colors.primary,
        contentColor = colors.onPrimary,
        border = if (highContrast) BorderStroke(4.dp, Color.Black) else null,
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 36.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.attract_start_order),
                    style = MaterialTheme.typography.headlineLarge,
                    maxLines = 2,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.attract_touch_to_start),
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.onPrimary.copy(alpha = 0.85f),
                    maxLines = 1,
                )
            }
            Spacer(Modifier.width(24.dp))
            Icon(
                imageVector = Icons.Rounded.TouchApp,
                contentDescription = null,
                modifier = Modifier.size(96.dp).graphicsLayer { translationY = tapOffset * tapTravelPx },
            )
        }
    }
}

@Composable
private fun LanguagePills(
    selected: KioskLanguage?,
    onSelected: (KioskLanguage) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
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
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.kioskPressable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        color = colors.surface,
        contentColor = colors.onSurface,
        border = BorderStroke(if (selected) 4.dp else 2.dp, if (selected) colors.secondary else colors.outlineVariant),
    ) {
        Row(
            modifier = Modifier.height(72.dp).width(240.dp).padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Text(text = flag, fontSize = 32.sp)
            Spacer(Modifier.width(14.dp))
            Text(text = label, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun OfflineBanner(message: String?) {
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
                Icon(
                    imageVector = if (message != null) Icons.Rounded.SettingsSuggest else Icons.Rounded.WifiOff,
                    contentDescription = null,
                    tint = FlowError,
                    modifier = Modifier.size(80.dp),
                )
            }
            Spacer(Modifier.height(32.dp))
            Text(
                text = stringResource(R.string.attract_out_of_service_title),
                style = MaterialTheme.typography.displaySmall,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = message ?: stringResource(R.string.attract_out_of_service),
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
    loop: Boolean,
    onFirstFrame: () -> Unit,
    onError: () -> Unit,
    onMediaEnded: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val firstFrame by rememberUpdatedState(onFirstFrame)
    val failed by rememberUpdatedState(onError)
    val ended by rememberUpdatedState(onMediaEnded)
    val exoPlayer =
        remember(url) {
            // Plays from the disk cache filled by the media preloader (network only on a cache miss).
            val cacheFactory = KioskMediaCache.getCacheDataSourceFactory(context)
            val mediaSourceFactory = DefaultMediaSourceFactory(cacheFactory)

            ExoPlayer.Builder(context)
                .setMediaSourceFactory(mediaSourceFactory)
                .build()
                .apply {
                    volume = 0f // Silent attract loop
                    addListener(
                        object : Player.Listener {
                            override fun onRenderedFirstFrame() {
                                firstFrame()
                            }

                            override fun onPlayerError(error: PlaybackException) {
                                failed()
                            }

                            override fun onPlaybackStateChanged(playbackState: Int) {
                                if (playbackState == Player.STATE_ENDED) ended()
                            }
                        },
                    )
                    setMediaItem(MediaItem.fromUri(url))
                    prepare()
                    playWhenReady = true
                }
        }
    // A single video banner loops forever; with several, the carousel moves on when it ends.
    LaunchedEffect(exoPlayer, loop) {
        exoPlayer.repeatMode = if (loop) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
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
                // No black shutter: the layer stays invisible until the first frame is rendered.
                setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                setKeepContentOnPlayerReset(true)
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
    url: String,
    onLoaded: () -> Unit,
    onError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    // Same request as the preloader: a preloaded banner is a memory-cache hit on the first frame.
    val request = remember(url) { KioskImages.bannerRequest(context, url) }
    AsyncImage(
        model = request,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        onSuccess = { onLoaded() },
        onError = { onError() },
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

/** Landscape: bubbles stay in the side margins, clear of the hero row and the logo. */
private val LandscapeFoodBubbles =
    listOf(
        FoodBubble(Icons.Rounded.LocalDrink, x = 0.04f, y = 0.12f, size = 180.dp, phase = 0f),
        FoodBubble(Icons.Rounded.Icecream, x = 0.86f, y = 0.10f, size = 196.dp, phase = 1.7f),
        FoodBubble(Icons.Rounded.LocalPizza, x = 0.89f, y = 0.44f, size = 150.dp, phase = 3.1f),
        FoodBubble(Icons.Rounded.Coffee, x = 0.03f, y = 0.47f, size = 140.dp, phase = 4.4f),
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
        val landscape = maxWidth > maxHeight
        (if (landscape) LandscapeFoodBubbles else FoodBubbles).forEach { bubble ->
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

        val plateFloat = { sin(time * TWO_PI) * floatPx * 0.6f }
        if (landscape) {
            // Side by side: plate on the left, welcome text on the right, above the CTA band.
            Row(
                modifier =
                    Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = maxHeight * LANDSCAPE_HERO_TOP_FRACTION, start = 64.dp, end = 64.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                HeroPlate(size = LandscapePlateSize, floatY = plateFloat)
                Spacer(modifier = Modifier.width(72.dp))
                Column {
                    WelcomeTexts(textAlign = TextAlign.Start)
                }
            }
        } else {
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = maxHeight * HERO_TOP_FRACTION, start = 64.dp, end = 64.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                HeroPlate(size = HeroPlateSize, floatY = plateFloat)
                Spacer(modifier = Modifier.height(56.dp))
                WelcomeTexts(textAlign = TextAlign.Center)
            }
        }

        FlowWaves(
            modifier = Modifier.fillMaxWidth().height(if (landscape) 380.dp else 520.dp).align(Alignment.BottomCenter),
            drift = { time },
            frontBrush = WaveFrontBrush,
            backColor = FlowLavender.copy(alpha = 0.45f),
            amplitude = 30.dp,
        )
    }
}

/** White hero "plate" with the burger artwork, gently floating by [floatY] pixels. */
@Composable
private fun HeroPlate(
    size: Dp,
    floatY: () -> Float,
) {
    Box(
        modifier =
            Modifier
                .size(size)
                .graphicsLayer { translationY = floatY() }
                .softShadow(size, Depth.High, tint = FlowIndigoDeep)
                .background(Color.White, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier.size(size - 56.dp).background(KioskColors.softBrush, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.LunchDining,
                contentDescription = null,
                tint = Color.Black,
                modifier = Modifier.size(size * PLATE_GLYPH_FRACTION).brushTint(KioskColors.heroBrush),
            )
        }
    }
}

@Composable
private fun WelcomeTexts(
    textAlign: TextAlign,
    onDark: Boolean = true,
) {
    Text(
        text = stringResource(R.string.attract_welcome),
        style = MaterialTheme.typography.displayLarge.copy(fontSize = 128.sp, lineHeight = 132.sp),
        color = if (onDark) Color.White else MaterialTheme.colorScheme.primary,
        textAlign = textAlign,
    )
    Spacer(modifier = Modifier.height(16.dp))
    Text(
        text = stringResource(R.string.attract_tagline),
        style = MaterialTheme.typography.headlineMedium,
        color = if (onDark) FlowIndigoSoft else MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = textAlign,
    )
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
    val colors = MaterialTheme.colorScheme
    KioskDialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.widthIn(max = 760.dp).padding(horizontal = 48.dp),
            shape = MaterialTheme.shapes.large,
            color = colors.surface,
            border = if (LocalHighContrast.current) BorderStroke(4.dp, colors.onSurface) else null,
            shadowElevation = 16.dp,
        ) {
            Column(modifier = Modifier.padding(40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Rounded.Lock, contentDescription = null, tint = colors.secondary, modifier = Modifier.size(48.dp))
                Spacer(modifier = Modifier.height(16.dp))
                Text(stringResource(R.string.admin_unlock_title), style = MaterialTheme.typography.headlineSmall)
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.admin_unlock_message),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
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
                        color = colors.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Spacer(modifier = Modifier.height(24.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onDismiss, enabled = !isLoading) {
                        Text(stringResource(R.string.btn_cancel), style = MaterialTheme.typography.titleMedium)
                    }
                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(48.dp), strokeWidth = 4.dp)
                    } else {
                        KioskButton(
                            text = stringResource(R.string.admin_unlock_confirm),
                            onClick = onConfirm,
                            height = 88.dp,
                        )
                    }
                }
            }
        }
    }
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
