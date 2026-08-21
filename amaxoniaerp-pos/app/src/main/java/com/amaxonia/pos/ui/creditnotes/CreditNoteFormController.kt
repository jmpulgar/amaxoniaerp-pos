package com.amaxonia.pos.ui.creditnotes

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/**
 * Edición de los campos del formulario de nota de crédito. Mantiene los updates
 * de estado del formulario agrupados fuera del ViewModel.
 */
class CreditNoteFormController(
    private val state: MutableStateFlow<CreditNotesState>,
) {
    fun onFechaChange(value: String) {
        state.update { it.copy(form = it.form.copy(fecha = value)) }
    }

    fun onPeriodoChange(value: String) {
        state.update { it.copy(form = it.form.copy(periodo = value)) }
    }

    fun onObservacionChange(value: String) {
        state.update { it.copy(form = it.form.copy(observacion = value)) }
    }

    fun onDevolverStockChange(enabled: Boolean) {
        state.update { it.copy(form = it.form.copy(devolverStock = enabled)) }
    }

    fun onGenerarAbonoChange(generar: Boolean) {
        state.update {
            it.copy(
                form =
                    it.form.copy(
                        generarAbono = generar,
                        idFormaPagoReintegro = if (generar) null else it.form.idFormaPagoReintegro,
                    ),
            )
        }
    }

    fun onRefundMethodChange(idFormaPago: Int?) {
        state.update { it.copy(form = it.form.copy(idFormaPagoReintegro = idFormaPago)) }
    }
}
