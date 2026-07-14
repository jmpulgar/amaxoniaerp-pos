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

data class CreditNotesState(
    val mode: CreditNotesMode = CreditNotesMode.LIST,
    val isLoading: Boolean = false,
    val isSubmitting: Boolean = false,
    val error: String? = null,
    val searchQuery: String = "",
    val invoiceSearchQuery: String = "",
    val creditNotes: List<CreditNoteSummaryDto> = emptyList(),
    val sourceInvoices: List<CreditNoteSourceInvoiceSummaryDto> = emptyList(),
    val selectedInvoice: CreditNoteSourceInvoiceDetailDto? = null,
    val selectedCreditNote: CreditNoteDetailDto? = null,
    val showCreditNoteDetail: Boolean = false,
    val form: CreditNoteFormState = CreditNoteFormState(),
    val availableRefundMethods: List<FormaPago> = emptyList(),
    val successMessage: String? = null,
)
