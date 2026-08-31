package com.amaxonia.pos.ui.creditnotes

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
import androidx.compose.material.icons.filled.Print
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amaxonia.pos.domain.model.creditnote.CreditNoteFiscalStatusDto
import com.amaxonia.pos.ui.common.components.AdaptiveAmountOptions
import com.amaxonia.pos.ui.common.components.AdaptiveAmountText
import com.amaxonia.pos.ui.theme.ConfirmedContainer
import com.amaxonia.pos.ui.theme.ConfirmedContent
import com.amaxonia.pos.ui.theme.PendingContainer
import com.amaxonia.pos.ui.theme.PendingContent
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

/** Chip con el estado fiscal de la nota de crédito. */
@Composable
internal fun FiscalStatusChip(status: CreditNoteFiscalStatusDto) {
    val isConfirmed = status == CreditNoteFiscalStatusDto.CONFIRMADA
    AssistChip(
        onClick = {},
        label = { Text(if (isConfirmed) "Fiscal confirmada" else "Fiscal pendiente") },
        leadingIcon = {
            Icon(
                imageVector = if (isConfirmed) Icons.Default.CheckCircle else Icons.Default.Print,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
            )
        },
        colors =
            AssistChipDefaults.assistChipColors(
                containerColor = if (isConfirmed) ConfirmedContainer else PendingContainer,
                labelColor = if (isConfirmed) ConfirmedContent else PendingContent,
                leadingIconContentColor = if (isConfirmed) ConfirmedContent else PendingContent,
            ),
    )
}

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
