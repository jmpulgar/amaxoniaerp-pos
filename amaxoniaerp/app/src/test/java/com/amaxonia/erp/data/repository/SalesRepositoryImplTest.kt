package com.amaxonia.erp.data.repository

import android.content.ContextWrapper
import com.amaxonia.erp.data.local.LocalStore
import com.amaxonia.erp.data.local.db.CajaPaymentMethodEntity
import com.amaxonia.erp.data.local.db.PaymentMethodDao
import com.amaxonia.erp.data.local.db.PaymentMethodEntity
import com.amaxonia.erp.data.remote.ApiClient
import com.amaxonia.erp.data.remote.ApiService
import com.amaxonia.erp.data.remote.NetworkMonitor
import com.amaxonia.erp.domain.model.AuthUser
import com.amaxonia.erp.domain.model.CompanySession
import com.amaxonia.erp.domain.model.SelectedCompany
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SalesRepositoryImplTest {

    private class TestLocalStore : LocalStore(ContextWrapper(null)) {
        var companySession: CompanySession? = CompanySession(
            token = "test-token",
            company = SelectedCompany(id = 10, name = "Test Company", adminDb = "test_db"),
            user = AuthUser(id = 1, username = "cashier", role = "admin"),
        )
        override suspend fun readCompanySession(): CompanySession? = companySession
    }

    private class FakePaymentMethodDao : PaymentMethodDao {
        val methods = mutableListOf<PaymentMethodEntity>()
        val cajaMappings = mutableListOf<CajaPaymentMethodEntity>()

        override suspend fun upsertAll(items: List<PaymentMethodEntity>) {
            methods.addAll(items)
        }

        override suspend fun upsertCajaMappings(items: List<CajaPaymentMethodEntity>) {
            cajaMappings.addAll(items)
        }

        override suspend fun clearPaymentMethods() {
            methods.clear()
        }

        override suspend fun clearCajaMappings() {
            cajaMappings.clear()
        }

        override suspend fun getAll(): List<PaymentMethodEntity> = methods

        override suspend fun getByCaja(cajaId: String): List<PaymentMethodEntity> {
            val allowedIds = cajaMappings
                .filter { it.idCaja == cajaId && (it.activo == null || it.activo == 1) }
                .map { it.idFormaPago }
                .toSet()
            return methods.filter { it.idFormaPago in allowedIds }
        }
    }

    private class FakeNetworkMonitor(var online: Boolean) : NetworkMonitor(ContextWrapper(null)) {
        override fun isOnline(): Boolean = online
    }

    private lateinit var localStore: TestLocalStore
    private lateinit var paymentMethodDao: FakePaymentMethodDao
    private lateinit var networkMonitor: FakeNetworkMonitor
    private lateinit var apiService: ApiService
    private lateinit var repository: SalesRepositoryImpl

    @Before
    fun setUp() {
        localStore = TestLocalStore()
        paymentMethodDao = FakePaymentMethodDao()
        networkMonitor = FakeNetworkMonitor(online = false)
        apiService = ApiService(ApiClient("http://localhost:59999"))
        repository = SalesRepositoryImpl(
            apiService = apiService,
            localStore = localStore,
            paymentMethodDao = paymentMethodDao,
            networkMonitor = networkMonitor,
        )
    }

    @Test
    fun getFormasPago_whenOffline_returnsMethodsFilteredByCaja() = runTest {
        val m1 = PaymentMethodEntity(idFormaPago = 1, descripcion = "Efectivo", siglas = "EF", activo = 1)
        val m2 = PaymentMethodEntity(idFormaPago = 2, descripcion = "Tarjeta Débito", siglas = "TD", activo = 1)
        val m3 = PaymentMethodEntity(idFormaPago = 3, descripcion = "Dólares", siglas = "USD", activo = 1)
        paymentMethodDao.upsertAll(listOf(m1, m2, m3))
        paymentMethodDao.upsertCajaMappings(
            listOf(
                CajaPaymentMethodEntity(idCaja = "CAJA-A", idFormaPago = 1, activo = 1),
                CajaPaymentMethodEntity(idCaja = "CAJA-A", idFormaPago = 2, activo = 1),
            )
        )

        val result = repository.getFormasPago("CAJA-A")

        assertTrue(result.isSuccess)
        val list = result.getOrNull().orEmpty()
        assertEquals(2, list.size)
        assertEquals(1, list[0].idFormaPago)
        assertEquals("Efectivo", list[0].descripcion)
        assertEquals(2, list[1].idFormaPago)
        assertEquals("Tarjeta Débito", list[1].descripcion)
    }

    @Test
    fun getFormasPago_whenOfflineAndNoSpecificCajaMappings_fallsBackToAll() = runTest {
        val m1 = PaymentMethodEntity(idFormaPago = 1, descripcion = "Efectivo", siglas = "EF")
        val m2 = PaymentMethodEntity(idFormaPago = 2, descripcion = "Tarjeta", siglas = "TARJ")
        paymentMethodDao.upsertAll(listOf(m1, m2))

        val result = repository.getFormasPago("CAJA-UNKNOWN")

        assertTrue(result.isSuccess)
        val list = result.getOrNull().orEmpty()
        assertEquals(2, list.size)
    }

    @Test
    fun getFormasPago_whenOnlineFails_recoversFromRoom() = runTest {
        networkMonitor.online = true
        val m1 = PaymentMethodEntity(idFormaPago = 1, descripcion = "Efectivo", siglas = "EF")
        paymentMethodDao.upsertAll(listOf(m1))

        val result = repository.getFormasPago("CAJA-1")

        assertTrue(result.isSuccess)
        val list = result.getOrNull().orEmpty()
        assertEquals(1, list.size)
        assertEquals(1, list[0].idFormaPago)
    }
}
