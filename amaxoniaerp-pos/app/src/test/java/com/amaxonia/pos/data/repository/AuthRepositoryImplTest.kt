package com.amaxonia.pos.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.amaxonia.pos.data.local.LocalStore
import com.amaxonia.pos.data.local.clearAuthSession
import com.amaxonia.pos.data.local.db.OfflineCompanyDao
import com.amaxonia.pos.data.local.db.OfflineCompanyEntity
import com.amaxonia.pos.data.local.db.OfflineUserDao
import com.amaxonia.pos.data.local.db.OfflineUserEntity
import com.amaxonia.pos.data.local.readAuthSnapshot
import com.amaxonia.pos.data.local.readCompanySession
import com.amaxonia.pos.data.local.security.SecureKeyValueStore
import com.amaxonia.pos.data.remote.ApiClient
import com.amaxonia.pos.data.remote.ApiConfigManager
import com.amaxonia.pos.data.remote.ApiService
import com.amaxonia.pos.data.sync.OfflineSyncScope
import com.amaxonia.pos.data.sync.OfflineSyncSettingsStore
import com.amaxonia.pos.domain.error.AuthenticationConnectivityException
import com.amaxonia.pos.domain.error.UnauthorizedException
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AuthRepositoryImplTest {
    private lateinit var store: LocalStore
    private lateinit var settingsStore: OfflineSyncSettingsStore
    private lateinit var fakeUserDao: FakeOfflineUserDao
    private lateinit var fakeCompanyDao: FakeOfflineCompanyDao
    private lateinit var authRepository: AuthRepositoryImpl

    private val testCountry = "PA"
    // MD5 de "secret123": 5d7845ac6ee7cfffafc5fe5f35cf666d
    private val passwordPlain = "secret123"
    private val passwordHash = "5d7845ac6ee7cfffafc5fe5f35cf666d"

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val secure = FakeSecureStore()
        store = LocalStore(context, secure)
        settingsStore = OfflineSyncSettingsStore(context)
        fakeUserDao = FakeOfflineUserDao()
        fakeCompanyDao = FakeOfflineCompanyDao()

        val configManager =
            ApiConfigManager().apply {
                updateBaseUrl(closedPortBaseUrl())
            }
        val apiService = ApiService(ApiClient(configManager))

        authRepository =
            AuthRepositoryImpl(
                apiService = apiService,
                localStore = store,
                offlineUserDao = fakeUserDao,
                offlineCompanyDao = fakeCompanyDao,
                offlineSyncSettingsStore = settingsStore,
            )
    }

    @After
    fun tearDown() =
        kotlinx.coroutines.runBlocking {
            store.clearAuthSession()
        }

    @Test
    fun `login offline exitoso cuando el modo offline esta habilitado y credenciales coinciden`() =
        runTest {
            settingsStore.save(OfflineSyncScope(enabled = true))
            fakeUserDao.users.add(
                OfflineUserEntity(
                    id = 1,
                    username = "cajero1",
                    passwordHash = passwordHash,
                    role = "2",
                    levelId = 1,
                    companyCodes = listOf(10),
                    countryCode = testCountry,
                    status = "A",
                ),
            )
            fakeCompanyDao.companies.add(
                OfflineCompanyEntity(
                    id = 10,
                    name = "Empresa Central",
                    adminDb = "amaxonia_central",
                    rif = "J-12345678",
                    countryCode = testCountry,
                ),
            )

            val result = authRepository.login("cajero1", passwordPlain, testCountry)

            assertTrue("El login debe ser exitoso: ${result.exceptionOrNull()}", result.isSuccess)
            val session = result.getOrThrow()
            assertTrue("Debe marcarse como offline", session.isOffline)
            assertEquals("cajero1", session.user.username)
            assertEquals(1, session.companies.size)
            assertEquals(10, session.companies.first().id)

            val snapshot = store.readAuthSnapshot()
            assertNotNull("Snapshot debe haberse guardado en LocalStore", snapshot)
            assertEquals("cajero1", snapshot?.user?.username)
        }

    @Test
    fun `login offline falla si la contrasena es incorrecta`() =
        runTest {
            settingsStore.save(OfflineSyncScope(enabled = true))
            fakeUserDao.users.add(
                OfflineUserEntity(
                    id = 1,
                    username = "cajero1",
                    passwordHash = passwordHash,
                    role = "2",
                    levelId = 1,
                    companyCodes = listOf(10),
                    countryCode = testCountry,
                    status = "A",
                ),
            )

            val result = authRepository.login("cajero1", "wrong_password", testCountry)

            assertTrue("Debe fallar", result.isFailure)
            assertTrue("Debe ser UnauthorizedException", result.exceptionOrNull() is UnauthorizedException)
        }

    @Test
    fun `login offline falla si el usuario esta inactivo`() =
        runTest {
            settingsStore.save(OfflineSyncScope(enabled = true))
            fakeUserDao.users.add(
                OfflineUserEntity(
                    id = 1,
                    username = "cajero_activo",
                    passwordHash = passwordHash,
                    role = "2",
                    levelId = 1,
                    companyCodes = listOf(10),
                    countryCode = testCountry,
                    status = "A",
                ),
            )
            fakeUserDao.users.add(
                OfflineUserEntity(
                    id = 2,
                    username = "cajero_inactivo",
                    passwordHash = passwordHash,
                    role = "2",
                    levelId = 1,
                    companyCodes = listOf(10),
                    countryCode = testCountry,
                    status = "I",
                ),
            )

            val result = authRepository.login("cajero_inactivo", passwordPlain, testCountry)

            assertTrue("Debe fallar", result.isFailure)
            assertTrue("Debe ser UnauthorizedException", result.exceptionOrNull() is UnauthorizedException)
        }

    @Test
    fun `login falla con error de conectividad si no hay usuarios sincronizados`() =
        runTest {
            fakeUserDao.users.clear()

            val result = authRepository.login("cajero1", passwordPlain, testCountry)

            assertTrue("Debe fallar", result.isFailure)
            assertTrue(
                "Debe ser UnexpectedAuthenticationException indicando que no hay usuarios sincronizados",
                result.exceptionOrNull() is com.amaxonia.pos.domain.error.UnexpectedAuthenticationException,
            )
        }

    @Test
    fun `selectCompany offline exitoso con datos de offlineCompanyDao`() =
        runTest {
            settingsStore.save(OfflineSyncScope(enabled = true))
            fakeUserDao.users.add(
                OfflineUserEntity(
                    id = 1,
                    username = "cajero1",
                    passwordHash = passwordHash,
                    role = "2",
                    levelId = 1,
                    companyCodes = listOf(10),
                    countryCode = testCountry,
                    status = "A",
                ),
            )
            fakeCompanyDao.companies.add(
                OfflineCompanyEntity(
                    id = 10,
                    name = "Empresa Central",
                    adminDb = "amaxonia_central",
                    rif = "J-12345678",
                    countryCode = testCountry,
                ),
            )

            val loginResult = authRepository.login("cajero1", passwordPlain, testCountry)
            assertTrue(loginResult.isSuccess)

            val companyResult = authRepository.selectCompany(10)
            assertTrue("selectCompany debe ser exitoso", companyResult.isSuccess)
            val companySession = companyResult.getOrThrow()
            assertTrue("Debe marcarse como offline", companySession.isOffline)
            assertEquals("Empresa Central", companySession.company.name)
            assertEquals("amaxonia_central", companySession.company.adminDb)

            val savedCompanySession = store.readCompanySession()
            assertNotNull("CompanySessionSnapshot debe haberse guardado", savedCompanySession)
            assertEquals("Empresa Central", savedCompanySession?.company?.name)
        }
}

private class FakeOfflineUserDao : OfflineUserDao {
    val users = mutableListOf<OfflineUserEntity>()

    override suspend fun upsertAll(users: List<OfflineUserEntity>) {
        this.users.removeAll { existing -> users.any { it.id == existing.id } }
        this.users.addAll(users)
    }

    override suspend fun findByUsernameAndCountry(
        username: String,
        countryCode: String,
    ): OfflineUserEntity? =
        users.firstOrNull {
            it.username.equals(username, ignoreCase = true) &&
                it.countryCode.equals(countryCode, ignoreCase = true)
        }

    override suspend fun countActiveUsers(countryCode: String): Int =
        users.count { it.countryCode.equals(countryCode, ignoreCase = true) && it.status == "A" }

    override suspend fun clearByCountry(countryCode: String) {
        users.removeAll { it.countryCode.equals(countryCode, ignoreCase = true) }
    }

    override suspend fun clearAll() {
        users.clear()
    }
}

private class FakeOfflineCompanyDao : OfflineCompanyDao {
    val companies = mutableListOf<OfflineCompanyEntity>()

    override suspend fun upsertAll(companies: List<OfflineCompanyEntity>) {
        this.companies.removeAll { existing -> companies.any { it.id == existing.id } }
        this.companies.addAll(companies)
    }

    override suspend fun getByIds(ids: List<Int>): List<OfflineCompanyEntity> = companies.filter { it.id in ids }

    override suspend fun getById(id: Int): OfflineCompanyEntity? = companies.firstOrNull { it.id == id }

    override suspend fun clearByCountry(countryCode: String) {
        companies.removeAll { it.countryCode.equals(countryCode, ignoreCase = true) }
    }

    override suspend fun clearAll() {
        companies.clear()
    }
}

private class FakeSecureStore : SecureKeyValueStore {
    private val values = mutableMapOf<String, String>()

    override fun readString(key: String): String? = values[key]

    override fun writeString(key: String, value: String) {
        values[key] = value
    }

    override fun remove(key: String) {
        values.remove(key)
    }
}
