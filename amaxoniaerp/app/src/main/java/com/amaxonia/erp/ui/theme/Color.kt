package com.amaxonia.erp.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import com.amaxonia.erp.R

// Brand Primary = Azul Índigo Profundo #201B82 (Contraste AAA 14.5:1 con blanco)
val BrandPrimary = Color(0xFF201B82)
val BrandOnPrimary = Color(0xFFFFFFFF)
val BrandPrimaryDark = Color(0xFFA5B4FC)
val BrandPrimaryContainer = Color(0xFFE0E3FE)
val BrandOnPrimaryContainer = Color(0xFF0D0860)

// Brand Secondary = Azul Moderno #2488FA
val BrandSecondary = Color(0xFF2488FA)
val BrandOnSecondary = Color(0xFFFFFFFF)
val BrandSecondaryContainer = Color(0xFFD7E8FE)
val BrandOnSecondaryContainer = Color(0xFF003B75)

// Brand Accent = Púrpura #A37EE3 / Lavanda Suave #EDE4FD
val BrandAccent = Color(0xFFA37EE3)
val BrandOnAccent = Color(0xFFFFFFFF)
val BrandAccentContainer = Color(0xFFEDE4FD)
val BrandOnAccentContainer = Color(0xFF320B66)

// Error / Peligro
val BrandError = Color(0xFFB91C1C)
val BrandOnError = Color(0xFFFFFFFF)
val BrandErrorContainer = Color(0xFFFEF2F2)
val BrandOnErrorContainer = Color(0xFFB91C1C)

// Superficies Light
val BrandBackground = Color(0xFFF4F5F8)
val BrandSurface = Color(0xFFFFFFFF)
val BrandOnBackground = Color(0xFF121212)
val BrandOnSurface = Color(0xFF121212)
val BrandSurfaceVariant = Color(0xFFEDF1F7)
val BrandOnSurfaceVariant = Color(0xFF475569)
val BrandOutline = Color(0xFF94A3B8)
val BrandOutlineVariant = Color(0xFFE2E8F0)

// Dark Palette
val BrandBackgroundDark = Color(0xFF0F172A)
val BrandSurfaceDark = Color(0xFF1E293B)
val BrandOnBackgroundDark = Color(0xFFF8FAFC)
val BrandOnSurfaceDark = Color(0xFFF8FAFC)
val BrandSurfaceVariantDark = Color(0xFF334155)
val BrandOnSurfaceVariantDark = Color(0xFF94A3B8)
val BrandOutlineDark = Color(0xFF64748B)
val BrandOutlineVariantDark = Color(0xFF475569)

// Paleta de Estado y Semántica POS
val SuccessGreen = Color(0xFF15803D)
val NeutralGray = Color(0xFF9E9E9E)
val InfoBlue = Color(0xFF1565C0)
val InfoCyan = Color(0xFF0277BD)
val PaymentTeal = Color(0xFF00897B)
val AccentPurple = Color(0xFF6A1B9A)
val WarningOrange = Color(0xFFEF6C00)
val StrongErrorRed = Color(0xFFC62828)
val OnlineGreen = Color(0xFF16A34A)
val OfflineRed = Color(0xFFDC2626)
val ConfirmedContainer = Color(0xFFF0FDF4)
val ConfirmedContent = Color(0xFF15803D)
val PendingContainer = Color(0xFFFFF3E0)
val PendingContent = Color(0xFFE65100)
val ReportOrange = Color(0xFFFF9800)

val PaymentMethodColors =
    listOf(
        InfoBlue,
        PaymentTeal,
        AccentPurple,
        WarningOrange,
        SuccessGreen,
        StrongErrorRed,
    )

/** Agrupación semántica para estados del POS, garantizando coherencia visual en badges y estados. */
object PosStatusColors {
    val success = SuccessGreen
    val warning = WarningOrange
    val info = InfoBlue
    val neutral = NeutralGray
    val online = OnlineGreen
    val offline = OfflineRed
    val confirmedContainer = ConfirmedContainer
    val confirmedContent = ConfirmedContent
    val pendingContainer = PendingContainer
    val pendingContent = PendingContent
}

/** Colores fijos de alto contraste para chrome e iconos. */
object PosPalette {
    val FixedWhite = Color.White
    val FixedBlack = Color.Black
    val DangerRed = Color(0xFFDC2626)
    val Transparent = Color.Transparent
}

/**
 * Gradiente de marca de 2 paradas (start -> mid) para botones principales (CTA), badges y cabeceras.
 * Elimina las bandas duras horizontales ("cuadrados") producidas cuando se fuerza un gradiente tricolor en anchos reducidos.
 */
@Composable
fun cartBrandGradient(): List<Color> =
    listOf(
        colorResource(R.color.brand_gradient_start),
        colorResource(R.color.brand_gradient_mid),
    )

/** Gradiente tricolor característico de fondo decorativo */
val BrandGradient =
    Brush.horizontalGradient(
        colors = listOf(BrandPrimary, BrandSecondary, BrandAccent),
    )

val BrandHeroGradient =
    Brush.linearGradient(
        colors = listOf(Color(0xFF140F5E), Color(0xFF201B82), Color(0xFF1E3A8A)),
    )

/** Color estable por método de pago. */
fun paymentMethodColor(sigla: String?): Color {
    val palette = PaymentMethodColors
    val index = (sigla?.hashCode() ?: 0).let { if (it < 0) -it else it } % palette.size
    return palette[index]
}
