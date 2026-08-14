@file:Suppress("MagicNumber", "UnusedPrivateMember", "LongMethod")

package com.amaxonia.pos.ui.history

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
import com.amaxonia.pos.domain.model.Transaction
import com.amaxonia.pos.domain.model.TransactionStatus
import com.amaxonia.pos.domain.repository.InvoiceHistoryFilter
import com.amaxonia.pos.ui.theme.PosTheme

/**
 * Visual regression surface for the invoice-history screen.
 *
 * Exercises the production composables — [HistoryFilters] (plegado y expandido),
 * [SummaryBar] and [TransactionCard] — at the target Android widths, including the
 * trickiest variants: enormous totals that must shrink via AdaptiveAmountText instead
 * of clipping, long client names that must ellipsize, and every transaction status.
 */
@Preview(name = "Filtros plegados · 320×568", showBackground = true, widthDp = 320, heightDp = 568)
@Composable
private fun HistoryFiltersCollapsed320() = PosTheme {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        HistoryFilters(
            filter = InvoiceHistoryFilter(search = "FAC-2026"),
            onSearchChanged = {},
            onUsuarioChanged = {},
            onSucursalChanged = {},
            onFechaInicioChanged = {},
            onFechaFinChanged = {},
            onEstatusChanged = {},
            onApply = {},
            onClear = {},
        )
    }
}

@Preview(name = "Filtros expandidos · 360×640", showBackground = true, widthDp = 360, heightDp = 640)
@Composable
private fun HistoryFiltersExpanded360() = PosTheme {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        HistoryFilters(
            filter =
                InvoiceHistoryFilter(
                    search = "FAC-2026",
                    usuario = "jperez",
                    sucursalId = 12,
                    fechaInicio = "2026-08-01",
                    fechaFin = "2026-08-14",
                    estatus = listOf(1, 3),
                ),
            onSearchChanged = {},
            onUsuarioChanged = {},
            onSucursalChanged = {},
            onFechaInicioChanged = {},
            onFechaFinChanged = {},
            onEstatusChanged = {},
            onApply = {},
            onClear = {},
        )
    }
}

@Preview(name = "Summary bar · 320×568 · monto enorme", showBackground = true, widthDp = 320, heightDp = 568)
@Composable
private fun HistorySummaryBarHuge() = PosTheme {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        SummaryBar(
            totalFacturas = 248,
            totalMonto = 9_876_543.21,
            currency = "USD",
        )
    }
}

@Preview(name = "Fila factura · 320×568 · estados + monto enorme", showBackground = true, widthDp = 320, heightDp = 568)
@Composable
private fun HistoryTransactionCards320() = PosTheme {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        HistoryTransactionCardsPreviewContent()
    }
}

@Preview(name = "Fila factura · landscape · 733×360", showBackground = true, widthDp = 733, heightDp = 360)
@Composable
private fun HistoryTransactionCardsLandscape() = PosTheme {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        HistoryTransactionCardsPreviewContent()
    }
}

@Composable
private fun HistoryTransactionCardsPreviewContent() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        TransactionCard(
            transaction =
                previewTransaction(
                    id = "1",
                    invoiceNumber = "FAC-2026-000123",
                    cliente = "María Fernández de la Torre",
                    amount = 248.75,
                    status = TransactionStatus.PAID,
                ),
            onClick = {},
            modifier = Modifier.fillMaxWidth(),
        )
        TransactionCard(
            transaction =
                previewTransaction(
                    id = "2",
                    invoiceNumber = "FAC-2026-000124",
                    cliente = "Constructorora Andina del Centro C.A.",
                    amount = 9_876_543.21,
                    status = TransactionStatus.PENDING,
                ),
            onClick = {},
            modifier = Modifier.fillMaxWidth(),
        )
        TransactionCard(
            transaction =
                previewTransaction(
                    id = "3",
                    invoiceNumber = "FAC-2026-000125",
                    cliente = "",
                    amount = 12.50,
                    status = TransactionStatus.CANCELLED,
                ),
            onClick = {},
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private fun previewTransaction(
    id: String,
    invoiceNumber: String,
    cliente: String,
    amount: Double,
    status: TransactionStatus,
): Transaction =
    Transaction(
        id = id,
        invoiceNumber = invoiceNumber,
        time = "14:32",
        amount = amount,
        currency = "USD",
        status = status,
        dateHeader = "Hoy, 14 de agosto",
        clienteNombre = cliente,
        formaPago = "Efectivo",
        totalRef = if (amount > 1_000.0) amount * 40.0 else null,
        abrMonedaSecundaria = if (amount > 1_000.0) "VES" else null,
    )
