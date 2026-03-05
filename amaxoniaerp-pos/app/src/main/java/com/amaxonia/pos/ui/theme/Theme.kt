package com.amaxonia.pos.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = AmaxoniaBlueDark,
    onPrimary = Color(0xFF00315F),
    primaryContainer = Color(0xFF004788),
    onPrimaryContainer = Color(0xFFD7E3FF),
    secondary = Color(0xFFBDC7E9),
    onSecondary = Color(0xFF27304A),
    tertiary = Color(0xFF77D0C2),
    onTertiary = Color(0xFF003730),
    background = BgDark,
    onBackground = TextPrimaryDark,
    surface = SurfaceDark,
    onSurface = TextPrimaryDark,
    surfaceVariant = Color(0xFF2A3244),
    onSurfaceVariant = TextSecondaryDark,
    outline = Color(0xFF8891A8),
    outlineVariant = Color(0xFF3D465D),
    error = ErrorRed,
    onError = Color.White,
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6)
)

private val LightColorScheme = lightColorScheme(
    primary = AmaxoniaBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD7E3FF),
    onPrimaryContainer = Color(0xFF001B44),
    secondary = Color(0xFF536081),
    onSecondary = Color.White,
    tertiary = Color(0xFF006A60),
    onTertiary = Color.White,
    background = BgLightGray,
    surface = SurfaceWhite,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    surfaceVariant = Color(0xFFDFE3EC),
    onSurfaceVariant = TextSecondary,
    outline = Color(0xFF757D91),
    outlineVariant = Color(0xFFC1C6D0),
    error = ErrorRed,
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002)
)

@Composable
fun AmaxoniaPOSTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
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
