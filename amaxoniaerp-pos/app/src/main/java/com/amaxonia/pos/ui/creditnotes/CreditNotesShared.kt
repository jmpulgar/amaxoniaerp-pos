package com.amaxonia.pos.ui.creditnotes

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amaxonia.pos.domain.model.creditnote.CreditNoteFiscalStatusDto
import com.amaxonia.pos.ui.common.components.AdaptiveAmountOptions
import com.amaxonia.pos.ui.common.components.AdaptiveAmountText
import com.amaxonia.pos.ui.theme.PosExtraShapes
import com.amaxonia.pos.ui.theme.PosPalette
import java.util.Locale

/** Banner resumen con cantidad y monto total. */
@Composable
internal fun SummaryBanner(
    title: String,
    value: String,
    amount: Double,
    modifier: Modifier = Modifier,
    currencySymbol: String = "$",
) {
    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.primary),
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = PosPalette.FixedWhite.copy(alpha = 0.8f), fontSize = 12.sp)
                Text(value, color = PosPalette.FixedWhite, fontWeight = FontWeight.Bold, fontSize = 24.sp)
            }
            Column(horizontalAlignment = Alignment.End, modifier = Modifier.weight(1f)) {
                Text("Monto total", color = PosPalette.FixedWhite.copy(alpha = 0.8f), fontSize = 12.sp)
                // Monto adaptive: totales enormes encogen sin recortarse en 320dp.
                AdaptiveAmountText(
                    text = "$currencySymbol ${formatAmount(amount)}",
                    baseStyle =
                        MaterialTheme.typography.headlineSmall.copy(
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.End,
                        ),
                    color = PosPalette.FixedWhite,
                    modifier = Modifier.fillMaxWidth(),
                    options =
                        AdaptiveAmountOptions(
                            minFontSizeSp = 13f,
                        ),
                )
            }
        }
    }
}

/** Chip / badge con el estado fiscal o electrónico de la nota de crédito. */
@Composable
internal fun FiscalStatusChip(
    status: CreditNoteFiscalStatusDto,
    modifier: Modifier = Modifier,
    isPanama: Boolean = false,
) {
    val isDark = isSystemInDarkTheme()
    val visuals =
        if (isPanama) {
            when (status) {
                CreditNoteFiscalStatusDto.CONFIRMADA ->
                    FiscalBadgeVisuals(
                        label = "Nota de Crédito Electrónica (NCE)",
                        icon = Icons.Default.CheckCircle,
                        container = if (isDark) Color(0xFF0C4A6E) else Color(0xFFE0F2FE),
                        content = if (isDark) Color(0xFF38BDF8) else Color(0xFF0284C7),
                    )
                CreditNoteFiscalStatusDto.PENDIENTE ->
                    FiscalBadgeVisuals(
                        label = "NCE pendiente",
                        icon = Icons.Default.Schedule,
                        container = if (isDark) Color(0xFF78350F) else Color(0xFFFEF3C7),
                        content = if (isDark) Color(0xFFFCD34D) else Color(0xFFB45309),
                    )
                CreditNoteFiscalStatusDto.RECHAZADA ->
                    FiscalBadgeVisuals(
                        label = "NCE rechazada",
                        icon = Icons.Default.ErrorOutline,
                        container = MaterialTheme.colorScheme.errorContainer,
                        content = MaterialTheme.colorScheme.onErrorContainer,
                    )
                CreditNoteFiscalStatusDto.INCIERTA ->
                    FiscalBadgeVisuals(
                        label = "NCE en revisión",
                        icon = Icons.Default.WarningAmber,
                        container = if (isDark) Color(0xFF78350F) else Color(0xFFFEF3C7),
                        content = if (isDark) Color(0xFFFCD34D) else Color(0xFFB45309),
                    )
            }
        } else {
            when (status) {
                CreditNoteFiscalStatusDto.CONFIRMADA ->
                    FiscalBadgeVisuals(
                        label = "Fiscal confirmada",
                        icon = Icons.Default.CheckCircle,
                        container = if (isDark) Color(0xFF064E3B) else Color(0xFFDCFCE7),
                        content = if (isDark) Color(0xFF4ADE80) else Color(0xFF15803D),
                    )
                CreditNoteFiscalStatusDto.PENDIENTE ->
                    FiscalBadgeVisuals(
                        label = "Fiscal pendiente",
                        icon = Icons.Default.Print,
                        container = if (isDark) Color(0xFF7C2D12) else Color(0xFFFFF3E0),
                        content = if (isDark) Color(0xFFFB923C) else Color(0xFFE65100),
                    )
                CreditNoteFiscalStatusDto.RECHAZADA ->
                    FiscalBadgeVisuals(
                        label = "Fiscal rechazada",
                        icon = Icons.Default.ErrorOutline,
                        container = MaterialTheme.colorScheme.errorContainer,
                        content = MaterialTheme.colorScheme.onErrorContainer,
                    )
                CreditNoteFiscalStatusDto.INCIERTA ->
                    FiscalBadgeVisuals(
                        label = "Fiscal incierta",
                        icon = Icons.Default.WarningAmber,
                        container = if (isDark) Color(0xFF78350F) else Color(0xFFFEF3C7),
                        content = if (isDark) Color(0xFFFCD34D) else Color(0xFFB45309),
                    )
            }
        }

    Surface(
        modifier = modifier,
        shape = PosExtraShapes.Pill,
        color = visuals.container,
        contentColor = visuals.content,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = visuals.icon,
                contentDescription = null,
                modifier = Modifier.size(15.dp),
            )
            Text(
                text = visuals.label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private data class FiscalBadgeVisuals(
    val label: String,
    val icon: ImageVector,
    val container: Color,
    val content: Color,
)

/** Estado vacío con ícono, título y subtítulo. */
@Composable
internal fun EmptyState(
    icon: ImageVector,
    title: String,
    subtitle: String,
) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.outline)
            Spacer(modifier = Modifier.height(12.dp))
            Text(title, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Spacer(modifier = Modifier.height(6.dp))
            Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
        }
    }
}

/** Estado de carga centrado con indicador circular y mensaje informativo. */
@Composable
internal fun CreditNotesLoadingState(
    message: String,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(24.dp),
        ) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            Text(
                text = message,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Título de la top bar según el modo del flujo. */
internal fun screenTitle(mode: CreditNotesMode): String =
    when (mode) {
        CreditNotesMode.LIST -> "Notas de crédito"
        CreditNotesMode.INVOICE_PICKER -> "Seleccionar factura"
        CreditNotesMode.CREATE -> "Nueva nota de crédito"
    }

/** Formatea un monto con dos decimales según el locale. */
internal fun formatAmount(value: Double): String = String.format(Locale.getDefault(), "%.2f", value)

/** Formatea una cantidad sin decimales si es entera, o con hasta 3 decimales. */
internal fun formatQuantity(value: Double): String =
    if (value % 1.0 == 0.0) {
        value.toInt().toString()
    } else {
        String.format(Locale.getDefault(), "%.3f", value)
    }
