package com.amaxonia.erp.data.repository

import com.amaxonia.erp.data.local.LocalStore
import com.amaxonia.erp.data.local.db.PendingInvoiceDao
import com.amaxonia.erp.data.local.db.PendingInvoiceEntity
import com.amaxonia.erp.data.remote.ApiClient
import com.amaxonia.erp.data.remote.ApiService
import com.amaxonia.erp.data.remote.AppJson
import com.amaxonia.erp.data.remote.NetworkMonitor
import com.amaxonia.erp.data.remote.dto.ProcessSaleRequestDto
import com.amaxonia.erp.data.remote.dto.SaleInvoiceDto
import com.amaxonia.erp.data.remote.dto.SaleItemDto
import com.amaxonia.erp.data.remote.dto.SalePaymentSummaryDto
import com.amaxonia.erp.domain.model.AuthUser
import com.amaxonia.erp.domain.model.CompanySession
import com.amaxonia.erp.domain.model.ElectronicInvoiceStatus
import com.amaxonia.erp.domain.model.InvoiceHistoryFilter
import com.amaxonia.erp.domain.model.SelectedCompany
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InvoiceHistoryRepositoryImplTest {

    private class FakeNetworkMonitor(var online: Boolean) : NetworkMonitor(
        android.content.ContextWrapper(null),
    ) {
        override fun isOnline(): Boolean = online
    }

    private class FakePendingInvoiceDao : PendingInvoiceDao {
        val invoices = mutableListOf<PendingInvoiceEntity>()

        override suspend fun insert(invoice: PendingInvoiceEntity) {
            invoices.add(invoice)
        }

        override suspend fun getById(id: String): PendingInvoiceEntity? {
            return invoices.find { it.id == id }
        }

        override suspend fun getPending(limit: Int): List<PendingInvoiceEntity> {
            return invoices.filter { it.status in listOf("PENDING", "FAILED") }.take(limit)
        }

        override suspend fun getPendingForTenant(tenantId: String, limit: Int): List<PendingInvoiceEntity> {
            return invoices.filter { it.tenantId == tenantId && it.status in listOf("PENDING", "FAILED") }.take(limit)
        }

        override suspend fun getAllRecent(limit: Int): List<PendingInvoiceEntity> {
            return invoices.take(limit)
        }

        override suspend fun getPendingCount(): Int {
            return invoices.count { it.status in listOf("PENDING", "FAILED") }
        }

        override suspend fun markSending(id: String, updatedAt: Long) {}
        override suspend fun markSent(id: String, remoteInvoiceId: String, remoteInvoiceNumber: String, updatedAt: Long) {}
        override suspend fun markFailed(id: String, lastError: String, updatedAt: Long) {}
        override suspend fun markRejected(id: String, message: String, updatedAt: Long) {}
        override suspend fun markInvalid(id: String, message: String, updatedAt: Long) {}
        override suspend fun tryClaim(id: String, now: Long, leasedUntil: Long, updatedAt: Long): Int = 1
        override suspend fun recoverInterrupted(staleBefore: Long, now: Long): Int = 0
        override suspend fun deleteById(id: String) { invoices.removeAll { it.id == id } }
    }

    private class FakeLocalStore : LocalStore(
        android.content.ContextWrapper(null),
    ) {
        var dummySession: CompanySession? = CompanySession(
            token = "dummy_token",
            company = SelectedCompany(id = 1, name = "Test Co", adminDb = "test_db"),
            user = AuthUser(id = 1, username = "admin", role = "admin"),
        )

        override suspend fun readCompanySession(): CompanySession? = dummySession
    }

    private fun createDummySaleRequest(
        total: Double = 100.0,
        clientName: String = "Cliente Offline",
        itemDesc: String = "Producto Alfa",
        itemCode: String = "PROD-A",
    ): ProcessSaleRequestDto {
        return ProcessSaleRequestDto(
            factura = SaleInvoiceDto(
                idCliente = "1",
                codCliente = "C1",
                idSucursal = 1,
                idCaja = "1",
                codigoCaja = "CJ1",
                subtotal = total,
                ivaTotalFactura = 0.0,
                totalTotalFactura = total,
                montoItemsFactura = total,
                totalizarBaseImponible = total,
                totalizarMontoIva = 0.0,
                totalizarTotalGeneral = total,
                usuarioCreacion = "admin",
                facturarA = clientName,
            ),
            items = listOf(
                SaleItemDto(
                    idItem = 10,
                    itemDescripcion = itemDesc,
                    itemCantidad = 2.0,
                    itemPrecioSinIva = total / 2.0,
                    itemPIva = 0.0,
                    itemTotalSinIva = total,
                    itemTotalConIva = total,
                    itemCantidadTotal = 2.0,
                    itemCodigo = itemCode,
                ),
            ),
            pagoResumen = SalePaymentSummaryDto(
                totalizarMontoCancelar = total,
                totalizarMontoEfectivo = total,
                totalizarCambio = 0.0,
            ),
        )
    }

    @Test
    fun getTransactionsOfflineReturnsPendingInvoices() = runTest {
        val dao = FakePendingInvoiceDao()
        val localStore = FakeLocalStore()
        val networkMonitor = FakeNetworkMonitor(online = false)

        val saleRequest = createDummySaleRequest(total = 50.0, clientName = "Consumidor Final")
        dao.insert(
            PendingInvoiceEntity(
                id = "OFF-101",
                countryCode = "PA",
                payloadJson = AppJson.encodeToString(ProcessSaleRequestDto.serializer(), saleRequest),
                localInvoiceNumber = "00000001",
                clientName = "Consumidor Final",
                status = "PENDING",
                tenantId = "1",
                total = 50.0,
                createdAt = 1700000000000L,
                updatedAt = 1700000000000L,
            ),
        )

        val repository = InvoiceHistoryRepositoryImpl(
            apiService = ApiService(ApiClient()),
            localStore = localStore,
            pendingInvoiceDao = dao,
            networkMonitor = networkMonitor,
        )

        val result = repository.getTransactions(filter = InvoiceHistoryFilter(), limit = 10)
        assertTrue(result.isSuccess)
        val page = result.getOrThrow()
        assertTrue(page.isOffline)
        assertEquals(1, page.transactions.size)
        val tx = page.transactions.first()
        assertEquals("OFF-101", tx.id)
        assertEquals("00000001", tx.invoiceNumber)
        assertEquals(50.0, tx.amount, 0.001)
        assertEquals(ElectronicInvoiceStatus.PENDING, tx.electronicStatus)
    }

    @Test
    fun getInvoiceDetailForOfflineTransactionDecodesPayloadJson() = runTest {
        val dao = FakePendingInvoiceDao()
        val localStore = FakeLocalStore()
        val networkMonitor = FakeNetworkMonitor(online = false)

        val saleRequest = createDummySaleRequest(
            total = 80.0,
            itemDesc = "Arroz Especial",
            itemCode = "ARR-01",
        )
        dao.insert(
            PendingInvoiceEntity(
                id = "OFF-202",
                countryCode = "PA",
                payloadJson = AppJson.encodeToString(ProcessSaleRequestDto.serializer(), saleRequest),
                localInvoiceNumber = "00000002",
                clientName = "Supermercado",
                status = "PENDING",
                tenantId = "1",
                total = 80.0,
                createdAt = 1700000000000L,
                updatedAt = 1700000000000L,
            ),
        )

        val repository = InvoiceHistoryRepositoryImpl(
            apiService = ApiService(ApiClient()),
            localStore = localStore,
            pendingInvoiceDao = dao,
            networkMonitor = networkMonitor,
        )

        val detailResult = repository.getInvoiceDetail("OFF-202")
        assertTrue(detailResult.isSuccess)
        val detail = detailResult.getOrThrow()
        assertEquals(1, detail.items.size)
        assertEquals("ARR-01", detail.items[0].codigo)
        assertEquals("Arroz Especial", detail.items[0].descripcion)
        assertEquals(2.0, detail.items[0].cantidad, 0.001)
        assertEquals(40.0, detail.items[0].precioUnitario, 0.001)
        assertEquals(80.0, detail.items[0].totalConIva, 0.001)
    }
}
