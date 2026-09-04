package com.amaxonia.pos.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Receipt
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amaxonia.pos.domain.model.ElectronicInvoiceStatus
import com.amaxonia.pos.domain.model.Transaction
import com.amaxonia.pos.domain.model.TransactionStatus
import com.amaxonia.pos.ui.common.components.AdaptiveAmountOptions
import com.amaxonia.pos.ui.common.components.AdaptiveAmountText
import com.amaxonia.pos.ui.payment.formatCurrencyLabel
import java.util.Locale

/** Encabezado sticky de fecha dentro del historial agrupado. */
@Composable
internal fun DateStickyHeader(date: String) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background)
                .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Rounded.CalendarToday,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(16.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = date,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

/** Tarjeta de una transacción del historial. */
@Composable
internal fun TransactionCard(
    transaction: Transaction,
    onClick: () -> Unit,
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TransactionCardIcon()
            Spacer(modifier = Modifier.width(14.dp))
            TransactionCardInfo(transaction = transaction, modifier = Modifier.weight(1f))
            TransactionCardAmount(transaction)
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(start = 4.dp).size(20.dp),
            )
        }
    }
}

@Composable
private fun TransactionCardIcon() {
    Box(
        modifier =
            Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Rounded.Receipt,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp),
        )
    }
}

@Composable
private fun TransactionCardInfo(
    transaction: Transaction,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = transaction.invoiceNumber,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            StatusBadge(status = transaction.status)
            if (transaction.electronicStatus != ElectronicInvoiceStatus.NONE) {
                ElectronicStatusBadge(status = transaction.electronicStatus)
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        TransactionMetadataRow(transaction)
    }
}

@Composable
private fun TransactionMetadataRow(transaction: Transaction) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            Icons.Rounded.Schedule,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(13.dp),
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = transaction.time,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (transaction.clienteNombre.isNotBlank()) {
            Spacer(modifier = Modifier.width(10.dp))
            Icon(
                Icons.Rounded.Person,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(13.dp),
            )
            Spacer(modifier = Modifier.width(3.dp))
            Text(
                text = transaction.clienteNombre,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
        }
    }
}

@Composable
private fun TransactionCardAmount(transaction: Transaction) {
    Column(horizontalAlignment = Alignment.End) {
        AdaptiveAmountText(
            text = "${transaction.currency} ${String.format(Locale.getDefault(), "%.2f", transaction.amount)}",
            baseStyle = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.primary,
            options =
                AdaptiveAmountOptions(
                    minFontSizeSp = 11f,
                ),
        )
        if (transaction.totalRef != null && transaction.totalRef > 0.0 && !transaction.abrMonedaSecundaria.isNullOrBlank()) {
            Text(
                text =
                    "${formatCurrencyLabel(transaction.abrMonedaSecundaria)} " +
                        String.format(Locale.getDefault(), "%.2f", transaction.totalRef),
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Badge de estado de la transacción con color semántico. */
@Composable
internal fun StatusBadge(status: TransactionStatus) {
    val backgroundColor = Color(status.colorHex).copy(alpha = 0.1f)
    val textColor = Color(status.colorHex)

    Box(
        modifier =
            Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(backgroundColor)
                .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(
            text = status.label,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = textColor,
        )
    }
}

/** Badge de estado de facturación electrónica ("FE Pendiente", "FE Fallida", "FE Exitosa"). */
@Composable
internal fun ElectronicStatusBadge(status: ElectronicInvoiceStatus) {
    if (status == ElectronicInvoiceStatus.NONE) return
    val backgroundColor = Color(status.colorHex).copy(alpha = 0.1f)
    val textColor = Color(status.colorHex)

    Box(
        modifier =
            Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(backgroundColor)
                .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(
            text = status.label,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = textColor,
        )
    }
}
