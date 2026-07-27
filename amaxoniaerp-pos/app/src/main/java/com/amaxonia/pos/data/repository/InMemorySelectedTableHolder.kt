package com.amaxonia.pos.data.repository

import com.amaxonia.pos.domain.model.mesas.SelectedTable
import com.amaxonia.pos.domain.repository.SelectedTableHolder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Selección de mesa en memoria de proceso. Se pierde al cerrar la app a propósito: mientras no
 * exista una sesión de mesa en el backend, persistirla daría la falsa impresión de que la mesa
 * quedó tomada.
 */
class InMemorySelectedTableHolder : SelectedTableHolder {
    private val _selectedTable = MutableStateFlow<SelectedTable?>(null)
    override val selectedTable: StateFlow<SelectedTable?> = _selectedTable.asStateFlow()

    override fun select(table: SelectedTable) {
        _selectedTable.value = table
    }

    override fun clear() {
        _selectedTable.value = null
    }
}
