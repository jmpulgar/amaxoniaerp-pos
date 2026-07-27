package com.amaxonia.pos.domain.repository

import com.amaxonia.pos.domain.model.mesas.SelectedTable
import kotlinx.coroutines.flow.StateFlow

/**
 * Mesa seleccionada, únicamente en memoria.
 *
 * Deliberadamente no se persiste ni se sincroniza: en esta fase seleccionar una mesa no crea
 * ningún registro operativo. Es el punto de enganche para la apertura de mesa de la fase siguiente.
 */
interface SelectedTableHolder {
    val selectedTable: StateFlow<SelectedTable?>

    fun select(table: SelectedTable)

    fun clear()
}
