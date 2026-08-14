package com.amaxonia.pos.ui.common.components

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp

/**
 * Single-line monetary text that gracefully shrinks its font size when the content would
 * otherwise overflow the available width. Used for heroes such as `Cobrar $XX.XX` and
 * `Total a pagar` so large amounts never wrap or clip on narrow POS displays (320dp class).
 *
 * The component is single-line by contract. Weight and alignment travel inside [baseStyle]
 * (`baseStyle.copy(fontWeight = …, textAlign = …)`); the shrinking is driven by
 * [TextLayoutResult.didOverflowWidth] and state is keyed on text/style so a new amount
 * re-evaluates from the base size.
 */
@Composable
fun AdaptiveAmountText(
    text: String,
    baseStyle: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    minFontSizeSp: Float = 14f,
) {
    val baseFontSize: TextUnit = if (baseStyle.fontSize.isSpecified) baseStyle.fontSize else 16.sp
    val minScale = (minFontSizeSp / baseFontSize.value).coerceIn(MIN_FLOOR, 1f)
    var scale by remember(text, baseStyle, minFontSizeSp) { mutableFloatStateOf(1f) }
    val resolvedColor = if (color.isSpecified) color else LocalContentColor.current
    Text(
        text = text,
        modifier = modifier,
        style = baseStyle.copy(color = resolvedColor),
        fontSize = (baseFontSize.value * scale).sp,
        maxLines = 1,
        softWrap = false,
        overflow = androidx.compose.ui.text.style.TextOverflow.Clip,
        onTextLayout = { result ->
            if (result.didOverflowWidth && scale > minScale) {
                scale = (scale - STEP).coerceAtLeast(minScale)
            }
        },
    )
}

private const val STEP = 0.075f
private const val MIN_FLOOR = 0.4f
