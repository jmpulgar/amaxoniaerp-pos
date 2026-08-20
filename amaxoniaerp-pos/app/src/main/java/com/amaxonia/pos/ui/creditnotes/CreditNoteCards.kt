package com.amaxonia.pos.ui.creditnotes

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Receipt
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amaxonia.pos.domain.model.creditnote.CreditNoteSourceInvoiceLineDto
import com.amaxonia.pos.domain.model.creditnote.CreditNoteSourceInvoiceSummaryDto
import com.amaxonia.pos.domain.model.creditnote.CreditNoteSummaryDto
import com.amaxonia.pos.domain.model.payment.FormaPago
import com.amaxonia.pos.ui.common.components.AdaptiveAmountOptions
import com.amaxonia.pos.ui.common.components.AdaptiveAmountText

@Composable
internal fun CreditNoteCard(
    note: CreditNoteSummaryDto,
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
            CreditNoteLeadingIcon()
            Spacer(modifier = Modifier.width(14.dp))
            CreditNoteInfo(note = note, modifier = Modifier.weight(1f))
            CreditNoteAmount(note)
        }
    }
}

@Composable
private fun CreditNoteLeadingIcon() {
    Box(
        modifier =
            Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.AutoMirrored.Filled.ReceiptLong,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp),
        )
    }
}

@Composable
private fun CreditNoteInfo(
    note: CreditNoteSummaryDto,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = note.codigo,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.height(6.dp))
        FiscalStatusChip(status = note.fiscalStatus)
        Spacer(modifier = Modifier.height(6.dp))
        CreditNoteMetadata(date = note.fecha, clientName = note.clienteNombre)
    }
}

@Composable
private fun CreditNoteMetadata(
    date: String,
    clientName: String,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            Icons.Rounded.Schedule,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(13.dp),
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(text = date, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (clientName.isNotBlank()) {
            Spacer(modifier = Modifier.width(10.dp))
            Icon(
                Icons.Rounded.Person,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(13.dp),
            )
            Spacer(modifier = Modifier.width(3.dp))
            Text(
                text = clientName,
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
private fun CreditNoteAmount(note: CreditNoteSummaryDto) {
    Column(horizontalAlignment = Alignment.End) {
        AdaptiveAmountText(
            text = "Bs ${formatAmount(note.total)}",
            baseStyle = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.primary,
            options = AdaptiveAmountOptions(minFontSizeSp = 11f),
        )
        Text(
            text = note.facturaCodigo,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
internal fun SourceInvoiceCard(
    invoice: CreditNoteSourceInvoiceSummaryDto,
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
            SourceInvoiceLeadingIcon()
            Spacer(modifier = Modifier.width(14.dp))
            SourceInvoiceInfo(invoice = invoice, modifier = Modifier.weight(1f))
            SourceInvoiceAmount(invoice)
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
private fun SourceInvoiceLeadingIcon() {
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
private fun SourceInvoiceInfo(
    invoice: CreditNoteSourceInvoiceSummaryDto,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = invoice.codigo,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.height(6.dp))
        CreditNoteMetadata(date = invoice.fecha, clientName = invoice.clienteNombre)
    }
}

@Composable
private fun SourceInvoiceAmount(invoice: CreditNoteSourceInvoiceSummaryDto) {
    Column(horizontalAlignment = Alignment.End) {
        AdaptiveAmountText(
            text = "${invoice.moneda} ${formatAmount(invoice.remainingAmount)}",
            baseStyle = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.primary,
            options = AdaptiveAmountOptions(minFontSizeSp = 11f),
        )
    }
}

/** Línea de factura origen mostrada como solo-lectura en el flujo de creación. */
@Composable
internal fun InvoiceLineReadOnlyCard(
    line: CreditNoteSourceInvoiceLineDto,
    currency: String,
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            InvoiceLineQuantityBadge(quantity = line.cantidadOriginal)

            Spacer(modifier = Modifier.width(12.dp))

            InvoiceLineDescription(line = line, modifier = Modifier.weight(1f))

            Spacer(modifier = Modifier.width(8.dp))

            InvoiceLineAmounts(line = line, currency = currency)
        }
    }
}

@Composable
private fun InvoiceLineQuantityBadge(quantity: Double) {
    Box(
        modifier =
            Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = formatQuantity(quantity),
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun InvoiceLineDescription(
    line: CreditNoteSourceInvoiceLineDto,
    modifier: Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = line.descripcion,
            fontWeight = FontWeight.Medium,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (line.codigo.isNotBlank()) {
            Text(
                text = line.codigo,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun InvoiceLineAmounts(
    line: CreditNoteSourceInvoiceLineDto,
    currency: String,
) {
    Column(horizontalAlignment = Alignment.End) {
        AdaptiveAmountText(
            text = "$currency ${formatAmount(line.totalConIvaOriginal)}",
            baseStyle =
                MaterialTheme.typography.labelLarge.copy(
                    fontWeight = FontWeight.Bold,
                ),
            color = MaterialTheme.colorScheme.onSurface,
            options = AdaptiveAmountOptions(minFontSizeSp = 11f),
        )
        Text(
            text = "IVA: ${formatAmount(line.pIva)}%",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RefundMethodSelector(
    methods: List<FormaPago>,
    selectedId: Int?,
    onSelected: (Int) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = methods.firstOrNull { it.idFormaPago == selectedId }?.descripcion.orEmpty()

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
        OutlinedTextField(
            value = selectedLabel,
            onValueChange = {},
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
            readOnly = true,
            label = { Text("Forma de pago de reintegro") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            methods.forEach { method ->
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text(method.descripcion.orEmpty()) },
                    onClick = {
                        onSelected(method.idFormaPago)
                        expanded = false
                    },
                )
            }
        }
    }
}

internal fun formatQuantity(value: Double): String =
    if (value % 1.0 == 0.0) {
        value.toInt().toString()
    } else {
        String.format(java.util.Locale.getDefault(), "%.3f", value)
    }
