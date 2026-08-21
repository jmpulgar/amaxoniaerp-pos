package com.amaxonia.pos.ui.creditnotes

import com.amaxonia.pos.domain.model.creditnote.CreateCreditNoteRequestDto
import com.amaxonia.pos.domain.model.creditnote.CreditNoteSettlementTypeDto
import com.amaxonia.pos.domain.model.creditnote.CreditNoteSourceInvoiceDetailDto

/** Tipo de liquidación derivado del formulario: abono, reintegro o ninguno. */
fun buildSettlementType(form: CreditNoteFormState): CreditNoteSettlementTypeDto =
    if (form.generarAbono) {
        CreditNoteSettlementTypeDto.ABONO
    } else if (form.idFormaPagoReintegro != null) {
        CreditNoteSettlementTypeDto.REINTEGRO
    } else {
        CreditNoteSettlementTypeDto.NINGUNO
    }

/** Construye el request de creación de nota de crédito (devolución total). */
fun buildCreateCreditNoteRequest(
    invoice: CreditNoteSourceInvoiceDetailDto,
    form: CreditNoteFormState,
    idCajaSecuencia: String,
): CreateCreditNoteRequestDto =
    CreateCreditNoteRequestDto(
        idFactura = invoice.id,
        fecha = form.fecha,
        periodo = form.periodo,
        observacion = form.observacion,
        detalle = emptyList(), // Devolución total
        anular = true, // Siempre se anula
        devolverStock = form.devolverStock,
        idCajaSecuencia = idCajaSecuencia,
        settlementType = buildSettlementType(form),
        idFormaPagoReintegro = form.idFormaPagoReintegro,
    )
