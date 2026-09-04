@file:Suppress("MagicNumber")

package com.amaxonia.pos.ui.payment

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** Fracciones genéricas de progreso usadas por todas las animaciones de celebración. */
private const val ANIMATION_START_FRACTION = 0f
private const val ANIMATION_END_FRACTION = 1f

/** Dimensiones del héroe de celebración. */
private const val HERO_HEIGHT_DP = 176
private const val HERO_CANVAS_SIZE_DP = 176

/** Tiempos y retardos de animación. */
private const val HALO_ENTRANCE_MS = 600
private const val DISC_SPRING_STIFFNESS = Spring.StiffnessMediumLow
private const val DISC_SPRING_DAMPING = Spring.DampingRatioMediumBouncy
private const val CHECK_DRAW_DELAY_MS = 280L
private const val CHECK_DRAW_DURATION_MS = 360

/** Partículas de confeti en la explosión de celebración. */
private const val CONFETTI_PARTICLE_COUNT = 16
private const val CONFETTI_RANDOM_SEED = 20260825L
private const val CONFETTI_START_DELAY_MS = 100L
private const val CONFETTI_DURATION_MS = 950

/**
 * Héroe visual de éxito de pago inspirado en la interfaz de referencia:
 * Halos concéntricos azulados con respiración suave, disco azul real con rebote elástico,
 * ilustración de teléfono con badge de check animado, estrellas centelleantes y partículas festivas.
 */
@Composable
internal fun CelebrationHero(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "HeroAmbientBreathing")
    val breathingPulse by infiniteTransition.animateFloat(
        initialValue = 0.985f,
        targetValue = 1.025f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(durationMillis = 1800, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
        label = "BreathingPulse",
    )
    val sparkleTwinkle by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(durationMillis = 1400, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
        label = "SparkleTwinkle",
    )

    // Animación de entrada
    val entranceProgress = remember { Animatable(ANIMATION_START_FRACTION) }
    val discScale = remember { Animatable(ANIMATION_START_FRACTION) }
    val checkProgress = remember { Animatable(ANIMATION_START_FRACTION) }

    LaunchedEffect(Unit) {
        launch {
            entranceProgress.animateTo(
                targetValue = ANIMATION_END_FRACTION,
                animationSpec = tween(durationMillis = HALO_ENTRANCE_MS, easing = FastOutSlowInEasing),
            )
        }
        launch {
            discScale.animateTo(
                targetValue = ANIMATION_END_FRACTION,
                animationSpec = spring(dampingRatio = DISC_SPRING_DAMPING, stiffness = DISC_SPRING_STIFFNESS),
            )
        }
        launch {
            delay(CHECK_DRAW_DELAY_MS)
            checkProgress.animateTo(
                targetValue = ANIMATION_END_FRACTION,
                animationSpec = tween(durationMillis = CHECK_DRAW_DURATION_MS, easing = FastOutSlowInEasing),
            )
        }
    }

    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .height(HERO_HEIGHT_DP.dp),
        contentAlignment = Alignment.Center,
    ) {
        ConfettiBurst(modifier = Modifier.size(HERO_CANVAS_SIZE_DP.dp))

        Canvas(
            modifier =
                Modifier
                    .size(HERO_CANVAS_SIZE_DP.dp)
                    .graphicsLayer {
                        alpha = entranceProgress.value
                    },
        ) {
            val cx = size.width / 2f
            val cy = size.height / 2f

            // 1. Halo exterior más suave
            val outerHaloRadius = 80.dp.toPx() * entranceProgress.value * breathingPulse
            drawCircle(
                color = Color(0xFFEBF3FE),
                radius = outerHaloRadius,
                center = Offset(cx, cy),
            )

            // 2. Halo interior suave
            val midHaloRadius = 64.dp.toPx() * entranceProgress.value * (2f - breathingPulse)
            drawCircle(
                color = Color(0xFFD6E6FD),
                radius = midHaloRadius,
                center = Offset(cx, cy),
            )

            // 3. Disco central azul real con resorte
            val discRadius = 48.dp.toPx() * discScale.value
            if (discRadius > 1f) {
                // Sombra suave bajo el disco
                drawCircle(
                    color = Color(0xFF1664EA).copy(alpha = 0.25f),
                    radius = discRadius + 3.dp.toPx(),
                    center = Offset(cx, cy + 2.dp.toPx()),
                )

                // Círculo principal azul
                drawCircle(
                    color = Color(0xFF1664EA),
                    radius = discRadius,
                    center = Offset(cx, cy),
                )

                // Contenido dentro del disco azul (recortado)
                val clipCirclePath =
                    Path().apply {
                        addOval(
                            androidx.compose.ui.geometry.Rect(
                                center = Offset(cx, cy),
                                radius = discRadius,
                            ),
                        )
                    }

                clipPath(clipCirclePath) {
                    // Líneas de fondo decorativas dentro del disco azul
                    val wavePath =
                        Path().apply {
                            moveTo(cx - discRadius, cy + discRadius * 0.4f)
                            cubicTo(
                                cx - discRadius * 0.4f,
                                cy + discRadius * 0.1f,
                                cx + discRadius * 0.2f,
                                cy + discRadius * 0.7f,
                                cx + discRadius,
                                cy + discRadius * 0.35f,
                            )
                            lineTo(cx + discRadius, cy + discRadius)
                            lineTo(cx - discRadius, cy + discRadius)
                            close()
                        }
                    drawPath(
                        path = wavePath,
                        color = Color.White.copy(alpha = 0.12f),
                    )

                    // Estrellas decorativas dentro del disco
                    drawSparkleStar(
                        cx = cx - 24.dp.toPx(),
                        cy = cy - 24.dp.toPx(),
                        radius = 4.dp.toPx(),
                        color = Color.White.copy(alpha = 0.75f * sparkleTwinkle),
                    )
                    drawSparkleStar(
                        cx = cx + 26.dp.toPx(),
                        cy = cy - 18.dp.toPx(),
                        radius = 3.2.dp.toPx(),
                        color = Color.White.copy(alpha = 0.65f * (1.4f - sparkleTwinkle)),
                    )
                    drawSparkleStar(
                        cx = cx + 22.dp.toPx(),
                        cy = cy + 22.dp.toPx(),
                        radius = 3.dp.toPx(),
                        color = Color.White.copy(alpha = 0.6f * sparkleTwinkle),
                    )
                    drawSparkleStar(
                        cx = cx - 22.dp.toPx(),
                        cy = cy + 20.dp.toPx(),
                        radius = 2.5.dp.toPx(),
                        color = Color.White.copy(alpha = 0.5f),
                    )

                    // Puntos brillantes flotantes
                    drawCircle(
                        color = Color.White.copy(alpha = 0.6f),
                        radius = 1.5.dp.toPx(),
                        center = Offset(cx - 32.dp.toPx(), cy - 6.dp.toPx()),
                    )
                    drawCircle(
                        color = Color.White.copy(alpha = 0.6f),
                        radius = 1.5.dp.toPx(),
                        center = Offset(cx + 32.dp.toPx(), cy + 6.dp.toPx()),
                    )

                    // 4. Ilustración del teléfono móvil (blanco con pantalla y badge de check)
                    val phoneWidth = 36.dp.toPx() * discScale.value
                    val phoneHeight = 56.dp.toPx() * discScale.value
                    val phoneCorner = 7.dp.toPx() * discScale.value

                    val phoneLeft = cx - phoneWidth / 2f
                    val phoneTop = cy - phoneHeight / 2f

                    // Sombra sutil del teléfono
                    drawRoundRect(
                        color = Color(0x33000000),
                        topLeft = Offset(phoneLeft, phoneTop + 2.dp.toPx()),
                        size = Size(phoneWidth, phoneHeight),
                        cornerRadius = CornerRadius(phoneCorner, phoneCorner),
                    )

                    // Cuerpo del teléfono
                    drawRoundRect(
                        color = Color.White,
                        topLeft = Offset(phoneLeft, phoneTop),
                        size = Size(phoneWidth, phoneHeight),
                        cornerRadius = CornerRadius(phoneCorner, phoneCorner),
                    )

                    // Altavoz/Notch superior
                    val notchWidth = 8.dp.toPx() * discScale.value
                    val notchHeight = 1.8.dp.toPx() * discScale.value
                    drawRoundRect(
                        color = Color(0xFFD2DCEE),
                        topLeft = Offset(cx - notchWidth / 2f, phoneTop + 3.dp.toPx()),
                        size = Size(notchWidth, notchHeight),
                        cornerRadius = CornerRadius(notchHeight / 2f, notchHeight / 2f),
                    )

                    // Pantalla del teléfono
                    val screenPaddingX = 3.dp.toPx() * discScale.value
                    val screenPaddingTop = 7.dp.toPx() * discScale.value
                    val screenPaddingBottom = 4.dp.toPx() * discScale.value
                    val screenWidth = phoneWidth - screenPaddingX * 2f
                    val screenHeight = phoneHeight - screenPaddingTop - screenPaddingBottom
                    val screenCorner = 4.dp.toPx() * discScale.value

                    drawRoundRect(
                        color = Color(0xFFF1F6FE),
                        topLeft = Offset(phoneLeft + screenPaddingX, phoneTop + screenPaddingTop),
                        size = Size(screenWidth, screenHeight),
                        cornerRadius = CornerRadius(screenCorner, screenCorner),
                    )

                    // Badge circular azul de confirmación en la pantalla
                    val badgeRadius = 9.5.dp.toPx() * discScale.value
                    val badgeCenterY = cy + 2.dp.toPx()
                    drawCircle(
                        color = Color(0xFF1664EA),
                        radius = badgeRadius,
                        center = Offset(cx, badgeCenterY),
                    )

                    // 5. Checkmark animado dentro del badge
                    if (checkProgress.value > 0f) {
                        val checkPath =
                            Path().apply {
                                moveTo(cx - 4.2.dp.toPx(), badgeCenterY + 0.6.dp.toPx())
                                lineTo(cx - 1.2.dp.toPx(), badgeCenterY + 3.6.dp.toPx())
                                lineTo(cx + 4.8.dp.toPx(), badgeCenterY - 2.8.dp.toPx())
                            }

                        val measurer = PathMeasure().apply { setPath(checkPath, forceClosed = false) }
                        val visibleSegment = Path()
                        measurer.getSegment(
                            startDistance = 0f,
                            stopDistance = measurer.length * checkProgress.value,
                            destination = visibleSegment,
                            startWithMoveTo = true,
                        )

                        drawPath(
                            path = visibleSegment,
                            color = Color.White,
                            style =
                                Stroke(
                                    width = 2.2.dp.toPx(),
                                    cap = StrokeCap.Round,
                                    join = StrokeJoin.Round,
                                ),
                        )
                    }
                }
            }
        }
    }
}

/** Dibuja una estrella de 4 puntas centelleante estilo chispa mágica. */
private fun DrawScope.drawSparkleStar(
    cx: Float,
    cy: Float,
    radius: Float,
    color: Color,
    rotationDegrees: Float = 0f,
) {
    if (radius <= 0f) return
    val innerRadius = radius * 0.28f
    val starPath =
        Path().apply {
            moveTo(cx, cy - radius)
            quadraticTo(cx, cy - innerRadius, cx + radius, cy)
            quadraticTo(cx + innerRadius, cy, cx, cy + radius)
            quadraticTo(cx, cy + innerRadius, cx - radius, cy)
            quadraticTo(cx - innerRadius, cy, cx, cy - radius)
            close()
        }
    rotate(rotationDegrees, pivot = Offset(cx, cy)) {
        drawPath(path = starPath, color = color)
    }
}

private data class ConfettiParticle(
    val angleRadians: Float,
    val travelFraction: Float,
    val sizeDp: Float,
    val color: Color,
    val delayFraction: Float,
)

/** Partículas determinísticas para una explosión radial limpia y festiva. */
private fun buildCelebrationParticles(): List<ConfettiParticle> {
    val random = Random(CONFETTI_RANDOM_SEED)
    val palette =
        listOf(
            Color(0xFF3B82F6), // Royal Blue
            Color(0xFF60A5FA), // Light Blue
            Color(0xFF10B981), // Emerald Green
            Color(0xFFF59E0B), // Amber Gold
            Color(0xFF8B5CF6), // Purple
            Color(0xFFFFFFFF), // Crisp White
        )

    return List(CONFETTI_PARTICLE_COUNT) { index ->
        val angleDegrees = (360f / CONFETTI_PARTICLE_COUNT) * index + random.nextFloat() * 10f
        ConfettiParticle(
            angleRadians = Math.toRadians(angleDegrees.toDouble()).toFloat(),
            travelFraction = 0.55f + random.nextFloat() * 0.35f,
            sizeDp = 3f + random.nextFloat() * 3.5f,
            color = palette[index % palette.size],
            delayFraction = random.nextFloat() * 0.2f,
        )
    }
}

@Composable
private fun ConfettiBurst(modifier: Modifier = Modifier) {
    val progress = remember { Animatable(ANIMATION_START_FRACTION) }
    LaunchedEffect(Unit) {
        delay(CONFETTI_START_DELAY_MS)
        progress.animateTo(
            targetValue = ANIMATION_END_FRACTION,
            animationSpec = tween(durationMillis = CONFETTI_DURATION_MS, easing = LinearEasing),
        )
    }
    val particles = remember { buildCelebrationParticles() }

    Canvas(modifier = modifier) {
        val maxRadius = size.minDimension / 2f
        particles.forEach { particle ->
            val localProgress =
                ((progress.value - particle.delayFraction) / (ANIMATION_END_FRACTION - particle.delayFraction))
                    .coerceIn(ANIMATION_START_FRACTION, ANIMATION_END_FRACTION)
            if (localProgress > ANIMATION_START_FRACTION && localProgress < ANIMATION_END_FRACTION) {
                val distance = maxRadius * particle.travelFraction * localProgress
                val particleCenter =
                    Offset(
                        x = center.x + (cos(particle.angleRadians) * distance),
                        y = center.y + (sin(particle.angleRadians) * distance),
                    )
                val alpha = (1f - localProgress).coerceIn(0f, 1f)
                drawCircle(
                    color = particle.color.copy(alpha = alpha),
                    radius = (particle.sizeDp.dp.toPx() / 2f) * (1f - localProgress * 0.5f),
                    center = particleCenter,
                )
            }
        }
    }
}
