package com.amaxonia.pos.ui.welcome

import android.content.Intent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import com.amaxonia.pos.R
import com.amaxonia.pos.ui.theme.PosPalette
import kotlin.math.sin

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WelcomeScreen(
    onLoginClick: () -> Unit,
    onRequestAccountClick: () -> Unit,
) {
    val context = LocalContext.current
    // Capas de las olas (cada flavor define sus propios tonos con buen contraste).
    val gradientStart = colorResource(R.color.brand_gradient_start)
    val gradientMid = colorResource(R.color.brand_gradient_mid)
    val gradientEnd = colorResource(R.color.brand_gradient_end)
    val waveBackTop = colorResource(R.color.brand_wave_back_top)
    val waveBackBottom = colorResource(R.color.brand_wave_back_bottom)
    val waveFrontStart = colorResource(R.color.brand_wave_front_start)
    val waveFrontMid = colorResource(R.color.brand_wave_front_mid)
    val waveFrontEnd = colorResource(R.color.brand_wave_front_end)
    val waveScrim = colorResource(R.color.brand_wave_scrim)
    val taglineColor = colorResource(R.color.brand_welcome_tagline)
    val websiteUrl = stringResource(R.string.brand_website_url)
    val supportUrl = stringResource(R.string.brand_support_url)
    var showContactSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()

    // Fade-in sutil
    val contentAlpha = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        contentAlpha.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
        )
    }

    // Animación de ola
    val infiniteTransition = rememberInfiniteTransition(label = "wave")
    val wavePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec =
            infiniteRepeatable(
                animation = tween(durationMillis = 4000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
        label = "wavePhase",
    )

    fun openLink(url: String) {
        context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
    }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(PosPalette.FixedWhite),
    ) {
        // ─── Olas animadas (zona inferior) ───────────────────────────
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val waveY = h * 0.46f // dónde comienza la ola
            val amplitude1 = 26f
            val amplitude2 = 16f

            // Ola trasera (más suave pero con bastante cuerpo para no perder profundidad)
            val backWave =
                Path().apply {
                    moveTo(0f, h)
                    lineTo(0f, waveY + amplitude2)
                    for (x in 0..w.toInt() step 4) {
                        val xf = x.toFloat()
                        val y = waveY + amplitude2 * sin(wavePhase * 0.8f + xf / w * 3 * Math.PI).toFloat()
                        lineTo(xf, y)
                    }
                    lineTo(w, h)
                    close()
                }
            drawPath(
                path = backWave,
                brush =
                    Brush.verticalGradient(
                        colors = listOf(waveBackTop, waveBackBottom),
                        startY = waveY - amplitude2,
                        endY = h,
                    ),
                style = Fill,
            )

            // Ola frontal (principal) con gradiente saturado opaco
            val frontWave =
                Path().apply {
                    moveTo(0f, h)
                    lineTo(0f, waveY)
                    for (x in 0..w.toInt() step 4) {
                        val xf = x.toFloat()
                        val y = waveY + amplitude1 * sin(wavePhase + xf / w * 4 * Math.PI).toFloat()
                        lineTo(xf, y)
                    }
                    lineTo(w, h)
                    close()
                }
            drawPath(
                path = frontWave,
                brush =
                    Brush.linearGradient(
                        colors = listOf(waveFrontStart, waveFrontMid, waveFrontEnd),
                        start = Offset(0f, waveY),
                        end = Offset(w, h),
                    ),
                style = Fill,
            )

            // Scrim inferior para asegurar contraste del texto blanco sobre las olas
            drawRect(
                color = waveScrim,
                topLeft = Offset(0f, waveY),
                size =
                    androidx.compose.ui.geometry
                        .Size(w, h - waveY),
            )
        }

        // Refuerzo de gradiente sutil para anclar la zona de texto al color de marca
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(
                        brush =
                            Brush.verticalGradient(
                                colors =
                                    listOf(
                                        gradientStart.copy(alpha = 0.0f),
                                        gradientStart.copy(alpha = 0.0f),
                                        gradientEnd.copy(alpha = 0.18f),
                                    ),
                            ),
                    ),
        )

        // ─── Contenido ──────────────────────────────────────────────
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .alpha(contentAlpha.value),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // ─── Logo grande sobre fondo blanco ──────────────────────
            Spacer(modifier = Modifier.height(48.dp))

            Image(
                painter = painterResource(id = R.drawable.brand_logo),
                contentDescription = stringResource(R.string.brand_logo_description),
                modifier =
                    Modifier
                        .fillMaxWidth(0.65f)
                        .aspectRatio(2f),
                // mantiene proporción sin estirarse
                contentScale = ContentScale.Fit,
            )

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = stringResource(R.string.brand_welcome_tagline),
                style =
                    MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.Normal,
                        letterSpacing = 0.2.sp,
                    ),
                color = taglineColor,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 40.dp),
            )

            Spacer(modifier = Modifier.weight(1f))

            // ─── Zona de bienvenida (sobre el azul) ──────────────────
            Text(
                text = "Bienvenido",
                style =
                    MaterialTheme.typography.displaySmall.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.5).sp,
                    ),
                color = PosPalette.FixedWhite,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = stringResource(R.string.brand_welcome_message),
                style = MaterialTheme.typography.bodyLarge,
                color = PosPalette.FixedWhite,
                textAlign = TextAlign.Center,
                lineHeight = 22.sp,
                modifier = Modifier.padding(horizontal = 36.dp),
            )

            Spacer(modifier = Modifier.height(32.dp))

            // ─── Botones ─────────────────────────────────────────────
            Column(modifier = Modifier.padding(horizontal = 32.dp)) {
                Button(
                    onClick = onLoginClick,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(54.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = PosPalette.FixedWhite,
                            contentColor = MaterialTheme.colorScheme.primary,
                        ),
                    elevation =
                        ButtonDefaults.buttonElevation(
                            defaultElevation = 6.dp,
                            pressedElevation = 2.dp,
                        ),
                ) {
                    Text(
                        text = "Iniciar sesión",
                        style =
                            MaterialTheme.typography.labelLarge.copy(
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                            ),
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                OutlinedButton(
                    onClick = {
                        onRequestAccountClick()
                        showContactSheet = true
                    },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(54.dp),
                    shape = RoundedCornerShape(14.dp),
                    border =
                        ButtonDefaults.outlinedButtonBorder(enabled = true).copy(
                            width = 1.5.dp,
                            brush =
                                Brush.linearGradient(
                                    colors =
                                        listOf(
                                            PosPalette.FixedWhite,
                                            PosPalette.FixedWhite.copy(alpha = 0.7f),
                                        ),
                                ),
                        ),
                    colors =
                        ButtonDefaults.outlinedButtonColors(
                            containerColor = PosPalette.Transparent,
                            contentColor = PosPalette.FixedWhite,
                        ),
                ) {
                    Text(
                        text = "Solicitar tu cuenta",
                        style =
                            MaterialTheme.typography.labelLarge.copy(
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                            ),
                        color = PosPalette.FixedWhite,
                    )
                }
            }

            Spacer(modifier = Modifier.height(36.dp))
        }

        // ─── Bottom Sheet de contacto ────────────────────────────────
        if (showContactSheet) {
            ModalBottomSheet(
                onDismissRequest = { showContactSheet = false },
                sheetState = sheetState,
                containerColor = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            ) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp)
                            .padding(bottom = 48.dp),
                ) {
                    Text(
                        text = "Solicitar Cuenta",
                        style =
                            MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                            ),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )

                    Text(
                        text = "Elige cómo deseas contactarnos:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 24.dp),
                    )

                    ContactOptionItem(
                        icon = Icons.Default.Language,
                        text = stringResource(R.string.brand_website_label),
                        onClick = {
                            showContactSheet = false
                            openLink(websiteUrl)
                        },
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    ContactOptionItem(
                        icon = Icons.Default.Phone,
                        text = stringResource(R.string.brand_support_label),
                        onClick = {
                            showContactSheet = false
                            openLink(supportUrl)
                        },
                    )
                }
            }
        }
    }
}

// ─── Opción de contacto ─────────────────────────────────────────────
@Composable
fun ContactOptionItem(
    icon: ImageVector,
    text: String,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(56.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable { onClick() }
                .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp),
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = text,
            style =
                MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = FontWeight.Medium,
                ),
            color = MaterialTheme.colorScheme.primary,
        )
    }
}
