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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp

/**
 * Monetary text that gracefully shrinks its font size when the content would otherwise
 * overflow the available width. Styling can be supplied either through [baseStyle] or the
 * optional text overrides, which keeps call sites concise while preserving one reusable
 * implementation for narrow POS displays.
 */
@Composable
fun AdaptiveAmountText(
    text: String,
    baseStyle: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    minFontSizeSp: Float = 14f,
    fontWeight: FontWeight? = null,
    textAlign: TextAlign? = null,
    maxLines: Int = 1,
) {
    val baseFontSize: TextUnit = if (baseStyle.fontSize.isSpecified) baseStyle.fontSize else 16.sp
    val resolvedStyle =
        baseStyle.copy(
            color = if (color.isSpecified) color else LocalContentColor.current,
            fontWeight = fontWeight ?: baseStyle.fontWeight,
            textAlign = textAlign ?: baseStyle.textAlign,
        )
    val minScale = (minFontSizeSp / baseFontSize.value).coerceIn(MIN_FLOOR, 1f)
    var scale by remember(text, resolvedStyle, minFontSizeSp, maxLines) { mutableFloatStateOf(1f) }

    Text(
        text = text,
        modifier = modifier,
        style = resolvedStyle,
        fontSize = (baseFontSize.value * scale).sp,
        maxLines = maxLines,
        softWrap = false,
        overflow = TextOverflow.Clip,
        onTextLayout = { result ->
            if (result.didOverflowWidth && scale > minScale) {
                scale = (scale - STEP).coerceAtLeast(minScale)
            }
        },
    )
}

private const val STEP = 0.075f
private const val MIN_FLOOR = 0.4f
