package com.amaxonia.kiosk.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

val LocalHighContrast = staticCompositionLocalOf { false }

private val LightColorScheme =
    lightColorScheme(
        primary = PrimaryRed,
        onPrimary = SurfaceWhite,
        secondary = AccentYellow,
        onSecondary = TextPrimary,
        background = BackgroundLight,
        onBackground = TextPrimary,
        surface = SurfaceWhite,
        onSurface = TextPrimary,
        error = ErrorRed,
        onError = SurfaceWhite,
    )

private val HighContrastColorScheme =
    lightColorScheme(
        primary = Color.Black,
        onPrimary = Color.White,
        secondary = Color(0xFFFFD600),
        onSecondary = Color.Black,
        background = Color.White,
        onBackground = Color.Black,
        surface = Color.White,
        onSurface = Color.Black,
        surfaceVariant = Color(0xFFEEEEEE),
        onSurfaceVariant = Color.Black,
        error = Color(0xFFB71C1C),
        onError = Color.White,
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
            content = content,
        )
    }
}
