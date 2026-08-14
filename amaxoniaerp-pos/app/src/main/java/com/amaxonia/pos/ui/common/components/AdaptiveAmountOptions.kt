package com.amaxonia.pos.ui.common.components

import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign

/** Optional fitting and typography overrides for [AdaptiveAmountText]. */
data class AdaptiveAmountOptions(
    val minFontSizeSp: Float = 14f,
    val fontWeight: FontWeight? = null,
    val textAlign: TextAlign? = null,
    val maxLines: Int = 1,
)
