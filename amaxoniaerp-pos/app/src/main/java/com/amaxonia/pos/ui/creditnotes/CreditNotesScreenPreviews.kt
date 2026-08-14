package com.amaxonia.pos.ui.creditnotes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.amaxonia.pos.domain.model.creditnote.CreditNoteFiscalStatusDto
import com.amaxonia.pos.domain.model.creditnote.CreditNoteSourceInvoiceSummaryDto
import com.amaxonia.pos.domain.model.creditnote.CreditNoteSummaryDto
import com.amaxonia.pos.ui.theme.PosTheme

private const val PREVIEW_HUGE_AMOUNT = 9_876_543.21
private const val PREVIEW_TAX_DIVISOR = 1.16
private const val PREVIEW_SOURCE_ITEMS = 4

@Preview(name = "Summary banner · 320×568 · monto enorme", showBackground = true, widthDp = 320, heightDp = 568)
@Composable
internal fun CreditNotesSummaryBannerHuge() =
    PosTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            SummaryBanner(
                title = "Devoluciones registradas",
                value = "24",
                amount = PREVIEW_HUGE_AMOUNT,
                modifier = Modifier.padding(16.dp),
            )
        }
    }

@Preview(name = "Tarjetas NC · 320×568 · estados + monto enorme", showBackground = true, widthDp = 320, heightDp = 568)
@Composable
internal fun CreditNoteCards320() =
    PosTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            CreditNoteCardsPreviewContent()
        }
    }

@Preview(name = "Tarjetas NC · 412×915", showBackground = true, widthDp = 412, heightDp = 915)
@Composable
internal fun CreditNoteCards412() =
    PosTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            CreditNoteCardsPreviewContent()
        }
    }

@Preview(name = "Tarjetas NC · landscape · 733×360", showBackground = true, widthDp = 733, heightDp = 360)
@Composable
internal fun CreditNoteCardsLandscape() =
    PosTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            CreditNoteCardsPreviewContent()
        }
    }

@Preview(name = "Facturas elegibles · 320×568 · saldo enorme", showBackground = true, widthDp = 320, heightDp = 568)
@Composable
internal fun SourceInvoiceCards320() =
    PosTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SourceInvoiceCard(
                    invoice = previewSourceInvoice(cliente = "María Fernández", remaining = 248.75),
                    onClick = {},
                )
                SourceInvoiceCard(
                    invoice =
                        previewSourceInvoice(
                            cliente = "Constructora Andina del Centro C.A.",
                            remaining = PREVIEW_HUGE_AMOUNT,
                        ),
                    onClick = {},
                )
            }
        }
    }

@Composable
internal fun CreditNoteCardsPreviewContent() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        CreditNoteCard(
            note = previewNote(id = "1", total = 248.75, fiscal = CreditNoteFiscalStatusDto.CONFIRMADA),
            onClick = {},
        )
        CreditNoteCard(
            note =
                previewNote(
                    id = "2",
                    total = PREVIEW_HUGE_AMOUNT,
                    fiscal = CreditNoteFiscalStatusDto.PENDIENTE,
                    cliente = "Constructora Andina del Centro C.A.",
                ),
            onClick = {},
        )
    }
}

private fun previewNote(
    id: String,
    total: Double,
    fiscal: CreditNoteFiscalStatusDto,
    cliente: String = "María Fernández",
): CreditNoteSummaryDto {
    val subtotal = total / PREVIEW_TAX_DIVISOR
    return CreditNoteSummaryDto(
        id = id,
        codigo = "NC-2026-0000$id",
        facturaId = "f-$id",
        facturaCodigo = "FAC-2026-00012$id",
        fecha = "14/08/2026",
        fechaCreacion = "2026-08-14T14:32:00",
        clienteNombre = cliente,
        clienteIdentificacion = "V-12345678",
        total = total,
        subtotal = subtotal,
        impuesto = total - subtotal,
        fiscalStatus = fiscal,
    )
}

private fun previewSourceInvoice(
    cliente: String,
    remaining: Double,
): CreditNoteSourceInvoiceSummaryDto =
    CreditNoteSourceInvoiceSummaryDto(
        id = "inv-$cliente",
        codigo = "FAC-2026-000200",
        codigoFiscal = "",
        numeroDocumentoFiscal = "",
        fecha = "12/08/2026",
        clienteNombre = cliente,
        clienteIdentificacion = "V-87654321",
        total = remaining,
        remainingAmount = remaining,
        items = PREVIEW_SOURCE_ITEMS,
        moneda = "USD",
    )
