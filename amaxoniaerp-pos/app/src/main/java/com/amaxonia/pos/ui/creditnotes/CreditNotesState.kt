package com.amaxonia.pos.ui.creditnotes

import com.amaxonia.pos.domain.model.creditnote.CreditNoteDetailDto
import com.amaxonia.pos.domain.model.creditnote.CreditNoteSourceInvoiceDetailDto
import com.amaxonia.pos.domain.model.creditnote.CreditNoteSourceInvoiceSummaryDto
import com.amaxonia.pos.domain.model.creditnote.CreditNoteSummaryDto
import com.amaxonia.pos.domain.model.payment.FormaPago
import java.time.LocalDate
import java.time.YearMonth

enum class CreditNotesMode {
    LIST,
    INVOICE_PICKER,
    CREATE,
}

data class CreditNoteFormState(
    val fecha: String = LocalDate.now().toString(),
    val periodo: String = YearMonth.now().toString(),
    val observacion: String = "",
    val devolverStock: Boolean = true,
    val generarAbono: Boolean = true,
    val idFormaPagoReintegro: Int? = null,
)

enum class InvoiceDateFilterType(
    val label: String,
) {
    HOY("Hoy"),
    MES_ACTUAL("Mes actual"),
    MES_ANTERIOR("Mes anterior"),
    PERSONALIZADO("Personalizado"),
}

data class InvoiceDateFilter(
    val type: InvoiceDateFilterType = InvoiceDateFilterType.HOY,
    val customFechaInicio: String = LocalDate.now().toString(),
    val customFechaFin: String = LocalDate.now().toString(),
) {
    fun resolveDateRange(today: LocalDate = LocalDate.now()): Pair<String?, String?> =
        when (type) {
            InvoiceDateFilterType.HOY -> {
                today.toString() to today.toString()
            }
            InvoiceDateFilterType.MES_ACTUAL -> {
                val ym = YearMonth.from(today)
                ym.atDay(1).toString() to ym.atEndOfMonth().toString()
            }
            InvoiceDateFilterType.MES_ANTERIOR -> {
                val ym = YearMonth.from(today).minusMonths(1)
                ym.atDay(1).toString() to ym.atEndOfMonth().toString()
            }
            InvoiceDateFilterType.PERSONALIZADO -> {
                val inicio = customFechaInicio.trim().ifBlank { null }
                val fin = customFechaFin.trim().ifBlank { null }
                inicio to fin
            }
        }

    fun displayLabel(today: LocalDate = LocalDate.now()): String =
        when (type) {
            InvoiceDateFilterType.HOY -> "Hoy ($today)"
            InvoiceDateFilterType.MES_ACTUAL -> {
                val ym = YearMonth.from(today)
                "Mes actual (${formatYearMonthSpanish(ym)})"
            }
            InvoiceDateFilterType.MES_ANTERIOR -> {
                val ym = YearMonth.from(today).minusMonths(1)
                "Mes anterior (${formatYearMonthSpanish(ym)})"
            }
            InvoiceDateFilterType.PERSONALIZADO -> {
                val (inicio, fin) = resolveDateRange(today)
                if (inicio != null && fin != null) {
                    "$inicio al $fin"
                } else if (inicio != null) {
                    "Desde $inicio"
                } else if (fin != null) {
                    "Hasta $fin"
                } else {
                    "Rango personalizado"
                }
            }
        }
}

internal fun formatYearMonthSpanish(ym: YearMonth): String {
    val months =
        arrayOf(
            "Ene",
            "Feb",
            "Mar",
            "Abr",
            "May",
            "Jun",
            "Jul",
            "Ago",
            "Sep",
            "Oct",
            "Nov",
            "Dic",
        )
    val monthName = months.getOrNull(ym.monthValue - 1) ?: ym.month.name
    return "$monthName ${ym.year}"
}

data class CreditNotesState(
    val mode: CreditNotesMode = CreditNotesMode.LIST,
    val isLoading: Boolean = false,
    val isSubmitting: Boolean = false,
    val error: String? = null,
    val searchQuery: String = "",
    val invoiceSearchQuery: String = "",
    val invoiceDateFilter: InvoiceDateFilter = InvoiceDateFilter(),
    val isDateFilterCustomExpanded: Boolean = false,
    val creditNotes: List<CreditNoteSummaryDto> = emptyList(),
    val sourceInvoices: List<CreditNoteSourceInvoiceSummaryDto> = emptyList(),
    val selectedInvoice: CreditNoteSourceInvoiceDetailDto? = null,
    val selectedCreditNote: CreditNoteDetailDto? = null,
    val showCreditNoteDetail: Boolean = false,
    val form: CreditNoteFormState = CreditNoteFormState(),
    val availableRefundMethods: List<FormaPago> = emptyList(),
    val successMessage: String? = null,
    val currencySymbol: String = "$",
    val isPanama: Boolean = false,
)
