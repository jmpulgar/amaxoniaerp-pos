package com.amaxonia.pos.ui.creditnotes

import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amaxonia.pos.domain.model.creditnote.CreditNoteDetailDto
import com.amaxonia.pos.domain.model.creditnote.CreditNoteDetailLineDto
import com.amaxonia.pos.domain.model.creditnote.CreditNoteFiscalStatusDto
import com.amaxonia.pos.ui.common.components.AdaptiveAmountOptions
import com.amaxonia.pos.ui.common.components.AdaptiveAmountText
import com.amaxonia.pos.ui.theme.PosPalette

/** Contenido de la hoja con el detalle completo de una nota de crédito. */
@Composable
internal fun CreditNoteDetailSheet(
    detail: CreditNoteDetailDto,
    isSubmitting: Boolean,
    onProcessFiscal: () -> Unit,
    currencySymbol: String = "$",
) {
    val scrollState = rememberScrollState()

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .padding(24.dp)
                .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        CreditNoteDetailHeader(detail = detail)
        CreditNoteDetailInfoCard(detail = detail)

        if (detail.observacion.isNotBlank()) {
            CreditNoteDetailObservacionCard(observacion = detail.observacion)
        }

        CreditNoteDetailProductsCard(
            lines = detail.lines,
            currencySymbol = currencySymbol,
        )

        CreditNoteDetailTotalsCard(
            detail = detail,
            currencySymbol = currencySymbol,
        )

        if (detail.fiscalStatus == CreditNoteFiscalStatusDto.PENDIENTE && detail.fiscalDocument != null) {
            ProcessFiscalButton(
                isSubmitting = isSubmitting,
                onClick = onProcessFiscal,
            )
        }
    }
}

/** Encabezado con código, estado fiscal y número de documento fiscal si aplica. */
@Composable
private fun CreditNoteDetailHeader(detail: CreditNoteDetailDto) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ReceiptLong,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp),
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = detail.codigo,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                FiscalStatusChip(status = detail.fiscalStatus)
            }
        }
    }
}

/** Tarjeta con cliente, factura origen, fecha y número fiscal. */
@Composable
private fun CreditNoteDetailInfoCard(
    detail: CreditNoteDetailDto,
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (detail.clienteNombre.isNotBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Rounded.Person,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = detail.clienteNombre,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                if (detail.clienteIdentificacion.isNotBlank()) {
                    Text(
                        text = detail.clienteIdentificacion,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 24.dp),
                    )
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant)
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Factura origen", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                Text(detail.facturaCodigo, fontWeight = FontWeight.Medium, fontSize = 13.sp)
            }

            if (detail.fecha.isNotBlank()) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Fecha emisión", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                    Text(detail.fecha, fontWeight = FontWeight.Medium, fontSize = 13.sp)
                }
            }

            if (detail.fiscalNumber.isNotBlank()) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("N° Documento Fiscal", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                    Text(detail.fiscalNumber, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary, fontSize = 13.sp)
                }
            }
        }
    }
}

/** Tarjeta con la observación de la nota de crédito. */
@Composable
private fun CreditNoteDetailObservacionCard(observacion: String) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Observación",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Text(
                text = observacion,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Tarjeta con la lista de productos devueltos. */
@Composable
private fun CreditNoteDetailProductsCard(
    lines: List<CreditNoteDetailLineDto>,
    currencySymbol: String = "$",
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Rounded.Inventory2,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Productos devueltos",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                ) {
                    Text(
                        text = "${lines.size} ${if (lines.size == 1) "item" else "items"}",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            if (lines.isEmpty()) {
                Text(
                    text = "No hay productos detallados en esta nota de crédito.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                lines.forEachIndexed { index, line ->
                    if (index > 0) {
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 4.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        )
                    }
                    CreditNoteProductLineRow(line = line, currencySymbol = currencySymbol)
                }
            }
        }
    }
}

/** Fila individual de producto devuelto con cantidad, descripción y montos. */
@Composable
private fun CreditNoteProductLineRow(
    line: CreditNoteDetailLineDto,
    currencySymbol: String = "$",
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = formatQuantity(line.cantidad),
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = line.descripcion,
                fontWeight = FontWeight.Medium,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val subinfo = listOfNotNull(
                line.codigo.takeIf { it.isNotBlank() },
                if (line.pIva > 0) "IVA ${formatAmount(line.pIva)}%" else "Exento",
            ).joinToString(" • ")
            if (subinfo.isNotBlank()) {
                Text(
                    text = subinfo,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        Column(horizontalAlignment = Alignment.End) {
            AdaptiveAmountText(
                text = "$currencySymbol ${formatAmount(line.totalConIva)}",
                baseStyle = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                options = AdaptiveAmountOptions(minFontSizeSp = 11f),
            )
            if (line.cantidad > 1.0) {
                Text(
                    text = "c/u $currencySymbol ${formatAmount(line.precioSinIva)}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Desglose de totales financieros de la nota de crédito. */
@Composable
private fun CreditNoteDetailTotalsCard(
    detail: CreditNoteDetailDto,
    currencySymbol: String = "$",
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Subtotal", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                Text("$currencySymbol ${formatAmount(detail.subtotal)}", fontSize = 13.sp, fontWeight = FontWeight.Medium)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Impuesto / ITBMS", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                Text("$currencySymbol ${formatAmount(detail.impuesto)}", fontSize = 13.sp, fontWeight = FontWeight.Medium)
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Total Nota de Crédito",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                AdaptiveAmountText(
                    text = "$currencySymbol ${formatAmount(detail.total)}",
                    baseStyle = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary,
                    options = AdaptiveAmountOptions(minFontSizeSp = 14f),
                )
            }
        }
    }
}

/** Botón para procesar la nota de crédito en la impresora fiscal. */
@Composable
private fun ProcessFiscalButton(
    isSubmitting: Boolean,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        enabled = !isSubmitting,
        modifier = Modifier.fillMaxWidth().height(56.dp),
        shape = RoundedCornerShape(12.dp),
        colors =
            ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
    ) {
        if (isSubmitting) {
            CircularProgressIndicator(color = PosPalette.FixedWhite, modifier = Modifier.size(24.dp))
        } else {
            Icon(Icons.Default.Print, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Procesar nota de crédito fiscal", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
    }
}

