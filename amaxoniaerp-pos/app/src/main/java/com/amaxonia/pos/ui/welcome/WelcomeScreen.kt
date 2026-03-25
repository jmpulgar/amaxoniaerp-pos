package com.amaxonia.pos.ui.welcome

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amaxonia.pos.R
import com.amaxonia.pos.ui.theme.AmaxoniaBlue
import kotlin.math.sin

// ─── Paleta ─────────────────────────────────────────────────────────
private val BlueDark = Color(0xFF0D3A8C)
private val BlueLight = Color(0xFF3B7BF7)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WelcomeScreen(
    onLoginClick: () -> Unit,
    onRequestAccountClick: () -> Unit
) {
    val context = LocalContext.current
    var showContactSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()

    // Fade-in sutil
    val contentAlpha = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        contentAlpha.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing)
        )
    }

    // Animación de ola
    val infiniteTransition = rememberInfiniteTransition(label = "wave")
    val wavePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wavePhase"
    )

    fun openLink(url: String) {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        // ─── Olas animadas (zona inferior) ───────────────────────────
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val waveY = h * 0.48f       // dónde comienza la ola
            val amplitude1 = 22f
            val amplitude2 = 14f

            // Ola trasera (más suave)
            val backWave = Path().apply {
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
                brush = Brush.verticalGradient(
                    colors = listOf(BlueLight.copy(alpha = 0.45f), AmaxoniaBlue.copy(alpha = 0.55f)),
                    startY = waveY - amplitude2,
                    endY = h
                ),
                style = Fill
            )

            // Ola frontal (principal)
            val frontWave = Path().apply {
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
                brush = Brush.linearGradient(
                    colors = listOf(BlueDark, AmaxoniaBlue, BlueLight),
                    start = Offset(0f, waveY),
                    end = Offset(w, h)
                ),
                style = Fill
            )
        }

        // ─── Contenido ──────────────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .alpha(contentAlpha.value),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ─── Logo grande sobre fondo blanco ──────────────────────
            Spacer(modifier = Modifier.height(48.dp))

            Image(
                painter = painterResource(id = R.drawable.logo_amaxonia_light),
                contentDescription = "Amaxonia",
                modifier = Modifier
                    .fillMaxWidth(0.65f)
                    .aspectRatio(2f),       // mantiene proporción sin estirarse
                contentScale = ContentScale.Fit
            )

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "Tu punto de venta inteligente",
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = FontWeight.Normal,
                    letterSpacing = 0.2.sp
                ),
                color = Color(0xFF5A6A80),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 40.dp)
            )

            Spacer(modifier = Modifier.weight(1f))

            // ─── Zona de bienvenida (sobre el azul) ──────────────────
            Text(
                text = "Bienvenido",
                style = MaterialTheme.typography.displaySmall.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.5).sp
                ),
                color = Color.White,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Inventario, facturación y reportes\nen la palma de tu mano.",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.80f),
                textAlign = TextAlign.Center,
                lineHeight = 22.sp,
                modifier = Modifier.padding(horizontal = 36.dp)
            )

            Spacer(modifier = Modifier.height(32.dp))

            // ─── Botones ─────────────────────────────────────────────
            Column(modifier = Modifier.padding(horizontal = 32.dp)) {
                Button(
                    onClick = onLoginClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = AmaxoniaBlue
                    ),
                    elevation = ButtonDefaults.buttonElevation(
                        defaultElevation = 6.dp,
                        pressedElevation = 2.dp
                    )
                ) {
                    Text(
                        text = "Iniciar sesión",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                OutlinedButton(
                    onClick = {
                        onRequestAccountClick()
                        showContactSheet = true
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    shape = RoundedCornerShape(14.dp),
                    border = ButtonDefaults.outlinedButtonBorder(enabled = true).copy(
                        width = 1.5.dp,
                        brush = Brush.linearGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.7f),
                                Color.White.copy(alpha = 0.3f)
                            )
                        )
                    ),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = Color.Transparent,
                        contentColor = Color.White
                    )
                ) {
                    Text(
                        text = "Solicitar tu cuenta",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = Color.White.copy(alpha = 0.9f)
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
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .padding(bottom = 48.dp)
                ) {
                    Text(
                        text = "Solicitar Cuenta",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = AmaxoniaBlue,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    Text(
                        text = "Elige cómo deseas contactarnos:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 24.dp)
                    )

                    ContactOptionItem(
                        icon = Icons.Default.Language,
                        text = "Visitar amaxoniaerp.com",
                        onClick = {
                            showContactSheet = false
                            openLink("https://amaxoniaerp.com")
                        }
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    ContactOptionItem(
                        icon = Icons.Default.Phone,
                        text = "Escribir por WhatsApp",
                        onClick = {
                            showContactSheet = false
                            openLink("https://wa.me/50764188582")
                        }
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
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable { onClick() }
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = AmaxoniaBlue,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge.copy(
                fontWeight = FontWeight.Medium
            ),
            color = AmaxoniaBlue
        )
    }
}
