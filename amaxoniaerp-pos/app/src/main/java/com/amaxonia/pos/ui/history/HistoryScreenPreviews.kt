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

private const val HUGE_TOTAL = 9_876_543.21
private const val LARGE_AMOUNT_THRESHOLD = 1_000.0
private const val SECONDARY_RATE = 40.0
private const val SUMMARY_INVOICE_COUNT = 248
private const val PAID_STATUS_ID = 1
private const val CANCELLED_STATUS_ID = 3

@Preview(name = "Filtros plegados · 320×568", showBackground = true, widthDp = 320, heightDp = 568)
@Composable
internal fun HistoryFiltersCollapsed320() =
    PosTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            HistorySearchField(
                value = "FAC-2026",
                onValueChange = {},
                filtersExpanded = false,
                onToggleFilters = {},
                modifier = Modifier.padding(16.dp),
            )
        }
    }

@Preview(name = "Filtros expandidos · 360×640", showBackground = true, widthDp = 360, heightDp = 640)
@Composable
internal fun HistoryFiltersExpanded360() =
    PosTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            val filter =
                InvoiceHistoryFilter(
                    search = "FAC-2026",
                    usuario = "jperez",
                    sucursalId = 12,
                    fechaInicio = "2026-08-01",
                    fechaFin = "2026-08-14",
                    estatus = listOf(PAID_STATUS_ID, CANCELLED_STATUS_ID),
                )
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                HistorySearchField(
                    value = filter.search.orEmpty(),
                    onValueChange = {},
                    filtersExpanded = true,
                    onToggleFilters = {},
                )
                HistoryIdentityFilters(filter, {}, {})
                HistoryDateRangeFilters(filter, {}, {})
                HistoryStatusFilter(filter, {})
                HistoryFilterActions({}, {})
            }
        }
    }

@Preview(name = "Summary bar · 320×568 · monto enorme", showBackground = true, widthDp = 320, heightDp = 568)
@Composable
internal fun HistorySummaryBarHuge() =
    PosTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            SummaryBar(
                totalFacturas = SUMMARY_INVOICE_COUNT,
                totalMonto = HUGE_TOTAL,
                currency = "USD",
            )
        }
    }

@Preview(name = "Fila factura · 320×568 · estados + monto enorme", showBackground = true, widthDp = 320, heightDp = 568)
@Composable
internal fun HistoryTransactionCards320() =
    PosTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            HistoryTransactionCardsPreviewContent()
        }
    }

@Preview(name = "Fila factura · landscape · 733×360", showBackground = true, widthDp = 733, heightDp = 360)
@Composable
internal fun HistoryTransactionCardsLandscape() =
    PosTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            HistoryTransactionCardsPreviewContent()
        }
    }

@Composable
internal fun HistoryTransactionCardsPreviewContent() {
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
        )
        TransactionCard(
            transaction =
                previewTransaction(
                    id = "2",
                    invoiceNumber = "FAC-2026-000124",
                    cliente = "Constructora Andina del Centro C.A.",
                    amount = HUGE_TOTAL,
                    status = TransactionStatus.PENDING,
                ),
            onClick = {},
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
        totalRef = if (amount > LARGE_AMOUNT_THRESHOLD) amount * SECONDARY_RATE else null,
        abrMonedaSecundaria = if (amount > LARGE_AMOUNT_THRESHOLD) "VES" else null,
    )
