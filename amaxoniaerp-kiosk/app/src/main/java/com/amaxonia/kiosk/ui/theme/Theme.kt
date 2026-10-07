package com.amaxonia.kiosk.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val LocalHighContrast = staticCompositionLocalOf { false }

private val LightColorScheme =
    lightColorScheme(
        primary = FlowIndigo,
        onPrimary = SurfaceWhite,
        primaryContainer = FlowIndigoSoft,
        onPrimaryContainer = FlowIndigoDeep,
        secondary = FlowBlue,
        onSecondary = SurfaceWhite,
        secondaryContainer = FlowBlueSoft,
        onSecondaryContainer = FlowBlueDeep,
        tertiary = FlowLavender,
        onTertiary = SurfaceWhite,
        tertiaryContainer = FlowLavenderSoft,
        onTertiaryContainer = FlowLavenderDeep,
        background = SurfaceWhite,
        onBackground = FlowInk,
        surface = SurfaceWhite,
        onSurface = FlowInk,
        surfaceVariant = FlowSurfaceVariant,
        onSurfaceVariant = FlowSlate,
        outline = FlowOutline,
        outlineVariant = FlowOutlineSoft,
        error = FlowError,
        onError = SurfaceWhite,
        errorContainer = FlowErrorSoft,
        onErrorContainer = FlowError,
    )

/**
 * High contrast: pure white surfaces, black text and outlines, and the deepest brand indigo for
 * selected/primary elements (17:1 against white). No yellow/red branding — red stays for errors.
 */
private val HighContrastColorScheme =
    lightColorScheme(
        primary = FlowIndigoDeep,
        onPrimary = Color.White,
        primaryContainer = Color.White,
        onPrimaryContainer = Color.Black,
        secondary = FlowIndigoDeep,
        onSecondary = Color.White,
        secondaryContainer = Color.White,
        onSecondaryContainer = Color.Black,
        tertiary = FlowIndigoDeep,
        onTertiary = Color.White,
        tertiaryContainer = Color.White,
        onTertiaryContainer = Color.Black,
        background = Color.White,
        onBackground = Color.Black,
        surface = Color.White,
        onSurface = Color.Black,
        surfaceVariant = Color.White,
        onSurfaceVariant = Color.Black,
        outline = Color.Black,
        outlineVariant = Color.Black,
        error = Color(0xFF9B0000),
        onError = Color.White,
        errorContainer = Color.White,
        onErrorContainer = Color(0xFF9B0000),
        inverseSurface = Color.Black,
        inverseOnSurface = Color.White,
    )

// Heavy, condensed-feeling sans for a bold self-order look. Roboto Black/ExtraBold ship on API 28.
private val Display = FontFamily.SansSerif

val KioskTypography =
    Typography(
        displayLarge = TextStyle(fontFamily = Display, fontWeight = FontWeight.Black, fontSize = 96.sp, lineHeight = 100.sp),
        displayMedium = TextStyle(fontFamily = Display, fontWeight = FontWeight.Black, fontSize = 72.sp, lineHeight = 78.sp),
        displaySmall = TextStyle(fontFamily = Display, fontWeight = FontWeight.Black, fontSize = 56.sp, lineHeight = 62.sp),
        headlineLarge = TextStyle(fontFamily = Display, fontWeight = FontWeight.Black, fontSize = 48.sp, lineHeight = 54.sp),
        headlineMedium = TextStyle(fontFamily = Display, fontWeight = FontWeight.ExtraBold, fontSize = 40.sp, lineHeight = 46.sp),
        headlineSmall = TextStyle(fontFamily = Display, fontWeight = FontWeight.ExtraBold, fontSize = 34.sp, lineHeight = 40.sp),
        titleLarge = TextStyle(fontFamily = Display, fontWeight = FontWeight.ExtraBold, fontSize = 30.sp, lineHeight = 36.sp),
        titleMedium = TextStyle(fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 26.sp, lineHeight = 32.sp),
        titleSmall = TextStyle(fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 28.sp),
        bodyLarge = TextStyle(fontFamily = Display, fontWeight = FontWeight.Medium, fontSize = 28.sp, lineHeight = 36.sp),
        bodyMedium = TextStyle(fontFamily = Display, fontWeight = FontWeight.Medium, fontSize = 24.sp, lineHeight = 32.sp),
        bodySmall = TextStyle(fontFamily = Display, fontWeight = FontWeight.Medium, fontSize = 20.sp, lineHeight = 26.sp),
        labelLarge = TextStyle(fontFamily = Display, fontWeight = FontWeight.ExtraBold, fontSize = 26.sp, lineHeight = 30.sp),
        labelMedium = TextStyle(fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 24.sp),
        labelSmall = TextStyle(fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 16.sp, lineHeight = 20.sp),
    )

val KioskShapes =
    Shapes(
        extraSmall = RoundedCornerShape(6.dp),
        small = RoundedCornerShape(8.dp),
        medium = RoundedCornerShape(12.dp),
        large = RoundedCornerShape(14.dp),
        extraLarge = RoundedCornerShape(20.dp),
    )

@Composable
fun AmaxoniaKioskTheme(
    highContrast: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = if (highContrast) HighContrastColorScheme else LightColorScheme
    CompositionLocalProvider(LocalHighContrast provides highContrast) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = KioskTypography,
            shapes = KioskShapes,
            content = content,
        )
    }
}

private val HighContrastSuccess = Color(0xFF00561F)
private val FlowSuccessOnDark = Color(0xFF4ADE80)

/**
 * Semantic kiosk colors/brushes not covered by the Material scheme. All of them collapse to flat,
 * maximum-contrast values in high-contrast mode.
 */
object KioskColors {
    /** Positive state (done steps, satisfied requirements, confirmations). Note: tertiary is lavender. */
    val success: Color
        @Composable @ReadOnlyComposable
        get() = if (LocalHighContrast.current) HighContrastSuccess else FlowSuccess

    /** Check/confirmation tint on dark surfaces (toasts on inverse surface). */
    val successOnDark: Color
        @Composable @ReadOnlyComposable
        get() = if (LocalHighContrast.current) Color.White else FlowSuccessOnDark

    /** Flat brand indigo behind white text (primary CTAs, selected states). */
    val ctaBrush: Brush
        @Composable @ReadOnlyComposable
        get() = if (LocalHighContrast.current) SolidColor(Color.Black) else CtaGradientBrush

    /** Quiet light grey: food placeholders and illustration backgrounds. */
    val softBrush: Brush
        @Composable @ReadOnlyComposable
        get() = if (LocalHighContrast.current) SolidColor(Color.White) else SoftGradientBrush

    /** Flat brand indigo for bands that carry white text (amount heroes). */
    val deepBrush: Brush
        @Composable @ReadOnlyComposable
        get() = if (LocalHighContrast.current) SolidColor(Color.Black) else DeepGradientBrush

    /** Indigo-tinted shadow color: depth that feels on-brand instead of grey. */
    val shadow: Color
        @Composable @ReadOnlyComposable
        get() = if (LocalHighContrast.current) Color.Transparent else FlowIndigoDeep

    /** Full brand gradient (indigo → blue → lavender) for decorative heroes and illustrations. */
    val heroBrush: Brush
        @Composable @ReadOnlyComposable
        get() = if (LocalHighContrast.current) SolidColor(Color.Black) else HeroGradientBrush
}

// Self-order kiosk look: flat brand colors, no gradients on functional surfaces.
private val CtaGradientBrush = SolidColor(FlowIndigo)
private val HeroGradientBrush = Brush.linearGradient(FlowGradient)
private val SoftGradientBrush = SolidColor(FlowBackground)
private val DeepGradientBrush = SolidColor(FlowIndigo)
