package com.amaxonia.erp.ui.clients

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.amaxonia.erp.domain.model.AccountStatement
import com.amaxonia.erp.domain.model.Client
import com.amaxonia.erp.domain.repository.ClientRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ClientListState(
    val clients: List<Client> = emptyList(),
    val isLoading: Boolean = false,
    val searchQuery: String = "",
    val page: Int = 1,
    val endOfListReached: Boolean = false,
    val error: String? = null,
    val editingClient: Client? = null,
    val isFormOpen: Boolean = false,
    val isSavingClient: Boolean = false,
    val saveClientError: String? = null,
    val isAccountStatementOpen: Boolean = false,
    val selectedAccountStatement: AccountStatement? = null,
    val isLoadingAccountStatement: Boolean = false,
    val accountStatementError: String? = null,
    val selectedClientForStatement: Client? = null,
    val statementSearchVisible: Boolean = true,
    val statementFilterTipo: String = "",
    val statementFilterNumero: String = "",
    val statementFilterSerieNumero: String = "",
    val statementFilterMes: String = "",
    val statementFilterAnio: String = "",
    val statementFilterFechaInicio: String = "",
    val statementFilterMesFin: String = "",
    val statementFilterAnioFin: String = "",
    val statementFilterFechaFin: String = "",
    val statementStatusPendiente: Boolean = true,
    val statementStatusPagado: Boolean = true,
    val statementStatusAnulado: Boolean = true,
    val statementActionMessage: String? = null,
)

class ClientListViewModel(
    private val clientRepository: ClientRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(ClientListState())
    val state: StateFlow<ClientListState> = _state.asStateFlow()
    private val pageSize = 20
    private var searchJob: Job? = null

    init {
        loadClients(reset = true)
    }

    fun onSearchQueryChange(query: String) {
        _state.update { it.copy(searchQuery = query, error = null) }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            kotlinx.coroutines.delay(300)
            loadClients(reset = true)
        }
    }

    fun loadMore() {
        if (_state.value.isLoading || _state.value.endOfListReached) return
        loadClients(reset = false)
    }

    fun retry() {
        loadClients(reset = true)
    }

    fun openCreateForm() {
        _state.update {
            it.copy(
                isFormOpen = true,
                editingClient = null,
                saveClientError = null,
                isSavingClient = false,
            )
        }
    }

    fun openEditForm(client: Client) {
        _state.update {
            it.copy(
                isFormOpen = true,
                editingClient = client,
                saveClientError = null,
                isSavingClient = false,
            )
        }
    }

    fun closeForm() {
        _state.update {
            it.copy(
                isFormOpen = false,
                editingClient = null,
                saveClientError = null,
                isSavingClient = false,
            )
        }
    }

    fun openAccountStatement(client: Client) {
        _state.update {
            it.copy(
                isAccountStatementOpen = true,
                selectedClientForStatement = client,
                isLoadingAccountStatement = true,
                accountStatementError = null,
                selectedAccountStatement = null,
            )
        }
        viewModelScope.launch {
            val clientId = client.id.ifBlank { client.code }
            clientRepository.getAccountStatement(clientId).fold(
                onSuccess = { statement ->
                    _state.update {
                        it.copy(
                            isLoadingAccountStatement = false,
                            selectedAccountStatement = statement,
                            accountStatementError = null,
                        )
                    }
                },
                onFailure = { error ->
                    _state.update {
                        it.copy(
                            isLoadingAccountStatement = false,
                            accountStatementError = error.message ?: "Error al cargar estado de cuenta",
                        )
                    }
                },
            )
        }
    }

    fun closeAccountStatement() {
        _state.update {
            it.copy(
                isAccountStatementOpen = false,
                selectedAccountStatement = null,
                selectedClientForStatement = null,
                accountStatementError = null,
                isLoadingAccountStatement = false,
                statementActionMessage = null,
            )
        }
    }

    fun toggleStatementAdvancedSearch() {
        _state.update { it.copy(statementSearchVisible = !it.statementSearchVisible) }
    }

    fun setStatementFilterTipo(tipo: String) {
        _state.update { it.copy(statementFilterTipo = tipo) }
    }

    fun setStatementFilterNumero(numero: String) {
        _state.update { it.copy(statementFilterNumero = numero) }
    }

    fun setStatementFilterSerieNumero(serie: String) {
        _state.update { it.copy(statementFilterSerieNumero = serie) }
    }

    fun setStatementFilterMes(mes: String, anioInput: String? = null) {
        val currentYear = anioInput ?: _state.value.statementFilterAnio.ifBlank {
            java.util.Calendar.getInstance().get(java.util.Calendar.YEAR).toString()
        }
        val (inicio, fin) = if (mes.isNotBlank()) {
            val y = currentYear.toIntOrNull() ?: java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
            val m = mes.toIntOrNull() ?: 1
            val days = getDaysInMonth(m, y)
            val formattedStart = "01/%02d/%04d".format(m, y)
            val formattedEnd = "%02d/%02d/%04d".format(days, m, y)
            formattedStart to formattedEnd
        } else {
            "" to ""
        }
        _state.update {
            it.copy(
                statementFilterMes = mes,
                statementFilterAnio = if (mes.isNotBlank()) currentYear else "",
                statementFilterFechaInicio = inicio,
                statementFilterFechaFin = fin,
                statementFilterMesFin = mes,
                statementFilterAnioFin = if (mes.isNotBlank()) currentYear else "",
            )
        }
    }

    fun setStatementFilterAnio(anio: String) {
        val mes = _state.value.statementFilterMes
        if (mes.isNotBlank()) {
            setStatementFilterMes(mes, anio)
        } else {
            _state.update { it.copy(statementFilterAnio = anio) }
        }
    }

    fun setStatementFilterMesFin(mes: String, anioInput: String? = null) {
        val currentYear = anioInput ?: _state.value.statementFilterAnioFin.ifBlank {
            java.util.Calendar.getInstance().get(java.util.Calendar.YEAR).toString()
        }
        val fin = if (mes.isNotBlank()) {
            val y = currentYear.toIntOrNull() ?: java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
            val m = mes.toIntOrNull() ?: 1
            val days = getDaysInMonth(m, y)
            "%02d/%02d/%04d".format(days, m, y)
        } else {
            ""
        }
        _state.update {
            it.copy(
                statementFilterMesFin = mes,
                statementFilterAnioFin = if (mes.isNotBlank()) currentYear else "",
                statementFilterFechaFin = fin,
            )
        }
    }

    fun setStatementFilterAnioFin(anio: String) {
        val mes = _state.value.statementFilterMesFin
        if (mes.isNotBlank()) {
            setStatementFilterMesFin(mes, anio)
        } else {
            _state.update { it.copy(statementFilterAnioFin = anio) }
        }
    }

    fun setStatementFilterFechaInicio(fecha: String) {
        _state.update { it.copy(statementFilterFechaInicio = fecha) }
    }

    fun setStatementFilterFechaFin(fecha: String) {
        _state.update { it.copy(statementFilterFechaFin = fecha) }
    }

    fun toggleStatementStatusPendiente() {
        _state.update { it.copy(statementStatusPendiente = !it.statementStatusPendiente) }
    }

    fun toggleStatementStatusPagado() {
        _state.update { it.copy(statementStatusPagado = !it.statementStatusPagado) }
    }

    fun toggleStatementStatusAnulado() {
        _state.update { it.copy(statementStatusAnulado = !it.statementStatusAnulado) }
    }

    fun clearStatementFilters() {
        _state.update {
            it.copy(
                statementFilterTipo = "",
                statementFilterNumero = "",
                statementFilterSerieNumero = "",
                statementFilterMes = "",
                statementFilterAnio = "",
                statementFilterFechaInicio = "",
                statementFilterMesFin = "",
                statementFilterAnioFin = "",
                statementFilterFechaFin = "",
                statementStatusPendiente = true,
                statementStatusPagado = true,
                statementStatusAnulado = true,
            )
        }
    }

    fun dismissStatementActionMessage() {
        _state.update { it.copy(statementActionMessage = null) }
    }

    fun onExportExcel() {
        _state.update { it.copy(statementActionMessage = "Generando archivo Excel del estado de cuenta...") }
    }

    fun onExportDetailExcel() {
        _state.update { it.copy(statementActionMessage = "Generando archivo Excel detallado...") }
    }

    fun onExportPdf() {
        _state.update { it.copy(statementActionMessage = "Generando reporte PDF del estado de cuenta...") }
    }

    fun onSendEmail() {
        _state.update { it.copy(statementActionMessage = "Enviando estado de cuenta por correo...") }
    }

    private fun getDaysInMonth(month: Int, year: Int): Int =
        when (month) {
            1, 3, 5, 7, 8, 10, 12 -> 31
            4, 6, 9, 11 -> 30
            2 -> if ((year % 4 == 0 && year % 100 != 0) || year % 400 == 0) 29 else 28
            else -> 30
        }

    fun saveClient(client: Client) {
        viewModelScope.launch {
            _state.update { it.copy(isSavingClient = true, saveClientError = null) }
            val isEdit = _state.value.editingClient != null || client.id.isNotBlank()
            val updateId =
                client.id.ifBlank {
                    _state.value.editingClient?.id?.ifBlank { _state.value.editingClient?.code } ?: client.code
                }
            val result =
                if (isEdit && updateId.isNotBlank()) {
                    clientRepository.updateClient(updateId, client)
                } else {
                    clientRepository.createClient(client)
                }
            result.fold(
                onSuccess = {
                    _state.update { it.copy(isSavingClient = false, saveClientError = null) }
                    closeForm()
                    loadClients(reset = true)
                },
                onFailure = { error ->
                    _state.update {
                        it.copy(
                            isSavingClient = false,
                            saveClientError = error.message ?: "Error al guardar cliente",
                        )
                    }
                },
            )
        }
    }

    private fun loadClients(reset: Boolean) {
        viewModelScope.launch {
            _state.update {
                it.copy(
                    isLoading = true,
                    page = if (reset) 1 else it.page + 1,
                    clients = if (reset) emptyList() else it.clients,
                    endOfListReached = if (reset) false else it.endOfListReached,
                    error = null,
                )
            }
            val query = _state.value.searchQuery.trim()
            val result = if (query.isEmpty()) {
                clientRepository.getAllClients(page = _state.value.page, pageSize = pageSize)
            } else {
                clientRepository.searchClients(query = query, page = _state.value.page, pageSize = pageSize)
            }
            result.fold(
                onSuccess = { newClients ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            clients = if (reset) newClients else it.clients + newClients,
                            endOfListReached = newClients.size < pageSize,
                            error = null,
                        )
                    }
                },
                onFailure = { error ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            error = error.message ?: "Error al cargar clientes",
                        )
                    }
                },
            )
        }
    }
}
