@file:Suppress("MagicNumber", "UnusedPrivateMember", "LongMethod")

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

/**
 * Visual regression surface for the credit-notes screen.
 *
 * Exercises the production composables — [SummaryBanner], [CreditNoteCard] and
 * [SourceInvoiceCard] — at the target Android widths plus landscape, including
 * enormous totals that must shrink via AdaptiveAmountText, long client names that
 * must ellipsize, and both fiscal statuses (confirmada / pendiente).
 */
@Preview(name = "Summary banner · 320×568 · monto enorme", showBackground = true, widthDp = 320, heightDp = 568)
@Composable
private fun CreditNotesSummaryBannerHuge() = PosTheme {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        SummaryBanner(
            title = "Devoluciones registradas",
            value = "24",
            amount = 9_876_543.21,
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Preview(name = "Tarjetas NC · 320×568 · estados + monto enorme", showBackground = true, widthDp = 320, heightDp = 568)
@Composable
private fun CreditNoteCards320() = PosTheme {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        CreditNoteCardsPreviewContent()
    }
}

@Preview(name = "Tarjetas NC · 412×915", showBackground = true, widthDp = 412, heightDp = 915)
@Composable
private fun CreditNoteCards412() = PosTheme {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        CreditNoteCardsPreviewContent()
    }
}

@Preview(name = "Tarjetas NC · landscape · 733×360", showBackground = true, widthDp = 733, heightDp = 360)
@Composable
private fun CreditNoteCardsLandscape() = PosTheme {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        CreditNoteCardsPreviewContent()
    }
}

@Preview(name = "Facturas elegibles · 320×568 · saldo enorme", showBackground = true, widthDp = 320, heightDp = 568)
@Composable
private fun SourceInvoiceCards320() = PosTheme {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SourceInvoiceCard(
                invoice = previewSourceInvoice(cliente = "María Fernández", remaining = 248.75),
                onClick = {},
                modifier = Modifier.fillMaxWidth(),
            )
            SourceInvoiceCard(
                invoice =
                    previewSourceInvoice(
                        cliente = "Constructorora Andina del Centro C.A.",
                        remaining = 9_876_543.21,
                    ),
                onClick = {},
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun CreditNoteCardsPreviewContent() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        CreditNoteCard(
            note = previewNote(id = "1", total = 248.75, fiscal = CreditNoteFiscalStatusDto.CONFIRMADA),
            onClick = {},
            modifier = Modifier.fillMaxWidth(),
        )
        CreditNoteCard(
            note =
                previewNote(
                    id = "2",
                    total = 9_876_543.21,
                    fiscal = CreditNoteFiscalStatusDto.PENDIENTE,
                    cliente = "Constructorora Andina del Centro C.A.",
                ),
            onClick = {},
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private fun previewNote(
    id: String,
    total: Double,
    fiscal: CreditNoteFiscalStatusDto,
    cliente: String = "María Fernández",
): CreditNoteSummaryDto =
    CreditNoteSummaryDto(
        id = id,
        codigo = "NC-2026-0000$id",
        facturaId = "f-$id",
        facturaCodigo = "FAC-2026-00012$id",
        fecha = "14/08/2026",
        fechaCreacion = "2026-08-14T14:32:00",
        clienteNombre = cliente,
        clienteIdentificacion = "V-12345678",
        total = total,
        subtotal = total / 1.16,
        impuesto = total - total / 1.16,
        fiscalStatus = fiscal,
    )

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
        items = 4,
        moneda = "USD",
    )
