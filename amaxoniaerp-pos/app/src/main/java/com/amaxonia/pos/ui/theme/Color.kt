package com.amaxonia.pos.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import com.amaxonia.pos.R

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

/** Semantic grouping over the raw status colors above, so new screens reference meaning, not hex. */
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

/**
 * Brand-aware gradient for primary CTA buttons, badges, and headers.
 * Uses a harmonious 2-stop ramp (start -> mid) from deep brand primary to vibrant brand secondary,
 * eliminating the harsh 3-block horizontal banding ("cuadrados") that occurs when accent colors
 * are forced into compact button widths.
 */
@Composable
fun cartBrandGradient(): List<Color> =
    listOf(
        colorResource(R.color.brand_gradient_start),
        colorResource(R.color.brand_gradient_mid),
    )

/** Stable per-payment-method color, centralized so tiles/rows/reports agree on the same mapping. */
fun paymentMethodColor(sigla: String?): Color {
    val palette = PaymentMethodColors
    val index = (sigla?.hashCode() ?: 0).let { if (it < 0) -it else it } % palette.size
    return palette[index]
}
