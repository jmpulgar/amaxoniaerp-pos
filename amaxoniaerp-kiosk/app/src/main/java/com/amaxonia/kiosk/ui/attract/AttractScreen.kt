package com.amaxonia.kiosk.ui.attract

import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
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
import com.amaxonia.kiosk.core.media.KioskMediaCache
import com.amaxonia.kiosk.core.network.KioskMediaItem
import kotlinx.coroutines.delay

private const val DEFAULT_IMAGE_DURATION_SEC = 8
private const val SECONDS_TO_MILLIS = 1000L

@Composable
fun AttractScreen(
    viewModel: AttractViewModel,
    onStartOrder: () -> Unit,
    onAdminUnlocked: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Box(
        modifier =
            modifier
                .fillMaxSize()
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
        if (current != null) {
            when (current.type.uppercase()) {
                "VIDEO" -> {
                    VideoAttractPlayer(
                        url = current.url,
                        onMediaEnded = { viewModel.advanceToNextMedia() },
                    )
                }
                "IMAGE" -> {
                    ImageAttractDisplay(
                        mediaItem = current,
                        onDurationExpired = { viewModel.advanceToNextMedia() },
                    )
                }
                else -> {
                    FallbackAttractDisplay(brandColorHex = uiState.brandColor)
                }
            }
        } else {
            FallbackAttractDisplay(brandColorHex = uiState.brandColor)
        }

        // Top Brand Logo with Admin Secret Tap Area
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(top = 48.dp, start = 32.dp, end = 32.dp),
            contentAlignment = Alignment.TopCenter,
        ) {
            Box(
                modifier =
                    Modifier
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) {
                            viewModel.onSecretTap()
                        }
                        .padding(16.dp),
            ) {
                if (!uiState.logoUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = uiState.logoUrl,
                        contentDescription = "Logo",
                        modifier = Modifier.height(64.dp),
                    )
                } else {
                    Text(
                        text = "AMAXONIA",
                        style =
                            MaterialTheme.typography.headlineLarge.copy(
                                fontWeight = FontWeight.Black,
                                letterSpacing = 6.sp,
                                color = Color.White,
                            ),
                    )
                }
            }
        }

        // Offline Notification Banner
        AnimatedVisibility(
            visible = uiState.isOffline,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center),
        ) {
            Card(
                colors =
                    CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.95f),
                    ),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.padding(32.dp),
            ) {
                Column(
                    modifier = Modifier.padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(64.dp),
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Fuera de Servicio",
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Este terminal está fuera de servicio temporalmente.\nPor favor acérquese a la caja principal.",
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }
        }

        // Bottom CTA Overlay
        if (!uiState.isOffline) {
            Box(
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f)),
                            ),
                        )
                        .padding(horizontal = 48.dp, vertical = 64.dp),
                contentAlignment = Alignment.Center,
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(32.dp),
                    shadowElevation = 8.dp,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 48.dp, vertical = 20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.TouchApp,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(36.dp),
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = "TOCA AQUÍ PARA COMENZAR",
                            style =
                                MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = 2.sp,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                ),
                        )
                    }
                }
            }
        }

        // Admin Unlock Dialog
        if (uiState.isAdminDialogOpen) {
            AdminUnlockDialog(
                password = uiState.adminPassword,
                isLoading = uiState.isAdminLoading,
                errorMessage = uiState.adminErrorMessage,
                onPasswordChanged = viewModel::onAdminPasswordChanged,
                onConfirm = { viewModel.submitAdminUnlock(onUnlocked = onAdminUnlocked) },
                onDismiss = viewModel::dismissAdminDialog,
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

@Composable
private fun FallbackAttractDisplay(
    brandColorHex: String?,
    modifier: Modifier = Modifier,
) {
    val brandColor =
        remember(brandColorHex) {
            parseHexColor(brandColorHex, default = Color(0xFF1E88E5))
        }

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(brandColor, Color(0xFF0D47A1), Color.Black),
                    ),
                ),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "¡BIENVENIDO!",
                style =
                    MaterialTheme.typography.displayMedium.copy(
                        fontWeight = FontWeight.Black,
                        letterSpacing = 4.sp,
                        color = Color.White,
                    ),
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Ordene fácil, rápido y sin filas",
                style =
                    MaterialTheme.typography.titleLarge.copy(
                        color = Color.White.copy(alpha = 0.85f),
                    ),
            )
        }
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
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Lock, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Acceso Administrador")
            }
        },
        text = {
            Column {
                Text(
                    text = "Ingrese la contraseña administrativa para configurar o salir del modo kiosco:",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = onPasswordChanged,
                    label = { Text("Contraseña") },
                    singleLine = true,
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
            Button(
                onClick = onConfirm,
                enabled = !isLoading,
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text("Desbloquear")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isLoading) {
                Text("Cancelar")
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
