package com.amaxonia.erp.ui.clients

import com.amaxonia.erp.domain.model.AccountStatement
import com.amaxonia.erp.domain.model.AccountStatementItem
import com.amaxonia.erp.domain.model.Client
import com.amaxonia.erp.domain.model.ClientBranch
import com.amaxonia.erp.domain.repository.ClientRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ClientListViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeClientRepository: FakeClientRepository
    private lateinit var viewModel: ClientListViewModel

    private class FakeClientRepository : ClientRepository {
        var clientsList = mutableListOf(
            Client(id = "1", code = "000000001", identification = "8-888-888", name = "EMPRESA ALFA", saldo = 150.0),
            Client(id = "2", code = "000000002", identification = "9-999-999", name = "JUAN PEREZ", saldo = 0.0),
        )
        var statementResult: Result<AccountStatement> = Result.success(
            AccountStatement(
                clientId = "1",
                clientCode = "000000001",
                clientName = "EMPRESA ALFA",
                identification = "8-888-888",
                totalDebt = 350.0,
                totalCredit = 200.0,
                currentBalance = 150.0,
                items = listOf(
                    AccountStatementItem(
                        id = "c1",
                        fecha = "2026-10-01",
                        comprobante = "FACTURA",
                        comprobanteNumero = "F-101",
                        comprobanteFiscal = "FISCAL-001",
                        montoOriginal = 350.0,
                        debito = 350.0,
                        credito = 0.0,
                        saldo = 350.0,
                        status = "Pendiente",
                    ),
                ),
            ),
        )

        override suspend fun getAllClients(page: Int, pageSize: Int): Result<List<Client>> =
            Result.success(clientsList)

        override suspend fun searchClients(query: String, page: Int, pageSize: Int): Result<List<Client>> =
            Result.success(clientsList.filter { it.name.contains(query, ignoreCase = true) || it.code.contains(query) })

        override suspend fun createClient(client: Client): Result<Client> {
            val created = client.copy(id = "new-${clientsList.size + 1}", code = "00000000${clientsList.size + 1}")
            clientsList.add(created)
            return Result.success(created)
        }

        override suspend fun updateClient(id: String, client: Client): Result<Client> {
            val index = clientsList.indexOfFirst { it.id == id }
            if (index >= 0) {
                clientsList[index] = client
            }
            return Result.success(client)
        }

        override suspend fun getClientSucursales(clientId: String): Result<List<ClientBranch>> =
            Result.success(emptyList())

        override suspend fun getAccountStatement(clientId: String): Result<AccountStatement> =
            statementResult
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeClientRepository = FakeClientRepository()
        viewModel = ClientListViewModel(fakeClientRepository)
        testDispatcher.scheduler.advanceUntilIdle()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial load populates clients in state`() = runTest(testDispatcher) {
        val state = viewModel.state.value
        assertFalse(state.isLoading)
        assertEquals(2, state.clients.size)
        assertEquals("EMPRESA ALFA", state.clients[0].name)
        assertEquals(150.0, state.clients[0].saldo, 0.001)
    }

    @Test
    fun `openCreateForm sets isFormOpen true and editingClient null`() {
        viewModel.openCreateForm()
        assertTrue(viewModel.state.value.isFormOpen)
        assertNull(viewModel.state.value.editingClient)
    }

    @Test
    fun `openEditForm sets isFormOpen true and assigns editingClient`() {
        val client = fakeClientRepository.clientsList[0]
        viewModel.openEditForm(client)
        assertTrue(viewModel.state.value.isFormOpen)
        assertEquals(client, viewModel.state.value.editingClient)
    }

    @Test
    fun `closeForm resets form state`() {
        viewModel.openCreateForm()
        assertTrue(viewModel.state.value.isFormOpen)
        viewModel.closeForm()
        assertFalse(viewModel.state.value.isFormOpen)
        assertNull(viewModel.state.value.editingClient)
    }

    @Test
    fun `saveClient create calls repository and reloads list`() = runTest(testDispatcher) {
        val newClient = Client(name = "DISTRIBUIDORA BETA", identification = "7-777-777")
        viewModel.saveClient(newClient)
        advanceUntilIdle()

        assertFalse(viewModel.state.value.isFormOpen)
        assertEquals(3, viewModel.state.value.clients.size)
        assertTrue(viewModel.state.value.clients.any { it.name == "DISTRIBUIDORA BETA" })
    }

    @Test
    fun `saveClient update calls repository and reloads list`() = runTest(testDispatcher) {
        val existing = fakeClientRepository.clientsList[0].copy(name = "EMPRESA ALFA ACTUALIZADA")
        viewModel.saveClient(existing)
        advanceUntilIdle()

        assertFalse(viewModel.state.value.isFormOpen)
        assertEquals(2, viewModel.state.value.clients.size)
        assertEquals("EMPRESA ALFA ACTUALIZADA", viewModel.state.value.clients[0].name)
    }

    @Test
    fun `openAccountStatement fetches and populates statement`() = runTest(testDispatcher) {
        val client = fakeClientRepository.clientsList[0]
        viewModel.openAccountStatement(client)
        advanceUntilIdle()

        assertTrue(viewModel.state.value.isAccountStatementOpen)
        assertFalse(viewModel.state.value.isLoadingAccountStatement)
        assertNotNull(viewModel.state.value.selectedAccountStatement)
        assertEquals(150.0, viewModel.state.value.selectedAccountStatement?.currentBalance ?: 0.0, 0.001)
        assertEquals(1, viewModel.state.value.selectedAccountStatement?.items?.size)
    }

    @Test
    fun `closeAccountStatement resets statement state`() = runTest(testDispatcher) {
        val client = fakeClientRepository.clientsList[0]
        viewModel.openAccountStatement(client)
        advanceUntilIdle()

        viewModel.closeAccountStatement()
        assertFalse(viewModel.state.value.isAccountStatementOpen)
        assertNull(viewModel.state.value.selectedAccountStatement)
        assertNull(viewModel.state.value.selectedClientForStatement)
    }

    @Test
    fun `toggleStatementAdvancedSearch toggles visibility`() {
        val initial = viewModel.state.value.statementSearchVisible
        viewModel.toggleStatementAdvancedSearch()
        assertEquals(!initial, viewModel.state.value.statementSearchVisible)
        viewModel.toggleStatementAdvancedSearch()
        assertEquals(initial, viewModel.state.value.statementSearchVisible)
    }

    @Test
    fun `statement filters update correctly`() {
        viewModel.setStatementFilterTipo("FACT")
        assertEquals("FACT", viewModel.state.value.statementFilterTipo)

        viewModel.setStatementFilterNumero("101")
        assertEquals("101", viewModel.state.value.statementFilterNumero)

        viewModel.setStatementFilterSerieNumero("FISCAL-001")
        assertEquals("FISCAL-001", viewModel.state.value.statementFilterSerieNumero)

        viewModel.setStatementFilterMes("02", "2024")
        assertEquals("02", viewModel.state.value.statementFilterMes)
        assertEquals("2024", viewModel.state.value.statementFilterAnio)
        assertEquals("01/02/2024", viewModel.state.value.statementFilterFechaInicio)
        assertEquals("29/02/2024", viewModel.state.value.statementFilterFechaFin)

        viewModel.setStatementFilterMesFin("03", "2024")
        assertEquals("03", viewModel.state.value.statementFilterMesFin)
        assertEquals("31/03/2024", viewModel.state.value.statementFilterFechaFin)
    }

    @Test
    fun `status filter toggles operate independently`() {
        assertTrue(viewModel.state.value.statementStatusPendiente)
        assertTrue(viewModel.state.value.statementStatusPagado)
        assertTrue(viewModel.state.value.statementStatusAnulado)

        viewModel.toggleStatementStatusPendiente()
        assertFalse(viewModel.state.value.statementStatusPendiente)
        assertTrue(viewModel.state.value.statementStatusPagado)

        viewModel.toggleStatementStatusPagado()
        assertFalse(viewModel.state.value.statementStatusPagado)

        viewModel.toggleStatementStatusAnulado()
        assertFalse(viewModel.state.value.statementStatusAnulado)
    }

    @Test
    fun `clearStatementFilters resets all statement filters`() {
        viewModel.setStatementFilterTipo("FACT")
        viewModel.setStatementFilterNumero("999")
        viewModel.setStatementFilterSerieNumero("SERIE-X")
        viewModel.toggleStatementStatusPendiente()

        viewModel.clearStatementFilters()

        assertEquals("", viewModel.state.value.statementFilterTipo)
        assertEquals("", viewModel.state.value.statementFilterNumero)
        assertEquals("", viewModel.state.value.statementFilterSerieNumero)
        assertTrue(viewModel.state.value.statementStatusPendiente)
        assertTrue(viewModel.state.value.statementStatusPagado)
        assertTrue(viewModel.state.value.statementStatusAnulado)
    }

    @Test
    fun `statement export and email actions set user feedback message`() {
        viewModel.onExportExcel()
        assertNotNull(viewModel.state.value.statementActionMessage)
        assertTrue(viewModel.state.value.statementActionMessage!!.contains("Excel"))

        viewModel.dismissStatementActionMessage()
        assertNull(viewModel.state.value.statementActionMessage)

        viewModel.onExportPdf()
        assertNotNull(viewModel.state.value.statementActionMessage)
        assertTrue(viewModel.state.value.statementActionMessage!!.contains("PDF"))

        viewModel.onSendEmail()
        assertNotNull(viewModel.state.value.statementActionMessage)
        assertTrue(viewModel.state.value.statementActionMessage!!.contains("correo"))
    }
}
