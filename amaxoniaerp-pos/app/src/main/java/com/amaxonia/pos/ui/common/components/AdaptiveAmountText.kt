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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp

/** Monetary text that shrinks only when the available width requires it. */
@Composable
fun AdaptiveAmountText(
    text: String,
    baseStyle: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    options: AdaptiveAmountOptions = AdaptiveAmountOptions(),
) {
    val baseFontSize: TextUnit = if (baseStyle.fontSize.isSpecified) baseStyle.fontSize else 16.sp
    val resolvedStyle =
        baseStyle.copy(
            color = if (color.isSpecified) color else LocalContentColor.current,
            fontWeight = options.fontWeight ?: baseStyle.fontWeight,
            textAlign = options.textAlign ?: baseStyle.textAlign,
        )
    val minScale = (options.minFontSizeSp / baseFontSize.value).coerceIn(MIN_FLOOR, 1f)
    var scale by remember(text, resolvedStyle, options) { mutableFloatStateOf(1f) }

    Text(
        text = text,
        modifier = modifier,
        style = resolvedStyle,
        fontSize = (baseFontSize.value * scale).sp,
        maxLines = options.maxLines,
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
