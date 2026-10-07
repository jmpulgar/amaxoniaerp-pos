package com.amaxonia.kiosk.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BakeryDining
import androidx.compose.material.icons.rounded.BreakfastDining
import androidx.compose.material.icons.rounded.Coffee
import androidx.compose.material.icons.rounded.DinnerDining
import androidx.compose.material.icons.rounded.Fastfood
import androidx.compose.material.icons.rounded.Icecream
import androidx.compose.material.icons.rounded.LocalDrink
import androidx.compose.material.icons.rounded.LocalPizza
import androidx.compose.material.icons.rounded.LunchDining
import androidx.compose.material.icons.rounded.RamenDining
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.SetMeal
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.amaxonia.kiosk.ui.theme.FlowBlue
import com.amaxonia.kiosk.ui.theme.FlowIndigo
import com.amaxonia.kiosk.ui.theme.FlowLavender
import com.amaxonia.kiosk.ui.theme.KioskColors
import com.amaxonia.kiosk.ui.theme.LocalHighContrast
import kotlin.math.PI
import kotlin.math.sin

private const val AMBIENT_ALPHA = 0.06f
private const val BACK_SPEED = 2f
private const val WAVE_STEPS = 48
private const val TWO_PI = (2 * PI).toFloat()

/** Depth levels for [softShadow]: blur radius, vertical offset and key-light opacity. */
enum class Depth(
    val blur: Dp,
    val offsetY: Dp,
    val alpha: Float,
) {
    Low(blur = 8.dp, offsetY = 2.dp, alpha = 0.04f),
    Medium(blur = 16.dp, offsetY = 4.dp, alpha = 0.06f),
    High(blur = 28.dp, offsetY = 8.dp, alpha = 0.10f),
}

/**
 * Soft, layered, indigo-tinted shadow (ambient + key light) drawn with a blurred shadow layer —
 * hardware-accelerated for shapes from API 28. Paints are cached per size, so nothing is
 * allocated per frame. No-op in high-contrast mode, where outlines replace depth.
 */
@Composable
fun Modifier.softShadow(
    cornerRadius: Dp,
    depth: Depth = Depth.Medium,
    tint: Color = KioskColors.shadow,
): Modifier {
    if (LocalHighContrast.current || tint == Color.Transparent) return this
    return drawWithCache {
        val radius = cornerRadius.toPx().coerceAtMost(size.minDimension / 2)
        val ambient =
            Paint().apply {
                color = Color.White
                asFrameworkPaint().setShadowLayer(4.dp.toPx(), 0f, 1.dp.toPx(), tint.copy(alpha = AMBIENT_ALPHA).toArgb())
            }
        val key =
            Paint().apply {
                color = Color.White
                asFrameworkPaint().setShadowLayer(
                    depth.blur.toPx(),
                    0f,
                    depth.offsetY.toPx(),
                    tint.copy(alpha = depth.alpha).toArgb(),
                )
            }
        onDrawBehind {
            drawIntoCanvas { canvas ->
                canvas.drawRoundRect(0f, 0f, size.width, size.height, radius, radius, key)
                canvas.drawRoundRect(0f, 0f, size.width, size.height, radius, radius, ambient)
            }
        }
    }
}

/**
 * Border that is skipped entirely when [width] is 0 (Compose draws a 0 dp border as a hairline,
 * which shows up as a stray dark edge in high-contrast-only outlines).
 */
fun Modifier.outline(
    width: Dp,
    color: Color,
    shape: Shape,
): Modifier = if (width > 0.dp) border(width, color, shape) else this

/** Paints the content (an icon or text) with [brush] instead of its flat color. */
fun Modifier.brushTint(brush: Brush): Modifier =
    this
        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithCache {
            onDrawWithContent {
                drawContent()
                drawRect(brush = brush, blendMode = BlendMode.SrcIn)
            }
        }

private val GlyphBrush = Brush.linearGradient(listOf(FlowIndigo, FlowBlue, FlowLavender))

private val GlyphRules: List<Pair<List<String>, ImageVector>> =
    listOf(
        listOf("combo", "meal") to Icons.Rounded.Fastfood,
        listOf("hamburg", "burger", "pollo", "chicken", "sandw", "crispy") to Icons.Rounded.LunchDining,
        listOf("papa", "fries", "acompa", "side", "aros", "onion", "nugget", "tapa") to Icons.Rounded.DinnerDining,
        listOf("caf", "coffee", "capuch", "latte") to Icons.Rounded.Coffee,
        listOf("bebida", "drink", "refresco", "soda", "limonada", "jugo", "juice", "agua", "water", "batido", "shake") to
            Icons.Rounded.LocalDrink,
        listOf("postre", "dessert", "helado", "ice cream", "sundae", "cono", "cone") to Icons.Rounded.Icecream,
        listOf("pizza") to Icons.Rounded.LocalPizza,
        listOf("desayuno", "breakfast") to Icons.Rounded.BreakfastDining,
        listOf("pan", "bread", "bakery", "dona", "donut") to Icons.Rounded.BakeryDining,
        listOf("sopa", "soup", "ramen") to Icons.Rounded.RamenDining,
        listOf("pescado", "fish", "mariscos", "seafood") to Icons.Rounded.SetMeal,
    )

/**
 * Picks a food glyph from product/category names (ES/EN keywords), used when a product has no
 * photo yet so the menu still reads "food-first". The first name with a match wins.
 */
fun foodGlyphFor(vararg names: String?): ImageVector {
    for (name in names) {
        val lower = name?.lowercase() ?: continue
        GlyphRules.firstOrNull { (keys, _) -> keys.any { lower.contains(it) } }?.let { return it.second }
    }
    return Icons.Rounded.Restaurant
}

/**
 * Illustrated stand-in for a missing product photo: a soft brand wash, a white "plate" disc and a
 * brand-gradient food glyph. Static, so it is cheap to draw in lazy grids.
 */
@Composable
fun FoodArt(
    glyph: ImageVector,
    modifier: Modifier = Modifier,
    glyphSize: Dp = 120.dp,
    plateScale: Float = 1.7f,
    grayscale: Boolean = false,
) {
    val highContrast = LocalHighContrast.current
    Box(modifier = modifier.background(KioskColors.softBrush), contentAlignment = Alignment.Center) {
        Box(
            modifier =
                Modifier
                    .size(glyphSize * plateScale)
                    .background(Color.White.copy(alpha = if (highContrast) 1f else 0.7f), CircleShape),
        )
        Icon(
            imageVector = glyph,
            contentDescription = null,
            tint = if (highContrast || grayscale) MaterialTheme.colorScheme.onSurfaceVariant else Color.Black,
            modifier =
                Modifier
                    .size(glyphSize)
                    .then(if (highContrast || grayscale) Modifier else Modifier.brushTint(GlyphBrush)),
        )
    }
}

/**
 * Layered Flow "waves" (as on the POS welcome screen): a translucent back wave and a brand
 * gradient front wave filling the bottom of the box. [drift] (0..1, read at draw time only) slides
 * the pre-built, seamlessly repeating paths sideways, so animating it allocates nothing per frame.
 */
@Composable
fun FlowWaves(
    modifier: Modifier = Modifier,
    drift: () -> Float = { 0f },
    frontBrush: Brush = KioskColors.heroBrush,
    backColor: Color = FlowLavender.copy(alpha = 0.35f),
    amplitude: Dp = 28.dp,
) {
    Box(
        modifier =
            modifier.drawWithCache {
                val amp = amplitude.toPx()
                val back = wavePath(size.width, size.height, amp, amp * 1.4f, phase = 1.3f)
                val front = wavePath(size.width, size.height, amp, amp * 2.6f, phase = 0f)
                onDrawBehind {
                    val shift = drift()
                    translate(left = -((shift * BACK_SPEED) % 1f) * size.width) { drawPath(back, backColor) }
                    translate(left = -(shift % 1f) * size.width) { drawPath(front, frontBrush) }
                }
            },
    )
}

/** One sine cycle per [width], drawn over two widths so a sideways slide of up to one width is seamless. */
private fun wavePath(
    width: Float,
    height: Float,
    amplitude: Float,
    baseline: Float,
    phase: Float,
): Path =
    Path().apply {
        moveTo(0f, height)
        for (step in 0..WAVE_STEPS * 2) {
            val x = width * step / WAVE_STEPS
            lineTo(x, baseline + amplitude * sin(phase + x / width * TWO_PI))
        }
        lineTo(width * 2, height)
        close()
    }
