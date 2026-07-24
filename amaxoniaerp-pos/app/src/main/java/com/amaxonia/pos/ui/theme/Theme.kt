package com.amaxonia.pos.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import com.amaxonia.pos.R

@Composable
private fun darkBrandColorScheme() =
    darkColorScheme(
        primary = colorResource(R.color.brand_primary_dark),
        onPrimary = colorResource(R.color.brand_on_primary_dark),
        primaryContainer = colorResource(R.color.brand_primary_container_dark),
        onPrimaryContainer = colorResource(R.color.brand_on_primary_container_dark),
        secondary = colorResource(R.color.brand_secondary_dark),
        onSecondary = colorResource(R.color.brand_on_secondary_dark),
        secondaryContainer = colorResource(R.color.brand_secondary_container_dark),
        onSecondaryContainer = colorResource(R.color.brand_on_secondary_container_dark),
        tertiary = colorResource(R.color.brand_accent_dark),
        onTertiary = colorResource(R.color.brand_on_accent_dark),
        tertiaryContainer = colorResource(R.color.brand_accent_container_dark),
        onTertiaryContainer = colorResource(R.color.brand_on_accent_container_dark),
        background = colorResource(R.color.brand_background_dark),
        onBackground = colorResource(R.color.brand_on_background_dark),
        surface = colorResource(R.color.brand_surface_dark),
        onSurface = colorResource(R.color.brand_on_surface_dark),
        surfaceVariant = colorResource(R.color.brand_surface_variant_dark),
        onSurfaceVariant = colorResource(R.color.brand_on_surface_variant_dark),
        outline = colorResource(R.color.brand_outline_dark),
        outlineVariant = colorResource(R.color.brand_outline_variant_dark),
        error = colorResource(R.color.brand_error_dark),
        onError = colorResource(R.color.brand_on_error_dark),
        errorContainer = colorResource(R.color.brand_error_container_dark),
        onErrorContainer = colorResource(R.color.brand_on_error_container_dark),
    )

@Composable
private fun lightBrandColorScheme() =
    lightColorScheme(
        primary = colorResource(R.color.brand_primary),
        onPrimary = colorResource(R.color.brand_on_primary),
        primaryContainer = colorResource(R.color.brand_primary_container),
        onPrimaryContainer = colorResource(R.color.brand_on_primary_container),
        secondary = colorResource(R.color.brand_secondary),
        onSecondary = colorResource(R.color.brand_on_secondary),
        secondaryContainer = colorResource(R.color.brand_secondary_container),
        onSecondaryContainer = colorResource(R.color.brand_on_secondary_container),
        tertiary = colorResource(R.color.brand_accent),
        onTertiary = colorResource(R.color.brand_on_accent),
        tertiaryContainer = colorResource(R.color.brand_accent_container),
        onTertiaryContainer = colorResource(R.color.brand_on_accent_container),
        background = colorResource(R.color.brand_background),
        surface = colorResource(R.color.brand_surface),
        onBackground = colorResource(R.color.brand_on_background),
        onSurface = colorResource(R.color.brand_on_surface),
        surfaceVariant = colorResource(R.color.brand_surface_variant),
        onSurfaceVariant = colorResource(R.color.brand_on_surface_variant),
        outline = colorResource(R.color.brand_outline),
        outlineVariant = colorResource(R.color.brand_outline_variant),
        error = colorResource(R.color.brand_error),
        onError = colorResource(R.color.brand_on_error),
        errorContainer = colorResource(R.color.brand_error_container),
        onErrorContainer = colorResource(R.color.brand_on_error_container),
    )

@Composable
fun PosTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme =
        when {
            dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                val context = LocalContext.current
                if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            }
            darkTheme -> darkBrandColorScheme()
            else -> lightBrandColorScheme()
        }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = PosShapes,
        content = content,
    )
}
