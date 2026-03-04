package com.amaxonia.pos.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

// Definimos el tema OSCURO (por si acaso, usando tu azul)
private val DarkColorScheme = darkColorScheme(
    primary = AmaxoniaBlue,
    secondary = AmaxoniaBlue,
    tertiary = TextSecondary,
    background = TextPrimary, // Fondo oscuro
    surface = TextPrimary
)

// Definimos el tema CLARO (El principal de tu diseño)
private val LightColorScheme = lightColorScheme(
    primary = AmaxoniaBlue,
    secondary = AmaxoniaBlue,
    tertiary = TextSecondary,
    background = BgLightGray,
    surface = SurfaceWhite,
    onPrimary = SurfaceWhite,
    onSecondary = SurfaceWhite,
    onTertiary = SurfaceWhite,
    onBackground = TextPrimary,
    onSurface = TextPrimary
)

@Composable
fun AmaxoniaPOSTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color está disponible en Android 12+
    dynamicColor: Boolean = false, // Lo pongo en false para forzar TU diseño azul, no el del sistema del usuario
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}