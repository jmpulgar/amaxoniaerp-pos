package com.amaxonia.erp.data.repository

import android.content.ContextWrapper
import com.amaxonia.erp.data.local.LocalStore
import com.amaxonia.erp.data.local.db.CajaSesionDao
import com.amaxonia.erp.data.local.db.CajaSesionEntity
import com.amaxonia.erp.data.remote.ApiClient
import com.amaxonia.erp.data.remote.ApiService
import com.amaxonia.erp.data.remote.AppJson
import com.amaxonia.erp.data.remote.NetworkMonitor
import com.amaxonia.erp.domain.model.AuthUser
import com.amaxonia.erp.domain.model.Caja
import com.amaxonia.erp.domain.model.CajaSecuencia
import com.amaxonia.erp.domain.model.CajaSessionStatus
import com.amaxonia.erp.domain.model.CompanySession
import com.amaxonia.erp.domain.model.SelectedCompany
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CajaRepositoryImplTest {

    private class TestLocalStore : LocalStore(ContextWrapper(null)) {
        var companySession: CompanySession? = CompanySession(
            token = "test-token",
            company = SelectedCompany(id = 10, name = "Test Company", adminDb = "test_db"),
            user = AuthUser(id = 1, username = "cashier", role = "admin"),
        )
        var cachedCajas: List<Caja> = emptyList()
        var savedActiveCaja: Caja? = null

        override suspend fun readCompanySession(): CompanySession? = companySession
        override suspend fun saveCajas(cajas: List<Caja>) {
            cachedCajas = cajas
        }
        override suspend fun readCajas(): List<Caja> = cachedCajas
        override suspend fun saveActiveCaja(caja: Caja) {
            savedActiveCaja = caja
        }
        override suspend fun readActiveCaja(): Pair<String, String>? =
            savedActiveCaja?.let { Pair(it.idCaja, it.displayName) }
        override suspend fun clearActiveCaja() {
            savedActiveCaja = null
        }
    }

    private class FakeCajaSesionDao : CajaSesionDao {
        val sessions = mutableMapOf<String, CajaSesionEntity>()

        override suspend fun upsert(sesion: CajaSesionEntity) {
            sessions[sesion.localId] = sesion
        }

        override suspend fun getAbierta(tenantId: String): CajaSesionEntity? {
            return sessions.values.firstOrNull { it.tenantId == tenantId && it.estado == "ABIERTA" }
        }

        override suspend fun getAbiertaPorCaja(tenantId: String, cajaId: String): CajaSesionEntity? {
            return sessions.values.firstOrNull {
                it.tenantId == tenantId && it.cajaId == cajaId && it.estado == "ABIERTA"
            }
        }

        override suspend fun markCerradaPorCaja(tenantId: String, cajaId: String, closedAt: Long) {
            sessions.values
                .filter { it.tenantId == tenantId && it.cajaId == cajaId && it.estado == "ABIERTA" }
                .forEach {
                    sessions[it.localId] = it.copy(estado = "CERRADA", closedAt = closedAt)
                }
        }

        override suspend fun getByLocalId(localId: String): CajaSesionEntity? = sessions[localId]

        override suspend fun clearAll() {
            sessions.clear()
        }
    }

    private class FakeNetworkMonitor(var online: Boolean) : NetworkMonitor(ContextWrapper(null)) {
        override fun isOnline(): Boolean = online
    }

    private lateinit var localStore: TestLocalStore
    private lateinit var cajaSesionDao: FakeCajaSesionDao
    private lateinit var networkMonitor: FakeNetworkMonitor
    private lateinit var apiService: ApiService
    private lateinit var repository: CajaRepositoryImpl

    @Before
    fun setUp() {
        localStore = TestLocalStore()
        cajaSesionDao = FakeCajaSesionDao()
        networkMonitor = FakeNetworkMonitor(online = false)
        apiService = ApiService(ApiClient("http://localhost:59999"))
        repository = CajaRepositoryImpl(
            apiService = apiService,
            localStore = localStore,
            cajaSesionDao = cajaSesionDao,
            networkMonitor = networkMonitor,
        )
    }

    @Test
    fun getCajas_whenOffline_returnsCachedCajasFromLocalStore() = runTest {
        val cached = listOf(
            Caja(idCaja = "1", caja = "Caja Principal", descripcion = "Principal"),
            Caja(idCaja = "2", caja = "Caja 2", descripcion = "Secundaria"),
        )
        localStore.cachedCajas = cached

        val result = repository.getCajas()

        assertTrue(result.isSuccess)
        assertEquals(cached, result.getOrNull())
    }

    @Test
    fun checkCajaStatus_whenOffline_withPersistedOpenSession_returnsOpen() = runTest {
        val sec = CajaSecuencia(
            idCajaSecuencia = "SEC-100",
            idCaja = "1",
            fechaApertura = "2026-10-08",
            montoApertura = 100.0,
        )
        val entity = CajaSesionEntity(
            localId = "10_1_SEC-100",
            cajaId = "1",
            serverSecuenciaId = "SEC-100",
            estado = "ABIERTA",
            openedAt = System.currentTimeMillis(),
            tenantId = "10",
            secuenciaJson = AppJson.encodeToString(sec),
        )
        cajaSesionDao.upsert(entity)

        val statusResult = repository.checkCajaStatus("1")

        assertTrue(statusResult.isSuccess)
        val status = statusResult.getOrNull()
        assertNotNull(status)
        assertTrue(status!!.isOpen)
        assertEquals("SEC-100", status.cajaSecuencia?.idCajaSecuencia)
        assertEquals(CajaSessionStatus.ABIERTA, repository.sessionStatus.value)
        assertEquals("SEC-100", repository.activeCajaSecuencia.value?.idCajaSecuencia)
    }

    @Test
    fun checkCajaStatus_whenOffline_withoutPersistedSession_returnsClosed() = runTest {
        val statusResult = repository.checkCajaStatus("99")

        assertTrue(statusResult.isSuccess)
        val status = statusResult.getOrNull()
        assertNotNull(status)
        assertFalse(status!!.isOpen)
        assertNull(status.cajaSecuencia)
    }

    @Test
    fun setActiveCaja_restoresPersistedOpenSession() = runTest {
        val sec = CajaSecuencia(
            idCajaSecuencia = "SEC-200",
            idCaja = "2",
        )
        cajaSesionDao.upsert(
            CajaSesionEntity(
                localId = "10_2_SEC-200",
                cajaId = "2",
                serverSecuenciaId = "SEC-200",
                estado = "ABIERTA",
                openedAt = System.currentTimeMillis(),
                tenantId = "10",
                secuenciaJson = AppJson.encodeToString(sec),
            )
        )

        val caja = Caja(idCaja = "2", caja = "Caja Express")
        repository.setActiveCaja(caja)

        assertEquals("2", repository.activeCaja.value?.idCaja)
        assertEquals(CajaSessionStatus.ABIERTA, repository.sessionStatus.value)
        assertEquals("SEC-200", repository.activeCajaSecuencia.value?.idCajaSecuencia)
    }

    @Test
    fun markSequenceClosed_marksSessionClosedInDao() = runTest {
        val sec = CajaSecuencia(
            idCajaSecuencia = "SEC-300",
            idCaja = "3",
        )
        cajaSesionDao.upsert(
            CajaSesionEntity(
                localId = "10_3_SEC-300",
                cajaId = "3",
                serverSecuenciaId = "SEC-300",
                estado = "ABIERTA",
                openedAt = System.currentTimeMillis(),
                tenantId = "10",
                secuenciaJson = AppJson.encodeToString(sec),
            )
        )

        repository.setActiveCaja(Caja(idCaja = "3", caja = "Caja 3"))
        assertEquals(CajaSessionStatus.ABIERTA, repository.sessionStatus.value)

        repository.markSequenceClosed()

        assertEquals(CajaSessionStatus.PENDIENTE_APERTURA, repository.sessionStatus.value)
        assertNull(repository.activeCajaSecuencia.value)
        val openInDao = cajaSesionDao.getAbiertaPorCaja("10", "3")
        assertNull(openInDao)
    }
}
