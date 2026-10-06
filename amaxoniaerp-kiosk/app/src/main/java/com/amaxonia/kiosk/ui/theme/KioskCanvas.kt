package com.amaxonia.kiosk.ui.theme

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * The kiosk UI is designed in dp for a 1080 × 1920 portrait canvas (SUNMI K2 at mdpi), or the same
 * canvas turned 1920 × 1080 on landscape screens. [scaleFor] maps the real window onto that canvas:
 * one design dp becomes `scale` physical pixels, whatever density or system font size the device
 * reports, so the UI always fills the same proportion of the screen.
 */
object KioskCanvas {
    /** Design canvas short side, in dp. */
    const val SHORT_SIDE = 1080f

    /** Design canvas long side, in dp. */
    const val LONG_SIDE = 1920f

    /** Never shrink the canvas below a quarter (tiny emulators) nor blow it up past 4× (8K panels). */
    const val MIN_SCALE = 0.25f
    const val MAX_SCALE = 4f

    /**
     * Pixels per design dp for a window of [widthPx] × [heightPx]. Portrait windows fit the
     * 1080 × 1920 canvas, landscape windows the 1920 × 1080 one; the tighter axis wins so the whole
     * canvas stays visible (the other axis gets extra room, which adaptive layouts use).
     * Returns null when the window has not been measured yet.
     */
    fun scaleFor(
        widthPx: Int,
        heightPx: Int,
    ): Float? {
        if (widthPx <= 0 || heightPx <= 0) return null
        val landscape = widthPx > heightPx
        val canvasWidth = if (landscape) LONG_SIDE else SHORT_SIDE
        val canvasHeight = if (landscape) SHORT_SIDE else LONG_SIDE
        val scale = minOf(widthPx / canvasWidth, heightPx / canvasHeight)
        return scale.coerceIn(MIN_SCALE, MAX_SCALE)
    }
}

/** Size of the scaled kiosk canvas in design dp (e.g. 1080 × 1920, or 1920 × 1080 in landscape). */
@Immutable
data class KioskCanvasSize(
    val width: Dp,
    val height: Dp,
) {
    val isLandscape: Boolean get() = width > height
}

/** Canvas size for layout decisions that need the whole screen, not just the local constraints. */
val LocalKioskCanvas = staticCompositionLocalOf { KioskCanvasSize(KioskCanvas.SHORT_SIDE.dp, KioskCanvas.LONG_SIDE.dp) }

/**
 * Fills the window and provides a [LocalDensity] that maps the design canvas onto it
 * (font scale pinned to 1: the system font size must not blow up a fixed-size kiosk layout).
 * Dialogs and popups inherit the density because they read composition locals.
 */
@Composable
fun KioskScaledCanvas(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val platform = LocalDensity.current
        val widthPx = if (constraints.hasBoundedWidth) constraints.maxWidth else 0
        val heightPx = if (constraints.hasBoundedHeight) constraints.maxHeight else 0
        val scale = KioskCanvas.scaleFor(widthPx, heightPx) ?: platform.density
        val density = remember(scale) { Density(density = scale, fontScale = 1f) }
        val canvas =
            remember(widthPx, heightPx, scale) {
                KioskCanvasSize(width = (widthPx / scale).dp, height = (heightPx / scale).dp)
            }
        CompositionLocalProvider(
            LocalDensity provides density,
            LocalKioskCanvas provides canvas,
            content = content,
        )
    }
}

/**
 * Kiosk dialog window. A dialog is a separate window whose root re-provides the platform density,
 * configuration and context, so the scaled canvas density and the kiosk language are captured here
 * and provided again inside it; the platform width
 * rules are disabled (callers set their own max width) and tall content scrolls instead of being
 * clipped on short landscape screens.
 */
@Composable
fun KioskDialog(
    onDismissRequest: () -> Unit,
    dismissOnBackPress: Boolean = true,
    dismissOnClickOutside: Boolean = true,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val canvas = LocalKioskCanvas.current
    val configuration = LocalConfiguration.current
    val context = LocalContext.current
    Dialog(
        onDismissRequest = onDismissRequest,
        properties =
            DialogProperties(
                dismissOnBackPress = dismissOnBackPress,
                dismissOnClickOutside = dismissOnClickOutside,
                usePlatformDefaultWidth = false,
            ),
    ) {
        CompositionLocalProvider(
            LocalDensity provides density,
            LocalKioskCanvas provides canvas,
            LocalConfiguration provides configuration,
            LocalContext provides context,
        ) {
            Box(
                modifier = Modifier.heightIn(max = canvas.height).verticalScroll(rememberScrollState()),
                contentAlignment = Alignment.Center,
            ) {
                content()
            }
        }
    }
}
