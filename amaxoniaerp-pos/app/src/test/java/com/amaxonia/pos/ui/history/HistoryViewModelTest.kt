package com.amaxonia.pos.ui.history

import com.amaxonia.pos.domain.model.Transaction
import com.amaxonia.pos.domain.repository.InvoiceHistoryFilter
import com.amaxonia.pos.domain.repository.InvoiceHistoryPage
import com.amaxonia.pos.domain.repository.InvoiceHistoryRepository
import com.amaxonia.pos.domain.repository.InvoiceHistorySummary
import com.amaxonia.pos.domain.model.sales.FacturaDetalleResponseDto
import com.amaxonia.pos.test.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun initialStateLoadsPageAndBackendSummary() = runTest(mainDispatcherRule.dispatcher) {
        val repository = FakeInvoiceHistoryRepository()
        val viewModel = HistoryViewModel(repository)

        advanceUntilIdle()

        assertEquals(1, viewModel.state.value.transactions.size)
        assertEquals(250L, viewModel.state.value.totalTransactions)
        assertEquals(250, viewModel.state.value.summary.totalFacturas)
        assertEquals(1, repository.filters.size)
        assertEquals(InvoiceHistoryFilter(), repository.filters.single())
    }

    @Test
    fun applyAndClearFiltersUseTheCurrentFilter() = runTest(mainDispatcherRule.dispatcher) {
        val repository = FakeInvoiceHistoryRepository()
        val viewModel = HistoryViewModel(repository)
        advanceUntilIdle()

        viewModel.onUsuarioChanged("alice")
        viewModel.onSucursalChanged("7")
        viewModel.onFechaInicioChanged("2026-01-01")
        viewModel.onFechaFinChanged("2026-01-31")
        viewModel.onEstatusChanged("1,2")
        viewModel.applyFilters()
        advanceUntilIdle()

        assertEquals(
            InvoiceHistoryFilter(
                usuario = "alice",
                sucursalId = 7,
                fechaInicio = "2026-01-01",
                fechaFin = "2026-01-31",
                estatus = listOf(1, 2),
            ),
            repository.filters.last(),
        )

        viewModel.clearFilters()
        advanceUntilIdle()

        assertEquals(InvoiceHistoryFilter(), repository.filters.last())
    }

    @Test
    fun searchUsesDebounceBeforeReloading() = runTest(mainDispatcherRule.dispatcher) {
        val repository = FakeInvoiceHistoryRepository()
        val viewModel = HistoryViewModel(repository)
        advanceUntilIdle()
        val initialCalls = repository.filters.size

        viewModel.onSearchChanged("INV-001")
        advanceTimeBy(349)
        assertEquals(initialCalls, repository.filters.size)

        advanceTimeBy(1)
        advanceUntilIdle()

        assertEquals("INV-001", repository.filters.last().search)
    }

    private class FakeInvoiceHistoryRepository : InvoiceHistoryRepository {
        val filters = mutableListOf<InvoiceHistoryFilter>()
        private val transaction =
            Transaction(
                id = "invoice-1",
                invoiceNumber = "INV-001",
                time = "10:00",
                amount = 12.0,
                dateHeader = "01/01/2026",
            )

        override suspend fun getTransactions(
            filter: InvoiceHistoryFilter,
            limit: Int,
            offset: Long,
        ): Result<InvoiceHistoryPage> {
            filters += filter
            return Result.success(InvoiceHistoryPage(listOf(transaction), total = 250))
        }

        override suspend fun getSummary(filter: InvoiceHistoryFilter): Result<InvoiceHistorySummary> {
            return Result.success(InvoiceHistorySummary(ventasNetas = 999.0, totalFacturas = 250))
        }

        override suspend fun getAllTransactions(): Result<List<Transaction>> = Result.success(listOf(transaction))

        override suspend fun getTransactionById(id: String): Result<Transaction> = Result.success(transaction)

        override suspend fun saveTransaction(transaction: Transaction): Result<Unit> = Result.success(Unit)

        override suspend fun getInvoiceDetail(invoiceId: String): Result<FacturaDetalleResponseDto> =
            Result.success(FacturaDetalleResponseDto(invoiceId, transaction.invoiceNumber, emptyList()))
    }
}
