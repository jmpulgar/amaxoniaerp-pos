package com.amaxonia.kiosk.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

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

@Composable
fun AmaxoniaKioskTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        content = content,
    )
}
